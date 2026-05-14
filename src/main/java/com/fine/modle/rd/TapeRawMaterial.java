package com.fine.modle.rd;

/**
 * 配胶原料字典实体
 */
public class TapeRawMaterial {
    
    private Long id;
    private String materialCode;    // 物料代码
    private String materialName;    // 物料名称
    private String supplierCode;    // 供应商代码（原始导入）
    private String materialMajor;   // 物料大类（原始导入）
    private String materialCategoryRaw; // 物料类别（原始导入）
    private String materialCategory; // 物料类别: film/chemical
    private String materialType;    // 物料类型: resin/solvent/additive/curing
    private String unit;            // 单位
    private String spec;            // 规格说明
    private String performanceParams; // 性能参数(JSON)
    private String remark;          // 备注（原始导入）
    private Integer status;

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    
    public String getMaterialCode() { return materialCode; }
    public void setMaterialCode(String materialCode) { this.materialCode = materialCode; }
    
    public String getMaterialName() { return materialName; }
    public void setMaterialName(String materialName) { this.materialName = materialName; }

    public String getSupplierCode() { return supplierCode; }
    public void setSupplierCode(String supplierCode) { this.supplierCode = supplierCode; }

    public String getMaterialMajor() { return materialMajor; }
    public void setMaterialMajor(String materialMajor) { this.materialMajor = materialMajor; }

    public String getMaterialCategoryRaw() { return materialCategoryRaw; }
    public void setMaterialCategoryRaw(String materialCategoryRaw) { this.materialCategoryRaw = materialCategoryRaw; }

    public String getMaterialCategory() { return materialCategory; }
    public void setMaterialCategory(String materialCategory) { this.materialCategory = materialCategory; }
    
    public String getMaterialType() { return materialType; }
    public void setMaterialType(String materialType) { this.materialType = materialType; }
    
    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }
    
    public String getSpec() { return spec; }
    public void setSpec(String spec) { this.spec = spec; }

    public String getPerformanceParams() { return performanceParams; }
    public void setPerformanceParams(String performanceParams) { this.performanceParams = performanceParams; }

    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    
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

    public String getMaterialCategoryDisplay() {
        if (materialCategory == null) return "";
        switch (materialCategory) {
            case "film": return "薄膜";
            case "chemical": return "化工物料";
            default: return materialCategory;
        }
    }
}
