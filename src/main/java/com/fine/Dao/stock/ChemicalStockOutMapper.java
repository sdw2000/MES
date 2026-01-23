package com.fine.Dao.stock;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fine.model.stock.ChemicalStockOut;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 化工原料出库记录Mapper接口
 * @author Fine
 * @date 2026-01-15
 */
@Mapper
public interface ChemicalStockOutMapper extends BaseMapper<ChemicalStockOut> {
    
    /**
     * 按化工库存ID查询出库记录
     * @param chemicalStockId 化工库存ID
     * @return 出库记录列表
     */
    List<ChemicalStockOut> selectByChemicalStockId(@Param("chemicalStockId") Long chemicalStockId);
    
    /**
     * 按排程ID查询出库记录
     * @param scheduleId 排程ID
     * @return 出库记录列表
     */
    List<ChemicalStockOut> selectByScheduleId(@Param("scheduleId") Long scheduleId);
    
    /**
     * 按涂布任务ID查询出库记录
     * @param coatingTaskId 涂布任务ID
     * @return 出库记录
     */
    ChemicalStockOut selectByCoatingTaskId(@Param("coatingTaskId") Long coatingTaskId);
}
