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
@TableName("gl_entry")
public class GlEntry {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String voucherNo;

    private Date entryDate;

    private Long glAccountId;

    private BigDecimal debit;

    private BigDecimal credit;

    private String currency;

    private String description;

    private String sourceType;

    private Long sourceId;

    private Integer isDeleted;
}
