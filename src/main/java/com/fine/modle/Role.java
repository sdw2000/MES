package com.fine.modle;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 角色实体类
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@TableName("roles")
public class Role implements Serializable {

    private static final long serialVersionUID = 1L;

    @JsonSerialize(using = ToStringSerializer.class)
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 角色标识(如admin/sales)
     */
    private String name;

    /**
     * 显示名称
     */
    @TableField(value = "display_name")
    private String displayName;

    /**
     * 角色描述
     */
    private String description;    /**
     * 状态(1启用 0禁用)
     */
    private Integer status;

    /**
     * 创建时间
     */
    @TableField(value = "created_at")
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    @TableField(value = "updated_at")
    private LocalDateTime updatedAt;
}
