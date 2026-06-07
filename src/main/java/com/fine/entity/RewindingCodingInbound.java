package com.fine.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("rewinding_coding_inbound")
public class RewindingCodingInbound {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String batchNo;
    private String motherRollCode;
    private Long motherStockId;

    private String materialCode;
    private String materialName;

    private Integer motherThickness;
    private Integer motherWidthMm;
    private BigDecimal motherLengthM;

    private BigDecimal rewindingLengthM;
    private Integer rewindingRollCount;
    private String rewindingSpec;

    private Integer lineNo;
    private Integer serialStart;
    private Integer printCount;

    private String operator;
    private String visibleFields;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
