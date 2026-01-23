package com.fine.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fine.Utils.ResponseResult;
import com.fine.Dao.SalesOrderItemMapper;
import com.fine.modle.SalesOrderItem;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 销售订单明细相关接口
 */
@RestController
@RequestMapping("/sales/order-items")
@PreAuthorize("hasAnyAuthority('admin','production')")
public class SalesOrderItemController {

    @Autowired
    private SalesOrderItemMapper salesOrderItemMapper;

    /**
     * 分页查询未完成的订单明细（按面积口径）
     * GET /sales/order-items/pending?current=1&size=20&orderNo=...&materialCode=...
     */
    @GetMapping("/pending")
    public ResponseResult<IPage<SalesOrderItem>> getPendingItems(
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String orderNo,
            @RequestParam(required = false) String materialCode) {

        Page<SalesOrderItem> page = new Page<>(current, size);
        IPage<SalesOrderItem> result = salesOrderItemMapper.selectPendingItems(page, orderNo, materialCode);
        return ResponseResult.success(result);
    }
}
