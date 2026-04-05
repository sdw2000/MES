package com.fine.Dao;

import org.springframework.stereotype.Repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fine.modle.User;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;


@Repository 
public interface UserMapper extends BaseMapper<User> {

    /**
     * 根据角色关键字查询用户列表
     */
    @Select("<script>" +
            "SELECT DISTINCT u.* FROM users u " +
            "INNER JOIN user_roles ur ON u.id = ur.user_id " +
            "INNER JOIN roles r ON r.id = ur.role_id " +
            "WHERE u.status = 0 AND u.del_flag = 0 " +
            "<if test='roleKeyword != null and roleKeyword != \"\"'>" +
            "AND (r.name LIKE CONCAT('%', #{roleKeyword}, '%') " +
            "OR r.display_name LIKE CONCAT('%', #{roleKeyword}, '%')) " +
            "</if>" +
            "ORDER BY u.username ASC" +
            "</script>")
    List<User> selectUsersByRoleKeyword(@Param("roleKeyword") String roleKeyword);


    // NOTE: Using BaseMapper methods (selectOne) is preferred. 
    // This custom query referenced 'sys_user' which is deprecated.
    // If needed, update to 'users' and 'username'.
    // @Select("select * from sys_user where user_name=#{username}")
    // User findByUsername(String username);
}
