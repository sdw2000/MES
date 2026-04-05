package com.fine.model.stock;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;

@Data
@TableName("chemical_purchase_request_item")
public class ChemicalPurchaseRequestItem {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long requestId;

    private Long scheduleId;

    private String orderNo;

    private String finishedMaterialCode;

    private String rawMaterialCode;

    private String rawMaterialName;

    /** 需求kg */
    private BigDecimal requiredKg;

    /** 建议请购数量（单位桶/包） */
    private Integer suggestedQty;

    /** 仓库确认请购数量（可编辑） */
    private Integer requestedQty;

    /** 累计实收数量 */
    private Integer receivedQty;

    private String unit;

    private Long purchaseOrderItemId;

    private String remark;

    private Date createTime;

    private Date updateTime;
}
