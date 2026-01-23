package com.fine.modle;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 送样明细实体类
 * @author AI Assistant
 * @date 2026-01-05
 */
@Data
@TableName("sample_items")
public class SampleItem implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;
    
    /**
     * 送样编号
     */
    private String sampleNo;
    
    /**
     * 物料代码
     */
    private String materialCode;
    
    /**
     * 物料名称/产品名称
     */
    private String materialName;
    
    /**
     * 规格
     */
    private String specification;
    
    /**
     * 型号
     */
    private String model;
    
    /**
     * 批次号
     */
    private String batchNo;
    
    /**
     * 长度(mm)
     */
    private BigDecimal length;
    
    /**
     * 宽度(mm)
     */
    private BigDecimal width;
    
    /**
     * 厚度(mm)
     */
    private BigDecimal thickness;
    
    /**
     * 数量/卷数
     */
    private Integer quantity;
    
    /**
     * 单位：卷、个、片、米等
     */
    private String unit;
    
    /**
     * 备注（在明细行中显示）
     */
    private String remark;
    
    /**
     * 创建时间
     */
    private LocalDateTime createTime;
    
    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
}
