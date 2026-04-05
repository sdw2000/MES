package com.fine.modle;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 送样订单实体类
 * @author AI Assistant
 * @date 2026-01-05
 */
@Data
@TableName("sample_orders")
public class SampleOrder implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;
    
    /**
     * 送样编号（格式：SP260302-001）
     */
    private String sampleNo;
    
    /**
     * 客户ID（关联客户表）
     */
    private Long customerId;
    
    /**
     * 客户名称
     */
    private String customerName;
    
    /**
     * 联系人姓名
     */
    private String contactName;
    
    /**
     * 联系电话
     */
    private String contactPhone;
    
    /**
     * 收货地址
     */
    private String contactAddress;
    
    /**
     * 送样日期
     */
    private LocalDate sendDate;
    
    /**
     * 期望反馈日期
     */
    private LocalDate expectedFeedbackDate;
    
    /**
     * 快递公司
     */
    private String expressCompany;
    
    /**
     * 快递单号
     */
    private String trackingNumber;
    
    /**
     * 发货日期
     */
    private LocalDate shipDate;
    
    /**
     * 送达日期
     */
    private LocalDate deliveryDate;
    
    /**
     * 状态：待发货、已发货、运输中、已签收、已拒收、已取消
     */
    private String status;
      /**
     * 物流状态（快递公司返回的状态）
     */
    private String logisticsStatus;
    
    /**
     * 最后一次物流查询时间
     */
    private LocalDateTime lastLogisticsQueryTime;
    
    /**
     * 总数量（统计明细）
     */
    private Integer totalQuantity;
    
    /**
     * 客户反馈
     */
    private String customerFeedback;
    
    /**
     * 反馈日期
     */
    private LocalDate feedbackDate;
    
    /**
     * 是否满意：0-否，1-是
     */
    private Boolean isSatisfied;
    
    /**
     * 是否已转订单：0-否，1-是
     */
    private Boolean convertedToOrder;
    
    /**
     * 关联订单号
     */
    private String orderNo;
    
    /**
     * 备注
     */
    private String remark;
    
    /**
     * 内部备注（客户不可见）
     */
    private String internalNote;
    
    /**
     * 创建人
     */
    @TableField(fill = FieldFill.INSERT)
    private String createBy;
    
    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    
    /**
     * 更新人
     */
    @TableField(fill = FieldFill.UPDATE)
    private String updateBy;
    
    /**
     * 更新时间
     */
    @TableField(fill = FieldFill.UPDATE)
    private LocalDateTime updateTime;
    
    /**
     * 是否删除（逻辑删除）
     */
    @TableLogic
    private Boolean isDeleted;
      /**
     * 送样明细列表（非数据库字段）
     */
    @TableField(exist = false)
    private List<SampleItem> items;
}
