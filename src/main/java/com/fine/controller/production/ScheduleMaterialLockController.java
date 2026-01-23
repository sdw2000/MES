package com.fine.controller.production;

import com.fine.Utils.ResponseResult;
import com.fine.model.production.ScheduleMaterialLockDTO;
import com.fine.service.production.ScheduleMaterialLockService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.fine.Dao.production.ScheduleCoatingMapper;
import com.fine.model.production.ScheduleCoating;

/**
 * 排程物料锁定控制器
 */
@Api(tags = "排程物料锁定管理")
@RestController
@RequestMapping("/api/schedule/material-lock")
@Slf4j
public class ScheduleMaterialLockController {
    
    @Autowired
    private ScheduleMaterialLockService lockService;
    
    @Autowired
    private ScheduleCoatingMapper scheduleCoatingMapper;
    
    /**
     * 为排程锁定物料
     */
    @ApiOperation("锁定排程物料")
    @PostMapping("/lock")
    public ResponseResult<String> lockMaterial(@RequestBody ScheduleMaterialLockDTO lockDTO) {
        try {
            log.info("锁定物料请求: scheduleId={}, filmWidth={}", 
                    lockDTO.getScheduleId(), lockDTO.getFilmWidth());
            
            boolean success = lockService.lockMaterialForSchedule(lockDTO);
            
            if (success) {
                return ResponseResult.success("物料锁定成功");
            } else {
                return ResponseResult.error("物料锁定失败");
            }
        } catch (Exception e) {
            log.error("锁定物料失败", e);
            return ResponseResult.error("锁定失败: " + e.getMessage());
        }
    }
    
    /**
     * 自动锁定物料（根据排程信息自动分配）
     */
    @ApiOperation("自动锁定物料")
    @PostMapping("/auto-lock")
    public ResponseResult<Map<String, Object>> autoLockMaterial(
            @RequestParam Long scheduleId,
            @RequestParam Integer filmWidth,
            @RequestParam(required = false) Integer filmThickness) {
        try {
            log.info("自动锁定物料: scheduleId={}, filmWidth={}, filmThickness={}", 
                    scheduleId, filmWidth, filmThickness);
            
            Map<String, Object> result = lockService.autoLockMaterial(scheduleId, filmWidth, filmThickness);
            return ResponseResult.success(result);
        } catch (Exception e) {
            log.error("自动锁定物料失败", e);
            return ResponseResult.error("自动锁定失败: " + e.getMessage());
        }
    }
    
    /**
     * 释放排程的锁定物料
     */
    @ApiOperation("释放锁定物料")
    @PostMapping("/unlock/{scheduleId}")
    public ResponseResult<String> unlockMaterial(@PathVariable Long scheduleId) {
        try {
            log.info("释放物料锁定: scheduleId={}", scheduleId);
            
            boolean success = lockService.unlockMaterialForSchedule(scheduleId);
            
            if (success) {
                return ResponseResult.success("物料释放成功");
            } else {
                return ResponseResult.error("物料释放失败");
            }
        } catch (Exception e) {
            log.error("释放物料失败", e);
            return ResponseResult.error("释放失败: " + e.getMessage());
        }
    }
    
    /**
     * 获取排程的锁定物料信息
     */
    @ApiOperation("获取锁定物料信息")
    @GetMapping("/locked-materials/{scheduleId}")
    public ResponseResult<List<Map<String, Object>>> getLockedMaterials(@PathVariable Long scheduleId) {
        try {
            log.info("查询锁定物料: scheduleId={}", scheduleId);
            
            List<Map<String, Object>> materials = lockService.getLockedMaterials(scheduleId);
            return ResponseResult.success(materials);
        } catch (Exception e) {
            log.error("查询锁定物料失败", e);
            return ResponseResult.error("查询失败: " + e.getMessage());
        }
    }
    
    /**
     * 批量锁定物料
     */
    @ApiOperation("批量锁定物料")
    @PostMapping("/batch-lock")
    public ResponseResult<Map<String, Object>> batchLockMaterial(@RequestBody List<ScheduleMaterialLockDTO> lockDTOs) {
        try {
            log.info("批量锁定物料, 数量={}", lockDTOs.size());
            
            int successCount = lockService.batchLockMaterial(lockDTOs);
            
            Map<String, Object> result = new HashMap<>();
            result.put("total", lockDTOs.size());
            result.put("success", successCount);
            result.put("failed", lockDTOs.size() - successCount);
            
            return ResponseResult.success(result);
        } catch (Exception e) {
            log.error("批量锁定物料失败", e);
            return ResponseResult.error("批量锁定失败: " + e.getMessage());
        }
    }
    
    /**
     * [调试用] 查询排程的所有涂布任务的jumboWidth值
     */
    @ApiOperation("[DEBUG] 查询排程涂布任务宽度")
    @GetMapping("/debug/check-jumbo-width/{scheduleId}")
    public ResponseResult<Map<String, Object>> checkJumboWidth(@PathVariable Long scheduleId) {
        try {
            log.info("[DEBUG] 查询排程涂布任务宽度: scheduleId={}", scheduleId);
            
            List<ScheduleCoating> tasks = scheduleCoatingMapper.selectByScheduleId(scheduleId);
            Map<String, Object> result = new HashMap<>();
            result.put("scheduleId", scheduleId);
            result.put("taskCount", tasks.size());
            
            List<Map<String, Object>> taskDetails = new java.util.ArrayList<>();
            int notNullCount = 0;
            for (ScheduleCoating task : tasks) {
                Map<String, Object> taskInfo = new HashMap<>();
                taskInfo.put("id", task.getId());
                taskInfo.put("taskNo", task.getTaskNo());
                taskInfo.put("jumboWidth", task.getJumboWidth());
                taskInfo.put("filmWidth", task.getFilmWidth());
                taskInfo.put("status", task.getStatus());
                taskDetails.add(taskInfo);
                
                if (task.getJumboWidth() != null) {
                    notNullCount++;
                }
            }
            
            result.put("tasks", taskDetails);
            result.put("withJumboWidthCount", notNullCount);
            result.put("message", "jumboWidth为NULL表示已解锁，有值表示仍已锁定");
            
            log.info("[DEBUG] 查询结果: 共{}个任务, 其中{}个有jumboWidth值", tasks.size(), notNullCount);
            
            return ResponseResult.success(result);
        } catch (Exception e) {
            log.error("[DEBUG] 查询失败", e);
            return ResponseResult.error("查询失败: " + e.getMessage());
        }
    }
}
