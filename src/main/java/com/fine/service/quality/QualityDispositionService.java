package com.fine.service.quality;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fine.model.quality.QualityDisposition;

public interface QualityDispositionService {
    IPage<QualityDisposition> list(Page<QualityDisposition> page, String dispositionNo, String inspectionNo, String status);
    QualityDisposition detail(Long id);
    QualityDisposition create(QualityDisposition disposition);
    QualityDisposition updateDisposition(QualityDisposition disposition);
    boolean delete(Long id);
    QualityDisposition approve(Long id, String status, String remark);
}
