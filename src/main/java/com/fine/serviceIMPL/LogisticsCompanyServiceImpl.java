package com.fine.serviceIMPL;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fine.Dao.LogisticsCompanyMapper;
import com.fine.modle.LogisticsCompany;
import com.fine.service.LogisticsCompanyService;

@Service
public class LogisticsCompanyServiceImpl extends ServiceImpl<LogisticsCompanyMapper, LogisticsCompany> implements LogisticsCompanyService {
}
