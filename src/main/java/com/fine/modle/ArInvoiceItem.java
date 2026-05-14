package com.fine.modle;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("ar_invoice_item")
public class ArInvoiceItem {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long invoiceId;

    private String orderNo;

    private String materialCode;

    private String description;

    private BigDecimal quantity;

    private BigDecimal unitPrice;

    private BigDecimal amount;

    private Integer isDeleted;
}
