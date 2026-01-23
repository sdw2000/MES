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
    private Integer changeRolls;
    
    /** 变动前卷数 */
    private Integer beforeRolls;
    
    /** 变动后卷数 */
    private Integer afterRolls;
    
    /** 关联单号 */
    private String refNo;
    
    /** 操作人 */
    private String operator;
    
    /** 备注 */
    private String remark;
    
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    
    // 类型常量
    public static final String TYPE_IN = "IN";       // 入库
    public static final String TYPE_OUT = "OUT";     // 出库
    public static final String TYPE_ADJUST = "ADJUST"; // 调整
}
