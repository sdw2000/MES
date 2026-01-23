package com.fine.model.stock;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 薄膜库存明细表
 */
@Data
@TableName("film_stock_detail")
public class FilmStockDetail {
    /** 主键ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联 film_stock 的ID */
    @TableField("stock_id")
    private Long filmStockId;

    /** 物料编码 */
    private String materialCode;

    /** 批次号 */
    private String batchNo;

    /** 卷号 */
    @TableField("roll_no")
    private String rollNo;

    /** 薄膜厚度(μm) */
    private BigDecimal thickness;

    /** 薄膜宽度(mm) */
    private Integer width;

    /** 薄膜长度(mm) */
    private Integer length;

    /** 卷径(mm) */
    private Integer diameter;

    /** 纸管内径(mm) */
    @TableField("core_diameter")
    private Integer coreDiameter;

    /** 单卷重量(kg) */
    private BigDecimal weight;

    /** 该卷面积(㎡) */
    private BigDecimal area;

    /** 质量等级 */
    @TableField("quality_level")
    private String qualityLevel;

    /** 质检状态：qualified/failed/pending */
    @TableField("quality_status")
    private String qcStatus;

    /** 仓库 */
    private String warehouse;

    /** 库位 */
    private String location;

    /** 供应商 */
    private String supplier;

    /** 采购单号 */
    @TableField("purchase_order_no")
    private String purchaseOrderNo;

    /** 到货日期 */
    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "GMT+8")
    @TableField("arrival_date")
    private Date arrivalDate;

    /** 入库日期 */
    @TableField("storage_date")
    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "GMT+8")
    private Date inboundDate;

    /** 单价 */
    @TableField("unit_price")
    private BigDecimal unitPrice;

    /** 状态：available-可用，locked-锁定，used-已使用 */
    private String status;

    /** 锁定人 */
    @TableField("locked_by")
    private String lockedBy;

    /** 锁定时间 */
    @TableField("locked_time")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date lockedTime;

    /** 备注 */
    private String remark;

    /** 创建人 */
    @TableField("create_by")
    private String createBy;

    /** 创建时间 */
    @TableField("create_time")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createTime;

    /** 更新人 */
    @TableField("update_by")
    private String updateBy;

    /** 更新时间 */
    @TableField("update_time")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date updateTime;

    /** 删除标记 */
    @TableField("is_deleted")
    private Integer isDeleted;
}
