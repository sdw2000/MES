package com.fine.controller.production;

import com.fine.Utils.ResponseResult;
import com.fine.model.production.EquipmentDailyStatus;
import com.fine.model.production.EquipmentStaffAssignment;
import com.fine.service.production.EquipmentDailyPlanningService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/production/equipment/daily-planning")
public class EquipmentDailyPlanningController {

    private static final DateTimeFormatter DATETIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Autowired
    private EquipmentDailyPlanningService equipmentDailyPlanningService;

    @Autowired
    private ObjectMapper objectMapper;

    @GetMapping("/status/list")
    public ResponseResult<List<EquipmentDailyStatus>> getDailyStatusList(@RequestParam String planDate,
                                                                          @RequestParam(required = false) String equipmentType,
                                                                          @RequestParam(required = false) String keyword) {
        LocalDateTime date = parsePlanDateTime(planDate);
        return equipmentDailyPlanningService.getDailyStatusList(date, equipmentType, keyword);
    }

    @GetMapping("/status/summary")
    public ResponseResult<Map<String, Object>> getDailyStatusSummary(@RequestParam String planDate,
                                                                     @RequestParam(required = false) String equipmentType,
                                                                     @RequestParam(required = false) String keyword) {
        LocalDateTime date = parsePlanDateTime(planDate);
        return equipmentDailyPlanningService.getDailyStatusSummary(date, equipmentType, keyword);
    }

    @PostMapping("/status/batch-save")
    public ResponseResult<List<EquipmentDailyStatus>> saveDailyStatusBatch(@RequestBody Map<String, Object> body) {
        Object listObj = body == null ? null : body.get("list");
        List<EquipmentDailyStatus> list = listObj == null
                ? Collections.emptyList()
                : objectMapper.convertValue(listObj, new TypeReference<List<EquipmentDailyStatus>>() {});
        String operator = body == null ? null : String.valueOf(body.getOrDefault("operator", "system"));
        return equipmentDailyPlanningService.saveDailyStatusBatch(list, operator);
    }

    @GetMapping("/assignment/list")
    public ResponseResult<List<EquipmentStaffAssignment>> getAssignments(@RequestParam String planDate,
                                                                          @RequestParam Long equipmentId) {
        LocalDateTime date = parsePlanDateTime(planDate);
        return equipmentDailyPlanningService.getStaffAssignments(date, equipmentId);
    }

    @PostMapping("/assignment/save")
    public ResponseResult<List<EquipmentStaffAssignment>> saveAssignments(@RequestBody Map<String, Object> body) {
        String planDate = body == null ? null : String.valueOf(body.get("planDate"));
        Object equipmentIdObj = body == null ? null : body.get("equipmentId");
        Object assignmentsObj = body == null ? null : body.get("assignments");
        if (planDate == null || planDate.trim().isEmpty()) {
            return ResponseResult.error("planDate 不能为空");
        }
        if (equipmentIdObj == null) {
            return ResponseResult.error("equipmentId 不能为空");
        }
        Long equipmentId = equipmentIdObj instanceof Number
                ? ((Number) equipmentIdObj).longValue()
                : Long.parseLong(String.valueOf(equipmentIdObj));
        List<EquipmentStaffAssignment> assignments = assignmentsObj == null
            ? Collections.emptyList()
            : objectMapper.convertValue(assignmentsObj, new TypeReference<List<EquipmentStaffAssignment>>() {});
        String operator = body == null ? null : String.valueOf(body.getOrDefault("operator", "system"));
        return equipmentDailyPlanningService.saveStaffAssignments(parsePlanDateTime(planDate), equipmentId, assignments, operator);
    }

    private LocalDateTime parsePlanDateTime(String input) {
        if (input == null || input.trim().isEmpty()) {
            throw new IllegalArgumentException("planDate 不能为空");
        }
        String text = input.trim();
        if (text.length() <= 10) {
            return LocalDate.parse(text).atTime(LocalTime.of(8, 0, 0));
        }
        return LocalDateTime.parse(text, DATETIME_FMT);
    }
}
