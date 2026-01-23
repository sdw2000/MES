package com.fine.modle;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

@TableName("quotation_details")
@Data
public class QuotationDetail {
	 @TableId(value = "quotation_detail_id", type = IdType.AUTO)
    private int quotationDetailId;
    private int quotationId;
    private int materialCode;
    @TableField(exist = false)
    private String materialName ;
    @TableField(exist = false)
    private String totalThickness ;
    @TableField(exist = false)
    private String partNumber;
    private double price;
    private int createdBy;
    private int updatedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private int isDeleted;
    }
