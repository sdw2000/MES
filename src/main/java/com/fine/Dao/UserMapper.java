package com.fine.Dao;

import org.springframework.stereotype.Repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fine.modle.User;


@Repository 
public interface UserMapper extends BaseMapper<User> {


    // NOTE: Using BaseMapper methods (selectOne) is preferred. 
    // This custom query referenced 'sys_user' which is deprecated.
    // If needed, update to 'users' and 'username'.
    // @Select("select * from sys_user where user_name=#{username}")
    // User findByUsername(String username);
}
