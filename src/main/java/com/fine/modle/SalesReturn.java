package com.fine.modle;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Date;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("sales_return_orders")
public class SalesReturn {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String returnNo;

    /** 客户代码 */
    private String customer;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate returnDate;

    /** draft/confirmed/cancelled */
    private String status;

    private BigDecimal totalAmount;

    private BigDecimal totalArea;

    /** 进入对账的金额，退货固定为负数 */
    private BigDecimal statementAmount;

    /** 对账月份：yyyy-MM */
    private String statementMonth;

    private String reason;

    private String remark;

    private String createdBy;

    private String updatedBy;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date updatedAt;

    @TableLogic
    private Integer isDeleted;

    @TableField(exist = false)
    private List<SalesReturnItem> items;
}
