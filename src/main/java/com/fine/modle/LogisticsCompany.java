package com.fine.modle;

import java.util.Date;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 物流公司
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("logistics_companies")
public class LogisticsCompany {

    @TableId(type = IdType.AUTO)
    private Long id;

    // 公司名称
    @TableField("company_name")
    private String companyName;

    // 公司编码
    @TableField("company_code")
    private String companyCode;

    // 公司地址
    @TableField("company_address")
    private String companyAddress;

    // 联系电话
    @TableField("contact_phone")
    private String contactPhone;

    // 联系人
    @TableField("contact_name")
    private String contactName;

    // 联系手机
    @TableField("contact_mobile")
    private String contactMobile;

    // 联系邮箱
    @TableField("contact_email")
    private String contactEmail;

    // 状态
    private String status;

    // 备注
    private String remark;

    // 创建时间
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createdAt;

    // 更新时间
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date updatedAt;

    // 逻辑删除
    @TableLogic
    private Integer isDeleted;
}
