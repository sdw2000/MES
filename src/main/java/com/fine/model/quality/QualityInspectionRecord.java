package com.fine.model.quality;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("quality_inspection")
public class QualityInspectionRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String inspectionNo;
    private String inspectionType; // incoming/process/outbound
    private String sourceOrderNo;
    private String taskType;
    private Long taskId;
    private String taskNo;
    private String batchNo;
    private String rollCode;
    private String materialCode;
    private String materialName;
    private String specification;
    private Integer sampleQty;
    private Integer passQty;
    private Integer failQty;
    private String overallResult; // pass/fail/pending
    private Long inspectorId;
    private String inspectorName;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime inspectionTime;
    private String defectType;
    private String defectDesc;
    private String remark;
    private String processNode;
    private String processSnapshot; // JSON string snapshot
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createdAt;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime updatedAt;
    @TableLogic
    private Integer isDeleted;
}
