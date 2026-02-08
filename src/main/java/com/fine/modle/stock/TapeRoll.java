package com.fine.modle.stock;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 库存详情表（每卷一行）
 */
@Data
@TableName("tape_stock_rolls")
public class TapeRoll {
    @TableId
    private Long id;

    /** 上级库存批次ID */
    private Long stockId;

    /** 卷唯一编码（二维码） */
    private String qrCode;

    /** 卷长度(米) */
    private Integer length;

    /** 可用面积 */
    private BigDecimal availableArea;

    /** 预留面积 */
    private BigDecimal reservedArea;

    /** 已消耗面积 */
    private BigDecimal consumedArea;

    /** 乐观锁版本 */
    private Integer version;

    /** 生产日期 */
    private LocalDate prodDate;

    /** FIFO排序号 */
    private Integer fifoOrder;

    // 以下字段由联表补充，便于显示
    @TableField(exist = false)
    private String materialCode;

    @TableField(exist = false)
    private String batchNo;

    @TableField(exist = false)
    private String specDesc;

    @TableField(exist = false)
    private String rollType;
}
