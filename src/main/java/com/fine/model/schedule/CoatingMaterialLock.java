package com.fine.model.schedule;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 涂布原材料锁定实体
 */
@Data
@TableName("coating_material_lock")
public class CoatingMaterialLock implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    @TableId(type = IdType.AUTO)
    private Long id;
    
    private String lockNo;              // 锁定单号
    private Long coatingTaskId;         // 涂布任务ID
    private String coatingTaskNo;       // 涂布任务编号
    
    private String materialCode;        // 原材料编号
    private String materialName;        // 原材料名称
    private String materialType;        // 材料类型：BASE_FILM/ADHESIVE/OTHER
    
    private Long stockId;               // 库存ID
    private String stockQrCode;         // 物料二维码
    private BigDecimal lockedQty;       // 锁定数量
    private String unit;                // 单位
    
    private String lockStatus;          // 锁定状态：LOCKED/RELEASED/CONSUMED
    
    private String lockedBy;            // 锁定操作人
    private Date lockedAt;              // 锁定时间
    private Date releasedAt;            // 解锁时间
    private Date consumedAt;            // 消耗时间
    private String remark;              // 备注
}
