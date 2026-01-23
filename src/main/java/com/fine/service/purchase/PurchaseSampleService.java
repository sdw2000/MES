package com.fine.service.purchase;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.fine.Utils.ResponseResult;
import com.fine.modle.purchase.PurchaseSample;
import org.springframework.web.multipart.MultipartFile;

public interface PurchaseSampleService extends IService<PurchaseSample> {
    ResponseResult<?> list(int current, int size, String supplier, String status, String trackingNumber);
    ResponseResult<?> detail(String sampleNo);
    ResponseResult<?> create(PurchaseSample sample);
    ResponseResult<?> updateSample(PurchaseSample sample);
    ResponseResult<?> deleteSample(String sampleNo);
    ResponseResult<?> updateStatus(String sampleNo, String status);
    String generateSampleNo();
    ResponseResult<?> importSamples(MultipartFile file);
    ResponseResult<?> exportSamples(String supplier, String status);
}
