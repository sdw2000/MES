package com.fine.serviceIMPL;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fine.Dao.RoleMapper;
import com.fine.Dao.UserRoleMapper;
import com.fine.Dao.UserMapper;
import com.fine.Utils.ResponseResult;
import com.fine.modle.Role;
import com.fine.modle.UserRole;
import com.fine.service.RoleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 角色Service实现类
 */
@Service
public class RoleServiceImpl implements RoleService {

    @Autowired
    private RoleMapper roleMapper;

    @Autowired
    private UserRoleMapper userRoleMapper;

    @Autowired
    private UserMapper userMapper; // check user existence before assigning roles

    @Override
    public ResponseResult<?> getAllRoles() {
        try {
            LambdaQueryWrapper<Role> wrapper = new LambdaQueryWrapper<>();
            wrapper.orderByAsc(Role::getId);
            List<Role> roles = roleMapper.selectList(wrapper);
            return new ResponseResult<>(200, "查询成功", roles);
        } catch (Exception e) {
            e.printStackTrace();
            return new ResponseResult<>(500, "查询失败: " + e.getMessage());
        }
    }

    @Override
    public ResponseResult<?> getRolePage(Integer page, Integer size, String keyword) {
        try {
            int current = (page == null || page < 1) ? 1 : page;
            int pageSize = (size == null || size < 1) ? 10 : size;

            LambdaQueryWrapper<Role> wrapper = new LambdaQueryWrapper<>();
            if (keyword != null && !keyword.trim().isEmpty()) {
                wrapper.and(w -> w.like(Role::getName, keyword.trim())
                                  .or()
                                  .like(Role::getDisplayName, keyword.trim()));
            }
            wrapper.orderByAsc(Role::getId);

            Page<Role> pageParam = new Page<>(current, pageSize);
            pageParam.setOptimizeCountSql(false);
            IPage<Role> result = roleMapper.selectPage(pageParam, wrapper);

            Map<String, Object> data = new HashMap<>();
            data.put("list", result.getRecords());
            data.put("total", result.getTotal());
            data.put("page", result.getCurrent());
            data.put("size", result.getSize());
            return new ResponseResult<>(200, "查询成功", data);
        } catch (Exception e) {
            e.printStackTrace();
            return new ResponseResult<>(500, "查询失败: " + e.getMessage());
        }
    }

    @Override
    public ResponseResult<?> getRoleById(Long id) {
        try {
            Role role = roleMapper.selectById(id);
            if (role == null) {
                return new ResponseResult<>(404, "角色不存在");
            }
            return new ResponseResult<>(200, "查询成功", role);
        } catch (Exception e) {
            e.printStackTrace();
            return new ResponseResult<>(500, "查询失败: " + e.getMessage());
        }
    }

    @Override
    public ResponseResult<?> createRole(Role role) {
        try {
            // 检查角色名称是否已存在
            if (roleMapper.checkNameExists(role.getName(), 0L) > 0) {
                return new ResponseResult<>(400, "角色标识已存在");
            }

            role.setCreatedAt(LocalDateTime.now());
            role.setUpdatedAt(LocalDateTime.now());
            if (role.getStatus() == null) {
                role.setStatus(1);
            }

            int result = roleMapper.insert(role);
            if (result > 0) {
                return new ResponseResult<>(200, "创建成功", role);
            }
            return new ResponseResult<>(500, "创建失败");
        } catch (Exception e) {
            e.printStackTrace();
            return new ResponseResult<>(500, "创建失败: " + e.getMessage());
        }
    }

