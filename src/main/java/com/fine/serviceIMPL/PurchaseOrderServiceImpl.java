package com.fine.serviceIMPL;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fine.Dao.purchase.PurchaseOrderItemMapper;
import com.fine.Dao.purchase.PurchaseOrderMapper;
import com.fine.Dao.rd.TapeSpecMapper;
import com.fine.Utils.ResponseResult;
import com.fine.modle.LoginUser;
import com.fine.modle.PurchaseOrder;
import com.fine.modle.PurchaseOrderItem;
import com.fine.modle.rd.TapeSpec;
import com.fine.service.PurchaseOrderService;

import javax.servlet.http.HttpServletResponse;

@Service
public class PurchaseOrderServiceImpl extends ServiceImpl<PurchaseOrderMapper, PurchaseOrder> implements PurchaseOrderService {

    @Autowired
    private PurchaseOrderMapper purchaseOrderMapper;

    @Autowired
    private PurchaseOrderItemMapper purchaseOrderItemMapper;

    @Autowired
    private TapeSpecMapper tapeSpecMapper;

    @Override
    public ResponseResult<?> getAllOrders(Integer pageNum, Integer pageSize, String orderNo, String supplier, String startDate, String endDate) {
        try {
            Page<PurchaseOrder> page = new Page<>(pageNum != null ? pageNum : 1, pageSize != null ? pageSize : 10);
            IPage<PurchaseOrder> pageResult = purchaseOrderMapper.selectOrdersWithSupplierSearch(page, orderNo, supplier, startDate, endDate);
            return new ResponseResult<>(200, "success", pageResult);
        } catch (Exception e) {
            e.printStackTrace();
            return new ResponseResult<>(500, "Failed to get purchase orders: " + e.getMessage());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResponseResult<?> createOrder(PurchaseOrder purchaseOrder) {
        try {
            String username = getCurrentUsername();

            if (purchaseOrder.getOrderNo() == null || purchaseOrder.getOrderNo().isEmpty()) {
                purchaseOrder.setOrderNo(generateOrderNo());
            }
            if (purchaseOrder.getStatus() == null || purchaseOrder.getStatus().isEmpty()) {
                purchaseOrder.setStatus("pending");
            }

            purchaseOrder.setCreatedBy(username);
            purchaseOrder.setUpdatedBy(username);
            purchaseOrder.setCreatedAt(new Date());
            purchaseOrder.setUpdatedAt(new Date());
            purchaseOrder.setIsDeleted(0);

            calculateOrderTotals(purchaseOrder);
            enrichItemsWithSpecInfo(purchaseOrder.getItems());

            purchaseOrderMapper.insert(purchaseOrder);

            if (purchaseOrder.getItems() != null && !purchaseOrder.getItems().isEmpty()) {
                for (PurchaseOrderItem item : purchaseOrder.getItems()) {
                    item.setOrderId(purchaseOrder.getId());
                    item.setCreatedBy(username);
                    item.setUpdatedBy(username);
                    item.setCreatedAt(new Date());
                    item.setUpdatedAt(new Date());
                    item.setIsDeleted(0);
                    calculateItemAmounts(item);
                    purchaseOrderItemMapper.insert(item);
                }
            }

            Map<String, Object> data = new HashMap<>();
            data.put("data", purchaseOrder);
            return new ResponseResult<>(200, "创建采购订单成功", data);
        } catch (Exception e) {
            e.printStackTrace();
            return new ResponseResult<>(500, "创建采购订单失败: " + e.getMessage());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResponseResult<?> updateOrder(PurchaseOrder purchaseOrder) {
        try {
            String username = getCurrentUsername();
            LambdaQueryWrapper<PurchaseOrder> queryWrapper = new LambdaQueryWrapper<>();
            queryWrapper.eq(PurchaseOrder::getOrderNo, purchaseOrder.getOrderNo())
                        .eq(PurchaseOrder::getIsDeleted, 0);
            PurchaseOrder existing = purchaseOrderMapper.selectOne(queryWrapper);
            if (existing == null) {
                return new ResponseResult<>(404, "采购订单不存在");
            }

            purchaseOrder.setId(existing.getId());
            purchaseOrder.setCreatedBy(existing.getCreatedBy());
            purchaseOrder.setCreatedAt(existing.getCreatedAt());
            purchaseOrder.setUpdatedBy(username);
            purchaseOrder.setUpdatedAt(new Date());
            purchaseOrder.setIsDeleted(0);

            calculateOrderTotals(purchaseOrder);
            enrichItemsWithSpecInfo(purchaseOrder.getItems());

            purchaseOrderMapper.updateById(purchaseOrder);

            LambdaQueryWrapper<PurchaseOrderItem> itemWrapper = new LambdaQueryWrapper<>();
            itemWrapper.eq(PurchaseOrderItem::getOrderId, existing.getId())
                       .eq(PurchaseOrderItem::getIsDeleted, 0);
            List<PurchaseOrderItem> oldItems = purchaseOrderItemMapper.selectList(itemWrapper);

            Set<Long> newItemIds = new HashSet<>();
            if (purchaseOrder.getItems() != null) {
                for (PurchaseOrderItem item : purchaseOrder.getItems()) {
                    if (item.getId() != null) {
                        newItemIds.add(item.getId());
                    }
                }
            }

            for (PurchaseOrderItem oldItem : oldItems) {
                if (!newItemIds.contains(oldItem.getId())) {
                    oldItem.setIsDeleted(1);
                    purchaseOrderItemMapper.updateById(oldItem);
                }
            }

            if (purchaseOrder.getItems() != null && !purchaseOrder.getItems().isEmpty()) {
                for (PurchaseOrderItem item : purchaseOrder.getItems()) {
                    item.setOrderId(purchaseOrder.getId());
                    item.setUpdatedBy(username);
                    item.setUpdatedAt(new Date());
                    item.setIsDeleted(0);
                    calculateItemAmounts(item);
                    if (item.getId() != null && item.getId() > 0) {
                        purchaseOrderItemMapper.updateById(item);
                    } else {
                        item.setCreatedBy(username);
                        item.setCreatedAt(new Date());
                        purchaseOrderItemMapper.insert(item);
                    }
                }
            }

            Map<String, Object> data = new HashMap<>();
            data.put("data", purchaseOrder);
            return new ResponseResult<>(200, "更新采购订单成功", data);
        } catch (Exception e) {
            e.printStackTrace();
            return new ResponseResult<>(500, "更新采购订单失败: " + e.getMessage());
        }
    }

    private void enrichItemsWithSpecInfo(List<PurchaseOrderItem> items) {
        if (items == null || items.isEmpty()) {
            return;
        }
        for (PurchaseOrderItem item : items) {
            if (item.getMaterialCode() != null) {
                TapeSpec spec = tapeSpecMapper.selectByMaterialCode(item.getMaterialCode());
                if (spec != null) {
                    item.setColorCode(spec.getColorCode());
                    if (item.getThickness() == null) {
                        item.setThickness(spec.getTotalThickness());
                    }
                }
            }
        }
    }

    private void calculateOrderTotals(PurchaseOrder purchaseOrder) {
        BigDecimal totalAmount = BigDecimal.ZERO;
        BigDecimal totalArea = BigDecimal.ZERO;
        if (purchaseOrder.getItems() != null) {
            for (PurchaseOrderItem item : purchaseOrder.getItems()) {
                calculateItemAmounts(item);
                if (item.getAmount() != null) {
                    totalAmount = totalAmount.add(item.getAmount());
                }
                // 仅薄膜类（有宽度+长度）计入总面积；其他原材料的sqm用于承载总重，不计入面积
                if (item.getSqm() != null && item.getWidth() != null && item.getLength() != null) {
                    totalArea = totalArea.add(item.getSqm());
                }
            }
        }
        purchaseOrder.setTotalAmount(totalAmount);
        purchaseOrder.setTotalArea(totalArea);
    }

    private void calculateItemAmounts(PurchaseOrderItem item) {
        if (item.getWidth() != null && item.getLength() != null && item.getRolls() != null) {
            BigDecimal widthM = item.getWidth().divide(new BigDecimal(1000), 6, BigDecimal.ROUND_HALF_UP);
            BigDecimal lengthM = item.getLength();
            BigDecimal area = widthM.multiply(lengthM).multiply(new BigDecimal(item.getRolls()));
            item.setSqm(area.setScale(2, BigDecimal.ROUND_HALF_UP));
            if (item.getUnitPrice() != null) {
                item.setAmount(area.multiply(item.getUnitPrice()).setScale(2, BigDecimal.ROUND_HALF_UP));
            }
            return;
        }

        // 其他原材料：前端将总重传入sqm，后端按 总重 * 单价 计算金额
        if (item.getSqm() != null && item.getUnitPrice() != null) {
            item.setAmount(item.getSqm().multiply(item.getUnitPrice()).setScale(2, BigDecimal.ROUND_HALF_UP));
        }
    }

    private String getCurrentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof LoginUser) {
            return ((LoginUser) authentication.getPrincipal()).getUsername();
        }
        return "system";
    }

    @Override
    public String generateOrderNo() {
        String dateCode = new SimpleDateFormat("yyMMdd").format(new Date());
        String prefix = "CD" + dateCode;

        String lastOrderNo = purchaseOrderMapper.selectLastOrderNoByPrefix(prefix);
        int nextSeq = 1;
        if (lastOrderNo != null && lastOrderNo.length() > prefix.length()) {
            String seqPart = lastOrderNo.substring(prefix.length());
            try {
                nextSeq = Integer.parseInt(seqPart) + 1;
            } catch (Exception ignored) {
                nextSeq = 1;
            }
        }

        // 序号按2位起，不足补0（如 01、02...）
        return prefix + String.format("%02d", nextSeq);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResponseResult<?> deleteOrder(String orderNo) {
        try {
            LambdaQueryWrapper<PurchaseOrder> queryWrapper = new LambdaQueryWrapper<>();
            queryWrapper.eq(PurchaseOrder::getOrderNo, orderNo);
            PurchaseOrder order = purchaseOrderMapper.selectOne(queryWrapper);
            if (order == null) {
                return new ResponseResult<>(404, "采购订单不存在或已删除: " + orderNo);
            }

            LambdaQueryWrapper<PurchaseOrderItem> itemWrapper = new LambdaQueryWrapper<>();
            itemWrapper.eq(PurchaseOrderItem::getOrderId, order.getId());
            purchaseOrderItemMapper.delete(itemWrapper);

            purchaseOrderMapper.deleteById(order.getId());
            return new ResponseResult<>(200, "删除成功");
        } catch (Exception e) {
            e.printStackTrace();
            return new ResponseResult<>(500, "删除失败: " + e.getMessage());
        }
    }

    @Override
    public ResponseResult<?> getOrderByOrderNo(String orderNo) {
        LambdaQueryWrapper<PurchaseOrder> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(PurchaseOrder::getOrderNo, orderNo);
        PurchaseOrder order = purchaseOrderMapper.selectOne(queryWrapper);
        if (order != null) {
            LambdaQueryWrapper<PurchaseOrderItem> itemWrapper = new LambdaQueryWrapper<>();
            itemWrapper.eq(PurchaseOrderItem::getOrderId, order.getId())
                       .eq(PurchaseOrderItem::getIsDeleted, 0);
            List<PurchaseOrderItem> items = purchaseOrderItemMapper.selectList(itemWrapper);
            enrichItemsWithSpecInfo(items);
            order.setItems(items);
        }
        return new ResponseResult<>(200, "success", order);
    }

    @Override
    public ResponseResult<?> searchOrders(String keyword, String status) {
        try {
            LambdaQueryWrapper<PurchaseOrder> queryWrapper = new LambdaQueryWrapper<>();
            queryWrapper.eq(PurchaseOrder::getIsDeleted, 0);
            if (keyword != null && !keyword.isEmpty()) {
                queryWrapper.and(wrapper -> wrapper.like(PurchaseOrder::getOrderNo, keyword)
                        .or()
                        .like(PurchaseOrder::getSupplier, keyword));
            }
            if (status != null && !status.isEmpty()) {
                queryWrapper.eq(PurchaseOrder::getStatus, status);
            }
            queryWrapper.orderByDesc(PurchaseOrder::getCreatedAt).last("LIMIT 20");
            List<PurchaseOrder> orders = purchaseOrderMapper.selectList(queryWrapper);
            return new ResponseResult<>(200, "success", orders);
        } catch (Exception e) {
            e.printStackTrace();
            return new ResponseResult<>(500, "搜索采购订单失败: " + e.getMessage());
        }
    }

    @Override
    public void exportOrders(HttpServletResponse response) {
        // TODO: implement export if needed
    }

    @Override
    public ResponseResult<?> importOrders(MultipartFile file, String username) {
        return new ResponseResult<>(200, "Not implemented yet");
    }

    @Override
    public void downloadTemplate(HttpServletResponse response) {
        // TODO: implement template download if needed
    }
}
