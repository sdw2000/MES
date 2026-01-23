package com.fine.Dao.stock;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fine.model.stock.FilmStockDetail;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 薄膜库存明细Mapper接口
 * @author Fine
 * @date 2026-01-15
 */
@Mapper
public interface FilmStockDetailMapper extends BaseMapper<FilmStockDetail> {
    
    /**
     * 按薄膜库存ID查询明细
     * @param filmStockId 薄膜库存ID
     * @return 明细列表
     */
    List<FilmStockDetail> selectByFilmStockId(@Param("filmStockId") Long filmStockId);
    
    /**
     * 按状态查询可用明细
     * @param filmStockId 薄膜库存ID
     * @param status 状态
     * @return 明细列表
     */
    List<FilmStockDetail> selectByStatus(@Param("filmStockId") Long filmStockId, @Param("status") String status);
    
    /**
     * 按批次号查询明细
     * @param batchNo 批次号
     * @return 明细列表
     */
    List<FilmStockDetail> selectByBatchNo(@Param("batchNo") String batchNo);
    
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