    @Override
    public ResponseResult<?> updateRole(Role role) {
        try {
            Role existing = roleMapper.selectById(role.getId());
            if (existing == null) {
                return new ResponseResult<>(404, "角色不存在");
            }

            // 检查角色名称是否重复
            if (!existing.getName().equals(role.getName())) {
                if (roleMapper.checkNameExists(role.getName(), role.getId()) > 0) {
                    return new ResponseResult<>(400, "角色标识已存在");
                }
            }

            role.setUpdatedAt(LocalDateTime.now());
            int result = roleMapper.updateById(role);
            if (result > 0) {
                return new ResponseResult<>(200, "更新成功");
            }
            return new ResponseResult<>(500, "更新失败");
        } catch (Exception e) {
            e.printStackTrace();
            return new ResponseResult<>(500, "更新失败: " + e.getMessage());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResponseResult<?> deleteRole(Long id) {
        try {
            Role role = roleMapper.selectById(id);
            if (role == null) {
                return new ResponseResult<>(404, "角色不存在");
            }

            // 不允许删除admin角色
            if ("admin".equals(role.getName())) {
                return new ResponseResult<>(400, "不能删除超级管理员角色");
            }

            // 删除用户角色关联
            userRoleMapper.deleteByRoleId(id);

            // 删除角色
            int result = roleMapper.deleteById(id);
            if (result > 0) {
                return new ResponseResult<>(200, "删除成功");
            }
            return new ResponseResult<>(500, "删除失败");
        } catch (Exception e) {
            e.printStackTrace();
            return new ResponseResult<>(500, "删除失败: " + e.getMessage());
        }
    }

    @Override
    public List<Role> getRolesByUserId(Long userId) {
        return roleMapper.selectRolesByUserId(userId);
    }

    @Override
    public List<String> getRoleNamesByUserId(Long userId) {
        return roleMapper.selectRoleNamesByUserId(userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResponseResult<?> assignRolesToUser(Long userId, List<Long> roleIds) {
        try {
            // ensure user exists to avoid FK constraint violation
            if (userMapper.selectById(userId) == null) {
                return new ResponseResult<>(404, "用户不存在");
            }

            // 先删除用户现有的所有角色
            userRoleMapper.deleteByUserId(userId);

            // 添加新的角色关联
            if (roleIds != null && !roleIds.isEmpty()) {
                for (Long roleId : roleIds) {
                    UserRole userRole = new UserRole();
                    userRole.setUserId(userId);
                    userRole.setRoleId(roleId);
                    userRole.setCreatedAt(LocalDateTime.now());
                    userRoleMapper.insert(userRole);
                }
            }

            return new ResponseResult<>(200, "角色分配成功");
        } catch (Exception e) {
            e.printStackTrace();
            return new ResponseResult<>(500, "角色分配失败: " + e.getMessage());
        }
    }

    @Override
    public ResponseResult<?> getUserRoleIds(Long userId) {
        try {
            List<Long> roleIds = userRoleMapper.selectRoleIdsByUserId(userId);
            return new ResponseResult<>(200, "查询成功", roleIds);
        } catch (Exception e) {
            e.printStackTrace();
            return new ResponseResult<>(500, "查询失败: " + e.getMessage());
        }
    }

    @Override
    public ResponseResult<?> createOrUpdateRole(Role role) {
        try {
            // 根据角色标识查找是否已存在
            LambdaQueryWrapper<Role> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(Role::getName, role.getName());
            Role existing = roleMapper.selectOne(wrapper);

            if (existing != null) {
                // 更新现有角色
                existing.setDisplayName(role.getDisplayName());
                existing.setDescription(role.getDescription());
                existing.setStatus(role.getStatus());
                existing.setUpdatedAt(LocalDateTime.now());
                roleMapper.updateById(existing);
                return new ResponseResult<>(20000, "更新成功", existing);
            } else {
                // 创建新角色
                role.setCreatedAt(LocalDateTime.now());
                role.setUpdatedAt(LocalDateTime.now());
                if (role.getStatus() == null) {
                    role.setStatus(1);
                }
                roleMapper.insert(role);
                return new ResponseResult<>(20000, "创建成功", role);
            }
        } catch (Exception e) {
            e.printStackTrace();
            return new ResponseResult<>(500, "操作失败: " + e.getMessage());
        }
    }
}
