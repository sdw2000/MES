package com.fine.modle;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data 
@AllArgsConstructor
@NoArgsConstructor
public class Order2 {
	
	private String order_no;
	private long timestamp;
	private String username;
	private double price;
	private String status;
	

	

}
