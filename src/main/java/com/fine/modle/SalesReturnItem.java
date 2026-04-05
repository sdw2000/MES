package com.fine.modle;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Date;

@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("sales_return_items")
public class SalesReturnItem {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long returnId;

    /** 退货对应订单号（允许一张退货单对应多订单） */
    private String orderNo;

    /** 可选：来源订单明细ID */
    private Long sourceOrderItemId;

    private String materialCode;

    private String materialName;

    private String colorCode;

    private BigDecimal thickness;

    /** mm */
    private BigDecimal width;

    /** m */
    private BigDecimal length;

    private Integer rolls;

    private BigDecimal sqm;

    private BigDecimal unitPrice;

    private BigDecimal amount;

    private String remark;

    private String createdBy;

    private String updatedBy;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date updatedAt;

    @TableLogic
    private Integer isDeleted;
}
