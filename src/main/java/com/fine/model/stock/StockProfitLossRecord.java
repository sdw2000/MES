package com.fine.model.stock;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;

@Data
@TableName("stock_profit_loss_record")
public class StockProfitLossRecord {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long stocktakeRecordId;
    private String warehouseType;
    private Long stockId;
    private Long detailId;
    private String materialCode;
    private String materialName;
    private String specDesc;
    private String batchNo;
    private String containerNo;
    private String location;
    private String unit;
    private BigDecimal beforeQuantity;
    private BigDecimal afterQuantity;
    private BigDecimal diffQuantity;
    private String profitLossType;
    private String operator;
    private String reason;

    @TableField("create_time")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createTime;
}