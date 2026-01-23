package com.fine.serviceIMPL;

import java.util.Map;

import org.apache.commons.collections4.map.HashedMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fine.Dao.TapeInventoryDao;
import com.fine.Utils.ResponseResult;
import com.fine.modle.TapeInventory;
import com.fine.service.TapeInventoryService;

@Service
public class TapeInventoryImpl implements TapeInventoryService {
	@Autowired
	private TapeInventoryDao tapeInventoryDao;


		@Override
	public ResponseResult<?> queryWithPagination(int current, int size, String sort,String materialNumber,String productionBatchNumber ) {
		System.out.println(productionBatchNumber);
		Page<TapeInventory> page = new Page<>(current,size);
		page.setOptimizeCountSql(false);
		 // 创建查询条件构造器
	    QueryWrapper<TapeInventory> queryWrapper = new QueryWrapper<>();
	    queryWrapper.eq("logical_delete_code", 0);
	    if (sort != null && !sort.isEmpty()) {
	        if (sort.startsWith("+") || sort.startsWith("%2B")) {
	            // 升序排序
	            queryWrapper.orderByAsc(sort.substring(1));
	        } else if (sort.startsWith("-")) {
	            // 降序排序
	            queryWrapper.orderByDesc(sort.substring(1));
	        }
	    }
	    if (materialNumber != null && !materialNumber.isEmpty()) {
            queryWrapper.like("material_number", materialNumber);
        }
        if (productionBatchNumber != null && !productionBatchNumber.isEmpty()) {
            queryWrapper.like("production_batch_number", productionBatchNumber);
        }
   
	    IPage<TapeInventory> iPage = tapeInventoryDao.selectPage(page, queryWrapper);
		Map<String, Object> map=new HashedMap<>();
		map.put("total",iPage.getTotal() );
		map.put( "items", iPage.getRecords());
		
		return new ResponseResult<>(20000, "登陆成功", map);
	}
		@Override
	public ResponseResult<?> updatDepoService(TapeInventory tape) {
		// 创建更新条件
        UpdateWrapper<TapeInventory> updateWrapper = new UpdateWrapper<>();
        updateWrapper.eq("id", tape.getId());

        // 执行更新操作
        tapeInventoryDao.update(tape, updateWrapper);

        // 返回结果
        return new ResponseResult<>(20000, "success");
		
	
	}


	@Override
	public ResponseResult<?> deleteTapeById(int id) {
		UpdateWrapper<TapeInventory> updateWrapper = new UpdateWrapper<>();
		ResponseResult<?> responseResult;
        updateWrapper.eq("id", id).set("logical_delete_code", 1);
        boolean result=   tapeInventoryDao.update(null, updateWrapper) > 0;
        if (result) {
            responseResult=new ResponseResult<>(20000, "删除成功", true);
        } else {
        	responseResult=new ResponseResult<>(50000, "删除失败", false);
        }
		
        return responseResult;
	}


		@Override
	public ResponseResult<?> creatDepoService(TapeInventory tape) {
            int i=tapeInventoryDao.insert(tape);
            if (i==1) {
                return   new ResponseResult<>(20000, "新增成功", true);
            } else {
            	return   new ResponseResult<>(50000, "新增失败", false);
            }
		
		
	}

	@Override
	public ResponseResult<?> queryWithPagination(int current, int size, String sort, String materialNumber) {
		// Overloaded method - delegates to main implementation with null productionBatchNumber
		return queryWithPagination(current, size, sort, materialNumber, null);
	}

}
