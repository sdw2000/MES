package com.fine.Dao.stock;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fine.modle.stock.TapeStock;
import org.apache.ibatis.annotations.*;

import java.math.BigDecimal;
import java.util.List;

/**
 * 胶带库存Mapper
 */
@Mapper
public interface TapeStockMapper extends BaseMapper<TapeStock> {
    
    /**
     * 按料号汇总库存
     */
    @Select("SELECT material_code, product_name, " +
            "SUM(total_rolls) as total_rolls, " +
            "SUM(total_sqm) as total_sqm, " +
            "COALESCE(SUM(available_area),0) as available_area, " +
            "COALESCE(SUM(reserved_area),0) as reserved_area, " +
            "COALESCE(SUM(consumed_area),0) as consumed_area " +
            "FROM tape_stock WHERE status = 1 " +
            "GROUP BY material_code, product_name " +
            "ORDER BY material_code")
    List<TapeStock> selectSummaryByMaterial();

    /**
     * 归一化面积字段，填充null为0，并在可用面积缺失时重新计算
     */
    @Update("UPDATE tape_stock " +
            "SET total_sqm = CASE " +
            "        WHEN (total_sqm IS NULL OR total_sqm = 0) " +
            "             AND width IS NOT NULL " +
            "             AND (current_length IS NOT NULL OR length IS NOT NULL) " +
            "             AND total_rolls IS NOT NULL " +
            "          THEN (width / 1000.0) * COALESCE(current_length, length) * total_rolls " +
            "        ELSE total_sqm END, " +
            "    reserved_area = COALESCE(reserved_area, 0), " +
            "    consumed_area = COALESCE(consumed_area, 0), " +
            "    available_area = CASE " +
            "        WHEN available_area IS NULL OR available_area = 0 " +
            "          THEN COALESCE(total_sqm, 0) - COALESCE(consumed_area,0) - COALESCE(reserved_area,0) " +
            "        ELSE available_area END " +
            "WHERE status = 1")
    int normalizeAreaFields();
    
    /**
     * 查询某料号下所有批次（按生产日期升序，用于FIFO）
     */
    @Select("SELECT * FROM tape_stock " +
            "WHERE material_code = #{materialCode} AND status = 1 AND total_rolls > 0 " +
            "ORDER BY prod_date ASC, id ASC")
    List<TapeStock> selectByMaterialCodeFIFO(@Param("materialCode") String materialCode);
    
    /**
     * 根据批次号查询
     */
    @Select("SELECT * FROM tape_stock WHERE batch_no = #{batchNo}")
    TapeStock selectByBatchNo(@Param("batchNo") String batchNo);
    
    /**
     * 更新库存卷数
     */
    @Update("UPDATE tape_stock SET total_rolls = #{totalRolls}, " +
            "total_sqm = #{totalSqm}, update_time = NOW() " +
            "WHERE id = #{id}")
    int updateRolls(@Param("id") Long id, @Param("totalRolls") Integer totalRolls, 
                    @Param("totalSqm") java.math.BigDecimal totalSqm);

    /**
     * 查询可用的母卷库存（按规格匹配，FIFO排序）
     * @param thickness 厚度(μm)
     * @param minWidth 最小宽度(mm)
     */
    @Select("<script>" +
            "SELECT * FROM tape_stock " +
            "WHERE status = 1 AND total_rolls > 0 " +
            "AND (stock_type = 'jumbo' OR stock_type IS NULL) " +
            "<if test='thickness != null'> AND thickness = #{thickness} </if>" +
            "<if test='minWidth != null'> AND width >= #{minWidth} </if>" +
            "ORDER BY prod_date ASC, id ASC" +
            "</script>")
    List<TapeStock> selectAvailableJumboStock(@Param("thickness") Integer thickness,
                                               @Param("minWidth") Integer minWidth);

    /**
     * 按规格查询可用库存（母卷或支料）
     */
    @Select("<script>" +
            "SELECT * FROM tape_stock " +
            "WHERE status = 1 AND total_rolls > 0 " +
            "<if test='stockType != null'> AND stock_type = #{stockType} </if>" +
            "<if test='thickness != null'> AND thickness = #{thickness} </if>" +
            "<if test='width != null'> AND width = #{width} </if>" +
            "<if test='materialCode != null'> AND material_code = #{materialCode} </if>" +
            "ORDER BY prod_date ASC, id ASC" +
            "</script>")
    List<TapeStock> selectAvailableBySpec(@Param("stockType") String stockType,
                                          @Param("thickness") Integer thickness,
                                          @Param("width") Integer width,
                                          @Param("materialCode") String materialCode);

