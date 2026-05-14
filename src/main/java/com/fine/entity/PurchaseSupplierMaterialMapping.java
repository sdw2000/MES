package com.fine.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("purchase_supplier_material_mapping")
public class PurchaseSupplierMaterialMapping {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 供应商代码 */
    private String supplierCode;

    /** 我司代码 */
    private String materialCode;

    /** 我司名称 */
    private String materialName;

    /** 供应商物料代码 */
    private String supplierMaterialCode;

    /** 供应商物料名称 */
    private String supplierMaterialName;

    /** 是否启用：1 启用，0 禁用 */
    private Integer isActive;

    private String remark;
    private String createBy;
    private LocalDateTime createTime;
    private String updateBy;
    private LocalDateTime updateTime;
}
