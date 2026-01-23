package com.fine.modle;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 发货通知单明细实体类
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("delivery_notice_items")
public class DeliveryNoticeItem {
    
    @TableId(type = IdType.AUTO)
    private Long id;
    
    // 关联发货单ID
    private Long noticeId;
    
    // 关联订单明细ID
    private Long orderItemId;
    
    // 物料代码
    private String materialCode;
    
    // 物料名称
    private String materialName;
    
    // 规格
    private String spec;
    
    // 批号
    private String batchNo;
    
    // 发货数量(卷)
    private Integer quantity;
    
    // 平方数 (m2)
    private java.math.BigDecimal areaSize;

    // 箱数
    private Integer boxCount;

    // 每箱毛重 (kg)
    private java.math.BigDecimal grossWeight;

    // 总毛重 (kg)
    private java.math.BigDecimal totalWeight;

    // 备注
    private String remark;
}
