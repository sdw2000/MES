package com.fine.modle.rd;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 胶带规格参数实体类
 * 用于研发管理模块维护产品规格
 */
public class TapeSpec {
    
    private Long id;
    private String materialCode;        // 胶带料号
    private String productName;         // 产品名称
    private String colorCode;           // 颜色代码
    private String colorName;           // 颜色名称
    
    // 基材参数
    private BigDecimal baseThickness;   // 基材厚度(μm)
    private String baseMaterial;        // 基材材质
    
    // 胶水参数
    private String glueMaterial;        // 胶水材质
    private BigDecimal glueThickness;   // 胶水厚度(μm)
    
    // 初粘性能
    private BigDecimal initialTackMin;  // 初粘下限
    private BigDecimal initialTackMax;  // 初粘上限
    private String initialTackType;     // range/gte/lte
    
    // 总厚度
    private BigDecimal totalThickness;     // 总厚度标准值
    private BigDecimal totalThicknessMin;  // 总厚度下限
    private BigDecimal totalThicknessMax;  // 总厚度上限
    
    // 剥离力
    private BigDecimal peelStrengthMin;    // 剥离力下限
    private BigDecimal peelStrengthMax;    // 剥离力上限
    private String peelStrengthType;       // range/gte
    
    // 解卷力
    private BigDecimal unwindForceMin;     // 解卷力下限
    private BigDecimal unwindForceMax;     // 解卷力上限
    private String unwindForceType;        // range/lte
    
    // 耐温
    private BigDecimal heatResistance;     // 耐温标准值
    private String heatResistanceType;     // gte

    // 扩展检测项目（用于检测报告额外项目）
    private String extraQcItem1Name;
    private String extraQcItem1Unit;
    private String extraQcItem1Standard;
    private String extraQcItem2Name;
    private String extraQcItem2Unit;
    private String extraQcItem2Standard;
    private String extraQcItem3Name;
    private String extraQcItem3Unit;
    private String extraQcItem3Standard;
    private String extraQcItem4Name;
    private String extraQcItem4Unit;
    private String extraQcItem4Standard;

    // 语义化别名字段（与扩展字段兼容）
    // 抗拉强度 <-> extraQcItem1
    private String tensileStrengthName;
    private String tensileStrengthUnit;
    private String tensileStrengthStandard;
    // 伸长率 <-> extraQcItem2
    private String elongationName;
    private String elongationUnit;
    private String elongationStandard;
    // 潘通色号（当前与颜色代码兼容）
    private String pantoneCode;
    
