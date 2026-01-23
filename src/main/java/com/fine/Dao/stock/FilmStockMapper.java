package com.fine.Dao.stock;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fine.model.stock.FilmStock;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.util.List;

/**
 * 薄膜库存总量Mapper接口
 * @author Fine
 * @date 2026-01-15
 */
@Mapper
public interface FilmStockMapper extends BaseMapper<FilmStock> {
    
    /**
     * 按规格查询薄膜库存
     * @param thickness 厚度(μm)
     * @param width 宽度(mm)
     * @return 薄膜库存列表
     */
    List<FilmStock> selectBySpec(@Param("thickness") Integer thickness, @Param("width") Integer width);
    
    /**
     * 按状态查询薄膜库存
     * @param status 状态
     * @return 薄膜库存列表
     */
    List<FilmStock> selectByStatus(@Param("status") String status);
    
    /**
     * 锁定薄膜库存
     * @param id 库存ID
     * @param lockArea 锁定面积
     * @param lockRolls 锁定卷数
     * @return 影响行数
     */
    int lockStock(@Param("id") Long id, @Param("lockArea") BigDecimal lockArea, @Param("lockRolls") Integer lockRolls);
    
    /**
     * 解锁薄膜库存
     * @param id 库存ID
     * @param unlockArea 解锁面积
     * @param unlockRolls 解锁卷数
     * @return 影响行数
     */
    int unlockStock(@Param("id") Long id, @Param("unlockArea") BigDecimal unlockArea, @Param("unlockRolls") Integer unlockRolls);
    
    /**
     * 扣减库存（出库）
     * @param id 库存ID
     * @param outArea 出库面积
     * @param outRolls 出库卷数
     * @return 影响行数
     */
    int deductStock(@Param("id") Long id, @Param("outArea") BigDecimal outArea, @Param("outRolls") Integer outRolls);
}
