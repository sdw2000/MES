package com.fine.modle.rd;

/**
 * 配胶原料字典实体
 */
public class TapeRawMaterial {
    
    private Long id;
    private String materialCode;    // 物料代码
    private String materialName;    // 物料名称
    private String materialType;    // 物料类型: resin/solvent/additive/curing
    private String unit;            // 单位
    private String spec;            // 规格说明
    private Integer sortOrder;
    private Integer status;

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    
    public String getMaterialCode() { return materialCode; }
    public void setMaterialCode(String materialCode) { this.materialCode = materialCode; }
    
    public String getMaterialName() { return materialName; }
    public void setMaterialName(String materialName) { this.materialName = materialName; }
    
    public String getMaterialType() { return materialType; }
    public void setMaterialType(String materialType) { this.materialType = materialType; }
    
    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }
    
    public String getSpec() { return spec; }
    public void setSpec(String spec) { this.spec = spec; }
    
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
    
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    
    /**
     * 获取物料类型显示名称
     */
    public String getMaterialTypeDisplay() {
        if (materialType == null) return "";
        switch (materialType) {
            case "resin": return "树脂";
            case "solvent": return "溶剂";
            case "additive": return "助剂";
            case "curing": return "固化剂";
            default: return materialType;
        }
    }
}
