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
 * 包材仓库存明细表
 */
@Data
@TableName("package_stock_detail")
public class PackageStockDetail {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联 package_stock.id */
    @TableField("stock_id")
    private Long packageStockId;

    /** 物料编码 */
    @TableField("material_code")
    private String materialCode;

    /** 批次号 */
    private String batchNo;

    /** 箱号/管号 */
    @TableField("container_no")
    private String containerNo;

    /** 最小包装单位 */
    @TableField("pack_uom")
    private String packUom;

    /** 当前包装数量 */
    @TableField("pack_count")
    private Integer packCount;

    /** 标准单位 */
    @TableField("std_uom")
    private String stdUom;

    /** 每包装标准量 */
    @TableField("std_qty_per_pack")
    private BigDecimal stdQtyPerPack;

    /** 数量（同 std_uom） */
    private BigDecimal quantity;

    /** 仓库 */
    private String warehouse;

    /** 库位 */
    private String location;

    /** 供应商 */
    private String supplier;

    /** 入库日期 */
    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "GMT+8")
    @TableField("storage_date")
    private Date inboundDate;

    /** 状态：available/locked/used */
    private String status;

    /** 备注 */
    private String remark;

    @TableField("create_by")
    private String createBy;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @TableField("create_time")
    private Date createTime;

    @TableField("update_by")
    private String updateBy;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @TableField("update_time")
    private Date updateTime;

    @TableField("is_deleted")
    private Integer isDeleted;
}
