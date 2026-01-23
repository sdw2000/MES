package com.fine.serviceIMPL.schedule;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fine.mapper.schedule.CustomerMaterialPriceStatsMapper;
import com.fine.mapper.schedule.CustomerTransactionStatsMapper;
import com.fine.mapper.schedule.OrderCustomerPriorityMapper;
import com.fine.model.schedule.CustomerMaterialPriceStats;
import com.fine.model.schedule.CustomerTransactionStats;
import com.fine.model.schedule.OrderCustomerPriority;
import com.fine.service.schedule.CustomerPriorityService;
import com.fine.Dao.production.SalesOrderMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 客户优先级计算Service实现
 * 
 * 计算规则：
 * 1. 账期得分 = max[10 - 1×(账期月数-3), 0]
 * 2. 月均成交金额得分 = 近3个月总成交金额 ÷ 30
 * 3. 单价得分：根据单价偏差率分档计算
 * 4. 总分 = 三项得分之和
 */
@Service
public class CustomerPriorityServiceImpl implements CustomerPriorityService {
    
    @Autowired
    private OrderCustomerPriorityMapper priorityMapper;
    
    @Autowired
    private CustomerTransactionStatsMapper transactionStatsMapper;
    
    @Autowired
    private CustomerMaterialPriceStatsMapper priceStatsMapper;
    
    @Autowired
    private SalesOrderMapper salesOrderMapper;
    
    @Override
    @Transactional
    public OrderCustomerPriority calculateOrderPriority(Long orderId, String orderNo, Long customerId,
                                                         String materialCode, BigDecimal unitPrice,
                                                         Date orderTime) {
        
        // 1. 查询或创建客户交易统计
        CustomerTransactionStats transStats = getOrCreateTransactionStats(customerId);
        
        // 2. 查询客户料号单价统计
        CustomerMaterialPriceStats priceStats = getPriceStats(customerId, materialCode);
        
        // 3. 计算三项得分
        BigDecimal paymentTermsScore = calculatePaymentTermsScore(transStats.getPaymentTerms());
        BigDecimal avgAmountScore = calculateAvgAmountScore(transStats.getAvgMonthlyAmount());
        BigDecimal unitPriceScore = calculateUnitPriceScore(unitPrice, priceStats);
        
        // 4. 计算总分
        BigDecimal totalScore = paymentTermsScore.add(avgAmountScore).add(unitPriceScore);
        
        // 5. 确定优先级级别
        String priorityLevel = determinePriorityLevel(totalScore);
        
        // 6. 保存或更新优先级记录
        OrderCustomerPriority priority = new OrderCustomerPriority();
        priority.setOrderId(orderId);
        priority.setOrderNo(orderNo);
        priority.setCustomerId(customerId);
        priority.setCustomerName(transStats.getCustomerName());
        priority.setPaymentTermsScore(paymentTermsScore);
        priority.setAvgAmountScore(avgAmountScore);
        priority.setUnitPriceScore(unitPriceScore);
        priority.setTotalScore(totalScore);
        priority.setPriorityLevel(priorityLevel);
        priority.setOrderTime(orderTime);
        priority.setCalculatedAt(new Date());
        
        // 检查是否已存在
        QueryWrapper<OrderCustomerPriority> wrapper = new QueryWrapper<>();
        wrapper.eq("order_id", orderId);
        OrderCustomerPriority existing = priorityMapper.selectOne(wrapper);
        
        if (existing != null) {
            priority.setId(existing.getId());
            priorityMapper.updateById(priority);
        } else {
            priorityMapper.insert(priority);
        }
        
        return priority;
    }
    
    @Override
    @Transactional
    public List<OrderCustomerPriority> batchCalculateOrderPriority(List<Long> orderIds) {
        List<OrderCustomerPriority> results = new ArrayList<>();
        
        // 查询订单信息并批量计算
        for (Long orderId : orderIds) {
            // 从销售订单表查询订单信息
            Map<String, Object> orderInfo = salesOrderMapper.selectOrderInfoById(orderId);
            if (orderInfo != null) {
                Long customerId = (Long) orderInfo.get("customer_id");
                String orderNo = (String) orderInfo.get("order_no");
                String materialCode = (String) orderInfo.get("material_code");
                BigDecimal unitPrice = (BigDecimal) orderInfo.get("unit_price");
                Date orderTime = (Date) orderInfo.get("created_at");
                
                OrderCustomerPriority priority = calculateOrderPriority(
                    orderId, orderNo, customerId, materialCode, unitPrice, orderTime
                );
                results.add(priority);
            }
        }
        
        return results;
    }
    
    @Override
    public List<Long> sortOrdersByPriority(List<Long> orderIds) {
        if (orderIds == null || orderIds.isEmpty()) {
            return new ArrayList<>();
        }
        
        // 查询订单优先级记录
        List<OrderCustomerPriority> priorities = priorityMapper.selectByOrderIdsOrdered(orderIds);
        
        // 提取排序后的订单ID
        return priorities.stream()
            .map(OrderCustomerPriority::getOrderId)
            .collect(Collectors.toList());
    }
    
