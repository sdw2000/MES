package com.fine.Dao.stock;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fine.modle.stock.TapeInboundRequest;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

/**
 * 入库申请Mapper
 */
@Mapper
public interface TapeInboundRequestMapper extends BaseMapper<TapeInboundRequest> {
    
    /**
     * 生成入库单号
     */
    @Select("SELECT CONCAT('IN', DATE_FORMAT(NOW(), '%Y%m%d'), LPAD(IFNULL(MAX(SUBSTRING(request_no, 11)), 0) + 1, 4, '0')) " +
            "FROM tape_inbound_request WHERE request_no LIKE CONCAT('IN', DATE_FORMAT(NOW(), '%Y%m%d'), '%')")
    String generateRequestNo();
    
    /**
     * 统计待审批数量
     */
    @Select("SELECT COUNT(*) FROM tape_inbound_request WHERE status = 0")
    int countPending();
}
