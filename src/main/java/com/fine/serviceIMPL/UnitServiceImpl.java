package com.fine.serviceIMPL;

import com.fine.model.UnitConversionResult;
import com.fine.service.UnitService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 简单的单位规范化/换算实现，覆盖常见的体积/重量/面积/计数单位。
 * 注意：某些跨量纲转换（如 卷 -> 平米）需要额外的物料规格信息（宽度/长度），此实现不做此类转换。
 */
@Service
public class UnitServiceImpl implements UnitService {

    private static final int SCALE = 6;

    private static class UnitMeta {
        private final String dimension;
        private final String standardUnit;
        private final BigDecimal factorToStandard;

        private UnitMeta(String dimension, String standardUnit, BigDecimal factorToStandard) {
            this.dimension = dimension;
            this.standardUnit = standardUnit;
            this.factorToStandard = factorToStandard;
        }
    }

    private static final String DIM_WEIGHT = "WEIGHT";
    private static final String DIM_VOLUME = "VOLUME";
    private static final String DIM_AREA = "AREA";
    private static final String DIM_COUNT = "COUNT";

    private static final Map<String, UnitMeta> UNIT_META;

    static {
        Map<String, UnitMeta> map = new HashMap<>();

        // 重量（标准单位：kg）
        register(map, new String[]{"kg", "kilogram", "kilograms", "千克"}, DIM_WEIGHT, "kg", BigDecimal.ONE);
        register(map, new String[]{"g", "gram", "grams", "克"}, DIM_WEIGHT, "kg", new BigDecimal("0.001"));
        register(map, new String[]{"t", "ton", "tons", "吨"}, DIM_WEIGHT, "kg", new BigDecimal("1000"));

        // 体积（标准单位：L）
        register(map, new String[]{"l", "liter", "litre", "liters", "升"}, DIM_VOLUME, "L", BigDecimal.ONE);
        register(map, new String[]{"ml", "milliliter", "milliliters", "毫升"}, DIM_VOLUME, "L", new BigDecimal("0.001"));
        register(map, new String[]{"m3", "m^3", "立方米"}, DIM_VOLUME, "L", new BigDecimal("1000"));

        // 面积（标准单位：㎡）
        register(map, new String[]{"㎡", "m2", "sqm", "m²", "squaremeter"}, DIM_AREA, "㎡", BigDecimal.ONE);

        // 计数（标准单位：卷）
        register(map, new String[]{"卷", "roll", "rolls"}, DIM_COUNT, "卷", BigDecimal.ONE);

        // 桶默认保留为“桶”（无上下文不强转到 L）
        register(map, new String[]{"桶", "barrel", "drum"}, DIM_COUNT, "桶", BigDecimal.ONE);

        UNIT_META = Collections.unmodifiableMap(map);
    }

    private static void register(Map<String, UnitMeta> map, String[] aliases, String dim, String std, BigDecimal factor) {
        UnitMeta meta = new UnitMeta(dim, std, factor);
        for (String alias : aliases) {
            map.put(normalize(alias), meta);
        }
    }

    private static String normalize(String unit) {
        return unit == null ? null : unit.trim().toLowerCase(Locale.ROOT);
    }

