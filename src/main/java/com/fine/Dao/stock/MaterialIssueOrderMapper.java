package com.fine.Dao.stock;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fine.modle.stock.MaterialIssueOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface MaterialIssueOrderMapper extends BaseMapper<MaterialIssueOrder> {

    @Select("SELECT * FROM material_issue_order WHERE issue_no = #{issueNo} AND is_deleted = 0 LIMIT 1")
    MaterialIssueOrder selectByIssueNo(@Param("issueNo") String issueNo);

    @Select("SELECT issue_no FROM material_issue_order WHERE issue_no LIKE CONCAT(#{prefix}, '%') ORDER BY issue_no DESC LIMIT 1")
    String selectLastIssueNoByPrefix(@Param("prefix") String prefix);

    @Select("<script>" +
            "SELECT * FROM material_issue_order WHERE is_deleted = 0 " +
            "<if test='planDate != null'> AND plan_date = #{planDate} </if> " +
            "<if test='orderNo != null and orderNo != \"\"'> AND order_no LIKE CONCAT('%', #{orderNo}, '%') </if> " +
            "<if test='materialCode != null and materialCode != \"\"'> AND material_code = #{materialCode} </if> " +
            "ORDER BY id DESC LIMIT #{offset}, #{size}" +
            "</script>")
    List<MaterialIssueOrder> selectPage(@Param("planDate") LocalDate planDate,
                                        @Param("orderNo") String orderNo,
                                        @Param("materialCode") String materialCode,
                                        @Param("offset") int offset,
                                        @Param("size") int size);

    @Select("<script>" +
            "SELECT COUNT(1) FROM material_issue_order WHERE is_deleted = 0 " +
            "<if test='planDate != null'> AND plan_date = #{planDate} </if> " +
            "<if test='orderNo != null and orderNo != \"\"'> AND order_no LIKE CONCAT('%', #{orderNo}, '%') </if> " +
            "<if test='materialCode != null and materialCode != \"\"'> AND material_code = #{materialCode} </if> " +
            "</script>")
    int selectPageCount(@Param("planDate") LocalDate planDate,
                        @Param("orderNo") String orderNo,
                        @Param("materialCode") String materialCode);
}