    @Override
    @Transactional
    public void updateCustomerTransactionStats() {
        // 从sales_order表统计近3个月的客户交易数据
        // 这里需要根据实际的sales_order表结构来实现
        // 暂时留空，后续实现
        System.out.println("更新客户交易统计数据...");
    }
    
    @Override
    @Transactional
    public void updateCustomerMaterialPriceStats() {
        // 从sales_order_item表统计近3个月的客户料号单价数据
        // 这里需要根据实际的表结构来实现
        // 暂时留空，后续实现
        System.out.println("更新客户料号单价统计...");
    }
    
    // ========== 私有方法：得分计算 ==========
    
    /**
     * 计算账期得分
     * 公式：max[10 - 1×(账期月数-3), 0]
     */
    private BigDecimal calculatePaymentTermsScore(Integer paymentTerms) {
        if (paymentTerms == null) {
            paymentTerms = 30; // 默认30天=1个月
        }
        
        // 转换为月数
        int months = (int) Math.ceil(paymentTerms / 30.0);
        
        // 计算得分
        int score = Math.max(10 - 1 * (months - 3), 0);
        
        // 特殊处理：1个月=12分，2个月=11分，3个月=10分
        if (months == 1) return new BigDecimal("12");
        if (months == 2) return new BigDecimal("11");
        
        return new BigDecimal(score);
    }
    
