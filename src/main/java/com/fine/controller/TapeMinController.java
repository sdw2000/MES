package com.fine.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fine.Utils.ResponseResult;
import com.fine.modle.TapeMin;
import com.fine.service.TapeMinService;

@PreAuthorize("hasAnyAuthority('admin','warehouse','sales','production','rd','finance','quality')")
@RestController
public class TapeMinController {
	
	@Autowired
	private TapeMinService tapeMinService;
	
	@PreAuthorize("hasAnyAuthority('admin','warehouse','sales','production','rd','finance','quality')")
	@RequestMapping("/tapemin")
	public ResponseResult<List<TapeMin>> getTapeMinList(@RequestParam String query,@RequestParam Integer id) {
		System.out.println(query+"------------------------------");
		
		return tapeMinService.queryWithPartNumber(query, id);
		
	}

}
