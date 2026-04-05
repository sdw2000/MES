package com.fine.controller;

import com.fine.Utils.ResponseResult;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/config")
public class CompanyInfoController {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private volatile boolean companyConfigChecked = false;
    private final DataFormatter dataFormatter = new DataFormatter();

    private static class StatusDetailRow {
        private String orderNo;
        private String materialCode;
        private String thickness;
        private String width;
        private String length;
        private String rolls;
        private Integer completedRolls;
        private Integer remainingRolls;
        private String status;
    }

    private static class OrderItemLite {
        private Long id;
        private Long orderId;
        private String orderNo;
        private String materialCode;
        private String thickness;
        private String width;
        private String length;
        private int rolls;
        private int deliveredQty;
        private int remainingQty;
        private String productionStatus;
    }

    private static class CompletedOrderRow {
        private String orderNo;
        private String materialCode;
        private String thickness;
        private String width;
        private String length;
    }

    private static class UncompletedOrderRow {
        private String orderNo;
        private String materialCode;
        private String thickness;
        private String width;
        private String length;
        private Integer completedQty;
    }

    @GetMapping("/company")
    public ResponseResult<Map<String, String>> getCompanyInfo() {
        ensureCompanyConfigTable();
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("SELECT * FROM system_company_config WHERE id = 1 LIMIT 1");
        if (rows == null || rows.isEmpty()) {
            insertDefaultIfAbsent();
            rows = jdbcTemplate.queryForList("SELECT * FROM system_company_config WHERE id = 1 LIMIT 1");
        }

        Map<String, String> m = new HashMap<>();
        if (rows != null && !rows.isEmpty()) {
            Map<String, Object> row = rows.get(0);
            m.put("companyName", toText(row.get("company_name")));
            m.put("address", toText(row.get("address")));
            m.put("phone", toText(row.get("phone")));
            m.put("fax", toText(row.get("fax")));
            m.put("website", toText(row.get("website")));
            m.put("logoUrl", toText(row.get("logo_url")));
        }
        return ResponseResult.success(m);
    }

    @PostMapping("/company")
    @PreAuthorize("hasAnyAuthority('admin', 'production')")
    public ResponseResult<?> saveCompanyInfo(@RequestBody Map<String, String> body) {
        ensureCompanyConfigTable();
        insertDefaultIfAbsent();

        String companyName = trimOrDefault(body.get("companyName"), "东莞市方恩电子材料科技有限公司");
        String address = trimOrDefault(body.get("address"), "广东省东莞市桥头镇东新路13号2号楼102室");
        String phone = trimOrDefault(body.get("phone"), "0769-82551118");
        String fax = trimOrDefault(body.get("fax"), "0769-82551160");
        String website = trimOrDefault(body.get("website"), "www.finechemfr.com");
        String logoUrl = trimOrDefault(body.get("logoUrl"), "/logo/finechem-logo.png");
        String operator = trimOrDefault(body.get("operator"), "system");

        jdbcTemplate.update(
                "UPDATE system_company_config SET company_name = ?, address = ?, phone = ?, fax = ?, website = ?, logo_url = ?, updated_by = ?, updated_at = NOW() WHERE id = 1",
                companyName, address, phone, fax, website, logoUrl, operator
        );
        return ResponseResult.success("保存成功", null);
    }