    /**
     * 计算月均成交金额得分
     * 公式：近3个月总成交金额 ÷ 30
     */
    private BigDecimal calculateAvgAmountScore(BigDecimal avgMonthlyAmount) {
        if (avgMonthlyAmount == null || avgMonthlyAmount.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        
        // 月均金额除以30
        return avgMonthlyAmount.divide(new BigDecimal("30"), 2, RoundingMode.HALF_UP);
    }
    
    /**
     * 计算单价得分
     * 根据单价偏差率分档
     */
    private BigDecimal calculateUnitPriceScore(BigDecimal unitPrice, CustomerMaterialPriceStats priceStats) {
        if (unitPrice == null || unitPrice.compareTo(BigDecimal.ZERO) == 0) {
            return new BigDecimal("3"); // 默认3分
        }
        
        if (priceStats == null || priceStats.getAvgUnitPrice() == null || 
            priceStats.getAvgUnitPrice().compareTo(BigDecimal.ZERO) == 0) {
            return new BigDecimal("5"); // 没有历史数据，给5分
        }
        
        BigDecimal avgPrice = priceStats.getAvgUnitPrice();
        
        // 计算偏差率 = (订单单价 - 平均单价) / 平均单价 * 100%
        BigDecimal deviation = unitPrice.subtract(avgPrice)
            .divide(avgPrice, 4, RoundingMode.HALF_UP)
            .multiply(new BigDecimal("100"));
        
        // 根据偏差率分档
        if (deviation.compareTo(new BigDecimal("20")) >= 0) {
            return new BigDecimal("10");
        } else if (deviation.compareTo(new BigDecimal("10")) >= 0) {
            return new BigDecimal("8");
        } else if (deviation.compareTo(BigDecimal.ZERO) >= 0) {
            return new BigDecimal("5");
        } else if (deviation.compareTo(new BigDecimal("-10")) >= 0) {
            return new BigDecimal("3");
        } else {
            return BigDecimal.ZERO;
        }
    }
    
    /**
     * 根据总分确定优先级级别
     */
    private String determinePriorityLevel(BigDecimal totalScore) {
        if (totalScore.compareTo(new BigDecimal("20")) >= 0) {
            return "HIGH";
        } else if (totalScore.compareTo(new BigDecimal("15")) >= 0) {
            return "MEDIUM";
        } else if (totalScore.compareTo(new BigDecimal("10")) >= 0) {
            return "NORMAL";
        } else {
            return "LOW";
        }
    }
    
    /**
     * 获取或创建客户交易统计
     */
    private CustomerTransactionStats getOrCreateTransactionStats(Long customerId) {
        QueryWrapper<CustomerTransactionStats> wrapper = new QueryWrapper<>();
        wrapper.eq("customer_id", customerId);
        wrapper.orderByDesc("stats_date");
        wrapper.last("LIMIT 1");
        
        CustomerTransactionStats stats = transactionStatsMapper.selectOne(wrapper);
        
        if (stats == null) {
            // 创建默认统计记录
            stats = new CustomerTransactionStats();
            stats.setCustomerId(customerId);
            stats.setCustomerName("客户" + customerId);
            stats.setPaymentTerms(30); // 默认30天
            stats.setLast3mAmount(BigDecimal.ZERO);
            stats.setLast3mOrderCount(0);
            stats.setAvgMonthlyAmount(BigDecimal.ZERO);
            stats.setStatsDate(new Date());
            transactionStatsMapper.insert(stats);
        }
        
        return stats;
    }
    
    /**
     * 获取客户料号单价统计
     */
    private CustomerMaterialPriceStats getPriceStats(Long customerId, String materialCode) {
        QueryWrapper<CustomerMaterialPriceStats> wrapper = new QueryWrapper<>();
        wrapper.eq("customer_id", customerId);
        wrapper.eq("material_code", materialCode);
        wrapper.orderByDesc("stats_date");
        wrapper.last("LIMIT 1");
        
        return priceStatsMapper.selectOne(wrapper);
    }

    @Override
    @Transactional
    public void calculatePriorities(List<Long> orderIds) {
        batchCalculateOrderPriority(orderIds);
    }

    @Override
    public IPage<OrderCustomerPriority> getCustomerPriorityPage(Map<String, Object> params) {
        Integer pageNum = (Integer) params.getOrDefault("pageNum", 1);
        Integer pageSize = (Integer) params.getOrDefault("pageSize", 20);
        String orderNo = (String) params.get("orderNo");
        String customerName = (String) params.get("customerName");
        String priorityRange = (String) params.get("priorityRange");

        QueryWrapper<OrderCustomerPriority> wrapper = new QueryWrapper<>();
        if (orderNo != null && !orderNo.isEmpty()) {
            wrapper.like("order_no", orderNo);
        }
        if (customerName != null && !customerName.isEmpty()) {
            wrapper.like("customer_name", customerName);
        }
        if (priorityRange != null && !priorityRange.isEmpty()) {
            if ("high".equals(priorityRange)) {
                wrapper.ge("total_score", 25);
            } else if ("medium".equals(priorityRange)) {
                wrapper.ge("total_score", 15).lt("total_score", 25);
            } else if ("low".equals(priorityRange)) {
                wrapper.lt("total_score", 15);
            }
        }
        wrapper.orderByDesc("total_score", "order_time");

        IPage<OrderCustomerPriority> page = new Page<>(pageNum, pageSize);
        return priorityMapper.selectPage(page, wrapper);
    }

    @Override
    public OrderCustomerPriority getById(Long orderId) {
        QueryWrapper<OrderCustomerPriority> wrapper = new QueryWrapper<>();
        wrapper.eq("order_id", orderId);
        return priorityMapper.selectOne(wrapper);
    }

    @Override
    @Transactional
    public void recalculateAllPriorities() {
        // 查询所有待排程订单并重新计算优先级
        List<OrderCustomerPriority> allPriorities = priorityMapper.selectList(new QueryWrapper<>());
        for (OrderCustomerPriority priority : allPriorities) {
            // 重新计算该订单的优先级
            calculateOrderPriority(
                    priority.getOrderId(),
                    priority.getOrderNo(),
                    priority.getCustomerId(),
                    null,
                    null,
                    priority.getOrderTime()
            );
        }
    }

    @Override
    public Map<String, Object> getCustomerTransactionStats(Long customerId) {
        QueryWrapper<CustomerTransactionStats> wrapper = new QueryWrapper<>();
        wrapper.eq("customer_id", customerId);
        wrapper.orderByDesc("stats_date");
        wrapper.last("LIMIT 1");

        CustomerTransactionStats stats = transactionStatsMapper.selectOne(wrapper);
        if (stats == null) {
            return new HashMap<>();
        }

        Map<String, Object> result = new HashMap<>();
        result.put("customerId", stats.getCustomerId());
        result.put("customerName", stats.getCustomerName());
        result.put("paymentTerms", stats.getPaymentTerms());
        result.put("totalAmount3Months", stats.getLast3mAmount());
        result.put("avgMonthlyAmount", stats.getAvgMonthlyAmount());
        result.put("statsDate", stats.getStatsDate());
        return result;
    }

    @Override
    public Map<String, Object> getCustomerMaterialPriceStats(Long customerId, String materialCode) {
        QueryWrapper<CustomerMaterialPriceStats> wrapper = new QueryWrapper<>();
        wrapper.eq("customer_id", customerId);
        wrapper.eq("material_code", materialCode);
        wrapper.orderByDesc("stats_date");
        wrapper.last("LIMIT 1");

        CustomerMaterialPriceStats stats = priceStatsMapper.selectOne(wrapper);
        if (stats == null) {
            return new HashMap<>();
        }

        Map<String, Object> result = new HashMap<>();
        result.put("customerId", stats.getCustomerId());
        result.put("materialCode", stats.getMaterialCode());
        result.put("avgPrice", stats.getAvgUnitPrice());
        result.put("totalQuantity", stats.getLast3mTotalQty());
        result.put("totalAmount", stats.getLast3mTotalAmount());
        result.put("statsDate", stats.getStatsDate());
        return result;
    }
}