    /**
     * 第1级：查询成品库存（料号+颜色+厚度+宽度精确匹配，长度≥需要）
     */
    @Select("<script>" +
            "SELECT * FROM tape_stock " +
            "WHERE status = 1 AND total_rolls > 0 " +
            "AND material_code = #{materialCode} " +
            "AND stock_type = 'finished' " +
            "<if test='thickness != null'> AND thickness = #{thickness} </if>" +
            "<if test='width != null'> AND width >= #{width} </if>" +
            "ORDER BY prod_date ASC, id ASC " +
            "LIMIT #{limit}" +
            "</script>")
    List<TapeStock> selectFinishedByMaterial(@Param("materialCode") String materialCode,
                                             @Param("thickness") Integer thickness,
                                             @Param("width") Integer width,
                                             @Param("limit") int limit);
    
    /**
     * 第2级：查询复好卷库存（料号+颜色+厚度+宽度，长度≥需要）
     */
    @Select("<script>" +
            "SELECT * FROM tape_stock " +
            "WHERE status = 1 AND total_rolls > 0 " +
            "AND material_code = #{materialCode} " +
            "AND stock_type = 'rewound' " +
            "<if test='thickness != null'> AND thickness = #{thickness} </if>" +
            "<if test='width != null'> AND width >= #{width} </if>" +
            "ORDER BY prod_date ASC, id ASC " +
            "LIMIT #{limit}" +
            "</script>")
    List<TapeStock> selectRewindByMaterial(@Param("materialCode") String materialCode,
                                           @Param("thickness") Integer thickness,
                                           @Param("width") Integer width,
                                           @Param("limit") int limit);
    
    /**
     * 第3级：查询母卷库存（料号+颜色+厚度，宽度≥需要）
     */
    @Select("<script>" +
            "SELECT * FROM tape_stock " +
            "WHERE status = 1 AND total_rolls > 0 " +
            "AND material_code = #{materialCode} " +
            "AND (stock_type = 'jumbo' OR stock_type IS NULL) " +
            "<if test='thickness != null'> AND thickness = #{thickness} </if>" +
            "<if test='width != null'> AND width >= #{width} </if>" +
            "ORDER BY prod_date ASC, id ASC " +
            "LIMIT #{limit}" +
            "</script>")
    List<TapeStock> selectJumboByMaterial(@Param("materialCode") String materialCode,
                                          @Param("thickness") Integer thickness,
                                          @Param("width") Integer width,
                                          @Param("limit") int limit);
    
    /**
     * 查询同料号所有库存的总平方数（用于计算库存天数）
     */
    @Select("SELECT COALESCE(SUM(total_sqm), 0) as total_sqm FROM tape_stock " +
            "WHERE material_code = #{materialCode} AND status = 1 AND total_rolls > 0")
    BigDecimal selectTotalSqmByMaterial(@Param("materialCode") String materialCode);

    /**
     * 按料号与卷类型汇总可用面积（兼容 roll_type / reel_type / stock_type 字段）
     */
    @Select("SELECT COALESCE(SUM(available_area), 0) FROM tape_stock " +
            "WHERE status = 1 AND material_code = #{materialCode} " +
            "AND (reel_type = #{rollType} OR roll_type = #{rollType} OR stock_type = #{stockType})")
    BigDecimal selectAvailableAreaByMaterialAndType(@Param("materialCode") String materialCode,
                                                    @Param("rollType") String rollType,
                                                    @Param("stockType") String stockType);

    /**
     * 复好卷库存超过24小时的可用面积
     */
    @Select("SELECT COALESCE(SUM(available_area), 0) FROM tape_stock " +
            "WHERE status = 1 AND material_code = #{materialCode} " +
            "AND stock_type = 'rewound' " +
            "AND prod_date IS NOT NULL " +
            "AND prod_date <= DATE_SUB(CURDATE(), INTERVAL 1 DAY)")
    BigDecimal selectAvailableRewoundArea24h(@Param("materialCode") String materialCode);
    
    // ==========================================
    // 库存锁定机制方法（新增）
    // ==========================================
    
    /**
     * 查询可用复卷（扣除已锁定部分）
     * 按优先级排序：先进先出 + 面积大优先
     */
    @Select("<script>" +
            "SELECT ts.id, ts.code as materialCode, ts.product_name as productName, " +
            "       ts.total_sqm as totalSqm, " +
            "       (ts.total_sqm - COALESCE(ts.reserved_area, 0) - COALESCE(ts.consumed_area, 0)) as availableArea " +
            "FROM tape_stock ts " +
            "WHERE ts.reel_type = '复卷' " +
            "  AND ts.thickness = #{thickness} " +
            "  AND ts.width = #{width} " +
            "  AND ts.status = 1 " +
            "  AND (ts.total_sqm - COALESCE(ts.reserved_area, 0) - COALESCE(ts.consumed_area, 0)) >= #{minArea} " +
            "ORDER BY ts.prod_date ASC, ts.available_area DESC " +
            "LIMIT #{limit}" +
            "</script>")
    List<TapeStock> findAvailableReels(@Param("thickness") Integer thickness,
                                       @Param("width") Integer width,
                                       @Param("minArea") BigDecimal minArea,
                                       @Param("limit") int limit);
    
