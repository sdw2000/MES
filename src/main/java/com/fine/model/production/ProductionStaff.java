package com.fine.model.production;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;
import java.util.List;

/**
 * 生产人员实体类
 */
@Data
@TableName("production_staff")
public class ProductionStaff implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 工号 */
    private String staffCode;

    /** 姓名 */
    private String staffName;

    /** 性别：M-男，F-女 */
    private String gender;

    /** 联系电话 */
    private String phone;

    /** 部门 */
    private String department;

    /** 岗位 */
    private String positionName;

    /** 学历 */
    private String education;

    /** 年龄 */
    private Integer age;

    /** 籍贯 */
    private String nativePlace;

    /** 身份证号码 */
    private String idCardNo;

    /** 户口所在地 */
    private String householdAddress;

    /** 现居住址 */
    private String currentAddress;

    /** 紧急联系人 */
    private String emergencyContact;

    /** 紧急联系人关系 */
    private String emergencyRelation;

    /** 签约日期 */
    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "GMT+8")
    private Date contractSignDate;

    /** 体检日期 */
    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "GMT+8")
    private Date medicalExamDate;

    /** 体检情况 */
    private String medicalExamResult;

    /** 所属班组ID */
    private Long teamId;

    /** 所属车间ID */
    private Long workshopId;

    /** 技能等级：junior-初级，middle-中级，senior-高级 */
    private String skillLevel;

    /** 入职日期 */
    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "GMT+8")
    private Date entryDate;

    /** 状态：active-在职，leave-休假，resigned-离职 */
    private String status;

    /** 备注 */
    private String remark;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date updateTime;

    private String createBy;

    private String updateBy;

    @TableLogic
    private Integer isDeleted;

    // ========== 非数据库字段 ==========

    /** 班组名称 */
    @TableField(exist = false)
    private String teamName;

    /** 车间名称 */
    @TableField(exist = false)
    private String workshopName;

    /** 人员技能列表 */
    @TableField(exist = false)
    private List<StaffSkill> skills;
}
