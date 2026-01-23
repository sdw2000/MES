package com.fine.model.schedule;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

@Data
@TableName("pending_slitting_order_pool")
public class PendingSlittingOrderPool implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;
    private String poolNo;
    private String materialCode;
    private String materialName;
    private Long orderId;
    private String orderNo;
    private Long orderItemId;
    private String customerName;
    private BigDecimal customerPriority;
    private Integer shortageQty;
    private BigDecimal shortageArea;
    private String poolStatus;
    private Long slittingTaskId;
    private Date addedAt;
    private Date completedAt;
}
