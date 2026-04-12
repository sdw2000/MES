package com.fine.serviceIMPL;

import java.util.List;
import java.util.Objects;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;


import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fine.Dao.MenuMapper;
import com.fine.Dao.UserMapper;
import com.fine.modle.LoginUser;
import com.fine.modle.User;



/**
* @Author 三更  B站： https://space.bilibili.com/663528522
*/
@Service
public class UserDetailsServiceImpl implements UserDetailsService {


    @Autowired
    private UserMapper userMapper;


    @Autowired
    private MenuMapper menuMapper;


    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        if (username != null) {
            username = username.trim();
        }
        System.out.println(">>> UserDetailsServiceImpl.loadUserByUsername: " + username);
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getUsername,username);
        User user = userMapper.selectOne(wrapper);
        if(Objects.isNull(user)){
            System.out.println(">>> User not found in database: " + username);
            throw new UsernameNotFoundException("用户名或密码错误");
        }
        System.out.println(">>> User found: " + user.getUsername());
        System.out.println(">>> Database Password (Hash): " + user.getPassword());
        
        // 调用接口获取权限信息（并做角色别名扩展：涂布/coating -> production）
        List<String> permissionKeyList = normalizeAuthorities(menuMapper.selectPermsByUserId(user.getId()));
        return new LoginUser(user,permissionKeyList);
    }

    private List<String> normalizeAuthorities(List<String> rawPermissions) {
        Set<String> normalized = new LinkedHashSet<>();
        if (rawPermissions != null) {
            for (String raw : rawPermissions) {
                String role = raw == null ? "" : raw.trim();
                if (role.isEmpty()) {
                    continue;
                }
                String lower = role.toLowerCase(Locale.ROOT);
                normalized.add(lower);

                // 兼容“涂布”岗位：具备 coating 与 production 双权限
                if ("涂布".equals(role) || "coating".equals(lower)) {
                    normalized.add("coating");
                    normalized.add("production");
                }
            }
        }
        return new ArrayList<>(normalized);
    }
}
