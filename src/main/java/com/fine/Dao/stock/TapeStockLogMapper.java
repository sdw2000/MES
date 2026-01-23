package com.fine.Dao.stock;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fine.modle.stock.TapeStockLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

/**
 * 库存流水Mapper
 */
@Mapper
public interface TapeStockLogMapper extends BaseMapper<TapeStockLog> {
    
    /**
     * 统计总记录数
     */
    @Select("SELECT COUNT(*) FROM tape_stock_log")
    long countAll();
}
