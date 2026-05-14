package com.fine.modle;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Date;

@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("bank_transaction")
public class BankTransaction {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long bankAccountId;

    private Date txnDate;

    private BigDecimal amount;

    private BigDecimal balance;

    private String txnType;

    private String description;

    private String externalRef;

    private Integer isReconciled;

    private Long reconciliationId;

    @TableLogic
    private Integer isDeleted;

    private Date createdAt;
}
