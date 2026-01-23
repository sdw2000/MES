package com.fine.Dao;

import org.apache.ibatis.annotations.Mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fine.modle.Customer;

@Mapper
public interface CustomerRepository extends BaseMapper<Customer> {

}
