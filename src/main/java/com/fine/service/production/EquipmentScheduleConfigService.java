package com.fine.service.production;

import com.baomidou.mybatisplus.extension.service.IService;
import com.fine.Utils.ResponseResult;
import com.fine.model.production.EquipmentScheduleConfig;

import java.time.LocalDateTime;
import java.util.List;

public interface EquipmentScheduleConfigService extends IService<EquipmentScheduleConfig> {

    ResponseResult<?> getConfigList(String equipmentType, String keyword);

    ResponseResult<?> saveBatch(List<EquipmentScheduleConfig> configs);

    EquipmentScheduleConfig getEffectiveConfig(String equipmentCode);

    LocalDateTime normalizeScheduleStart(String equipmentCode, LocalDateTime candidate, Integer durationMinutes);
}
