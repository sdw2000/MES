package com.fine.serviceIMPL.production;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fine.Dao.production.EquipmentDailyStatusMapper;
import com.fine.Dao.production.EquipmentMapper;
import com.fine.Dao.production.EquipmentStaffAssignmentMapper;
import com.fine.Dao.production.ProductionStaffMapper;
import com.fine.Dao.production.ShiftDefinitionMapper;
import com.fine.Dao.production.StaffSkillMapper;
import com.fine.Utils.ResponseResult;
import com.fine.model.production.Equipment;
import com.fine.model.production.EquipmentDailyStatus;
import com.fine.model.production.EquipmentStaffAssignment;
import com.fine.model.production.ProductionStaff;
import com.fine.model.production.ShiftDefinition;
import com.fine.model.production.StaffSkill;
import com.fine.service.production.EquipmentDailyPlanningService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class EquipmentDailyPlanningServiceImpl implements EquipmentDailyPlanningService {

    private static final String CODE_OK = "OK";
    private static final String CODE_EQUIPMENT_NOT_NORMAL = "EQUIPMENT_NOT_NORMAL";
    private static final String CODE_DAILY_STATUS_NOT_OPEN = "DAILY_STATUS_NOT_OPEN";
    private static final String CODE_STAFF_NOT_ENOUGH = "STAFF_NOT_ENOUGH";

    @Autowired
    private EquipmentDailyStatusMapper equipmentDailyStatusMapper;

    @Autowired
    private EquipmentStaffAssignmentMapper equipmentStaffAssignmentMapper;

    @Autowired
    private EquipmentMapper equipmentMapper;

    @Autowired
    private ShiftDefinitionMapper shiftDefinitionMapper;

    @Autowired
    private ProductionStaffMapper productionStaffMapper;

    @Autowired
    private StaffSkillMapper staffSkillMapper;

    @Override
    public ResponseResult<List<EquipmentDailyStatus>> getDailyStatusList(LocalDateTime planDate, String equipmentType, String keyword) {
        LocalDateTime target = planDate == null ? LocalDate.now().atTime(LocalTime.of(8, 0, 0)) : planDate;
        List<EquipmentDailyStatus> list = equipmentDailyStatusMapper.selectDailyStatusList(target, equipmentType, keyword);
        for (EquipmentDailyStatus row : list) {
            if (row == null) {
                continue;
            }
            fillScheduleDecision(row, target);
        }
        return ResponseResult.success(list);
    }

    @Override
    public ResponseResult<Map<String, Object>> getDailyStatusSummary(LocalDateTime planDate, String equipmentType, String keyword) {
        LocalDateTime target = planDate == null ? LocalDate.now().atTime(LocalTime.of(8, 0, 0)) : planDate;
        List<EquipmentDailyStatus> list = equipmentDailyStatusMapper.selectDailyStatusList(target, equipmentType, keyword);

        int total = 0;
        int schedulable = 0;
        int unschedulable = 0;
        Map<String, Map<String, Object>> breakdownMap = new LinkedHashMap<>();

        for (EquipmentDailyStatus row : list) {
            if (row == null) {
                continue;
            }
            fillScheduleDecision(row, target);
            total++;
            boolean canSchedule = row.getCanSchedule() != null && row.getCanSchedule() == 1;
            if (canSchedule) {
                schedulable++;
            } else {
                unschedulable++;
            }

            String code = row.getCanScheduleCode() == null || row.getCanScheduleCode().trim().isEmpty()
                    ? CODE_OK
                    : row.getCanScheduleCode().trim().toUpperCase();
            String reason = row.getCanScheduleReason() == null || row.getCanScheduleReason().trim().isEmpty()
                    ? "可排"
                    : row.getCanScheduleReason().trim();

            Map<String, Object> bucket = breakdownMap.computeIfAbsent(code, key -> {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("code", key);
                item.put("label", reason);
                item.put("count", 0);
                item.put("canSchedule", CODE_OK.equals(key));
                return item;
            });
            bucket.put("count", ((Integer) bucket.get("count")) + 1);
        }

        Map<String, Object> data = new HashMap<>();
        data.put("planDate", target.toString());
        data.put("total", total);
        data.put("schedulable", schedulable);
        data.put("unschedulable", unschedulable);
        data.put("breakdown", new ArrayList<>(breakdownMap.values()));
        return ResponseResult.success(data);
    }

    @Override
    @Transactional
    public ResponseResult<List<EquipmentDailyStatus>> saveDailyStatusBatch(List<EquipmentDailyStatus> list, String operator) {
        if (list == null || list.isEmpty()) {
            return ResponseResult.fail("请提供要保存的数据");
        }
        Date now = new Date();
        String op = operator == null || operator.trim().isEmpty() ? "system" : operator.trim();
        List<EquipmentDailyStatus> saved = new ArrayList<>();

        for (EquipmentDailyStatus item : list) {
            if (item == null || item.getPlanDate() == null || item.getEquipmentId() == null) {
                continue;
            }
            Equipment equipment = equipmentMapper.selectById(item.getEquipmentId());
            if (equipment == null) {
                continue;
            }
            EquipmentDailyStatus existing = equipmentDailyStatusMapper.selectByDateAndEquipment(item.getPlanDate(), item.getEquipmentId());
            EquipmentDailyStatus target = existing == null ? new EquipmentDailyStatus() : existing;
            if (target.getId() == null) {
                target.setCreateTime(now);
                target.setCreateBy(op);
            }
            target.setEquipmentId(item.getEquipmentId());
            target.setEquipmentCode(equipment.getEquipmentCode());
            target.setPlanDate(item.getPlanDate());
            target.setDailyStatus(normalizeDailyStatus(item.getDailyStatus()));
            target.setReason(trimToNull(item.getReason()));
            target.setMinStaffRequired(item.getMinStaffRequired() == null || item.getMinStaffRequired() <= 0 ? 1 : item.getMinStaffRequired());
            target.setRequiredSkillLevel(trimToNull(item.getRequiredSkillLevel()));
            target.setUpdateTime(now);
            target.setUpdateBy(op);

            if (target.getId() == null) {
                equipmentDailyStatusMapper.insert(target);
            } else {
                equipmentDailyStatusMapper.updateById(target);
            }
            saved.add(target);
        }

        return ResponseResult.success("保存成功", saved);
    }

    @Override
    public ResponseResult<List<EquipmentStaffAssignment>> getStaffAssignments(LocalDateTime planDate, Long equipmentId) {
        if (planDate == null || equipmentId == null) {
            return ResponseResult.fail("planDate/equipmentId 不能为空");
        }
        List<EquipmentStaffAssignment> list = equipmentStaffAssignmentMapper.selectByDateAndEquipment(planDate, equipmentId);
        return ResponseResult.success(list);
    }

    @Override
    @Transactional
    public ResponseResult<List<EquipmentStaffAssignment>> saveStaffAssignments(LocalDateTime planDate,
                                                                                Long equipmentId,
                                                                                List<EquipmentStaffAssignment> assignments,
                                                                                String operator) {
        if (planDate == null || equipmentId == null) {
            return ResponseResult.fail("planDate/equipmentId 不能为空");
        }
        Equipment equipment = equipmentMapper.selectById(equipmentId);
        if (equipment == null) {
            return ResponseResult.fail("设备不存在");
        }

        String op = operator == null || operator.trim().isEmpty() ? "system" : operator.trim();
        Date now = new Date();

        equipmentStaffAssignmentMapper.deleteByDateAndEquipment(planDate, equipmentId);

        List<EquipmentStaffAssignment> saved = new ArrayList<>();
        for (EquipmentStaffAssignment item : assignments == null ? new ArrayList<EquipmentStaffAssignment>() : assignments) {
            if (item == null || item.getStaffId() == null || item.getShiftId() == null) {
                continue;
            }
            ProductionStaff staff = productionStaffMapper.selectById(item.getStaffId());
            if (staff == null || staff.getIsDeleted() != null && staff.getIsDeleted() == 1) {
                continue;
            }
            ShiftDefinition shift = shiftDefinitionMapper.selectById(item.getShiftId());
            EquipmentStaffAssignment target = new EquipmentStaffAssignment();
            target.setEquipmentId(equipmentId);
            target.setEquipmentCode(equipment.getEquipmentCode());
            target.setPlanDate(planDate);
            target.setShiftId(item.getShiftId());
            target.setShiftCode(shift == null ? null : shift.getShiftCode());
            target.setShiftName(shift == null ? null : shift.getShiftName());
            target.setStaffId(staff.getId());
            target.setStaffCode(staff.getStaffCode());
            target.setStaffName(staff.getStaffName());
            target.setRoleName(trimToNull(item.getRoleName()));
            target.setOnDuty(item.getOnDuty() == null ? 1 : (item.getOnDuty() == 0 ? 0 : 1));
            target.setRemark(trimToNull(item.getRemark()));
            target.setCreateTime(now);
            target.setUpdateTime(now);
            target.setCreateBy(op);
            target.setUpdateBy(op);
            equipmentStaffAssignmentMapper.insert(target);
            saved.add(target);
        }

        return ResponseResult.success("保存成功", saved);
    }

    @Override
    public int countOnDutyQualifiedStaff(String equipmentCode, LocalDate planDate, String requiredSkillLevel) {
        if (equipmentCode == null || equipmentCode.trim().isEmpty() || planDate == null) {
            return 0;
        }
        Equipment equipment = findByEquipmentCode(equipmentCode.trim());
        if (equipment == null) {
            return 0;
        }
        LocalDateTime targetDateTime = planDate.atTime(LocalTime.of(8, 0, 0));
        List<EquipmentStaffAssignment> assignments = equipmentStaffAssignmentMapper.selectByDateAndEquipment(targetDateTime, equipment.getId());
        if (assignments == null || assignments.isEmpty()) {
            return 0;
        }

        int count = 0;
        for (EquipmentStaffAssignment assignment : assignments) {
            if (assignment.getOnDuty() == null || assignment.getOnDuty() == 0 || assignment.getStaffId() == null) {
                continue;
            }
            if (requiredSkillLevel == null || requiredSkillLevel.trim().isEmpty()) {
                count++;
                continue;
            }
            List<StaffSkill> skills = staffSkillMapper.selectByStaffId(assignment.getStaffId());
            if (skills == null || skills.isEmpty()) {
                continue;
            }
            for (StaffSkill skill : skills) {
                if (skill == null) {
                    continue;
                }
                if (!equipment.getEquipmentType().equalsIgnoreCase(String.valueOf(skill.getEquipmentType()))) {
                    continue;
                }
                if (skillLevelEnough(skill.getProficiency(), requiredSkillLevel)) {
                    count++;
                    break;
                }
            }
        }
        return count;
    }

    private void fillScheduleDecision(EquipmentDailyStatus row, LocalDateTime target) {
        int available = countOnDutyQualifiedStaff(row.getEquipmentCode(), target.toLocalDate(), row.getRequiredSkillLevel());
        row.setAvailableStaffCount(available);

        int required = row.getMinStaffRequired() == null || row.getMinStaffRequired() <= 0 ? 1 : row.getMinStaffRequired();
        String equipmentStatus = row.getEquipmentStatus() == null ? "" : row.getEquipmentStatus().trim().toLowerCase();
        String dailyStatus = row.getDailyStatus() == null ? "OPEN" : row.getDailyStatus().trim().toUpperCase();

        boolean can = true;
        String reason = "可排";
        String code = CODE_OK;
        if (!"normal".equals(equipmentStatus)) {
            can = false;
            reason = "设备状态非normal";
            code = CODE_EQUIPMENT_NOT_NORMAL;
        } else if (!"OPEN".equals(dailyStatus)) {
            can = false;
            reason = "日状态非OPEN(" + dailyStatus + ")";
            code = CODE_DAILY_STATUS_NOT_OPEN;
        } else if (available < required) {
            can = false;
            reason = "在岗人数不足(" + available + "/" + required + ")";
            code = CODE_STAFF_NOT_ENOUGH;
        }
        row.setCanSchedule(can ? 1 : 0);
        row.setCanScheduleReason(reason);
        row.setCanScheduleCode(code);
    }

    private Equipment findByEquipmentCode(String equipmentCode) {
        QueryWrapper<Equipment> wrapper = new QueryWrapper<>();
        wrapper.eq("equipment_code", equipmentCode).eq("is_deleted", 0).last("LIMIT 1");
        return equipmentMapper.selectOne(wrapper);
    }

    private boolean skillLevelEnough(String current, String required) {
        int currentRank = skillRank(current);
        int requiredRank = skillRank(required);
        return currentRank >= requiredRank;
    }

    private int skillRank(String level) {
        String val = level == null ? "" : level.trim().toLowerCase();
        if ("expert".equals(val) || "精通".equals(val)) return 3;
        if ("skilled".equals(val) || "熟练".equals(val) || "middle".equals(val)) return 2;
        if ("normal".equals(val) || "一般".equals(val) || "junior".equals(val)) return 1;
        return 0;
    }

    private String normalizeDailyStatus(String status) {
        String val = status == null ? "OPEN" : status.trim().toUpperCase();
        if (!"OPEN".equals(val) && !"CLOSED".equals(val) && !"MAINTENANCE".equals(val) && !"FAULT".equals(val)) {
            return "OPEN";
        }
        return val;
    }

    private String trimToNull(String value) {
        if (value == null) return null;
        String text = value.trim();
        return text.isEmpty() ? null : text;
    }
}
