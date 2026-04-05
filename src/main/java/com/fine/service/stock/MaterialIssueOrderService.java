package com.fine.service.stock;

import com.fine.modle.stock.MaterialIssueOrder;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public interface MaterialIssueOrderService {

    MaterialIssueOrder createIssueOrder(List<Long> lockIds,
                                        LocalDate planDate,
                                        String materialCode,
                                        String orderNo,
                                        String remark) throws Exception;

    MaterialIssueOrder createIssueOrderBySchedule(Long scheduleId,
                                                  String processType,
                                                  LocalDate planDate,
                                                  String materialCode,
                                                  String orderNo,
                                                  String remark) throws Exception;

    MaterialIssueOrder getIssueOrderDetail(String issueNo);

    Map<String, Object> getIssueOrderPage(int current,
                                          int size,
                                          LocalDate planDate,
                                          String orderNo,
                                          String materialCode);
}
