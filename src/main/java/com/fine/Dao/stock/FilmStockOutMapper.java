package com.fine.Dao.stock;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fine.model.stock.FilmStockOut;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 薄膜出库记录Mapper接口
 * @author Fine
 * @date 2026-01-15
 */
@Mapper
public interface FilmStockOutMapper extends BaseMapper<FilmStockOut> {
    
    /**
     * 按薄膜库存ID查询出库记录
     * @param filmStockId 薄膜库存ID
     * @return 出库记录列表
     */
    List<FilmStockOut> selectByFilmStockId(@Param("filmStockId") Long filmStockId);
    
    /**
     * 按排程ID查询出库记录
     * @param scheduleId 排程ID
     * @return 出库记录列表
     */
    List<FilmStockOut> selectByScheduleId(@Param("scheduleId") Long scheduleId);
    
    /**
     * 按涂布任务ID查询出库记录
     * @param coatingTaskId 涂布任务ID
     * @return 出库记录
     */
    FilmStockOut selectByCoatingTaskId(@Param("coatingTaskId") Long coatingTaskId);
}
