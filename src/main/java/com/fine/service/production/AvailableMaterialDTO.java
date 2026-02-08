package com.fine.service.production;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 可用物料DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AvailableMaterialDTO {
    /** 胶带库存ID */
    private Long tapeStockId;

    /** 二维码 */
    private String qrCode;

    /** 批次号 */
    private String batchNo;

    /** 物料代码 */
    private String materialCode;

    /** 规格描述 */
    private String specDesc;

    /** 总卷数 */
    private Integer totalRolls;

    /** 已被锁定的卷数 */
    private Integer lockedRolls;

    /** 可锁定的卷数 */
    private Integer availableRolls;

    /** 总面积 */
    private BigDecimal totalArea;

    /** 可用面积 */
    private BigDecimal availableArea;

    /** FIFO排序号 */
    private Integer fifoOrder;

    /** 生产日期 */
    private String prodDate;

    /** 库存来源表名（tape_stock_rolls / tape_stock） */
    private String stockTableName;
}
