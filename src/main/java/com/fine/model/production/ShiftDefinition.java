package com.fine.model.production;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.sql.Time;

/**
 * 班次定义实体类
 */
@Data
@TableName("shift_definition")
public class ShiftDefinition implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 班次编码 */
    private String shiftCode;

    /** 班次名称 */
    private String shiftName;

    /** 开始时间 */
    private Time startTime;

    /** 结束时间 */
    private Time endTime;

    /** 有效工作时长(小时，扣除休息) */
    private BigDecimal workHours;

    /** 是否跨天：0-否，1-是 */
    private Integer crossDay;

    /** 休息时间(分钟) */
    private Integer breakMinutes;

    /** 备注 */
    private String remark;

    /** 状态：0-停用，1-启用 */
    private Integer status;
}
