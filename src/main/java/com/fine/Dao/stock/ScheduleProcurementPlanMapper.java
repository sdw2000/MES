package com.fine.Dao.stock;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fine.modle.stock.ScheduleProcurementPlan;
import org.apache.ibatis.annotations.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Mapper
public interface ScheduleProcurementPlanMapper extends BaseMapper<ScheduleProcurementPlan> {

    @Select("SELECT " +
            "l.schedule_id AS scheduleId, " +
            "l.order_no AS orderNo, " +
            "l.material_code AS materialCode, " +
            "ROUND(SUM(COALESCE(l.required_area,0) - COALESCE(l.locked_area,0)), 2) AS shortageArea " +
            "FROM schedule_material_lock l " +
            "WHERE l.lock_status = '待补锁' " +
            "GROUP BY l.schedule_id, l.order_no, l.material_code " +
            "HAVING ROUND(SUM(COALESCE(l.required_area,0) - COALESCE(l.locked_area,0)), 2) > 0")
    List<Map<String, Object>> selectPendingShortageForProcurement();

    @Select("SELECT COUNT(1) FROM schedule_procurement_plan " +
            "WHERE schedule_id = #{scheduleId} AND order_no = #{orderNo} AND material_code = #{materialCode} " +
            "AND status IN ('PENDING','CREATED','LINKED')")
    int countOpenPlan(@Param("scheduleId") Long scheduleId,
                      @Param("orderNo") String orderNo,
                      @Param("materialCode") String materialCode);

    @Insert("INSERT INTO schedule_procurement_plan(" +
            "plan_no, schedule_id, order_no, material_code, required_area, status, remark, created_at, updated_at" +
            ") VALUES (" +
            "#{planNo}, #{scheduleId}, #{orderNo}, #{materialCode}, #{requiredArea}, #{status}, #{remark}, NOW(), NOW()" +
            ")")
    int insertPlan(@Param("planNo") String planNo,
                   @Param("scheduleId") Long scheduleId,
                   @Param("orderNo") String orderNo,
                   @Param("materialCode") String materialCode,
                   @Param("requiredArea") BigDecimal requiredArea,
                   @Param("status") String status,
                   @Param("remark") String remark);

        @Select("SELECT * FROM schedule_procurement_plan WHERE status = 'PENDING' ORDER BY id ASC")
        List<ScheduleProcurementPlan> selectPendingPlans();

        @Update("UPDATE schedule_procurement_plan SET status = #{status}, purchase_order_no = #{purchaseOrderNo}, purchase_order_item_id = #{purchaseOrderItemId}, updated_at = NOW() WHERE id = #{id}")
        int updateLinkInfo(@Param("id") Long id,
                                           @Param("status") String status,
                                           @Param("purchaseOrderNo") String purchaseOrderNo,
                                           @Param("purchaseOrderItemId") Long purchaseOrderItemId);
}
