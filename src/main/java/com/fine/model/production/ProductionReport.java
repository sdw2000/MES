package com.fine.model.production;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 生产报工表实体类
 */
@Data
public class ProductionReport {
    
    private Long id;
    
    /** 报工单号 */
    private String reportNo;
    
    /** 任务类型：COATING/REWINDING/SLITTING/STRIPPING */
    private String taskType;
    
    /** 任务ID */
    private Long taskId;
    
    /** 任务单号 */
    private String taskNo;
    
    /** 设备ID */
    private Long equipmentId;
    
    /** 报工人员ID */
    private Long staffId;
    
    /** 报工人员 */
    private String staffName;
    
    /** 班次 */
    private String shiftCode;
    
    /** 报工日期 */
    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "GMT+8")
    private Date reportDate;
    
    // ========== 产出信息 ==========
    
    /** 产出数量(卷) */
    private Integer outputQty;
    
    /** 产出长度(米) */
    private BigDecimal outputLength;
    
    /** 产出面积(平方米) */
    private BigDecimal outputSqm;
    
    /** 不良数量 */
    private Integer defectQty;
    
    /** 不良原因 */
    private String defectReason;
    
    // ========== 时间 ==========
    
    /** 开始时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date startTime;
    
    /** 结束时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date endTime;
    
    /** 工作时长(分钟) */
    private Integer workMinutes;
    
    /** 暂停时长(分钟) */
    private Integer pauseMinutes;
    
    // ========== 批次 ==========
    
    /** 产出批次号 */
    private String outputBatchNo;
    
    /** 备注 */
    private String remark;
    
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createTime;
    
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date updateTime;
    
    // ========== 非数据库字段 ==========
    
    /** 设备名称 */
    private String equipmentName;
}
