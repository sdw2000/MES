package com.fine.model.production;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 人员技能实体类
 */
@Data
@TableName("staff_skill")
public class StaffSkill implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 人员ID */
    private Long staffId;

    /** 可操作设备类型 */
    private String equipmentType;

    /** 熟练度：normal-一般，skilled-熟练，expert-精通 */
    private String proficiency;

    /** 最多同时操作机器数 */
    private Integer maxMachines;

    /** 资格证书 */
    private String certificate;

    /** 证书有效期 */
    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "GMT+8")
    private Date certExpireDate;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createTime;

    // ========== 非数据库字段 ==========

    /** 设备类型名称 */
    @TableField(exist = false)
    private String equipmentTypeName;

    /** 人员姓名 */
    @TableField(exist = false)
    private String staffName;
}
