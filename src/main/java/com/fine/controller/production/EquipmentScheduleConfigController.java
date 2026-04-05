package com.fine.controller.production;

import com.fine.Utils.ResponseResult;
import com.fine.model.production.EquipmentScheduleConfig;
import com.fine.service.production.EquipmentScheduleConfigService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/production/equipment/schedule-config")
public class EquipmentScheduleConfigController {

    @Autowired
    private EquipmentScheduleConfigService equipmentScheduleConfigService;

    @GetMapping("/list")
    public ResponseResult<?> getList(@RequestParam(required = false) String equipmentType,
                                     @RequestParam(required = false) String keyword) {
        return equipmentScheduleConfigService.getConfigList(equipmentType, keyword);
    }

    @PostMapping("/batch-save")
    public ResponseResult<?> batchSave(@RequestBody List<EquipmentScheduleConfig> configs) {
        return equipmentScheduleConfigService.saveBatch(configs);
    }
}
