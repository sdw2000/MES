package com.fine.controller.stock;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fine.Utils.ResponseResult;
import com.fine.Dao.stock.TapeRollMapper;
import com.fine.modle.stock.TapeRoll;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@PreAuthorize("hasAnyAuthority('admin','warehouse')")
@RestController
@RequestMapping("/api/stock/rolls")
public class TapeRollController {

    @Autowired
    private TapeRollMapper tapeRollMapper;

    /**
     * 卷级库存分页查询（支持按批次ID过滤，支持仅显示可用卷）
     */
    @GetMapping("/list")
    public ResponseResult<Map<String, Object>> list(
            @RequestParam(name = "current", defaultValue = "1") int current,
            @RequestParam(name = "size", defaultValue = "20") int size,
            @RequestParam(name = "stockId", required = false) Long stockId,
            @RequestParam(name = "availableOnly", defaultValue = "false") boolean availableOnly
    ) {
        Page<TapeRoll> page = new Page<>(current, size);
        QueryWrapper<TapeRoll> qw = new QueryWrapper<>();
        if (stockId != null) {
            qw.eq("stock_id", stockId);
        }
        if (availableOnly) {
            qw.gt("available_area", 0);
        }
        qw.orderByAsc("fifo_order").orderByAsc("id");

        Page<TapeRoll> result = tapeRollMapper.selectPage(page, qw);
        Map<String, Object> payload = new HashMap<>();
        payload.put("records", result.getRecords());
        payload.put("total", result.getTotal());
        payload.put("current", result.getCurrent());
        payload.put("size", result.getSize());
        return ResponseResult.success(payload);
    }

    /**
     * 卷级库存分页联表查询（带料号/批次/卷类型过滤，返回批次字段便于前端展示）
     */
    @GetMapping("/list-with-stock")
    public ResponseResult<Map<String, Object>> listWithStock(
            @RequestParam(name = "current", defaultValue = "1") int current,
            @RequestParam(name = "size", defaultValue = "20") int size,
            @RequestParam(name = "materialCode", required = false) String materialCode,
            @RequestParam(name = "batchNo", required = false) String batchNo,
            @RequestParam(name = "rollType", required = false) String rollType,
            @RequestParam(name = "availableOnly", defaultValue = "false") boolean availableOnly
    ) {
        Page<TapeRoll> page = new Page<>(current, size);
        Page<TapeRoll> result = tapeRollMapper.selectPageWithStock(page, materialCode, batchNo, rollType, availableOnly);
        Map<String, Object> payload = new HashMap<>();
        payload.put("records", result.getRecords());
        payload.put("total", result.getTotal());
        payload.put("current", result.getCurrent());
        payload.put("size", result.getSize());
        return ResponseResult.success(payload);
    }
}
