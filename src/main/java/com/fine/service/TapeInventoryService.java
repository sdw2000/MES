package com.fine.service;

import com.fine.Utils.ResponseResult;
import com.fine.modle.TapeInventory;

public interface TapeInventoryService {
	


	ResponseResult<?> queryWithPagination(int current, int size, String sort,String materialNumber);

	ResponseResult<?> updatDepoService(TapeInventory tape);

	ResponseResult<?> deleteTapeById(int id);

	ResponseResult<?> creatDepoService(TapeInventory tape);

	ResponseResult<?> queryWithPagination(int current, int size, String sort, String materialNumber,
			String productionBatchNumber);
	



}
