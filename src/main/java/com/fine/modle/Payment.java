package com.fine.modle;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Date;

@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("payment")
public class Payment {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String paymentNo;

    private Date paymentDate;

    private String payer;

    private String payee;

    private BigDecimal amount;

    private String currency;

    private String method;

    private Long bankAccountId;

    private Long relatedInvoiceId;

    private Long relatedBillId;

    private String reference;

    private String status;

    private Integer isDeleted;
}
