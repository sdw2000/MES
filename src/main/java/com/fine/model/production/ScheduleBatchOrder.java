package com.fine.model.production;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;

@Data
@TableName("schedule_batch_order")
public class ScheduleBatchOrder {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long batchId;
    private Long orderId;
    private Long orderItemId;
    private String orderNo;

    private Integer quantity;
    private BigDecimal area;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createTime;
}
