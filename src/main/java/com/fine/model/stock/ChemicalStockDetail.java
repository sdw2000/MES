package com.fine.model.stock;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 化工原料库存明细表
 */
@Data
@TableName("chemical_stock_detail")
public class ChemicalStockDetail {
    
    /** 主键ID */
    @TableId(type = IdType.AUTO)
    private Long id;
    
    /** 关联chemical_stock表的ID */
    private Long chemicalStockId;
    
    /** 批次号 */
    private String batchNo;
    
    /** 桶号/包号 */
    private String containerNo;
    
    /** 重量(kg) */
    private BigDecimal weight;
    
    /** 库位 */
    private String location;
    
    /** 供应商 */
    private String supplier;
    
    /** 入库日期 */
    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "GMT+8")
    private Date inboundDate;
    
    /** 有效期至 */
    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "GMT+8")
    private Date expiryDate;
    
    /** 是否开封 */
    private Boolean isOpened;
    
    /** 危险等级：1-低，2-中，3-高 */
    private Integer dangerLevel;
    
    /** 状态：available-可用，locked-锁定，used-已使用 */
    private String status;
    
    /** 备注 */
    private String remark;
    
    /** 创建时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createTime;
    
    /** 更新时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date updateTime;
}
