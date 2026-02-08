package com.fine.modle;

import java.math.BigDecimal;
import java.util.Date;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 销售订单明细实体类
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("sales_order_items")
public class SalesOrderItem {
    
    @TableId(type = IdType.AUTO)
    private Long id;
    
    // 关联的订单ID
    private Long orderId;
    
    // 物料代码
    private String materialCode;
      // 物料名称
    private String materialName;
    
    // 颜色代码
    private String colorCode;
    
    // 厚度（mm，存储时已从μm转换）
    private BigDecimal thickness;
    
    // 宽度（mm）
    private BigDecimal width;
    
    // 长度（m）
    private BigDecimal length;
    
    // 卷数
    private Integer rolls;
    
    // 已排程数量
    private Integer scheduledQty;

    // 已排程面积（㎡）
    private BigDecimal scheduledArea;

    // 已生产面积（㎡）
    private BigDecimal producedArea;

    // 已发货面积（㎡）
    private BigDecimal deliveredArea;
    
    // 平方米数（计算得出）
    private BigDecimal sqm;
    
    // 单价（每平方米）
    private BigDecimal unitPrice;
    
    // 金额（计算得出）
    private BigDecimal amount;
    
    // 备注
    private String remark;
    
    // 创建人
    private String createdBy;
    
    // 更新人
    private String updatedBy;
    
    // 创建时间
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createdAt;
    
    // 更新时间
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date updatedAt;
    
    // 逻辑删除标记
    @TableLogic
    private Integer isDeleted;

    // 已发货卷数（不映射到数据库）
    @TableField(exist = false)
    private Integer shippedRolls;

    // 订单号（关联字段，不入库）
    @TableField(exist = false)
    private String orderNo;

    // 交期（关联字段，不入库）
    @TableField(exist = false)
    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "GMT+8")
    private Date deliveryDate;

    // 计算用的临时字段（不入库）：待排程面积（㎡）
    @TableField(exist = false)
    private BigDecimal pendingArea;
}
