package com.fine.model.stock;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 化工原料库存总量表
 */
@Data
@TableName("chemical_stock")
public class ChemicalStock {
    
    /** 主键ID */
    @TableId(type = IdType.AUTO)
    private Long id;
    
    /** 物料编号 */
    private String materialCode;
    
    /** 物料名称 */
    private String materialName;
    
    /** 化工类型：adhesive-胶水，solvent-溶剂，additive-助剂，other-其他 */
    private String chemicalType;
    
    /** 单位：桶，包 */
    private String unit;
    
    /** 单桶/包重量(kg) */
    private BigDecimal unitWeight;
    
    /** 总数量 */
    private Integer totalQuantity;
    
    /** 可用数量 */
    private Integer availableQuantity;

    /** 可用包装数 */
    @TableField("available_pack_count")
    private Integer availablePackCount;
    
    /** 锁定数量 */
    private Integer lockedQuantity;

    /** 锁定包装数 */
    @TableField("locked_pack_count")
    private Integer lockedPackCount;
    
    /** 安全库存 */
    private Integer safetyStock;

    /** 总包装数 */
    @TableField("total_pack_count")
    private Integer totalPackCount;
    
    /** 状态：active-正常，low_stock-库存不足，out_of_stock-缺货 */
    private String status;
    
    /** 备注 */
    private String remark;
    
    /** 创建时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createTime;
    
    /** 更新时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date updateTime;
    
    /** 创建人 */
    private String createBy;
    
    /** 更新人 */
    private String updateBy;

    /** 桶数（按导入原值持久化） */
    @TableField("bucket_count")
    private Integer bucketCount;
}
