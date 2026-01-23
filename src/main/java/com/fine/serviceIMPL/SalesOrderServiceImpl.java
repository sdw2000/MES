package com.fine.serviceIMPL;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.*;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fine.Dao.SalesOrderItemMapper;
import com.fine.Dao.production.SalesOrderMapper;
import com.fine.Dao.rd.TapeSpecMapper;
import com.fine.Dao.DeliveryNoticeItemMapper;
import com.fine.Utils.ResponseResult;
import com.fine.modle.LoginUser;
import com.fine.modle.SalesOrder;
import com.fine.modle.SalesOrderItem;
import com.fine.modle.rd.TapeSpec;
import com.fine.service.SalesOrderService;

import javax.servlet.http.HttpServletResponse;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.core.metadata.IPage;

@Service
public class SalesOrderServiceImpl extends ServiceImpl<SalesOrderMapper, SalesOrder> implements SalesOrderService {

    @Autowired
    private SalesOrderMapper salesOrderMapper;
    
    @Autowired
    private SalesOrderItemMapper salesOrderItemMapper;

    @Autowired
    private TapeSpecMapper tapeSpecMapper;

    @Autowired
    private DeliveryNoticeItemMapper deliveryNoticeItemMapper;

    @Override
    public ResponseResult<?> getAllOrders(Integer pageNum, Integer pageSize, String orderNo, String customer, String startDate, String endDate) {
        try {
            // 使用 MyBatis-Plus 分页
            Page<SalesOrder> page = new Page<>(pageNum != null ? pageNum : 1, pageSize != null ? pageSize : 10);
            
            // 使用自定义SQL查询，支持客户名称搜索
            IPage<SalesOrder> pageResult = salesOrderMapper.selectOrdersWithCustomerSearch(
                page, 
                orderNo, 
                customer,  // 现在支持客户代码、客户名称、简称的模糊搜索
                startDate, 
                endDate
            );
            
            System.out.println("查询订单 - 客户关键字: " + customer + ", 结果数: " + pageResult.getRecords().size());
            
            return new ResponseResult<>(200, "success", pageResult);
        } catch (Exception e) {
            e.printStackTrace();
            return new ResponseResult<>(500, "Failed to get orders: " + e.getMessage());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResponseResult<?> createOrder(SalesOrder salesOrder) {
        try {
            // 获取当前登录用户
            String username = getCurrentUsername();
            
            // 生成订单号
            if (salesOrder.getOrderNo() == null || salesOrder.getOrderNo().isEmpty()) {
                salesOrder.setOrderNo(generateOrderNo());
            }
            
            // 设置默认状态
            if (salesOrder.getStatus() == null || salesOrder.getStatus().isEmpty()) {
                salesOrder.setStatus("pending");
            }
            
            // 设置创建信息
            salesOrder.setCreatedBy(username);
            salesOrder.setUpdatedBy(username);
            salesOrder.setCreatedAt(new Date());
            salesOrder.setUpdatedAt(new Date());
            salesOrder.setIsDeleted(0);
            
            // 计算总金额和总面积
            calculateOrderTotals(salesOrder);
            
            // 预先填充规格信息，确保保存时包含颜色代码
            enrichItemsWithSpecInfo(salesOrder.getItems());

            // 保存订单主表
            int result = salesOrderMapper.insert(salesOrder);
            
            System.out.println("=== 保存订单明细 ===");
            System.out.println("订单ID: " + salesOrder.getId());
            System.out.println("明细数量: " + (salesOrder.getItems() != null ? salesOrder.getItems().size() : 0));
            
            if (result > 0 && salesOrder.getItems() != null && !salesOrder.getItems().isEmpty()) {
                // 保存订单明细
                int itemIndex = 0;
                for (SalesOrderItem item : salesOrder.getItems()) {
                    itemIndex++;
                    item.setOrderId(salesOrder.getId());
                    item.setCreatedBy(username);
                    item.setUpdatedBy(username);
                    item.setCreatedAt(new Date());
                    item.setUpdatedAt(new Date());
                    item.setIsDeleted(0);
                    
                    // 计算平方米数和金额
                    calculateItemAmounts(item);
                    
                    salesOrderItemMapper.insert(item);
                    System.out.println("保存明细 " + itemIndex + ": " + item.getMaterialCode() + " - " + item.getMaterialName());
                }
            }
            
            System.out.println("=== 订单创建成功 ===");
            System.out.println("订单号: " + salesOrder.getOrderNo());
            System.out.println("客户: " + salesOrder.getCustomer());
            System.out.println("总金额: " + salesOrder.getTotalAmount());
            System.out.println("==================");
            
            Map<String, Object> data = new HashMap<>();
            data.put("data", salesOrder);
            
            return new ResponseResult<>(200, "创建订单成功", data);
        } catch (Exception e) {
            System.err.println("创建订单失败: " + e.getMessage());
            e.printStackTrace();
            return new ResponseResult<>(500, "创建订单失败: " + e.getMessage());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResponseResult<?> updateOrder(SalesOrder salesOrder) {
        try {
            // 获取当前登录用户
            String username = getCurrentUsername();
            
            System.out.println("=== 开始更新订单 ===");
            System.out.println("订单号: " + salesOrder.getOrderNo());
            System.out.println("明细数量: " + (salesOrder.getItems() != null ? salesOrder.getItems().size() : 0));
            if (salesOrder.getItems() != null) {
                for (int i = 0; i < salesOrder.getItems().size(); i++) {
                    SalesOrderItem item = salesOrder.getItems().get(i);
                    System.out.println("  明细[" + i + "] - ID: " + item.getId() + ", 料号: " + item.getMaterialCode());
                }
            }
            
            // 查询原订单（只查未删除的）
            LambdaQueryWrapper<SalesOrder> queryWrapper = new LambdaQueryWrapper<>();
            queryWrapper.eq(SalesOrder::getOrderNo, salesOrder.getOrderNo())
                       .eq(SalesOrder::getIsDeleted, 0);
            SalesOrder existingOrder = salesOrderMapper.selectOne(queryWrapper);
            
            if (existingOrder == null) {
                return new ResponseResult<>(404, "订单不存在");
            }
            
            // 更新订单信息，保留原有的创建信息
            salesOrder.setId(existingOrder.getId());
            salesOrder.setCreatedBy(existingOrder.getCreatedBy());
            salesOrder.setCreatedAt(existingOrder.getCreatedAt());
            salesOrder.setUpdatedBy(username);
            salesOrder.setUpdatedAt(new Date());
            salesOrder.setIsDeleted(0); // 确保不会被误删
            
            // 计算总金额和总面积
            calculateOrderTotals(salesOrder);

            // 预先填充规格信息
            enrichItemsWithSpecInfo(salesOrder.getItems());
            
            // 更新订单主表
            salesOrderMapper.updateById(salesOrder);
            
            // 获取原有明细列表（只查未删除的）
            LambdaQueryWrapper<SalesOrderItem> itemWrapper = new LambdaQueryWrapper<>();
            itemWrapper.eq(SalesOrderItem::getOrderId, existingOrder.getId())
                      .eq(SalesOrderItem::getIsDeleted, 0);
            List<SalesOrderItem> oldItems = salesOrderItemMapper.selectList(itemWrapper);
            
            System.out.println("数据库中旧明细数量: " + oldItems.size());
            for (SalesOrderItem oldItem : oldItems) {
                System.out.println("  旧明细 - ID: " + oldItem.getId() + ", 料号: " + oldItem.getMaterialCode());
            }
            
            // 收集前端传来的明细ID
            Set<Long> newItemIds = new HashSet<>();
            if (salesOrder.getItems() != null) {
                for (SalesOrderItem item : salesOrder.getItems()) {
                    if (item.getId() != null) {
                        newItemIds.add(item.getId());
                    }
                }
            }
            
            System.out.println("前端传来的明细ID集合: " + newItemIds);
            
            // 逻辑删除前端没有传来的旧明细（说明被删除了）
            for (SalesOrderItem oldItem : oldItems) {
                if (!newItemIds.contains(oldItem.getId())) {
                    System.out.println("  删除旧明细 ID: " + oldItem.getId());
                    oldItem.setIsDeleted(1);
                    salesOrderItemMapper.updateById(oldItem);
                }
            }
            
            // 处理明细：有ID就更新，无ID就插入
            if (salesOrder.getItems() != null && !salesOrder.getItems().isEmpty()) {
                for (SalesOrderItem item : salesOrder.getItems()) {
                    item.setOrderId(salesOrder.getId());
                    item.setUpdatedBy(username);
                    item.setUpdatedAt(new Date());
                    item.setIsDeleted(0);
                    
                    // 计算平方米数和金额
                    calculateItemAmounts(item);
                    
                    if (item.getId() != null && item.getId() > 0) {
                        // 更新现有明细
                        System.out.println("  更新明细 ID: " + item.getId() + ", 料号: " + item.getMaterialCode());
                        salesOrderItemMapper.updateById(item);
                    } else {
                        // 新增明细
                        System.out.println("  新增明细，料号: " + item.getMaterialCode());
                        item.setCreatedBy(username);
                        item.setCreatedAt(new Date());
                        salesOrderItemMapper.insert(item);
                    }
                }
            }
            
            System.out.println("=== 订单更新成功 ===");
            System.out.println("订单号: " + salesOrder.getOrderNo());
            System.out.println("==================");
            
            Map<String, Object> data = new HashMap<>();
            data.put("data", salesOrder);
            
            return new ResponseResult<>(200, "更新订单成功", data);
        } catch (Exception e) {
            System.err.println("更新订单失败: " + e.getMessage());
            e.printStackTrace();
            return new ResponseResult<>(500, "更新订单失败: " + e.getMessage());
        }
    }

    private void enrichItemsWithSpecInfo(List<SalesOrderItem> items) {
        if (items == null || items.isEmpty()) return;
        
        for (SalesOrderItem item : items) {
            if (item.getMaterialCode() != null) {
                // 尝试从规格表中获取颜色代码
                TapeSpec spec = tapeSpecMapper.selectByMaterialCode(item.getMaterialCode());
                
                if (spec != null) {
                    item.setColorCode(spec.getColorCode());
                    // 如果明细中没有规格信息，使用规格表中的默认值
                    if (item.getThickness() == null) item.setThickness(spec.getTotalThickness());
                }
            }
        }
    }
    
    private void calculateOrderTotals(SalesOrder salesOrder) {
        BigDecimal totalAmount = BigDecimal.ZERO;
        BigDecimal totalArea = BigDecimal.ZERO;
        
        if (salesOrder.getItems() != null) {
            for (SalesOrderItem item : salesOrder.getItems()) {
                calculateItemAmounts(item);
                if (item.getAmount() != null) {
                    totalAmount = totalAmount.add(item.getAmount());
                }
                if (item.getSqm() != null) {
                    totalArea = totalArea.add(item.getSqm());
                }
            }
        }
        
        salesOrder.setTotalAmount(totalAmount);
        salesOrder.setTotalArea(totalArea);
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
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmss");
        return "SO" + sdf.format(new Date()) + (int) (Math.random() * 1000);
    }

    private void calculateItemAmounts(SalesOrderItem item) {
        if (item.getWidth() != null && item.getLength() != null && item.getRolls() != null) {
            // length已经是米，width是毫米，需要转换为米
            BigDecimal widthM = item.getWidth().divide(new BigDecimal(1000), 6, BigDecimal.ROUND_HALF_UP);
            BigDecimal lengthM = item.getLength(); // 已经是米，不需要转换
            
            BigDecimal area = widthM.multiply(lengthM).multiply(new BigDecimal(item.getRolls()));
            item.setSqm(area.setScale(2, BigDecimal.ROUND_HALF_UP));

            if (item.getUnitPrice() != null) {
                item.setAmount(area.multiply(item.getUnitPrice()).setScale(2, BigDecimal.ROUND_HALF_UP));
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResponseResult<?> deleteOrder(String orderNo) {
        try {
            // 查询订单
            LambdaQueryWrapper<SalesOrder> queryWrapper = new LambdaQueryWrapper<>();
            queryWrapper.eq(SalesOrder::getOrderNo, orderNo);
            SalesOrder order = salesOrderMapper.selectOne(queryWrapper);
            
            if (order == null) {
                return new ResponseResult<>(404, "订单不存在或已被删除: " + orderNo);
            }

            // 1. 逻辑删除关联明细
            // MyBatis-Plus 逻辑删除: Mapper.delete() 会自动转换为 UPDATE is_deleted=1 ...
            LambdaQueryWrapper<SalesOrderItem> itemWrapper = new LambdaQueryWrapper<>();
            itemWrapper.eq(SalesOrderItem::getOrderId, order.getId());
            salesOrderItemMapper.delete(itemWrapper);
            
            // 2. 逻辑删除主订单
            salesOrderMapper.deleteById(order.getId());
            
            System.out.println("=== 删除订单成功 ===");
            System.out.println("订单号: " + orderNo);
            
            return new ResponseResult<>(200, "删除成功");
        } catch (Exception e) {
            e.printStackTrace();
            return new ResponseResult<>(500, "删除失败: " + e.getMessage());
        }
    }

    @Override
    public ResponseResult<?> getOrderByOrderNo(String orderNo) {
        LambdaQueryWrapper<SalesOrder> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(SalesOrder::getOrderNo, orderNo);
        SalesOrder order = salesOrderMapper.selectOne(queryWrapper);
        
        if (order != null) {
            // 获取订单详情时，加载明细数据
            LambdaQueryWrapper<SalesOrderItem> itemWrapper = new LambdaQueryWrapper<>();
            itemWrapper.eq(SalesOrderItem::getOrderId, order.getId())
                      .eq(SalesOrderItem::getIsDeleted, 0);
            List<SalesOrderItem> items = salesOrderItemMapper.selectList(itemWrapper);
            
            // 填充颜色代码等信息
            enrichItemsWithSpecInfo(items);
            
            // 填充已发货数量
            for (SalesOrderItem item : items) {
                Integer shipped = deliveryNoticeItemMapper.getShippedQuantityByOrderItemId(item.getId());
                item.setShippedRolls(shipped);
            }

            order.setItems(items);
        }
        
        return new ResponseResult<>(200, "success", order);
    }

    @Override
    public ResponseResult<?> searchOrders(String keyword, String status) {
        try {
            LambdaQueryWrapper<SalesOrder> queryWrapper = new LambdaQueryWrapper<>();
            queryWrapper.eq(SalesOrder::getIsDeleted, 0);
            
            // 根据关键词搜索订单号或客户名
            if (keyword != null && !keyword.isEmpty()) {
                queryWrapper.and(wrapper -> 
                    wrapper.like(SalesOrder::getOrderNo, keyword)
                          .or()
                          .like(SalesOrder::getCustomer, keyword)
                );
            }
            
            // 根据状态筛选
            if (status != null && !status.isEmpty()) {
                queryWrapper.eq(SalesOrder::getStatus, status);
            }
            
            queryWrapper.orderByDesc(SalesOrder::getCreatedAt)
                       .last("LIMIT 20"); // 限制返回20条结果
            
            List<SalesOrder> orders = salesOrderMapper.selectList(queryWrapper);
            
            return new ResponseResult<>(200, "success", orders);
        } catch (Exception e) {
            e.printStackTrace();
            return new ResponseResult<>(500, "搜索订单失败: " + e.getMessage());
        }
    }

    @Override
    public void exportOrders(HttpServletResponse response) {
        // Implementation for exportOrders
    }

    @Override
    public ResponseResult<?> importOrders(MultipartFile file, String username) {
        return new ResponseResult<>(200, "Not implemented yet");
    }

    @Override
    public void downloadTemplate(HttpServletResponse response) {
         // Implementation for downloadTemplate
    }
}
