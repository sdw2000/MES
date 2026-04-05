package com.fine.service.production;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.fine.model.production.RewindingProcessParams;

import java.util.List;

public interface RewindingProcessParamsService extends IService<RewindingProcessParams> {
    IPage<RewindingProcessParams> getPage(String materialCode, String equipmentCode, Integer page, Integer size);

    RewindingProcessParams getByMaterialAndEquipment(String materialCode, String equipmentCode);

    boolean addParams(RewindingProcessParams params);

    boolean updateParams(RewindingProcessParams params);

    boolean deleteParams(Long id);

    List<RewindingProcessParams> getListForExport(String materialCode, String equipmentCode);
}
