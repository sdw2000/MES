package com.fine.model.production;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 分条计划表实体类（母卷直接切成小卷）
 */
@Data
public class ScheduleStripping {
    
    private Long id;
    
    /** 排程ID */
    private Long scheduleId;
    
    /** 任务单号（ST-YYYYMMDD-XXX） */
    private String taskNo;
    
    /** 设备ID */
    private Long equipmentId;
    
    /** 设备编号 */
    private String equipmentCode;
    
    /** 操作人员ID */
    private Long staffId;
    
    /** 操作人员 */
    private String staffName;
    
    /** 班次 */
    private String shiftCode;
    
    /** 计划日期 */
    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "GMT+8")
    private Date planDate;
    
    // ========== 来源母卷 ==========
    
    /** 来源母卷批次号 */
    private String sourceBatchNo;
    
    /** 来源库存ID */
    private Long sourceStockId;
    
    /** 母卷宽度(mm) */
    private Integer jumboWidth;
    
    /** 母卷长度(米) */
    private BigDecimal jumboLength;
    
    // ========== 产品信息 ==========
    
    /** 产品料号 */
    private String materialCode;
    
    /** 产品名称 */
    private String materialName;
    
    /** 厚度(mm) */
    private BigDecimal thickness;
    
    // ========== 分条规格（同时切长度和宽度） ==========
    
    /** 目标宽度(mm) */
    private Integer targetWidth;
    
    /** 目标长度(米) */
    private Integer targetLength;
    
    /** 宽度方向切几条 */
    private Integer cutsWidth;
    
    /** 长度方向切几段 */
    private Integer cutsLength;
    
    /** 计划卷数 */
    private Integer planRolls;
    
    /** 实际卷数 */
    private Integer actualRolls;
    
    // ========== 工艺参数 ==========
    
    /** 分条速度(米/分钟) */
    private BigDecimal strippingSpeed;
    
    // ========== 时间 ==========
    
    /** 计划开始时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date planStartTime;
    
    /** 计划结束时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date planEndTime;
    
    /** 计划时长(分钟) */
    private Integer planDuration;
    
    /** 实际开始时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date actualStartTime;
    
    /** 实际结束时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date actualEndTime;
    
    /** 实际时长(分钟) */
    private Integer actualDuration;
    
    // ========== 状态 ==========
    
    /** 状态：pending-待生产，in_progress-生产中，completed-已完成，cancelled-已取消 */
    private String status;
    
    /** 产出批次号 */
    private String outputBatchNo;
    
    /** 备注 */
    private String remark;
    
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createTime;
    
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date updateTime;
    
    private String createBy;
    
    private String updateBy;
    
    // ========== 非数据库字段 ==========
    
    /** 设备名称 */
    private String equipmentName;
}
