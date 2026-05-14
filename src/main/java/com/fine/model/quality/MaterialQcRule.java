package com.fine.model.quality;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("material_qc_rule")
public class MaterialQcRule {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String materialCode;
    private String itemCode;
    private String itemName;
    private String unit;
    private String judgeMode; // range/min/max/eq
    private BigDecimal min;
    private BigDecimal max;
    private String standardValue;
    private Integer sort;
    private String remark;
    private Integer isActive;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
