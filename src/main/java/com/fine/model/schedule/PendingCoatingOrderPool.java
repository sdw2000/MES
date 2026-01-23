package com.fine.model.schedule;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 待涂布订单池实体
 */
@Data
@TableName("pending_coating_order_pool")
public class PendingCoatingOrderPool implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    @TableId(type = IdType.AUTO)
    private Long id;
    
    private String poolNo;              // 池编号（按料号）
    private String materialCode;        // 物料编号
    private String materialName;        // 物料名称
    
    private Long orderId;               // 订单ID
    private String orderNo;             // 订单编号
    private Long orderItemId;           // 订单明细ID
    private String customerName;        // 客户名称
    private BigDecimal customerPriority; // 客户优先级得分
    
    private Integer shortageQty;        // 缺口数量
    private BigDecimal shortageArea;    // 缺口面积（㎡）
    
    private String poolStatus;          // 状态：WAITING/COATING/COMPLETED/CANCELLED
    private Long coatingTaskId;         // 关联涂布任务ID
    
    private Date addedAt;               // 加入时间
    private Date completedAt;           // 完成时间
}
