package com.fine.model.production;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 复卷分切任务
 * 处理物料缺口，从原始胶带分切出所需规格
 */
@Data
@TableName("slitting_task")
public class SlittingTask {
    
    @TableId
    private Long id;
    
    @TableField("task_code")
    private String taskCode;
    
    @TableField("shortage_id")
    private Long shortageId;
    
    @TableField("order_id")
    private Long orderId;
    
    @TableField("order_no")
    private String orderNo;
    
    @TableField("source_material_code")
    private String sourceMaterialCode;
    
    @TableField("target_material_code")
    private String targetMaterialCode;
    
    @TableField("film_width")
    private BigDecimal filmWidth;
    
    @TableField("qty")
    private Integer qty;
    
    @TableField("slitting_equipment_id")
    private Long slittingEquipmentId;
    
    @TableField("status")
    private String status; // PENDING, RUNNING, COMPLETED, FAILED
    
    @TableField("scheduled_date")
    private Date scheduledDate;
    
    @TableField("actual_start")
    private Date actualStart;
    
    @TableField("actual_end")
    private Date actualEnd;
    
    @TableField("completed_qty")
    private Integer completedQty;
    
    @TableField("waste_qty")
    private Integer wasteQty;
    
    @TableField("created_at")
    private Date createdAt;
    
    @TableField("updated_at")
    private Date updatedAt;
}
