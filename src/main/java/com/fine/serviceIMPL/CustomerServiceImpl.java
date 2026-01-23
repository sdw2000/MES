package com.fine.serviceIMPL;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fine.Dao.CustomerContactMapper;
import com.fine.Dao.CustomerMapper;
import com.fine.modle.Customer;
import com.fine.modle.CustomerContact;
import com.fine.modle.CustomerDTO;
import com.fine.service.CustomerService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 客户服务实现类
 * @author Fine
 * @date 2026-01-06
 */
@Service
public class CustomerServiceImpl implements CustomerService {
    
    @Autowired
    private CustomerMapper customerMapper;
    
    @Autowired
    private CustomerContactMapper customerContactMapper;
    
    @Override
    public IPage<CustomerDTO> getCustomerPage(Integer current, Integer size, CustomerDTO query) {
        Page<CustomerDTO> page = new Page<>(current, size);
        page.setOptimizeCountSql(false); // 禁用COUNT优化，修复MyBatis-Plus 3.4.1的bug
        return customerMapper.selectCustomerPage(page, query);
    }
    
    @Override
    public CustomerDTO getCustomerDetailById(Long id) {
        return customerMapper.selectCustomerDetailById(id);
    }
      @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean addCustomer(CustomerDTO customerDTO) {
        if (customerDTO == null) {
            throw new IllegalArgumentException("客户信息不能为空");
        }
        
        // 1. 生成客户编号
        String customerCode = generateCustomerCode(customerDTO.getCodePrefix());
        
        // 2. 保存客户主表
        Customer customer = new Customer();
        BeanUtils.copyProperties(customerDTO, customer);
        customer.setCustomerCode(customerCode);
        customer.setCreateTime(LocalDateTime.now());
        customer.setUpdateTime(LocalDateTime.now());
        customer.setIsDeleted(0);
        
        int result = customerMapper.insert(customer);
        if (result <= 0) {
            return false;
        }
        
        // 3. 保存联系人（至少一个）
        List<CustomerContact> contacts = customerDTO.getContacts();
        if (contacts == null || contacts.isEmpty()) {
            throw new RuntimeException("每个客户至少需要一个联系人");
        }
        
        // 确保第一个联系人是主联系人
        boolean hasPrimary = contacts.stream().anyMatch(c -> c.getIsPrimary() != null && c.getIsPrimary() == 1);
        if (!hasPrimary) {
            contacts.get(0).setIsPrimary(1);
        }
        
        for (int i = 0; i < contacts.size(); i++) {
            CustomerContact contact = contacts.get(i);
            contact.setCustomerId(customer.getId());
            contact.setCreateTime(LocalDateTime.now());
            contact.setUpdateTime(LocalDateTime.now());
            if (contact.getSortOrder() == null) {
                contact.setSortOrder(i + 1);
            }
            customerContactMapper.insert(contact);
        }
        
        return true;
    }
      @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateCustomer(CustomerDTO customerDTO) {
        if (customerDTO == null) {
            throw new IllegalArgumentException("客户信息不能为空");
        }
        
        // 1. 更新客户主表
        Customer customer = new Customer();
        BeanUtils.copyProperties(customerDTO, customer);
        customer.setUpdateTime(LocalDateTime.now());
        
        int result = customerMapper.updateById(customer);
        if (result <= 0) {
            return false;
        }
        
        // 2. 删除原有联系人
        customerContactMapper.deleteByCustomerId(customer.getId());
        
        // 3. 保存新联系人
        List<CustomerContact> contacts = customerDTO.getContacts();
        if (contacts == null || contacts.isEmpty()) {
            throw new RuntimeException("每个客户至少需要一个联系人");
        }
        
        // 确保有主联系人
        boolean hasPrimary = contacts.stream().anyMatch(c -> c.getIsPrimary() != null && c.getIsPrimary() == 1);
        if (!hasPrimary) {
            contacts.get(0).setIsPrimary(1);
        }
        
        for (int i = 0; i < contacts.size(); i++) {
            CustomerContact contact = contacts.get(i);
            contact.setId(null); // 清空ID，重新插入
            contact.setCustomerId(customer.getId());
            contact.setCreateTime(LocalDateTime.now());
            contact.setUpdateTime(LocalDateTime.now());
            if (contact.getSortOrder() == null) {
                contact.setSortOrder(i + 1);
            }
            customerContactMapper.insert(contact);
        }
        
        return true;
    }
      @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteCustomer(Long id) {
        // 业务规则：检查是否有关联订单（这里简化处理，实际需要查询订单表）
        // Future enhancement: 检查是否有关联的送样单或销售订单
        
        // 逻辑删除
        Customer customer = new Customer();
        customer.setId(id);
        customer.setIsDeleted(1);
        customer.setUpdateTime(LocalDateTime.now());
        
        return customerMapper.updateById(customer) > 0;
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean batchDeleteCustomers(List<Long> ids) {
        for (Long id : ids) {
            if (!deleteCustomer(id)) {
                return false;
            }
        }
        return true;
    }
    
    @Override
    public boolean updateCustomerStatus(Long id, String status) {
        Customer customer = new Customer();
        customer.setId(id);
        customer.setStatus(status);
        customer.setUpdateTime(LocalDateTime.now());
        
        return customerMapper.updateById(customer) > 0;
    }
      @Override
    @Transactional(rollbackFor = Exception.class)
    public synchronized String generateCustomerCode(String prefix) {
        try {
            // 1. 查询当前序号
            Integer currentNumber = customerMapper.selectCurrentNumber(prefix);
            
            // 2. 如果前缀不存在，插入新记录
            if (currentNumber == null) {
                customerMapper.insertSequence(prefix);
                currentNumber = 1;
            } else {
                // 3. 更新序号
                currentNumber++;
                customerMapper.updateSequenceNumber(prefix);
            }
            
            // 4. 生成客户编号：前缀 + 三位流水号
            return prefix + String.format("%03d", currentNumber);
        } catch (Exception e) {
            // Fallback: 如果序列表不存在，使用时间戳生成
            System.err.println("警告：序列表不存在，使用备用方案生成客户编号");
            e.printStackTrace();
            return prefix + String.format("%03d", (int)(System.currentTimeMillis() % 1000));
        }
    }
    
    @Override
    public boolean checkCustomerCodeExists(String customerCode) {
        return customerMapper.checkCustomerCodeExists(customerCode) > 0;
    }
    
    @Override
    public boolean checkCustomerNameExists(String customerName, Long excludeId) {
        if (excludeId == null) {
            excludeId = 0L;
        }
        return customerMapper.checkCustomerNameExists(customerName, excludeId) > 0;
    }
    
    @Override
    public List<CustomerContact> getContactsByCustomerId(Long customerId) {
        return customerContactMapper.selectByCustomerId(customerId);
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean setPrimaryContact(Long customerId, Long contactId) {
        // 1. 取消该客户的所有主联系人
        customerContactMapper.cancelAllPrimaryContacts(customerId);
        
        // 2. 设置新的主联系人
        return customerContactMapper.setPrimaryContact(contactId) > 0;
    }
    
    // ============== 导入导出功能实现 ==============
    
    @Override
    public void downloadTemplate(javax.servlet.http.HttpServletResponse response) {
        try {
            org.apache.poi.xssf.usermodel.XSSFWorkbook workbook = new org.apache.poi.xssf.usermodel.XSSFWorkbook();
            
            // Sheet1: 客户信息
            org.apache.poi.ss.usermodel.Sheet customerSheet = workbook.createSheet("客户信息");
            String[] customerHeaders = {
                "客户代码", "客户简称*", "客户全称*", "客户类型", "客户等级", "所属行业",
                "纳税人识别号", "法人代表", "注册资本(万元)", "注册地址", "经营地址", "联系地址",
                "公司电话", "公司传真", "公司邮箱", "公司网站",
                "信用额度(元)", "付款条件", "税率(%)", "开户银行", "银行账号",
                "客户来源", "销售负责人", "所属部门", "状态", "备注"
            };
            createHeaderRow(workbook, customerSheet, customerHeaders);
            
            // 设置列宽
            int[] customerWidths = {4000, 3500, 6000, 3000, 3000, 3000, 5000, 3000, 3000, 8000, 8000, 8000,
                    4000, 4000, 5000, 5000, 4000, 3500, 2500, 5000, 5000, 3000, 3500, 3500, 2500, 5000};
            for (int i = 0; i < customerWidths.length; i++) {
                customerSheet.setColumnWidth(i, customerWidths[i]);
            }
            
            // 添加示例数据
            org.apache.poi.ss.usermodel.Row sampleRow = customerSheet.createRow(1);
            String[] sampleData = {"AHYC5001", "安徽亿丛", "安徽亿丛新能源有限公司", "企业客户", "A级客户", "新能源",
                    "91340100MA2TXXXXXX", "张三", "1000", "安徽省合肥市xxx", "安徽省合肥市xxx", "安徽省合肥市xxx",
                    "0551-12345678", "0551-12345679", "contact@yicong.com", "www.yicong.com",
                    "100000", "月结30天", "13", "中国银行合肥分行", "1234567890123456789",
                    "网络推广", "罗妙静", "销售一部", "正常", "优质客户"};
            for (int i = 0; i < sampleData.length; i++) {
                sampleRow.createCell(i).setCellValue(sampleData[i]);
            }
            
            // Sheet2: 联系人信息
            org.apache.poi.ss.usermodel.Sheet contactSheet = workbook.createSheet("联系人信息");
            String[] contactHeaders = {
                "客户代码*", "联系人姓名*", "联系电话*", "手机号码", "性别", "职位", "部门",
                "邮箱", "微信号", "QQ号", "联系地址", "是否主联系人", "是否决策人", "生日", "爱好", "备注"
            };
            createHeaderRow(workbook, contactSheet, contactHeaders);
            
            // 设置列宽
            int[] contactWidths = {4000, 3000, 4000, 4000, 2000, 3000, 3000,
                    5000, 4000, 3500, 8000, 3500, 3500, 3500, 4000, 5000};
            for (int i = 0; i < contactWidths.length; i++) {
                contactSheet.setColumnWidth(i, contactWidths[i]);
            }
            
            // 添加示例数据
            org.apache.poi.ss.usermodel.Row contactSample1 = contactSheet.createRow(1);
            String[] contactData1 = {"AHYC5001", "李经理", "13812345678", "13812345678", "男", "采购经理", "采购部",
                    "li@yicong.com", "lixxx", "123456789", "安徽省合肥市xxx", "是", "是", "1985-06-15", "高尔夫", "主要联系人"};
            for (int i = 0; i < contactData1.length; i++) {
                contactSample1.createCell(i).setCellValue(contactData1[i]);
            }
            
            org.apache.poi.ss.usermodel.Row contactSample2 = contactSheet.createRow(2);
            String[] contactData2 = {"AHYC5001", "王助理", "13887654321", "13887654321", "女", "采购助理", "采购部",
                    "wang@yicong.com", "wangxxx", "", "", "否", "否", "", "", "备用联系人"};
            for (int i = 0; i < contactData2.length; i++) {
                contactSample2.createCell(i).setCellValue(contactData2[i]);
            }
            
            // Sheet3: 填写说明
            org.apache.poi.ss.usermodel.Sheet helpSheet = workbook.createSheet("填写说明");
            String[][] helpData = {
                {"字段说明", ""},
                {"", ""},
                {"【客户信息表】", ""},
                {"客户代码", "可选，如不填写则系统自动生成。格式建议：字母+数字，如AHYC5001"},
                {"客户简称*", "必填，用于显示和快速识别"},
                {"客户全称*", "必填，公司完整名称"},
                {"客户类型", "企业客户 / 个人客户，默认：企业客户"},
                {"客户等级", "A级客户 / B级客户 / C级客户 / 潜在客户，默认：C级客户"},
                {"付款条件", "现款现货 / 货到付款 / 月结30天 / 月结60天 / 预付30%"},
                {"状态", "正常 / 冻结 / 黑名单，默认：正常"},
                {"", ""},
                {"【联系人信息表】", ""},
                {"客户代码*", "必填，需与客户信息表中的客户代码一致"},
                {"联系人姓名*", "必填"},
                {"联系电话*", "必填，固定电话或手机"},
                {"是否主联系人", "是 / 否，每个客户至少需要一个主联系人"},
                {"是否决策人", "是 / 否"},
                {"生日", "格式：yyyy-MM-dd，如2025-06-15"},
                {"", ""},
                {"【注意事项】", ""},
                {"1. 带*的字段为必填项", ""},
                {"2. 同一客户可以有多个联系人，通过【客户代码】进行关联", ""},
                {"3. 每个客户至少需要一个联系人", ""},
                {"4. 如果没有指定主联系人，系统会自动将第一个联系人设为主联系人", ""},
                {"5. 客户代码如果已存在，将更新该客户信息；如果不存在，将新建客户", ""},
            };
            for (int i = 0; i < helpData.length; i++) {
                org.apache.poi.ss.usermodel.Row row = helpSheet.createRow(i);
                row.createCell(0).setCellValue(helpData[i][0]);
                row.createCell(1).setCellValue(helpData[i][1]);
            }
            helpSheet.setColumnWidth(0, 6000);
            helpSheet.setColumnWidth(1, 15000);
            
            // 输出文件
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setHeader("Content-Disposition", "attachment; filename=" + 
                    java.net.URLEncoder.encode("客户导入模板.xlsx", "UTF-8"));
            workbook.write(response.getOutputStream());
            workbook.close();
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public java.util.Map<String, Object> importCustomers(org.springframework.web.multipart.MultipartFile file) {
        java.util.Map<String, Object> result = new java.util.HashMap<>();
        java.util.List<String> errors = new java.util.ArrayList<>();
        int insertCount = 0;
        int updateCount = 0;
        
        try {
            org.apache.poi.ss.usermodel.Workbook workbook = org.apache.poi.ss.usermodel.WorkbookFactory.create(file.getInputStream());
            
            // 1. 读取客户信息
            org.apache.poi.ss.usermodel.Sheet customerSheet = workbook.getSheet("客户信息");
            if (customerSheet == null) {
                customerSheet = workbook.getSheetAt(0);
            }
            
            // 客户代码 -> 客户对象的映射
            java.util.Map<String, Customer> customerMap = new java.util.LinkedHashMap<>();
            
            for (int i = 1; i <= customerSheet.getLastRowNum(); i++) {
                org.apache.poi.ss.usermodel.Row row = customerSheet.getRow(i);
                if (row == null) continue;
                
                String customerCode = getCellStringValue(row.getCell(0));
                String shortName = getCellStringValue(row.getCell(1));
                String customerName = getCellStringValue(row.getCell(2));
                
                // 必须有客户简称或客户代码作为标识
                if ((shortName == null || shortName.isEmpty()) && (customerCode == null || customerCode.isEmpty())) {
                    continue;
                }
                if (customerName == null || customerName.isEmpty()) {
                    errors.add("第" + (i + 1) + "行：客户全称不能为空");
                    continue;
                }
                
                Customer customer = new Customer();
                customer.setCustomerCode(customerCode); // 可能为空，后续处理
                customer.setShortName(shortName);
                customer.setCustomerName(customerName);
                customer.setCustomerType(getValueOrDefault(getCellStringValue(row.getCell(3)), "企业客户"));
                customer.setCustomerLevel(getValueOrDefault(getCellStringValue(row.getCell(4)), "C级客户"));
                customer.setIndustry(getCellStringValue(row.getCell(5)));
                customer.setTaxNumber(getCellStringValue(row.getCell(6)));
                customer.setLegalPerson(getCellStringValue(row.getCell(7)));
                customer.setRegisteredCapital(getCellBigDecimalValue(row.getCell(8)));
                customer.setRegisteredAddress(getCellStringValue(row.getCell(9)));
                customer.setBusinessAddress(getCellStringValue(row.getCell(10)));
                customer.setContactAddress(getCellStringValue(row.getCell(11)));
                customer.setCompanyPhone(getCellStringValue(row.getCell(12)));
                customer.setCompanyFax(getCellStringValue(row.getCell(13)));
                customer.setCompanyEmail(getCellStringValue(row.getCell(14)));
                customer.setWebsite(getCellStringValue(row.getCell(15)));
                customer.setCreditLimit(getCellBigDecimalValue(row.getCell(16)));
                customer.setPaymentTerms(getValueOrDefault(getCellStringValue(row.getCell(17)), "现款现货"));
                customer.setTaxRate(getCellBigDecimalValue(row.getCell(18)));
                customer.setBankName(getCellStringValue(row.getCell(19)));
                customer.setBankAccount(getCellStringValue(row.getCell(20)));
                customer.setSource(getCellStringValue(row.getCell(21)));
                // 销售和跟单员：在导入时设为null，用户需要在UI中手动选择
                // customer.setSalesUserId(...);
                // customer.setDocumentationPersonUserId(...);
                customer.setStatus(getValueOrDefault(getCellStringValue(row.getCell(24)), "正常"));
                customer.setRemark(getCellStringValue(row.getCell(25)));
                
                // 使用客户代码作为key，如果没有则用简称
                String mapKey = (customerCode != null && !customerCode.isEmpty()) ? customerCode : shortName;
                customerMap.put(mapKey, customer);
            }
            
            // 2. 读取联系人信息
            org.apache.poi.ss.usermodel.Sheet contactSheet = workbook.getSheet("联系人信息");
            if (contactSheet == null && workbook.getNumberOfSheets() > 1) {
                contactSheet = workbook.getSheetAt(1);
            }
              // 客户代码 -> 联系人列表的映射
            java.util.Map<String, java.util.List<CustomerContact>> contactMap = new java.util.HashMap<>();
            
            if (contactSheet != null) {
                java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd");
                for (int i = 1; i <= contactSheet.getLastRowNum(); i++) {
                    org.apache.poi.ss.usermodel.Row row = contactSheet.getRow(i);
                    if (row == null) continue;
                    
                    String contactCustomerCode = getCellStringValue(row.getCell(0));
                    String contactName = getCellStringValue(row.getCell(1));
                    String contactPhone = getCellStringValue(row.getCell(2));
                    
                    // 客户代码是关联联系人和客户的关键字段
                    if (contactCustomerCode == null || contactCustomerCode.isEmpty()) {
                        errors.add("联系人表第" + (i + 1) + "行：客户代码不能为空");
                        continue;
                    }
                    if (contactName == null || contactName.isEmpty()) {
                        errors.add("联系人表第" + (i + 1) + "行：联系人姓名不能为空");
                        continue;
                    }
                    if (contactPhone == null || contactPhone.isEmpty()) {
                        errors.add("联系人表第" + (i + 1) + "行：联系电话不能为空");
                        continue;
                    }
                    
                    CustomerContact contact = new CustomerContact();
                    contact.setContactName(contactName);
                    contact.setContactPhone(contactPhone);
                    contact.setContactMobile(getCellStringValue(row.getCell(3)));
                    contact.setContactGender(getCellStringValue(row.getCell(4)));
                    contact.setContactPosition(getCellStringValue(row.getCell(5)));
                    contact.setContactDepartment(getCellStringValue(row.getCell(6)));
                    contact.setContactEmail(getCellStringValue(row.getCell(7)));
                    contact.setContactWechat(getCellStringValue(row.getCell(8)));
                    contact.setContactQq(getCellStringValue(row.getCell(9)));
                    contact.setContactAddress(getCellStringValue(row.getCell(10)));
                    contact.setIsPrimary("是".equals(getCellStringValue(row.getCell(11))) ? 1 : 0);
                    contact.setIsDecisionMaker("是".equals(getCellStringValue(row.getCell(12))) ? 1 : 0);
                    
                    String birthdayStr = getCellStringValue(row.getCell(13));
                    if (birthdayStr != null && !birthdayStr.isEmpty()) {
                        try {
                            java.util.Date date = sdf.parse(birthdayStr);
                            contact.setBirthday(date.toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDate());
                        } catch (Exception e) {
                            // 忽略日期解析错误
                        }
                    }
                    
                    contact.setHobby(getCellStringValue(row.getCell(14)));
                    contact.setRemark(getCellStringValue(row.getCell(15)));
                    
                    // 使用客户代码作为key来关联联系人
                    contactMap.computeIfAbsent(contactCustomerCode, k -> new java.util.ArrayList<>()).add(contact);
                }
            }
              workbook.close();
            
            // 3. 保存数据
            for (java.util.Map.Entry<String, Customer> entry : customerMap.entrySet()) {
                String mapKey = entry.getKey();
                Customer customer = entry.getValue();
                
                // 获取联系人列表（使用mapKey关联）
                java.util.List<CustomerContact> contacts = contactMap.get(mapKey);
                if (contacts == null || contacts.isEmpty()) {
                    errors.add("客户【" + customer.getShortName() + "】没有对应的联系人信息，请检查联系人表中的客户代码是否与客户信息表一致");
                    continue;
                }
                
                // 确保有主联系人
                boolean hasPrimary = contacts.stream().anyMatch(c -> c.getIsPrimary() != null && c.getIsPrimary() == 1);
                if (!hasPrimary) {
                    contacts.get(0).setIsPrimary(1);
                }
                
                try {
                    // 优先使用客户代码判断是否存在
                    Customer existing = null;
                    String importedCode = customer.getCustomerCode();
                    
                    if (importedCode != null && !importedCode.isEmpty()) {
                        // 有客户代码，按客户代码查找
                        existing = customerMapper.selectByCustomerCode(importedCode);
                    }
                    
                    // 如果按代码没找到，再按简称或名称查找
                    if (existing == null) {
                        existing = customerMapper.selectByShortNameOrCustomerName(customer.getShortName(), customer.getCustomerName());
                    }
                    
                    if (existing != null) {
                        // 更新现有客户
                        customer.setId(existing.getId());
                        customer.setCustomerCode(existing.getCustomerCode()); // 保留原有客户代码
                        customer.setUpdateTime(LocalDateTime.now());
                        customerMapper.updateById(customer);
                        
                        // 删除原有联系人
                        customerContactMapper.deleteByCustomerId(existing.getId());
                        
                        // 保存新联系人
                        for (int i = 0; i < contacts.size(); i++) {
                            CustomerContact contact = contacts.get(i);
                            contact.setCustomerId(existing.getId());
                            contact.setCreateTime(LocalDateTime.now());
                            contact.setUpdateTime(LocalDateTime.now());
                            contact.setSortOrder(i + 1);
                            customerContactMapper.insert(contact);
                        }
                        
                        updateCount++;
                    } else {
                        // 新增客户
                        String customerCode;
                        if (importedCode != null && !importedCode.isEmpty()) {
                            // 使用导入的客户代码
                            customerCode = importedCode;
                        } else {
                            // 生成客户编号（使用简称前几个字的拼音首字母作为前缀）
                            String prefix = generatePrefixFromName(customer.getShortName());
                            customerCode = generateCustomerCode(prefix);
                        }
                        
                        customer.setCustomerCode(customerCode);
                        customer.setCreateTime(LocalDateTime.now());
                        customer.setUpdateTime(LocalDateTime.now());
                        customer.setIsDeleted(0);
                        customerMapper.insert(customer);
                        
                        // 保存联系人
                        for (int i = 0; i < contacts.size(); i++) {
                            CustomerContact contact = contacts.get(i);
                            contact.setCustomerId(customer.getId());
                            contact.setCreateTime(LocalDateTime.now());
                            contact.setUpdateTime(LocalDateTime.now());
                            contact.setSortOrder(i + 1);
                            customerContactMapper.insert(contact);
                        }
                        
                        insertCount++;
                    }
                } catch (Exception e) {
                    errors.add("客户【" + customer.getShortName() + "】保存失败：" + e.getMessage());
                }
            }
            
        } catch (Exception e) {
            e.printStackTrace();
            errors.add("文件解析失败：" + e.getMessage());
        }
        
        result.put("insertCount", insertCount);
        result.put("updateCount", updateCount);
        result.put("errors", errors);
        
        return result;
    }
    
    @Override
    public void exportCustomers(javax.servlet.http.HttpServletResponse response) {
        try {
            // 查询所有客户
            java.util.List<CustomerDTO> customers = customerMapper.selectAllCustomersWithContacts();
            
            org.apache.poi.xssf.usermodel.XSSFWorkbook workbook = new org.apache.poi.xssf.usermodel.XSSFWorkbook();
            
            // Sheet1: 客户信息
            org.apache.poi.ss.usermodel.Sheet customerSheet = workbook.createSheet("客户信息");
            String[] customerHeaders = {
                "客户编码", "客户简称", "客户全称", "客户类型", "客户等级", "所属行业",
                "纳税人识别号", "法人代表", "注册资本(万元)", "注册地址", "经营地址", "联系地址",
                "公司电话", "公司传真", "公司邮箱", "公司网站",
                "信用额度(元)", "付款条件", "税率(%)", "开户银行", "银行账号",
                "客户来源", "销售负责人", "所属部门", "状态", "备注", "创建时间"
            };
            createHeaderRow(workbook, customerSheet, customerHeaders);
            
            int rowNum = 1;
            for (CustomerDTO customer : customers) {
                org.apache.poi.ss.usermodel.Row row = customerSheet.createRow(rowNum++);
                row.createCell(0).setCellValue(customer.getCustomerCode() != null ? customer.getCustomerCode() : "");
                row.createCell(1).setCellValue(customer.getShortName() != null ? customer.getShortName() : "");
                row.createCell(2).setCellValue(customer.getCustomerName() != null ? customer.getCustomerName() : "");
                row.createCell(3).setCellValue(customer.getCustomerType() != null ? customer.getCustomerType() : "");
                row.createCell(4).setCellValue(customer.getCustomerLevel() != null ? customer.getCustomerLevel() : "");
                row.createCell(5).setCellValue(customer.getIndustry() != null ? customer.getIndustry() : "");
                row.createCell(6).setCellValue(customer.getTaxNumber() != null ? customer.getTaxNumber() : "");
                row.createCell(7).setCellValue(customer.getLegalPerson() != null ? customer.getLegalPerson() : "");
                row.createCell(8).setCellValue(customer.getRegisteredCapital() != null ? customer.getRegisteredCapital().toString() : "");
                row.createCell(9).setCellValue(customer.getRegisteredAddress() != null ? customer.getRegisteredAddress() : "");
                row.createCell(10).setCellValue(customer.getBusinessAddress() != null ? customer.getBusinessAddress() : "");
                row.createCell(11).setCellValue(customer.getContactAddress() != null ? customer.getContactAddress() : "");
                row.createCell(12).setCellValue(customer.getCompanyPhone() != null ? customer.getCompanyPhone() : "");
                row.createCell(13).setCellValue(customer.getCompanyFax() != null ? customer.getCompanyFax() : "");
                row.createCell(14).setCellValue(customer.getCompanyEmail() != null ? customer.getCompanyEmail() : "");
                row.createCell(15).setCellValue(customer.getWebsite() != null ? customer.getWebsite() : "");
                row.createCell(16).setCellValue(customer.getCreditLimit() != null ? customer.getCreditLimit().toString() : "");
                row.createCell(17).setCellValue(customer.getPaymentTerms() != null ? customer.getPaymentTerms() : "");
                row.createCell(18).setCellValue(customer.getTaxRate() != null ? customer.getTaxRate().toString() : "");
                row.createCell(19).setCellValue(customer.getBankName() != null ? customer.getBankName() : "");
                row.createCell(20).setCellValue(customer.getBankAccount() != null ? customer.getBankAccount() : "");
                row.createCell(21).setCellValue(customer.getSource() != null ? customer.getSource() : "");
                row.createCell(22).setCellValue(customer.getSalesUserName() != null ? customer.getSalesUserName() : "");
                row.createCell(23).setCellValue(customer.getDocumentationPersonUserName() != null ? customer.getDocumentationPersonUserName() : "");
                row.createCell(24).setCellValue(customer.getStatus() != null ? customer.getStatus() : "");
                row.createCell(25).setCellValue(customer.getRemark() != null ? customer.getRemark() : "");
                row.createCell(26).setCellValue(customer.getCreateTime() != null ? customer.getCreateTime().toString() : "");
            }
            
            // Sheet2: 联系人信息
            org.apache.poi.ss.usermodel.Sheet contactSheet = workbook.createSheet("联系人信息");
            String[] contactHeaders = {
                "客户编码", "客户简称", "联系人姓名", "联系电话", "手机号码", "性别", "职位", "部门",
                "邮箱", "微信号", "QQ号", "联系地址", "是否主联系人", "是否决策人", "生日", "爱好", "备注"
            };
            createHeaderRow(workbook, contactSheet, contactHeaders);
            
            rowNum = 1;
            for (CustomerDTO customer : customers) {
                if (customer.getContacts() != null) {
                    for (CustomerContact contact : customer.getContacts()) {
                        org.apache.poi.ss.usermodel.Row row = contactSheet.createRow(rowNum++);
                        row.createCell(0).setCellValue(customer.getCustomerCode() != null ? customer.getCustomerCode() : "");
                        row.createCell(1).setCellValue(customer.getShortName() != null ? customer.getShortName() : "");
                        row.createCell(2).setCellValue(contact.getContactName() != null ? contact.getContactName() : "");
                        row.createCell(3).setCellValue(contact.getContactPhone() != null ? contact.getContactPhone() : "");
                        row.createCell(4).setCellValue(contact.getContactMobile() != null ? contact.getContactMobile() : "");
                        row.createCell(5).setCellValue(contact.getContactGender() != null ? contact.getContactGender() : "");
                        row.createCell(6).setCellValue(contact.getContactPosition() != null ? contact.getContactPosition() : "");
                        row.createCell(7).setCellValue(contact.getContactDepartment() != null ? contact.getContactDepartment() : "");
                        row.createCell(8).setCellValue(contact.getContactEmail() != null ? contact.getContactEmail() : "");
                        row.createCell(9).setCellValue(contact.getContactWechat() != null ? contact.getContactWechat() : "");
                        row.createCell(10).setCellValue(contact.getContactQq() != null ? contact.getContactQq() : "");
                        row.createCell(11).setCellValue(contact.getContactAddress() != null ? contact.getContactAddress() : "");
                        row.createCell(12).setCellValue(contact.getIsPrimary() != null && contact.getIsPrimary() == 1 ? "是" : "否");
                        row.createCell(13).setCellValue(contact.getIsDecisionMaker() != null && contact.getIsDecisionMaker() == 1 ? "是" : "否");
                        row.createCell(14).setCellValue(contact.getBirthday() != null ? contact.getBirthday().toString() : "");
                        row.createCell(15).setCellValue(contact.getHobby() != null ? contact.getHobby() : "");
                        row.createCell(16).setCellValue(contact.getRemark() != null ? contact.getRemark() : "");
                    }
                }
            }
            
            // 输出文件
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyyMMdd");
            String fileName = "客户数据_" + sdf.format(new java.util.Date()) + ".xlsx";
            response.setHeader("Content-Disposition", "attachment; filename=" + 
                    java.net.URLEncoder.encode(fileName, "UTF-8"));
            workbook.write(response.getOutputStream());
            workbook.close();
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    // ============== 辅助方法 ==============
    
    private void createHeaderRow(org.apache.poi.xssf.usermodel.XSSFWorkbook workbook, 
                                  org.apache.poi.ss.usermodel.Sheet sheet, String[] headers) {
        org.apache.poi.ss.usermodel.CellStyle headerStyle = workbook.createCellStyle();
        headerStyle.setFillForegroundColor(org.apache.poi.ss.usermodel.IndexedColors.GREY_25_PERCENT.getIndex());
        headerStyle.setFillPattern(org.apache.poi.ss.usermodel.FillPatternType.SOLID_FOREGROUND);
        org.apache.poi.ss.usermodel.Font headerFont = workbook.createFont();
        headerFont.setBold(true);
        headerStyle.setFont(headerFont);
        
        org.apache.poi.ss.usermodel.Row headerRow = sheet.createRow(0);
        for (int i = 0; i < headers.length; i++) {
            org.apache.poi.ss.usermodel.Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }
    }
    
    private String getCellStringValue(org.apache.poi.ss.usermodel.Cell cell) {
        if (cell == null) return null;
        try {
            cell.setCellType(org.apache.poi.ss.usermodel.CellType.STRING);
            String value = cell.getStringCellValue();
            return value != null ? value.trim() : null;
        } catch (Exception e) {
            return null;
        }
    }
    
    private java.math.BigDecimal getCellBigDecimalValue(org.apache.poi.ss.usermodel.Cell cell) {
        if (cell == null) return null;
        try {
            if (cell.getCellType() == org.apache.poi.ss.usermodel.CellType.NUMERIC) {
                return java.math.BigDecimal.valueOf(cell.getNumericCellValue());
            } else if (cell.getCellType() == org.apache.poi.ss.usermodel.CellType.STRING) {
                String value = cell.getStringCellValue().trim();
                if (value.isEmpty()) return null;
                return new java.math.BigDecimal(value);
            }
        } catch (Exception e) {
            // ignore
        }
        return null;
    }
    
    private String getValueOrDefault(String value, String defaultValue) {
        return (value != null && !value.isEmpty()) ? value : defaultValue;
    }
    
    /**
     * 从名称生成编号前缀（简单实现：取前两个汉字的拼音首字母）
     */
    private String generatePrefixFromName(String name) {
        if (name == null || name.isEmpty()) {
            return "KH"; // 默认前缀：客户
        }
        // 简单处理：取前两个字符的大写
        StringBuilder prefix = new StringBuilder();
        for (int i = 0; i < Math.min(name.length(), 3); i++) {
            char c = name.charAt(i);
            if (Character.isLetter(c)) {
                prefix.append(Character.toUpperCase(c));
            }
        }
        if (prefix.length() == 0) {
            return "KH";
        }
        return prefix.toString();
    }
}
