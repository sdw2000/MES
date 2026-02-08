package com.fine.model.schedule;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 客户料号单价统计实体
 */
@Data
@TableName("customer_material_price_stats")
public class CustomerMaterialPriceStats implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    @TableId(type = IdType.AUTO)
    private Long id;
    
    private Long customerId;
    private String materialCode;
    private String materialName;
    @com.baomidou.mybatisplus.annotation.TableField("last_3m_total_qty")
    private Integer last3mTotalQty;         // 近3个月总成交数量

    @com.baomidou.mybatisplus.annotation.TableField("last_3m_total_amount")
    private BigDecimal last3mTotalAmount;   // 近3个月总成交金额
    private BigDecimal avgUnitPrice;        // 平均单价
    private Date statsDate;                 // 统计日期
    private Date createdAt;
    private Date updatedAt;
}