    private String remark;
    private Integer status;
    private Date createTime;
    private Date updateTime;
    private String createBy;
    private String updateBy;

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    
    public String getMaterialCode() { return materialCode; }
    public void setMaterialCode(String materialCode) { this.materialCode = materialCode; }
    
    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }
    
    public String getColorCode() { return colorCode; }
    public void setColorCode(String colorCode) {
        this.colorCode = colorCode;
        if (pantoneCode == null || pantoneCode.trim().isEmpty()) {
            this.pantoneCode = colorCode;
        }
    }
    
    public String getColorName() { return colorName; }
    public void setColorName(String colorName) { this.colorName = colorName; }
    
    public BigDecimal getBaseThickness() { return baseThickness; }
    public void setBaseThickness(BigDecimal baseThickness) { this.baseThickness = baseThickness; }
    
    public String getBaseMaterial() { return baseMaterial; }
    public void setBaseMaterial(String baseMaterial) { this.baseMaterial = baseMaterial; }
    
    public String getGlueMaterial() { return glueMaterial; }
    public void setGlueMaterial(String glueMaterial) { this.glueMaterial = glueMaterial; }
    
    public BigDecimal getGlueThickness() { return glueThickness; }
    public void setGlueThickness(BigDecimal glueThickness) { this.glueThickness = glueThickness; }
    
    public BigDecimal getInitialTackMin() { return initialTackMin; }
    public void setInitialTackMin(BigDecimal initialTackMin) { this.initialTackMin = initialTackMin; }
    
    public BigDecimal getInitialTackMax() { return initialTackMax; }
    public void setInitialTackMax(BigDecimal initialTackMax) { this.initialTackMax = initialTackMax; }
    
    public String getInitialTackType() { return initialTackType; }
    public void setInitialTackType(String initialTackType) { this.initialTackType = initialTackType; }
    
    public BigDecimal getTotalThickness() { return totalThickness; }
    public void setTotalThickness(BigDecimal totalThickness) { this.totalThickness = totalThickness; }
    
    public BigDecimal getTotalThicknessMin() { return totalThicknessMin; }
    public void setTotalThicknessMin(BigDecimal totalThicknessMin) { this.totalThicknessMin = totalThicknessMin; }
    
    public BigDecimal getTotalThicknessMax() { return totalThicknessMax; }
    public void setTotalThicknessMax(BigDecimal totalThicknessMax) { this.totalThicknessMax = totalThicknessMax; }
    
    public BigDecimal getPeelStrengthMin() { return peelStrengthMin; }
    public void setPeelStrengthMin(BigDecimal peelStrengthMin) { this.peelStrengthMin = peelStrengthMin; }
    
    public BigDecimal getPeelStrengthMax() { return peelStrengthMax; }
    public void setPeelStrengthMax(BigDecimal peelStrengthMax) { this.peelStrengthMax = peelStrengthMax; }
    
    public String getPeelStrengthType() { return peelStrengthType; }
    public void setPeelStrengthType(String peelStrengthType) { this.peelStrengthType = peelStrengthType; }
    
    public BigDecimal getUnwindForceMin() { return unwindForceMin; }
    public void setUnwindForceMin(BigDecimal unwindForceMin) { this.unwindForceMin = unwindForceMin; }
    
    public BigDecimal getUnwindForceMax() { return unwindForceMax; }
    public void setUnwindForceMax(BigDecimal unwindForceMax) { this.unwindForceMax = unwindForceMax; }
    
    public String getUnwindForceType() { return unwindForceType; }
    public void setUnwindForceType(String unwindForceType) { this.unwindForceType = unwindForceType; }
    
    public BigDecimal getHeatResistance() { return heatResistance; }
    public void setHeatResistance(BigDecimal heatResistance) { this.heatResistance = heatResistance; }
    
    public String getHeatResistanceType() { return heatResistanceType; }
    public void setHeatResistanceType(String heatResistanceType) { this.heatResistanceType = heatResistanceType; }

    public String getExtraQcItem1Name() { return extraQcItem1Name; }
    public void setExtraQcItem1Name(String extraQcItem1Name) {
        this.extraQcItem1Name = extraQcItem1Name;
        if (tensileStrengthName == null || tensileStrengthName.trim().isEmpty()) {
            this.tensileStrengthName = extraQcItem1Name;
        }
    }

    public String getExtraQcItem1Unit() { return extraQcItem1Unit; }
    public void setExtraQcItem1Unit(String extraQcItem1Unit) {
        this.extraQcItem1Unit = extraQcItem1Unit;
        if (tensileStrengthUnit == null || tensileStrengthUnit.trim().isEmpty()) {
            this.tensileStrengthUnit = extraQcItem1Unit;
        }
    }

    public String getExtraQcItem1Standard() { return extraQcItem1Standard; }
    public void setExtraQcItem1Standard(String extraQcItem1Standard) {
        this.extraQcItem1Standard = extraQcItem1Standard;
        if (tensileStrengthStandard == null || tensileStrengthStandard.trim().isEmpty()) {
            this.tensileStrengthStandard = extraQcItem1Standard;
        }
    }

    public String getExtraQcItem2Name() { return extraQcItem2Name; }
    public void setExtraQcItem2Name(String extraQcItem2Name) {
        this.extraQcItem2Name = extraQcItem2Name;
        if (elongationName == null || elongationName.trim().isEmpty()) {
            this.elongationName = extraQcItem2Name;
        }
    }

    public String getExtraQcItem2Unit() { return extraQcItem2Unit; }
    public void setExtraQcItem2Unit(String extraQcItem2Unit) {
        this.extraQcItem2Unit = extraQcItem2Unit;
        if (elongationUnit == null || elongationUnit.trim().isEmpty()) {
            this.elongationUnit = extraQcItem2Unit;
        }
    }

    public String getExtraQcItem2Standard() { return extraQcItem2Standard; }
    public void setExtraQcItem2Standard(String extraQcItem2Standard) {
        this.extraQcItem2Standard = extraQcItem2Standard;
        if (elongationStandard == null || elongationStandard.trim().isEmpty()) {
            this.elongationStandard = extraQcItem2Standard;
        }
    }

    public String getExtraQcItem3Name() { return extraQcItem3Name; }
    public void setExtraQcItem3Name(String extraQcItem3Name) { this.extraQcItem3Name = extraQcItem3Name; }

    public String getExtraQcItem3Unit() { return extraQcItem3Unit; }
    public void setExtraQcItem3Unit(String extraQcItem3Unit) { this.extraQcItem3Unit = extraQcItem3Unit; }

    public String getExtraQcItem3Standard() { return extraQcItem3Standard; }
    public void setExtraQcItem3Standard(String extraQcItem3Standard) { this.extraQcItem3Standard = extraQcItem3Standard; }

    public String getExtraQcItem4Name() { return extraQcItem4Name; }
    public void setExtraQcItem4Name(String extraQcItem4Name) { this.extraQcItem4Name = extraQcItem4Name; }

    public String getExtraQcItem4Unit() { return extraQcItem4Unit; }
    public void setExtraQcItem4Unit(String extraQcItem4Unit) { this.extraQcItem4Unit = extraQcItem4Unit; }

    public String getExtraQcItem4Standard() { return extraQcItem4Standard; }
    public void setExtraQcItem4Standard(String extraQcItem4Standard) { this.extraQcItem4Standard = extraQcItem4Standard; }

    // ===== 语义化别名 Getter/Setter（对外推荐使用） =====
    public String getTensileStrengthName() {
        return isBlank(tensileStrengthName) ? extraQcItem1Name : tensileStrengthName;
    }

    public void setTensileStrengthName(String tensileStrengthName) {
        this.tensileStrengthName = tensileStrengthName;
        this.extraQcItem1Name = tensileStrengthName;
    }

    public String getTensileStrengthUnit() {
        return isBlank(tensileStrengthUnit) ? extraQcItem1Unit : tensileStrengthUnit;
    }

    public void setTensileStrengthUnit(String tensileStrengthUnit) {
        this.tensileStrengthUnit = tensileStrengthUnit;
        this.extraQcItem1Unit = tensileStrengthUnit;
    }

    public String getTensileStrengthStandard() {
        return isBlank(tensileStrengthStandard) ? extraQcItem1Standard : tensileStrengthStandard;
    }

    public void setTensileStrengthStandard(String tensileStrengthStandard) {
        this.tensileStrengthStandard = tensileStrengthStandard;
        this.extraQcItem1Standard = tensileStrengthStandard;
    }

    public String getElongationName() {
        return isBlank(elongationName) ? extraQcItem2Name : elongationName;
    }

    public void setElongationName(String elongationName) {
        this.elongationName = elongationName;
        this.extraQcItem2Name = elongationName;
    }

    public String getElongationUnit() {
        return isBlank(elongationUnit) ? extraQcItem2Unit : elongationUnit;
    }

    public void setElongationUnit(String elongationUnit) {
        this.elongationUnit = elongationUnit;
        this.extraQcItem2Unit = elongationUnit;
    }

    public String getElongationStandard() {
        return isBlank(elongationStandard) ? extraQcItem2Standard : elongationStandard;
    }

    public void setElongationStandard(String elongationStandard) {
        this.elongationStandard = elongationStandard;
        this.extraQcItem2Standard = elongationStandard;
    }

    public String getPantoneCode() {
        return isBlank(pantoneCode) ? colorCode : pantoneCode;
    }

    public void setPantoneCode(String pantoneCode) {
        this.pantoneCode = pantoneCode;
        if (colorCode == null || colorCode.trim().isEmpty()) {
            this.colorCode = pantoneCode;
        }
    }
    
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    
    public Date getCreateTime() { return createTime; }
    public void setCreateTime(Date createTime) { this.createTime = createTime; }
    
    public Date getUpdateTime() { return updateTime; }
    public void setUpdateTime(Date updateTime) { this.updateTime = updateTime; }
    
    public String getCreateBy() { return createBy; }
    public void setCreateBy(String createBy) { this.createBy = createBy; }
    
    public String getUpdateBy() { return updateBy; }
    public void setUpdateBy(String updateBy) { this.updateBy = updateBy; }

    private boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
    
    // ========== 辅助方法：格式化显示 ==========
    
    /**
     * 获取初粘显示文本（如：2~6、≤4、≥3）
     */
    public String getInitialTackDisplay() {
        return formatRangeValue(initialTackMin, initialTackMax, initialTackType);
    }
    
    /**
     * 获取总厚度波动显示文本（如：10~14）
     */
    public String getThicknessRangeDisplay() {
        if (totalThicknessMin != null && totalThicknessMax != null) {
            return totalThicknessMin + "~" + totalThicknessMax;
        }
        return "";
    }
    
    /**
     * 获取剥离力显示文本
     */
    public String getPeelStrengthDisplay() {
        return formatRangeValue(peelStrengthMin, peelStrengthMax, peelStrengthType);
    }
    
    /**
     * 获取解卷力显示文本
     */
    public String getUnwindForceDisplay() {
        return formatRangeValue(unwindForceMin, unwindForceMax, unwindForceType);
    }
    
    /**
     * 获取耐温显示文本
     */
    public String getHeatResistanceDisplay() {
        if (heatResistance == null) return "";
        if ("gte".equals(heatResistanceType)) {
            return "≥" + heatResistance;
        }
        return heatResistance.toString();
    }
    
    private String formatRangeValue(BigDecimal min, BigDecimal max, String type) {
        if (type == null) type = "range";
        switch (type) {
            case "lte":
                return max != null ? "≤" + max : "";
            case "gte":
                return min != null ? "≥" + min : "";
            case "range":
            default:
                if (min != null && max != null) {
                    return min + "~" + max;
                } else if (min != null) {
                    return "≥" + min;
                } else if (max != null) {
                    return "≤" + max;
                }
                return "";
        }
    }
}
