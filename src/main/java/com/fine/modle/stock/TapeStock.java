package com.fine.modle.stock;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 胶带库存实体
 */
@Data
@TableName("tape_stock")
public class TapeStock {
    
    @TableId(type = IdType.AUTO)
    private Long id;
    
    /** 料号 */
    private String materialCode;
    
    /** 产品名称 */
    private String productName;
    
    /** 生产批次号 */
    private String batchNo;
    
    /** 二维码内容（=批次号） */
    private String qrCode;
    
    /** 卷类型：母卷/复卷/分切卷 */
    private String rollType;
    
    /** 来源批次号（复卷时记录母卷批号） */
    private String parentBatchNo;
    
    /** 序列号（同一母卷的第几个子卷） */
    private Integer sequenceNo;
    
    /** 厚度μm */
    private Integer thickness;
    
    /** 宽度mm */
    private Integer width;
    
    /** 长度M（每卷） */
    private Integer length;
    
    /** 原始长度(m) */
    private Integer originalLength;
    
    /** 当前剩余长度(m) */
    private Integer currentLength;
    
    /** 当前库存卷数 */
    private Integer totalRolls;
    
    /** 总平米数 */
    private BigDecimal totalSqm;
    
    // ==========================================
    // 库存锁定机制字段（新增）
    // ==========================================
    /** 可用面积(m²) = 总面积 - 锁定面积 - 消耗面积 */
    private BigDecimal availableArea;
    
    /** 已预留面积(m²)，被锁定的 */
    private BigDecimal reservedArea;
    
    /** 已消耗面积(m²)，已领料的 */
    private BigDecimal consumedArea;
    
    /** 物料类型：复卷、母卷、支料 */
    private String reelType;
    
    /** 版本号，用于乐观锁 */
    private Integer version;
    
    /** 最后修改人 */
    private Long updatedBy;
    
    /** 最后一次锁定更新时间 */
    private LocalDateTime lockUpdatedTime;
    
    // ==========================================
    // 原有字段（保持不变）
    // ==========================================

    /** 卡板位/库位 */
    private String location;
    
    /** 规格描述 */
    private String specDesc;
    
    /** 生产年份 */
    private Integer prodYear;
    
    /** 生产月份 */
    private Integer prodMonth;
    
    /** 生产日期 */
    private Integer prodDay;
    
    /** 完整生产日期 */
    private LocalDate prodDate;
    
    /** 备注 */
    private String remark;
    
    /** 状态：1正常 0已清空 */
    private Integer status;
    
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    
    /**
     * 计算总平米数
     */
    public void calculateTotalSqm() {
        if (width != null && length != null && totalRolls != null) {
            // 总平米 = 宽度(mm)/1000 * 长度(m) * 卷数
            double sqm = (width / 1000.0) * length * totalRolls;
            this.totalSqm = BigDecimal.valueOf(sqm);
        }
    }
    
    /**
     * 生成规格描述（基于厚度、宽度、当前长度）
     */
    public void generateSpecDesc() {
        if (thickness != null && width != null) {
            int len = currentLength != null ? currentLength : (length != null ? length : 0);
            this.specDesc = thickness + "μm*" + width + "mm*" + len + "m";
        }
    }
    
    /**
     * 获取规格描述（动态计算）
     */
    public String getSpecDesc() {
        if (specDesc == null && thickness != null && width != null) {
            int len = currentLength != null ? currentLength : (length != null ? length : 0);
            return thickness + "μm*" + width + "mm*" + len + "m";
        }
        return specDesc;
    }
    
    /**
     * 生成二维码内容（默认为批次号）
     */
    public void generateQrCode() {
        if (qrCode == null && batchNo != null) {
            this.qrCode = batchNo;
        }
    }
    
    /**
     * 初始化长度信息
     */
    public void initLength() {
        if (length != null) {
            if (originalLength == null) {
                this.originalLength = length;
            }
            if (currentLength == null) {
                this.currentLength = length;
            }
        }
    }
    
    /**
     * 生成完整生产日期
     */
    public void generateProdDate() {
        if (prodYear != null && prodMonth != null && prodDay != null) {
            int fullYear = prodYear < 100 ? 2000 + prodYear : prodYear;
            this.prodDate = LocalDate.of(fullYear, prodMonth, prodDay);
        }
    }
}
