package com.fine.modle;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

@Data
@TableName("order_details")
public class OrderDetail {

	@TableId(type = IdType.AUTO)
    private Integer id;
    private String materialCode;
    private String materialName;
    private BigDecimal length;
    private BigDecimal width;
    private BigDecimal thickness;
    private Integer rollCount;
    private BigDecimal price;
    private BigDecimal amount;
    @TableLogic
    private Boolean isDeleted;
    private Integer orderId;
    private String createdBy;
    private String updatedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
