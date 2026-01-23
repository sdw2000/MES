package com.fine.serviceIMPL;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.alibaba.excel.EasyExcel;
import com.fine.Dao.QuotationDetailsMapper;
import com.fine.Utils.QuotationDetailsDataListener;
import com.fine.modle.QuotationDetail;
import com.fine.service.QuotationDetailsService;

@Service
public class QuotationDetailsServiceImpl implements QuotationDetailsService{
	@Autowired
	private QuotationDetailsMapper quotationDetailsMapper;
	@Override
	 @Transactional
	public void save(MultipartFile file) {
		try {
    		System.out.println("进来这里了");
            EasyExcel.read(file.getInputStream(), QuotationDetail.class, new QuotationDetailsDataListener(quotationDetailsMapper))
                     .sheet()
                     .doRead();
        } catch (Exception e) {  // Catching general Exception to handle all types of exceptions
            e.printStackTrace();
            throw new RuntimeException("Error processing file", e);
        }
		
		
		
	}

}
