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
 * 包材仓出库记录表
 */
@Data
@TableName("package_stock_out")
public class PackageStockOut {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("stock_id")
    private Long packageStockId;

    @TableField("detail_id")
    private Long packageDetailId;

    @TableField("material_code")
    private String materialCode;

    @TableField("out_no")
    private String outboundNo;

    private String batchNo;

    @TableField("out_quantity")
    private BigDecimal outQuantity;

    @TableField("out_type")
    private String purpose;

    private String operator;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @TableField("out_date")
    private Date outboundTime;

    private String remark;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @TableField("create_time")
    private Date createTime;

    @TableField("create_by")
    private String createBy;
}
