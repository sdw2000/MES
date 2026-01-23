package com.fine.model.customer;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 客户历史成交统计表
 * 用于客户优先级计算
 */
@Data
@TableName("customer_transaction_summary")
public class CustomerTransactionSummary {
    
    @TableId(type = IdType.AUTO)
    private Long id;
    
    /** 客户ID */
    private Long customerId;
    
    /** 客户名称 */
    private String customerName;
    
    /** 近3个月总成交金额（元） */
    private BigDecimal recent3mTotalAmount;
    
    /** 近3个月月均成交金额（元） */
    private BigDecimal recent3mAvgAmount;
    
    /** 成交次数 */
    private Integer transactionCount;
    
    /** 最后一次成交日期 */
    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "GMT+8")
    private Date lastTransactionDate;
    
    /** 是否新客户：1-是，0-否 */
    private Integer isNewCustomer;
    
    /** 统计更新时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date statsUpdatedAt;
}
