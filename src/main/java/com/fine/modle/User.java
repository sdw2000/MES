package com.fine.modle;

import java.io.Serializable;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.IdType;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用户(User)实体类
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@TableName(value = "users")
public class User implements Serializable {

    private static final long serialVersionUID = 3634428493171883054L;

    // 主键
    // 使用 ToStringSerializer 将 Long 类型的 id 序列化为 String 返回给前端，解决精度丢失问题
    @JsonSerialize(using = ToStringSerializer.class)
    @TableId(type = IdType.AUTO)
    private Long id;

    // 用户名
    private String username;

    // 密码
    private String password;

    // 真实姓名
    @TableField("real_name")
    private String realName;

    // 关联人员ID（production_staff.id）
    @TableField("staff_id")
    private Long staffId;

    // 邮箱
    private String email;

    // 账号状态（0正常 1停用）
    private Integer status;

    // 创建人
    @TableField("created_by")
    private Long createdBy;

    // 创建时间
    @TableField("created_at")
    private LocalDateTime createdAt;

    // 更新时间
    @TableField("updated_at")
    private LocalDateTime updatedAt;

    // 删除标志（0代表未删除，1代表已删除）
    @TableField("del_flag")
    private Integer delFlag;
}

