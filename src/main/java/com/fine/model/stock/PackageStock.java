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
 * 包材仓库存总量表
 */
@Data
@TableName("package_stock")
public class PackageStock {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 物料编号 */
    private String materialCode;

    /** 物料名称 */
    private String materialName;

    /** 规格 */
    private String specDesc;

    /** 单位（PCS/个/支/箱） */
    private String unit;

    /** 总数量 */
    private Double totalQuantity;

    /** 可用数量 */
    private Double availableQuantity;

    /** 锁定数量 */
    private Double lockedQuantity;

    /** 总包装数 */
    @TableField("total_pack_count")
    private Double totalPackCount;

    /** 可用包装数 */
    @TableField("available_pack_count")
    private Double availablePackCount;

    /** 锁定包装数 */
    @TableField("locked_pack_count")
    private Double lockedPackCount;

    /** 安全库存 */
    private Double safetyStock;

    /** 状态：active/low_stock/out_of_stock */
    private String status;

    /** 参考单件重量（kg，可空） */
    private BigDecimal unitWeight;

    /** 备注 */
    private String remark;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date updateTime;

    private String createBy;

    private String updateBy;
}
