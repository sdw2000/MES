package com.fine.Dao.stock;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fine.model.stock.StockFlowLog;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 统一库存流水Mapper
 */
@org.apache.ibatis.annotations.Mapper
public interface StockFlowLogMapper extends BaseMapper<StockFlowLog> {

    /**
     * 分页查询库存流水
     */
    @Select("<script>" +
            "SELECT * FROM stock_flow_log WHERE 1=1 " +
            "<if test='stockType != null and stockType != \"\"'> AND stock_type = #{stockType} </if> " +
            "<if test='materialCode != null and materialCode != \"\"'> AND material_code LIKE CONCAT('%', #{materialCode}, '%') </if> " +
            "<if test='batchNo != null and batchNo != \"\"'> AND batch_no LIKE CONCAT('%', #{batchNo}, '%') </if> " +
            "<if test='type != null and type != \"\"'> AND type = #{type} </if> " +
            "<if test='refNo != null and refNo != \"\"'> AND ref_no LIKE CONCAT('%', #{refNo}, '%') </if> " +
            "<if test='beginTime != null and beginTime != \"\"'> AND create_time &gt;= #{beginTime} </if> " +
            "<if test='endTime != null and endTime != \"\"'> AND create_time &lt;= #{endTime} </if> " +
            "ORDER BY create_time DESC " +
            "</script>")
    IPage<StockFlowLog> selectPage(Page<StockFlowLog> page,
                                   @Param("stockType") String stockType,
                                   @Param("materialCode") String materialCode,
                                   @Param("batchNo") String batchNo,
                                   @Param("type") String type,
                       @Param("refNo") String refNo,
                       @Param("beginTime") String beginTime,
                       @Param("endTime") String endTime);

    /**
     * 根据库存ID查询流水
     */
    @Select("SELECT * FROM stock_flow_log WHERE stock_type = #{stockType} AND stock_id = #{stockId} ORDER BY create_time DESC")
    List<StockFlowLog> selectByStock(@Param("stockType") String stockType, @Param("stockId") Long stockId);
}