package com.fine.modle;

import java.util.Date;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;


@Data
@AllArgsConstructor
@NoArgsConstructor
public class TapeInventory {
    private int id;
    private String materialNumber;
    private String materialTypeCode;
    private int thickness;
    private double width;
    private int length;
    private int quantity;
    private String palletNumber;
    private String productionBatchNumber;
    private String warehouseCode;
    private String creator;
    private Date createTime;
    private String modifier;
    private Date modifyTime;
    private int logicalDeleteCode;
    }
