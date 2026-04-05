package com.fine.serviceIMPL.production;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fine.Dao.production.SlittingProcessParamsMapper;
import com.fine.model.production.SlittingProcessParams;
import com.fine.service.production.SlittingProcessParamsService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

@Service
public class SlittingProcessParamsServiceImpl extends ServiceImpl<SlittingProcessParamsMapper, SlittingProcessParams>
        implements SlittingProcessParamsService {

    private static final String DIMENSION_MATERIAL_CODE = "DIMENSION_RULE";

    @Override
    public IPage<SlittingProcessParams> getPage(BigDecimal totalThickness, BigDecimal processLength, BigDecimal processWidth, String equipmentCode, Integer page, Integer size) {
        IPage<SlittingProcessParams> req = new Page<>(page, size);
        IPage<SlittingProcessParams> result = baseMapper.selectPageList(req, totalThickness, processLength, processWidth, equipmentCode);
        result.getRecords().forEach(item -> {
            syncSpeedFields(item);
            item.setProcessType("SLITTING");
            item.setProcessTypeName("分切");
        });
        return result;
    }

    @Override
    public SlittingProcessParams getByDimensions(BigDecimal totalThickness, BigDecimal processLength, BigDecimal processWidth, String equipmentCode) {
        SlittingProcessParams params = baseMapper.selectByDimensions(totalThickness, processLength, processWidth, equipmentCode);
        if (params != null) {
            syncSpeedFields(params);
            params.setProcessType("SLITTING");
            params.setProcessTypeName("分切");
        }
        return params;
    }

    @Override
    @Transactional
    public boolean addParams(SlittingProcessParams params) {
        validateDimensionRequired(params);
        syncSpeedFields(params);
        int count = baseMapper.checkExists(params.getTotalThickness(), params.getProcessLength(), params.getProcessWidth(), params.getEquipmentCode(), 0L);
        if (count > 0) {
            throw new RuntimeException("该厚度+长度+宽度的分切参数已存在");
        }
        params.setMaterialCode(buildDimensionMaterialCode(params));
        params.setStatus(1);
        params.setCreateTime(new Date());
        params.setUpdateTime(new Date());
        return baseMapper.insert(params) > 0;
    }

    @Override
    @Transactional
    public boolean updateParams(SlittingProcessParams params) {
        validateDimensionRequired(params);
        syncSpeedFields(params);
        int count = baseMapper.checkExists(params.getTotalThickness(), params.getProcessLength(), params.getProcessWidth(), params.getEquipmentCode(), params.getId());
        if (count > 0) {
            throw new RuntimeException("该厚度+长度+宽度的分切参数已存在");
        }
        params.setMaterialCode(buildDimensionMaterialCode(params));
        params.setUpdateTime(new Date());
        return baseMapper.updateById(params) > 0;
    }

    @Override
    @Transactional
    public boolean deleteParams(Long id) {
        SlittingProcessParams params = new SlittingProcessParams();
        params.setId(id);
        params.setStatus(0);
        params.setUpdateTime(new Date());
        return baseMapper.updateById(params) > 0;
    }

    @Override
    public List<SlittingProcessParams> getListForExport(BigDecimal totalThickness, BigDecimal processLength, BigDecimal processWidth, String equipmentCode) {
        List<SlittingProcessParams> list = baseMapper.selectListForExport(totalThickness, processLength, processWidth, equipmentCode);
        list.forEach(this::syncSpeedFields);
        return list;
    }

    private void syncSpeedFields(SlittingProcessParams params) {
        if (params == null) {
            return;
        }
        BigDecimal productionSpeed = params.getProductionSpeed();
        BigDecimal slittingSpeed = params.getSlittingSpeed();
        if (productionSpeed == null && slittingSpeed != null) {
            params.setProductionSpeed(slittingSpeed);
        }
        if (slittingSpeed == null && productionSpeed != null) {
            params.setSlittingSpeed(productionSpeed);
        }
    }

    private void validateDimensionRequired(SlittingProcessParams params) {
        if (params == null) {
            throw new RuntimeException("参数不能为空");
        }
        if (params.getTotalThickness() == null) {
            throw new RuntimeException("总厚度不能为空");
        }
        if (params.getProcessLength() == null) {
            throw new RuntimeException("长度不能为空");
        }
        if (params.getProcessWidth() == null) {
            throw new RuntimeException("宽度不能为空");
        }
    }

    private String buildDimensionMaterialCode(SlittingProcessParams params) {
        String t = normalizeDimToken(params.getTotalThickness());
        String l = normalizeDimToken(params.getProcessLength());
        String w = normalizeDimToken(params.getProcessWidth());
        return DIMENSION_MATERIAL_CODE + "-" + t + "-" + l + "-" + w;
    }

    private String normalizeDimToken(BigDecimal value) {
        if (value == null) {
            return "0";
        }
        return value.stripTrailingZeros().toPlainString().replace('.', '_');
    }
}
