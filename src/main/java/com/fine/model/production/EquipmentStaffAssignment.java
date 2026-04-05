package com.fine.model.production;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Date;

/**
 * 设备-人员-班次排班
 */
@Data
@TableName("equipment_staff_assignment")
public class EquipmentStaffAssignment implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long equipmentId;

    private String equipmentCode;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime planDate;

    private Long shiftId;

    private String shiftCode;

    private String shiftName;

    private Long staffId;

    private String staffCode;

    private String staffName;

    /** operator/assistant/captain */
    private String roleName;

    /** 1-到岗，0-未到岗 */
    private Integer onDuty;

    private String remark;

    private Date createTime;

    private Date updateTime;

    private String createBy;

    private String updateBy;

    @TableField(exist = false)
    private String equipmentType;
}
