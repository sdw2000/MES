package com.fine.controller.production;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.fine.Utils.ResponseResult;
import com.fine.model.production.CoatingSchedule;
import com.fine.service.schedule.CoatingScheduleService;
import com.fine.service.production.ProductionScheduleService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import java.util.Date;
import java.util.Map;
import java.util.List;

/**
 * 涂布排程 Controller
 */
@Api(tags = "涂布排程管理")
@RestController
@RequestMapping("/api/production/coating-schedule")
public class CoatingScheduleController {
    
    @Autowired
    private CoatingScheduleService scheduleService;

    @Autowired
    private ProductionScheduleService productionScheduleService;
    
    @ApiOperation("从待涂布池创建排程")
    @PostMapping("/create-from-pool")
    public ResponseResult<CoatingSchedule> createFromPool(
            @ApiParam("待涂布池ID") @RequestParam Long poolId,
            @ApiParam("设备ID") @RequestParam Long equipmentId,
            @ApiParam("计划开始时间") @RequestParam long scheduledStart) {
        try {
            CoatingSchedule schedule = scheduleService.scheduleFromPool(poolId, equipmentId, new Date(scheduledStart));
            return ResponseResult.success(schedule);
        } catch (Exception e) {
            return ResponseResult.error(e.getMessage());
        }
    }
    
    @ApiOperation("分页查询排程")
    @GetMapping("/page")
    public ResponseResult<IPage<Map<String, Object>>> getSchedulePage(
            @ApiParam("页码") @RequestParam(defaultValue = "1") Integer pageNum,
            @ApiParam("每页数量") @RequestParam(defaultValue = "10") Integer pageSize,
            @ApiParam("排程代码") @RequestParam(required = false) String scheduleCode,
            @ApiParam("设备名称") @RequestParam(required = false) String equipmentName,
            @ApiParam("状态") @RequestParam(required = false) String status) {
        try {
            IPage<Map<String, Object>> page = scheduleService.getSchedulePage(pageNum, pageSize, scheduleCode, equipmentName, status);
            return ResponseResult.success(page);
        } catch (Exception e) {
            return ResponseResult.error(e.getMessage());
        }
    }
    
    @ApiOperation("检查设备冲突")
    @GetMapping("/check-conflicts")
    public ResponseResult<Map<String, Object>> checkEquipmentConflicts(
            @ApiParam("设备ID") @RequestParam Long equipmentId,
            @ApiParam("开始时间") @RequestParam long startTime,
            @ApiParam("结束时间") @RequestParam long endTime) {
        try {
            Map<String, Object> result = scheduleService.checkEquipmentConflicts(equipmentId, new Date(startTime), new Date(endTime));
            return ResponseResult.success(result);
        } catch (Exception e) {
            return ResponseResult.error(e.getMessage());
        }
    }
    
    @ApiOperation("完成排程")
    @PostMapping("/complete/{scheduleId}")
    public ResponseResult<String> completeSchedule(
            @ApiParam("排程ID") @PathVariable Long scheduleId,
            @ApiParam("实际完成时间") @RequestParam long actualEnd) {
        try {
            scheduleService.completeSchedule(scheduleId, new Date(actualEnd));
            return ResponseResult.success("排程已完成");
        } catch (Exception e) {
            return ResponseResult.error(e.getMessage());
        }
    }
    
    @ApiOperation("取消排程")
    @PostMapping("/cancel/{scheduleId}")
    public ResponseResult<String> cancelSchedule(
            @ApiParam("排程ID") @PathVariable Long scheduleId) {
        try {
            scheduleService.cancelSchedule(scheduleId);
            return ResponseResult.success("排程已取消");
        } catch (Exception e) {
            return ResponseResult.error(e.getMessage());
        }
    }

