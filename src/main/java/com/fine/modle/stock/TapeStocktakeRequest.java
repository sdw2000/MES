package com.fine.modle.stock;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 库存盘点请求
 */
@Data
public class TapeStocktakeRequest {

    /** 盘点后卷数（必填） */
    private Integer actualRolls;

    /** 盘点后总平米（可选，不传则按比例估算） */
    private BigDecimal actualSqm;

    /** 盘点人（可选） */
    private String operator;

    /** 盘点原因/备注（可选） */
    private String reason;
}
