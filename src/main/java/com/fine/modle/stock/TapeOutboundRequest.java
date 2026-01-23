package com.fine.modle.stock;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 出库申请实体
 */
@Data
@TableName("tape_outbound_request")
public class TapeOutboundRequest {
    
    @TableId(type = IdType.AUTO)
    private Long id;
    
    /** 出库申请单号 */
    private String requestNo;
    
    /** 关联库存ID */
    private Long stockId;
    
    /** 料号 */
    private String materialCode;
    
    /** 产品名称 */
    private String productName;
    
    /** 生产批次号 */
    private String batchNo;
    
    /** 规格描述 */
    private String specDesc;
    
    /** 出库卷数 */
    private Integer rolls;
    
    /** 申请时可用卷数 */
    private Integer availableRolls;
    
    /** 申请人 */
    private String applicant;
    
    /** 申请部门 */
    private String applyDept;
    
    /** 申请时间 */
    private LocalDateTime applyTime;
    
    /** 状态：0待审批 1已通过 2已拒绝 3已取消 */
    private Integer status;
    
    /** 审批人 */
    private String auditor;
    
    /** 审批时间 */
    private LocalDateTime auditTime;
    
    /** 审批备注 */
    private String auditRemark;
    
    /** 申请备注（如用途、去向） */
    private String remark;
    
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    
    // 状态常量
    public static final int STATUS_PENDING = 0;    // 待审批
    public static final int STATUS_APPROVED = 1;   // 已通过
    public static final int STATUS_REJECTED = 2;   // 已拒绝
    public static final int STATUS_CANCELLED = 3;  // 已取消
}
