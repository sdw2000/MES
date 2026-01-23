package com.fine.service;

import org.springframework.web.multipart.MultipartFile;

import com.baomidou.mybatisplus.extension.service.IService;
import com.fine.Utils.ResponseResult;
import com.fine.modle.Quotation;

/**
 * 报价单服务接口
 */
public interface QuotationService extends IService<Quotation> {
      // ============= 新增方法：完整的报价单CRUD =============
    
    /**
     * 获取所有报价单列表
     */
    ResponseResult<?> getAllQuotations();
    
    /**
     * 根据ID获取报价单详情
     */
    ResponseResult<?> getQuotationById(Long quotationId);
    
    /**
     * 创建报价单
     */
    ResponseResult<?> createQuotation(Quotation quotation);
    
    /**
     * 更新报价单
     */
    ResponseResult<?> updateQuotation(Quotation quotation);
    
    /**
     * 删除报价单（逻辑删除）
     */
    ResponseResult<?> deleteQuotation(Long quotationId);
    
    // ============= 保留原有方法 =============
    
    void save(MultipartFile file);

    ResponseResult<?> fetchQueryList(String customerCode, String shortName, int page, int size);

    ResponseResult<?> searchTableByKeyWord(String keyword);

    ResponseResult<?> insert(Quotation quotation);

    ResponseResult<?> deleteQuotationDetails(String quotationDetailId, String id);

    ResponseResult<?> getOrdersQuotationByNumble(String id);

    ResponseResult<?> deleteQuotation(String id);
    
    /**
     * 根据料号获取材料类别
     */
    String getCategoryByMaterialCode(String materialCode);
    
    /**
     * 从Excel导入报价单
     */
    ResponseResult<?> importFromExcel(MultipartFile file);
    
    /**
     * 导出报价单
     */
    ResponseResult<?> exportQuotations();
}
