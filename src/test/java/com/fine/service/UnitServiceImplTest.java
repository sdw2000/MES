package com.fine.service;

import com.fine.model.UnitConversionResult;
import com.fine.serviceIMPL.UnitServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class UnitServiceImplTest {

    private UnitService unitService;

    @BeforeEach
    void setUp() {
        unitService = new UnitServiceImpl();
    }

    @Test
    void toStandard_shouldConvertWeightAndVolume() {
        UnitConversionResult g = unitService.toStandard(new BigDecimal("1500"), "g");
        assertEquals(new BigDecimal("1.500000"), g.getQuantity());
        assertEquals("kg", g.getUnit());

        UnitConversionResult ml = unitService.toStandard(new BigDecimal("2500"), "ml");
        assertEquals(new BigDecimal("2.500000"), ml.getQuantity());
        assertEquals("L", ml.getUnit());
    }

    @Test
    void toStandard_shouldNormalizeAliases() {
        UnitConversionResult area = unitService.toStandard(new BigDecimal("12.3"), "sqm");
        assertEquals(new BigDecimal("12.300000"), area.getQuantity());
        assertEquals("㎡", area.getUnit());

        UnitConversionResult roll = unitService.toStandard(new BigDecimal("8"), "rolls");
        assertEquals(new BigDecimal("8.000000"), roll.getQuantity());
        assertEquals("卷", roll.getUnit());
    }

    @Test
    void toStandard_shouldKeepUnknownUnit() {
        UnitConversionResult unknown = unitService.toStandard(new BigDecimal("3"), "BOX");
        assertEquals(new BigDecimal("3"), unknown.getQuantity());
        assertEquals("BOX", unknown.getUnit());
    }

    @Test
    void toStandard_withContext_shouldConvertBucketToL() {
        Map<String, BigDecimal> ctx = new HashMap<>();
        ctx.put("bucketVolumeL", new BigDecimal("18"));

        UnitConversionResult result = unitService.toStandard(new BigDecimal("2"), "桶", ctx);
        assertEquals(new BigDecimal("36.000000"), result.getQuantity());
        assertEquals("L", result.getUnit());
    }

    @Test
    void toStandard_withContext_shouldConvertRollToArea() {
        Map<String, BigDecimal> ctx = new HashMap<>();
        ctx.put("rollAreaSqm", new BigDecimal("120"));

        UnitConversionResult result = unitService.toStandard(new BigDecimal("2"), "卷", ctx);
        assertEquals(new BigDecimal("240.000000"), result.getQuantity());
        assertEquals("㎡", result.getUnit());
    }

    @Test
    void convert_shouldConvertWithinSameDimension() {
        BigDecimal kgToG = unitService.convert(new BigDecimal("1.5"), "kg", "g");
        assertEquals(new BigDecimal("1500.000000"), kgToG);

        BigDecimal tToKg = unitService.convert(new BigDecimal("2"), "吨", "kg");
        assertEquals(new BigDecimal("2000.000000"), tToKg);
    }

    @Test
    void convert_shouldThrowOnCrossDimension() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> unitService.convert(new BigDecimal("1"), "kg", "L"));
        assertTrue(ex.getMessage().contains("跨量纲"));
    }

    @Test
    void convert_withContext_shouldConvertBucketAndRoll() {
        Map<String, BigDecimal> ctx = new HashMap<>();
        ctx.put("bucketVolumeL", new BigDecimal("20"));
        ctx.put("rollAreaSqm", new BigDecimal("50"));

        BigDecimal bucketToL = unitService.convert(new BigDecimal("3"), "桶", "L", ctx);
        assertEquals(new BigDecimal("60.000000"), bucketToL);

        BigDecimal areaToRoll = unitService.convert(new BigDecimal("100"), "㎡", "卷", ctx);
        assertEquals(new BigDecimal("2.000000"), areaToRoll);
    }
}
