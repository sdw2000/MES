package com.fine.Dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fine.modle.Customer;
import com.fine.modle.CustomerDTO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 客户Mapper接口
 * @author Fine
 * @date 2026-01-06
 */
@Mapper
public interface CustomerMapper extends BaseMapper<Customer> {
    
    /**
     * 分页查询客户列表（带主联系人信息）
     */
    IPage<CustomerDTO> selectCustomerPage(Page<CustomerDTO> page, @Param("query") CustomerDTO query);
    
    /**
     * 根据ID查询客户详情（带联系人列表）
     */
    CustomerDTO selectCustomerDetailById(@Param("id") Long id);
    
    /**
     * 检查客户编号是否存在
     */
    @Select("SELECT COUNT(*) FROM customers WHERE customer_code = #{customerCode} AND is_deleted = 0")
    int checkCustomerCodeExists(@Param("customerCode") String customerCode);
    
    /**
     * 检查客户名称是否存在
     */
    @Select("SELECT COUNT(*) FROM customers WHERE customer_name = #{customerName} AND is_deleted = 0 AND id != #{excludeId}")
    int checkCustomerNameExists(@Param("customerName") String customerName, @Param("excludeId") Long excludeId);
      /**
     * 获取并更新编号序列
     */
    @Select("SELECT current_number FROM customer_code_sequence WHERE prefix = #{prefix} FOR UPDATE")
    Integer selectCurrentNumber(@Param("prefix") String prefix);
    
    @Update("UPDATE customer_code_sequence SET current_number = current_number + 1 WHERE prefix = #{prefix}")
    int updateSequenceNumber(@Param("prefix") String prefix);
    
    @Insert("INSERT INTO customer_code_sequence (prefix, current_number) VALUES (#{prefix}, 1)")
    int insertSequence(@Param("prefix") String prefix);
    
    /**
     * 根据简称或全称查询客户
     */
    @Select("SELECT * FROM customers WHERE is_deleted = 0 AND (short_name = #{shortName} OR customer_name = #{customerName}) LIMIT 1")
    Customer selectByShortNameOrCustomerName(@Param("shortName") String shortName, @Param("customerName") String customerName);
    
    /**
     * 根据客户代码查询客户
     */
    @Select("SELECT * FROM customers WHERE is_deleted = 0 AND customer_code = #{customerCode} LIMIT 1")
    Customer selectByCustomerCode(@Param("customerCode") String customerCode);
    
    /**
     * 查询所有客户（带联系人，用于导出）
     */
    java.util.List<CustomerDTO> selectAllCustomersWithContacts();

    /**
     * 根据用户ID查询可访问的客户ID列表（销售/跟单）
     */
    @Select("SELECT id FROM customers WHERE is_deleted = 0 AND (sales = #{userId} OR documentation_person = #{userId})")
    java.util.List<Long> selectCustomerIdsByOwner(@Param("userId") Long userId);

    /**
     * 根据用户ID查询可访问的客户名称列表（销售/跟单）
     */
    @Select("SELECT customer_name FROM customers WHERE is_deleted = 0 AND (sales = #{userId} OR documentation_person = #{userId})")
    java.util.List<String> selectCustomerNamesByOwner(@Param("userId") Long userId);

    /**
     * 根据用户ID查询可访问的客户代码列表（销售/跟单）
     */
    @Select("SELECT customer_code FROM customers WHERE is_deleted = 0 AND (sales = #{userId} OR documentation_person = #{userId})")
    java.util.List<String> selectCustomerCodesByOwner(@Param("userId") Long userId);

    /**
     * 获取客户表中出现的销售用户ID列表
     */
    @Select("SELECT DISTINCT sales FROM customers WHERE is_deleted = 0 AND sales IS NOT NULL")
    java.util.List<Long> selectDistinctSalesUserIds();

    /**
     * 获取客户表中出现的跟单用户ID列表
     */
    @Select("SELECT DISTINCT documentation_person FROM customers WHERE is_deleted = 0 AND documentation_person IS NOT NULL")
    java.util.List<Long> selectDistinctDocumentationUserIds();

    /**
     * 获取某销售用户关联的跟单用户ID列表
     */
    @Select("SELECT DISTINCT documentation_person FROM customers WHERE is_deleted = 0 AND sales = #{salesUserId} AND documentation_person IS NOT NULL")
    java.util.List<Long> selectDistinctDocumentationUserIdsBySalesUser(@Param("salesUserId") Long salesUserId);

    /**
     * 获取某跟单用户关联的销售用户ID列表
     */
    @Select("SELECT DISTINCT sales FROM customers WHERE is_deleted = 0 AND documentation_person = #{docUserId} AND sales IS NOT NULL")
    java.util.List<Long> selectDistinctSalesUserIdsByDocumentationUser(@Param("docUserId") Long docUserId);
}
