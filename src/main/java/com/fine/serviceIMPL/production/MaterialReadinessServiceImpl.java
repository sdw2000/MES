package com.fine.serviceIMPL.production;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fine.Dao.SalesOrderItemMapper;
import com.fine.Dao.purchase.PurchaseOrderItemMapper;
import com.fine.Dao.purchase.PurchaseOrderMapper;
import com.fine.Dao.rd.TapeFormulaMapper;
import com.fine.Dao.stock.ChemicalPurchaseRequestItemMapper;
import com.fine.Dao.stock.ChemicalPurchaseRequestMapper;
import com.fine.Dao.stock.ChemicalStockMapper;
import com.fine.modle.PurchaseOrder;
import com.fine.modle.PurchaseOrderItem;
import com.fine.modle.SalesOrderItem;
import com.fine.modle.rd.TapeFormula;
import com.fine.modle.rd.TapeFormulaItem;
import com.fine.model.production.readiness.ReadinessStatus;
import com.fine.model.stock.ChemicalPurchaseRequest;
import com.fine.model.stock.ChemicalPurchaseRequestItem;
import com.fine.model.stock.ChemicalStock;
import com.fine.model.stock.enums.ChemicalRequisitionStatus;
import com.fine.service.production.MaterialReadinessService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

@Service
public class MaterialReadinessServiceImpl implements MaterialReadinessService {

    @Autowired
    private SalesOrderItemMapper salesOrderItemMapper;
    @Autowired
    private TapeFormulaMapper tapeFormulaMapper;
    @Autowired
    private ChemicalStockMapper chemicalStockMapper;
    @Autowired
    private ChemicalPurchaseRequestMapper chemicalPurchaseRequestMapper;
    @Autowired
    private ChemicalPurchaseRequestItemMapper chemicalPurchaseRequestItemMapper;
    @Autowired
    private PurchaseOrderItemMapper purchaseOrderItemMapper;
    @Autowired
    private PurchaseOrderMapper purchaseOrderMapper;

