package com.fine.controller.stock;

import com.fine.Utils.ResponseResult;
import com.fine.service.stock.FilmStockService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 薄膜宽度查询控制器
 */
@Api(tags = "薄膜宽度管理")
@RestController
@RequestMapping("/api/film")
@Slf4j
public class FilmWidthController {

    @Autowired
    private FilmStockService filmStockService;

    /**
     * 获取可用的薄膜宽度列表（用于排程时选择）
     * @param thickness 可选：按厚度筛选
     * @return 可用宽度列表，包含库存信息
     */
    @ApiOperation("获取可用薄膜宽度列表")
    @GetMapping("/available-widths")
    public ResponseResult<List<Map<String, Object>>> getAvailableWidths(
            @RequestParam(required = false) Integer thickness) {
        try {
            log.info("查询可用薄膜宽度, thickness={}", thickness);
            List<Map<String, Object>> widths = filmStockService.getAvailableWidths(thickness);
            return ResponseResult.success(widths);
        } catch (Exception e) {
            log.error("查询可用薄膜宽度失败", e);
            return ResponseResult.error("查询失败: " + e.getMessage());
        }
    }

    /**
     * 获取指定宽度和厚度的详细库存信息
     * @param width 宽度(mm)
     * @param thickness 厚度(μm)
     * @return 库存详情
     */
    @ApiOperation("获取指定规格的库存详情")
    @GetMapping("/stock-detail")
    public ResponseResult<Map<String, Object>> getStockDetail(
            @RequestParam Integer width,
            @RequestParam(required = false) Integer thickness) {
        try {
            log.info("查询薄膜库存详情, width={}, thickness={}", width, thickness);
            Map<String, Object> detail = filmStockService.getStockDetailBySpec(width, thickness);
            return ResponseResult.success(detail);
        } catch (Exception e) {
            log.error("查询库存详情失败", e);
            return ResponseResult.error("查询失败: " + e.getMessage());
        }
    }

    /**
     * 检查指定规格的薄膜是否有足够库存
     * @param width 宽度(mm)
     * @param thickness 厚度(μm)
     * @param requiredArea 需求面积(㎡)
     * @return 是否有足够库存
     */
    @ApiOperation("检查库存是否充足")
    @GetMapping("/check-stock")
    public ResponseResult<Map<String, Object>> checkStock(
            @RequestParam Integer width,
            @RequestParam(required = false) Integer thickness,
            @RequestParam Double requiredArea) {
        try {
            log.info("检查库存, width={}, thickness={}, requiredArea={}", width, thickness, requiredArea);
            boolean isEnough = filmStockService.checkStockAvailability(width, thickness, requiredArea);
            Map<String, Object> result = new HashMap<>();
            result.put("isEnough", isEnough);
            result.put("width", width);
            result.put("thickness", thickness != null ? thickness : 0);
            result.put("requiredArea", requiredArea
            );
            return ResponseResult.success(result);
        } catch (Exception e) {
            log.error("检查库存失败", e);
            return ResponseResult.error("检查失败: " + e.getMessage());
        }
    }
}
