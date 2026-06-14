package com.fine.modle;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import java.io.Serializable;
import java.util.Date;

/**
 * 送样反馈记录实体类
 */
@Data
@TableName("sample_feedback")
public class SampleFeedback implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 关联送样单ID
     */
    private Long sampleOrderId;

    /**     * 送样明细ID（料号级别反馈）
     */
    private Long sampleItemId;

    /**     * 反馈内容
     */
    private String feedbackContent;

    /**
     * 剥离力
     */
    private String peelStrength;

    /**
     * 初粘
     */
    private String initialAdhesion;

    /**
     * 耐电解液
     */
    private String electrolyteResistance;

    /**
     * 耐温
     */
    private String temperatureResistance;

    /**
     * 切刀粘胶
     */
    private String cutterAdhesive;

    /**
     * 是否合格
     */
    private Boolean isQualified;

    /**
     * 反馈日期
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private java.util.Date feedbackDate;

    /**
     * 满意度
     */
    private String satisfactionLevel;

    /**
     * 附件(JSON)
     */
    private String attachments;

    private String createBy;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createTime;

    private String updateBy;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date updateTime;

    @TableLogic
    private Integer isDeleted;
}
