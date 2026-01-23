package com.fine.Dao;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fine.modle.Menu;


@Mapper
public interface MenuMapper extends BaseMapper<Menu> {
    @Select("SELECT DISTINCT r.name\r\n" + 
    		"FROM users u\r\n" + 
    		"LEFT JOIN user_roles ur ON ur.user_id = u.id\r\n" + 
    		"LEFT JOIN roles r ON ur.role_id = r.id\r\n" + 
    		"WHERE u.id  = #{userid}")
    List<String> selectPermsByUserId(Long id);
}
