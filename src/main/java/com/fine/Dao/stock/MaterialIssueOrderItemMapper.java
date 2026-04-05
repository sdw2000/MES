package com.fine.Dao.stock;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fine.modle.stock.MaterialIssueOrderItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface MaterialIssueOrderItemMapper extends BaseMapper<MaterialIssueOrderItem> {

    @Select("SELECT * FROM material_issue_order_item WHERE issue_order_id = #{issueOrderId} AND is_deleted = 0 ORDER BY id ASC")
    List<MaterialIssueOrderItem> selectByIssueOrderId(@Param("issueOrderId") Long issueOrderId);
}
