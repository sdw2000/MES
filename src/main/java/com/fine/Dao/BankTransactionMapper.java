package com.fine.Dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fine.modle.BankTransaction;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface BankTransactionMapper extends BaseMapper<BankTransaction> {
}
