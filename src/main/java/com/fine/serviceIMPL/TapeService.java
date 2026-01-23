package com.fine.serviceIMPL;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.alibaba.excel.EasyExcel;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fine.Dao.TapeMapper;
import com.fine.Utils.TapeDataListener;
import com.fine.modle.Tape;



	@Service
	public class TapeService {

	    @Autowired
	    private TapeMapper tapeMapper;
//	    @Transactional
	    public void save(MultipartFile file) {
	    	try {
	            EasyExcel.read(file.getInputStream(), Tape.class, new TapeDataListener(tapeMapper))
	                     .sheet()
	                     .doRead();
	        } catch (Exception e) {  // Catching general Exception to handle all types of exceptions
	            e.printStackTrace();
	            throw new RuntimeException("Error processing file", e);
	        }
	    	
	}
	    public List<Tape> getAll() {
	    	
	    	return tapeMapper.selectList(null);
			
		}		public IPage<Tape> getTapeDetails(Page<Tape> tapePage, QueryWrapper<Tape> queryWrapper) {
			return this.tapeMapper.getTapeDetails(tapePage, queryWrapper);
		}
}