package com.fine.Dao.stock;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fine.modle.stock.ScheduleMaterialLock;
import org.apache.ibatis.annotations.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 排程物料锁定 Mapper
 */
@Mapper
public interface ScheduleMaterialLockMapper extends BaseMapper<ScheduleMaterialLock> {
    
    /**
     * 查询排程的所有锁定记录
     */
    @Select("SELECT * FROM schedule_material_lock " +
            "WHERE schedule_id = #{scheduleId} AND lock_status IN ('锁定中', '已领料', 'LOCKED', 'ALLOCATED', 'PICKED') " +
            "ORDER BY locked_time DESC")
    List<ScheduleMaterialLock> selectByScheduleId(@Param("scheduleId") Long scheduleId);
    
    /**
     * 查询某物料的被锁定情况（被哪些订单锁定了）
     */
    @Select("SELECT * FROM schedule_material_lock " +
            "WHERE tape_stock_id = #{tapeStockId} AND lock_status IN ('锁定中', '已领料', 'LOCKED', 'ALLOCATED', 'PICKED') " +
            "ORDER BY locked_time ASC")
    List<ScheduleMaterialLock> selectByTapeStockId(@Param("tapeStockId") Long tapeStockId);
    
    /**
     * 查询订单的锁定记录
     */
    @Select("SELECT * FROM schedule_material_lock " +
            "WHERE order_id = #{orderId} AND lock_status IN ('锁定中', '已领料', 'LOCKED', 'ALLOCATED', 'PICKED') " +
            "ORDER BY locked_time DESC")
    List<ScheduleMaterialLock> selectByOrderId(@Param("orderId") Long orderId);
    
    /**
     * 查询排程中某订单的锁定记录
     */
    @Select("SELECT * FROM schedule_material_lock " +
            "WHERE schedule_id = #{scheduleId} AND order_id = #{orderId} " +
            "ORDER BY locked_time DESC")
    List<ScheduleMaterialLock> selectByScheduleAndOrder(@Param("scheduleId") Long scheduleId,
                                                        @Param("orderId") Long orderId);
    
    /**
     * 更新锁定状态（乐观锁）
     */
    @Update("UPDATE schedule_material_lock " +
            "SET lock_status = #{newStatus}, " +
            "    allocated_time = CASE WHEN #{newStatus} = '已领料' THEN NOW() ELSE allocated_time END, " +
            "    version = version + 1 " +
            "WHERE id = #{id} AND version = #{version}")
    int updateStatus(@Param("id") Long id,
                     @Param("newStatus") String newStatus,
                     @Param("version") Integer version);
    
    /**
     * 批量更新锁定状态
     */
    @Update("<script>" +
            "UPDATE schedule_material_lock " +
            "SET lock_status = #{newStatus}, version = version + 1 " +
            "WHERE schedule_id = #{scheduleId} AND lock_status = #{oldStatus} " +
            "</script>")
    int updateStatusBySchedule(@Param("scheduleId") Long scheduleId,
                              @Param("oldStatus") String oldStatus,
                              @Param("newStatus") String newStatus);
    
    /**
     * 查询排程的锁定统计
     */
    @Select("SELECT lock_status as status, COUNT(*) as count " +
            "FROM schedule_material_lock " +
            "WHERE schedule_id = #{scheduleId} " +
            "GROUP BY lock_status")
    List<Map<String, Object>> selectStatusCount(@Param("scheduleId") Long scheduleId);
    
    /**
     * 查询排程的总锁定面积
     */
    @Select("SELECT COALESCE(SUM(locked_area), 0) as totalArea " +
            "FROM schedule_material_lock " +
            "WHERE schedule_id = #{scheduleId} AND lock_status IN ('锁定中', '已领料', 'LOCKED', 'ALLOCATED', 'PICKED')")
    java.util.Map<String, Object> selectTotalLockedArea(@Param("scheduleId") Long scheduleId);

