package com.fine.modle;

import com.alibaba.excel.annotation.ExcelProperty;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;

@Data
@Getter
@Setter
@TableName("tapes")
public class TapeMin {
	
	private Integer customerId;
	
	@ExcelProperty("名称")
    private String materialName;

    @ExcelProperty("胶带料号")
    private String partNumber;
    
    @ExcelProperty("总厚度")
    private Double totalThickness;
    
    private double price;
    

}
