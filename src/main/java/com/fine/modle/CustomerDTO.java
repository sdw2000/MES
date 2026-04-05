package com.fine.modle;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 客户数据传输对象
 * @author Fine
 * @date 2026-01-06
 */
@Data
public class CustomerDTO {
    
    private Long id;
    
    // 基本信息
    private String customerKeyword;
    private String customerCode;
    private String customerName;
    private String shortName;
    private String customerType;
    private String customerLevel;
    private String industry;
    private String orderNoPrefix;
    private String orderNoSuffix;
    
    // 企业信息
    private String taxNumber;
    private String legalPerson;
    private BigDecimal registeredCapital;
    private String registeredAddress;
    private String businessAddress;
    private String contactAddress;
    private String receiveAddress;
    private String businessScope;
    private String creditCode;
    
    // 联系信息
    private String companyPhone;
    private String companyFax;
    private String companyEmail;
    private String website;
    
    // 财务信息
    private BigDecimal creditLimit;
    private String paymentTerms;
    private BigDecimal taxRate;
    private String bankName;
    private String bankAccount;
    
    // 销售信息
    private String source;
    private Long salesUserId;           // 销售用户ID
    private Long documentationPersonUserId; // 跟单员用户ID
    private String salesUserName;       // 销售真实姓名（前端展示）
    private String documentationPersonUserName; // 跟单员真实姓名（前端展示）
    
    // 状态管理
    private String status;
    private String remark;
    
    // 系统字段
    private String createBy;
    private LocalDateTime createTime;
    private String updateBy;
    private LocalDateTime updateTime;
    
    // 联系人列表
    private List<CustomerContact> contacts;
    
    // 主联系人信息（用于列表显示）
    private String primaryContactName;
    private String primaryContactMobile;
    private String primaryContactEmail;
    
    // 统计信息
    private Integer contactCount;
    
    // 前端新增客户时使用的编号前缀
    private String codePrefix;
    
    // 相关项目统计
    private Integer quotationCount;      // 报价单数量
    private Integer salesOrderCount;     // 销售订单数量
    private Integer sampleOrderCount;    // 送样单数量
    private BigDecimal totalOrderAmount; // 累计订单金额
    
    // 相关项目列表（用于详情页）
    private List<CustomerQuotationDTO> quotations;      // 报价单列表
    private List<CustomerSalesOrderDTO> salesOrders;    // 销售订单列表
    private List<CustomerSampleOrderDTO> sampleOrders;  // 送样单列表
    
    /**
     * 客户报价单简要信息
     */
    @Data
    public static class CustomerQuotationDTO {
        private Long id;
        private String quotationNo;
        private BigDecimal totalAmount;
        private String quotationDate;
        private String status;
    }
    
    /**
     * 客户销售订单简要信息
     */
    @Data
    public static class CustomerSalesOrderDTO {
        private Long id;
        private String orderNo;
        private BigDecimal totalAmount;
        private String orderDate;
        private String status;
    }
    
    /**
     * 客户送样单简要信息
     */
    @Data
    public static class CustomerSampleOrderDTO {
        private Long id;
        private String sampleNo;
        private String sendDate;
        private String status;
        private String trackingNumber;
    }
}
