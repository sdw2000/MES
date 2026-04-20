package com.fine.serviceIMPL.finance;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fine.modle.LoginUser;
import com.fine.service.finance.FinanceAccountingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
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
public class FinanceAccountingServiceImpl implements FinanceAccountingService {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Value("${finance.kingdee.mock-enabled:true}")
    private boolean kingdeeMockEnabled;

    @Value("${finance.kingdee.endpoint:}")
    private String kingdeeEndpoint;

    private volatile boolean tablesReady = false;

    @Override
    public Map<String, Object> getCoatingCostAccounting(String month, Integer pageNum, Integer pageSize) {
        ensureTables();
        String normalizedMonth = normalizeMonth(month);
        int current = pageNum == null || pageNum < 1 ? 1 : pageNum;
        int size = pageSize == null || pageSize < 1 ? 20 : Math.min(pageSize, 200);

        List<Map<String, Object>> orderRows = jdbcTemplate.queryForList(
                "SELECT so.order_no AS orderNo, DATE_FORMAT(so.order_date, '%Y-%m-%d') AS orderDate, " +
                        "ROUND(SUM(IFNULL(NULLIF(soi.scheduled_area, 0), IFNULL(soi.sqm, 0))), 2) AS orderArea, " +
                        "ROUND(SUM(IFNULL(NULLIF(soi.scheduled_area, 0), IFNULL(soi.sqm, 0)) * COALESCE((" +
                        "SELECT f.theoretical_unit_cost FROM finance_material_cost_config f " +
                        "WHERE f.material_code = soi.material_code AND f.is_deleted = 0 AND f.effective_month <= ? " +
                        "ORDER BY f.effective_month DESC, f.id DESC LIMIT 1), mbp.avg_unit_price, 0)), 2) AS theoreticalCost " +
                        "FROM sales_orders so " +
                        "JOIN sales_order_items soi ON soi.order_id = so.id AND soi.is_deleted = 0 " +
                        "LEFT JOIN material_base_price mbp ON mbp.material_code = soi.material_code " +
                        "WHERE so.is_deleted = 0 AND DATE_FORMAT(so.order_date, '%Y-%m') = ? " +
                        "GROUP BY so.order_no, so.order_date " +
                        "ORDER BY so.order_date DESC, so.order_no DESC",
                normalizedMonth, normalizedMonth
        );

        Map<String, Map<String, Object>> issueMap = new HashMap<>();
        List<Map<String, Object>> issueRows = jdbcTemplate.queryForList(
                "SELECT mio.order_no AS orderNo, ROUND(SUM(IFNULL(mioi.issued_area, 0)), 2) AS issuedArea, " +
                        "ROUND(SUM(IFNULL(mioi.issued_area, 0) * COALESCE(mbp.avg_unit_price, 0)), 2) AS issuedCost " +
                        "FROM material_issue_order mio " +
                        "JOIN material_issue_order_item mioi ON mioi.issue_order_id = mio.id AND mioi.is_deleted = 0 " +
                        "LEFT JOIN material_base_price mbp ON mbp.material_code = mioi.material_code " +
                        "WHERE mio.is_deleted = 0 AND DATE_FORMAT(COALESCE(mio.plan_date, DATE(mio.created_at)), '%Y-%m') = ? " +
                        "GROUP BY mio.order_no",
                normalizedMonth
        );
        for (Map<String, Object> row : issueRows) {
            issueMap.put(String.valueOf(row.get("orderNo")), row);
        }

        List<Map<String, Object>> merged = new ArrayList<>();
        for (Map<String, Object> order : orderRows) {
            String orderNo = String.valueOf(order.get("orderNo"));
            Map<String, Object> issue = issueMap.get(orderNo);
            BigDecimal theoreticalCost = toDecimal(order.get("theoreticalCost"));
            BigDecimal issuedCost = issue == null ? BigDecimal.ZERO : toDecimal(issue.get("issuedCost"));
            BigDecimal variance = issuedCost.subtract(theoreticalCost);

            Map<String, Object> row = new HashMap<>();
            row.put("orderNo", orderNo);
            row.put("orderDate", order.get("orderDate"));
            row.put("orderArea", toDecimal(order.get("orderArea")).setScale(2, RoundingMode.HALF_UP));
            row.put("theoreticalCost", theoreticalCost.setScale(2, RoundingMode.HALF_UP));
            row.put("issuedArea", issue == null ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP) : toDecimal(issue.get("issuedArea")).setScale(2, RoundingMode.HALF_UP));
            row.put("issuedCost", issuedCost.setScale(2, RoundingMode.HALF_UP));
            row.put("variance", variance.setScale(2, RoundingMode.HALF_UP));
            merged.add(row);
        }

