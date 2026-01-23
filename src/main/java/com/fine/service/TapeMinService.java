package com.fine.service;

import java.util.List;

import com.baomidou.mybatisplus.extension.service.IService;
import com.fine.Utils.ResponseResult;
import com.fine.modle.TapeMin;

public interface TapeMinService  extends IService<TapeMin>{
    
    ResponseResult<List<TapeMin>> queryWithPartNumber(String query,Integer id);

}
