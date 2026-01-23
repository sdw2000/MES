package com.fine.model.schedule;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 订单客户优先级实体
 */
@Data
@TableName("order_customer_priority")
public class OrderCustomerPriority implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    @TableId(type = IdType.AUTO)
    private Long id;
    
    private Long orderId;
    private String orderNo;
    private Long customerId;
    private String customerName;
    
    // 三项得分
    private BigDecimal paymentTermsScore;  // 账期得分
    private BigDecimal avgAmountScore;     // 月均成交金额得分
    private BigDecimal unitPriceScore;     // 单价得分
    private BigDecimal totalScore;         // 总得分
    
    private String priorityLevel;  // 优先级级别：HIGH/MEDIUM/NORMAL/LOW
    private Date orderTime;        // 下单时间
    private Date calculatedAt;     // 计算时间
}
