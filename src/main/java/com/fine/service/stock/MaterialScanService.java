package com.fine.service.stock;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.fine.model.stock.MaterialScanTxn;

import java.util.Map;

public interface MaterialScanService {

    Map<String, Object> resolveByCode(String code, String stockTypeHint);

    Map<String, Object> issueByScan(Map<String, Object> payload);

    Map<String, Object> returnByScan(Map<String, Object> payload);

    IPage<MaterialScanTxn> getTxnPage(int page,
                                      int size,
                                      String txnType,
                                      String stockType,
                                      String orderNo,
                                      Long scheduleId,
                                      String qrCode);
}
