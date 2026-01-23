package com.fine.modle.rd;

/**
 * 字典项实体（颜色、材质等）
 */
public class DictItem {
    
    private Long id;
    private String code;
    private String name;
    private String type;        // 字典类型
    private String extra;       // 额外信息（如颜色的hex值）
    private Integer sortOrder;
    private Integer status;
    
    public DictItem() {}
    
    public DictItem(String code, String name) {
        this.code = code;
        this.name = name;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    
    public String getExtra() { return extra; }
    public void setExtra(String extra) { this.extra = extra; }
    
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
    
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
}
