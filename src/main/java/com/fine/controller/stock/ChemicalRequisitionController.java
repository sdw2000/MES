package com.fine.controller.stock;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.fine.Utils.ResponseResult;
import com.fine.model.stock.ChemicalPurchaseRequest;
import com.fine.service.stock.ChemicalRequisitionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Map;

@RestController
@RequestMapping("/api/stock/chemical-requisition")
@PreAuthorize("hasAnyAuthority('admin','warehouse','production')")
public class ChemicalRequisitionController {

    @Autowired
    private ChemicalRequisitionService chemicalRequisitionService;

    @PostMapping("/generate")
    public ResponseResult<Map<String, Object>> generate(@RequestParam String planDate,
                                                        @RequestParam(required = false) String orderNo,
                                                        @RequestParam(required = false) String materialCode) {
        Map<String, Object> data = chemicalRequisitionService.generateFromCoatingPlan(LocalDate.parse(planDate), orderNo, materialCode);
        return ResponseResult.success("已按配方生成锁定/请购", data);
    }

    @PostMapping("/lock-query")
    public ResponseResult<Map<String, Object>> lockQuery(@RequestParam String planDate,
                                                         @RequestParam(required = false) String orderNo,
                                                         @RequestParam(required = false) String materialCode) {
        LocalDate targetDate = LocalDate.parse(planDate);
        Map<String, Object> summary = chemicalRequisitionService.generateFromCoatingPlan(targetDate, orderNo, materialCode);
        java.util.List<Map<String, Object>> locks = chemicalRequisitionService.queryLocksByPlan(targetDate, orderNo, materialCode);

        java.util.Map<String, Object> data = new java.util.LinkedHashMap<>();
        data.put("planDate", planDate);
        data.put("summary", summary);
        data.put("locks", locks);
        return ResponseResult.success("查询成功", data);
    }

    @PostMapping("/issue-confirm")
    public ResponseResult<Map<String, Object>> issueConfirm(@RequestBody Map<String, Object> payload) {
        java.util.List<Long> lockIds = new java.util.ArrayList<>();
        Object raw = payload == null ? null : payload.get("lockIds");
        if (raw instanceof java.util.List) {
            for (Object obj : (java.util.List<?>) raw) {
                try {
                    lockIds.add(Long.parseLong(String.valueOf(obj)));
                } catch (Exception ignore) {
                }
            }
        }
        String operator = payload == null ? null : String.valueOf(payload.getOrDefault("operator", "production"));
        Map<String, Object> data = chemicalRequisitionService.confirmIssueByLocks(lockIds, operator);
        return ResponseResult.success("领料确认成功，已同步仓库出库", data);
    }

    @GetMapping("/page")
    public ResponseResult<IPage<ChemicalPurchaseRequest>> page(@RequestParam(defaultValue = "1") int current,
                                                               @RequestParam(defaultValue = "20") int size,
                                                               @RequestParam(required = false) String status) {
        return ResponseResult.success(chemicalRequisitionService.getRequestPage(current, size, status));
    }

    @GetMapping("/{requestNo}")
    public ResponseResult<ChemicalPurchaseRequest> detail(@PathVariable String requestNo) {
        ChemicalPurchaseRequest req = chemicalRequisitionService.getRequestDetail(requestNo);
        if (req == null) {
            return ResponseResult.error("请购单不存在");
        }
        return ResponseResult.success(req);
    }

    @PutMapping("/item/{itemId}/requested-qty")
    public ResponseResult<Void> updateQty(@PathVariable Long itemId, @RequestParam Integer requestedQty) {
        chemicalRequisitionService.updateRequestedQty(itemId, requestedQty);
        return ResponseResult.success("更新成功", null);
    }

    @PostMapping("/{requestNo}/submit")
    public ResponseResult<Void> submit(@PathVariable String requestNo) {
        chemicalRequisitionService.submitRequest(requestNo);
        return ResponseResult.success("提交成功", null);
    }

    @PostMapping("/{requestNo}/approve")
    public ResponseResult<Void> approve(@PathVariable String requestNo) {
        chemicalRequisitionService.approveRequest(requestNo);
        return ResponseResult.success("审核通过", null);
    }

    @PostMapping("/{requestNo}/create-purchase-order")
    public ResponseResult<Map<String, Object>> createPurchaseOrder(@PathVariable String requestNo) {
        String poNo = chemicalRequisitionService.createPurchaseOrder(requestNo);
        java.util.Map<String, Object> data = new java.util.HashMap<>();
        data.put("purchaseOrderNo", poNo);
        return ResponseResult.success("已生成采购单", data);
    }

    @PostMapping("/{requestNo}/receive")
    public ResponseResult<Map<String, Object>> receive(@PathVariable String requestNo,
                                                       @RequestBody(required = false) Map<String, Object> payload) {
        Map<Long, Integer> receiveQtyMap = new java.util.HashMap<>();
        if (payload != null && payload.get("items") instanceof java.util.List) {
            java.util.List<?> items = (java.util.List<?>) payload.get("items");
            for (Object obj : items) {
                if (!(obj instanceof Map)) {
                    continue;
                }
                Map<?, ?> row = (Map<?, ?>) obj;
                Object itemIdObj = row.get("id");
                Object receiveQtyObj = row.get("receiveQty");
                if (itemIdObj == null || receiveQtyObj == null) {
                    continue;
                }
                try {
                    Long itemId = Long.parseLong(String.valueOf(itemIdObj));
                    Integer receiveQty = Integer.parseInt(String.valueOf(receiveQtyObj));
                    receiveQtyMap.put(itemId, receiveQty);
                } catch (Exception ignore) {
                }
            }
        }
        Map<String, Object> data = chemicalRequisitionService.receiveAndFulfill(requestNo, receiveQtyMap);
        return ResponseResult.success("到货入库完成", data);
    }
}
