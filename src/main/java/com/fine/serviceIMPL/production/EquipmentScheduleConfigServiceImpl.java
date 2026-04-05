package com.fine.serviceIMPL.production;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fine.Dao.production.EquipmentMapper;
import com.fine.Dao.production.EquipmentScheduleConfigMapper;
import com.fine.Utils.ResponseResult;
import com.fine.model.production.Equipment;
import com.fine.model.production.EquipmentScheduleConfig;
import com.fine.service.production.EquipmentScheduleConfigService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Service
public class EquipmentScheduleConfigServiceImpl extends ServiceImpl<EquipmentScheduleConfigMapper, EquipmentScheduleConfig>
        implements EquipmentScheduleConfigService {

    private static final String DEFAULT_WEEK_START_TIME = "08:00:00";

    @Autowired
    private EquipmentScheduleConfigMapper equipmentScheduleConfigMapper;

    @Autowired
    private EquipmentMapper equipmentMapper;

    @Override
    public ResponseResult<?> getConfigList(String equipmentType, String keyword) {
        return ResponseResult.success("查询成功", equipmentScheduleConfigMapper.selectConfigList(equipmentType, keyword));
    }

    @Override
    @Transactional
    public ResponseResult<?> saveBatch(List<EquipmentScheduleConfig> configs) {
        if (configs == null || configs.isEmpty()) {
            return ResponseResult.fail("请提供要保存的设备排程配置");
        }

        List<EquipmentScheduleConfig> savedList = new ArrayList<>();
        Date now = new Date();

        for (EquipmentScheduleConfig item : configs) {
            if (item == null) {
                continue;
            }
            Equipment equipment = resolveEquipment(item);
            if (equipment == null || (equipment.getIsDeleted() != null && equipment.getIsDeleted() == 1)) {
                return ResponseResult.fail("设备不存在: " + fallbackEquipmentKey(item));
            }

            EquipmentScheduleConfig existing = equipmentScheduleConfigMapper.selectByEquipmentId(equipment.getId());
            EquipmentScheduleConfig target = existing == null ? new EquipmentScheduleConfig() : existing;
            if (target.getId() == null) {
                target.setCreateTime(now);
            }
            target.setEquipmentId(equipment.getId());
            target.setEquipmentCode(equipment.getEquipmentCode());
            target.setInitialScheduleTime(item.getInitialScheduleTime());
            target.setCycleEndTime(item.getCycleEndTime());
            target.setNextWeekStartTime(normalizeTimeText(item.getNextWeekStartTime()));
            target.setWeekendRest(defaultFlag(item.getWeekendRest(), 1));
            target.setSundayDisabled(defaultFlag(item.getSundayDisabled(), 1));
            target.setEnabled(defaultFlag(item.getEnabled(), 1));
            target.setMinStaffRequired(item.getMinStaffRequired() == null || item.getMinStaffRequired() <= 0 ? 1 : item.getMinStaffRequired());
            target.setRequiredSkillLevel(trimToNull(item.getRequiredSkillLevel()));
            target.setRemark(trimToNull(item.getRemark()));
            target.setUpdateTime(now);

            if (target.getId() == null) {
                equipmentScheduleConfigMapper.insert(target);
            } else {
                equipmentScheduleConfigMapper.updateById(target);
            }

            target.setEquipmentName(equipment.getEquipmentName());
            target.setEquipmentType(equipment.getEquipmentType());
            target.setEquipmentStatus(equipment.getStatus());
            savedList.add(applyDefaultValues(target));
        }

        return ResponseResult.success("保存成功", savedList);
    }

    @Override
    public EquipmentScheduleConfig getEffectiveConfig(String equipmentCode) {
        EquipmentScheduleConfig config = null;
        if (equipmentCode != null && !equipmentCode.trim().isEmpty()) {
            config = equipmentScheduleConfigMapper.selectByEquipmentCode(equipmentCode.trim());
        }
        EquipmentScheduleConfig effective = applyDefaultValues(config);
        if (effective.getEquipmentCode() == null && equipmentCode != null) {
            effective.setEquipmentCode(equipmentCode.trim());
        }
        if (effective.getEquipmentId() == null && equipmentCode != null) {
            Equipment equipment = resolveEquipmentByCode(equipmentCode.trim());
            if (equipment != null) {
                effective.setEquipmentId(equipment.getId());
                effective.setEquipmentCode(equipment.getEquipmentCode());
            }
        }
        return effective;
    }

    @Override
    public LocalDateTime normalizeScheduleStart(String equipmentCode, LocalDateTime candidate, Integer durationMinutes) {
        if (candidate == null) {
            return null;
        }
        EquipmentScheduleConfig config = getEffectiveConfig(equipmentCode);
        if (!flag(config.getEnabled())) {
            return candidate;
        }

        LocalDateTime result = candidate;
        if (config.getInitialScheduleTime() != null && result.isBefore(config.getInitialScheduleTime())) {
            result = config.getInitialScheduleTime();
        }

        int safeDurationMinutes = durationMinutes == null ? 0 : Math.max(durationMinutes, 0);
        for (int i = 0; i < 8; i++) {
            LocalDateTime weekendAdjusted = moveOutOfWeekend(result, config);
            if (!weekendAdjusted.equals(result)) {
                result = weekendAdjusted;
                continue;
            }

            if (config.getCycleEndTime() != null) {
                boolean exceeded = result.isAfter(config.getCycleEndTime());
                if (!exceeded && safeDurationMinutes > 0) {
                    exceeded = result.plusMinutes(safeDurationMinutes).isAfter(config.getCycleEndTime());
                }
                if (exceeded) {
                    result = nextWorkingStart(config.getCycleEndTime().plusSeconds(1), config);
                    continue;
                }
            }
            return result;
        }
        return result;
    }

    private Equipment resolveEquipment(EquipmentScheduleConfig item) {
        if (item.getEquipmentId() != null) {
            Equipment equipment = equipmentMapper.selectById(item.getEquipmentId());
            if (equipment != null) {
                return equipment;
            }
        }
        if (item.getEquipmentCode() != null && !item.getEquipmentCode().trim().isEmpty()) {
            return resolveEquipmentByCode(item.getEquipmentCode().trim());
        }
        return null;
    }

    private Equipment resolveEquipmentByCode(String equipmentCode) {
        QueryWrapper<Equipment> wrapper = new QueryWrapper<>();
        wrapper.eq("equipment_code", equipmentCode)
                .eq("is_deleted", 0)
                .last("LIMIT 1");
        return equipmentMapper.selectOne(wrapper);
    }

    private EquipmentScheduleConfig applyDefaultValues(EquipmentScheduleConfig config) {
        EquipmentScheduleConfig target = config == null ? new EquipmentScheduleConfig() : config;
        if (target.getNextWeekStartTime() == null || target.getNextWeekStartTime().trim().isEmpty()) {
            target.setNextWeekStartTime(DEFAULT_WEEK_START_TIME);
        } else {
            target.setNextWeekStartTime(normalizeTimeText(target.getNextWeekStartTime()));
        }
        if (target.getWeekendRest() == null) {
            target.setWeekendRest(1);
        }
        if (target.getSundayDisabled() == null) {
            target.setSundayDisabled(1);
        }
        if (target.getEnabled() == null) {
            target.setEnabled(1);
        }
        if (target.getMinStaffRequired() == null || target.getMinStaffRequired() <= 0) {
            target.setMinStaffRequired(1);
        }
        return target;
    }

    private LocalDateTime moveOutOfWeekend(LocalDateTime candidate, EquipmentScheduleConfig config) {
        if (candidate == null) {
            return null;
        }
        DayOfWeek dayOfWeek = candidate.getDayOfWeek();
        if (flag(config.getWeekendRest()) && dayOfWeek == DayOfWeek.SATURDAY) {
            return nextWorkingStart(candidate, config);
        }
        if ((flag(config.getWeekendRest()) || flag(config.getSundayDisabled())) && dayOfWeek == DayOfWeek.SUNDAY) {
            return nextWorkingStart(candidate, config);
        }
        return candidate;
    }

    private LocalDateTime nextWorkingStart(LocalDateTime from, EquipmentScheduleConfig config) {
        LocalDateTime cursor = from == null ? LocalDateTime.now() : from;
        LocalTime weekStart = parseLocalTime(config.getNextWeekStartTime());
        for (int i = 0; i < 8; i++) {
            DayOfWeek dayOfWeek = cursor.getDayOfWeek();
            if (flag(config.getWeekendRest()) && dayOfWeek == DayOfWeek.SATURDAY) {
                cursor = cursor.plusDays(2).with(weekStart);
                continue;
            }
            if ((flag(config.getWeekendRest()) || flag(config.getSundayDisabled())) && dayOfWeek == DayOfWeek.SUNDAY) {
                cursor = cursor.plusDays(1).with(weekStart);
                continue;
            }
            if (dayOfWeek == DayOfWeek.MONDAY && cursor.toLocalTime().isBefore(weekStart)) {
                cursor = cursor.with(weekStart);
            }
            return cursor;
        }
        return cursor.with(weekStart);
    }

    private LocalTime parseLocalTime(String value) {
        String normalized = normalizeTimeText(value);
        List<DateTimeFormatter> formatters = new ArrayList<>();
        formatters.add(DateTimeFormatter.ofPattern("HH:mm:ss"));
        formatters.add(DateTimeFormatter.ofPattern("HH:mm"));
        for (DateTimeFormatter formatter : formatters) {
            try {
                return LocalTime.parse(normalized, formatter);
            } catch (DateTimeParseException ignored) {
            }
        }
        return LocalTime.of(8, 0);
    }

    private String normalizeTimeText(String value) {
        if (value == null || value.trim().isEmpty()) {
            return DEFAULT_WEEK_START_TIME;
        }
        String text = value.trim();
        List<DateTimeFormatter> formatters = new ArrayList<>();
        formatters.add(DateTimeFormatter.ofPattern("HH:mm:ss"));
        formatters.add(DateTimeFormatter.ofPattern("HH:mm"));
        for (DateTimeFormatter formatter : formatters) {
            try {
                LocalTime time = LocalTime.parse(text, formatter);
                return time.format(DateTimeFormatter.ofPattern("HH:mm:ss"));
            } catch (DateTimeParseException ignored) {
            }
        }
        return DEFAULT_WEEK_START_TIME;
    }

    private Integer defaultFlag(Integer value, Integer defaultValue) {
        return value == null ? defaultValue : (value == 0 ? 0 : 1);
    }

    private boolean flag(Integer value) {
        return value == null || value == 1;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String text = value.trim();
        return text.isEmpty() ? null : text;
    }

    private String fallbackEquipmentKey(EquipmentScheduleConfig item) {
        if (item == null) {
            return "未知设备";
        }
        if (item.getEquipmentCode() != null && !item.getEquipmentCode().trim().isEmpty()) {
            return item.getEquipmentCode().trim();
        }
        if (item.getEquipmentId() != null) {
            return String.valueOf(item.getEquipmentId());
        }
        return "未知设备";
    }
}
