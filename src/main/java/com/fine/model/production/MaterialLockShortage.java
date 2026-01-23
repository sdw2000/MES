package com.fine.model.production;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 物料锁定缺口
 * 当订单物料不足时，记录缺口信息并触发分切排程
 */
@Data
@TableName("material_lock_shortage")
public class MaterialLockShortage {
    
    @TableId
    private Long id;
    
    @TableField("order_id")
    private Long orderId;
    
    @TableField("order_no")
    private String orderNo;
    
    @TableField("material_code")
    private String materialCode;
    
    @TableField("shortage_qty")
    private Integer shortageQty;
    
    @TableField("customer_priority")
    private BigDecimal customerPriority;
    
    @TableField("status")
    private String status; // PENDING, IN_SLITTING, COMPLETED, FAILED
    
    @TableField("slitting_task_id")
    private Long slittingTaskId;
    
    @TableField("created_at")
    private Date createdAt;
    
    @TableField("updated_at")
    private Date updatedAt;
    
    @TableField("remark")
    private String remark;
}
