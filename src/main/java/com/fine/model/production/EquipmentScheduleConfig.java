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
 * 设备排程状态配置
 */
@Data
@TableName("equipment_schedule_config")
public class EquipmentScheduleConfig implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 设备ID */
    private Long equipmentId;

    /** 设备编码 */
    private String equipmentCode;

    /** 默认排程起点 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime initialScheduleTime;

    /** 当前周期结束时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime cycleEndTime;

    /** 周末后重新开排时间，格式 HH:mm:ss */
    private String nextWeekStartTime;

    /** 周末休息：1-是，0-否 */
    private Integer weekendRest;

    /** 周日不可排：1-是，0-否 */
    private Integer sundayDisabled;

    /** 是否启用：1-启用，0-停用 */
    private Integer enabled;

    /** 默认最低在岗人数 */
    private Integer minStaffRequired;

    /** 默认技能要求 normal/skilled/expert */
    private String requiredSkillLevel;

    /** 备注 */
    private String remark;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date updateTime;

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
}
