package com.fine.serviceIMPL.production;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fine.Dao.production.RewindingProcessParamsMapper;
import com.fine.Dao.rd.TapeSpecMapper;
import com.fine.modle.rd.TapeSpec;
import com.fine.model.production.RewindingProcessParams;
import com.fine.service.production.RewindingProcessParamsService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;

@Service
public class RewindingProcessParamsServiceImpl extends ServiceImpl<RewindingProcessParamsMapper, RewindingProcessParams>
        implements RewindingProcessParamsService {

    private final TapeSpecMapper tapeSpecMapper;

    public RewindingProcessParamsServiceImpl(TapeSpecMapper tapeSpecMapper) {
        this.tapeSpecMapper = tapeSpecMapper;
    }

    @Override
    public IPage<RewindingProcessParams> getPage(String materialCode, String equipmentCode, Integer page, Integer size) {
        IPage<RewindingProcessParams> req = new Page<>(page, size);
        IPage<RewindingProcessParams> result = baseMapper.selectPageList(req, materialCode, equipmentCode);
        result.getRecords().forEach(item -> {
            item.setProcessType("REWINDING");
            item.setProcessTypeName("复卷");
        });
        return result;
    }

    @Override
    public RewindingProcessParams getByMaterialAndEquipment(String materialCode, String equipmentCode) {
        RewindingProcessParams params = baseMapper.selectByMaterialAndEquipment(materialCode, equipmentCode);
        if (params != null) {
            params.setProcessType("REWINDING");
            params.setProcessTypeName("复卷");
        }
        return params;
    }

    @Override
    @Transactional
    public boolean addParams(RewindingProcessParams params) {
        validateMaterialCodeFromRd(params.getMaterialCode());
        int count = baseMapper.checkExists(params.getMaterialCode(), params.getEquipmentCode(), 0L);
        if (count > 0) {
            throw new RuntimeException("该料号复卷工艺参数已存在");
        }
        params.setStatus(1);
        params.setCreateTime(new Date());
        params.setUpdateTime(new Date());
        return baseMapper.insert(params) > 0;
    }

    @Override
    @Transactional
    public boolean updateParams(RewindingProcessParams params) {
        validateMaterialCodeFromRd(params.getMaterialCode());
        int count = baseMapper.checkExists(params.getMaterialCode(), params.getEquipmentCode(), params.getId());
        if (count > 0) {
            throw new RuntimeException("该料号复卷工艺参数已存在");
        }
        params.setUpdateTime(new Date());
        return baseMapper.updateById(params) > 0;
    }

    private void validateMaterialCodeFromRd(String materialCode) {
        if (materialCode == null || materialCode.trim().isEmpty()) {
            throw new RuntimeException("料号不能为空");
        }
        TapeSpec spec = tapeSpecMapper.selectByMaterialCode(materialCode.trim());
        if (spec == null) {
            throw new RuntimeException("料号不存在于研发表，请先维护研发规格");
        }
        if (spec.getStatus() == null || spec.getStatus() != 1) {
            throw new RuntimeException("料号在研发表中未启用，不能用于复卷工艺参数");
        }
    }

    @Override
    @Transactional
    public boolean deleteParams(Long id) {
        RewindingProcessParams params = new RewindingProcessParams();
        params.setId(id);
        params.setStatus(0);
        params.setUpdateTime(new Date());
        return baseMapper.updateById(params) > 0;
    }

    @Override
    public List<RewindingProcessParams> getListForExport(String materialCode, String equipmentCode) {
        return baseMapper.selectListForExport(materialCode, equipmentCode);
    }
}
