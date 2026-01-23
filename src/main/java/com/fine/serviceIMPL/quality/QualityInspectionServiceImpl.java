package com.fine.serviceIMPL.quality;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fine.Dao.quality.QualityInspectionRecordMapper;
import com.fine.model.quality.QualityInspectionRecord;
import com.fine.service.quality.QualityInspectionService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

@Service
public class QualityInspectionServiceImpl extends ServiceImpl<QualityInspectionRecordMapper, QualityInspectionRecord>
        implements QualityInspectionService {

    @Override
    public IPage<QualityInspectionRecord> list(Page<QualityInspectionRecord> page,
                                               String inspectionType,
                                               String inspectionNo,
                                               String batchNo,
                                               String rollCode,
                                               String result,
                                               String startDate,
                                               String endDate) {
        return baseMapper.selectPaged(page, inspectionType, inspectionNo, batchNo, rollCode, result, startDate, endDate);
    }

    @Override
    public QualityInspectionRecord detail(Long id) {
        return this.getById(id);
    }

    @Override
    public QualityInspectionRecord create(QualityInspectionRecord record) {
        if (!StringUtils.hasText(record.getInspectionType())) {
            record.setInspectionType("incoming");
        }
        if (!StringUtils.hasText(record.getInspectionNo())) {
            record.setInspectionNo(baseMapper.generateInspectionNo());
        }
        LocalDateTime now = LocalDateTime.now();
        record.setCreatedAt(now);
        record.setUpdatedAt(now);
        this.save(record);
        return record;
    }

    @Override
    public QualityInspectionRecord updateRecord(QualityInspectionRecord record) {
        record.setUpdatedAt(LocalDateTime.now());
        this.updateById(record);
        return record;
    }

    @Override
    public boolean deleteRecord(Long id) {
        return this.removeById(id);
    }
}
