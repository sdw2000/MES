package com.fine.serviceIMPL.quality;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fine.Dao.quality.QualityDispositionMapper;
import com.fine.model.quality.QualityDisposition;
import com.fine.service.quality.QualityDispositionService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

@Service
public class QualityDispositionServiceImpl extends ServiceImpl<QualityDispositionMapper, QualityDisposition>
        implements QualityDispositionService {

    @Override
    public IPage<QualityDisposition> list(Page<QualityDisposition> page, String dispositionNo, String inspectionNo, String status) {
        LambdaQueryWrapper<QualityDisposition> qw = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(dispositionNo)) {
            qw.like(QualityDisposition::getDispositionNo, dispositionNo);
        }
        if (StringUtils.hasText(inspectionNo)) {
            qw.like(QualityDisposition::getInspectionNo, inspectionNo);
        }
        if (StringUtils.hasText(status)) {
            qw.eq(QualityDisposition::getStatus, status);
        }
        qw.eq(QualityDisposition::getIsDeleted, 0);
        qw.orderByDesc(QualityDisposition::getCreateTime);
        return this.page(page, qw);
    }

    @Override
    public QualityDisposition detail(Long id) {
        return this.getById(id);
    }

    @Override
    public QualityDisposition create(QualityDisposition disposition) {
        disposition.setCreateTime(LocalDateTime.now());
        disposition.setStatus(StringUtils.hasText(disposition.getStatus()) ? disposition.getStatus() : "pending");
        disposition.setIsDeleted(0);
        this.save(disposition);
        return disposition;
    }

    @Override
    public QualityDisposition updateDisposition(QualityDisposition disposition) {
        disposition.setIsDeleted(0);
        this.updateById(disposition);
        return disposition;
    }

    @Override
    public boolean delete(Long id) {
        return this.removeById(id);
    }

    @Override
    public QualityDisposition approve(Long id, String status, String remark) {
        QualityDisposition disp = this.getById(id);
        if (disp == null) {
            return null;
        }
        if (StringUtils.hasText(status)) {
            disp.setStatus(status);
        }
        if (StringUtils.hasText(remark)) {
            disp.setRemark(remark);
        }
        this.updateById(disp);
        return disp;
    }
}
