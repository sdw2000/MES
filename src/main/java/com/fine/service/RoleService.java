package com.fine.service;

import com.fine.Utils.ResponseResult;
import com.fine.modle.Role;

import java.util.List;

/**
 * 角色Service接口
 */
public interface RoleService {

    /**
     * 获取所有角色列表
     */
    ResponseResult<?> getAllRoles();

    /**
     * 分页查询角色列表
     */
    ResponseResult<?> getRolePage(Integer page, Integer size, String keyword);

    /**
     * 根据ID获取角色
     */
    ResponseResult<?> getRoleById(Long id);

    /**
     * 创建角色
     */
    ResponseResult<?> createRole(Role role);

    /**
     * 更新角色
     */
    ResponseResult<?> updateRole(Role role);

    /**
     * 删除角色
     */
    ResponseResult<?> deleteRole(Long id);

    /**
     * 根据用户ID获取角色列表
     */
    List<Role> getRolesByUserId(Long userId);

    /**
     * 根据用户ID获取角色名称列表
     */
    List<String> getRoleNamesByUserId(Long userId);

    /**
     * 为用户分配角色
     */
    ResponseResult<?> assignRolesToUser(Long userId, List<Long> roleIds);

    /**
     * 获取用户的角色ID列表
     */
    ResponseResult<?> getUserRoleIds(Long userId);

    /**
     * 创建或更新角色（用于导入）
     */
    ResponseResult<?> createOrUpdateRole(Role role);
}
