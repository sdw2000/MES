package com.fine.serviceIMPL.quality;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fine.Dao.quality.QualityDefectTypeMapper;
import com.fine.model.quality.QualityDefectType;
import com.fine.service.quality.QualityDefectTypeService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
public class QualityDefectTypeServiceImpl extends ServiceImpl<QualityDefectTypeMapper, QualityDefectType>
        implements QualityDefectTypeService {

    @Override
    public List<QualityDefectType> list() {
        LambdaQueryWrapper<QualityDefectType> qw = new LambdaQueryWrapper<>();
        qw.eq(QualityDefectType::getIsDeleted, 0);
        qw.orderByAsc(QualityDefectType::getDefectCode);
        return this.list(qw);
    }

    @Override
    public QualityDefectType create(QualityDefectType type) {
        if (!StringUtils.hasText(type.getCategory())) {
            type.setCategory("general");
        }
        type.setIsDeleted(0);
        this.save(type);
        return type;
    }

    @Override
    public boolean delete(Long id) {
        return this.removeById(id);
    }
}