    @ApiOperation("重算涂布计划时间")
    @PostMapping("/recalculate")
    public ResponseResult<String> recalculate(
            @ApiParam("计划日期yyyy-MM-dd，可空") @RequestParam(required = false) String planDate,
            @ApiParam("任务间准备时间(分钟)，默认10") @RequestParam(required = false, defaultValue = "10") Integer gapMinutes) {
        try {
            boolean updated = productionScheduleService.recalculateCoatingPlan(planDate, gapMinutes);
            if (updated) {
                return ResponseResult.success("重算完成");
            }
            return ResponseResult.error("无可重算的涂布任务");
        } catch (Exception e) {
            return ResponseResult.error(e.getMessage());
        }
    }
    
    @ApiOperation("获取排程统计")
    @GetMapping("/stats")
    public ResponseResult<Map<String, Object>> getScheduleStats() {
        try {
            Map<String, Object> stats = productionScheduleService.getCoatingStats();
            return ResponseResult.success(stats);
        } catch (Exception e) {
            return ResponseResult.error(e.getMessage());
        }
    }

    // ========== 涂布看板数据 ==========

    @ApiOperation("获取涂布任务队列")
    @GetMapping("/queue")
    public ResponseResult<Map<String, Object>> getQueue(
            @ApiParam("计划日期") @RequestParam(required = false) String planDate,
            @ApiParam("页码") @RequestParam(required = false, defaultValue = "1") Integer pageNum,
            @ApiParam("页大小") @RequestParam(required = false, defaultValue = "10") Integer pageSize) {
        try {
            List<Map<String, Object>> all = productionScheduleService.getCoatingQueue(planDate);
            int total = all == null ? 0 : all.size();
            int from = Math.max(0, (pageNum - 1) * pageSize);
            int to = Math.min(total, from + pageSize);
            List<Map<String, Object>> pageList = total == 0 ? java.util.Collections.emptyList() : all.subList(from, to);

            Map<String, Object> result = new java.util.HashMap<>();
            result.put("list", pageList);
            result.put("total", total);
            result.put("pageNum", pageNum);
            result.put("pageSize", pageSize);
            return ResponseResult.success(result);
        } catch (Exception e) {
            return ResponseResult.error(e.getMessage());
        }
    }

    @ApiOperation("获取涂布时间轴")
    @GetMapping("/timeline")
    public ResponseResult<List<Map<String, Object>>> getTimeline(@ApiParam("计划日期") @RequestParam(required = false) String planDate) {
        try {
            return ResponseResult.success(productionScheduleService.getCoatingTimeline(planDate));
        } catch (Exception e) {
            return ResponseResult.error(e.getMessage());
        }
    }

    @ApiOperation("更新涂布任务设备")
    @PostMapping("/equipment")
    public ResponseResult<Boolean> updateEquipment(@RequestBody Map<String, Object> body) {
        try {
            Long taskId = body.get("taskId") == null ? null : Long.valueOf(body.get("taskId").toString());
            Long equipmentId = body.get("equipmentId") == null ? null : Long.valueOf(body.get("equipmentId").toString());
            boolean ok = productionScheduleService.updateCoatingEquipment(taskId, equipmentId);
            return ok ? ResponseResult.success(true) : ResponseResult.error("更新设备失败");
        } catch (Exception e) {
            return ResponseResult.error(e.getMessage());
        }
    }

    @Deprecated
    @ApiOperation(value = "获取涂布合并记录", notes = "已废弃：前端已移除该板块，仅保留兼容。")
    @GetMapping("/merge-records")
    public ResponseResult<List<Map<String, Object>>> getMergeRecords(@ApiParam("计划日期") @RequestParam(required = false) String planDate) {
        try {
            return ResponseResult.success(productionScheduleService.getCoatingMergeRecords(planDate));
        } catch (Exception e) {
            return ResponseResult.error(e.getMessage());
        }
    }

