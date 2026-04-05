package com.fine.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.fine.Utils.ResponseResult;
import com.fine.modle.SalesReturn;

public interface SalesReturnService extends IService<SalesReturn> {

    ResponseResult<?> getAllReturns(Integer pageNum, Integer pageSize, String returnNo, String customer,
                                    String startDate, String endDate, String status);

    ResponseResult<?> createReturn(SalesReturn salesReturn);

    ResponseResult<?> updateReturn(SalesReturn salesReturn);

    ResponseResult<?> deleteReturn(String returnNo);

    ResponseResult<?> getReturnDetail(String returnNo);

    ResponseResult<?> generateReturnNo(String customerCode, java.time.LocalDate returnDate);

    ResponseResult<?> reconciliationSummary(String month);

    ResponseResult<?> getReturnableOrderItems(String orderNo, String excludeReturnNo);

    ResponseResult<?> getReturnAuditLogs(String returnNo, Integer pageNum, Integer pageSize);
}
