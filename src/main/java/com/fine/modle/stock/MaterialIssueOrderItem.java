package com.fine.modle.stock;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 领料单明细
 */
@Data
@TableName("material_issue_order_item")
public class MaterialIssueOrderItem {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long issueOrderId;

    private Long lockId;

    private Long scheduleId;

    private Long orderId;

    private String orderNo;

    private String materialCode;

    private Long filmStockId;

    private BigDecimal issuedArea;

    private String lockStatusBefore;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createdAt;

    @TableLogic
    private Integer isDeleted;
}
