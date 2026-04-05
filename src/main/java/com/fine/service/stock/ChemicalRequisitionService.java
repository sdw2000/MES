package com.fine.service.stock;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.fine.model.stock.ChemicalPurchaseRequest;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public interface ChemicalRequisitionService {

    Map<String, Object> generateFromCoatingPlan(LocalDate planDate, String orderNo, String materialCode);

    List<Map<String, Object>> queryLocksByPlan(LocalDate planDate, String orderNo, String materialCode);

    IPage<ChemicalPurchaseRequest> getRequestPage(int current, int size, String status);

    ChemicalPurchaseRequest getRequestDetail(String requestNo);

    void updateRequestedQty(Long itemId, Integer requestedQty);

    void submitRequest(String requestNo);

    void approveRequest(String requestNo);

    String createPurchaseOrder(String requestNo);

    Map<String, Object> receiveAndFulfill(String requestNo, Map<Long, Integer> receiveQtyMap);

    Map<String, Object> confirmIssueByLocks(java.util.List<Long> lockIds, String operator);
}