    /**
     * 查询可用母卷（扣除已锁定部分）
     * 按优先级排序：先进先出 + 面积大优先
     */
    @Select("<script>" +
            "SELECT ts.id, ts.code as materialCode, ts.product_name as productName, " +
            "       ts.total_sqm as totalSqm, " +
            "       (ts.total_sqm - COALESCE(ts.reserved_area, 0) - COALESCE(ts.consumed_area, 0)) as availableArea " +
            "FROM tape_stock ts " +
            "WHERE ts.reel_type = '母卷' " +
            "  AND ts.thickness = #{thickness} " +
            "  AND ts.width >= #{width} " +
            "  AND ts.status = 1 " +
            "  AND (ts.total_sqm - COALESCE(ts.reserved_area, 0) - COALESCE(ts.consumed_area, 0)) >= #{minArea} " +
            "ORDER BY ts.prod_date ASC, ts.available_area DESC " +
            "LIMIT #{limit}" +
            "</script>")
    List<TapeStock> findAvailableMotherReels(@Param("thickness") Integer thickness,
                                             @Param("width") Integer width,
                                             @Param("minArea") BigDecimal minArea,
                                             @Param("limit") int limit);
    
    /**
     * 更新库存的预留和消耗面积（乐观锁）
     */
    @Update("UPDATE tape_stock " +
            "SET available_area = COALESCE(available_area,0) - #{reservedArea}, " +
            "    reserved_area = COALESCE(reserved_area,0) + #{reservedArea}, " +
            "    version = version + 1, " +
            "    lock_updated_time = NOW() " +
            "WHERE id = #{id} AND version = #{version}")
    int updateReservedArea(@Param("id") Long id,
                           @Param("reservedArea") BigDecimal reservedArea,
                           @Param("version") Integer version);
    
    /**
     * 更新库存的消耗面积（生产领料时扣减库存）
     */
    @Update("UPDATE tape_stock " +
            "SET total_sqm = total_sqm - #{consumedArea}, " +
            "    consumed_area = consumed_area + #{consumedArea}, " +
            "    reserved_area = reserved_area - #{consumedArea}, " +
            "    version = version + 1, " +
            "    lock_updated_time = NOW() " +
            "WHERE id = #{id} AND version = #{version}")
    int updateConsumedArea(@Param("id") Long id,
                          @Param("consumedArea") BigDecimal consumedArea,
                          @Param("version") Integer version);
    
    /**
     * 释放锁定（恢复库存）
     */
    @Update("UPDATE tape_stock " +
            "SET available_area = COALESCE(available_area,0) + #{releaseArea}, " +
            "    reserved_area = COALESCE(reserved_area,0) - #{releaseArea}, " +
            "    version = version + 1, " +
            "    lock_updated_time = NOW() " +
            "WHERE id = #{id} AND version = #{version}")
    int releaseLock(@Param("id") Long id,
                    @Param("releaseArea") BigDecimal releaseArea,
                    @Param("version") Integer version);

    /**
     * 退料：归还已扣减的消耗面积，恢复总面积和可用面积
     */
    @Update("UPDATE tape_stock " +
            "SET total_sqm = total_sqm + #{returnArea}, " +
            "    consumed_area = consumed_area - #{returnArea}, " +
            "    available_area = COALESCE(available_area,0) + #{returnArea}, " +
            "    version = version + 1, " +
            "    lock_updated_time = NOW() " +
            "WHERE id = #{id} AND version = #{version}")
    int returnConsumedArea(@Param("id") Long id,
                          @Param("returnArea") BigDecimal returnArea,
                          @Param("version") Integer version);
    
    /**
     * 初始化物料的可用面积（从总面积）
     */
    @Update("UPDATE tape_stock " +
            "SET available_area = total_sqm - COALESCE(consumed_area, 0) " +
            "WHERE available_area = 0 OR available_area IS NULL")
    int initializeAvailableArea();

    /**
     * 按物料代码查询所有库存（FIFO排序）
     */
    @Select("SELECT * FROM tape_stock " +
            "WHERE material_code = #{materialCode} AND status = 1 AND total_rolls > 0 " +
            "ORDER BY sequence_no ASC, id ASC")
    List<TapeStock> selectByMaterialCode(@Param("materialCode") String materialCode);

    /**
     * 更新预留面积（带乐观锁）
     */
    @Update("UPDATE tape_stock " +
            "SET reserved_area = COALESCE(reserved_area,0) + #{addArea}, " +
            "    available_area = COALESCE(available_area,0) - #{addArea}, " +
            "    version = version + 1 " +
            "WHERE id = #{id} AND version = #{version}")
    int updateReservedAreaWithVersion(@Param("id") Long id,
                                      @Param("addArea") BigDecimal addArea,
                                      @Param("version") Integer version);
}
