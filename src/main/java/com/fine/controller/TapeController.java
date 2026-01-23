package com.fine.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fine.Utils.ResponseResult;
import com.fine.modle.Tape;
import com.fine.serviceIMPL.TapeService;

@RestController
@RequestMapping("/tapes")
@PreAuthorize("hasAnyAuthority('admin','warehouse','sales','production')")
public class TapeController {

    @Autowired
    private TapeService tapeService;
    
    @GetMapping("/getAllTapes")
    @PreAuthorize("hasAnyAuthority('admin','warehouse','sales','production')")
    public ResponseResult<IPage<Tape>> listTapes(@RequestParam(required = false) String materialName,
                                     @RequestParam(required = false) String partNumber,
                                     @RequestParam(defaultValue = "1") int page,
                                     @RequestParam(defaultValue = "10") int size) {
    	System.out.println(size+materialName+partNumber+page);
    	 Page<Tape> tapePage = new Page<>(page, size);
    	    QueryWrapper<Tape> queryWrapper = new QueryWrapper<>();

    	    if (materialName != null && !materialName.isEmpty()) {
    	        queryWrapper.like("material_name", materialName);
    	    }
    	    if (partNumber != null && !partNumber.isEmpty()) {
    	        queryWrapper.like("part_number", partNumber);
    	    }
    	    queryWrapper.eq("is_deleted", 0);
    	    // Performing join operation and selecting required fields
    	    IPage<Tape> result = tapeService.getTapeDetails(tapePage,queryWrapper);
    	    
    	    return new ResponseResult<>(20000, "操作成功", result);
    }
    
    @GetMapping("/getAll")
    @PreAuthorize("hasAnyAuthority('admin','warehouse','sales','production')")
    public ResponseResult<List<Tape>> getAll() {
    	
    List<Tape>	results=   tapeService.getAll();
    	
    	
    	
    	return new ResponseResult<>(20000, "操作成功", results);
    }
    
    

    @PostMapping("/upload")
    @PreAuthorize("hasAnyAuthority('admin','warehouse')")
    public ResponseResult<Void> uploadFile(@RequestParam("file") MultipartFile file) {
    	System.out.println("jinlaile "+file.getSize());
        try {
            tapeService.save(file);
            return new ResponseResult<>(20000, "操作成功");
        } catch (Exception e) {
            return new ResponseResult<>(50000, "操作失败");
        }
    }
}