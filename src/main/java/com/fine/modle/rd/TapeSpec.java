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
    public void setColorCode(String colorCode) { this.colorCode = colorCode; }
    
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
