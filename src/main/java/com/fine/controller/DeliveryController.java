package com.fine.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fine.modle.DeliveryNotice;
import com.fine.service.DeliveryNoticeService;
import com.fine.Dao.DeliveryNoticeItemMapper;
import com.fine.modle.DeliveryNoticeItem;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fine.Utils.ResponseResult;

@RestController
@RequestMapping("/delivery")
@PreAuthorize("hasAnyAuthority('admin', 'sales')")
public class DeliveryController {
    
    @Autowired
    private DeliveryNoticeService deliveryNoticeService;

    @Autowired
    private DeliveryNoticeItemMapper deliveryNoticeItemMapper;
    
    /**
     * 分页查询发货通知单
     */
    @GetMapping("/list")
    public ResponseResult list(
        @RequestParam(defaultValue = "1") Integer pageNum,
        @RequestParam(defaultValue = "10") Integer pageSize,
        @RequestParam(required = false) String noticeNo,
        @RequestParam(required = false) String orderNo,
        @RequestParam(required = false) String customer
    ) {
        Page<DeliveryNotice> page = new Page<>(pageNum, pageSize);
        QueryWrapper<DeliveryNotice> queryWrapper = new QueryWrapper<>();
        
        if (noticeNo != null && !noticeNo.isEmpty()) {
            queryWrapper.like("notice_no", noticeNo);
        }
        if (orderNo != null && !orderNo.isEmpty()) {
            queryWrapper.like("order_no", orderNo);
        }
        if (customer != null && !customer.isEmpty()) {
            queryWrapper.like("customer", customer);
        }
        queryWrapper.orderByDesc("created_at");
        
        return ResponseResult.success(deliveryNoticeService.page(page, queryWrapper));
    }
    
    /**
     * 创建发货通知单
     */
    @PostMapping("/create")
    public ResponseResult create(@RequestBody DeliveryNotice deliveryNotice) {
        try {
            DeliveryNotice created = deliveryNoticeService.createDeliveryNotice(deliveryNotice);
            return ResponseResult.success(created);
        } catch (Exception e) {
            return ResponseResult.error(500, "创建失败: " + e.getMessage());
        }
    }
    
    /**
     * 获取发货通知单详情
     */
    @GetMapping("/{id}")
    public ResponseResult getDetail(@PathVariable Long id) {
        DeliveryNotice notice = deliveryNoticeService.getDeliveryNoticeDetail(id);
        if (notice != null) {
            return ResponseResult.success(notice);
        } else {
            return ResponseResult.error(404, "未找到该发货单");
        }
    }
    
    /**
     * 确认发货 - 更新状态为已发货
     */
    @PostMapping("/confirm/{id}")
    public ResponseResult confirmShip(@PathVariable Long id) {
        try {
            DeliveryNotice notice = deliveryNoticeService.getById(id);
            if (notice == null) {
                return ResponseResult.error(404, "未找到该发货单");
            }
            
            if ("已发货".equals(notice.getStatus())) {
                return ResponseResult.error(400, "该发货单已确认发货");
            }
            
            // 更新状态为已发货
            notice.setStatus("已发货");
            boolean updated = deliveryNoticeService.updateById(notice);
            
            if (updated) {
                return ResponseResult.success("确认发货成功");
            } else {
                return ResponseResult.error(500, "确认发货失败");
            }
        } catch (Exception e) {
            return ResponseResult.error(500, "确认发货失败: " + e.getMessage());
        }
    }

    /**
     * 更新发货通知（包含明细）
     */
    @PostMapping("/update")
    public ResponseResult update(@RequestBody DeliveryNotice deliveryNotice) {
        try {
            if (deliveryNotice.getId() == null) {
                return ResponseResult.error(400, "缺少发货单 ID");
            }

            boolean ok = deliveryNoticeService.updateById(deliveryNotice);
            if (!ok) {
                return ResponseResult.error(500, "更新主表失败");
            }

            // 更新明细：先删除旧明细，再插入新明细（如果有）
            deliveryNoticeItemMapper.delete(new QueryWrapper<DeliveryNoticeItem>().eq("notice_id", deliveryNotice.getId()));
            if (deliveryNotice.getItems() != null && !deliveryNotice.getItems().isEmpty()) {
                for (DeliveryNoticeItem item : deliveryNotice.getItems()) {
                    item.setNoticeId(deliveryNotice.getId());
                    item.setId(null);
                    deliveryNoticeItemMapper.insert(item);
                }
            }

            return ResponseResult.success("更新成功");
        } catch (Exception e) {
            return ResponseResult.error(500, "更新失败: " + e.getMessage());
        }
    }
}
