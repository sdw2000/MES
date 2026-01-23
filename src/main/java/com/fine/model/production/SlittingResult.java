package com.fine.model.production;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.util.Date;

/**
 * 分切结果
 * 记录分切任务的详细结果，用于库存回流
 */
@Data
@TableName("slitting_result")
public class SlittingResult {
    
    @TableId
    private Long id;
    
    @TableField("task_id")
    private Long taskId;
    
    @TableField("task_code")
    private String taskCode;
    
    @TableField("target_material_code")
    private String targetMaterialCode;
    
    @TableField("qty")
    private Integer qty;
    
    @TableField("waste_qty")
    private Integer wasteQty;
    
    @TableField("batch_no")
    private String batchNo;
    
    @TableField("produced_at")
    private Date producedAt;
    
    @TableField("warehouse_status")
    private String warehouseStatus; // PENDING, IN_WAREHOUSE, ALLOCATED
    
    @TableField("created_at")
    private Date createdAt;
}
