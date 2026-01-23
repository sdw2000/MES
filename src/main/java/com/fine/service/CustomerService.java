package com.fine.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.fine.modle.CustomerContact;
import com.fine.modle.CustomerDTO;

import java.util.List;

/**
 * 客户服务接口
 * @author Fine
 * @date 2026-01-06
 */
public interface CustomerService {
    
    /**
     * 分页查询客户列表
     */
    IPage<CustomerDTO> getCustomerPage(Integer current, Integer size, CustomerDTO query);
    
    /**
     * 根据ID查询客户详情（含联系人）
     */
    CustomerDTO getCustomerDetailById(Long id);
    
    /**
     * 新增客户（含联系人）
     */
    boolean addCustomer(CustomerDTO customerDTO);
    
    /**
     * 更新客户（含联系人）
     */
    boolean updateCustomer(CustomerDTO customerDTO);
    
    /**
     * 删除客户（逻辑删除）
     */
    boolean deleteCustomer(Long id);
    
    /**
     * 批量删除客户
     */
    boolean batchDeleteCustomers(List<Long> ids);
    
    /**
     * 更新客户状态
     */
    boolean updateCustomerStatus(Long id, String status);
    
    /**
     * 生成客户编号
     */
    String generateCustomerCode(String prefix);
    
    /**
     * 检查客户编号是否存在
     */
    boolean checkCustomerCodeExists(String customerCode);
    
    /**
     * 检查客户名称是否存在
     */
    boolean checkCustomerNameExists(String customerName, Long excludeId);
    
    /**
     * 根据客户ID查询联系人列表
     */
    List<CustomerContact> getContactsByCustomerId(Long customerId);
    
    /**
     * 设置主联系人
     */
    boolean setPrimaryContact(Long customerId, Long contactId);
    
    // ============== 导入导出功能 ==============
    
    /**
     * 下载导入模板
     */
    void downloadTemplate(javax.servlet.http.HttpServletResponse response);
    
    /**
     * 导入客户数据
     */
    java.util.Map<String, Object> importCustomers(org.springframework.web.multipart.MultipartFile file);
    
    /**
     * 导出客户数据
     */
    void exportCustomers(javax.servlet.http.HttpServletResponse response);
}

