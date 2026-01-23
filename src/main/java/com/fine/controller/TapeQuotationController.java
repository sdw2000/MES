package com.fine.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fine.Utils.ResponseResult;
import com.fine.service.QuotationService;

@RestController
@RequestMapping("/tapequotation")
@PreAuthorize("hasAuthority('admin')")
public class TapeQuotationController {
	
	@Autowired
	private QuotationService quotationService;
	
	@GetMapping("/getAll")
	public ResponseResult<?> fetchQueryList(@RequestParam(required = false) String customerCode,
            @RequestParam(required = false) String shortName,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
		
		
		
		return quotationService.fetchQueryList(customerCode, shortName,page,size);
	}
	
	
	
	
	
	
	
	
	

}
