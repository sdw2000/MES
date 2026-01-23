package com.fine.modle.purchase;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("purchase_sample_items")
public class PurchaseSampleItem {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String sampleNo;
    private String materialCode;
    private String materialName;
    private String specification;
    private String model;
    private String batchNo;
    private BigDecimal length;
    private BigDecimal width;
    private BigDecimal thickness;
    private Integer quantity;
    private String unit;
    private String remark;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer isDeleted;
}
