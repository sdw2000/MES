package com.fine.Utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.event.AnalysisEventListener;
import com.fine.Dao.QuotationDetailsMapper;
import com.fine.modle.QuotationDetail;

public class QuotationDetailsDataListener extends AnalysisEventListener<QuotationDetail>{
	

    private static final Logger logger = LoggerFactory.getLogger(QuotationDataListener.class);
    private QuotationDetailsMapper quotationDetailsMapper;

    public  QuotationDetailsDataListener(QuotationDetailsMapper quotationDetailsMapper) {
        this.quotationDetailsMapper = quotationDetailsMapper;
    }

    @Override
    public void invoke(QuotationDetail quotationDetail, AnalysisContext context) {
        try {
        	quotationDetailsMapper.insert(quotationDetail);
            logger.info("插入成功: {}",quotationDetail);
        } catch (Exception e) {
            logger.error("插入失败: {}", quotationDetail, e);
        }
    }

    @Override
    public void doAfterAllAnalysed(AnalysisContext context) {
        // No operation needed here
    }
}
