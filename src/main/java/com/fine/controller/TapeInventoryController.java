package com.fine.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fine.Utils.ResponseResult;
import com.fine.modle.TapeInventory;
import com.fine.serviceIMPL.TapeInventoryImpl;
import org.springframework.security.access.prepost.PreAuthorize;

@PreAuthorize("hasAnyAuthority('admin','warehouse')")
@RestController
public class TapeInventoryController {
	
	@Autowired
	private TapeInventoryImpl tapeInventoryImpl;
	
	@GetMapping("/depot/list")
    public ResponseResult<?> getPaginatedData(@RequestParam("page") int page, @RequestParam("limit") int limit,@RequestParam("sort") String sort,String materialNumber, String productionBatchNumber) {
        return tapeInventoryImpl.queryWithPagination(page, limit,sort,materialNumber,productionBatchNumber);
    }
	
	@PostMapping("/depot/update")
	public ResponseResult<?> updatDepo(@RequestBody TapeInventory tape){
		
		
		return tapeInventoryImpl.updatDepoService(tape);
	}
	
	@GetMapping("/depot/delete")
	public ResponseResult<?> deleteTapeById(@RequestParam("id") int id){
		System.out.println(id);
	
		return tapeInventoryImpl.deleteTapeById(id);
	}
		
	@PostMapping("/depot/create")
	public ResponseResult<?> creatDepot(@RequestBody TapeInventory tape){
		
		
		return tapeInventoryImpl.creatDepoService(tape);
	}
    
	
	

}
