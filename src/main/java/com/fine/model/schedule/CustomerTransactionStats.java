package com.fine.model.schedule;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 客户交易统计实体
 */
@Data
@TableName("customer_transaction_stats")
public class CustomerTransactionStats implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    @TableId(type = IdType.AUTO)
    private Long id;
    
    private Long customerId;
    private String customerName;
    private Integer paymentTerms;           // 账期（月数）
    private BigDecimal last3mAmount;        // 近3个月成交总金额
    private Integer last3mOrderCount;       // 近3个月订单数量
    private BigDecimal avgMonthlyAmount;    // 月均成交金额
    private Date statsDate;                 // 统计日期
    private Date createdAt;
    private Date updatedAt;
}
