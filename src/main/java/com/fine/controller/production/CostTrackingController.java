package com.fine.controller.production;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.fine.Utils.ResponseResult;
import com.fine.model.production.CostTracking;
import com.fine.service.schedule.CostTrackingService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.util.Map;

/**
 * 成本追溯 Controller
 */
@Api(tags = "成本追溯管理")
@RestController
@RequestMapping("/api/production/cost-tracking")
@PreAuthorize("hasAnyAuthority('admin','finance','production')")
public class CostTrackingController {
    
    @Autowired
    private CostTrackingService costService;
    
    @ApiOperation("初始化订单成本追溯")
    @PostMapping("/initialize/{orderId}")
    public ResponseResult<CostTracking> initializeCostTracking(
            @ApiParam("订单ID") @PathVariable Long orderId) {
        try {
            CostTracking tracking = costService.initializeCostTracking(orderId);
            return ResponseResult.success(tracking);
        } catch (Exception e) {
            return ResponseResult.error(e.getMessage());
        }
    }
    
    @ApiOperation("更新物料成本")
    @PostMapping("/update-material-cost/{orderId}")
    public ResponseResult<String> updateMaterialCost(
            @ApiParam("订单ID") @PathVariable Long orderId,
            @ApiParam("成本金额") @RequestParam BigDecimal cost) {
        try {
            costService.updateMaterialCost(orderId, cost);
            return ResponseResult.success("物料成本已更新");
        } catch (Exception e) {
            return ResponseResult.error(e.getMessage());
        }
    }
    
    @ApiOperation("更新分切成本")
    @PostMapping("/update-slitting-cost/{orderId}")
    public ResponseResult<String> updateSlittingCost(
            @ApiParam("订单ID") @PathVariable Long orderId,
            @ApiParam("成本金额") @RequestParam BigDecimal cost) {
        try {
            costService.updateSlittingCost(orderId, cost);
            return ResponseResult.success("分切成本已更新");
        } catch (Exception e) {
            return ResponseResult.error(e.getMessage());
        }
    }
    
    @ApiOperation("更新涂布成本")
    @PostMapping("/update-coating-cost/{orderId}")
    public ResponseResult<String> updateCoatingCost(
            @ApiParam("订单ID") @PathVariable Long orderId,
            @ApiParam("成本金额") @RequestParam BigDecimal cost) {
        try {
            costService.updateCoatingCost(orderId, cost);
            return ResponseResult.success("涂布成本已更新");
        } catch (Exception e) {
            return ResponseResult.error(e.getMessage());
        }
    }
    
    @ApiOperation("更新人工成本")
    @PostMapping("/update-labor-cost/{orderId}")
    public ResponseResult<String> updateLaborCost(
            @ApiParam("订单ID") @PathVariable Long orderId,
            @ApiParam("成本金额") @RequestParam BigDecimal cost) {
        try {
            costService.updateLaborCost(orderId, cost);
            return ResponseResult.success("人工成本已更新");
        } catch (Exception e) {
            return ResponseResult.error(e.getMessage());
        }
    }
    
    @ApiOperation("完成成本汇总")
    @PostMapping("/complete/{orderId}")
    public ResponseResult<String> completeCostTracking(
            @ApiParam("订单ID") @PathVariable Long orderId,
            @ApiParam("完成数量") @RequestParam Integer finishedQty) {
        try {
            costService.completeCostTracking(orderId, finishedQty);
            return ResponseResult.success("成本追溯已完成");
        } catch (Exception e) {
            return ResponseResult.error(e.getMessage());
        }
    }
    
    @ApiOperation("分页查询成本追溯")
    @GetMapping("/page")
    public ResponseResult<IPage<Map<String, Object>>> getCostTrackingPage(
            @ApiParam("页码") @RequestParam(defaultValue = "1") Integer pageNum,
            @ApiParam("每页数量") @RequestParam(defaultValue = "10") Integer pageSize,
            @ApiParam("订单号") @RequestParam(required = false) String orderNo) {
        try {
            IPage<Map<String, Object>> page = costService.getCostTrackingPage(pageNum, pageSize, orderNo);
            return ResponseResult.success(page);
        } catch (Exception e) {
            return ResponseResult.error(e.getMessage());
        }
    }
    
    @ApiOperation("获取订单成本详情")
    @GetMapping("/detail/{orderId}")
    public ResponseResult<Map<String, Object>> getOrderCost(
            @ApiParam("订单ID") @PathVariable Long orderId) {
        try {
            Map<String, Object> cost = costService.getOrderCost(orderId);
            return ResponseResult.success(cost);
        } catch (Exception e) {
            return ResponseResult.error(e.getMessage());
        }
    }
}
