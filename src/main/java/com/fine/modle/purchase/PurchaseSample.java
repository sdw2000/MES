package com.fine.modle.purchase;

import java.time.LocalDate;
import java.time.LocalDateTime;
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
@TableName("purchase_samples")
public class PurchaseSample {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String sampleNo;
    private String supplier;
    private String contactName;
    private String contactPhone;
    private String contactAddress;

    private LocalDate sendDate;
    private LocalDate expectedFeedbackDate;

    private String expressCompany;
    private String trackingNumber;
    private LocalDate shipDate;
    private LocalDate deliveryDate;

    private String status;
    private String logisticsStatus;
    private LocalDateTime lastLogisticsQueryTime;

    private Integer totalQuantity;
    private String remark;
    private String internalNote;

    private String createdBy;
    private String updatedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer isDeleted;

    @TableField(exist = false)
    private List<PurchaseSampleItem> items;
}