    /**
     * 上传“订单状态维护表”，按订单更新完成卷数/未完成卷数与订单状态。
     * 规则：
     * 1) 表中出现的订单按上传值更新；
     * 2) 表中未出现的订单，全部设置为已完成；
     * 3) 完成卷数按订单明细卷数顺序分摊到各明细（delivered_qty / remaining_qty）。
     */
    @PostMapping("/order-status/upload")
    @PreAuthorize("hasAnyAuthority('admin', 'production')")
    @Transactional(rollbackFor = Exception.class)
    public ResponseResult<?> uploadOrderStatusSheet(@RequestParam("file") MultipartFile file,
                                                    @RequestParam(value = "operator", required = false) String operator) {
        if (file == null || file.isEmpty()) {
            return new ResponseResult<>(400, "请选择上传文件");
        }
        String op = trimOrDefault(operator, "system");
        ensureCompanyConfigTable();
        insertDefaultIfAbsent();

        if (isStatusSheetInitialized()) {
            return new ResponseResult<>(400, "订单状态表已完成一次性初始化，后续新订单不再与状态表比对");
        }

        try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
            List<StatusDetailRow> statusRows = parseOrderStatusWorkbook(workbook);

            if (statusRows.isEmpty()) {
                return new ResponseResult<>(400,
                        "上传失败：未识别到有效明细。请检查是否包含：订单号、料号、厚度、宽度、长度及已完成数量/未完成数量。");
            }

            List<Map<String, Object>> orders = jdbcTemplate.queryForList(
                    "SELECT id, order_no FROM sales_orders WHERE is_deleted = 0"
            );
            Map<String, Long> orderIdByNo = new HashMap<>();
            Set<String> existingOrderNos = new LinkedHashSet<>();
            for (Map<String, Object> order : orders) {
                Long orderId = toLong(order.get("id"));
                String orderNo = toText(order.get("order_no")).trim();
                if (orderId == null || orderNo.isEmpty()) {
                    continue;
                }
                orderIdByNo.put(orderNo, orderId);
                existingOrderNos.add(orderNo);
            }

            List<Map<String, Object>> allItems = jdbcTemplate.queryForList(
                    "SELECT soi.id, soi.order_id, soi.rolls, soi.delivered_qty, soi.remaining_qty, soi.production_status, " +
                            "so.order_no, soi.material_code, soi.thickness, soi.width, soi.length " +
                            "FROM sales_order_items soi " +
                            "INNER JOIN sales_orders so ON so.id = soi.order_id " +
                            "WHERE soi.is_deleted = 0 AND so.is_deleted = 0 " +
                            "ORDER BY soi.order_id ASC, soi.id ASC"
            );

            List<OrderItemLite> items = new ArrayList<>();
            Map<String, List<OrderItemLite>> itemMapByCompositeKey = new HashMap<>();
            Map<Long, List<OrderItemLite>> itemMapByOrderId = new HashMap<>();
            Map<Long, Long> orderIdByItemId = new HashMap<>();
            for (Map<String, Object> itemRow : allItems) {
                OrderItemLite item = new OrderItemLite();
                item.id = toLong(itemRow.get("id"));
                item.orderId = toLong(itemRow.get("order_id"));
                item.orderNo = toText(itemRow.get("order_no")).trim();
                item.materialCode = normalizeKeyPart(toText(itemRow.get("material_code")));
                item.thickness = normalizeKeyPart(toText(itemRow.get("thickness")));
                item.width = normalizeKeyPart(toText(itemRow.get("width")));
                item.length = normalizeKeyPart(toText(itemRow.get("length")));
                item.rolls = Math.max(0, toInt(itemRow.get("rolls"), 0));
                item.deliveredQty = Math.max(0, toInt(itemRow.get("delivered_qty"), 0));
                item.remainingQty = Math.max(0, toInt(itemRow.get("remaining_qty"), Math.max(0, item.rolls - item.deliveredQty)));
                item.productionStatus = toText(itemRow.get("production_status"));

                if (item.id == null || item.orderId == null || item.orderNo.isEmpty()) {
                    continue;
                }

                items.add(item);
                orderIdByItemId.put(item.id, item.orderId);
                String key = buildCompositeKey(item.orderNo, item.materialCode, item.thickness, item.width, item.length, String.valueOf(item.rolls));
                itemMapByCompositeKey.computeIfAbsent(key, k -> new ArrayList<>()).add(item);
                itemMapByOrderId.computeIfAbsent(item.orderId, k -> new ArrayList<>()).add(item);
            }

            Map<Long, Object[]> itemUpdateById = new LinkedHashMap<>();
            Set<Long> orderIdsDefaultCompleted = new LinkedHashSet<>();

            // 默认策略：未在状态表比对到的明细全部视为已完成
            for (OrderItemLite item : items) {
                int targetCompleted = item.rolls;
                int targetRemaining = 0;
                String targetStatus = "completed";
                if (item.deliveredQty != targetCompleted || item.remainingQty != targetRemaining || !"completed".equalsIgnoreCase(trimOrDefault(item.productionStatus, ""))) {
                    itemUpdateById.put(item.id, new Object[]{targetCompleted, targetRemaining, targetStatus, op, item.id});
                }
                orderIdsDefaultCompleted.add(item.orderId);
            }

            Set<String> missingOrderNos = new LinkedHashSet<>();
            Set<String> missingDetailKeys = new LinkedHashSet<>();
            Set<Long> matchedOrderIds = new LinkedHashSet<>();

            // 明细比对：订单号+料号+厚宽长完全一致，按上传已完成数量覆盖
            for (StatusDetailRow row : statusRows) {
                if (row == null || row.orderNo == null || row.orderNo.trim().isEmpty()) {
                    continue;
                }
                if (!existingOrderNos.contains(row.orderNo)) {
                    missingOrderNos.add(row.orderNo);
                    continue;
                }

                String rowKey = buildCompositeKey(
                        row.orderNo,
                        normalizeKeyPart(row.materialCode),
                        normalizeKeyPart(row.thickness),
                        normalizeKeyPart(row.width),
                    normalizeKeyPart(row.length),
                    normalizeKeyPart(row.rolls)
                );

                List<OrderItemLite> matchedItems = itemMapByCompositeKey.get(rowKey);
                if (matchedItems == null || matchedItems.isEmpty()) {
                    missingDetailKeys.add(rowKey);
                    continue;
                }

                int totalRolls = 0;
                for (OrderItemLite matchedItem : matchedItems) {
                    totalRolls += matchedItem.rolls;
                    matchedOrderIds.add(matchedItem.orderId);
                }
                int targetCompleted = resolveTargetCompleted(totalRolls, row.completedRolls, row.remainingRolls, row.status);
                int remainingToComplete = Math.max(0, Math.min(targetCompleted, totalRolls));

                for (OrderItemLite matchedItem : matchedItems) {
                    int itemCompleted = Math.min(matchedItem.rolls, remainingToComplete);
                    remainingToComplete -= itemCompleted;
                    int itemRemaining = Math.max(0, matchedItem.rolls - itemCompleted);
                    String itemStatus = itemRemaining == 0 ? "completed" : (itemCompleted > 0 ? "processing" : "pending");
                    itemUpdateById.put(matchedItem.id, new Object[]{itemCompleted, itemRemaining, itemStatus, op, matchedItem.id});
                }
            }

            List<Object[]> itemBatchArgs = new ArrayList<>(itemUpdateById.values());
            if (!itemBatchArgs.isEmpty()) {
                jdbcTemplate.batchUpdate(
                        "UPDATE sales_order_items SET delivered_qty = ?, remaining_qty = ?, production_status = ?, updated_by = ?, updated_at = NOW() WHERE id = ?",
                        itemBatchArgs
                );
            }

            Map<Long, int[]> orderAggMap = new HashMap<>(); // [0]=completed, [1]=remaining
            for (Object[] args : itemBatchArgs) {
                Long itemId = toLong(args[4]);
                if (itemId == null) {
                    continue;
                }
                Long orderId = orderIdByItemId.get(itemId);
                if (orderId == null) {
                    continue;
                }
                int completed = toInt(args[0], 0);
                int remaining = toInt(args[1], 0);
                int[] agg = orderAggMap.computeIfAbsent(orderId, k -> new int[]{0, 0});
                agg[0] += Math.max(0, completed);
                agg[1] += Math.max(0, remaining);
            }

            List<Object[]> orderBatchArgs = new ArrayList<>();
            for (Map.Entry<Long, List<OrderItemLite>> entry : itemMapByOrderId.entrySet()) {
                Long orderId = entry.getKey();
                int[] agg = orderAggMap.get(orderId);
                int completed = agg == null ? 0 : agg[0];
                int remaining = agg == null ? 0 : agg[1];
                String orderStatus = remaining == 0 ? "completed" : (completed > 0 ? "processing" : "pending");
                orderBatchArgs.add(new Object[]{orderStatus, op, orderId});
            }
            if (!orderBatchArgs.isEmpty()) {
                jdbcTemplate.batchUpdate(
                        "UPDATE sales_orders SET status = ?, updated_by = ?, updated_at = NOW() WHERE id = ?",
                        orderBatchArgs
                );
            }

            List<String> missingOrderNoList = new ArrayList<>(missingOrderNos);
            List<String> missingDetailKeyList = new ArrayList<>(missingDetailKeys);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("uploadedOrderCount", statusRows.size());
            result.put("updatedOrders", orderBatchArgs.size());
            result.put("updatedItems", itemBatchArgs.size());
            result.put("completedByDefaultOrders", orderIdsDefaultCompleted.size() - matchedOrderIds.size());
            result.put("missingOrderNos", missingOrderNoList.size() > 200 ? missingOrderNoList.subList(0, 200) : missingOrderNoList);
            result.put("missingDetailKeys", missingDetailKeyList.size() > 200 ? missingDetailKeyList.subList(0, 200) : missingDetailKeyList);
            result.put("missingOrderNosTotal", missingOrderNoList.size());
            result.put("missingDetailKeysTotal", missingDetailKeyList.size());
            result.put("message", "状态维护更新完成");

            markStatusSheetInitialized(op);
            return new ResponseResult<>(200, "状态维护更新完成", result);
        } catch (Exception e) {
            Throwable root = e;
            while (root.getCause() != null) {
                root = root.getCause();
            }
            String detail = root.getMessage() == null ? root.getClass().getSimpleName()
                    : (root.getClass().getSimpleName() + " - " + root.getMessage());
            return new ResponseResult<>(500, "状态维护上传失败: " + detail);
        }
    }

    /**
     * 导入“历史已完成订单明细”表。
     * 规则：
     * 1) 仅按 订单号+料号+厚度+宽度+长度 完全匹配；
     * 2) 已有报工数据（produced_qty>0）的明细不修改；
     * 3) 命中且可更新的明细置为 completed（delivered_qty=rolls, remaining_qty=0）；
     * 4) 订单状态按明细重算：全完成=completed，否则=processing。
     */
    @PostMapping("/order-completed/upload")
    @PreAuthorize("hasAnyAuthority('admin', 'production')")
    @Transactional(rollbackFor = Exception.class)
    public ResponseResult<?> uploadCompletedOrdersSheet(@RequestParam("file") MultipartFile file,
                                                        @RequestParam(value = "operator", required = false) String operator) {
        if (file == null || file.isEmpty()) {
            return new ResponseResult<>(400, "请选择上传文件");
        }
        String op = trimOrDefault(operator, "system");
        ensureCompanyConfigTable();
        insertDefaultIfAbsent();
        if (isStatusSheetInitialized()) {
            return new ResponseResult<>(400, "订单状态初始化已锁定，禁止批量导入更新订单状态；请在销售订单中逐条编辑");
        }

        try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
            List<CompletedOrderRow> rows = parseCompletedOrdersWorkbook(workbook);
            if (rows.isEmpty()) {
                return new ResponseResult<>(400, "上传失败：未识别到有效明细，请检查列头（订单号、料号/物料代码、厚度、宽度、长度）");
            }

            Set<String> uploadKeys = new LinkedHashSet<>();
            for (CompletedOrderRow row : rows) {
                String key = buildCompletedCompositeKey(row.orderNo, row.materialCode, row.thickness, row.width, row.length);
                if (!key.isEmpty()) {
                    uploadKeys.add(key);
                }
            }
            if (uploadKeys.isEmpty()) {
                return new ResponseResult<>(400, "上传失败：无可用匹配键");
            }

            Set<Long> reportedOrderDetailIds = new LinkedHashSet<>();
            List<Map<String, Object>> reportedRows = jdbcTemplate.queryForList(
                    "SELECT DISTINCT ms.order_detail_id " +
                            "FROM manual_schedule_process_report r " +
                            "JOIN manual_schedule ms ON ms.id = r.schedule_id " +
                            "WHERE r.is_deleted = 0 AND IFNULL(r.produced_qty, 0) > 0 AND ms.order_detail_id IS NOT NULL"
            );
            for (Map<String, Object> row : reportedRows) {
                Long id = toLong(row.get("order_detail_id"));
                if (id != null) {
                    reportedOrderDetailIds.add(id);
                }
            }

            List<Map<String, Object>> allItems = jdbcTemplate.queryForList(
                    "SELECT soi.id, soi.order_id, soi.rolls, soi.delivered_qty, soi.remaining_qty, soi.production_status, " +
                            "so.order_no, soi.material_code, soi.thickness, soi.width, soi.length " +
                            "FROM sales_order_items soi " +
                            "INNER JOIN sales_orders so ON so.id = soi.order_id " +
                            "WHERE soi.is_deleted = 0 AND so.is_deleted = 0"
            );

            Map<String, List<OrderItemLite>> itemMapByKey = new HashMap<>();
            for (Map<String, Object> row : allItems) {
                OrderItemLite item = new OrderItemLite();
                item.id = toLong(row.get("id"));
                item.orderId = toLong(row.get("order_id"));
                item.orderNo = toText(row.get("order_no")).trim();
                item.materialCode = normalizeKeyPart(toText(row.get("material_code")));
                item.thickness = normalizeKeyPart(toText(row.get("thickness")));
                item.width = normalizeKeyPart(toText(row.get("width")));
                item.length = normalizeKeyPart(toText(row.get("length")));
                item.rolls = Math.max(0, toInt(row.get("rolls"), 0));
                item.deliveredQty = Math.max(0, toInt(row.get("delivered_qty"), 0));
                item.remainingQty = Math.max(0, toInt(row.get("remaining_qty"), Math.max(0, item.rolls - item.deliveredQty)));
                item.productionStatus = toText(row.get("production_status"));

                if (item.id == null || item.orderId == null || item.orderNo.isEmpty()) {
                    continue;
                }
                String key = buildCompletedCompositeKey(item.orderNo, item.materialCode, item.thickness, item.width, item.length);
                itemMapByKey.computeIfAbsent(key, k -> new ArrayList<>()).add(item);
            }

            int matchedRows = 0;
            int skippedReportedItems = 0;
            int updatedItems = 0;
            Set<Long> touchedOrderIds = new LinkedHashSet<>();
            Set<String> unmatchedKeys = new LinkedHashSet<>();

            List<Object[]> itemBatchArgs = new ArrayList<>();
            for (String key : uploadKeys) {
                List<OrderItemLite> matchedItems = itemMapByKey.get(key);
                if (matchedItems == null || matchedItems.isEmpty()) {
                    unmatchedKeys.add(key);
                    continue;
                }
                matchedRows++;

                for (OrderItemLite item : matchedItems) {
                    if (item == null || item.id == null) {
                        continue;
                    }
                    if (item.orderId != null) {
                        touchedOrderIds.add(item.orderId);
                    }
                    if (reportedOrderDetailIds.contains(item.id)) {
                        skippedReportedItems++;
                        continue;
                    }

                    int targetCompleted = item.rolls;
                    int targetRemaining = 0;
                    String targetStatus = "completed";

                    boolean changed = item.deliveredQty != targetCompleted
                            || item.remainingQty != targetRemaining
                            || !"completed".equalsIgnoreCase(trimOrDefault(item.productionStatus, ""));
                    if (!changed) {
                        continue;
                    }

                    itemBatchArgs.add(new Object[]{targetCompleted, targetRemaining, targetStatus, op, item.id});
                    updatedItems++;
                }
            }

            if (!itemBatchArgs.isEmpty()) {
                jdbcTemplate.batchUpdate(
                        "UPDATE sales_order_items SET delivered_qty = ?, remaining_qty = ?, production_status = ?, updated_by = ?, updated_at = NOW() WHERE id = ?",
                        itemBatchArgs
                );
            }

            int updatedOrders = 0;
            if (!touchedOrderIds.isEmpty()) {
                StringBuilder inClause = new StringBuilder();
                List<Object> inArgs = new ArrayList<>();
                for (Long orderId : touchedOrderIds) {
                    if (orderId == null) {
                        continue;
                    }
                    if (inClause.length() > 0) {
                        inClause.append(",");
                    }
                    inClause.append("?");
                    inArgs.add(orderId);
                }

                if (inClause.length() > 0) {
                    List<Map<String, Object>> aggRows = jdbcTemplate.queryForList(
                            "SELECT order_id, " +
                                    "IFNULL(SUM(IFNULL(delivered_qty, 0)), 0) AS completed_rolls, " +
                                    "IFNULL(SUM(IFNULL(remaining_qty, GREATEST(rolls - IFNULL(delivered_qty, 0), 0))), 0) AS remaining_rolls " +
                                    "FROM sales_order_items " +
                                    "WHERE is_deleted = 0 AND order_id IN (" + inClause + ") " +
                                    "GROUP BY order_id",
                            inArgs.toArray()
                    );

                    List<Object[]> orderBatchArgs = new ArrayList<>();
                    for (Map<String, Object> agg : aggRows) {
                        Long orderId = toLong(agg.get("order_id"));
                        int completed = toInt(agg.get("completed_rolls"), 0);
                        int remaining = toInt(agg.get("remaining_rolls"), 0);
                        String status = remaining <= 0 ? "completed" : (completed > 0 ? "processing" : "pending");
                        if (orderId != null) {
                            orderBatchArgs.add(new Object[]{status, op, orderId});
                        }
                    }

                    if (!orderBatchArgs.isEmpty()) {
                        jdbcTemplate.batchUpdate(
                                "UPDATE sales_orders SET status = ?, updated_by = ?, updated_at = NOW() WHERE id = ?",
                                orderBatchArgs
                        );
                        updatedOrders = orderBatchArgs.size();
                    }
                }
            }

            List<String> unmatchedKeyList = new ArrayList<>(unmatchedKeys);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("uploadedRows", rows.size());
            result.put("matchedRows", matchedRows);
            result.put("updatedItems", updatedItems);
            result.put("skippedReportedItems", skippedReportedItems);
            result.put("updatedOrders", updatedOrders);
            result.put("unmatchedKeys", unmatchedKeyList.size() > 200 ? unmatchedKeyList.subList(0, 200) : unmatchedKeyList);
            result.put("unmatchedKeysTotal", unmatchedKeyList.size());
            result.put("message", "历史完成订单导入完成");

            return new ResponseResult<>(200, "历史完成订单导入完成", result);
        } catch (Exception e) {
            Throwable root = e;
            while (root.getCause() != null) {
                root = root.getCause();
            }
            String detail = root.getMessage() == null ? root.getClass().getSimpleName()
                    : (root.getClass().getSimpleName() + " - " + root.getMessage());
            return new ResponseResult<>(500, "导入失败: " + detail);
        }
    }

    /**
     * 导入“历史未完成订单明细”表。
     * 规则：
     * 1) 仅按 订单号+料号+厚度+宽度+长度 完全匹配；
     * 2) 已有报工数据（produced_qty>0）的明细不修改；
     * 3) 命中后按上传“已完成数量”覆盖 delivered_qty/remaining_qty；
     * 4) 已完成数量>0 的明细状态置为 partial，否则置为 not_started；
     * 5) 订单状态按明细重算：有已完成数量则 processing，否则 pending。
     */
    @PostMapping("/order-uncompleted/upload")
    @PreAuthorize("hasAnyAuthority('admin', 'production')")
    @Transactional(rollbackFor = Exception.class)
    public ResponseResult<?> uploadUncompletedOrdersSheet(@RequestParam("file") MultipartFile file,
                                                          @RequestParam(value = "operator", required = false) String operator) {
        if (file == null || file.isEmpty()) {
            return new ResponseResult<>(400, "请选择上传文件");
        }
        String op = trimOrDefault(operator, "system");
        ensureCompanyConfigTable();
        insertDefaultIfAbsent();
        if (isStatusSheetInitialized()) {
            return new ResponseResult<>(400, "订单状态初始化已锁定，禁止批量导入更新订单状态；请在销售订单中逐条编辑");
        }

        try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
            List<UncompletedOrderRow> rows = parseUncompletedOrdersWorkbook(workbook);
            if (rows.isEmpty()) {
                return new ResponseResult<>(400, "上传失败：未识别到有效明细，请检查列头（订单号、料号/物料代码、厚度、宽度、长度、已完成数量）");
            }

            Set<String> uploadKeys = new LinkedHashSet<>();
            for (UncompletedOrderRow row : rows) {
                if (row == null) {
                    continue;
                }
                String key = buildCompletedCompositeKey(row.orderNo, row.materialCode, row.thickness, row.width, row.length);
                if (!key.isEmpty()) {
                    uploadKeys.add(key);
                }
            }
            if (uploadKeys.isEmpty()) {
                return new ResponseResult<>(400, "上传失败：无可用匹配键");
            }

            Set<Long> reportedOrderDetailIds = new LinkedHashSet<>();
            List<Map<String, Object>> reportedRows = jdbcTemplate.queryForList(
                    "SELECT DISTINCT ms.order_detail_id " +
                            "FROM manual_schedule_process_report r " +
                            "JOIN manual_schedule ms ON ms.id = r.schedule_id " +
                            "WHERE r.is_deleted = 0 AND IFNULL(r.produced_qty, 0) > 0 AND ms.order_detail_id IS NOT NULL"
            );
            for (Map<String, Object> row : reportedRows) {
                Long id = toLong(row.get("order_detail_id"));
                if (id != null) {
                    reportedOrderDetailIds.add(id);
                }
            }

            List<Map<String, Object>> allItems = jdbcTemplate.queryForList(
                    "SELECT soi.id, soi.order_id, soi.rolls, soi.delivered_qty, soi.remaining_qty, soi.production_status, " +
                            "so.order_no, soi.material_code, soi.thickness, soi.width, soi.length " +
                            "FROM sales_order_items soi " +
                            "INNER JOIN sales_orders so ON so.id = soi.order_id " +
                            "WHERE soi.is_deleted = 0 AND so.is_deleted = 0"
            );

            Map<String, List<OrderItemLite>> itemMapByKey = new HashMap<>();
            List<OrderItemLite> itemList = new ArrayList<>();
            for (Map<String, Object> row : allItems) {
                OrderItemLite item = new OrderItemLite();
                item.id = toLong(row.get("id"));
                item.orderId = toLong(row.get("order_id"));
                item.orderNo = toText(row.get("order_no")).trim();
                item.materialCode = normalizeKeyPart(toText(row.get("material_code")));
                item.thickness = normalizeKeyPart(toText(row.get("thickness")));
                item.width = normalizeKeyPart(toText(row.get("width")));
                item.length = normalizeKeyPart(toText(row.get("length")));
                item.rolls = Math.max(0, toInt(row.get("rolls"), 0));
                item.deliveredQty = Math.max(0, toInt(row.get("delivered_qty"), 0));
                item.remainingQty = Math.max(0, toInt(row.get("remaining_qty"), Math.max(0, item.rolls - item.deliveredQty)));
                item.productionStatus = toText(row.get("production_status"));

                if (item.id == null || item.orderId == null || item.orderNo.isEmpty()) {
                    continue;
                }
                String key = buildCompletedCompositeKey(item.orderNo, item.materialCode, item.thickness, item.width, item.length);
                itemMapByKey.computeIfAbsent(key, k -> new ArrayList<>()).add(item);
                itemList.add(item);
            }

            int matchedRows = 0;
            int skippedReportedItems = 0;
            int skippedOverLimitRows = 0;
            int updatedItems = 0;
            int autoCompletedItems = 0;
            Set<Long> touchedOrderIds = new LinkedHashSet<>();
            Set<String> unmatchedKeys = new LinkedHashSet<>();
            Set<String> overLimitKeys = new LinkedHashSet<>();
            Map<Long, Object[]> itemUpdateById = new LinkedHashMap<>();

            for (UncompletedOrderRow row : rows) {
                if (row == null) {
                    continue;
                }
                String key = buildCompletedCompositeKey(row.orderNo, row.materialCode, row.thickness, row.width, row.length);
                if (key.isEmpty()) {
                    continue;
                }

                List<OrderItemLite> matchedItems = itemMapByKey.get(key);
                if (matchedItems == null || matchedItems.isEmpty()) {
                    unmatchedKeys.add(key);
                    continue;
                }
                matchedRows++;

                int uploadedCompleted = row.completedQty == null ? 0 : Math.max(0, row.completedQty);
                int totalRolls = 0;
                for (OrderItemLite item : matchedItems) {
                    if (item != null) {
                        totalRolls += Math.max(0, item.rolls);
                    }
                }
                if (uploadedCompleted > totalRolls) {
                    skippedOverLimitRows++;
                    overLimitKeys.add(key);
                    continue;
                }

                int remainingToComplete = uploadedCompleted;
                for (OrderItemLite item : matchedItems) {
                    if (item == null || item.id == null) {
                        continue;
                    }
                    if (item.orderId != null) {
                        touchedOrderIds.add(item.orderId);
                    }
                    if (reportedOrderDetailIds.contains(item.id)) {
                        skippedReportedItems++;
                        continue;
                    }

                    int targetCompleted = Math.min(item.rolls, Math.max(remainingToComplete, 0));
                    remainingToComplete -= targetCompleted;
                    int targetRemaining = Math.max(0, item.rolls - targetCompleted);
                    String targetStatus = targetRemaining == 0 ? "completed" : (targetCompleted > 0 ? "partial" : "not_started");

                    boolean changed = item.deliveredQty != targetCompleted
                            || item.remainingQty != targetRemaining
                            || !targetStatus.equalsIgnoreCase(trimOrDefault(item.productionStatus, ""));
                    if (!changed) {
                        continue;
                    }

                    itemUpdateById.put(item.id, new Object[]{targetCompleted, targetRemaining, targetStatus, op, item.id});
                    updatedItems++;
                }
            }

            // 不在“未完成表”中的系统明细，自动置为已完成（已有报工明细跳过不动）
            for (OrderItemLite item : itemList) {
                if (item == null || item.id == null) {
                    continue;
                }
                String key = buildCompletedCompositeKey(item.orderNo, item.materialCode, item.thickness, item.width, item.length);
                if (uploadKeys.contains(key)) {
                    continue;
                }
                if (reportedOrderDetailIds.contains(item.id)) {
                    continue;
                }

                int targetCompleted = item.rolls;
                int targetRemaining = 0;
                String targetStatus = "completed";
                boolean changed = item.deliveredQty != targetCompleted
                        || item.remainingQty != targetRemaining
                        || !targetStatus.equalsIgnoreCase(trimOrDefault(item.productionStatus, ""));
                if (!changed) {
                    continue;
                }

                if (!itemUpdateById.containsKey(item.id)) {
                    updatedItems++;
                }
                itemUpdateById.put(item.id, new Object[]{targetCompleted, targetRemaining, targetStatus, op, item.id});
                touchedOrderIds.add(item.orderId);
                autoCompletedItems++;
            }

            List<Object[]> itemBatchArgs = new ArrayList<>(itemUpdateById.values());

            if (!itemBatchArgs.isEmpty()) {
                jdbcTemplate.batchUpdate(
                        "UPDATE sales_order_items SET delivered_qty = ?, remaining_qty = ?, production_status = ?, updated_by = ?, updated_at = NOW() WHERE id = ?",
                        itemBatchArgs
                );
            }

            int updatedOrders = 0;
            if (!touchedOrderIds.isEmpty()) {
                StringBuilder inClause = new StringBuilder();
                List<Object> inArgs = new ArrayList<>();
                for (Long orderId : touchedOrderIds) {
                    if (orderId == null) {
                        continue;
                    }
                    if (inClause.length() > 0) {
                        inClause.append(",");
                    }
                    inClause.append("?");
                    inArgs.add(orderId);
                }

                if (inClause.length() > 0) {
                    Map<Long, String> currentOrderStatusMap = new HashMap<>();
                    List<Map<String, Object>> currentStatusRows = jdbcTemplate.queryForList(
                            "SELECT id, status FROM sales_orders WHERE is_deleted = 0 AND id IN (" + inClause + ")",
                            inArgs.toArray()
                    );
                    for (Map<String, Object> row : currentStatusRows) {
                        Long id = toLong(row.get("id"));
                        if (id != null) {
                            currentOrderStatusMap.put(id, trimOrDefault(toText(row.get("status")), "").toLowerCase());
                        }
                    }

                    List<Map<String, Object>> aggRows = jdbcTemplate.queryForList(
                            "SELECT order_id, " +
                            "IFNULL(SUM(IFNULL(delivered_qty, 0)), 0) AS completed_rolls, " +
                            "IFNULL(SUM(IFNULL(remaining_qty, GREATEST(rolls - IFNULL(delivered_qty, 0), 0))), 0) AS remaining_rolls " +
                                    "FROM sales_order_items " +
                                    "WHERE is_deleted = 0 AND order_id IN (" + inClause + ") " +
                                    "GROUP BY order_id",
                            inArgs.toArray()
                    );

                    List<Object[]> orderBatchArgs = new ArrayList<>();
                    for (Map<String, Object> agg : aggRows) {
                        Long orderId = toLong(agg.get("order_id"));
                        int completed = toInt(agg.get("completed_rolls"), 0);
                        int remaining = toInt(agg.get("remaining_rolls"), 0);
                        String status = remaining <= 0 ? "completed" : (completed > 0 ? "processing" : "pending");
                        if (orderId != null) {
                            String currentStatus = trimOrDefault(currentOrderStatusMap.get(orderId), "").toLowerCase();
                            String targetStatus = trimOrDefault(status, "").toLowerCase();

                            if ("cancelled".equals(currentStatus) || "canceled".equals(currentStatus) || "closed".equals(currentStatus)) {
                                continue;
                            }
                            if ("completed".equals(currentStatus) && !"completed".equals(targetStatus)) {
                                continue;
                            }
                            if ("processing".equals(currentStatus) && "pending".equals(targetStatus)) {
                                continue;
                            }
                            if (!currentStatus.equals(targetStatus)) {
                                orderBatchArgs.add(new Object[]{status, op, orderId});
                            }
                        }
                    }

                    if (!orderBatchArgs.isEmpty()) {
                        jdbcTemplate.batchUpdate(
                                "UPDATE sales_orders SET status = ?, updated_by = ?, updated_at = NOW() WHERE id = ?",
                                orderBatchArgs
                        );
                        updatedOrders = orderBatchArgs.size();
                    }
                }
            }

            List<String> unmatchedKeyList = new ArrayList<>(unmatchedKeys);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("uploadedRows", rows.size());
            result.put("matchedRows", matchedRows);
            result.put("updatedItems", updatedItems);
            result.put("autoCompletedItems", autoCompletedItems);
            result.put("skippedReportedItems", skippedReportedItems);
            result.put("skippedOverLimitRows", skippedOverLimitRows);
            result.put("updatedOrders", updatedOrders);
            result.put("unmatchedKeys", unmatchedKeyList.size() > 200 ? unmatchedKeyList.subList(0, 200) : unmatchedKeyList);
            result.put("unmatchedKeysTotal", unmatchedKeyList.size());
            List<String> overLimitKeyList = new ArrayList<>(overLimitKeys);
            result.put("overLimitKeys", overLimitKeyList.size() > 200 ? overLimitKeyList.subList(0, 200) : overLimitKeyList);
            result.put("overLimitKeysTotal", overLimitKeyList.size());
            result.put("message", "历史未完成订单导入完成");

            return new ResponseResult<>(200, "历史未完成订单导入完成", result);
        } catch (Exception e) {
            Throwable root = e;
            while (root.getCause() != null) {
                root = root.getCause();
            }
            String detail = root.getMessage() == null ? root.getClass().getSimpleName()
                    : (root.getClass().getSimpleName() + " - " + root.getMessage());
            return new ResponseResult<>(500, "导入失败: " + detail);
        }
    }

    private List<StatusDetailRow> parseOrderStatusWorkbook(Workbook workbook) {
        List<StatusDetailRow> rows = new ArrayList<>();
        if (workbook == null) {
            return rows;
        }

        for (int s = 0; s < workbook.getNumberOfSheets(); s++) {
            Sheet sheet = workbook.getSheetAt(s);
            if (sheet == null || sheet.getLastRowNum() < 1) {
                continue;
            }
            Row header = sheet.getRow(0);
            if (header == null) {
                continue;
            }

            Map<String, Integer> headerIndex = buildHeaderIndex(header);
            int orderNoIdx = resolveHeaderIndex(headerIndex, "订单号", "订单编号", "销售订单号", "order_no", "orderno");
            int materialCodeIdx = resolveHeaderIndex(headerIndex, "产品编码", "料号", "material_code", "materialcode");
            int thicknessIdx = resolveHeaderIndex(headerIndex, "厚度", "厚度/μ", "厚度(μm)", "thickness");
            int widthIdx = resolveHeaderIndex(headerIndex, "宽度", "宽度/mm", "width");
            int lengthIdx = resolveHeaderIndex(headerIndex, "长度", "长度/m", "length");
                int rollsIdx = resolveHeaderIndex(headerIndex,
                    "卷数", "订单数量卷", "订单数量(卷)", "订单数量（卷）", "订单数量", "生产数量", "rolls", "qty");
            int completedIdx = resolveHeaderIndex(headerIndex,
                    "完成卷数", "已完成卷数", "完成数量", "completed_qty", "completedrolls");
            int remainingIdx = resolveHeaderIndex(headerIndex,
                    "未完成卷数", "未完成数量", "未完成卷", "欠卷", "欠卷数", "remaining_qty", "remainingrolls");
            int statusIdx = resolveHeaderIndex(headerIndex,
                    "完成状态", "未完成状态", "状态", "status");

            if (orderNoIdx < 0) {
                continue;
            }

            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) {
                    continue;
                }

                String orderNo = getCellText(row.getCell(orderNoIdx));
                if (orderNo == null || orderNo.trim().isEmpty()) {
                    continue;
                }

                StatusDetailRow detail = new StatusDetailRow();
                detail.orderNo = orderNo.trim();
                detail.materialCode = materialCodeIdx >= 0 ? getCellText(row.getCell(materialCodeIdx)) : "";
                detail.thickness = thicknessIdx >= 0 ? getCellText(row.getCell(thicknessIdx)) : "";
                detail.width = widthIdx >= 0 ? getCellText(row.getCell(widthIdx)) : "";
                detail.length = lengthIdx >= 0 ? getCellText(row.getCell(lengthIdx)) : "";
                detail.rolls = rollsIdx >= 0 ? getCellText(row.getCell(rollsIdx)) : "";
                detail.completedRolls = completedIdx >= 0 ? getCellInt(row.getCell(completedIdx)) : null;
                detail.remainingRolls = remainingIdx >= 0 ? getCellInt(row.getCell(remainingIdx)) : null;
                detail.status = statusIdx >= 0 ? getCellText(row.getCell(statusIdx)) : null;
                rows.add(detail);
            }
        }
        return rows;
    }

    private List<CompletedOrderRow> parseCompletedOrdersWorkbook(Workbook workbook) {
        List<CompletedOrderRow> rows = new ArrayList<>();
        if (workbook == null) {
            return rows;
        }

        for (int s = 0; s < workbook.getNumberOfSheets(); s++) {
            Sheet sheet = workbook.getSheetAt(s);
            if (sheet == null || sheet.getLastRowNum() < 1) {
                continue;
            }
            Row header = sheet.getRow(0);
            if (header == null) {
                continue;
            }

            Map<String, Integer> headerIndex = buildHeaderIndex(header);
            int orderNoIdx = resolveHeaderIndex(headerIndex, "订单号", "订单编号", "销售订单号", "order_no", "orderno");
            int materialCodeIdx = resolveHeaderIndex(headerIndex, "物料代码", "产品编码", "料号", "material_code", "materialcode");
            int thicknessIdx = resolveHeaderIndex(headerIndex, "厚度", "厚度/μ", "厚度(μm)", "thickness");
            int widthIdx = resolveHeaderIndex(headerIndex, "宽度", "宽度/mm", "width");
            int lengthIdx = resolveHeaderIndex(headerIndex, "长度", "长度/m", "length");

            if (orderNoIdx < 0 || materialCodeIdx < 0 || thicknessIdx < 0 || widthIdx < 0 || lengthIdx < 0) {
                continue;
            }

            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) {
                    continue;
                }

                String orderNo = getCellText(row.getCell(orderNoIdx));
                String materialCode = getCellText(row.getCell(materialCodeIdx));
                String thickness = getCellText(row.getCell(thicknessIdx));
                String width = getCellText(row.getCell(widthIdx));
                String length = getCellText(row.getCell(lengthIdx));

                if (orderNo == null || orderNo.trim().isEmpty()
                        || materialCode == null || materialCode.trim().isEmpty()
                        || thickness == null || thickness.trim().isEmpty()
                        || width == null || width.trim().isEmpty()
                        || length == null || length.trim().isEmpty()) {
                    continue;
                }

                CompletedOrderRow detail = new CompletedOrderRow();
                detail.orderNo = orderNo.trim();
                detail.materialCode = materialCode.trim();
                detail.thickness = thickness.trim();
                detail.width = width.trim();
                detail.length = length.trim();
                rows.add(detail);
            }
        }
        return rows;
    }

    private List<UncompletedOrderRow> parseUncompletedOrdersWorkbook(Workbook workbook) {
        List<UncompletedOrderRow> rows = new ArrayList<>();
        if (workbook == null) {
            return rows;
        }

        for (int s = 0; s < workbook.getNumberOfSheets(); s++) {
            Sheet sheet = workbook.getSheetAt(s);
            if (sheet == null || sheet.getLastRowNum() < 1) {
                continue;
            }
            Row header = sheet.getRow(0);
            if (header == null) {
                continue;
            }

            Map<String, Integer> headerIndex = buildHeaderIndex(header);
            int orderNoIdx = resolveHeaderIndex(headerIndex, "订单号", "订单编号", "销售订单号", "order_no", "orderno");
            int materialCodeIdx = resolveHeaderIndex(headerIndex, "物料代码", "产品编码", "料号", "material_code", "materialcode");
            int thicknessIdx = resolveHeaderIndex(headerIndex, "厚度", "厚度/μ", "厚度(μm)", "thickness");
            int widthIdx = resolveHeaderIndex(headerIndex, "宽度", "宽度/mm", "width");
            int lengthIdx = resolveHeaderIndex(headerIndex, "长度", "长度/m", "length");
            int completedQtyIdx = resolveHeaderIndex(headerIndex,
                    "已完成数量", "已完成卷数", "完成数量", "完成卷数", "completed_qty", "completedqty", "completedrolls");

            if (orderNoIdx < 0 || materialCodeIdx < 0 || thicknessIdx < 0 || widthIdx < 0 || lengthIdx < 0 || completedQtyIdx < 0) {
                continue;
            }

            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) {
                    continue;
                }

                String orderNo = getCellText(row.getCell(orderNoIdx));
                String materialCode = getCellText(row.getCell(materialCodeIdx));
                String thickness = getCellText(row.getCell(thicknessIdx));
                String width = getCellText(row.getCell(widthIdx));
                String length = getCellText(row.getCell(lengthIdx));
                Integer completedQty = getCellInt(row.getCell(completedQtyIdx));

                if (orderNo == null || orderNo.trim().isEmpty()
                        || materialCode == null || materialCode.trim().isEmpty()
                        || thickness == null || thickness.trim().isEmpty()
                        || width == null || width.trim().isEmpty()
                        || length == null || length.trim().isEmpty()) {
                    continue;
                }

                UncompletedOrderRow detail = new UncompletedOrderRow();
                detail.orderNo = orderNo.trim();
                detail.materialCode = materialCode.trim();
                detail.thickness = thickness.trim();
                detail.width = width.trim();
                detail.length = length.trim();
                detail.completedQty = completedQty == null ? 0 : Math.max(0, completedQty);
                rows.add(detail);
            }
        }
        return rows;
    }

    private String normalizeKeyPart(String value) {
        if (value == null) {
            return "";
        }
        String v = value
                .replace('\u00A0', ' ')
                .replace('\u3000', ' ')
                .replace("\t", " ")
                .trim();
        if (v.isEmpty()) {
            return "";
        }
        String compact = v.replaceAll("\\s+", "");
        try {
            return new BigDecimal(compact.replace(",", "")).stripTrailingZeros().toPlainString();
        } catch (Exception ignored) {
            return compact.toLowerCase();
        }
    }

    private String buildCompositeKey(String orderNo, String materialCode, String thickness, String width, String length, String rolls) {
        String o = orderNo == null ? "" : orderNo.trim();
        String m = materialCode == null ? "" : materialCode.trim();
        return o + "|" + normalizeKeyPart(m) + "|" + normalizeKeyPart(thickness) + "|" + normalizeKeyPart(width) + "|" + normalizeKeyPart(length) + "|" + normalizeKeyPart(rolls);
    }

    private String buildCompletedCompositeKey(String orderNo, String materialCode, String thickness, String width, String length) {
        String o = orderNo == null ? "" : orderNo.trim();
        String m = materialCode == null ? "" : materialCode.trim();
        if (o.isEmpty() || m.isEmpty()) {
            return "";
        }
        return o + "|" + normalizeKeyPart(m) + "|" + normalizeKeyPart(thickness) + "|" + normalizeKeyPart(width) + "|" + normalizeKeyPart(length);
    }

    private int resolveTargetCompleted(int totalRolls, Integer completedRolls, Integer remainingRolls, String statusText) {
        if (totalRolls <= 0) {
            return 0;
        }
        if (completedRolls != null) {
            return Math.max(0, Math.min(totalRolls, completedRolls));
        }
        if (remainingRolls != null) {
            int remaining = Math.max(0, remainingRolls);
            remaining = Math.min(remaining, totalRolls);
            return totalRolls - remaining;
        }
        String status = statusText == null ? "" : statusText.trim().toLowerCase();

        // 先识别“未完成/部分完成”，避免“未完成”被“完成”关键词误判
        if (status.contains("未完成") || status.contains("未完") || status.contains("unfinished")
                || status.contains("pending") || status.contains("not_started") || status.contains("未开始")) {
            return 0;
        }

        // 部分完成：若无数量字段，按未完成处理（保持未完成状态）
        if (status.contains("部分") || status.contains("partial") || status.contains("processing") || status.contains("进行")) {
            return 0;
        }

        if (status.contains("已完成") || status.contains("完工") || status.contains("completed")
                || status.contains("complete") || status.contains("done")) {
            return totalRolls;
        }
        return 0;
    }

    private Map<String, Integer> buildHeaderIndex(Row headerRow) {
        Map<String, Integer> map = new HashMap<>();
        short lastCellNum = headerRow.getLastCellNum();
        for (int i = 0; i < lastCellNum; i++) {
            String text = normalizeHeader(getCellText(headerRow.getCell(i)));
            if (text != null && !text.isEmpty()) {
                map.put(text, i);
            }
        }
        return map;
    }

    private int resolveHeaderIndex(Map<String, Integer> headerIndex, String... aliases) {
        if (headerIndex == null || aliases == null) {
            return -1;
        }
        for (String alias : aliases) {
            Integer idx = headerIndex.get(normalizeHeader(alias));
            if (idx != null) {
                return idx;
            }
        }
        return -1;
    }

    private String normalizeHeader(String header) {
        if (header == null) {
            return null;
        }
        return header.trim()
                .replace("（", "(")
                .replace("）", ")")
                .replace("/", "")
                .replace("_", "")
                .replace("*", "")
                .replace(" ", "")
                .toLowerCase();
    }

    private String getCellText(Cell cell) {
        if (cell == null) {
            return null;
        }
        try {
            if (cell.getCellType() == CellType.NUMERIC) {
                double value = cell.getNumericCellValue();
                long longValue = (long) value;
                if (Double.compare(value, longValue) == 0) {
                    return String.valueOf(longValue);
                }
            }
            String text = dataFormatter.formatCellValue(cell);
            if (text == null) {
                return null;
            }
            text = text.trim();
            return text.isEmpty() ? null : text;
        } catch (Exception e) {
            return null;
        }
    }

    private Integer getCellInt(Cell cell) {
        String text = getCellText(cell);
        if (text == null) {
            return null;
        }
        try {
            return new BigDecimal(text.replace(",", "")).setScale(0, BigDecimal.ROUND_HALF_UP).intValue();
        } catch (Exception e) {
            return null;
        }
    }

    private Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (Exception e) {
            return null;
        }
    }

    private Integer toInt(Object value, Integer defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private void ensureCompanyConfigTable() {
        if (companyConfigChecked) {
            return;
        }
        synchronized (this) {
            if (companyConfigChecked) {
                return;
            }
            jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS system_company_config (" +
                    "id BIGINT PRIMARY KEY," +
                    "company_name VARCHAR(255) NOT NULL," +
                    "address VARCHAR(500)," +
                    "phone VARCHAR(100)," +
                    "fax VARCHAR(100)," +
                    "website VARCHAR(255)," +
                    "logo_url VARCHAR(500)," +
                    "status_sheet_initialized TINYINT NOT NULL DEFAULT 0," +
                    "status_sheet_initialized_at DATETIME NULL," +
                    "status_sheet_initialized_by VARCHAR(64) NULL," +
                    "updated_by VARCHAR(64)," +
                    "updated_at DATETIME" +
                    ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");

                    // 兼容老表结构（兼容不支持 ADD COLUMN IF NOT EXISTS 的 MySQL 版本）
                    ensureColumnIfMissing("system_company_config", "status_sheet_initialized", "status_sheet_initialized TINYINT NOT NULL DEFAULT 0");
                    ensureColumnIfMissing("system_company_config", "status_sheet_initialized_at", "status_sheet_initialized_at DATETIME NULL");
                    ensureColumnIfMissing("system_company_config", "status_sheet_initialized_by", "status_sheet_initialized_by VARCHAR(64) NULL");
            companyConfigChecked = true;
        }
    }

        private void ensureColumnIfMissing(String tableName, String columnName, String columnDefinition) {
            Integer cnt = jdbcTemplate.queryForObject(
                    "SELECT COUNT(1) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?",
                    Integer.class,
                    tableName,
                    columnName
            );
            if (cnt == null || cnt == 0) {
                jdbcTemplate.execute("ALTER TABLE " + tableName + " ADD COLUMN " + columnDefinition);
            }
        }

            private boolean isStatusSheetInitialized() {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT status_sheet_initialized FROM system_company_config WHERE id = 1 LIMIT 1"
            );
            if (rows == null || rows.isEmpty()) {
                return false;
            }
            Object v = rows.get(0).get("status_sheet_initialized");
            return toInt(v, 0) == 1;
            }

            private void markStatusSheetInitialized(String operator) {
            jdbcTemplate.update(
                "UPDATE system_company_config SET status_sheet_initialized = 1, status_sheet_initialized_at = NOW(), status_sheet_initialized_by = ?, updated_by = ?, updated_at = NOW() WHERE id = 1",
                operator, operator
            );
            }

    private void insertDefaultIfAbsent() {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("SELECT id FROM system_company_config WHERE id = 1 LIMIT 1");
        if (rows == null || rows.isEmpty()) {
            jdbcTemplate.update(
                    "INSERT INTO system_company_config (id, company_name, address, phone, fax, website, logo_url, updated_by, updated_at) VALUES (1, ?, ?, ?, ?, ?, ?, ?, NOW())",
                    "东莞市方恩电子材料科技有限公司",
                    "广东省东莞市桥头镇东新路13号2号楼102室",
                    "0769-82551118",
                    "0769-82551160",
                    "www.finechemfr.com",
                    "/logo/finechem-logo.png",
                    "system"
            );
        }
    }

    private String toText(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private String trimOrDefault(String value, String defaultValue) {
        if (value == null || value.trim().isEmpty()) {
            return defaultValue;
        }
        return value.trim();
    }
}
