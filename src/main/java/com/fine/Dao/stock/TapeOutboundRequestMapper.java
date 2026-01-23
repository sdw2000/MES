package com.fine.Dao.stock;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fine.modle.stock.TapeOutboundRequest;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

/**
 * 出库申请Mapper
 */
@Mapper
public interface TapeOutboundRequestMapper extends BaseMapper<TapeOutboundRequest> {
    
    /**
     * 生成出库单号
     */
    @Select("SELECT CONCAT('OUT', DATE_FORMAT(NOW(), '%Y%m%d'), LPAD(IFNULL(MAX(SUBSTRING(request_no, 12)), 0) + 1, 4, '0')) " +
            "FROM tape_outbound_request WHERE request_no LIKE CONCAT('OUT', DATE_FORMAT(NOW(), '%Y%m%d'), '%')")
    String generateRequestNo();
    
    /**
     * 统计待审批数量
     */
    @Select("SELECT COUNT(*) FROM tape_outbound_request WHERE status = 0")
    int countPending();
}
