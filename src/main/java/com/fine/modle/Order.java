package com.fine.modle;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("orders")
	public class Order {
	    @TableId(type = IdType.AUTO)
	    private Integer id;
	    private String orderNumber;
	    private String customerOrderNumber;
	    @TableField(exist = false)
	    private String shortName;
	    private Date orderDate;
	    private Date deliveryDate;
	    private BigDecimal amount;
	    private String notes;
	    @TableLogic
	    private Boolean isDeleted;
	    private Integer customerId;
	    private String status;
	    private String createdBy;
	    private String updatedBy;
	    private LocalDateTime createdAt;
	    private LocalDateTime updatedAt;
	    @TableField(exist = false)
	    private List<OrderDetail> orderDetails;
}
