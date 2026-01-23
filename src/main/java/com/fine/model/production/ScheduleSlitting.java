package com.fine.model.production;

import com.baomidou.mybatisplus.annotation.TableField;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 分切计划表实体类
 */
@Data
public class ScheduleSlitting {
    
    private Long id;
    
    /** 排程ID */
    private Long scheduleId;
    
    /** 任务单号（SL-YYYYMMDD-XXX） */
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
    
    // ========== 来源支料 ==========
    
    // ========== 订单关联 ==========
    
    /** 订单ID */
    private Long orderId;
    
    /** 订单号 */
    private String orderNo;
    
    /** 订单明细ID */
    private Long orderItemId;
    
    /** 订单详情号 */
    private String orderDetailNo;
    
    // ========== 来源支料 ==========
    
    /** 来源支料批次号 */
    private String sourceBatchNo;
    
    /** 来源库存ID */
    private Long sourceStockId;
    
    /** 支料宽度(mm) */
    private Integer slitWidth;
    
    /** 支料长度(米) */
    private Integer slitLength;
    
    // ========== 产品信息 ==========
    
    /** 产品料号 */
    private String materialCode;
    
    /** 产品名称 */
    private String materialName;
    
    /** 厚度(mm) */
    private BigDecimal thickness;
    
    /** 规格信息(综合显示) */
    private String spec;
    
    // ========== 分切规格 ==========
    
    /** 目标宽度(mm) */
    private Integer targetWidth;
    
    /** 每支料切几条 */
    private Integer cutsPerSlit;
    
    /** 计划卷数 */
    private Integer planRolls;
    
    /** 实际卷数 */
    private Integer actualRolls;
    
    /** 边料损耗(mm) */
    private Integer edgeLoss;
    
    // ========== 工艺参数 ==========
    
    /** 分切速度(米/分钟) */
    private BigDecimal slittingSpeed;
    
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
    
    /** 操作人ID */
    private Long operatorId;
    
    /** 操作人名称 */
    private String operatorName;
    
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
    @TableField(exist = false)
    private String equipmentName;
}
