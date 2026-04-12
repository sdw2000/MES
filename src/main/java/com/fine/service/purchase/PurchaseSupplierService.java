package com.fine.service.purchase;

import com.baomidou.mybatisplus.extension.service.IService;
import com.fine.Utils.ResponseResult;
import com.fine.modle.purchase.PurchaseSupplier;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletResponse;

public interface PurchaseSupplierService extends IService<PurchaseSupplier> {
    ResponseResult<?> listSuppliers(String keyword, Integer page, Integer size);
    ResponseResult<?> saveSupplier(PurchaseSupplier supplier);
    ResponseResult<?> deleteSupplier(Long id);
    ResponseResult<?> getSupplierDetail(Long id);
    void exportSuppliers(HttpServletResponse response, String keyword);
    void downloadTemplate(HttpServletResponse response);
    ResponseResult<?> importSuppliers(MultipartFile file);
}
