package com.fine.modle.production;

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
 * 批次排程实体类
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("batch_schedules")
public class BatchSchedule {
    
    @TableId(type = IdType.AUTO)
    private Long id;
    
    // 排程号
    private String scheduleNo;
    
    // 排程名称
    private String scheduleName;
    
    // 排程状态（draft-草稿，scheduled-已排程，inProgress-进行中，completed-已完成，cancelled-已取消）
    private String status;
    
    // 计划开始时间
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date plannedStartTime;
    
    // 计划结束时间
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date plannedEndTime;
    
    // 实际开始时间
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date actualStartTime;
    
    // 实际结束时间
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date actualEndTime;
    
    // 生产产线ID
    private Long productionLineId;
    
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
}
