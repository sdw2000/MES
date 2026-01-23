package com.fine.Dao.production;

import com.fine.model.production.ProductionSchedule;
import org.apache.ibatis.annotations.*;
import java.util.List;
import java.util.Map;
import com.baomidou.mybatisplus.core.metadata.IPage;

/**
 * 排程主表Mapper
 */
@Mapper
public interface ProductionScheduleMapper {
    
    /**
     * 分页查询排程列表
     */
    @Select("<script>" +
            "SELECT * FROM production_schedule WHERE is_deleted = 0 " +
            "<if test='params.scheduleNo != null and params.scheduleNo != \"\"'>" +
            "AND schedule_no LIKE CONCAT('%', #{params.scheduleNo}, '%') " +
            "</if>" +
            "<if test='params.scheduleType != null and params.scheduleType != \"\"'>" +
            "AND schedule_type = #{params.scheduleType} " +
            "</if>" +
            "<if test='params.status != null and params.status != \"\"'>" +
            "AND status = #{params.status} " +
            "</if>" +
            "<if test='params.startDate != null'>" +
            "AND schedule_date &gt;= #{params.startDate} " +
            "</if>" +
            "<if test='params.endDate != null'>" +
            "AND schedule_date &lt;= #{params.endDate} " +
            "</if>" +
            "ORDER BY create_time DESC" +
            "</script>")
    IPage<ProductionSchedule> selectPageList(IPage<ProductionSchedule> page, @Param("params") Map<String, Object> params);

    /**
     * 查询排程列表 (Deprecated: Use selectPageList)
     */
    @Select("<script>" +
            "SELECT * FROM production_schedule WHERE is_deleted = 0 " +
            "<if test='scheduleNo != null and scheduleNo != \"\"'>" +
            "AND schedule_no LIKE CONCAT('%', #{scheduleNo}, '%') " +
            "</if>" +
            "<if test='scheduleType != null and scheduleType != \"\"'>" +
            "AND schedule_type = #{scheduleType} " +
            "</if>" +
            "<if test='status != null and status != \"\"'>" +
            "AND status = #{status} " +
            "</if>" +
            "<if test='startDate != null'>" +
            "AND schedule_date &gt;= #{startDate} " +
            "</if>" +
            "<if test='endDate != null'>" +
            "AND schedule_date &lt;= #{endDate} " +
            "</if>" +
            "ORDER BY create_time DESC" +
            "</script>")
    List<ProductionSchedule> selectList(Map<String, Object> params);
    
    /**
     * 根据ID查询
     */
    @Select("SELECT * FROM production_schedule WHERE id = #{id} AND is_deleted = 0")
    ProductionSchedule selectById(@Param("id") Long id);
    
    /**
     * 根据排程单号查询
     */
    @Select("SELECT * FROM production_schedule WHERE schedule_no = #{scheduleNo} AND is_deleted = 0")
    ProductionSchedule selectByScheduleNo(@Param("scheduleNo") String scheduleNo);
    
    /**
     * 生成排程单号
     */
    @Select("SELECT CONCAT('PS-', DATE_FORMAT(NOW(), '%Y%m%d'), '-', " +
            "LPAD(IFNULL(MAX(CAST(SUBSTRING(schedule_no, -3) AS UNSIGNED)), 0) + 1, 3, '0')) " +
            "FROM production_schedule WHERE schedule_no LIKE CONCAT('PS-', DATE_FORMAT(NOW(), '%Y%m%d'), '%')")
    String generateScheduleNo();
    
    /**
     * 插入排程
     */
    @Insert("INSERT INTO production_schedule (schedule_no, schedule_date, schedule_type, total_orders, " +
            "total_items, total_sqm, status, remark, create_by) VALUES " +
            "(#{scheduleNo}, #{scheduleDate}, #{scheduleType}, #{totalOrders}, #{totalItems}, " +
            "#{totalSqm}, #{status}, #{remark}, #{createBy})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(ProductionSchedule schedule);
    
    /**
     * 更新排程
     */
    @Update("<script>" +
            "UPDATE production_schedule SET " +
            "<if test='scheduleDate != null'>schedule_date = #{scheduleDate}, </if>" +
            "<if test='scheduleType != null'>schedule_type = #{scheduleType}, </if>" +
            "<if test='totalOrders != null'>total_orders = #{totalOrders}, </if>" +
            "<if test='totalItems != null'>total_items = #{totalItems}, </if>" +
            "<if test='totalSqm != null'>total_sqm = #{totalSqm}, </if>" +
            "<if test='status != null'>status = #{status}, </if>" +
            "<if test='confirmedBy != null'>confirmed_by = #{confirmedBy}, </if>" +
            "<if test='confirmedTime != null'>confirmed_time = #{confirmedTime}, </if>" +
            "<if test='remark != null'>remark = #{remark}, </if>" +
            "update_by = #{updateBy}, update_time = NOW() " +
            "WHERE id = #{id}" +
            "</script>")
    int update(ProductionSchedule schedule);
    
    /**
     * 逻辑删除
     */
    @Update("UPDATE production_schedule SET is_deleted = 1, update_by = #{updateBy}, update_time = NOW() WHERE id = #{id}")
    int deleteById(@Param("id") Long id, @Param("updateBy") String updateBy);
    
    /**
     * 统计各状态数量
     */
    @Select("SELECT status, COUNT(*) as count FROM production_schedule WHERE is_deleted = 0 GROUP BY status")
    List<Map<String, Object>> countByStatus();
    
    /**
     * 查询今日排程数
     */
    @Select("SELECT COUNT(*) FROM production_schedule WHERE is_deleted = 0 AND DATE(schedule_date) = CURDATE()")
    int countTodaySchedules();
    
    /**
     * 获取指定前缀的最大序号
     * @param prefix 前缀（如：SCH20260115）
     * @return 最大序号
     */
    @Select("SELECT MAX(CAST(SUBSTRING(schedule_no, LENGTH(#{prefix}) + 1) AS UNSIGNED)) " +
            "FROM production_schedule WHERE schedule_no LIKE CONCAT(#{prefix}, '%')")
    Integer getMaxSeqByPrefix(@Param("prefix") String prefix);
}
