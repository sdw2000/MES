package com.fine.modle;

import java.time.LocalDateTime;

import lombok.Data;


@Data
public class QuotationDTO {

	private int customerId;
	private String customerCode;
	private String shortName;
	private String partNumber;
	private String materialName;
	private int quotationId; 
	private int quotationDetailId;
	private  int materialCode;
	private double price;
	private LocalDateTime createdAt;
	private int isDeleted;
	private LocalDateTime validityPeriod;
	

}
