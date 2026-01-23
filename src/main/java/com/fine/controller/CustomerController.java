package com.fine.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
// import com.fine.model.CustomerContact;
import com.fine.modle.CustomerContact;
import com.fine.service.CustomerService;
import com.fine.modle.CustomerDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletResponse;
import java.util.List;
import java.util.Map;
import com.fine.Utils.ResponseResult;

/**
 * 客户管理Controller
 * author Fine
 * date 2026-01-06
 */
@PreAuthorize("hasAuthority('admin')")
@RestController
@RequestMapping("/api/sales/customers")
@CrossOrigin
public class CustomerController {
    
    @Autowired
    private CustomerService customerService;
      /**
     * 分页查询客户列表
     */
    @GetMapping
    public ResponseResult<IPage<CustomerDTO>> getCustomerList(
            @RequestParam(defaultValue = "1") Integer current,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) String customerName,
            @RequestParam(required = false) String customerCode,
            @RequestParam(required = false) String customerType,
            @RequestParam(required = false) String customerLevel,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long salesUserId
    ) {
        CustomerDTO query = new CustomerDTO();
        query.setCustomerName(customerName);
        query.setCustomerCode(customerCode);
        query.setCustomerType(customerType);
        query.setCustomerLevel(customerLevel);
        query.setStatus(status);
        query.setSalesUserId(salesUserId);
        
        IPage<CustomerDTO> page = customerService.getCustomerPage(current, size, query);
        return new ResponseResult<>(20000, "查询成功", page);
    }
    
    /**
     * 根据ID查询客户详情
     */
    @GetMapping("/{id}")
    public ResponseResult<CustomerDTO> getCustomerDetail(@PathVariable Long id) {
        CustomerDTO customer = customerService.getCustomerDetailById(id);
        if (customer == null) {
            return new ResponseResult<>(40004, "客户不存在", null);
        }
        return new ResponseResult<>(20000, "查询成功", customer);
    }
      /**
     * 新增客户
     */
    @PostMapping
    public ResponseResult<Void> addCustomer(@RequestBody CustomerDTO customerDTO) {
        // 参数验证
        if (customerDTO.getCustomerName() == null || customerDTO.getCustomerName().isEmpty()) {
            return new ResponseResult<>(40000, "客户名称不能为空", null);
        }
        if (customerDTO.getCodePrefix() == null || customerDTO.getCodePrefix().isEmpty()) {
            return new ResponseResult<>(40000, "客户编号前缀不能为空", null);
        }
        if (customerDTO.getContacts() == null || customerDTO.getContacts().isEmpty()) {
            return new ResponseResult<>(40000, "每个客户至少需要一个联系人", null);
        }
        
        // 检查客户名称是否重复
        if (customerService.checkCustomerNameExists(customerDTO.getCustomerName(), null)) {
            return new ResponseResult<>(40000, "客户名称已存在", null);
        }
        
        boolean success = customerService.addCustomer(customerDTO);
        if (success) {
            return new ResponseResult<>(20000, "新增成功", null);
        } else {
            return new ResponseResult<>(50000, "新增失败", null);
        }
    }
    
    /**
     * 更新客户
     */
    @PutMapping("/{id}")
    public ResponseResult<Void> updateCustomer(@PathVariable Long id, @RequestBody CustomerDTO customerDTO) {
        customerDTO.setId(id);
        
        // 参数验证
        if (customerDTO.getCustomerName() == null || customerDTO.getCustomerName().isEmpty()) {
            return new ResponseResult<>(40000, "客户名称不能为空", null);
        }
        if (customerDTO.getContacts() == null || customerDTO.getContacts().isEmpty()) {
            return new ResponseResult<>(40000, "每个客户至少需要一个联系人", null);
        }
        
        // 检查客户名称是否重复
        if (customerService.checkCustomerNameExists(customerDTO.getCustomerName(), id)) {
            return new ResponseResult<>(40000, "客户名称已存在", null);
        }
        
        boolean success = customerService.updateCustomer(customerDTO);
        if (success) {
            return new ResponseResult<>(20000, "更新成功", null);
        } else {
            return new ResponseResult<>(50000, "更新失败", null);
        }
    }
    
    /**
     * 删除客户
     */    @DeleteMapping("/{id}")
    public ResponseResult<Void> deleteCustomer(@PathVariable Long id) {
        // Note: Consider implementing validation to check for associated orders before deletion
        boolean success = customerService.deleteCustomer(id);
        if (success) {
            return new ResponseResult<>(20000, "删除成功", null);
        } else {
            return new ResponseResult<>(50000, "删除失败", null);
        }
    }
    
    /**
     * 批量删除客户
     */
    @DeleteMapping("/batch")
    public ResponseResult<Void> batchDeleteCustomers(@RequestBody List<Long> ids) {
        boolean success = customerService.batchDeleteCustomers(ids);
        if (success) {
            return new ResponseResult<>(20000, "批量删除成功", null);
        } else {
            return new ResponseResult<>(50000, "批量删除失败", null);
        }
    }
    
    /**
     * 更新客户状态
     */
    @PutMapping("/{id}/status")
    public ResponseResult<Void> updateCustomerStatus(@PathVariable Long id, @RequestParam String status) {
        boolean success = customerService.updateCustomerStatus(id, status);
        if (success) {
            return new ResponseResult<>(20000, "状态更新成功", null);
        } else {
            return new ResponseResult<>(50000, "状态更新失败", null);
        }
    }
    
    /**
     * 根据客户ID查询联系人列表
     */
    @GetMapping("/{customerId}/contacts")
    public ResponseResult<List<CustomerContact>> getContactsByCustomerId(@PathVariable Long customerId) {
        List<CustomerContact> contacts = customerService.getContactsByCustomerId(customerId);
        return new ResponseResult<>(20000, "查询成功", contacts);
    }
    
    /**
     * 设置主联系人
     */
    @PutMapping("/{customerId}/contacts/{contactId}/primary")
    public ResponseResult<Void> setPrimaryContact(@PathVariable Long customerId, @PathVariable Long contactId) {
        boolean success = customerService.setPrimaryContact(customerId, contactId);
        if (success) {
            return new ResponseResult<>(20000, "设置成功", null);
        } else {
            return new ResponseResult<>(50000, "设置失败", null);
        }
    }
    
    /**
     * 检查客户编号是否存在
     */
    @GetMapping("/check-code")
    public ResponseResult<Boolean> checkCustomerCode(@RequestParam String customerCode) {
        boolean exists = customerService.checkCustomerCodeExists(customerCode);
        return new ResponseResult<>(20000, "查询成功", exists);
    }
    
    /**
     * 检查客户名称是否存在
     */
    @GetMapping("/check-name")
    public ResponseResult<Boolean> checkCustomerName(
            @RequestParam String customerName,
            @RequestParam(required = false) Long excludeId
    ) {
        boolean exists = customerService.checkCustomerNameExists(customerName, excludeId);
        return new ResponseResult<>(20000, "查询成功", exists);
    }
    
    /**
     * 生成客户编号预览
     */
    @GetMapping("/generate-code")
    public ResponseResult<String> generateCustomerCode(@RequestParam String prefix) {
        // 这里只是预览，不实际生成
        String code = prefix + "001"; // 简化处理
        return new ResponseResult<>(20000, "生成成功", code);
    }
    
    // ============== 导入导出功能 ==============
    
    /**
     * 下载导入模板
     */
    @GetMapping("/template")
    public void downloadTemplate(HttpServletResponse response) {
        customerService.downloadTemplate(response);
    }
    
    /**
     * 导入客户数据
     */
    @PostMapping("/import")
    public ResponseResult<Map<String, Object>> importCustomers(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return new ResponseResult<>(40000, "请选择要导入的文件", null);
        }
        try {
            Map<String, Object> result = customerService.importCustomers(file);
            return new ResponseResult<>(20000, "导入完成", result);
        } catch (Exception e) {
            e.printStackTrace();
            return new ResponseResult<>(50000, "导入失败：" + e.getMessage(), null);
        }
    }
    
    /**
     * 导出客户数据
     */
    @GetMapping("/export")
    public void exportCustomers(HttpServletResponse response) {
        customerService.exportCustomers(response);
    }
}
