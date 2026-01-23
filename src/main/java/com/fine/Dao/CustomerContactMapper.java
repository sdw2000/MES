package com.fine.Dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fine.modle.CustomerContact;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 客户联系人Mapper接口
 * @author Fine
 * @date 2026-01-06
 */
@Mapper
public interface CustomerContactMapper extends BaseMapper<CustomerContact> {
    
    /**
     * 根据客户ID查询联系人列表
     */
    @Select("SELECT * FROM customer_contacts WHERE customer_id = #{customerId} ORDER BY sort_order ASC, id ASC")
    List<CustomerContact> selectByCustomerId(@Param("customerId") Long customerId);
    
    /**
     * 根据客户ID查询主联系人
     */
    @Select("SELECT * FROM customer_contacts WHERE customer_id = #{customerId} AND is_primary = 1 LIMIT 1")
    CustomerContact selectPrimaryContact(@Param("customerId") Long customerId);
    
    /**
     * 取消客户的所有主联系人标记
     */
    @Update("UPDATE customer_contacts SET is_primary = 0 WHERE customer_id = #{customerId}")
    int cancelAllPrimaryContacts(@Param("customerId") Long customerId);
    
    /**
     * 设置主联系人
     */
    @Update("UPDATE customer_contacts SET is_primary = 1 WHERE id = #{contactId}")
    int setPrimaryContact(@Param("contactId") Long contactId);
    
    /**
     * 统计客户联系人数量
     */
    @Select("SELECT COUNT(*) FROM customer_contacts WHERE customer_id = #{customerId}")
    int countByCustomerId(@Param("customerId") Long customerId);
    
    /**
     * 批量删除客户联系人
     */
    @Update("DELETE FROM customer_contacts WHERE customer_id = #{customerId}")
    int deleteByCustomerId(@Param("customerId") Long customerId);
}
