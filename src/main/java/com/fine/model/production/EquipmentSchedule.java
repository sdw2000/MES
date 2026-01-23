package com.fine.model.production;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;

/**
 * 设备日历占用表
 * 记录设备时间段占用情况
 */
@Data
@TableName("equipment_schedule")
public class EquipmentSchedule {
    
    @TableId(type = IdType.AUTO)
    private Long id;
    
    /** 设备ID */
    private Long equipmentId;
    
    /** 设备编号 */
    private String equipmentCode;
    
    /** 设备类型：coating-涂布，rewinding-复卷，slitting-分切 */
    private String equipmentCategory;
    
    /** 关联任务ID（涂布/复卷/分切任务ID） */
    private Long taskId;
    
    /** 任务类型：coating-涂布，rewinding-复卷，slitting-分切 */
    private String taskType;
    
    /** 生产料号 */
    private String materialCode;
    
    /** 关联订单ID */
    private Long orderId;
    
    /** 占用开始时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date startTime;
    
    /** 占用结束时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date endTime;
    
    /** 状态：scheduled-已排程，in_progress-进行中，completed-已完成，cancelled-已取消 */
    private String status;
    
    /** 创建时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createdAt;
    
    /** 更新时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date updatedAt;
}
