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
 * 扫码领退料事务流水
 */
@Data
@TableName("material_scan_txn")
public class MaterialScanTxn {

    public static final String TXN_TYPE_ISSUE = "ISSUE";
    public static final String TXN_TYPE_RETURN = "RETURN";

    public static final String STOCK_TYPE_FILM = "FILM";
    public static final String STOCK_TYPE_CHEMICAL = "CHEMICAL";

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("txn_no")
    private String txnNo;

    @TableField("txn_type")
    private String txnType;

    @TableField("stock_type")
    private String stockType;

    @TableField("detail_id")
    private Long detailId;

    @TableField("stock_id")
    private Long stockId;

    @TableField("qr_code")
    private String qrCode;

    @TableField("batch_no")
    private String batchNo;

    @TableField("material_code")
    private String materialCode;

    @TableField("qty")
    private BigDecimal qty;

    @TableField("pack_qty")
    private Integer packQty;

    @TableField("pack_uom")
    private String packUom;

    @TableField("std_qty")
    private BigDecimal stdQty;

    @TableField("std_uom")
    private String stdUom;

    @TableField("unit")
    private String unit;

    @TableField("before_qty")
    private BigDecimal beforeQty;

    @TableField("after_qty")
    private BigDecimal afterQty;

    @TableField("source_issue_txn_id")
    private Long sourceIssueTxnId;

    @TableField("order_no")
    private String orderNo;

    @TableField("schedule_id")
    private Long scheduleId;

    @TableField("process_type")
    private String processType;

    @TableField("operator")
    private String operator;

    @TableField("device_id")
    private String deviceId;

    @TableField("channel")
    private String channel;

    @TableField("idempotency_key")
    private String idempotencyKey;

    @TableField("remark")
    private String remark;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @TableField("create_time")
    private Date createTime;
}
