package com.fine.model;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 单位转换结果 DTO
 */
@Data
public class UnitConversionResult {
    private BigDecimal quantity;
    private String unit;

    public UnitConversionResult() {}

    public UnitConversionResult(BigDecimal quantity, String unit) {
        this.quantity = quantity;
        this.unit = unit;
    }
}