    @ApiOperation("调整涂布任务时间")
    @PostMapping("/adjust-time/{taskId}")
    public ResponseResult<String> adjustTime(@ApiParam("任务ID") @PathVariable Long taskId,
                                             @RequestBody Map<String, Object> data) {
        try {
            boolean updated = productionScheduleService.adjustCoatingTaskTime(taskId, data);
            return updated ? ResponseResult.success("调整成功") : ResponseResult.error("调整失败");
        } catch (Exception e) {
            return ResponseResult.error(e.getMessage());
        }
    }

    @ApiOperation("调整涂布任务涂布量")
    @PostMapping("/adjust-quantity/{taskId}")
    public ResponseResult<String> adjustQuantity(@ApiParam("任务ID") @PathVariable Long taskId,
                                                 @RequestBody Map<String, Object> data) {
        try {
            boolean updated = productionScheduleService.adjustCoatingTaskQuantity(taskId, data);
            return updated ? ResponseResult.success("调整成功") : ResponseResult.error("调整失败");
        } catch (Exception e) {
            return ResponseResult.error(e.getMessage());
        }
    }

    // ========== 待涂布池（动态排程看板） ==========

    @ApiOperation("获取待涂布订单池")
    @GetMapping("/pending-pool")
    public ResponseResult<Map<String, Object>> getPendingPool(
            @ApiParam("料号") @RequestParam(required = false) String materialCode,
            @ApiParam("状态") @RequestParam(required = false) String status) {
        try {
            Map<String, Object> params = new java.util.HashMap<>();
            params.put("materialCode", materialCode);
            params.put("status", status);
            Map<String, Object> pool = productionScheduleService.getPendingCoatingPool(params);
            return ResponseResult.success(pool);
        } catch (Exception e) {
            return ResponseResult.error(e.getMessage());
        }
    }

    @ApiOperation("按料号分组待涂布订单")
    @GetMapping("/pending-by-material")
    public ResponseResult<List<Map<String, Object>>> getPendingByMaterial() {
        try {
            List<Map<String, Object>> grouped = productionScheduleService.getPendingCoatingByMaterial();
            return ResponseResult.success(grouped);
        } catch (Exception e) {
            return ResponseResult.error(e.getMessage());
        }
    }

    @ApiOperation("复卷汇总（按料号+长度）")
    @GetMapping("/rewind-summary")
    public ResponseResult<List<Map<String, Object>>> getRewindSummary() {
        try {
            List<Map<String, Object>> grouped = productionScheduleService.getRewindSummary();
            return ResponseResult.success(grouped);
        } catch (Exception e) {
            return ResponseResult.error(e.getMessage());
        }
    }

    @ApiOperation("添加到待涂布池")
    @PostMapping("/add-to-pool")
    public ResponseResult<String> addToPool(@RequestBody Map<String, Object> data) {
        try {
            productionScheduleService.addToPendingCoatingPool(data);
            return ResponseResult.success("添加成功");
        } catch (Exception e) {
            return ResponseResult.error(e.getMessage());
        }
    }

    @ApiOperation("从待涂布池移除")
    @PostMapping("/remove-from-pool/{poolId}")
    public ResponseResult<String> removeFromPool(
            @ApiParam("池ID") @PathVariable Long poolId,
            @ApiParam("操作人") @RequestParam(defaultValue = "admin") String operator) {
        try {
            productionScheduleService.removeFromPendingCoatingPool(poolId, operator);
            return ResponseResult.success("移除成功");
        } catch (Exception e) {
            return ResponseResult.error(e.getMessage());
        }
    }

    @ApiOperation("生成涂布排程任务")
    @PostMapping("/generate-tasks")
    public ResponseResult<Map<String, Object>> generateTasks(@RequestBody Map<String, Object> data) {
        try {
            Map<String, Object> result = productionScheduleService.generateCoatingTasks(data);
            return ResponseResult.success(result);
        } catch (Exception e) {
            return ResponseResult.error(e.getMessage());
        }
    }
}
