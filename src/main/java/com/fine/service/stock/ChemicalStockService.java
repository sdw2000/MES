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
     * @return 分页结果
     */
    IPage<ChemicalStock> getChemicalStockPage(long current, long size, String chemicalType);
    
    /**
     * 根据ID查询化工库存
     * @param id 库存ID
     * @return 化工库存
     */
    ChemicalStock getById(Long id);
    
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
}
