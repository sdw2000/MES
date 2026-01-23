package com.fine.model.customer;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 客户料号成交明细统计表
 * 用于单价得分计算
 */
@Data
@TableName("customer_material_stats")
public class CustomerMaterialStats {
    
    @TableId(type = IdType.AUTO)
    private Long id;
    
    /** 客户ID */
    private Long customerId;
    
    /** 料号 */
    private String materialCode;
    
    /** 近3个月成交数量 */
    private BigDecimal recent3mQuantity;
    
    /** 近3个月成交金额（元） */
    private BigDecimal recent3mAmount;
    
    /** 近3个月平均单价（元/平方米） */
    private BigDecimal avgUnitPrice;
    
    /** 最后一次成交日期 */
    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "GMT+8")
    private Date lastTransactionDate;
    
    /** 统计更新时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date statsUpdatedAt;
}