    @Override
    public Map<String, Object> getChemicalReadinessSummary(LocalDate requiredByDate, String orderNo, String materialCode) {
        LocalDate targetDate = requiredByDate == null ? LocalDate.now() : requiredByDate;
        Map<String, Aggregation> aggMap = new LinkedHashMap<>();

        // 1) 订单需求（基于未完成订单 + BOM）
        Page<SalesOrderItem> page = new Page<>(1, 20000);
        IPage<SalesOrderItem> pending = salesOrderItemMapper.selectPendingItems(page, trim(orderNo), trim(materialCode));
        List<SalesOrderItem> items = pending == null ? Collections.emptyList() : pending.getRecords();

        Map<String, TapeFormula> formulaCache = new HashMap<>();
        for (SalesOrderItem item : items) {
            if (item == null || item.getMaterialCode() == null) continue;
            if (item.getCoatingDate() != null) {
                LocalDate coatingDate = item.getCoatingDate().toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
                if (coatingDate.isAfter(targetDate)) {
                    continue;
                }
            }
            BigDecimal pendingArea = item.getPendingArea() == null ? BigDecimal.ZERO : item.getPendingArea();
            if (pendingArea.compareTo(BigDecimal.ZERO) <= 0) continue;

            TapeFormula formula = formulaCache.computeIfAbsent(item.getMaterialCode(), tapeFormulaMapper::selectByMaterialCode);
            if (formula == null || formula.getId() == null || formula.getCoatingArea() == null || formula.getCoatingArea().compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            List<TapeFormulaItem> formulaItems = tapeFormulaMapper.selectItemsByFormulaId(formula.getId());
            if (formulaItems == null || formulaItems.isEmpty()) continue;

            BigDecimal factor = pendingArea.divide(formula.getCoatingArea(), 8, RoundingMode.HALF_UP);
            BigDecimal totalWeight = formula.getTotalWeight() == null ? BigDecimal.ZERO : formula.getTotalWeight();

            for (TapeFormulaItem fi : formulaItems) {
                if (fi == null || fi.getMaterialCode() == null) continue;
                BigDecimal needKg = calcNeedKg(fi, factor, totalWeight);
                if (needKg.compareTo(BigDecimal.ZERO) <= 0) continue;

                Aggregation agg = aggMap.computeIfAbsent(fi.getMaterialCode(), k -> new Aggregation(fi.getMaterialCode(), fi.getMaterialName()));
                agg.demandKg = agg.demandKg.add(needKg);
                if (item.getOrderNo() != null) {
                    agg.impactedOrders.add(item.getOrderNo());
                }
            }
        }

        if (aggMap.isEmpty()) {
            return emptyResult(targetDate);
        }

        // 2) 在库覆盖
        List<ChemicalStock> stockList = chemicalStockMapper.selectList(new QueryWrapper<>());
        Map<String, ChemicalStock> stockByCode = new HashMap<>();
        for (ChemicalStock stock : stockList) {
            if (stock == null || stock.getMaterialCode() == null) continue;
            stockByCode.put(stock.getMaterialCode(), stock);
        }

        for (Aggregation agg : aggMap.values()) {
            ChemicalStock stock = stockByCode.get(agg.rawMaterialCode);
            if (stock == null) continue;
            int availableQty = stock.getAvailableQuantity() == null ? 0 : stock.getAvailableQuantity();
            BigDecimal unitWeight = stock.getUnitWeight() == null || stock.getUnitWeight().compareTo(BigDecimal.ZERO) <= 0
                    ? BigDecimal.ONE : stock.getUnitWeight();
            agg.unit = stock.getUnit() == null ? "桶" : stock.getUnit();
            agg.onHandQty = availableQty;
            agg.onHandKg = unitWeight.multiply(BigDecimal.valueOf(availableQty)).setScale(3, RoundingMode.HALF_UP);
            agg.unitWeight = unitWeight;
        }

        // 3) 在途覆盖（已创建采购且未收完）
        QueryWrapper<ChemicalPurchaseRequest> reqQw = new QueryWrapper<>();
        reqQw.in("status", Arrays.asList(ChemicalRequisitionStatus.PO_CREATED, ChemicalRequisitionStatus.PARTIAL_RECEIVED));
        List<ChemicalPurchaseRequest> reqList = chemicalPurchaseRequestMapper.selectList(reqQw);

        for (ChemicalPurchaseRequest req : reqList) {
            if (req == null || req.getId() == null) continue;
            List<ChemicalPurchaseRequestItem> reqItems = chemicalPurchaseRequestItemMapper.selectByRequestId(req.getId());
            if (reqItems == null) continue;
            for (ChemicalPurchaseRequestItem ri : reqItems) {
                if (ri == null || ri.getRawMaterialCode() == null) continue;
                Aggregation agg = aggMap.get(ri.getRawMaterialCode());
                if (agg == null) continue;
                int requested = ri.getRequestedQty() == null ? 0 : ri.getRequestedQty();
                int received = ri.getReceivedQty() == null ? 0 : ri.getReceivedQty();
                int remainingInTransitQty = Math.max(requested - received, 0);
                if (remainingInTransitQty <= 0) continue;

                agg.inTransitQty += remainingInTransitQty;
                agg.inTransitKg = agg.inTransitKg.add(agg.unitWeight.multiply(BigDecimal.valueOf(remainingInTransitQty))).setScale(3, RoundingMode.HALF_UP);

                if (ri.getPurchaseOrderItemId() != null) {
                    PurchaseOrderItem poi = purchaseOrderItemMapper.selectById(ri.getPurchaseOrderItemId());
                    if (poi != null && poi.getOrderId() != null) {
                        PurchaseOrder po = purchaseOrderMapper.selectById(poi.getOrderId());
                        if (po != null && po.getDeliveryDate() != null) {
                            LocalDate eta = po.getDeliveryDate().toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
                            if (agg.earliestEta == null || eta.isBefore(agg.earliestEta)) {
                                agg.earliestEta = eta;
                            }
                        }
                    }
                }
            }
        }

        // 4) 计算净额与状态
        BigDecimal totalDemandKg = BigDecimal.ZERO;
        BigDecimal totalShortageKg = BigDecimal.ZERO;
        int readyCount = 0;
        int shortageCount = 0;

        List<Map<String, Object>> rows = new ArrayList<>();
        for (Aggregation agg : aggMap.values()) {
            BigDecimal netAvailableKg = agg.onHandKg.add(agg.inTransitKg).setScale(3, RoundingMode.HALF_UP);
            BigDecimal shortageKg = agg.demandKg.subtract(netAvailableKg);
            if (shortageKg.compareTo(BigDecimal.ZERO) < 0) {
                shortageKg = BigDecimal.ZERO;
            }
            int suggestPurchaseQty = agg.unitWeight.compareTo(BigDecimal.ZERO) > 0
                    ? shortageKg.divide(agg.unitWeight, 0, RoundingMode.CEILING).intValue()
                    : 0;

            String statusCode;
            if (shortageKg.compareTo(BigDecimal.ZERO) <= 0) {
                statusCode = ReadinessStatus.READY;
                readyCount++;
            } else if (agg.earliestEta != null && !agg.earliestEta.isAfter(targetDate)) {
                statusCode = ReadinessStatus.READY_BY_ETA;
                readyCount++;
            } else {
                statusCode = ReadinessStatus.SHORTAGE;
                shortageCount++;
            }

            totalDemandKg = totalDemandKg.add(agg.demandKg);
            totalShortageKg = totalShortageKg.add(shortageKg);

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("rawMaterialCode", agg.rawMaterialCode);
            row.put("rawMaterialName", agg.rawMaterialName == null ? agg.rawMaterialCode : agg.rawMaterialName);
            row.put("unit", agg.unit);
            row.put("demandKg", agg.demandKg.setScale(3, RoundingMode.HALF_UP));
            row.put("onHandQty", agg.onHandQty);
            row.put("onHandKg", agg.onHandKg.setScale(3, RoundingMode.HALF_UP));
            row.put("inTransitQty", agg.inTransitQty);
            row.put("inTransitKg", agg.inTransitKg.setScale(3, RoundingMode.HALF_UP));
            row.put("netAvailableKg", netAvailableKg);
            row.put("shortageKg", shortageKg.setScale(3, RoundingMode.HALF_UP));
            row.put("suggestPurchaseQty", suggestPurchaseQty);
            row.put("earliestEta", agg.earliestEta == null ? null : agg.earliestEta.toString());
            row.put("statusCode", statusCode);
            row.put("statusText", ReadinessStatus.labelOf(statusCode));
            row.put("impactedOrderCount", agg.impactedOrders.size());
            row.put("impactedOrders", new ArrayList<>(agg.impactedOrders));
            rows.add(row);
        }

        rows.sort(Comparator
            .comparing((Map<String, Object> x) -> String.valueOf(x.get("statusCode")))
            .thenComparing(x -> String.valueOf(x.get("rawMaterialCode"))));

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("materialCount", rows.size());
        summary.put("readyCount", readyCount);
        summary.put("shortageCount", shortageCount);
        summary.put("totalDemandKg", totalDemandKg.setScale(3, RoundingMode.HALF_UP));
        summary.put("totalShortageKg", totalShortageKg.setScale(3, RoundingMode.HALF_UP));

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("requiredByDate", targetDate.toString());
        res.put("generatedAt", new Date());
        res.put("summary", summary);
        res.put("items", rows);
        return res;
    }

    @Override
    public Map<String, Object> getOrderItemReadiness(Long orderItemId) {
        SalesOrderItem item = orderItemId == null ? null : salesOrderItemMapper.selectById(orderItemId);
        if (item == null) {
            throw new RuntimeException("订单明细不存在");
        }

        LocalDate targetDate = LocalDate.now();
        if (item.getCoatingDate() != null) {
            targetDate = item.getCoatingDate().toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        }

        Map<String, Object> all = getChemicalReadinessSummary(targetDate, item.getOrderNo(), item.getMaterialCode());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> lines = (List<Map<String, Object>>) all.getOrDefault("items", Collections.emptyList());

        String statusCode = ReadinessStatus.READY;
        for (Map<String, Object> line : lines) {
            String lineStatus = String.valueOf(line.get("statusCode"));
            if (ReadinessStatus.SHORTAGE.equals(lineStatus)) {
                statusCode = ReadinessStatus.SHORTAGE;
                break;
            }
            if (ReadinessStatus.READY_BY_ETA.equals(lineStatus)
                    && !ReadinessStatus.SHORTAGE.equals(statusCode)) {
                statusCode = ReadinessStatus.READY_BY_ETA;
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("orderItemId", item.getId());
        result.put("orderNo", item.getOrderNo());
        result.put("materialCode", item.getMaterialCode());
        result.put("materialName", item.getMaterialName());
        result.put("pendingArea", item.getPendingArea());
        result.put("requiredByDate", targetDate.toString());
        result.put("statusCode", statusCode);
        result.put("statusText", ReadinessStatus.labelOf(statusCode));
        result.put("components", lines);
        return result;
    }

    private BigDecimal calcNeedKg(TapeFormulaItem item, BigDecimal factor, BigDecimal totalWeight) {
        BigDecimal need = BigDecimal.ZERO;
        if (item.getWeight() != null && item.getWeight().compareTo(BigDecimal.ZERO) > 0) {
            need = item.getWeight().multiply(factor);
        } else if (item.getRatio() != null && item.getRatio().compareTo(BigDecimal.ZERO) > 0 && totalWeight.compareTo(BigDecimal.ZERO) > 0) {
            need = totalWeight.multiply(item.getRatio())
                    .divide(new BigDecimal("100"), 8, RoundingMode.HALF_UP)
                    .multiply(factor);
        }
        return need.setScale(3, RoundingMode.HALF_UP);
    }

    private String trim(String text) {
        if (text == null) return null;
        String t = text.trim();
        return t.isEmpty() ? null : t;
    }

    private BigDecimal toDecimal(Object value) {
        if (value == null) return BigDecimal.ZERO;
        try {
            return new BigDecimal(String.valueOf(value));
        } catch (Exception ex) {
            return BigDecimal.ZERO;
        }
    }

    private Map<String, Object> emptyResult(LocalDate targetDate) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("materialCount", 0);
        summary.put("readyCount", 0);
        summary.put("shortageCount", 0);
        summary.put("totalDemandKg", BigDecimal.ZERO.setScale(3, RoundingMode.HALF_UP));
        summary.put("totalShortageKg", BigDecimal.ZERO.setScale(3, RoundingMode.HALF_UP));

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("requiredByDate", targetDate.toString());
        res.put("generatedAt", new Date());
        res.put("summary", summary);
        res.put("items", Collections.emptyList());
        return res;
    }

    private static class Aggregation {
        private final String rawMaterialCode;
        private final String rawMaterialName;
        private String unit = "桶";
        private BigDecimal unitWeight = BigDecimal.ONE;
        private BigDecimal demandKg = BigDecimal.ZERO;
        private int onHandQty = 0;
        private BigDecimal onHandKg = BigDecimal.ZERO;
        private int inTransitQty = 0;
        private BigDecimal inTransitKg = BigDecimal.ZERO;
        private LocalDate earliestEta;
        private final Set<String> impactedOrders = new LinkedHashSet<>();

        private Aggregation(String rawMaterialCode, String rawMaterialName) {
            this.rawMaterialCode = rawMaterialCode;
            this.rawMaterialName = rawMaterialName;
        }
    }
}
