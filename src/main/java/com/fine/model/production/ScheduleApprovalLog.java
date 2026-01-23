package com.fine.model.production;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;

/**
 * 排程审批记录实体类
 */
@Data
@TableName("schedule_approval_log")
public class ScheduleApprovalLog {
    
    @TableId(type = IdType.AUTO)
    private Long id;
    
    /**
     * 排程ID
     */
    private Long scheduleId;
    
    /**
     * 排程单号
     */
    private String scheduleNo;
    
    /**
     * 操作：submit-提交，approve-批准，reject-驳回，withdraw-撤回
     */
    private String action;
    
    /**
     * 原状态
     */
    private String fromStatus;
    
    /**
     * 新状态
     */
    private String toStatus;
    
    /**
     * 操作人ID
     */
    private Long operatorId;
    
    /**
     * 操作人
     */
    private String operatorName;
      /**
     * 备注/意见
     */
    private String opinion;
    
    /**
     * 操作时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createTime;
}
