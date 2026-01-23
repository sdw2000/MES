package com.fine.model.quality.dto;

import lombok.Data;

@Data
public class QualityReportTypeStat {
    private String inspectionType;
    private Long totalCount;
    private Long passCount;
    private Long failCount;
}
