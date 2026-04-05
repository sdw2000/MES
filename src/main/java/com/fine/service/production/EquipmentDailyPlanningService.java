package com.fine.service.production;

import com.fine.Utils.ResponseResult;
import com.fine.model.production.EquipmentDailyStatus;
import com.fine.model.production.EquipmentStaffAssignment;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public interface EquipmentDailyPlanningService {

    ResponseResult<List<EquipmentDailyStatus>> getDailyStatusList(LocalDateTime planDate, String equipmentType, String keyword);

    ResponseResult<List<EquipmentDailyStatus>> saveDailyStatusBatch(List<EquipmentDailyStatus> list, String operator);

    ResponseResult<List<EquipmentStaffAssignment>> getStaffAssignments(LocalDateTime planDate, Long equipmentId);

    ResponseResult<List<EquipmentStaffAssignment>> saveStaffAssignments(LocalDateTime planDate,
                                                                        Long equipmentId,
                                                                        List<EquipmentStaffAssignment> assignments,
                                                                        String operator);

    ResponseResult<Map<String, Object>> getDailyStatusSummary(LocalDateTime planDate, String equipmentType, String keyword);

    int countOnDutyQualifiedStaff(String equipmentCode, LocalDate planDate, String requiredSkillLevel);
}
