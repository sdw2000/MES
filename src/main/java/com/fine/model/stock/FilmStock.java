package com.fine.model.stock;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 薄膜库存总量表
 */
@Data
@TableName("film_stock")
public class FilmStock {
    
    /** 主键ID */
    @TableId(type = IdType.AUTO)
    private Long id;
    
    /** 物料编号 */
    private String materialCode;
    
    /** 物料名称 */
    private String materialName;
    
    /** 规格描述（如：25*1040） */
    private String specDesc;
    
    /** 薄膜厚度(μm) */
    private BigDecimal thickness;
    
    /** 薄膜宽度(mm) */
    private Integer width;
    
    /** 总面积(㎡) */
    private BigDecimal totalArea;
    
    /** 可用面积(㎡) */
    private BigDecimal availableArea;
    
    /** 锁定面积(㎡) */
    private BigDecimal lockedArea;
    
    /** 总卷数 */
    private Integer totalRolls;
    
    /** 可用卷数 */
    private Integer availableRolls;
    
    /** 锁定卷数 */
    private Integer lockedRolls;
    
    /** 安全库存(㎡) */
    private BigDecimal safetyStock;
    
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
}
