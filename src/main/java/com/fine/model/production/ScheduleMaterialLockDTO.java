package com.fine.model.production;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

/**
 * 排程物料锁定请求DTO
 */
@Data
public class ScheduleMaterialLockDTO {
    
    /** 排程ID */
    private Long scheduleId;
    
    /** 订单ID */
    private Long orderId;
    
    /** 薄膜库存ID */
    private Long filmStockId;
    
    /** 选择的薄膜宽度(mm) */
    private Integer filmWidth;
    
    /** 选择的薄膜厚度(μm) */
    private Integer filmThickness;
    
    /** 需求面积(㎡) */
    private BigDecimal requiredArea;
    
    /** 需求长度(m) */
    private BigDecimal requiredLength;
    
    /** 锁定的薄膜明细ID列表 */
    private List<Long> filmDetailIds;
    
    /** 备注 */
    private String remark;
    
    /** 操作人 */
    private String operator;
}
