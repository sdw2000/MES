package com.fine.modle;

import java.util.Date;
import java.util.List;
import java.time.LocalDate;

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
 * 发货通知单实体类
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("delivery_notices")
public class DeliveryNotice {
    
    @TableId(type = IdType.AUTO)
    private Long id;
    
    // 发货单号
    private String noticeNo;
    
    // 关联销售订单ID
    private Long orderId;
    
    // 关联销售订单号
    private String orderNo;
    
    // 客户名称
    private String customer;
    
    // 发货日期
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate deliveryDate;
    
    // 收货地址
    private String deliveryAddress;
    
    // 联系人
    private String contactPerson;
    
    // 联系电话
    private String contactPhone;
    
    // 客户订单号
    private String customerOrderNo;

    // 承运公司
    private String carrierName;

    // 物流单号
    @TableField("carrier_no")
    private String carrierNo;

    // 运输公司电话
    private String carrierPhone;

    // 状态：draft-草稿, shipped-已发货, cancelled-已作废
    private String status;
    
    // 备注（数据库字段名为 remarks）
    @TableField("remarks")
    private String remark;
    
    // 创建人
    private String createdBy;
    
    // 创建时间
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createdAt;
    
    // 更新人
    private String updatedBy;
    
    // 更新时间
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date updatedAt;
    
    // 逻辑删除标记
    @TableLogic
    private Integer isDeleted;
    
    // 发货明细（不映射到数据库）
    @TableField(exist = false)
    private List<DeliveryNoticeItem> items;
}