        /**
         * 按订单维度查询仓库已锁定物料（工序可控）
         */
    @Select("<script>" +
            "SELECT l.*, l.order_no AS order_no, COALESCE(l.material_code, ts.material_code) AS material_code, " +
            "COALESCE(l.roll_code, ts.qr_code, ts.batch_no, '') AS roll_code, ts.location AS location, ts.spec_desc AS spec_desc " +
            "FROM schedule_material_lock l " +
            "JOIN tape_stock ts ON ts.id = l.film_stock_id " +
            "WHERE l.lock_status IN ('锁定中','已领料','LOCKED','ALLOCATED','PICKED') " +
            "<choose>" +
            "  <when test='processType != null and processType.toUpperCase() == \"SLITTING\"'> " +
            "    AND (ts.reel_type = '支料' OR ts.roll_type = '支料' OR ts.stock_type IN ('slit','slitting','strip')) " +
            "  </when>" +
            "  <when test='processType != null and processType.toUpperCase() == \"REWINDING\"'> " +
            "    AND (" +
            "      ts.reel_type = '母卷' OR " +
            "      ts.roll_type = '母卷' OR " +
            "      ts.stock_type IN ('jumbo','mother','mother_roll')" +
            "    ) " +
            "  </when>" +
            "  <otherwise> " +
            "    AND (" +
            "      ts.reel_type IN ('母卷','复卷','支料') OR " +
            "      ts.roll_type IN ('母卷','复卷','支料') OR " +
            "      ts.stock_type IN ('jumbo','rewound','slit','slitting','strip')" +
            "    ) " +
            "  </otherwise>" +
            "</choose> " +
            "<if test='orderNo != null and orderNo != \"\"'> AND l.order_no LIKE CONCAT('%', #{orderNo}, '%') </if> " +
            "<if test='materialCode != null and materialCode != \"\"'> AND COALESCE(l.material_code, ts.material_code) = #{materialCode} </if> " +
            "<if test='requiredLength != null and requiredLength > 0'> AND COALESCE(ts.current_length, ts.length) = #{requiredLength} </if> " +
            "<if test='rollCode != null and rollCode != \"\"'> AND COALESCE(l.roll_code, ts.qr_code, ts.batch_no, '') LIKE CONCAT('%', #{rollCode}, '%') </if> " +
            "ORDER BY " +
            "CASE WHEN l.lock_status IN ('锁定中','LOCKED') THEN 0 ELSE 1 END ASC, " +
            "COALESCE(l.material_code, ts.material_code) ASC, COALESCE(l.roll_code, ts.qr_code, ts.batch_no, '') ASC, l.locked_time DESC" +
            "</script>")
        List<ScheduleMaterialLock> selectOrderLockedStocks(@Param("planDate") LocalDate planDate,
                                                                                                           @Param("materialCode") String materialCode,
                                                                                                           @Param("orderNo") String orderNo,
                                                                                                           @Param("rollCode") String rollCode,
                                                                                                           @Param("processType") String processType,
                                                                                                           @Param("requiredLength") Integer requiredLength);

    /**
     * 锁定/领料/退料历史分页查询
     */
    @Select("<script>" +
            "SELECT l.*, " +
            "COALESCE(l.material_code, ts.material_code) AS material_code, " +
            "COALESCE(l.roll_code, ts.qr_code, ts.batch_no, '') AS roll_code, " +
            "ts.location AS location, ts.spec_desc AS spec_desc " +
            "FROM schedule_material_lock l " +
            "LEFT JOIN tape_stock ts ON ts.id = l.film_stock_id " +
            "WHERE 1=1 " +
            "<if test='planDate != null and planDate != \"\"'> AND DATE(l.locked_time) = #{planDate} </if> " +
            "<if test='orderNo != null and orderNo != \"\"'> AND l.order_no LIKE CONCAT('%', #{orderNo}, '%') </if> " +
            "<if test='materialCode != null and materialCode != \"\"'> AND COALESCE(l.material_code, ts.material_code) LIKE CONCAT('%', #{materialCode}, '%') </if> " +
            "<if test='rollCode != null and rollCode != \"\"'> AND COALESCE(l.roll_code, ts.qr_code, ts.batch_no, '') LIKE CONCAT('%', #{rollCode}, '%') </if> " +
            "<if test='lockStatus != null and lockStatus != \"\"'> AND l.lock_status = #{lockStatus} </if> " +
            "ORDER BY l.locked_time DESC, l.id DESC " +
            "LIMIT #{offset}, #{size}" +
            "</script>")
    List<ScheduleMaterialLock> selectLockHistoryPage(@Param("planDate") String planDate,
                                                     @Param("orderNo") String orderNo,
                                                     @Param("materialCode") String materialCode,
                                                     @Param("rollCode") String rollCode,
                                                     @Param("lockStatus") String lockStatus,
                                                     @Param("offset") int offset,
                                                     @Param("size") int size);

    /**
     * 锁定/领料/退料历史总数
     */
    @Select("<script>" +
            "SELECT COUNT(1) " +
            "FROM schedule_material_lock l " +
            "LEFT JOIN tape_stock ts ON ts.id = l.film_stock_id " +
            "WHERE 1=1 " +
            "<if test='planDate != null and planDate != \"\"'> AND DATE(l.locked_time) = #{planDate} </if> " +
            "<if test='orderNo != null and orderNo != \"\"'> AND l.order_no LIKE CONCAT('%', #{orderNo}, '%') </if> " +
            "<if test='materialCode != null and materialCode != \"\"'> AND COALESCE(l.material_code, ts.material_code) LIKE CONCAT('%', #{materialCode}, '%') </if> " +
            "<if test='rollCode != null and rollCode != \"\"'> AND COALESCE(l.roll_code, ts.qr_code, ts.batch_no, '') LIKE CONCAT('%', #{rollCode}, '%') </if> " +
            "<if test='lockStatus != null and lockStatus != \"\"'> AND l.lock_status = #{lockStatus} </if> " +
            "</script>")
    int countLockHistory(@Param("planDate") String planDate,
                         @Param("orderNo") String orderNo,
                         @Param("materialCode") String materialCode,
                         @Param("rollCode") String rollCode,
                         @Param("lockStatus") String lockStatus);
}
