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
@TableName("fixed_asset_depreciation")
public class FixedAssetDepreciation {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long assetId;
    private String periodMonth;
    private BigDecimal depreciationAmount;
    private BigDecimal accumulatedAfter;
    private BigDecimal netValueAfter;
    private String voucherNo;
    private String note;

    @TableLogic
    private Integer isDeleted;

    private Date createdAt;
}
