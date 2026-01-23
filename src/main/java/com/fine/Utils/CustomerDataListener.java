package com.fine.Utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.event.AnalysisEventListener;
import com.fine.Dao.CustomerRepository;
import com.fine.modle.Customer;

public class CustomerDataListener extends AnalysisEventListener<Customer> {
	
	private static final Logger logger = LoggerFactory.getLogger(CustomerDataListener.class);
    private CustomerRepository customerRepository;

    public CustomerDataListener(CustomerRepository customerRepository) {
        this.customerRepository = customerRepository;
    }

    @Override
    public void invoke(Customer customer, AnalysisContext context) {
        try {
        	customerRepository.insert(customer);
            logger.info("插入成功: {}", customer);
        } catch (Exception e) {
            logger.error("插入失败: {}", customer, e);
        }
    }

    @Override
    public void doAfterAllAnalysed(AnalysisContext context) {
        // No operation needed here
    }
	

}
