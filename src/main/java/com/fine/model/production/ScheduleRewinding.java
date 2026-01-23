package com.fine.model.production;

import com.baomidou.mybatisplus.annotation.TableField;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

/**
 * 复卷计划表实体类
 */
@Data
public class ScheduleRewinding {
    
    private Long id;
    
    /** 排程ID */
    private Long scheduleId;
    
    /** 任务单号（RW-YYYYMMDD-XXX） */
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
    
    // ========== 复卷规格 ==========
    
    /** 支料长度(米/卷) */
    private Integer slitLength;
    
    /** 计划卷数 */
    private Integer planRolls;
    
    /** 实际卷数 */
    private Integer actualRolls;
    
    // ========== 工艺参数 ==========
    
    /** 复卷速度(米/分钟) */
    private BigDecimal rewindingSpeed;
    
    /** 张力设定 */
    private BigDecimal tension;
    
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
    
    /** 设备名称（非持久化，来自联表） */
    @TableField(exist = false)
    private String equipmentName;

    /** 待涂布池记录ID，用于提交后移除列表 */
    @TableField(exist = false)
    private Long pendingPoolId;

    /** 待涂布池记录ID列表，支持批量移除 */
    @TableField(exist = false)
    private List<Long> pendingPoolIds;

    /** 关联订单号列表（前端展示用，不入库） */
    @TableField(exist = false)
    private List<String> orderNos;

    /** 关联订单号（持久化，逗号分隔） */
    @TableField("order_nos")
    private String orderNosText;

    /** 前端提交的长度(米)映射到 slitLength，用于兼容旧字段 */
    @TableField(exist = false)
    private Integer length;

    /** 前端提交的数量(卷数)映射到 planRolls，用于兼容旧字段 */
    @TableField(exist = false)
    private Integer quantity;

    /** 前端提交的所需卷数（含损耗）优先映射到 planRolls */
    @TableField(exist = false)
    private Integer requiredRolls;

    /** 前端提交的宽度(mm)映射到 jumboWidth，用于兼容旧字段 */
    @TableField(exist = false)
    private Integer width;
}
