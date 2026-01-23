package com.fine.modle.rd;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

/**
 * 配胶标准单主表实体
 */
public class TapeFormula {
    
    private Long id;
    private String materialCode;        // 产品料号
    private String productName;         // 产品名称
    private String formulaNo;           // 文件编号
    private String version;             // 版次
    private Date createDate;            // 制定日期
    
    // 胶水信息
    private String glueModel;           // 胶水型号
    private String colorCode;           // 颜色代码
    private BigDecimal coatingThickness;// 涂胶厚度(μm)
    private BigDecimal glueDensity;     // 胶水密度(g/cm³)
    private String solidContent;        // 固含量(%)
    private BigDecimal coatingArea;     // 涂布数量(㎡)
    
    private String processRemark;       // 工艺备注
    private BigDecimal totalWeight;     // 总重量(kg)
    
    // 审批信息
    private String preparedBy;          // 编制人
    private String reviewedBy;          // 审核人
    private String approvedBy;          // 批准人
    
    private Integer status;
    private String remark;
    private Date createTime;
    private Date updateTime;
    private String createBy;
    private String updateBy;
    
    // 关联的原料明细列表
    private List<TapeFormulaItem> items;

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    
    public String getMaterialCode() { return materialCode; }
    public void setMaterialCode(String materialCode) { this.materialCode = materialCode; }
    
    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }
    
    public String getFormulaNo() { return formulaNo; }
    public void setFormulaNo(String formulaNo) { this.formulaNo = formulaNo; }
    
    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }
    
    public Date getCreateDate() { return createDate; }
    public void setCreateDate(Date createDate) { this.createDate = createDate; }
    
    public String getGlueModel() { return glueModel; }
    public void setGlueModel(String glueModel) { this.glueModel = glueModel; }
    
    public String getColorCode() { return colorCode; }
    public void setColorCode(String colorCode) { this.colorCode = colorCode; }
    
    public BigDecimal getCoatingThickness() { return coatingThickness; }
    public void setCoatingThickness(BigDecimal coatingThickness) { this.coatingThickness = coatingThickness; }
    
    public BigDecimal getGlueDensity() { return glueDensity; }
    public void setGlueDensity(BigDecimal glueDensity) { this.glueDensity = glueDensity; }
    
    public String getSolidContent() { return solidContent; }
    public void setSolidContent(String solidContent) { this.solidContent = solidContent; }
    
    public BigDecimal getCoatingArea() { return coatingArea; }
    public void setCoatingArea(BigDecimal coatingArea) { this.coatingArea = coatingArea; }
    
    public String getProcessRemark() { return processRemark; }
    public void setProcessRemark(String processRemark) { this.processRemark = processRemark; }
    
    public BigDecimal getTotalWeight() { return totalWeight; }
    public void setTotalWeight(BigDecimal totalWeight) { this.totalWeight = totalWeight; }
    
    public String getPreparedBy() { return preparedBy; }
    public void setPreparedBy(String preparedBy) { this.preparedBy = preparedBy; }
    
    public String getReviewedBy() { return reviewedBy; }
    public void setReviewedBy(String reviewedBy) { this.reviewedBy = reviewedBy; }
    
    public String getApprovedBy() { return approvedBy; }
    public void setApprovedBy(String approvedBy) { this.approvedBy = approvedBy; }
    
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    
    public Date getCreateTime() { return createTime; }
    public void setCreateTime(Date createTime) { this.createTime = createTime; }
    
    public Date getUpdateTime() { return updateTime; }
    public void setUpdateTime(Date updateTime) { this.updateTime = updateTime; }
    
    public String getCreateBy() { return createBy; }
    public void setCreateBy(String createBy) { this.createBy = createBy; }
    
    public String getUpdateBy() { return updateBy; }
    public void setUpdateBy(String updateBy) { this.updateBy = updateBy; }
    
    public List<TapeFormulaItem> getItems() { return items; }
    public void setItems(List<TapeFormulaItem> items) { this.items = items; }
}
