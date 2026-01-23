package com.fine.model.production;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 待涂布池
 * 物料准备就绪后进入池子，等待涂布排程分配到具体机台
 */
@Data
@TableName("pending_coating_pool")
public class PendingCoatingPool {
    
    @TableId
    private Long id;
    
    @TableField("order_id")
    private Long orderId;
    
    @TableField("order_no")
    private String orderNo;
    
    @TableField("material_code")
    private String materialCode;
    
    @TableField("tape_spec_id")
    private Long tapeSpecId;
    
    @TableField("qty")
    private Integer qty;
    
    @TableField("film_width")
    private BigDecimal filmWidth;
    
    @TableField("customer_priority")
    private BigDecimal customerPriority;
    
    @TableField("status")
    private String status; // WAITING, SCHEDULED, IN_PROGRESS, COMPLETED
    
    @TableField("coating_schedule_id")
    private Long coatingScheduleId;
    
    @TableField("created_at")
    private Date createdAt;
    
    @TableField("updated_at")
    private Date updatedAt;
}
