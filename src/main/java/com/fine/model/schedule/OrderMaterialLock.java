package com.fine.model.schedule;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 订单物料锁定实体
 */
@Data
@TableName("order_material_lock")
public class OrderMaterialLock implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    @TableId(type = IdType.AUTO)
    private Long id;
    
    private String lockNo;              // 锁定单号
    private Long orderId;               // 订单ID
    private String orderNo;             // 订单编号
    private Long orderItemId;           // 订单明细ID
    private String customerName;        // 客户名称
    private BigDecimal customerPriority; // 客户优先级得分
    
    private String materialCode;        // 物料编号
    private String materialSpec;        // 物料规格
    private Long stockId;               // 库存ID
    private String stockQrCode;         // 物料二维码
    private Integer lockedQty;          // 锁定数量
    private Integer sharedOrderCount;   // 共用订单数
    
    private String lockStatus;          // 锁定状态：LOCKED/RELEASED/ISSUED
    private String issueStatus;         // 领料状态：PENDING/ISSUED/CANCELLED
    
    private String lockedBy;            // 锁定操作人
    private Date lockedAt;              // 锁定时间
    private String releasedBy;          // 解锁操作人
    private Date releasedAt;            // 解锁时间
    private String pickedBy;            // 领料操作人
    private Date pickedAt;              // 领料时间
    private Date issuedAt;              // 领料时间
    private String remark;              // 备注
}
