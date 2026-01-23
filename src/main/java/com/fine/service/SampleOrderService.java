package com.fine.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fine.Utils.ResponseResult;
import com.fine.modle.SampleOrderDTO;
import com.fine.modle.LogisticsUpdateDTO;
import org.springframework.web.multipart.MultipartFile;
import java.util.Map;

/**
 * 送样订单Service接口
 * @author AI Assistant
 * @date 2026-01-05
 */
public interface SampleOrderService {
    
    /**
     * 分页查询送样订单列表
     */
    Page<SampleOrderDTO> list(int current, int size, String customerName, String status, String trackingNumber);
    
    /**
     * 根据编号查询送样订单详情（包含明细）
     */
    SampleOrderDTO getDetailBySampleNo(String sampleNo);
    
    /**
     * 创建送样订单
     */
    String create(SampleOrderDTO dto);
    
    /**
     * 更新送样订单
     */
    boolean update(SampleOrderDTO dto);
    
    /**
     * 删除送样订单（逻辑删除）
     */
    boolean delete(String sampleNo);
    
    /**
     * 更新物流信息
     */
    boolean updateLogistics(LogisticsUpdateDTO dto);
    
    /**
     * 更新状态
     */
    boolean updateStatus(String sampleNo, String newStatus, String reason);
    
    /**
     * 查询物流信息（调用快递100 API）
     */
    Map<String, Object> queryLogistics(String sampleNo);
    
    /**
     * 转为订单
     */
    String convertToOrder(String sampleNo);
    
    /**
     * 生成送样编号
     */
    String generateSampleNo();
    
    /**
     * 从Excel导入送样单
     */
    Map<String, Object> importFromExcel(MultipartFile file) throws Exception;
    
    /**
     * 导出送样单到Excel
     */
    ResponseResult<?> exportToExcel(String customerName, String status);
}
