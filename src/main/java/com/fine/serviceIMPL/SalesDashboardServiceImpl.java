package com.fine.serviceIMPL;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fine.Dao.CustomerMapper;
import com.fine.Dao.DeliveryNoticeMapper;
import com.fine.Dao.production.SalesOrderMapper;
import com.fine.modle.Customer;
import com.fine.modle.DeliveryNotice;
import com.fine.modle.SalesOrder;
import com.fine.service.SalesDashboardService;

@Service
public class SalesDashboardServiceImpl implements SalesDashboardService {

    @Autowired
    private SalesOrderMapper salesOrderMapper;

    @Autowired
    private CustomerMapper customerMapper;

    @Autowired
    private DeliveryNoticeMapper deliveryNoticeMapper;

    @Override
    public Map<String, Object> getSummary() {
        Map<String, Object> result = new HashMap<>();

        // 客户总数
        QueryWrapper<Customer> customerWrapper = new QueryWrapper<>();
        customerWrapper.eq("is_deleted", 0);
        long customerTotal = customerMapper.selectCount(customerWrapper);
        result.put("customerTotal", customerTotal);

        LocalDate today = LocalDate.now();
        result.put("todayAmount", sumOrderAmount(today, today));

        LocalDate monthStart = today.withDayOfMonth(1);
        LocalDate monthEnd = today.withDayOfMonth(today.lengthOfMonth());
        result.put("monthAmount", sumOrderAmount(monthStart, monthEnd));

        LocalDate yearStart = today.withDayOfYear(1);
        LocalDate yearEnd = today.withMonth(12).withDayOfMonth(31);
        result.put("yearAmount", sumOrderAmount(yearStart, yearEnd));

        return result;
    }

    @Override
    public List<Map<String, Object>> getTopCustomers() {
        QueryWrapper<SalesOrder> wrapper = new QueryWrapper<>();
        wrapper.eq("is_deleted", 0);
        wrapper.select("customer as customerName", "COALESCE(SUM(total_amount),0) as amount");
        wrapper.groupBy("customer");
        wrapper.orderByDesc("amount");
        wrapper.last("LIMIT 10");

        List<Map<String, Object>> records = salesOrderMapper.selectMaps(wrapper);
        return records != null ? records : new ArrayList<>();
    }

    @Override
    public Map<String, Object> getYearTrend() {
        LocalDate today = LocalDate.now();
        LocalDate yearStart = today.withDayOfYear(1);
        LocalDate yearEnd = today.withMonth(12).withDayOfMonth(31);

        QueryWrapper<SalesOrder> wrapper = new QueryWrapper<>();
        wrapper.eq("is_deleted", 0);
        wrapper.ge("order_date", toDate(yearStart));
        wrapper.le("order_date", toDate(yearEnd));
        wrapper.select("DATE_FORMAT(order_date, '%m') AS month", "COALESCE(SUM(total_amount),0) AS amount");
        wrapper.groupBy("DATE_FORMAT(order_date, '%m')");
        wrapper.orderByAsc("month");

        Map<String, BigDecimal> monthAmountMap = new HashMap<>();
        List<Map<String, Object>> rows = salesOrderMapper.selectMaps(wrapper);
        if (rows != null) {
            for (Map<String, Object> row : rows) {
                String monthStr = String.valueOf(row.get("month"));
                BigDecimal amount = parseBigDecimal(row.get("amount"));
                monthAmountMap.put(monthStr, amount);
            }
        }

        List<String> months = new ArrayList<>();
        List<BigDecimal> amounts = new ArrayList<>();
        for (int m = 1; m <= 12; m++) {
            String key = String.format("%02d", m);
            months.add(m + "月");
            amounts.add(monthAmountMap.getOrDefault(key, BigDecimal.ZERO));
        }

        Map<String, Object> result = new HashMap<>();
        result.put("months", months);
        result.put("amounts", amounts);
        return result;
    }

    @Override
    public Map<String, Object> getShipmentStats() {
        Map<String, Object> result = new HashMap<>();
        LocalDate today = LocalDate.now();

        // 已发货
        QueryWrapper<DeliveryNotice> shippedWrapper = new QueryWrapper<>();
        shippedWrapper.eq("is_deleted", 0);
        shippedWrapper.in("status", "已发货", "shipped");
        long shipped = deliveryNoticeMapper.selectCount(shippedWrapper);
        result.put("shipped", shipped);

        // 待发货（不包含已发货/作废）
        QueryWrapper<DeliveryNotice> pendingWrapper = new QueryWrapper<>();
        pendingWrapper.eq("is_deleted", 0);
        pendingWrapper.notIn("status", "已发货", "shipped", "已作废", "cancelled");
        long pending = deliveryNoticeMapper.selectCount(pendingWrapper);
        result.put("pending", pending);

        // 逾期客户（交货日期早于今天且未发货）
        QueryWrapper<DeliveryNotice> overdueWrapper = new QueryWrapper<>();
        overdueWrapper.eq("is_deleted", 0);
        overdueWrapper.notIn("status", "已发货", "shipped", "已作废", "cancelled");
        overdueWrapper.isNotNull("delivery_date");
        overdueWrapper.le("delivery_date", toDate(today));
        overdueWrapper.select("customer");
        overdueWrapper.groupBy("customer");
        List<Object> overdueCustomers = deliveryNoticeMapper.selectObjs(overdueWrapper);
        result.put("overdue", overdueCustomers != null ? overdueCustomers.size() : 0);

        return result;
    }

    private BigDecimal sumOrderAmount(LocalDate start, LocalDate end) {
        QueryWrapper<SalesOrder> wrapper = new QueryWrapper<>();
        wrapper.eq("is_deleted", 0);
        if (start != null) {
            wrapper.ge("order_date", toDate(start));
        }
        if (end != null) {
            wrapper.le("order_date", toDate(end));
        }
        wrapper.select("COALESCE(SUM(total_amount),0) AS totalAmount");

        List<Map<String, Object>> rows = salesOrderMapper.selectMaps(wrapper);
        if (rows != null && !rows.isEmpty()) {
            Object value = rows.get(0).get("totalAmount");
            return parseBigDecimal(value);
        }
        return BigDecimal.ZERO;
    }

    private java.util.Date toDate(LocalDate localDate) {
        return java.util.Date.from(localDate.atStartOfDay(ZoneId.systemDefault()).toInstant());
    }

    private BigDecimal parseBigDecimal(Object value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        if (value instanceof BigDecimal) {
            return (BigDecimal) value;
        }
        try {
            return new BigDecimal(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return BigDecimal.ZERO;
        }
    }
}
