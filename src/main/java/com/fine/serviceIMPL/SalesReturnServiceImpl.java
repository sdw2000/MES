package com.fine.serviceIMPL;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fine.Dao.CustomerMapper;
import com.fine.Dao.SalesReturnItemMapper;
import com.fine.Dao.SalesReturnMapper;
import com.fine.Dao.SalesOrderItemMapper;
import com.fine.Dao.production.SalesOrderMapper;
import com.fine.Utils.ResponseResult;
import com.fine.modle.LoginUser;
import com.fine.modle.SalesOrder;
import com.fine.modle.SalesOrderItem;
import com.fine.modle.SalesReturn;
import com.fine.modle.SalesReturnItem;
import com.fine.service.SalesReturnService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class SalesReturnServiceImpl extends ServiceImpl<SalesReturnMapper, SalesReturn> implements SalesReturnService {

    @Autowired
    private SalesReturnMapper salesReturnMapper;

    @Autowired
    private SalesReturnItemMapper salesReturnItemMapper;

    @Autowired
    private SalesOrderItemMapper salesOrderItemMapper;

    @Autowired
    private SalesOrderMapper salesOrderMapper;

    @Autowired
    private CustomerMapper customerMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private volatile boolean returnTablesChecked = false;

    @Override
    public ResponseResult<?> getAllReturns(Integer pageNum, Integer pageSize, String returnNo, String customer,
                                           String startDate, String endDate, String status) {
        try {
            ensureReturnTables();
            Page<SalesReturn> page = new Page<>(pageNum, pageSize);
            LambdaQueryWrapper<SalesReturn> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(SalesReturn::getIsDeleted, 0);

            Set<String> allowedCustomers = getAccessibleCustomerKeys();
            if (allowedCustomers != null && allowedCustomers.isEmpty()) {
                Map<String, Object> empty = new HashMap<>();
                empty.put("list", Collections.emptyList());
                empty.put("total", 0);
                empty.put("pageNum", pageNum);
                empty.put("pageSize", pageSize);
                return new ResponseResult<>(200, "查询成功", empty);
            }
            if (allowedCustomers != null) {
                wrapper.in(SalesReturn::getCustomer, allowedCustomers);
            }

            if (returnNo != null && !returnNo.trim().isEmpty()) {
                wrapper.like(SalesReturn::getReturnNo, returnNo.trim());
            }
            if (customer != null && !customer.trim().isEmpty()) {
                wrapper.like(SalesReturn::getCustomer, customer.trim());
            }
            if (status != null && !status.trim().isEmpty()) {
                wrapper.eq(SalesReturn::getStatus, status.trim());
            }
            if (startDate != null && !startDate.trim().isEmpty()) {
                wrapper.ge(SalesReturn::getReturnDate, startDate.trim());
            }
            if (endDate != null && !endDate.trim().isEmpty()) {
                wrapper.le(SalesReturn::getReturnDate, endDate.trim());
            }
            wrapper.orderByDesc(SalesReturn::getCreatedAt);

            Page<SalesReturn> result = salesReturnMapper.selectPage(page, wrapper);

            Map<String, Object> data = new HashMap<>();
            data.put("list", result.getRecords());
            data.put("total", result.getTotal());
            data.put("pageNum", pageNum);
            data.put("pageSize", pageSize);
            return new ResponseResult<>(200, "查询成功", data);
        } catch (Exception e) {
            e.printStackTrace();
            return new ResponseResult<>(500, "查询退货单失败: " + e.getMessage(), null);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResponseResult<?> createReturn(SalesReturn salesReturn) {
        try {
            ensureReturnTables();
            if (salesReturn == null) {
                return new ResponseResult<>(400, "参数不能为空", null);
            }
            if (salesReturn.getCustomer() == null || salesReturn.getCustomer().trim().isEmpty()) {
                return new ResponseResult<>(400, "客户不能为空", null);
            }
            if (!canAccessCustomer(salesReturn.getCustomer())) {
                return new ResponseResult<>(403, "无权限操作该客户");
            }
            if (salesReturn.getReturnDate() == null) {
                salesReturn.setReturnDate(LocalDate.now());
            }
            if (salesReturn.getReturnNo() == null || salesReturn.getReturnNo().trim().isEmpty()) {
                String generatedNo = buildReturnNo(salesReturn.getReturnDate());
                salesReturn.setReturnNo(generatedNo);
            }
            if (salesReturn.getStatus() == null || salesReturn.getStatus().trim().isEmpty()) {
                salesReturn.setStatus("confirmed");
            }

            Date now = new Date();
            String operator = getCurrentUsername();
            salesReturn.setCreatedAt(now);
            salesReturn.setUpdatedAt(now);
            salesReturn.setCreatedBy(operator);
            salesReturn.setUpdatedBy(operator);
            salesReturn.setIsDeleted(0);

            computeAndFillTotals(salesReturn);
            ResponseResult<?> validation = validateReturnItems(salesReturn.getItems(), null);
            if (validation != null) {
                return validation;
            }
            salesReturnMapper.insert(salesReturn);

            saveItems(salesReturn.getId(), salesReturn.getItems(), now);
                writeAuditLog(salesReturn.getId(), salesReturn.getReturnNo(), "CREATE",
                    null, salesReturn.getStatus(), salesReturn.getReason(),
                    "创建退货单");

            return new ResponseResult<>(200, "创建退货单成功", salesReturn);
        } catch (Exception e) {
            e.printStackTrace();
            return new ResponseResult<>(500, "创建退货单失败: " + e.getMessage(), null);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResponseResult<?> updateReturn(SalesReturn salesReturn) {
        try {
            ensureReturnTables();
            if (salesReturn == null || salesReturn.getReturnNo() == null || salesReturn.getReturnNo().trim().isEmpty()) {
                return new ResponseResult<>(400, "退货单号不能为空", null);
            }

            LambdaQueryWrapper<SalesReturn> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(SalesReturn::getReturnNo, salesReturn.getReturnNo()).eq(SalesReturn::getIsDeleted, 0);
            SalesReturn existing = salesReturnMapper.selectOne(wrapper);
            if (existing == null) {
                return new ResponseResult<>(404, "退货单不存在", null);
            }
            if (!canAccessCustomer(existing.getCustomer())) {
                return new ResponseResult<>(403, "无权限操作该退货单");
            }
            if (salesReturn.getCustomer() != null && !salesReturn.getCustomer().trim().isEmpty()
                    && !canAccessCustomer(salesReturn.getCustomer())) {
                return new ResponseResult<>(403, "无权限操作该客户");
            }

            salesReturn.setId(existing.getId());
            salesReturn.setCreatedAt(existing.getCreatedAt());
            salesReturn.setCreatedBy(existing.getCreatedBy());
            salesReturn.setUpdatedAt(new Date());
            salesReturn.setUpdatedBy(getCurrentUsername());
            salesReturn.setIsDeleted(0);
            if (salesReturn.getReturnDate() == null) {
                salesReturn.setReturnDate(existing.getReturnDate());
            }

            computeAndFillTotals(salesReturn);
            ResponseResult<?> validation = validateReturnItems(salesReturn.getItems(), salesReturn.getReturnNo());
            if (validation != null) {
                return validation;
            }
            salesReturnMapper.updateById(salesReturn);

            LambdaQueryWrapper<SalesReturnItem> deleteWrapper = new LambdaQueryWrapper<>();
            deleteWrapper.eq(SalesReturnItem::getReturnId, salesReturn.getId());
            salesReturnItemMapper.delete(deleteWrapper);

            saveItems(salesReturn.getId(), salesReturn.getItems(), new Date());
                writeAuditLog(salesReturn.getId(), salesReturn.getReturnNo(), "UPDATE",
                    existing.getStatus(), salesReturn.getStatus(), salesReturn.getReason(),
                    "更新退货单");

            return new ResponseResult<>(200, "更新退货单成功", salesReturn);
        } catch (Exception e) {
            e.printStackTrace();
            return new ResponseResult<>(500, "更新退货单失败: " + e.getMessage(), null);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResponseResult<?> deleteReturn(String returnNo) {
        try {
            ensureReturnTables();
            LambdaQueryWrapper<SalesReturn> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(SalesReturn::getReturnNo, returnNo).eq(SalesReturn::getIsDeleted, 0);
            SalesReturn existing = salesReturnMapper.selectOne(wrapper);
            if (existing == null) {
                return new ResponseResult<>(404, "退货单不存在", null);
            }
            if (!canAccessCustomer(existing.getCustomer())) {
                return new ResponseResult<>(403, "无权限操作该退货单");
            }

            salesReturnMapper.deleteById(existing.getId());
            LambdaQueryWrapper<SalesReturnItem> deleteWrapper = new LambdaQueryWrapper<>();
            deleteWrapper.eq(SalesReturnItem::getReturnId, existing.getId());
            salesReturnItemMapper.delete(deleteWrapper);

                writeAuditLog(existing.getId(), existing.getReturnNo(), "DELETE",
                    existing.getStatus(), "deleted", existing.getReason(),
                    "删除退货单");

            return new ResponseResult<>(200, "删除成功", null);
        } catch (Exception e) {
            e.printStackTrace();
            return new ResponseResult<>(500, "删除退货单失败: " + e.getMessage(), null);
        }
    }

    @Override
    public ResponseResult<?> getReturnDetail(String returnNo) {
        try {
            ensureReturnTables();
            LambdaQueryWrapper<SalesReturn> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(SalesReturn::getReturnNo, returnNo).eq(SalesReturn::getIsDeleted, 0);
            SalesReturn found = salesReturnMapper.selectOne(wrapper);
            if (found == null) {
                return new ResponseResult<>(404, "退货单不存在", null);
            }
            if (!canAccessCustomer(found.getCustomer())) {
                return new ResponseResult<>(403, "无权限访问该退货单");
            }

            LambdaQueryWrapper<SalesReturnItem> itemWrapper = new LambdaQueryWrapper<>();
            itemWrapper.eq(SalesReturnItem::getReturnId, found.getId()).eq(SalesReturnItem::getIsDeleted, 0);
            found.setItems(salesReturnItemMapper.selectList(itemWrapper));
            return new ResponseResult<>(200, "查询成功", found);
        } catch (Exception e) {
            e.printStackTrace();
            return new ResponseResult<>(500, "查询退货详情失败: " + e.getMessage(), null);
        }
    }

    @Override
    public ResponseResult<?> generateReturnNo(String customerCode, LocalDate returnDate) {
        try {
            ensureReturnTables();
            LocalDate date = returnDate == null ? LocalDate.now() : returnDate;
            String no = buildReturnNo(date);
            Map<String, String> data = new HashMap<>();
            data.put("returnNo", no);
            return new ResponseResult<>(200, "生成成功", data);
        } catch (Exception e) {
            e.printStackTrace();
            return new ResponseResult<>(500, "生成退货单号失败: " + e.getMessage(), null);
        }
    }

    @Override
    public ResponseResult<?> reconciliationSummary(String month) {
        try {
            ensureReturnTables();
            if (month == null || month.trim().isEmpty()) {
                return new ResponseResult<>(400, "month不能为空，格式yyyy-MM", null);
            }
            QueryWrapper<SalesReturn> wrapper = new QueryWrapper<>();
            wrapper.eq("is_deleted", 0).eq("statement_month", month.trim());
            Set<String> allowedCustomers = getAccessibleCustomerKeys();
            if (allowedCustomers != null && allowedCustomers.isEmpty()) {
                Map<String, Object> data = new HashMap<>();
                data.put("month", month.trim());
                data.put("rows", Collections.emptyList());
                data.put("total", 0);
                return new ResponseResult<>(200, "查询成功", data);
            }
            if (allowedCustomers != null) {
                wrapper.in("customer", allowedCustomers);
            }
            List<SalesReturn> list = salesReturnMapper.selectList(wrapper);

            Map<String, BigDecimal> customerAmountMap = new LinkedHashMap<>();
            Map<String, Integer> customerCountMap = new LinkedHashMap<>();
            for (SalesReturn one : list) {
                String customer = one.getCustomer() == null ? "" : one.getCustomer();
                BigDecimal amt = one.getStatementAmount() == null ? BigDecimal.ZERO : one.getStatementAmount();
                customerAmountMap.put(customer, customerAmountMap.getOrDefault(customer, BigDecimal.ZERO).add(amt));
                customerCountMap.put(customer, customerCountMap.getOrDefault(customer, 0) + 1);
            }

            List<Map<String, Object>> rows = new ArrayList<>();
            for (Map.Entry<String, BigDecimal> entry : customerAmountMap.entrySet()) {
                Map<String, Object> row = new HashMap<>();
                row.put("customer", entry.getKey());
                row.put("returnCount", customerCountMap.getOrDefault(entry.getKey(), 0));
                row.put("statementAmount", entry.getValue().setScale(2, RoundingMode.HALF_UP));
                rows.add(row);
            }

            Map<String, Object> data = new HashMap<>();
            data.put("month", month.trim());
            data.put("rows", rows);
            data.put("total", rows.size());
            return new ResponseResult<>(200, "查询成功", data);
        } catch (Exception e) {
            e.printStackTrace();
            return new ResponseResult<>(500, "查询退货对账汇总失败: " + e.getMessage(), null);
        }
    }

    @Override
    public ResponseResult<?> getReturnableOrderItems(String orderNo, String excludeReturnNo) {
        try {
            ensureReturnTables();
            if (orderNo == null || orderNo.trim().isEmpty()) {
                return new ResponseResult<>(400, "orderNo不能为空", null);
            }

            SalesOrder salesOrder = salesOrderMapper.selectByOrderNo(orderNo.trim());
            if (salesOrder == null) {
                return new ResponseResult<>(404, "订单不存在", null);
            }
            if (!canAccessCustomer(salesOrder.getCustomer())) {
                return new ResponseResult<>(403, "无权限访问该订单");
            }

            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT soi.id AS id, " +
                        "so.order_no AS orderNo, " +
                        "soi.material_code AS materialCode, " +
                        "soi.material_name AS materialName, " +
                        "soi.color_code AS colorCode, " +
                        "soi.thickness AS thickness, " +
                        "soi.width AS width, " +
                        "soi.length AS length, " +
                        "soi.rolls AS rolls, " +
                        "soi.sqm AS sqm, " +
                        "soi.unit_price AS unitPrice, " +
                        "soi.amount AS amount, " +
                        "soi.remark AS remark " +
                            "FROM sales_order_items soi " +
                            "INNER JOIN sales_orders so ON so.id = soi.order_id " +
                            "WHERE soi.is_deleted = 0 AND so.is_deleted = 0 AND so.order_no = ?",
                    orderNo.trim()
            );

            Map<Long, Integer> returnedMap = getExistingReturnedRollsMap(excludeReturnNo);
            List<Map<String, Object>> result = new ArrayList<>();
            for (Map<String, Object> row : rows) {
                Long itemId = toLong(row.get("id"));
                Integer orderRolls = toInteger(row.get("rolls"));
                Integer returnedRolls = itemId == null ? 0 : returnedMap.getOrDefault(itemId, 0);
                int availableRolls = Math.max(0, orderRolls - returnedRolls);

                Map<String, Object> item = new HashMap<>(row);
                item.put("sourceOrderItemId", itemId);
                item.put("returnedRolls", returnedRolls);
                item.put("availableReturnRolls", availableRolls);
                result.add(item);
            }
            return new ResponseResult<>(200, "查询成功", result);
        } catch (Exception e) {
            e.printStackTrace();
            return new ResponseResult<>(500, "查询订单可退明细失败: " + e.getMessage(), null);
        }
    }

    @Override
    public ResponseResult<?> getReturnAuditLogs(String returnNo, Integer pageNum, Integer pageSize) {
        try {
            ensureReturnTables();

            int safePageNum = (pageNum == null || pageNum < 1) ? 1 : pageNum;
            int safePageSize = (pageSize == null || pageSize < 1) ? 20 : Math.min(pageSize, 200);
            int offset = (safePageNum - 1) * safePageSize;

            StringBuilder where = new StringBuilder(" WHERE 1=1 ");
            List<Object> params = new ArrayList<>();

            if (returnNo != null && !returnNo.trim().isEmpty()) {
                where.append(" AND l.return_no = ? ");
                params.add(returnNo.trim());
            }

            Set<String> allowedCustomers = getAccessibleCustomerKeys();
            if (allowedCustomers != null) {
                if (allowedCustomers.isEmpty()) {
                    Map<String, Object> empty = new HashMap<>();
                    empty.put("list", Collections.emptyList());
                    empty.put("total", 0);
                    empty.put("pageNum", safePageNum);
                    empty.put("pageSize", safePageSize);
                    return new ResponseResult<>(200, "查询成功", empty);
                }
                where.append(" AND sro.customer IN (");
                for (int i = 0; i < allowedCustomers.size(); i++) {
                    if (i > 0) {
                        where.append(",");
                    }
                    where.append("?");
                }
                where.append(") ");
                params.addAll(allowedCustomers);
            }

            String countSql = "SELECT COUNT(1) " +
                    "FROM sales_return_audit_logs l " +
                    "LEFT JOIN sales_return_orders sro ON sro.id = l.return_id " +
                    where;
            Integer total = jdbcTemplate.queryForObject(countSql, Integer.class, params.toArray());

            List<Object> queryArgs = new ArrayList<>(params);
            queryArgs.add(offset);
            queryArgs.add(safePageSize);
            String querySql = "SELECT l.id, l.return_id AS returnId, l.return_no AS returnNo, " +
                    "l.action_type AS actionType, l.before_status AS beforeStatus, l.after_status AS afterStatus, " +
                    "l.reason, l.detail, l.operator, l.created_at AS createdAt " +
                    "FROM sales_return_audit_logs l " +
                    "LEFT JOIN sales_return_orders sro ON sro.id = l.return_id " +
                    where +
                    " ORDER BY l.id DESC LIMIT ?, ?";
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(querySql, queryArgs.toArray());

            Map<String, Object> data = new HashMap<>();
            data.put("list", rows);
            data.put("total", total == null ? 0 : total);
            data.put("pageNum", safePageNum);
            data.put("pageSize", safePageSize);
            return new ResponseResult<>(200, "查询成功", data);
        } catch (Exception e) {
            e.printStackTrace();
            return new ResponseResult<>(500, "查询退货审计日志失败: " + e.getMessage(), null);
        }
    }

    private String buildReturnNo(LocalDate returnDate) {
        String dateStr = returnDate.format(DateTimeFormatter.ofPattern("yyMMdd"));
        String base = "RT" + dateStr + "-";

        LambdaQueryWrapper<SalesReturn> wrapper = new LambdaQueryWrapper<>();
        wrapper.likeRight(SalesReturn::getReturnNo, base).eq(SalesReturn::getIsDeleted, 0);
        List<SalesReturn> exists = salesReturnMapper.selectList(wrapper);

        int maxSeq = 0;
        for (SalesReturn one : exists) {
            if (one.getReturnNo() == null) continue;
            String tail = one.getReturnNo().substring(base.length());
            if (tail.matches("\\d+")) {
                maxSeq = Math.max(maxSeq, Integer.parseInt(tail));
            }
        }
        return base + String.format("%03d", maxSeq + 1);
    }

    private void ensureReturnTables() {
        if (returnTablesChecked) {
            return;
        }
        synchronized (this) {
            if (returnTablesChecked) {
                return;
            }

            if (!tableExists("sales_return_orders") || !tableExists("sales_return_items")) {
                throw new IllegalStateException("退货模块缺少必要数据表，请先执行版本化脚本: sql/V20260316_01__sales_return_tables.sql");
            }

            ensureReturnAuditTable();

            returnTablesChecked = true;
        }
    }

    private void ensureReturnAuditTable() {
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS sales_return_audit_logs (" +
                "id BIGINT PRIMARY KEY AUTO_INCREMENT," +
                "return_id BIGINT," +
                "return_no VARCHAR(64)," +
                "action_type VARCHAR(32) NOT NULL," +
                "before_status VARCHAR(32)," +
                "after_status VARCHAR(32)," +
                "reason VARCHAR(255)," +
                "detail VARCHAR(500)," +
                "operator VARCHAR(64)," +
                "created_at DATETIME NOT NULL," +
                "INDEX idx_sral_return_no (return_no)," +
                "INDEX idx_sral_return_id (return_id)," +
                "INDEX idx_sral_created_at (created_at)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
    }

    private void writeAuditLog(Long returnId,
                               String returnNo,
                               String actionType,
                               String beforeStatus,
                               String afterStatus,
                               String reason,
                               String detail) {
        try {
            jdbcTemplate.update(
                    "INSERT INTO sales_return_audit_logs(" +
                            "return_id, return_no, action_type, before_status, after_status, reason, detail, operator, created_at" +
                            ") VALUES (?,?,?,?,?,?,?,?,NOW())",
                    returnId,
                    returnNo,
                    actionType,
                    beforeStatus,
                    afterStatus,
                    trimToNull(reason),
                    trimToNull(detail),
                    getCurrentUsername()
            );
        } catch (Exception ignored) {
        }
    }

    private String trimToNull(String text) {
        if (text == null) {
            return null;
        }
        String trimmed = text.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private boolean tableExists(String tableName) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ?",
                Integer.class,
                tableName
        );
        return count != null && count > 0;
    }

    private void computeAndFillTotals(SalesReturn salesReturn) {
        List<SalesReturnItem> items = salesReturn.getItems() == null ? new ArrayList<>() : salesReturn.getItems();
        BigDecimal totalArea = BigDecimal.ZERO;
        BigDecimal totalAmount = BigDecimal.ZERO;

        for (SalesReturnItem item : items) {
            BigDecimal sqm = calcSqm(item);
            item.setSqm(sqm);
            BigDecimal unitPrice = item.getUnitPrice() == null ? BigDecimal.ZERO : item.getUnitPrice();
            BigDecimal amount = item.getAmount();
            if (amount == null) {
                amount = sqm.multiply(unitPrice).setScale(2, RoundingMode.HALF_UP);
            }
            item.setAmount(amount);
            totalArea = totalArea.add(sqm);
            totalAmount = totalAmount.add(amount);
        }

        salesReturn.setTotalArea(totalArea.setScale(2, RoundingMode.HALF_UP));
        salesReturn.setTotalAmount(totalAmount.setScale(2, RoundingMode.HALF_UP));
        salesReturn.setStatementAmount(totalAmount.abs().negate().setScale(2, RoundingMode.HALF_UP));
        LocalDate d = salesReturn.getReturnDate() == null ? LocalDate.now() : salesReturn.getReturnDate();
        salesReturn.setStatementMonth(YearMonth.from(d).toString());
    }

    private ResponseResult<?> validateReturnItems(List<SalesReturnItem> items, String excludeReturnNo) {
        if (items == null || items.isEmpty()) {
            return null;
        }
        Map<Long, Integer> existingReturnedMap = getExistingReturnedRollsMap(excludeReturnNo);
        Map<Long, Integer> currentRequestMap = new HashMap<>();

        for (SalesReturnItem item : items) {
            if (item == null || item.getSourceOrderItemId() == null) {
                continue;
            }
            int requestRolls = item.getRolls() == null ? 0 : item.getRolls();
            if (requestRolls <= 0) {
                continue;
            }
            currentRequestMap.put(item.getSourceOrderItemId(), currentRequestMap.getOrDefault(item.getSourceOrderItemId(), 0) + requestRolls);
        }

        for (Map.Entry<Long, Integer> entry : currentRequestMap.entrySet()) {
            SalesOrderItem orderItem = salesOrderItemMapper.selectById(entry.getKey());
            if (orderItem == null || orderItem.getIsDeleted() == 1) {
                return new ResponseResult<>(400, "存在无效的来源订单明细，无法保存退货单", null);
            }
            int totalRolls = orderItem.getRolls() == null ? 0 : orderItem.getRolls();
            int alreadyReturned = existingReturnedMap.getOrDefault(entry.getKey(), 0);
            int available = Math.max(0, totalRolls - alreadyReturned);
            if (entry.getValue() > available) {
                String materialCode = orderItem.getMaterialCode() == null ? "" : orderItem.getMaterialCode();
                return new ResponseResult<>(400,
                        "退货数量超出可退范围：料号" + materialCode + "，可退卷数" + available + "，本次填写" + entry.getValue(), null);
            }
        }
        return null;
    }

    private Map<Long, Integer> getExistingReturnedRollsMap(String excludeReturnNo) {
        Map<Long, Integer> map = new HashMap<>();
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT sri.source_order_item_id AS source_order_item_id, SUM(COALESCE(sri.rolls, 0)) AS returned_rolls ")
                .append("FROM sales_return_items sri ")
                .append("INNER JOIN sales_return_orders sro ON sro.id = sri.return_id ")
                .append("WHERE sri.is_deleted = 0 AND sro.is_deleted = 0 ")
                .append("AND sri.source_order_item_id IS NOT NULL ");

        List<Object> args = new ArrayList<>();
        if (excludeReturnNo != null && !excludeReturnNo.trim().isEmpty()) {
            sql.append("AND sro.return_no <> ? ");
            args.add(excludeReturnNo.trim());
        }
        sql.append("GROUP BY sri.source_order_item_id");

        String querySql = Objects.requireNonNull(sql.toString());
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(querySql, args.toArray());
        for (Map<String, Object> row : rows) {
            Long itemId = toLong(row.get("source_order_item_id"));
            Integer returnedRolls = toInteger(row.get("returned_rolls"));
            if (itemId != null) {
              map.put(itemId, returnedRolls);
            }
        }
        return map;
    }

    private Long toLong(Object value) {
        if (value == null) return null;
        if (value instanceof Number) return ((Number) value).longValue();
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (Exception e) {
            return null;
        }
    }

    private Integer toInteger(Object value) {
        if (value == null) return 0;
        if (value instanceof Number) return ((Number) value).intValue();
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception e) {
            return 0;
        }
    }

    private BigDecimal calcSqm(SalesReturnItem item) {
        BigDecimal length = item.getLength() == null ? BigDecimal.ZERO : item.getLength(); // m
        BigDecimal width = item.getWidth() == null ? BigDecimal.ZERO : item.getWidth();   // mm
        Integer rolls = item.getRolls() == null ? 0 : item.getRolls();

        return length.multiply(width)
                .multiply(BigDecimal.valueOf(rolls))
                .divide(BigDecimal.valueOf(1000), 2, RoundingMode.HALF_UP);
    }

    private void saveItems(Long returnId, List<SalesReturnItem> items, Date now) {
        if (items == null || items.isEmpty()) return;
        String operator = getCurrentUsername();
        for (SalesReturnItem item : items) {
            item.setId(null);
            item.setReturnId(returnId);
            // 降低冗余：退货明细仅保留料号，品名展示由关联主数据带出
            item.setMaterialName(null);
            item.setCreatedAt(now);
            item.setUpdatedAt(now);
            item.setCreatedBy(operator);
            item.setUpdatedBy(operator);
            item.setIsDeleted(0);
            salesReturnItemMapper.insert(item);
        }
    }

    private LoginUser getLoginUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof LoginUser) {
            return (LoginUser) authentication.getPrincipal();
        }
        return null;
    }

    private boolean hasRole(LoginUser loginUser, String role) {
        return loginUser != null && loginUser.getPermissions() != null && loginUser.getPermissions().contains(role);
    }

    private Long getCurrentUserId(LoginUser loginUser) {
        return loginUser != null && loginUser.getUser() != null ? loginUser.getUser().getId() : null;
    }

    private Set<String> getAccessibleCustomerKeys() {
        LoginUser loginUser = getLoginUser();
        if (loginUser == null || hasRole(loginUser, "admin")) {
            return null;
        }
        Long uid = getCurrentUserId(loginUser);
        if (uid == null) {
            return Collections.emptySet();
        }

        Set<String> allowed = new LinkedHashSet<>();
        List<String> names = customerMapper.selectCustomerNamesByOwner(uid);
        List<String> codes = customerMapper.selectCustomerCodesByOwner(uid);
        if (names != null) {
            for (String name : names) {
                if (name != null && !name.trim().isEmpty()) {
                    allowed.add(name.trim());
                }
            }
        }
        if (codes != null) {
            for (String code : codes) {
                if (code != null && !code.trim().isEmpty()) {
                    allowed.add(code.trim());
                }
            }
        }
        return allowed;
    }

    private boolean canAccessCustomer(String customerKey) {
        Set<String> allowed = getAccessibleCustomerKeys();
        if (allowed == null) {
            return true;
        }
        if (customerKey == null || customerKey.trim().isEmpty()) {
            return false;
        }
        return allowed.contains(customerKey.trim());
    }

    private String getCurrentUsername() {
        LoginUser loginUser = getLoginUser();
        return loginUser != null ? loginUser.getUsername() : "system";
    }
}
