package com.fine.model.quality;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("quality_defect_type")
public class QualityDefectType {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String defectCode;
    private String defectName;
    private String category;
    private String description;
    @TableLogic
    private Integer isDeleted;
}
