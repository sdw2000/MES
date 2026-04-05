package com.fine.modle;

import java.math.BigDecimal;
import java.util.Date;
import java.time.LocalDate;
import java.util.List;

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
 * 销售订单实体类
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("sales_orders")
public class SalesOrder {
    
    @TableId(type = IdType.AUTO)
    private Long id;
    
    // 订单号（系统生成）
    private String orderNo;
    
    // 客户名称
    private String customer;

    @TableField(exist = false)
    private Long customerId;

    @TableField(exist = false)
    private String customerCode;

    @TableField(exist = false)
    private String customerName;

    @TableField(exist = false)
    private String shortName;

    @TableField(exist = false)
    private String customerDisplay;
    
    // 客户订单号
    private String customerOrderNo;
    
    // 销售（从客户表关联，存储用户ID）
    @TableField("sales")
    private Long salesUserId;
    
    // 跟单员（从客户表关联，存储用户ID）
    @TableField("documentation_person")
    private Long documentationPersonUserId;
    
    // 前端展示字段
    @TableField(exist = false)
    private String salesUserName;
    @TableField(exist = false)
    private String documentationPersonUserName;
    
    // 总金额
    private BigDecimal totalAmount;
      // 总面积（平方米）
    private BigDecimal totalArea;
    
    // 所需面积（平方米）- 用于物料锁定（数据库已移除字段，保留为业务计算字段）
    @TableField(exist = false)
    private BigDecimal requiredArea;

    // 下单日期
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate orderDate;
    
    // 交货日期
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate deliveryDate;
    
    // 送货地址
    private String deliveryAddress;
    
    // 订单状态（pending-待处理，processing-处理中，completed-已完成，cancelled-已取消）
    private String status;
    
    // 备注
    private String remark;

    // 取消原因（仅请求参数，不入库；实际落备注）
    @TableField(exist = false)
    private String cancelReason;
    
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
    
    // 订单明细（不映射到数据库）
    @TableField(exist = false)
    private List<SalesOrderItem> items;

    // 未发货卷数（不映射到数据库）
    @TableField(exist = false)
    private Integer remainingRolls;

    // 总卷数（不映射到数据库）
    @TableField(exist = false)
    private Integer totalRolls;

    // 已发货卷数（不映射到数据库）
    @TableField(exist = false)
    private Integer shippedRolls;
}
