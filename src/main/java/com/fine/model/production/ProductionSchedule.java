package com.fine.model.production;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

/**
 * 排程主表实体类
 */
@Data
public class ProductionSchedule {
    
    private Long id;
    
    /** 排程单号（PS-YYYYMMDD-XXX） */
    private String scheduleNo;
    
    /** 排程日期 */
    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "GMT+8")
    private Date scheduleDate;
    
    /** 排程类型：order-订单排程，safety-安全库存补货 */
    private String scheduleType;
    
    /** 涉及订单数 */
    private Integer totalOrders;
    
    /** 涉及订单明细数 */
    private Integer totalItems;
    
    /** 总面积(平方米) */
    private BigDecimal totalSqm;
    
    /** 状态：draft-草稿，confirmed-已确认，in_progress-执行中，completed-已完成，cancelled-已取消 */
    private String status;
    
    /** 确认人 */
    private String confirmedBy;
    
    /** 确认时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date confirmedTime;
    
    /** 备注 */
    private String remark;
    
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createTime;
    
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date updateTime;
    
    private String createBy;
    
    private String updateBy;
    
    private Integer isDeleted;
    
    // ========== 非数据库字段 ==========
    
    /** 排程订单关联列表 */
    private List<ScheduleOrderItem> orderItems;
    
    /** 涂布计划列表 */
    private List<ScheduleCoating> coatingTasks;
    
    /** 复卷计划列表 */
    private List<ScheduleRewinding> rewindingTasks;
    
    /** 分切计划列表 */
    private List<ScheduleSlitting> slittingTasks;
    
    /** 分条计划列表 */
    private List<ScheduleStripping> strippingTasks;
}
