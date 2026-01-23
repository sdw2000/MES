package com.fine.serviceIMPL;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fine.Dao.TapeMinMapper;
import com.fine.Utils.ResponseResult;
import com.fine.modle.TapeMin;
import com.fine.service.TapeMinService;

@Service
public class TapeMinServiceImpl implements TapeMinService{
	
	@Autowired
	private TapeMinMapper tapeMinMapper;
	
	@Override
	public ResponseResult<List<TapeMin>> queryWithPartNumber(String query,Integer id) {
//		System.out.println(partNumber);
//		QueryWrapper<TapeMin> queryWrapper=new QueryWrapper<>();
//		queryWrapper.like("t.part_number", partNumber);
//		queryWrapper.eq("q.customer_id", id);
		System.out.println(query+id);
		
		List<TapeMin> list=tapeMinMapper.selectUserOrdersqueryWithPartNumber(query,id);
		System.out.println(list);
		return new ResponseResult<List<TapeMin>>(20000, "查询成功", list);
	}
	@Override
	public boolean saveBatch(Collection<TapeMin> entityList, int batchSize) {
		// MyBatis-Plus interface method - not implemented
		return false;
	}

	@Override
	public boolean saveOrUpdateBatch(Collection<TapeMin> entityList, int batchSize) {
		// MyBatis-Plus interface method - not implemented
		return false;
	}

	@Override
	public boolean updateBatchById(Collection<TapeMin> entityList, int batchSize) {
		// MyBatis-Plus interface method - not implemented
		return false;
	}

	@Override
	public boolean saveOrUpdate(TapeMin entity) {
		// MyBatis-Plus interface method - not implemented
		return false;
	}

	@Override
	public TapeMin getOne(Wrapper<TapeMin> queryWrapper, boolean throwEx) {
		// MyBatis-Plus interface method - not implemented
		return null;
	}

	@Override
	public Map<String, Object> getMap(Wrapper<TapeMin> queryWrapper) {
		// MyBatis-Plus interface method - not implemented
		return null;
	}

	@Override
	public <V> V getObj(Wrapper<TapeMin> queryWrapper, Function<? super Object, V> mapper) {
		// MyBatis-Plus interface method - not implemented
		return null;
	}

	@Override
	public BaseMapper<TapeMin> getBaseMapper() {
		// MyBatis-Plus interface method - not implemented
		return null;
	}

	@Override
	public Class<TapeMin> getEntityClass() {
		// MyBatis-Plus interface method - not implemented
		return null;
	}

	

	
	
	

}
