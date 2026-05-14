package com.fine.service;

import com.fine.Utils.ResponseResult;
import java.util.Map;

public interface ApService {
    ResponseResult<?> listBills(Map<String, Object> params);
    ResponseResult<?> getBill(Long id);
    ResponseResult<?> createBill(Map<String, Object> payload);
}
