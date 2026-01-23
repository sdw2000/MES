package com.fine.model.quality;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("quality_disposition")
public class QualityDisposition {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String dispositionNo;
    private Long inspectionId;
    private String inspectionNo;
    private String batchNo;
    private Integer failQty;
    private Integer processedQty;
    private String dispositionMethod;
    private String dispositionDescription;
    private String status;
    private String creatorName;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createTime;
    private String remark;
    @TableLogic
    private Integer isDeleted;
}
