package com.fine.model.production;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 成本追溯
 * 记录订单全生命周期的成本：物料、分切、涂布、人工等
 */
@Data
@TableName("cost_tracking")
public class CostTracking {
    
    @TableId
    private Long id;
    
    @TableField("order_id")
    private Long orderId;
    
    @TableField("order_no")
    private String orderNo;
    
    @TableField("material_cost")
    private BigDecimal materialCost;
    
    @TableField("slitting_cost")
    private BigDecimal slittingCost;
    
    @TableField("coating_cost")
    private BigDecimal coatingCost;
    
    @TableField("labor_cost")
    private BigDecimal laborCost;
    
    @TableField("equipment_cost")
    private BigDecimal equipmentCost;
    
    @TableField("other_cost")
    private BigDecimal otherCost;
    
    @TableField("total_cost")
    private BigDecimal totalCost;
    
    @TableField("material_weight")
    private BigDecimal materialWeight;
    
    @TableField("finished_qty")
    private Integer finishedQty;
    
    @TableField("unit_cost")
    private BigDecimal unitCost;
    
    @TableField("status")
    private String status; // IN_PROGRESS, COMPLETED
    
    @TableField("created_at")
    private Date createdAt;
    
    @TableField("updated_at")
    private Date updatedAt;
}
