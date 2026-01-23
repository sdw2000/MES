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
@TableName("purchase_receipts")
public class PurchaseReceipt {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String receiptNo;
    private String supplier;
    private String contactName;
    private String contactPhone;
    private String receiveAddress;

    private LocalDate expectedDate;
    private LocalDate receivedDate;

    private String status; // planned / receiving / received / partial / cancelled
    private String remark;

    private String createdBy;
    private String updatedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer isDeleted;

    @TableField(exist = false)
    private List<PurchaseReceiptItem> items;
}
