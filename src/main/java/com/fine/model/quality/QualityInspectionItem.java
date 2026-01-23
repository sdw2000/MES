package com.fine.model.quality;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("quality_inspection_item")
public class QualityInspectionItem {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long inspectionId;
    private String itemCode;
    private String itemName;
    private String standardValue;
    private String measuredValue;
    private String unit;
    private String result; // pass/fail
    private String remark;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createdAt;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime updatedAt;
    @TableLogic
    private Integer isDeleted;
}
