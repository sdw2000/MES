package com.fine.service.stock;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.fine.modle.stock.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * 胶带库存服务接口
 */
public interface TapeStockService {
    
    // ============= 库存管理 =============
    
    /**
     * 分页查询库存
     * @param qrCode 二维码/批次号
     * @param rollType 卷类型
     */
    IPage<TapeStock> getStockPage(int page, int size, String qrCode, String materialCode, String rollType, String location);
    
    /**
     * 按料号汇总库存
     */
    List<TapeStock> getStockSummary();
    
    /**
     * 根据料号查询所有批次（FIFO排序）
     */
    List<TapeStock> getStockByMaterialFIFO(String materialCode);
    
    /**
     * 根据ID查询库存
     */
    TapeStock getStockById(Long id);
    
    /**
     * 根据批次号查询库存
     */
    TapeStock getStockByBatchNo(String batchNo);
    
    /**
     * 导入Excel库存数据
     */
    Map<String, Object> importExcel(MultipartFile file);
    
    /**
     * 导出库存数据
     */
    List<TapeStock> exportStock(String materialCode, String location);
    
    // ============= 入库申请 =============
    
    /**
     * 分页查询入库申请
     */
    IPage<TapeInboundRequest> getInboundPage(int page, int size, Integer status, String materialCode);
    
    /**
     * 创建入库申请
     */
    TapeInboundRequest createInboundRequest(TapeInboundRequest request);
    
    /**
     * 审批入库申请
     */
    void approveInbound(Long id, boolean approved, String auditor, String auditRemark);
    
    /**
     * 取消入库申请
     */
    void cancelInbound(Long id);
    
    /**
     * 待审批入库数量
     */
    int countPendingInbound();
    
    // ============= 出库申请 =============
    
    /**
     * 分页查询出库申请
     */
    IPage<TapeOutboundRequest> getOutboundPage(int page, int size, Integer status, String materialCode);
    
    /**
     * 创建出库申请（手动选择批次）
     */
    TapeOutboundRequest createOutboundRequest(TapeOutboundRequest request);
    
    /**
     * 创建出库申请（FIFO自动分配）
     */
    List<TapeOutboundRequest> createOutboundRequestFIFO(String materialCode, int totalRolls, 
                                                         String applicant, String applyDept, String remark);
    
    /**
     * 审批出库申请
     */
    void approveOutbound(Long id, boolean approved, String auditor, String auditRemark);
    
    /**
     * 取消出库申请
     */
    void cancelOutbound(Long id);
    
    /**
     * 待审批出库数量
     */
    int countPendingOutbound();
    
    // ============= 库存流水 =============
    
    /**
     * 分页查询库存流水
     */
    IPage<TapeStockLog> getStockLogPage(int page, int size, String type, String materialCode, String batchNo);
    
    /**
     * 导出流水数据
     */
    List<TapeStockLog> exportStockLog(String type, String materialCode, String startDate, String endDate);
}
