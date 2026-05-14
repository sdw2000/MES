package com.fine.Dao.stock;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fine.model.stock.PackageStock;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface PackageStockMapper extends BaseMapper<PackageStock> {
}
