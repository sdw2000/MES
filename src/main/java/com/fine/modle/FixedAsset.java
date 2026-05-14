package com.fine.modle;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Date;

@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("fixed_asset")
public class FixedAsset {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String assetCode;
    private String assetName;
    private String category;
    private Date purchaseDate;
    private BigDecimal originalValue;
    private BigDecimal salvageValue;
    private Integer usefulLifeMonths;
    private String depreciationMethod;
    private BigDecimal accumulatedDepreciation;
    private BigDecimal netValue;
    private String status;
    private String location;
    private String responsiblePerson;
    private Date disposeDate;
    private BigDecimal disposeAmount;
    private String remark;

    @TableLogic
    private Integer isDeleted;

    private Date createdAt;
    private Date updatedAt;
}
