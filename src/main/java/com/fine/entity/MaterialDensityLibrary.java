package com.fine.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 常用材质密度库
 */
@Data
@TableName("material_density_library")
public class MaterialDensityLibrary {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 材质英文名 */
    private String materialEnName;

    /** 材质中文名 */
    private String materialCnName;

    /** 密度，单位 g/cm3 */
    private Double density;

    /** 备注 */
    private String remark;

    /** 启用状态：1启用，0禁用 */
    private Integer isActive;

    /** 逻辑删除 */
    @TableLogic(value = "0", delval = "1")
    private Integer deleted;

    private String createBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    private String updateBy;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
