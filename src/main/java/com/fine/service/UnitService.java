package com.fine.service;

import com.fine.model.UnitConversionResult;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 单位规范化与换算服务接口
 */
public interface UnitService {

    /**
     * 将给定数量和单位转换为规范单位（如 kg/㎡/卷/L），返回转换后的数量与单位。
     * 若无法转换则返回原始值。
     */
    UnitConversionResult toStandard(BigDecimal quantity, String unit);

    /**
     * 带上下文的单位规范化。
     * context 可选键：
     * - bucketVolumeL: BigDecimal，每桶对应多少升（用于 桶 -> L）
     * - rollAreaSqm: BigDecimal，每卷对应多少㎡（用于 卷 -> ㎡）
     */
    UnitConversionResult toStandard(BigDecimal quantity, String unit, Map<String, BigDecimal> context);

    /**
     * 将数量从一个单位转换到另一个单位（若支持）。不支持时抛出 IllegalArgumentException 或返回 null。
     */
    BigDecimal convert(BigDecimal quantity, String fromUnit, String toUnit);

    /**
     * 带上下文的单位换算。
     */
    BigDecimal convert(BigDecimal quantity, String fromUnit, String toUnit, Map<String, BigDecimal> context);
}
