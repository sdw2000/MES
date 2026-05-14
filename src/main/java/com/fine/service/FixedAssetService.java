package com.fine.service;

import com.fine.Utils.ResponseResult;
import javax.servlet.http.HttpServletResponse;

import java.util.Map;

public interface FixedAssetService {
    ResponseResult<?> listAssets(Map<String, Object> params);
    ResponseResult<?> getAsset(Long id);
    ResponseResult<?> createAsset(Map<String, Object> payload);
    ResponseResult<?> updateAsset(Long id, Map<String, Object> payload);
    ResponseResult<?> depreciate(Map<String, Object> payload);
    ResponseResult<?> batchDepreciate(Map<String, Object> payload);
    ResponseResult<?> listDepreciations(Long assetId);
    ResponseResult<?> disposeAsset(Long id, Map<String, Object> payload);
    ResponseResult<?> reportSummary(String month);
    ResponseResult<?> reportLedger(Map<String, Object> params);
    void exportReport(Map<String, Object> params, HttpServletResponse response);
}
