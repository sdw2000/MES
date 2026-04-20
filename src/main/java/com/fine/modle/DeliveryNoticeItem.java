package com.fine.modle;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
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
    
    // 物料名称（展示字段，通过料号动态查询，不入库）
    @TableField(exist = false)
    private String materialName;
    
    // 规格
    private String spec;
    
    // 批号
    @TableField("batch_no")
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

    // 客户物料编号（仅打印展示，不入库）
    @TableField(exist = false)
    private String customerMaterialNo;
}
