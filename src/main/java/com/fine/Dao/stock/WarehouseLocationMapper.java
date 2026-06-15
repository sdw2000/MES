package com.fine.Dao.stock;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fine.modle.stock.WarehouseLocation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface WarehouseLocationMapper extends BaseMapper<WarehouseLocation> {
    
    @Select("SELECT l.*, w.warehouse_name as warehouseName " +
            "FROM warehouse_location l " +
            "LEFT JOIN warehouse w ON l.warehouse_id = w.id " +
            " ${ew.customSqlSegment}")
    IPage<WarehouseLocation> selectLocationPage(Page<WarehouseLocation> page, @Param("ew") com.baomidou.mybatisplus.core.conditions.Wrapper<WarehouseLocation> queryWrapper);
}
