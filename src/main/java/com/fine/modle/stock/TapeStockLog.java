package com.fine.modle.stock;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 库存流水实体
 */
@Data
@TableName("tape_stock_log")
public class TapeStockLog {
    
    @TableId(type = IdType.AUTO)
    private Long id;
    
    /** 关联库存ID */
    private Long stockId;
    
    /** 生产批次号 */
    private String batchNo;
    
    /** 料号 */
    private String materialCode;
    
    /** 产品名称 */
    private String productName;
    
    /** 类型：IN入库/OUT出库/ADJUST调整 */
    private String type;
    
    /** 变动卷数（入库正数，出库负数） */
    private Double changeRolls;
    
    /** 变动前卷数 */
    private Double beforeRolls;
    
    /** 变动后卷数 */
    private Double afterRolls;
    
    /** 关联单号 */
    private String refNo;
    
    /** 操作人 */
    private String operator;
    
    /** 备注 */
    private String remark;

    /** 变动面积(m²) */
    private java.math.BigDecimal changeArea;

    /** 损耗面积(m²) */
    private java.math.BigDecimal lossArea;

    /** 损耗原因 */
    private String lossReason;

    /** 发生车间工段 */
    private String workshopSection;

    /** 班次/班组 (如: A班, 甲组) */
    private String shiftCode;

    /** 变动前规格 (如: 1000mm*500m) */
    private String beforeSpec;

    /** 变动后规格 (如: 1000mm*480m) */
    private String afterSpec;

    /** 订单号（展示字段，不落库） */
    @TableField(exist = false)
    private String orderNo;

    /** 规格（展示字段，不落库） */
    @TableField(exist = false)
    private String specDesc;
    
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    
    // 类型常量
    public static final String TYPE_IN = "IN";       // 入库
    public static final String TYPE_OUT = "OUT";     // 出库
    public static final String TYPE_ADJUST = "ADJUST"; // 调整
    public static final String TYPE_TRANSFER = "TRANSFER"; // 班组交接
    public static final String TYPE_MOVE = "MOVE";     // 移库入线边
}
