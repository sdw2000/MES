package com.fine.service.stock;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.fine.model.stock.ChemicalStock;
import com.fine.model.stock.ChemicalStockDetail;
import com.fine.model.stock.ChemicalStockOut;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * 化工原料库存服务接口
 * @author Fine
 * @date 2026-01-15
 */
public interface ChemicalStockService {
    
    /**
     * 按类型查询化工库存
     * @param chemicalType 化工类型
     * @return 化工库存列表
     */
    List<ChemicalStock> getByType(String chemicalType);
    
    /**
     * 查询所有化工库存
     * @return 化工库存列表
     */
    List<ChemicalStock> getAllChemicalStock();

    /**
     * 分页查询化工库存
     * @param current 当前页（从1开始）
     * @param size 每页大小
     * @param chemicalType 化工类型筛选（可选）
        * @param materialCode 料号筛选（可选，支持模糊匹配）
        * @param sortField 排序字段（可选，前端字段名）
        * @param sortOrder 排序方向（ascending/descending）
     * @return 分页结果
     */
        IPage<ChemicalStock> getChemicalStockPage(long current,
                                         long size,
                                         String chemicalType,
                                         String materialCode,
                                         String sortField,
                                         String sortOrder);

        /**
        * 查询化工库存统计（全表聚合，非当前页）
        * @param chemicalType 化工类型筛选（可选）
        * @param materialCode 料号筛选（可选，支持模糊匹配）
        * @return 统计结果
        */
        Map<String, Object> getChemicalStockStatistics(String chemicalType, String materialCode);
    
    /**
     * 根据ID查询化工库存
     * @param id 库存ID
     * @return 化工库存
     */
    ChemicalStock getById(Long id);

    /**
     * 更新化工库存主表（用于初始化维护）
     * @param id 库存ID
     * @param stock 更新字段
     * @return 更新后的库存
     */
    ChemicalStock updateStock(Long id, ChemicalStock stock);
    
    /**
     * 查询化工库存明细
     * @param chemicalStockId 化工库存ID
     * @return 明细列表
     */
    List<ChemicalStockDetail> getDetailsByChemicalStockId(Long chemicalStockId);
    
    /**
     * 查询可用的化工明细
     * @param chemicalStockId 化工库存ID
     * @return 可用明细列表
     */
    List<ChemicalStockDetail> getAvailableDetails(Long chemicalStockId);

    /**
     * 新增化工库存明细
     * @param chemicalStockId 库存ID
     * @param detail 明细
     * @return 新增后的明细
     */
    ChemicalStockDetail createDetail(Long chemicalStockId, ChemicalStockDetail detail);

    /**
     * 更新化工库存明细
     * @param chemicalStockId 库存ID
     * @param detailId 明细ID
     * @param detail 明细
     * @return 更新后的明细
     */
    ChemicalStockDetail updateDetail(Long chemicalStockId, Long detailId, ChemicalStockDetail detail);

    /**
     * 删除化工库存明细
     * @param chemicalStockId 库存ID
     * @param detailId 明细ID
     * @return 是否成功
     */
    boolean deleteDetail(Long chemicalStockId, Long detailId);
    
    /**
     * 查询即将过期的化工原料
     * @param days 天数（如30天内）
     * @return 明细列表
     */
    List<ChemicalStockDetail> getExpiringSoon(Integer days);
    
    /**
     * 锁定化工库存
     * @param chemicalStockId 化工库存ID
     * @param lockQuantity 锁定数量
     * @param detailIds 明细ID列表
     * @return 是否成功
     */
    boolean lockStock(Long chemicalStockId, Integer lockQuantity, List<Long> detailIds);
    
    /**
     * 解锁化工库存
     * @param chemicalStockId 化工库存ID
     * @param unlockQuantity 解锁数量
     * @param detailIds 明细ID列表
     * @return 是否成功
     */
    boolean unlockStock(Long chemicalStockId, Integer unlockQuantity, List<Long> detailIds);
    
    /**
     * 化工出库
     * @param chemicalStockOut 出库记录
     * @param detailIds 明细ID列表
     * @return 是否成功
     */
    boolean outbound(ChemicalStockOut chemicalStockOut, List<Long> detailIds);
    
    /**
     * 查询化工出库记录
     * @param scheduleId 排程ID
     * @return 出库记录列表
     */
    List<ChemicalStockOut> getOutboundByScheduleId(Long scheduleId);

    /**
     * 导入化工库存汇总Excel
     * @param file Excel文件
     * @return 导入结果
     */
    Map<String, Object> importExcel(MultipartFile file);

    /**
     * 导入化工库存Excel（可选：导入前先清空）
     * @param file Excel文件
     * @param clearBeforeImport 是否导入前先清空化工库存
     * @return 导入结果
     */
    Map<String, Object> importExcel(MultipartFile file, boolean clearBeforeImport);

    /**
     * 清空化工库存数据（用于重新盘点后全量重导）
     * @param clearOutboundRecords 是否清空出库记录
     * @return 清空结果统计
     */
    Map<String, Object> clearForReimport(boolean clearOutboundRecords);
}
