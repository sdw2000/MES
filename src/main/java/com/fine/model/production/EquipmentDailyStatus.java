package com.fine.model.production;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Date;

/**
 * 设备日状态
 */
@Data
@TableName("equipment_daily_status")
public class EquipmentDailyStatus implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long equipmentId;

    private String equipmentCode;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime planDate;

    /** OPEN/CLOSED/MAINTENANCE/FAULT */
    private String dailyStatus;

    private String reason;

    /** 每台设备最低在岗人数 */
    private Integer minStaffRequired;

    /** 可选：要求技能等级 normal/skilled/expert */
    private String requiredSkillLevel;

    private Date createTime;

    private Date updateTime;

    private String createBy;

    private String updateBy;

    @TableField(exist = false)
    private String equipmentName;

    @TableField(exist = false)
    private String equipmentType;

    @TableField(exist = false)
    private String equipmentTypeName;

    @TableField(exist = false)
    private String workshopName;

    @TableField(exist = false)
    private String equipmentStatus;

    @TableField(exist = false)
    private Integer availableStaffCount;

    @TableField(exist = false)
    private Integer canSchedule;

    @TableField(exist = false)
    private String canScheduleReason;

    @TableField(exist = false)
    private String canScheduleCode;
}
