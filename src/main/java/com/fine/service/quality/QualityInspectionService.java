package com.fine.service.quality;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fine.model.quality.QualityInspectionRecord;

import java.util.List;
import java.util.Map;

public interface QualityInspectionService {
    IPage<QualityInspectionRecord> list(Page<QualityInspectionRecord> page,
                                        String inspectionType,
                                        String inspectionNo,
                                        String batchNo,
                                        String rollCode,
                                        String materialCode,
                                        String inspectorName,
                                        String result,
                                        String startDate,
                                        String endDate);

    QualityInspectionRecord detail(Long id);

    QualityInspectionRecord create(QualityInspectionRecord record);

    QualityInspectionRecord updateRecord(QualityInspectionRecord record);

    boolean deleteRecord(Long id);

    List<String> listDistinctBatchNos(String inspectionType, String materialCode, String keyword, int limit);

    List<Map<String, Object>> listCoatingRollCandidates(String keyword, String materialCode, Boolean onlyPending, int limit);
}
