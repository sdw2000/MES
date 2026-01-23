package com.fine.modle;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

@TableName( "erp_contact_list")
@Data
public class Contact {
    @TableId(type = IdType.AUTO)
    private Integer contactListId;
    private Integer companyCode;
    private Integer departmentCode;
    private Integer postCode;
    private String name;
    private Integer jobNumber;
    private String educationalBackground;
    private String idNo;
    private String maritalStatus;
    private String registeredResidenc;
    private String placeResidence;
    private String phoneNumber;

    // Getters and setters
}