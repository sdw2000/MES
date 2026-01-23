package com.fine.Dao;

import org.apache.ibatis.annotations.Mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fine.modle.Contact;

@Mapper
public interface ContactDao extends BaseMapper<Contact>{

}
