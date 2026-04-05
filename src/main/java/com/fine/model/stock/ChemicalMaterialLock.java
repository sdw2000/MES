package com.fine.model.stock;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;

@Data
@TableName("chemical_material_lock")
public class ChemicalMaterialLock {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Date planDate;

    private Long scheduleId;

    private String orderNo;

    private String finishedMaterialCode;

    private String rawMaterialCode;

    private String rawMaterialName;

    private Long chemicalStockId;

    private BigDecimal requiredKg;

    private Integer requiredQty;

    private Integer lockedQty;

    /** LOCKED/PARTIAL/PENDING */
    private String lockStatus;

    private String sourceRef;

    private String remark;

    private Date createTime;

    private Date updateTime;
}
