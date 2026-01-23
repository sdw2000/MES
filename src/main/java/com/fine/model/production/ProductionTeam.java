package com.fine.model.production;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;
import java.util.List;

/**
 * 班组实体类
 */
@Data
@TableName("production_team")
public class ProductionTeam implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 班组编号 */
    private String teamCode;

    /** 班组名称 */
    private String teamName;

    /** 所属车间ID */
    private Long workshopId;

    /** 班组长ID */
    private Long leaderId;

    /** 默认班次 */
    private String shiftCode;

    /** 备注 */
    private String remark;

    /** 状态：0-停用，1-启用 */
    private Integer status;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date updateTime;

    // ========== 非数据库字段 ==========

    /** 车间名称 */
    @TableField(exist = false)
    private String workshopName;

    /** 班组长姓名 */
    @TableField(exist = false)
    private String leaderName;

    /** 班次名称 */
    @TableField(exist = false)
    private String shiftName;

    /** 组员列表 */
    @TableField(exist = false)
    private List<ProductionStaff> members;

    /** 组员数量 */
    @TableField(exist = false)
    private Integer memberCount;
}
