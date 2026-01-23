package com.fine.Dao;

import org.apache.ibatis.annotations.Mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fine.modle.Quotation;

/**
 * 报价单数据访问层
 */
@Mapper
public interface QuotationMapper extends BaseMapper<Quotation> {
    // MyBatis-Plus 提供了基本的 CRUD 方法
}
