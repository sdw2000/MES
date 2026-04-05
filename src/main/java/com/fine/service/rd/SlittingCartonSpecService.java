package com.fine.service.rd;

import com.fine.Utils.ResponseResult;
import com.fine.modle.rd.SlittingCartonSpec;

public interface SlittingCartonSpecService {

    ResponseResult<?> getList(int page, int size, String materialCode, String specName, Integer status);

    ResponseResult<?> getById(Long id);

    ResponseResult<?> getByMaterialCode(String materialCode, Integer status);

    ResponseResult<?> create(SlittingCartonSpec spec, String operator);

    ResponseResult<?> update(SlittingCartonSpec spec, String operator);

    ResponseResult<?> delete(Long id);
}
