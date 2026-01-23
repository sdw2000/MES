package com.fine.controller.production;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.fine.Utils.ResponseResult;
import com.fine.model.production.SlittingTask;
import com.fine.service.schedule.SlittingScheduleService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import java.util.Date;
import java.util.Map;

/**
 * 复卷分切排程 Controller
 */
@Api(tags = "复卷分切管理")
@RestController
@RequestMapping("/api/production/slitting-schedule")
public class SlittingScheduleController {
    
    @Autowired
    private SlittingScheduleService slittingService;
    
    @ApiOperation("从物料缺口创建分切任务")
    @PostMapping("/create-from-shortage")
    public ResponseResult<SlittingTask> createFromShortage(
            @ApiParam("缺口ID") @RequestParam Long shortageId,
            @ApiParam("设备ID") @RequestParam Long equipmentId,
            @ApiParam("计划日期") @RequestParam long scheduledDate) {
        try {
            SlittingTask task = slittingService.createSlittingTask(shortageId, equipmentId, new Date(scheduledDate));
            return ResponseResult.success(task);
        } catch (Exception e) {
            return ResponseResult.error(e.getMessage());
        }
    }
    
    @ApiOperation("分页查询分切任务")
    @GetMapping("/page")
    public ResponseResult<IPage<Map<String, Object>>> getSlittingTaskPage(
            @ApiParam("页码") @RequestParam(defaultValue = "1") Integer pageNum,
            @ApiParam("每页数量") @RequestParam(defaultValue = "10") Integer pageSize,
            @ApiParam("任务代码") @RequestParam(required = false) String taskCode,
            @ApiParam("状态") @RequestParam(required = false) String status) {
        try {
            IPage<Map<String, Object>> page = slittingService.getSlittingTaskPage(pageNum, pageSize, taskCode, status);
            return ResponseResult.success(page);
        } catch (Exception e) {
            return ResponseResult.error(e.getMessage());
        }
    }
    
    @ApiOperation("启动分切任务")
    @PostMapping("/start/{taskId}")
    public ResponseResult<String> startSlittingTask(
            @ApiParam("任务ID") @PathVariable Long taskId) {
        try {
            slittingService.startSlittingTask(taskId);
            return ResponseResult.success("分切任务已启动");
        } catch (Exception e) {
            return ResponseResult.error(e.getMessage());
        }
    }
    
    @ApiOperation("完成分切任务（回流库存）")
    @PostMapping("/complete/{taskId}")
    public ResponseResult<String> completeSlittingTask(
            @ApiParam("任务ID") @PathVariable Long taskId,
            @ApiParam("完成数量") @RequestParam Integer completedQty,
            @ApiParam("废料数量") @RequestParam Integer wasteQty) {
        try {
            slittingService.completeSlittingTask(taskId, completedQty, wasteQty);
            return ResponseResult.success("分切任务已完成，库存已回流");
        } catch (Exception e) {
            return ResponseResult.error(e.getMessage());
        }
    }
    
    @ApiOperation("标记分切任务失败")
    @PostMapping("/fail/{taskId}")
    public ResponseResult<String> failSlittingTask(
            @ApiParam("任务ID") @PathVariable Long taskId,
            @ApiParam("失败原因") @RequestParam String reason) {
        try {
            slittingService.failSlittingTask(taskId, reason);
            return ResponseResult.success("分切任务已标记为失败");
        } catch (Exception e) {
            return ResponseResult.error(e.getMessage());
        }
    }
    
    @ApiOperation("获取分切统计")
    @GetMapping("/stats")
    public ResponseResult<Map<String, Object>> getSlittingStats() {
        try {
            Map<String, Object> stats = slittingService.getSlittingStats();
            return ResponseResult.success(stats);
        } catch (Exception e) {
            return ResponseResult.error(e.getMessage());
        }
    }
}
