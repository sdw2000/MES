package com.fine.model.production;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 车间实体类
 */
@Data
@TableName("workshop")
public class Workshop implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 车间编号 */
    private String workshopCode;

    /** 车间名称 */
    private String workshopName;

    /** 负责人 */
    private String manager;

    /** 负责人电话 */
    private String managerPhone;

    /** 位置描述 */
    private String location;

    /** 备注 */
    private String remark;

    /** 状态：0-停用，1-启用 */
    private Integer status;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date updateTime;

    private String createBy;

    private String updateBy;

    @TableLogic
    private Integer isDeleted;
}
