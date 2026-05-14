package com.fine.Dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fine.modle.BankAccount;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface BankAccountMapper extends BaseMapper<BankAccount> {
}
