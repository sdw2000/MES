package com.fine.service;

import com.fine.Utils.ResponseResult;
import java.util.Map;

public interface ArService {
    ResponseResult<?> listInvoices(Map<String, Object> params);
    ResponseResult<?> getInvoice(Long id);
    ResponseResult<?> createInvoice(Map<String, Object> payload);
    ResponseResult<?> createAndPostInvoice(Map<String, Object> payload);
}
