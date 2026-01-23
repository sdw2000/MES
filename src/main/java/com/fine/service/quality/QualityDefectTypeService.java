package com.fine.service.quality;

import com.fine.model.quality.QualityDefectType;

import java.util.List;

public interface QualityDefectTypeService {
    List<QualityDefectType> list();
    QualityDefectType create(QualityDefectType type);
    boolean delete(Long id);
}
