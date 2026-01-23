package com.fine.Dao;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Constants;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fine.modle.Tape;

@Mapper
public interface TapeMapper extends BaseMapper<Tape> {

	@Select("SELECT* FROM tapes ${ew.customSqlSegment}")
	IPage<Tape> getTapeDetails(Page<Tape> tapePage,  @Param(Constants.WRAPPER)QueryWrapper<Tape> queryWrapper);

}
