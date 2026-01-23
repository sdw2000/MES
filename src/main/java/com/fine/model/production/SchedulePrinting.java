package com.fine.model.production;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 印刷计划实体类
 */
@Data
@TableName("schedule_printing")
public class SchedulePrinting {
    
    @TableId(type = IdType.AUTO)
    private Long id;
    
    /**
     * 排程ID
     */
    private Long scheduleId;
    
    /**
     * 任务单号（PR-YYYYMMDD-XXX）
     */
    private String taskNo;
    
    /**
     * 印刷机ID
     */
    private Long equipmentId;
    
    /**
     * 设备编号
     */
    private String equipmentCode;
    
    /**
     * 操作人员ID
     */
    private Long staffId;
    
    /**
     * 操作人员
     */
    private String staffName;
    
    /**
     * 班次
     */
    private String shiftCode;
    
    /**
     * 计划日期
     */
    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "GMT+8")
    private Date planDate;
    
    /**
     * 产品料号
     */
    private String materialCode;
    
    /**
     * 产品名称
     */
    private String materialName;
    
    /**
     * 印刷类型：logo-商标，text-文字，pattern-图案，full-满版印刷
     */
    private String printType;
    
    /**
     * 印刷颜色
     */
    private String printColor;
    
    /**
     * 印刷内容
     */
    private String printContent;
    
    /**
     * 底材宽度(mm)
     */
    private Integer baseWidth;
    
    /**
     * 底材长度(米)
     */
    private BigDecimal baseLength;
    
    /**
     * 计划印刷长度(米)
     */
    private BigDecimal planLength;
    
    /**
     * 计划面积(平方米)
     */
    private BigDecimal planSqm;
    
    /**
     * 实际印刷长度(米)
     */
    private BigDecimal actualLength;
    
    /**
     * 实际面积(平方米)
     */
    private BigDecimal actualSqm;
    
    /**
     * 印刷速度(米/分钟)
     */
    private BigDecimal printSpeed;
    
    /**
     * 干燥温度(℃)
     */
    private BigDecimal dryTemp;
    
    /**
     * 计划开始时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date planStartTime;
    
    /**
     * 计划结束时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date planEndTime;
    
    /**
     * 计划时长(分钟)
     */
    private Integer planDuration;
    
    /**
     * 实际开始时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date actualStartTime;
    
    /**
     * 实际结束时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date actualEndTime;
    
    /**
     * 实际时长(分钟)
     */
    private Integer actualDuration;
    
    /**
     * 状态：pending-待生产，in_progress-生产中，completed-已完成，cancelled-已取消
     */
    private String status;
    
    /**
     * 产出批次号
     */
    private String outputBatchNo;
    
    /**
     * 下一工序：coating-涂布
     */
    private String nextProcess;
    
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
    
    /**
     * 创建人
     */
    private String createBy;
    
    /**
     * 更新人
     */
    private String updateBy;
    
    // ========== 非数据库字段 ==========
    
    /**
     * 设备名称（关联查询）
     */
    @TableField(exist = false)
    private String equipmentName;
    
    /**
     * 排程单号（关联查询）
     */
    @TableField(exist = false)
    private String scheduleNo;
}
