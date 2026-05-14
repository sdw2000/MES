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
@TableName("bank_account")
public class BankAccount {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String accountNo;

    private String bankName;

    private String currency;

    private BigDecimal openingBalance;

    private BigDecimal currentBalance;

    @TableLogic
    private Integer isDeleted;

    private Date createdAt;
}
