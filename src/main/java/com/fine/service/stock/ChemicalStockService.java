package com.fine.service.stock;

import com.fine.model.stock.ChemicalStock;
import com.fine.model.stock.ChemicalStockDetail;
import com.fine.model.stock.ChemicalStockOut;

import java.util.List;

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
}
