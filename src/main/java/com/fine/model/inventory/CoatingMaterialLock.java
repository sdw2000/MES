package com.fine.model.inventory;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 涂布原料锁定表
 * 用于涂布生产时锁定原料（薄膜母卷）
 */
@Data
@TableName("coating_material_lock")
public class CoatingMaterialLock {
    
    @TableId(type = IdType.AUTO)
    private Long id;
    
    /** 涂布任务ID */
    private Long coatingTaskId;
    
    /** 涂布任务编号 */
    private String coatingTaskNo;
    
    /** 原料二维码（薄膜母卷） */
    private String materialQrCode;
    
    /** 原料料号 */
    private String materialCode;
    
    /** 原料名称 */
    private String materialName;
    
    /** 锁定数量（平方米） */
    private BigDecimal lockedQuantity;
    
    /** 锁定状态：locked-已锁定，released-已释放，consumed-已消耗 */
    private String lockStatus;
    
    /** 锁定时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date lockedAt;
    
    /** 释放时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date releasedAt;
    
    /** 创建时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createdAt;
}
