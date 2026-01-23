package com.fine.Dao;

import org.apache.ibatis.annotations.Mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fine.modle.QuotationItem;

/**
 * 报价单明细数据访问层
 */
@Mapper
public interface QuotationItemMapper extends BaseMapper<QuotationItem> {
    // MyBatis-Plus 提供了基本的 CRUD 方法
}