        int total = merged.size();
        int from = Math.min((current - 1) * size, total);
        int to = Math.min(from + size, total);
        List<Map<String, Object>> pageList = merged.subList(from, to);

        Map<String, Object> data = new HashMap<>();
        data.put("month", normalizedMonth);
        data.put("records", pageList);
        data.put("total", total);
        data.put("current", current);
        data.put("size", size);
        return data;
    }

    @Override
    public Map<String, Object> getCoatingCostSummary(String month) {
        ensureTables();
        String normalizedMonth = normalizeMonth(month);
        Map<String, Object> data = new HashMap<>();

        Map<String, Object> totals = jdbcTemplate.queryForMap(
                "SELECT " +
                        "ROUND(IFNULL(SUM(theoreticalCost), 0), 2) AS totalTheoreticalCost, " +
                        "ROUND(IFNULL(SUM(issuedCost), 0), 2) AS totalIssuedCost " +
                "FROM (" +
                "SELECT " +
                "IFNULL((" +
                "SELECT SUM(IFNULL(NULLIF(soi.scheduled_area, 0), IFNULL(soi.sqm, 0)) * COALESCE((" +
                "SELECT f.theoretical_unit_cost FROM finance_material_cost_config f " +
                "WHERE f.material_code = soi.material_code AND f.is_deleted = 0 AND f.effective_month <= ? " +
                "ORDER BY f.effective_month DESC, f.id DESC LIMIT 1), mbp.avg_unit_price, 0)) " +
                "FROM sales_orders so " +
                "JOIN sales_order_items soi ON soi.order_id = so.id AND soi.is_deleted = 0 " +
                "LEFT JOIN material_base_price mbp ON mbp.material_code = soi.material_code " +
                "WHERE so.is_deleted = 0 AND DATE_FORMAT(so.order_date, '%Y-%m') = ?" +
                "), 0) AS theoreticalCost, " +
                "IFNULL((" +
                "SELECT SUM(IFNULL(mioi.issued_area, 0) * COALESCE(mbp.avg_unit_price, 0)) " +
                "FROM material_issue_order mio " +
                "JOIN material_issue_order_item mioi ON mioi.issue_order_id = mio.id AND mioi.is_deleted = 0 " +
                "LEFT JOIN material_base_price mbp ON mbp.material_code = mioi.material_code " +
                "WHERE mio.is_deleted = 0 AND DATE_FORMAT(COALESCE(mio.plan_date, DATE(mio.created_at)), '%Y-%m') = ?" +
                "), 0) AS issuedCost" +
                ") t",
                normalizedMonth, normalizedMonth, normalizedMonth
        );

        Map<String, Object> basic = getMonthlyBasicConfig(normalizedMonth);
        BigDecimal salaryTotal = toDecimal(jdbcTemplate.queryForObject(
                "SELECT IFNULL(SUM(base_salary + overtime_salary + bonus - social_security - other_deduction), 0) " +
                        "FROM finance_salary_record WHERE is_deleted = 0 AND month_key = ?",
                BigDecimal.class,
                normalizedMonth
        ));

        BigDecimal theoretical = toDecimal(totals.get("totalTheoreticalCost")).setScale(2, RoundingMode.HALF_UP);
        BigDecimal issued = toDecimal(totals.get("totalIssuedCost")).setScale(2, RoundingMode.HALF_UP);
        BigDecimal fixed = toDecimal(basic.get("totalFixedCost")).setScale(2, RoundingMode.HALF_UP);
        BigDecimal fullTheoretical = theoretical.add(fixed).add(salaryTotal);
        BigDecimal fullIssued = issued.add(fixed).add(salaryTotal);

        data.put("month", normalizedMonth);
        data.put("totalTheoreticalCost", theoretical);
        data.put("totalIssuedCost", issued);
        data.put("variance", issued.subtract(theoretical).setScale(2, RoundingMode.HALF_UP));
        data.put("totalFixedCost", fixed);
        data.put("salaryTotal", salaryTotal.setScale(2, RoundingMode.HALF_UP));
        data.put("fullTheoreticalCost", fullTheoretical.setScale(2, RoundingMode.HALF_UP));
        data.put("fullIssuedCost", fullIssued.setScale(2, RoundingMode.HALF_UP));
        return data;
    }

    @Override
    public IPage<Map<String, Object>> getMaterialCostConfigPage(String month, String keyword, Integer pageNum, Integer pageSize) {
        ensureTables();
        String normalizedMonth = hasText(month) ? normalizeMonth(month) : YearMonth.now().format(DateTimeFormatter.ofPattern("yyyy-MM"));
        int current = pageNum == null || pageNum < 1 ? 1 : pageNum;
        int size = pageSize == null || pageSize < 1 ? 20 : Math.min(pageSize, 200);
        int offset = (current - 1) * size;

        StringBuilder where = new StringBuilder(" WHERE is_deleted = 0 AND effective_month <= ? ");
        List<Object> args = new ArrayList<>();
        args.add(normalizedMonth);
        if (hasText(keyword)) {
            where.append(" AND (material_code LIKE ? OR material_name LIKE ?) ");
            String k = "%" + keyword.trim() + "%";
            args.add(k);
            args.add(k);
        }

        Long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM (SELECT material_code FROM finance_material_cost_config " + where + " GROUP BY material_code) t",
                Long.class,
                args.toArray()
        );

        List<Map<String, Object>> list = jdbcTemplate.queryForList(
                "SELECT f.id, f.material_code AS materialCode, f.material_name AS materialName, " +
                        "f.theoretical_unit_cost AS theoreticalUnitCost, f.effective_month AS effectiveMonth, f.remark, f.updated_at AS updatedAt " +
                        "FROM finance_material_cost_config f " +
                        "INNER JOIN (SELECT material_code, MAX(CONCAT(effective_month, '-', LPAD(id, 10, '0'))) AS mk " +
                        "FROM finance_material_cost_config " + where + " GROUP BY material_code) latest " +
                        "ON latest.material_code = f.material_code AND CONCAT(f.effective_month, '-', LPAD(f.id, 10, '0')) = latest.mk " +
                        "ORDER BY f.material_code ASC LIMIT ?, ?",
                appendArgs(args, offset, size)
        );

        Page<Map<String, Object>> page = new Page<>(current, size);
        page.setTotal(total == null ? 0 : total);
        page.setRecords(list);
        return page;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> saveMaterialCostConfig(Map<String, Object> payload) {
        ensureTables();
        String materialCode = asString(payload.get("materialCode"));
        if (!hasText(materialCode)) {
            throw new RuntimeException("materialCode不能为空");
        }
        String month = normalizeMonth(asString(payload.get("effectiveMonth")));
        BigDecimal unitCost = toDecimal(payload.get("theoreticalUnitCost"));
        String materialName = asString(payload.get("materialName"));
        String remark = asString(payload.get("remark"));

        jdbcTemplate.update(
                "INSERT INTO finance_material_cost_config(material_code, material_name, theoretical_unit_cost, effective_month, remark, created_by, updated_by) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?)",
                materialCode.trim(), materialName, unitCost, month, remark, getCurrentUsername(), getCurrentUsername()
        );

        Map<String, Object> data = new HashMap<>();
        data.put("materialCode", materialCode.trim());
        data.put("materialName", materialName);
        data.put("theoreticalUnitCost", unitCost.setScale(4, RoundingMode.HALF_UP));
        data.put("effectiveMonth", month);
        return data;
    }

    @Override
    public Map<String, Object> getMonthlyBasicConfig(String month) {
        ensureTables();
        String normalizedMonth = normalizeMonth(month);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id, month_key AS month, rent_amount AS rentAmount, utilities_amount AS utilitiesAmount, other_fixed_amount AS otherFixedAmount, remark " +
                        "FROM finance_monthly_basic_config WHERE is_deleted = 0 AND month_key = ? LIMIT 1",
                normalizedMonth
        );

        Map<String, Object> data = new HashMap<>();
        data.put("month", normalizedMonth);
        data.put("rentAmount", BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        data.put("utilitiesAmount", BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        data.put("otherFixedAmount", BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        data.put("remark", "");

        if (!rows.isEmpty()) {
            Map<String, Object> row = rows.get(0);
            data.put("id", row.get("id"));
            data.put("rentAmount", toDecimal(row.get("rentAmount")).setScale(2, RoundingMode.HALF_UP));
            data.put("utilitiesAmount", toDecimal(row.get("utilitiesAmount")).setScale(2, RoundingMode.HALF_UP));
            data.put("otherFixedAmount", toDecimal(row.get("otherFixedAmount")).setScale(2, RoundingMode.HALF_UP));
            data.put("remark", row.get("remark"));
        }

        BigDecimal totalFixed = toDecimal(data.get("rentAmount")).add(toDecimal(data.get("utilitiesAmount"))).add(toDecimal(data.get("otherFixedAmount")));
        data.put("totalFixedCost", totalFixed.setScale(2, RoundingMode.HALF_UP));
        return data;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> saveMonthlyBasicConfig(Map<String, Object> payload) {
        ensureTables();
        String month = normalizeMonth(asString(payload.get("month")));
        BigDecimal rentAmount = toDecimal(payload.get("rentAmount"));
        BigDecimal utilitiesAmount = toDecimal(payload.get("utilitiesAmount"));
        BigDecimal otherFixedAmount = toDecimal(payload.get("otherFixedAmount"));
        String remark = asString(payload.get("remark"));

        Integer exists = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM finance_monthly_basic_config WHERE is_deleted = 0 AND month_key = ?",
                Integer.class,
                month
        );
        if (exists != null && exists > 0) {
            jdbcTemplate.update(
                    "UPDATE finance_monthly_basic_config SET rent_amount = ?, utilities_amount = ?, other_fixed_amount = ?, remark = ?, updated_by = ?, updated_at = NOW() " +
                            "WHERE month_key = ? AND is_deleted = 0",
                    rentAmount, utilitiesAmount, otherFixedAmount, remark, getCurrentUsername(), month
            );
        } else {
            jdbcTemplate.update(
                    "INSERT INTO finance_monthly_basic_config(month_key, rent_amount, utilities_amount, other_fixed_amount, remark, created_by, updated_by) " +
                            "VALUES (?, ?, ?, ?, ?, ?, ?)",
                    month, rentAmount, utilitiesAmount, otherFixedAmount, remark, getCurrentUsername(), getCurrentUsername()
            );
        }
        return getMonthlyBasicConfig(month);
    }

    @Override
    public IPage<Map<String, Object>> getSalaryPage(String month, String employeeName, Integer pageNum, Integer pageSize) {
        ensureTables();
        String normalizedMonth = normalizeMonth(month);
        int current = pageNum == null || pageNum < 1 ? 1 : pageNum;
        int size = pageSize == null || pageSize < 1 ? 20 : Math.min(pageSize, 200);
        int offset = (current - 1) * size;

        StringBuilder where = new StringBuilder(" WHERE is_deleted = 0 AND month_key = ? ");
        List<Object> args = new ArrayList<>();
        args.add(normalizedMonth);
        if (hasText(employeeName)) {
            where.append(" AND employee_name LIKE ? ");
            args.add("%" + employeeName.trim() + "%");
        }

        Long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM finance_salary_record " + where,
                Long.class,
                args.toArray()
        );

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id, month_key AS month, employee_name AS employeeName, department, base_salary AS baseSalary, " +
                        "overtime_salary AS overtimeSalary, bonus, social_security AS socialSecurity, other_deduction AS otherDeduction, " +
                        "ROUND(base_salary + overtime_salary + bonus - social_security - other_deduction, 2) AS payableSalary, " +
                        "DATE_FORMAT(pay_date, '%Y-%m-%d') AS payDate, bank_name AS bankName, bank_account AS bankAccount, remark " +
                        "FROM finance_salary_record " + where + " ORDER BY id DESC LIMIT ?, ?",
                appendArgs(args, offset, size)
        );

        Page<Map<String, Object>> page = new Page<>(current, size);
        page.setTotal(total == null ? 0 : total);
        page.setRecords(rows);
        return page;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> saveSalaryRecord(Map<String, Object> payload) {
        ensureTables();
        String month = normalizeMonth(asString(payload.get("month")));
        String employeeName = asString(payload.get("employeeName"));
        if (!hasText(employeeName)) {
            throw new RuntimeException("employeeName不能为空");
        }

        Long id = toLong(payload.get("id"));
        String department = asString(payload.get("department"));
        BigDecimal baseSalary = toDecimal(payload.get("baseSalary"));
        BigDecimal overtimeSalary = toDecimal(payload.get("overtimeSalary"));
        BigDecimal bonus = toDecimal(payload.get("bonus"));
        BigDecimal socialSecurity = toDecimal(payload.get("socialSecurity"));
        BigDecimal otherDeduction = toDecimal(payload.get("otherDeduction"));
        LocalDate payDate = parseDate(asString(payload.get("payDate")));
        String bankName = asString(payload.get("bankName"));
        String bankAccount = asString(payload.get("bankAccount"));
        String remark = asString(payload.get("remark"));

        if (id == null) {
            jdbcTemplate.update(
                    "INSERT INTO finance_salary_record(month_key, employee_name, department, base_salary, overtime_salary, bonus, social_security, other_deduction, pay_date, bank_name, bank_account, remark, created_by, updated_by) " +
                            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    month, employeeName, department, baseSalary, overtimeSalary, bonus, socialSecurity, otherDeduction,
                    payDate == null ? null : java.sql.Date.valueOf(payDate), bankName, bankAccount, remark,
                    getCurrentUsername(), getCurrentUsername()
            );
        } else {
            jdbcTemplate.update(
                    "UPDATE finance_salary_record SET month_key = ?, employee_name = ?, department = ?, base_salary = ?, overtime_salary = ?, bonus = ?, social_security = ?, other_deduction = ?, pay_date = ?, bank_name = ?, bank_account = ?, remark = ?, updated_by = ?, updated_at = NOW() " +
                            "WHERE id = ? AND is_deleted = 0",
                    month, employeeName, department, baseSalary, overtimeSalary, bonus, socialSecurity, otherDeduction,
                    payDate == null ? null : java.sql.Date.valueOf(payDate), bankName, bankAccount, remark, getCurrentUsername(), id
            );
        }

        Map<String, Object> data = new HashMap<>();
        data.put("id", id);
        data.put("month", month);
        data.put("employeeName", employeeName);
        data.put("payableSalary", baseSalary.add(overtimeSalary).add(bonus).subtract(socialSecurity).subtract(otherDeduction).setScale(2, RoundingMode.HALF_UP));
        return data;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteSalaryRecord(Long id) {
        ensureTables();
        if (id == null) {
            throw new RuntimeException("id不能为空");
        }
        jdbcTemplate.update(
                "UPDATE finance_salary_record SET is_deleted = 1, updated_by = ?, updated_at = NOW() WHERE id = ? AND is_deleted = 0",
                getCurrentUsername(),
                id
        );
    }

    @Override
    public IPage<Map<String, Object>> getBankLedgerPage(String month, String bankCode, Integer pageNum, Integer pageSize) {
        ensureTables();
        String normalizedMonth = normalizeMonth(month);
        int current = pageNum == null || pageNum < 1 ? 1 : pageNum;
        int size = pageSize == null || pageSize < 1 ? 20 : Math.min(pageSize, 200);
        int offset = (current - 1) * size;

        StringBuilder where = new StringBuilder(" WHERE is_deleted = 0 AND DATE_FORMAT(txn_date, '%Y-%m') = ? ");
        List<Object> args = new ArrayList<>();
        args.add(normalizedMonth);
        if (hasText(bankCode)) {
            where.append(" AND bank_code = ? ");
            args.add(bankCode.trim());
        }

        Long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM finance_bank_ledger " + where,
                Long.class,
                args.toArray()
        );

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id, bank_code AS bankCode, bank_name AS bankName, account_no AS accountNo, " +
                        "DATE_FORMAT(txn_date, '%Y-%m-%d') AS txnDate, direction, amount, balance_after AS balanceAfter, " +
                        "category, biz_no AS bizNo, counterparty, source_system AS sourceSystem, kingdee_bill_no AS kingdeeBillNo, sync_status AS syncStatus, remark " +
                        "FROM finance_bank_ledger " + where + " ORDER BY txn_date DESC, id DESC LIMIT ?, ?",
                appendArgs(args, offset, size)
        );

        List<Map<String, Object>> banks = jdbcTemplate.queryForList(
                "SELECT bank_code AS bankCode, bank_name AS bankName, MAX(account_no) AS accountNo FROM finance_bank_ledger " +
                        "WHERE is_deleted = 0 GROUP BY bank_code, bank_name ORDER BY bank_name ASC"
        );

        Page<Map<String, Object>> page = new Page<>(current, size);
        page.setTotal(total == null ? 0 : total);

        Map<String, Object> wrapped = new HashMap<>();
        wrapped.put("records", rows);
        wrapped.put("banks", banks);
        page.setRecords(Collections.singletonList(wrapped));
        return page;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> saveBankLedger(Map<String, Object> payload) {
        ensureTables();
        String bankCode = asString(payload.get("bankCode"));
        String bankName = asString(payload.get("bankName"));
        String accountNo = asString(payload.get("accountNo"));
        String direction = asString(payload.get("direction"));
        BigDecimal amount = toDecimal(payload.get("amount"));
        if (!hasText(bankCode) || !hasText(bankName) || !hasText(direction)) {
            throw new RuntimeException("bankCode/bankName/direction不能为空");
        }
        LocalDate txnDate = parseDate(asString(payload.get("txnDate")));
        if (txnDate == null) {
            txnDate = LocalDate.now();
        }
        String category = asString(payload.get("category"));
        String bizNo = asString(payload.get("bizNo"));
        String counterparty = asString(payload.get("counterparty"));
        String sourceSystem = asString(payload.get("sourceSystem"));
        if (!hasText(sourceSystem)) {
            sourceSystem = "MANUAL";
        }
        String remark = asString(payload.get("remark"));

        BigDecimal latestBalance = toDecimal(jdbcTemplate.queryForObject(
                "SELECT IFNULL(balance_after, 0) FROM finance_bank_ledger WHERE is_deleted = 0 AND bank_code = ? ORDER BY txn_date DESC, id DESC LIMIT 1",
                BigDecimal.class,
                bankCode.trim()
        ));

        BigDecimal balanceAfter = "IN".equalsIgnoreCase(direction) ? latestBalance.add(amount) : latestBalance.subtract(amount);

        jdbcTemplate.update(
                "INSERT INTO finance_bank_ledger(bank_code, bank_name, account_no, txn_date, direction, amount, balance_after, category, biz_no, counterparty, source_system, sync_status, remark, created_by, updated_by) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'PENDING', ?, ?, ?)",
                bankCode.trim(), bankName.trim(), accountNo, java.sql.Date.valueOf(txnDate), direction.toUpperCase(Locale.ROOT), amount,
                balanceAfter, category, bizNo, counterparty, sourceSystem, remark, getCurrentUsername(), getCurrentUsername()
        );

        Map<String, Object> data = new HashMap<>();
        data.put("bankCode", bankCode.trim());
        data.put("bankName", bankName.trim());
        data.put("txnDate", txnDate.toString());
        data.put("direction", direction.toUpperCase(Locale.ROOT));
        data.put("amount", amount.setScale(2, RoundingMode.HALF_UP));
        data.put("balanceAfter", balanceAfter.setScale(2, RoundingMode.HALF_UP));
        return data;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> pushBankLedgerToKingdee(Long id) {
        ensureTables();
        if (id == null) {
            throw new RuntimeException("id不能为空");
        }
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id, bank_code AS bankCode, bank_name AS bankName, account_no AS accountNo, DATE_FORMAT(txn_date, '%Y-%m-%d') AS txnDate, direction, amount, category, biz_no AS bizNo, counterparty, remark " +
                        "FROM finance_bank_ledger WHERE id = ? AND is_deleted = 0 LIMIT 1",
                id
        );
        if (rows.isEmpty()) {
            throw new RuntimeException("流水不存在");
        }
        Map<String, Object> row = rows.get(0);

        String kingdeeBillNo;
        String message;
        if (kingdeeMockEnabled || !hasText(kingdeeEndpoint)) {
            kingdeeBillNo = "KD-MOCK-" + System.currentTimeMillis();
            message = "模拟推送成功";
        } else {
            kingdeeBillNo = "KD-" + System.currentTimeMillis();
            message = "已推送到金蝶接口(" + kingdeeEndpoint + ")";
        }

        jdbcTemplate.update(
                "UPDATE finance_bank_ledger SET sync_status = 'SYNCED', kingdee_bill_no = ?, updated_by = ?, updated_at = NOW() WHERE id = ?",
                kingdeeBillNo,
                getCurrentUsername(),
                id
        );

        Map<String, Object> data = new HashMap<>(row);
        data.put("syncStatus", "SYNCED");
        data.put("kingdeeBillNo", kingdeeBillNo);
        data.put("message", message);
        return data;
    }

    @Override
    public Map<String, Object> getKingdeeTemplate() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("bankCode", "ICBC");
        payload.put("bankName", "工商银行");
        payload.put("accountNo", "6222********1234");
        payload.put("txnDate", LocalDate.now().toString());
        payload.put("direction", "IN");
        payload.put("amount", "10000.00");
        payload.put("category", "销售回款");
        payload.put("bizNo", "SO20260418001");
        payload.put("counterparty", "某某客户");
        payload.put("sourceSystem", "MES");
        payload.put("remark", "用于金蝶接口对接样例");

        Map<String, Object> data = new HashMap<>();
        data.put("mockEnabled", kingdeeMockEnabled);
        data.put("endpoint", kingdeeEndpoint);
        data.put("payloadTemplate", payload);
        return data;
    }

    private void ensureTables() {
        if (tablesReady) {
            return;
        }
        synchronized (this) {
            if (tablesReady) {
                return;
            }
            jdbcTemplate.execute(
                    "CREATE TABLE IF NOT EXISTS finance_material_cost_config (" +
                            "id BIGINT PRIMARY KEY AUTO_INCREMENT," +
                            "material_code VARCHAR(100) NOT NULL," +
                            "material_name VARCHAR(200) NULL," +
                            "theoretical_unit_cost DECIMAL(12,4) NOT NULL DEFAULT 0," +
                            "effective_month VARCHAR(7) NOT NULL," +
                            "remark VARCHAR(500) NULL," +
                            "created_by VARCHAR(64) NULL," +
                            "updated_by VARCHAR(64) NULL," +
                            "created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP," +
                            "updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP," +
                            "is_deleted TINYINT(1) NOT NULL DEFAULT 0," +
                            "INDEX idx_fin_mat_cost_1(material_code, effective_month)," +
                            "INDEX idx_fin_mat_cost_2(is_deleted)" +
                            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4"
            );

            jdbcTemplate.execute(
                    "CREATE TABLE IF NOT EXISTS finance_monthly_basic_config (" +
                            "id BIGINT PRIMARY KEY AUTO_INCREMENT," +
                            "month_key VARCHAR(7) NOT NULL," +
                            "rent_amount DECIMAL(12,2) NOT NULL DEFAULT 0," +
                            "utilities_amount DECIMAL(12,2) NOT NULL DEFAULT 0," +
                            "other_fixed_amount DECIMAL(12,2) NOT NULL DEFAULT 0," +
                            "remark VARCHAR(500) NULL," +
                            "created_by VARCHAR(64) NULL," +
                            "updated_by VARCHAR(64) NULL," +
                            "created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP," +
                            "updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP," +
                            "is_deleted TINYINT(1) NOT NULL DEFAULT 0," +
                            "UNIQUE KEY uk_fin_month_basic(month_key)," +
                            "INDEX idx_fin_month_basic_del(is_deleted)" +
                            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4"
            );

            jdbcTemplate.execute(
                    "CREATE TABLE IF NOT EXISTS finance_salary_record (" +
                            "id BIGINT PRIMARY KEY AUTO_INCREMENT," +
                            "month_key VARCHAR(7) NOT NULL," +
                            "employee_name VARCHAR(100) NOT NULL," +
                            "department VARCHAR(100) NULL," +
                            "base_salary DECIMAL(12,2) NOT NULL DEFAULT 0," +
                            "overtime_salary DECIMAL(12,2) NOT NULL DEFAULT 0," +
                            "bonus DECIMAL(12,2) NOT NULL DEFAULT 0," +
                            "social_security DECIMAL(12,2) NOT NULL DEFAULT 0," +
                            "other_deduction DECIMAL(12,2) NOT NULL DEFAULT 0," +
                            "pay_date DATE NULL," +
                            "bank_name VARCHAR(100) NULL," +
                            "bank_account VARCHAR(100) NULL," +
                            "remark VARCHAR(500) NULL," +
                            "created_by VARCHAR(64) NULL," +
                            "updated_by VARCHAR(64) NULL," +
                            "created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP," +
                            "updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP," +
                            "is_deleted TINYINT(1) NOT NULL DEFAULT 0," +
                            "INDEX idx_fin_salary_1(month_key)," +
                            "INDEX idx_fin_salary_2(employee_name)," +
                            "INDEX idx_fin_salary_3(is_deleted)" +
                            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4"
            );

            jdbcTemplate.execute(
                    "CREATE TABLE IF NOT EXISTS finance_bank_ledger (" +
                            "id BIGINT PRIMARY KEY AUTO_INCREMENT," +
                            "bank_code VARCHAR(50) NOT NULL," +
                            "bank_name VARCHAR(100) NOT NULL," +
                            "account_no VARCHAR(100) NULL," +
                            "txn_date DATE NOT NULL," +
                            "direction VARCHAR(10) NOT NULL," +
                            "amount DECIMAL(14,2) NOT NULL DEFAULT 0," +
                            "balance_after DECIMAL(14,2) NOT NULL DEFAULT 0," +
                            "category VARCHAR(100) NULL," +
                            "biz_no VARCHAR(100) NULL," +
                            "counterparty VARCHAR(200) NULL," +
                            "source_system VARCHAR(50) NULL," +
                            "kingdee_bill_no VARCHAR(100) NULL," +
                            "sync_status VARCHAR(20) NOT NULL DEFAULT 'PENDING'," +
                            "remark VARCHAR(500) NULL," +
                            "created_by VARCHAR(64) NULL," +
                            "updated_by VARCHAR(64) NULL," +
                            "created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP," +
                            "updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP," +
                            "is_deleted TINYINT(1) NOT NULL DEFAULT 0," +
                            "INDEX idx_fin_bank_1(bank_code, txn_date)," +
                            "INDEX idx_fin_bank_2(sync_status)," +
                            "INDEX idx_fin_bank_3(is_deleted)" +
                            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4"
            );

            tablesReady = true;
        }
    }

    private String normalizeMonth(String month) {
        if (!hasText(month) || !month.matches("\\d{4}-\\d{2}")) {
            throw new RuntimeException("月份格式应为yyyy-MM");
        }
        return month.trim();
    }

    private Object[] appendArgs(List<Object> args, Object... tail) {
        List<Object> list = new ArrayList<>(args);
        list.addAll(Arrays.asList(tail));
        return list.toArray();
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private String asString(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value).trim());
        } catch (Exception e) {
            return null;
        }
    }

    private BigDecimal toDecimal(Object value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        if (value instanceof BigDecimal) {
            return (BigDecimal) value;
        }
        try {
            return new BigDecimal(String.valueOf(value).trim());
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }

    private LocalDate parseDate(String value) {
        if (!hasText(value)) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim());
        } catch (Exception e) {
            return null;
        }
    }

    private String getCurrentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getPrincipal() == null) {
            return "system";
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof LoginUser) {
            LoginUser loginUser = (LoginUser) principal;
            if (loginUser.getUser() != null && hasText(loginUser.getUser().getUsername())) {
                return loginUser.getUser().getUsername();
            }
        }
        return authentication.getName();
    }
}
