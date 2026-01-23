package com.fine.Utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.event.AnalysisEventListener;
import com.fine.Dao.QuotationMapper;
import com.fine.modle.Quotation;

public class QuotationDataListener extends AnalysisEventListener<Quotation>{
	

	    private static final Logger logger = LoggerFactory.getLogger(QuotationDataListener.class);
	    private QuotationMapper quotationMapper;

	    public  QuotationDataListener(QuotationMapper quotationMapper) {
	        this.quotationMapper = quotationMapper;
	    }

	    @Override
	    public void invoke(Quotation quotation, AnalysisContext context) {
	        try {
	            quotationMapper.insert(quotation);
	            logger.info("插入成功: {}",quotation);
	        } catch (Exception e) {
	            logger.error("插入失败: {}", quotation, e);
	        }
	    }

	    @Override
	    public void doAfterAllAnalysed(AnalysisContext context) {
	        // No operation needed here
	    }
	}


