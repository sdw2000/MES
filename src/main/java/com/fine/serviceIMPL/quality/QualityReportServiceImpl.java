package com.fine.serviceIMPL.quality;

import com.fine.Dao.quality.QualityInspectionRecordMapper;
import com.fine.model.quality.dto.QualityReportDefectStat;
import com.fine.model.quality.dto.QualityReportTypeStat;
import com.fine.service.quality.QualityReportService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class QualityReportServiceImpl implements QualityReportService {

    @Autowired
    private QualityInspectionRecordMapper inspectionMapper;

    @Override
    public Map<String, Object> summary() {
        List<QualityReportTypeStat> typeStats = inspectionMapper.selectTypeStats();
        List<QualityReportDefectStat> defectTop = inspectionMapper.selectDefectTop(10);
        Map<String, Object> map = new HashMap<>();
        map.put("typeStats", typeStats);
        map.put("defectTop", defectTop);
        return map;
    }
}
