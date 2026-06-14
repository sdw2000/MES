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
     * @param workshopSection 所属车间(涂布/包装等)
     * @param workshopStatus 车间状态
     */
    IPage<TapeStock> getStockPage(int page, int size, String qrCode, String materialCode, String rollType, String location, String workshopSection, String workshopStatus);
    
    /**
     * 按料号汇总库存
     */
    List<TapeStock> getStockSummary();

    /**
     * 按料号汇总库存（按库位过滤）
     */
    List<TapeStock> getStockSummary(String location);

    /**
     * 按料号汇总库存（按退货专仓过滤）
     */
    List<TapeStock> getStockSummary(Boolean includeReturnWarehouse);

    /**
     * 按料号汇总库存（分页）
     */
    IPage<TapeStock> getStockSummaryPage(int page, int size, String materialCode);

    /**
     * 按料号汇总库存（分页，按库位过滤）
     */
    IPage<TapeStock> getStockSummaryPage(int page, int size, String materialCode, String location);

    /**
     * 按料号汇总库存（分页，按退货专仓过滤）
     */
    IPage<TapeStock> getStockSummaryPage(int page, int size, String materialCode, Boolean includeReturnWarehouse);
    
    /**
     * 根据料号查询所有批次（FIFO排序）
     */
    List<TapeStock> getStockByMaterialFIFO(String materialCode);

    /**
     * 按关键词查询可用批次（支持胶带/原材料料号模糊匹配）
     */
    List<TapeStock> searchStockByMaterialKeyword(String keyword);

    /**
     * 根据料号查询库存明细（分页）
     */
    IPage<TapeStock> getStockByMaterialPage(int page, int size, String materialCode);

    /**
     * 根据料号查询库存明细（分页，按库位过滤）
     */
    IPage<TapeStock> getStockByMaterialPage(int page, int size, String materialCode, String location);

    /**
     * 根据料号查询库存明细（分页，按退货专仓过滤）
     */
    IPage<TapeStock> getStockByMaterialPage(int page, int size, String materialCode, Boolean includeReturnWarehouse);

    /**
     * 根据料号查询库存明细（分页，按退货专仓过滤，支持排序）
     */
    IPage<TapeStock> getStockByMaterialPage(int page, int size, String materialCode, Boolean includeReturnWarehouse,
                                            String sortField, String sortOrder);
    
    /**
     * 根据ID查询库存
     */
    TapeStock getStockById(Long id);

    /**
     * 库存盘点
     */
    TapeStock stocktake(Long stockId, Integer actualRolls, java.math.BigDecimal actualSqm, String operator, String reason);
    
    /**
     * 根据批次号查询库存
     */
    TapeStock getStockByBatchNo(String batchNo);
    
    /**
     * 导入Excel库存数据
     */
    Map<String, Object> importExcel(MultipartFile file);

    /**
     * 异步导入Excel库存数据（返回任务ID）
     */
    Map<String, Object> importExcelAsync(MultipartFile file);

    /**
     * 查询异步导入任务状态
     */
    Map<String, Object> getImportTaskStatus(String taskId);

    /**
     * 获取异步导入任务失败明细Excel
     */
    byte[] getImportTaskFailedExcel(String taskId);
    
    /**
     * 导出库存数据
     */
    List<TapeStock> exportStock(String materialCode, String location);
    
    // ============= 入库申请 =============
    
    /**
     * 分页查询入库申请
     */
    IPage<TapeInboundRequest> getInboundPage(int page, int size, Integer status, String materialCode, String sourceType,
                                             Long receiptId, Long itemId, String keyword);

    /**
     * 采购收货标签打印前置：按规则生成标签数据（含日期+日流水二维码）
     */
    Map<String, Object> preparePurchaseInboundLabelPrint(Long inboundId, Map<String, Object> payload, String operator);
    
    /**
     * 创建入库申请
     */
    TapeInboundRequest createInboundRequest(TapeInboundRequest request);
    
    /**
     * 审批入库申请
     */
    void approveInbound(Long id, boolean approved, String auditor, String auditRemark, String scannedRollCode, String scannedLocation);

    /**
     * 按母卷号批量审批入库（同一卡板）
     */
    Map<String, Object> approveInboundByRollCodes(List<String> rollCodes, String auditor, String auditRemark, String scannedLocation);
    
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
    IPage<TapeOutboundRequest> getOutboundPage(int page, int size, Integer status, String materialCode, String orderNo);

    /**
     * 统一分页查询出库列表（胶带产品 + 原材料）
     */
    Map<String, Object> getUnifiedOutboundPage(int page, int size, Integer status, String materialCode, String bizType, String orderNo);
    
    /**
     * 创建出库申请（手动选择批次）
     */
    TapeOutboundRequest createOutboundRequest(TapeOutboundRequest request);

    /**
     * 修改出库申请（仅待审批）
     */
    TapeOutboundRequest updateOutboundRequest(Long id, Integer rolls, String applyDept, String remark);
    
    /**
     * 创建出库申请（FIFO自动分配）
     */
    List<TapeOutboundRequest> createOutboundRequestFIFO(String materialCode, int totalRolls,
                                                         String applicant, String applyDept, String remark,
                                                         String orderNo, Long orderItemId, String bizType);
    
    /**
     * 审批出库申请
     */
    void approveOutbound(Long id, boolean approved, String auditor, String auditRemark, String scannedRollCode);

    /**
     * 批量扫码审批出库
     */
    Map<String, Object> approveOutboundByRollCodes(List<String> rollCodes, String auditor, String auditRemark);
    
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
    IPage<TapeStockLog> getStockLogPage(int page, int size, String type, String materialCode, String batchNo, String orderNo);

    /**
     * 分页查询出库流水汇总（按关联单号+料号+批次聚合）
     */
    IPage<TapeStockLog> getOutboundLogSummaryPage(int page, int size, String materialCode, String batchNo, String orderNo);
    
    /**
     * 导出流水数据
     */
    List<TapeStockLog> exportStockLog(String type, String materialCode, String startDate, String endDate);

    /**
     * 历史分切成品库存合并：将“每卷一条”的旧数据按申请单聚合为单条多卷库存
     */
    Map<String, Object> mergeHistoricalSlittingFinishedStock();

    /**
     * 历史采购入库纠偏：将误入胶带仓的化工/薄膜来料迁移到原料仓
     */
    Map<String, Object> migrateMisroutedPurchaseInboundToRawWarehouse(String auditor);
}
