package com.fine.Dao.stock;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fine.modle.stock.TapeRoll;
import org.apache.ibatis.annotations.*;

import java.math.BigDecimal;
import java.util.List;

/**
 * 库存详情（每卷）Mapper
 */
@Mapper
public interface TapeRollMapper extends BaseMapper<TapeRoll> {

    /** 根据ID查询（联表补充展示字段） */
    @Select("SELECT r.*, s.material_code AS materialCode, s.batch_no AS batchNo, s.spec_desc AS specDesc, s.roll_type AS rollType " +
            "FROM tape_stock_rolls r JOIN tape_stock s ON r.stock_id = s.id WHERE r.id = #{id}")
    TapeRoll selectWithStock(@Param("id") Long id);

    /** 查询某料号下可用的卷（长度>=订单长度，面积>0），FIFO排序 */
    @Select("SELECT r.*, s.material_code AS materialCode, s.batch_no AS batchNo, s.spec_desc AS specDesc, s.roll_type AS rollType, s.sequence_no AS fifoOrder " +
            "FROM tape_stock_rolls r JOIN tape_stock s ON r.stock_id = s.id " +
            "WHERE s.material_code = #{materialCode} AND r.available_area > 0 " +
            "  AND (#{orderLengthM} IS NULL OR r.length >= #{orderLengthM}) " +
            "ORDER BY s.sequence_no ASC, r.id ASC LIMIT #{limit}")
    List<TapeRoll> selectAvailableByMaterial(@Param("materialCode") String materialCode,
                                             @Param("orderLengthM") BigDecimal orderLengthM,
                                             @Param("limit") int limit);

    /** 更新卷的预留面积（乐观锁） */
    @Update("UPDATE tape_stock_rolls SET reserved_area = COALESCE(reserved_area,0) + #{addArea}, " +
            "available_area = COALESCE(available_area,0) - #{addArea}, version = COALESCE(version,0) + 1 " +
            "WHERE id = #{id} AND version = #{version}")
    int updateReservedAreaWithVersion(@Param("id") Long id,
                                      @Param("addArea") BigDecimal addArea,
                                      @Param("version") Integer version);

    /** 释放卷的预留面积（乐观锁） */
    @Update("UPDATE tape_stock_rolls SET reserved_area = COALESCE(reserved_area,0) - #{releaseArea}, " +
            "available_area = COALESCE(available_area,0) + #{releaseArea}, version = COALESCE(version,0) + 1 " +
            "WHERE id = #{id} AND version = #{version}")
    int releaseReservedAreaWithVersion(@Param("id") Long id,
                                       @Param("releaseArea") BigDecimal releaseArea,
                                       @Param("version") Integer version);

    /** 分页联表查询，返回卷信息+批次字段，可选过滤 */
    @Select({
            "<script>",
            "SELECT r.*, s.material_code AS materialCode, s.batch_no AS batchNo, s.spec_desc AS specDesc, s.roll_type AS rollType, s.sequence_no AS fifoOrder",
            "FROM tape_stock_rolls r JOIN tape_stock s ON r.stock_id = s.id",
            "WHERE 1=1",
            "<if test='materialCode != null and materialCode != \"\"'> AND s.material_code = #{materialCode}</if>",
            "<if test='batchNo != null and batchNo != \"\"'> AND s.batch_no = #{batchNo}</if>",
            "<if test='rollType != null and rollType != \"\"'> AND s.roll_type = #{rollType}</if>",
            "<if test='availableOnly != null and availableOnly'> AND r.available_area > 0</if>",
            "ORDER BY s.sequence_no ASC, r.id ASC",
            "</script>"
    })
    Page<TapeRoll> selectPageWithStock(Page<TapeRoll> page,
                                       @Param("materialCode") String materialCode,
                                       @Param("batchNo") String batchNo,
                                       @Param("rollType") String rollType,
                                       @Param("availableOnly") Boolean availableOnly);
}
