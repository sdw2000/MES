package com.fine.model.production;

import com.baomidou.mybatisplus.annotation.TableField;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 涂布计划表实体类
 */
@Data
public class ScheduleCoating {
    
    private Long id;
    
    /** 排程ID */
    private Long scheduleId;
    
    /** 任务单号（CT-YYYYMMDD-XXX） */
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
    
    // ========== 产品信息 ==========
    
    /** 产品料号 */
    private String materialCode;
    
    /** 产品名称 */
    private String materialName;
    
    /** 颜色代码 */
    private String colorCode;
    
    /** 颜色名称 */
    private String colorName;
    
    /** 厚度(mm) */
    private BigDecimal thickness;
    
    // ========== 生产数量 ==========
    
    /** 计划涂布长度(米) */
    private BigDecimal planLength;
    
    /** 计划面积(平方米) */
    private BigDecimal planSqm;
    
    /** 实际涂布长度(米) */
    private BigDecimal actualLength;
    
    /** 实际面积(平方米) */
    private BigDecimal actualSqm;
    
    /** 母卷宽度(mm) */
    private Integer jumboWidth;
    
    // ========== 工艺参数 ==========
    
    /** 涂布速度(米/分钟) */
    private BigDecimal coatingSpeed;
    
    /** 烘箱温度(℃) */
    private BigDecimal ovenTemp;
    
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
    
    @TableField(exist = false)
    private String updateBy;
    
    // ========== 订单信息 ==========
    
    /** 订单号 */
    private String orderNo;
    
    /** 订单ID */
    private Long orderId;
    
    /** 订单明细ID */
    private Long orderItemId;
    
    /** 基材料号 */
    @TableField(exist = false)
    private String baseMaterialCode;
    
    /** 基材厚度(μm) */
    @TableField(exist = false)
    private Integer baseThickness;
    
    /** 胶水编码 */
    @TableField(exist = false)
    private String adhesiveCode;
    
    /** 胶水固含量(%) */
    @TableField(exist = false)
    private BigDecimal adhesiveSolidContent;
    
    /** 涂胶厚度(g/m²) */
    @TableField(exist = false)
    private BigDecimal coatingThickness;
    
    /** 离型层 */
    @TableField(exist = false)
    private String releaseLayer;
    
    /** 膜宽(mm) */
    @TableField(exist = false)
    private Integer filmWidth;
    
    /** 工时(分钟) */
    @TableField(exist = false)
    private Integer workHours;
    
    /** 订单状态 */
    @TableField(exist = false)
    private String orderStatus;
    
    // ========== 基材薄膜信息 ==========
    
    /** 薄膜库存ID */
    @TableField(exist = false)
    private Long filmStockId;
    
    /** 薄膜明细ID列表（逗号分隔） */
    @TableField(exist = false)
    private String filmDetailIds;
    
    /** 薄膜批次号 */
    @TableField(exist = false)
    private String filmBatchNo;
    
    /** 薄膜厚度(μm) */
    @TableField(exist = false)
    private Integer filmThickness;
    
    /** 基材卷数 */
    @TableField(exist = false)
    private Integer baseFilmRolls;
    
    /** 基材面积(㎡) */
    @TableField(exist = false)
    private BigDecimal baseFilmArea;
    
    // ========== 非数据库字段 ==========
    
    /** 设备名称 */
    @TableField(exist = false)
    private String equipmentName;
    
    /** 未排程面积(㎡) - 该料号订单剩余未排程的面积 */
    @TableField(exist = false)
    private BigDecimal pendingSqm;}