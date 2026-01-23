package com.fine.model.production;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;

/**
 * 紧急插单记录实体类
 */
@Data
@TableName("urgent_insert_order")
public class UrgentOrderLog {
    
    @TableId(type = IdType.AUTO)
    private Long id;
    
    /**
     * 紧急排程ID
     */
    private Long scheduleId;
    
    /**
     * 排程单号
     */
    private String scheduleNo;
    
    /**
     * 关联订单ID
     */
    private Long orderId;
    
    /**
     * 订单号
     */
    private String orderNo;
    
    /**
     * 客户
     */
    private String customer;
    
    /**
     * 紧急程度：normal-一般，high-紧急，critical-特急
     */
    private String urgentLevel;
    
    /**
     * 紧急原因
     */
    private String urgentReason;
    
    /**
     * 原交货日期
     */
    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "GMT+8")
    private Date originalDeliveryDate;
    
    /**
     * 新交货日期
     */
    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "GMT+8")
    private Date newDeliveryDate;
    
    /**
     * 受影响的排程ID列表（JSON）
     */
    private String affectedSchedules;
    
    /**
     * 受影响的订单ID列表（JSON）
     */
    private String affectedOrders;
    
    /**
     * 调整说明
     */
    private String adjustmentDesc;
    
    /**
     * 申请人ID
     */
    private Long applicantId;
    
    /**
     * 申请人
     */
    private String applicantName;
    
    /**
     * 申请时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date applyTime;
    
    /**
     * 审批人ID
     */
    private Long approverId;
    
    /**
     * 审批人
     */
    private String approverName;
    
    /**
     * 审批时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date approveTime;
    
    /**
     * 状态：pending-待审批，approved-已批准，rejected-已驳回，executed-已执行
     */
    private String status;
    
    /**
     * 备注
     */
    private String remark;
    
    /**
     * 创建时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createTime;
    
    /**
     * 更新时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date updateTime;
}
