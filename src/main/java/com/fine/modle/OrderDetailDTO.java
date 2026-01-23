package com.fine.modle;

import java.math.BigDecimal;
import java.util.Date;

import lombok.Data;

@Data
public class OrderDetailDTO {
	private Integer id;
    private String orderNumber;
    private String customerOrderNumber;
    private String shortName;
    private Date orderDate;
    private Date deliveryDate;
    private int customerId;
    private String customerCode;
    private String notes;
    private String materialCode;
    private String materialName;
    private BigDecimal length;
    private BigDecimal width;
    private BigDecimal thickness;
    private int rollCount;
    private BigDecimal price;
    private BigDecimal amount;
    
    // getters and setters
}
