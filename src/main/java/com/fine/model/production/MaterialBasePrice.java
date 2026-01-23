package com.fine.model.production;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 料号基准价格表
 * 用于单价偏差率计算
 */
@Data
@TableName("material_base_price")
public class MaterialBasePrice {
    
    @TableId(type = IdType.AUTO)
    private Long id;
    
    /** 料号 */
    private String materialCode;
    
    /** 料号名称 */
    private String materialName;
    
    /** 近3个月总成交数量 */
    private BigDecimal recent3mTotalQuantity;
    
    /** 近3个月总成交金额（元） */
    private BigDecimal recent3mTotalAmount;
    
    /** 近3个月平均单价（元/平方米） */
    private BigDecimal avgUnitPrice;
    
    /** 统计更新时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date statsUpdatedAt;
}
