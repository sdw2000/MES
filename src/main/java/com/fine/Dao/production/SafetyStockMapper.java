package com.fine.Dao.production;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.fine.model.production.SafetyStock;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * 安全库存Mapper
 */
@Mapper
public interface SafetyStockMapper extends BaseMapper<SafetyStock> {

    /**
     * 分页查询安全库存列表（MyBatis-Plus分页）
     */
    @Select("<script>" +
            "SELECT ss.id, ss.material_code, ss.product_name as material_name, " +
            "ss.safety_qty, ss.safety_sqm as safety_area, ss.reorder_point, " +
            "ss.max_stock as max_qty, " +
            "IFNULL(ts.total_rolls, 0) as current_qty, " +
            "IFNULL(ts.total_sqm, 0) as current_area, " +
            "ss.status, ss.remark, ss.create_time, ss.update_time " +
            "FROM safety_stock ss " +
            "LEFT JOIN (SELECT material_code, SUM(total_rolls) as total_rolls, SUM(total_sqm) as total_sqm FROM tape_stock WHERE status = 1 GROUP BY material_code) ts ON ss.material_code = ts.material_code " +
            "WHERE ss.status = 1 " +
            "<if test='materialCode != null and materialCode != \"\"'>" +
            "AND ss.material_code LIKE CONCAT('%', #{materialCode}, '%') " +
            "</if>" +
            "<if test='lowStockOnly == true'>" +
            "AND IFNULL(ts.total_rolls, 0) &lt;= ss.reorder_point " +
            "</if>" +
            "ORDER BY ss.material_code" +
            "</script>")
    IPage<SafetyStock> selectSafetyStockPageList(IPage<SafetyStock> page,
                                                  @Param("materialCode") String materialCode,
                                                  @Param("stockType") String stockType,
                                                  @Param("lowStockOnly") Boolean lowStockOnly);

    /**
     * 查询安全库存列表（带当前库存信息，从tape_stock实时聚合）
     */
    @Select("<script>" +
            "SELECT ss.id, ss.material_code, ss.product_name as material_name, " +
            "ss.safety_qty, ss.safety_sqm as safety_area, ss.reorder_point, " +
            "ss.max_stock as max_qty, " +
            "IFNULL(ts.total_rolls, 0) as current_qty, " +
            "IFNULL(ts.total_sqm, 0) as current_area, " +
            "ss.status, ss.remark, ss.create_time, ss.update_time " +
            "FROM safety_stock ss " +
            "LEFT JOIN (SELECT material_code, SUM(total_rolls) as total_rolls, SUM(total_sqm) as total_sqm FROM tape_stock WHERE status = 1 GROUP BY material_code) ts ON ss.material_code = ts.material_code " +
            "WHERE ss.status = 1 " +
            "<if test='materialCode != null and materialCode != \"\"'>" +
            "AND ss.material_code LIKE CONCAT('%', #{materialCode}, '%') " +
            "</if>" +
            "<if test='lowStockOnly == true'>" +
            "AND IFNULL(ts.total_rolls, 0) &lt;= ss.reorder_point " +
            "</if>" +
            "ORDER BY ss.material_code" +
            "</script>")
    List<SafetyStock> selectSafetyStockList(@Param("materialCode") String materialCode,
                                            @Param("stockType") String stockType,
                                            @Param("lowStockOnly") Boolean lowStockOnly);

    /**
     * 根据料号查询（带实时库存）
     */
    @Select("SELECT ss.id, ss.material_code, ss.product_name as material_name, " +
            "ss.safety_qty, ss.safety_sqm as safety_area, ss.reorder_point, " +
            "ss.max_stock as max_qty, " +
            "IFNULL(ts.total_rolls, 0) as current_qty, " +
            "IFNULL(ts.total_sqm, 0) as current_area, " +
            "ss.status, ss.remark " +
            "FROM safety_stock ss " +
            "LEFT JOIN (SELECT material_code, SUM(total_rolls) as total_rolls, SUM(total_sqm) as total_sqm FROM tape_stock WHERE status = 1 GROUP BY material_code) ts ON ss.material_code = ts.material_code " +
            "WHERE ss.material_code = #{materialCode} AND ss.status = 1")
    SafetyStock selectByMaterialCode(@Param("materialCode") String materialCode);

    /**
     * 检查是否已存在
     */
    @Select("SELECT COUNT(1) FROM safety_stock WHERE material_code = #{materialCode} AND id != #{excludeId} AND status = 1")
    int checkExists(@Param("materialCode") String materialCode,
                   @Param("excludeId") Long excludeId);    /**
     * 查询需要补货的产品列表（从tape_stock实时聚合库存）
     */
    @Select("SELECT ss.id, ss.material_code, ss.product_name as material_name, " +
            "ss.safety_qty, ss.safety_sqm as safety_area, ss.reorder_point, " +
            "ss.max_stock as max_qty, " +
            "IFNULL(ts.total_rolls, 0) as current_qty, " +
            "IFNULL(ts.total_sqm, 0) as current_area, " +
            "ss.status, ss.remark " +
            "FROM safety_stock ss " +
            "LEFT JOIN (" +
            "  SELECT material_code, SUM(total_rolls) as total_rolls, SUM(total_sqm) as total_sqm " +
            "  FROM tape_stock WHERE status = 1 GROUP BY material_code" +
            ") ts ON ss.material_code = ts.material_code " +
            "WHERE ss.status = 1 " +
            "AND IFNULL(ts.total_rolls, 0) <= ss.reorder_point " +
            "ORDER BY ss.material_code")
    List<SafetyStock> selectNeedRestock();
}
