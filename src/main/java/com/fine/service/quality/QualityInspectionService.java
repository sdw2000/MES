package com.fine.service.quality;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fine.model.quality.QualityInspectionRecord;

public interface QualityInspectionService {
    IPage<QualityInspectionRecord> list(Page<QualityInspectionRecord> page,
                                        String inspectionType,
                                        String inspectionNo,
                                        String batchNo,
                                        String rollCode,
                                        String result,
                                        String startDate,
                                        String endDate);

    QualityInspectionRecord detail(Long id);

    QualityInspectionRecord create(QualityInspectionRecord record);

    QualityInspectionRecord updateRecord(QualityInspectionRecord record);

    boolean deleteRecord(Long id);
}