    private static BigDecimal getContextValue(Map<String, BigDecimal> context, String key) {
        if (context == null) {
            return null;
        }
        BigDecimal v = context.get(key);
        if (v == null || v.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        return v;
    }

    @Override
    public UnitConversionResult toStandard(BigDecimal quantity, String unit) {
        return toStandard(quantity, unit, null);
    }

    @Override
    public UnitConversionResult toStandard(BigDecimal quantity, String unit, Map<String, BigDecimal> context) {
        if (quantity == null) {
            return new UnitConversionResult(BigDecimal.ZERO, unit);
        }
        if (unit == null) {
            return new UnitConversionResult(quantity, null);
        }

        String u = normalize(unit);
        UnitMeta meta = UNIT_META.get(u);
        if (meta == null) {
            return new UnitConversionResult(quantity, unit);
        }

        // 桶：若提供 bucketVolumeL 上下文，则转 L
        if ("桶".equals(meta.standardUnit)) {
            BigDecimal bucketVolumeL = getContextValue(context, "bucketVolumeL");
            if (bucketVolumeL != null) {
                return new UnitConversionResult(quantity.multiply(bucketVolumeL).setScale(SCALE, RoundingMode.HALF_UP), "L");
            }
            return new UnitConversionResult(quantity.setScale(SCALE, RoundingMode.HALF_UP), "桶");
        }

        // 卷：若提供 rollAreaSqm 上下文，则可转 ㎡（用于跨类型统计）
        if ("卷".equals(meta.standardUnit)) {
            BigDecimal rollAreaSqm = getContextValue(context, "rollAreaSqm");
            if (rollAreaSqm != null) {
                return new UnitConversionResult(quantity.multiply(rollAreaSqm).setScale(SCALE, RoundingMode.HALF_UP), "㎡");
            }
            return new UnitConversionResult(quantity.setScale(SCALE, RoundingMode.HALF_UP), "卷");
        }

        BigDecimal stdQty = quantity.multiply(meta.factorToStandard).setScale(SCALE, RoundingMode.HALF_UP);
        return new UnitConversionResult(stdQty, meta.standardUnit);
    }

    @Override
    public BigDecimal convert(BigDecimal quantity, String fromUnit, String toUnit) {
        return convert(quantity, fromUnit, toUnit, null);
    }

    @Override
    public BigDecimal convert(BigDecimal quantity, String fromUnit, String toUnit, Map<String, BigDecimal> context) {
        if (quantity == null || fromUnit == null || toUnit == null) {
            return null;
        }

        String from = normalize(fromUnit);
        String to = normalize(toUnit);
        UnitMeta fromMeta = UNIT_META.get(from);
        UnitMeta toMeta = UNIT_META.get(to);

        // context 场景：桶与卷转标准（L/㎡）后再转目标
        UnitConversionResult srcStd = toStandard(quantity, fromUnit, context);
        if (srcStd == null || srcStd.getUnit() == null) {
            return null;
        }

        // 目标是“桶”或“卷”时，如需反推需要上下文
        if ("桶".equalsIgnoreCase(toUnit) || "drum".equalsIgnoreCase(to) || "barrel".equalsIgnoreCase(to)) {
            BigDecimal bucketVolumeL = getContextValue(context, "bucketVolumeL");
            if (bucketVolumeL == null) {
                throw new IllegalArgumentException("目标单位为桶时需提供 context.bucketVolumeL");
            }
            if (!"L".equals(srcStd.getUnit())) {
                throw new IllegalArgumentException("仅支持从 L 转换到 桶");
            }
            return srcStd.getQuantity().divide(bucketVolumeL, SCALE, RoundingMode.HALF_UP);
        }
        if ("卷".equals(toUnit) || "roll".equals(to) || "rolls".equals(to)) {
            BigDecimal rollAreaSqm = getContextValue(context, "rollAreaSqm");
            if (rollAreaSqm == null) {
                throw new IllegalArgumentException("目标单位为卷时需提供 context.rollAreaSqm");
            }
            if (!"㎡".equals(srcStd.getUnit())) {
                throw new IllegalArgumentException("仅支持从 ㎡ 转换到 卷");
            }
            return srcStd.getQuantity().divide(rollAreaSqm, SCALE, RoundingMode.HALF_UP);
        }

        if (toMeta == null) {
            throw new IllegalArgumentException("不支持的目标单位: " + toUnit);
        }

        // 非 context 场景，from 必须可识别
        if (fromMeta == null) {
            throw new IllegalArgumentException("不支持的来源单位: " + fromUnit);
        }

        // 检查量纲一致性（基于标准化后的单位）
        UnitConversionResult oneToStd = toStandard(BigDecimal.ONE, toUnit, context);
        if (oneToStd == null || oneToStd.getUnit() == null) {
            throw new IllegalArgumentException("目标单位不可标准化: " + toUnit);
        }
        if (!srcStd.getUnit().equals(oneToStd.getUnit())) {
            throw new IllegalArgumentException("不支持跨量纲转换: " + srcStd.getUnit() + " -> " + oneToStd.getUnit());
        }

        // srcStd 已是标准单位数量，目标单位由 factorToStandard 反推
        BigDecimal factor = toMeta.factorToStandard;
        return srcStd.getQuantity().divide(factor, SCALE, RoundingMode.HALF_UP);
    }
}
