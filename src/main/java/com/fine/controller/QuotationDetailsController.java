package com.fine.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.fine.Utils.ResponseResult;
import com.fine.service.QuotationDetailsService;


@RestController
@RequestMapping("/quotationDetails")
@PreAuthorize("hasAnyAuthority('admin','sales')")
public class QuotationDetailsController {
	
	
	@Autowired
	   private	QuotationDetailsService quotationDetailsService; 
		
		@PostMapping("/upload")
	    public ResponseResult<?> uploadFile(@RequestParam("file") MultipartFile file) {
	    	System.out.println("jinlaile "+file.getSize());
	        try {
	        	quotationDetailsService.save(file);
	            return new ResponseResult<>(20000, "操作成功");
	        } catch (Exception e) {
	            return new ResponseResult<>(50000, "操作失败");
	        }
	    }


}
