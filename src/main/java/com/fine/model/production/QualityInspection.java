package com.fine.model.production;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 质检记录实体类
 */
@Data
@TableName("quality_inspection")
public class QualityInspection {
    
    @TableId(type = IdType.AUTO)
    private Long id;
    
    /**
     * 质检单号（QC-YYYYMMDD-XXX）
     */
    private String inspectionNo;
    
    /**
     * 任务类型：PRINTING/COATING/REWINDING/SLITTING/STRIPPING
     */
    private String taskType;
    
    /**
     * 任务ID
     */
    private Long taskId;
    
    /**
     * 任务单号
     */
    private String taskNo;
    
    /**
     * 批次号
     */
    private String batchNo;
    
    /**
     * 产品料号
     */
    private String materialCode;
    
    /**
     * 产品名称
     */
    private String materialName;
    
    /**
     * 质检类型：first-首检，process-过程检，final-完工检，sampling-抽检
     */
    private String inspectionType;
    
    /**
     * 抽样数量
     */
    private Integer sampleQty;
    
    /**
     * 质检员ID
     */
    private Long inspectorId;
    
    /**
     * 质检员
     */
    private String inspectorName;
    
    /**
     * 质检时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date inspectionTime;
    
    // ========== 外观检验项 ==========
    
    /**
     * 外观结果：pass-合格，fail-不合格，conditional-有条件放行
     */
    private String appearanceResult;
    
    /**
     * 外观描述
     */
    private String appearanceDesc;
    
    // ========== 尺寸检验项 ==========
    
    /**
     * 实测厚度(mm)
     */
    private BigDecimal thicknessActual;
    
    /**
     * 厚度结果：pass/fail
     */
    private String thicknessResult;
    
    /**
     * 实测宽度(mm)
     */
    private BigDecimal widthActual;
    
    /**
     * 宽度结果：pass/fail
     */
    private String widthResult;
    
    /**
     * 实测长度(mm)
     */
    private BigDecimal lengthActual;
    
    /**
     * 长度结果：pass/fail
     */
    private String lengthResult;
    
    // ========== 性能检验项 ==========
    
    /**
     * 粘性测试结果：pass/fail
     */
    private String adhesionResult;
    
    /**
     * 粘性值
     */
    private BigDecimal adhesionValue;
    
    /**
     * 拉伸测试结果：pass/fail
     */
    private String tensileResult;
    
    /**
     * 拉伸值
     */
    private BigDecimal tensileValue;
    
    /**
     * 伸长率测试结果：pass/fail
     */
    private String elongationResult;
    
    /**
     * 伸长率(%)
     */
    private BigDecimal elongationValue;
    
    // ========== 综合结论 ==========
    
    /**
     * 综合结论：pass-合格，fail-不合格，conditional-有条件放行
     */
    private String overallResult;
    
    /**
     * 缺陷类型
     */
    private String defectType;
    
    /**
     * 缺陷描述
     */
    private String defectDesc;
    
    /**
     * 不良数量
     */
    private Integer defectQty;
    
    /**
     * 合格数量
     */
    private Integer passQty;
    
    // ========== 处理措施 ==========
    
    /**
     * 处理方式：rework-返工，scrap-报废，downgrade-降级，accept-特采
     */
    private String handleMethod;
    
    /**
     * 处理说明
     */
    private String handleDesc;
    
    /**
     * 处理人
     */
    private String handleBy;
    
    /**
     * 处理时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date handleTime;
    
    /**
     * 备注
     */
    private String remark;
    
    /**
     * 创建时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createTime;
    
    /**
     * 更新时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date updateTime;
    
    /**
     * 创建人
     */
    private String createBy;
    
    // ========== 非数据库字段 ==========
    
    /**
     * 任务类型名称（关联查询）
     */
    @TableField(exist = false)
    private String taskTypeName;
}
