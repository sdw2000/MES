package com.fine.modle;

import lombok.Data;
import java.io.Serializable;

/**
 * 物流信息更新DTO
 * @author AI Assistant
 * @date 2026-01-05
 */
@Data
public class LogisticsUpdateDTO implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    private String sampleNo;
    private String expressCompany;
    private String trackingNumber;
    private String shipDate;
    private String deliveryDate;
    private String remark;
}
