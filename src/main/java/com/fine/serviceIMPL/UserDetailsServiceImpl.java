package com.fine.serviceIMPL;

import java.util.List;
import java.util.Objects;


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
        System.out.println(">>> UserDetailsServiceImpl.loadUserByUsername: " + username);
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getUsername,username);
        User user = userMapper.selectOne(wrapper);
        if(Objects.isNull(user)){
            System.out.println(">>> User not found in database: " + username);
            throw new RuntimeException("用户名或密码错误");
        }
        System.out.println(">>> User found: " + user.getUsername());
        System.out.println(">>> Database Password (Hash): " + user.getPassword());
        
        // 调用接口获取权限信息
        List<String> permissionKeyList =  menuMapper.selectPermsByUserId(user.getId());
        return new LoginUser(user,permissionKeyList);
    }
}
