package com.fine.modle;

import lombok.Data;
import java.io.Serializable;
import java.time.LocalDate;
import java.util.List;

/**
 * 送样订单DTO - 用于前后端数据传输
 * @author AI Assistant
 * @date 2026-01-05
 */
@Data
public class SampleOrderDTO implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    private Long id;
    private String sampleNo;
    
    // 客户信息
    private Long customerId;
    private String customerName;
    
    // 联系人信息
    private String contactName;
    private String contactPhone;
    private String contactAddress;
    
    // 送样信息
    private LocalDate sendDate;
    private LocalDate expectedFeedbackDate;
    
    // 物流信息
    private String expressCompany;
    private String trackingNumber;
    private LocalDate shipDate;
    private LocalDate deliveryDate;
    
    // 状态
    private String status;
    private String logisticsStatus;
    
    // 反馈
    private String customerFeedback;
    private LocalDate feedbackDate;
    private Boolean isSatisfied;
    
    // 转订单
    private Boolean convertedToOrder;
    private String orderNo;
    
    // 备注
    private String remark;
    private String internalNote;
    
    // 明细列表
    private List<SampleItem> items;
    
    // 统计字段
    private Integer totalQuantity;
    
    // 系统字段
    private String createBy;
    private String createTime;
    private String updateBy;
    private String updateTime;
}
