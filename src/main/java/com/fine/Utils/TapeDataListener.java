package com.fine.Utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.event.AnalysisEventListener;
import com.fine.Dao.TapeMapper;
import com.fine.modle.Tape;



	public class TapeDataListener extends AnalysisEventListener<Tape> {

	    private static final Logger logger = LoggerFactory.getLogger(TapeDataListener.class);
	    private TapeMapper tapeMapper;

	    public TapeDataListener(TapeMapper tapeMapper) {
	        this.tapeMapper = tapeMapper;
	    }

	    @Override
	    public void invoke(Tape tape, AnalysisContext context) {
	        try {
	        	System.out.println(tape);
	            tapeMapper.insert(tape);
	            logger.info("插入成功: {}", tape);
	        } catch (Exception e) {
	            logger.error("插入失败: {}", tape, e);
	        }
	    }

	    @Override
	    public void doAfterAllAnalysed(AnalysisContext context) {
	        // No operation needed here
	    }
	}