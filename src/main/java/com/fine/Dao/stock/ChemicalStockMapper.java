package com.fine.Dao.stock;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fine.model.stock.ChemicalStock;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 化工原料库存总量Mapper接口
 * @author Fine
 * @date 2026-01-15
 */
@Mapper
public interface ChemicalStockMapper extends BaseMapper<ChemicalStock> {
    
    /**
     * 按化工类型查询库存
     * @param chemicalType 化工类型
     * @return 库存列表
     */
    List<ChemicalStock> selectByType(@Param("chemicalType") String chemicalType);
    
    /**
     * 按状态查询库存
     * @param status 状态
     * @return 库存列表
     */
    List<ChemicalStock> selectByStatus(@Param("status") String status);
    
    /**
     * 锁定化工库存
     * @param id 库存ID
     * @param lockQuantity 锁定数量
     * @return 影响行数
     */
    int lockStock(@Param("id") Long id, @Param("lockQuantity") Integer lockQuantity);
    
    /**
     * 解锁化工库存
     * @param id 库存ID
     * @param unlockQuantity 解锁数量
     * @return 影响行数
     */
    int unlockStock(@Param("id") Long id, @Param("unlockQuantity") Integer unlockQuantity);
    
    /**
     * 扣减库存（出库）
     * @param id 库存ID
     * @param outQuantity 出库数量
     * @return 影响行数
     */
    int deductStock(@Param("id") Long id, @Param("outQuantity") Integer outQuantity);

    /**
     * 到货入库：增加总量和可用量
     */
    int addStock(@Param("id") Long id, @Param("inQuantity") Integer inQuantity);
}
