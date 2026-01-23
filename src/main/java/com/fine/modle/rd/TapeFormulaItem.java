package com.fine.modle.rd;

import java.math.BigDecimal;

/**
 * 配胶原料明细实体
 */
public class TapeFormulaItem {
    
    private Long id;
    private Long formulaId;         // 配方主表ID
    private String materialCode;    // 物料代码
    private String materialName;    // 物料名称
    private BigDecimal weight;      // 重量(Kg/桶)
    private BigDecimal ratio;       // 比例(%)
    private String remark;          // 备注
    private Integer sortOrder;      // 排序

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    
    public Long getFormulaId() { return formulaId; }
    public void setFormulaId(Long formulaId) { this.formulaId = formulaId; }
    
    public String getMaterialCode() { return materialCode; }
    public void setMaterialCode(String materialCode) { this.materialCode = materialCode; }
    
    public String getMaterialName() { return materialName; }
    public void setMaterialName(String materialName) { this.materialName = materialName; }
    
    public BigDecimal getWeight() { return weight; }
    public void setWeight(BigDecimal weight) { this.weight = weight; }
    
    public BigDecimal getRatio() { return ratio; }
    public void setRatio(BigDecimal ratio) { this.ratio = ratio; }
    
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
}
