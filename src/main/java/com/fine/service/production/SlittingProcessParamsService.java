package com.fine.service.production;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.fine.model.production.SlittingProcessParams;

import java.math.BigDecimal;
import java.util.List;

public interface SlittingProcessParamsService extends IService<SlittingProcessParams> {
    IPage<SlittingProcessParams> getPage(BigDecimal totalThickness, BigDecimal processLength, BigDecimal processWidth, String equipmentCode, Integer page, Integer size);

    SlittingProcessParams getByDimensions(BigDecimal totalThickness, BigDecimal processLength, BigDecimal processWidth, String equipmentCode);

    boolean addParams(SlittingProcessParams params);

    boolean updateParams(SlittingProcessParams params);

    boolean deleteParams(Long id);

    List<SlittingProcessParams> getListForExport(BigDecimal totalThickness, BigDecimal processLength, BigDecimal processWidth, String equipmentCode);
}
