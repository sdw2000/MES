package com.fine.Dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fine.modle.Role;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 角色Mapper
 */
@Mapper
public interface RoleMapper extends BaseMapper<Role> {    /**
     * 根据用户ID查询角色列表
     */
    @Select("SELECT r.* FROM roles r " +
            "INNER JOIN user_roles ur ON r.id = ur.role_id " +
            "WHERE ur.user_id = #{userId}")
    List<Role> selectRolesByUserId(@Param("userId") Long userId);

    /**
     * 根据用户ID查询角色名称列表
     */
    @Select("SELECT r.name FROM roles r " +
            "INNER JOIN user_roles ur ON r.id = ur.role_id " +
            "WHERE ur.user_id = #{userId}")
    List<String> selectRoleNamesByUserId(@Param("userId") Long userId);

    /**
     * 检查角色名称是否存在
     */
    @Select("SELECT COUNT(*) FROM roles WHERE name = #{name} AND id != #{excludeId}")
    int checkNameExists(@Param("name") String name, @Param("excludeId") Long excludeId);
}
