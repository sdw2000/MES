package com.fine.controller.stock;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.fine.Utils.ResponseResult;
import com.fine.model.stock.StockFlowLog;
import com.fine.service.stock.StockFlowLogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 统一库存流水控制器
 */
@RestController
@RequestMapping("/api/stock/flow")
public class StockFlowLogController {

    @Autowired
    private StockFlowLogService stockFlowLogService;

    /**
     * 分页查询库存流水
     */
    @GetMapping("/page")
    public ResponseResult<?> getStockFlowPage(
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String stockType,
            @RequestParam(required = false) String materialCode,
            @RequestParam(required = false) String batchNo,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String refNo,
            @RequestParam(required = false) String beginTime,
            @RequestParam(required = false) String endTime) {

        IPage<?> result = stockFlowLogService.getStockFlowPage(current, size, stockType,
                materialCode, batchNo, type, refNo, beginTime, endTime);

        Map<String, Object> data = new HashMap<>();
        data.put("records", result.getRecords());
        data.put("total", result.getTotal());
        data.put("current", result.getCurrent());
        data.put("size", result.getSize());
        data.put("pages", result.getPages());

        return ResponseResult.success("查询成功", data);
    }

    /**
     * 根据库存ID查询流水
     */
    @GetMapping("/by-stock/{stockType}/{stockId}")
    public ResponseResult<?> getStockFlowByStock(@PathVariable String stockType, @PathVariable Long stockId) {
        return ResponseResult.success("查询成功", stockFlowLogService.getStockFlowByStock(stockType, stockId));
    }
}