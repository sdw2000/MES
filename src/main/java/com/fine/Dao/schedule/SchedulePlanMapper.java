package com.fine.Dao.schedule;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fine.modle.schedule.SchedulePlan;
import org.apache.ibatis.annotations.*;

import java.util.List;
import java.util.Map;

@Mapper
public interface SchedulePlanMapper extends BaseMapper<SchedulePlan> {

    /**
     * 插入或更新计划（按 order_detail_id + stage 唯一）
     */
    @Insert("INSERT INTO schedule_plan (order_detail_id, order_no, stage, plan_date, equipment, plan_area, status, created_at, updated_at) " +
            "VALUES (#{orderDetailId}, #{orderNo}, #{stage}, #{planDate}, #{equipment}, #{planArea}, #{status}, NOW(), NOW()) " +
            "ON DUPLICATE KEY UPDATE " +
            "order_no = VALUES(order_no), " +
            "plan_date = VALUES(plan_date), " +
            "equipment = VALUES(equipment), " +
            "plan_area = VALUES(plan_area), " +
            "status = VALUES(status), " +
            "updated_at = NOW()")
    int upsertPlan(SchedulePlan plan);

    /**
     * 查询指定日期的计划（按阶段）
     */
            @Select("SELECT sp.stage, " +
                    "sp.order_no, " +
                    "COALESCE(sp.material_code, soi.material_code) AS material_code, " +
                    "COALESCE(sp.material_name, soi.material_name) AS material_name, " +
                    "COALESCE(sp.thickness, soi.thickness) AS thickness, " +
                    "COALESCE(sp.width, soi.width) AS width, " +
                    "COALESCE(sp.length, soi.length) AS length, " +
                    "sp.plan_date, " +
                    "sp.equipment, " +
                    "sp.plan_area, " +
                    "sp.status " +
                    "FROM schedule_plan sp " +
                    "LEFT JOIN sales_order_items soi ON sp.order_detail_id = soi.id " +
                    "WHERE sp.plan_date >= #{start} AND sp.plan_date < #{end} " +
                    "ORDER BY sp.stage, sp.plan_date ASC")
    List<Map<String, Object>> selectDailyPlan(@Param("start") String start,
                                              @Param("end") String end);

    /**
     * 查询全部计划（按阶段+时间）
     */
    @Select("SELECT sp.stage, " +
            "sp.order_no, " +
            "COALESCE(sp.material_code, soi.material_code) AS material_code, " +
            "COALESCE(sp.material_name, soi.material_name) AS material_name, " +
            "COALESCE(sp.thickness, soi.thickness) AS thickness, " +
            "COALESCE(sp.width, soi.width) AS width, " +
            "COALESCE(sp.length, soi.length) AS length, " +
            "sp.plan_date, " +
            "sp.equipment, " +
            "sp.plan_area, " +
            "sp.status " +
            "FROM schedule_plan sp " +
            "LEFT JOIN sales_order_items soi ON sp.order_detail_id = soi.id " +
            "ORDER BY sp.stage, sp.plan_date ASC")
    List<Map<String, Object>> selectAllPlan();
}
