package com.fine.Dao.stock;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fine.model.stock.ChemicalStockDetail;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 化工原料库存明细Mapper接口
 * @author Fine
 * @date 2026-01-15
 */
@Mapper
public interface ChemicalStockDetailMapper extends BaseMapper<ChemicalStockDetail> {
    
    /**
     * 按化工库存ID查询明细
     * @param chemicalStockId 化工库存ID
     * @return 明细列表
     */
    List<ChemicalStockDetail> selectByChemicalStockId(@Param("chemicalStockId") Long chemicalStockId);
    
    /**
     * 按状态查询可用明细
     * @param chemicalStockId 化工库存ID
     * @param status 状态
     * @return 明细列表
     */
    List<ChemicalStockDetail> selectByStatus(@Param("chemicalStockId") Long chemicalStockId, @Param("status") String status);
    
    /**
     * 按批次号查询明细
     * @param batchNo 批次号
     * @return 明细列表
     */
    List<ChemicalStockDetail> selectByBatchNo(@Param("batchNo") String batchNo);
    
    /**
     * 查询即将过期的明细
     * @param days 天数（如30天内）
     * @return 明细列表
     */
    List<ChemicalStockDetail> selectExpiringSoon(@Param("days") Integer days);
    
    /**
     * 查询已过期的明细
     * @return 明细列表
     */
    List<ChemicalStockDetail> selectExpired();
    
    /**
     * 更新明细状态
     * @param id 明细ID
     * @param status 新状态
     * @return 影响行数
     */
    int updateStatus(@Param("id") Long id, @Param("status") String status);
    
    /**
     * 批量更新明细状态
     * @param ids 明细ID列表
     * @param status 新状态
     * @return 影响行数
     */
    int batchUpdateStatus(@Param("ids") List<Long> ids, @Param("status") String status);
}
