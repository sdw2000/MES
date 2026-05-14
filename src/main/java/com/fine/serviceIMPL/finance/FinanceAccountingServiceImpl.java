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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
    public Map<String, Object> getFormulaTheoreticalCost(String month, Integer pageNum, Integer pageSize, String keyword,
                                                         String sortField, String sortOrder) {
        ensureTables();
        String normalizedMonth = normalizeMonth(month);
        int current = pageNum == null || pageNum < 1 ? 1 : pageNum;
        int size = pageSize == null || pageSize < 1 ? 20 : Math.min(pageSize, 200);

        Map<String, Object> factor = getFormulaCostFactor(normalizedMonth);
        BigDecimal electricUnitCost = toDecimal(factor.get("electricUnitCost"));
        BigDecimal laborUnitCost = toDecimal(factor.get("laborUnitCost"));
        BigDecimal freightUnitCost = toDecimal(factor.get("freightUnitCost"));
        BigDecimal taxRate = toDecimal(factor.get("taxRate"));

        List<Map<String, Object>> formulas = jdbcTemplate.queryForList(
                "SELECT id, material_code AS materialCode, product_name AS productName, formula_no AS formulaNo, version, " +
                        "coating_area AS coatingArea, total_weight AS totalWeight, status, DATE_FORMAT(update_time, '%Y-%m-%d %H:%i:%s') AS updateTime " +
                        "FROM tape_formula WHERE status = 1 ORDER BY update_time DESC, id DESC"
        );

        Map<String, Map<String, Object>> latestFormulaByMaterialCode = new HashMap<>();
        for (Map<String, Object> formula : formulas) {
            String code = asString(formula.get("materialCode"));
            if (!hasText(code)) {
                continue;
            }
            String key = code.trim().toUpperCase(Locale.ROOT);
            if (!latestFormulaByMaterialCode.containsKey(key)) {
                latestFormulaByMaterialCode.put(key, formula);
            }
        }

        Map<Long, List<Map<String, Object>>> itemMap = new HashMap<>();
        List<Map<String, Object>> items = jdbcTemplate.queryForList(
                "SELECT formula_id AS formulaId, material_code AS materialCode, material_name AS materialName, weight, ratio " +
                        "FROM tape_formula_item ORDER BY formula_id ASC, sort_order ASC, id ASC"
        );
        for (Map<String, Object> item : items) {
            Long formulaId = toLong(item.get("formulaId"));
            if (formulaId == null) {
                continue;
            }
            itemMap.computeIfAbsent(formulaId, k -> new ArrayList<>()).add(item);
        }

        Map<String, BigDecimal> priceMap = new HashMap<>();
        Map<String, String> priceUomMap = new HashMap<>();
        Map<String, BigDecimal> latestKgPriceMap = new HashMap<>();
        Map<String, BigDecimal> latestAreaPriceMap = new HashMap<>();

        // 优先：财务库存信息汇总（finance_inventory_price_latest）
        // 目的：价格与单位统一来源于库存价格体系，避免仅靠报价表导致单位缺失
        List<Map<String, Object>> latestInventoryPrices = jdbcTemplate.queryForList(
                "SELECT material_code AS materialCode, avg_unit_price AS avgUnitPrice, uom " +
                        "FROM finance_inventory_price_latest WHERE avg_unit_price IS NOT NULL AND avg_unit_price > 0"
        );
        for (Map<String, Object> row : latestInventoryPrices) {
            String code = asString(row.get("materialCode"));
            if (!hasText(code)) {
                continue;
            }
            BigDecimal unitPrice = toDecimal(row.get("avgUnitPrice"));
            if (unitPrice.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            String normalizedCode = code.trim();
            String upperCode = normalizedCode.toUpperCase(Locale.ROOT);
            priceMap.put(normalizedCode, unitPrice);
            priceMap.put(upperCode, unitPrice);

            String uom = normalizePriceUom(asString(row.get("uom")));
            if (hasText(uom)) {
                priceUomMap.put(upperCode, uom);
                if ("KG".equals(uom)) {
                    latestKgPriceMap.put(upperCode, unitPrice);
                } else if ("AREA".equals(uom)) {
                    latestAreaPriceMap.put(upperCode, unitPrice);
                }
            }
        }

        List<Map<String, Object>> prices = jdbcTemplate.queryForList(
                "SELECT material_code AS materialCode, avg_unit_price AS avgUnitPrice FROM material_base_price"
        );
        for (Map<String, Object> row : prices) {
            String code = asString(row.get("materialCode"));
            if (!hasText(code)) {
                continue;
            }
            BigDecimal unitPrice = toDecimal(row.get("avgUnitPrice"));
            if (unitPrice.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            String normalizedCode = code.trim();
            String upperCode = normalizedCode.toUpperCase(Locale.ROOT);
            // 仅在库存汇总缺失时，才用 material_base_price 补位
            if (!priceMap.containsKey(upperCode) || priceMap.get(upperCode).compareTo(BigDecimal.ZERO) <= 0) {
                priceMap.put(normalizedCode, unitPrice);
                priceMap.put(upperCode, unitPrice);
            }
        }

        // 兜底：若 material_base_price 缺失，则取采购报价表中“相对当前时间最新”的物料报价
        List<Map<String, Object>> quotationPrices = jdbcTemplate.queryForList(
            "SELECT qi.material_code AS materialCode, qi.unit_price AS unitPrice, qi.unit AS unit, qi.specification AS specification " +
                        "FROM quotation_items qi " +
                        "LEFT JOIN quotations q ON q.id = qi.quotation_id " +
                        "WHERE qi.is_deleted = 0 AND IFNULL(q.is_deleted, 0) = 0 " +
                        "AND qi.unit_price IS NOT NULL AND qi.unit_price > 0 " +
                        "AND COALESCE(qi.updated_at, qi.created_at, q.updated_at, q.created_at, CONCAT(q.quotation_date, ' 00:00:00')) <= NOW() " +
                        "ORDER BY qi.material_code ASC, " +
                        "COALESCE(qi.updated_at, qi.created_at, q.updated_at, q.created_at, CONCAT(q.quotation_date, ' 00:00:00')) DESC, qi.id DESC"
        );
        Set<String> latestQuotedCodeSet = new HashSet<>();
        for (Map<String, Object> row : quotationPrices) {
            String code = asString(row.get("materialCode"));
            if (!hasText(code)) {
                continue;
            }
            String normalizedCode = code.trim();
            String normalizedUpper = normalizedCode.toUpperCase(Locale.ROOT);
            BigDecimal unitPrice = normalizeQuotationUnitPriceToKg(
                    toDecimal(row.get("unitPrice")),
                    asString(row.get("unit")),
                    asString(row.get("specification"))
            );
            if (unitPrice.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            String uom = normalizePriceUom(asString(row.get("unit")));
            if ("KG".equals(uom) && !latestKgPriceMap.containsKey(normalizedUpper)) {
                latestKgPriceMap.put(normalizedUpper, unitPrice);
            }
            if ("AREA".equals(uom) && !latestAreaPriceMap.containsKey(normalizedUpper)) {
                latestAreaPriceMap.put(normalizedUpper, unitPrice);
            }
            if (latestQuotedCodeSet.contains(normalizedUpper)) {
                continue;
            }
            latestQuotedCodeSet.add(normalizedUpper);
            BigDecimal existing = priceMap.get(normalizedCode);
            if (existing == null || existing.compareTo(BigDecimal.ZERO) <= 0) {
                priceMap.put(normalizedCode, unitPrice);
                priceMap.put(normalizedUpper, unitPrice);
                if (hasText(uom)) {
                    priceUomMap.put(normalizedUpper, uom);
                }
            }
        }

        // 第二兜底：采购报价明细（purchase_quotation_items）
        List<Map<String, Object>> purchaseQuotationPrices = jdbcTemplate.queryForList(
                "SELECT pqi.material_code AS materialCode, pqi.unit_price AS unitPrice, pqi.unit AS unit, pqi.specifications AS specification " +
                        "FROM purchase_quotation_items pqi " +
                        "WHERE pqi.is_deleted = 0 " +
                        "AND pqi.unit_price IS NOT NULL AND pqi.unit_price > 0 " +
                        "AND COALESCE(pqi.updated_at, pqi.created_at) <= NOW() " +
                        "ORDER BY pqi.material_code ASC, COALESCE(pqi.updated_at, pqi.created_at) DESC, pqi.id DESC"
        );
        Set<String> latestPurchaseQuotedCodeSet = new HashSet<>();
        for (Map<String, Object> row : purchaseQuotationPrices) {
            String code = asString(row.get("materialCode"));
            if (!hasText(code)) {
                continue;
            }
            String normalizedCode = code.trim();
            String normalizedUpper = normalizedCode.toUpperCase(Locale.ROOT);
            BigDecimal unitPrice = normalizeQuotationUnitPriceToKg(
                    toDecimal(row.get("unitPrice")),
                    asString(row.get("unit")),
                    asString(row.get("specification"))
            );
            if (unitPrice.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            String uom = normalizePriceUom(asString(row.get("unit")));
            if ("KG".equals(uom) && !latestKgPriceMap.containsKey(normalizedUpper)) {
                latestKgPriceMap.put(normalizedUpper, unitPrice);
            }
            if ("AREA".equals(uom) && !latestAreaPriceMap.containsKey(normalizedUpper)) {
                latestAreaPriceMap.put(normalizedUpper, unitPrice);
            }
            if (latestPurchaseQuotedCodeSet.contains(normalizedUpper)) {
                continue;
            }
            latestPurchaseQuotedCodeSet.add(normalizedUpper);
            BigDecimal existing = priceMap.get(normalizedCode);
            if (existing == null || existing.compareTo(BigDecimal.ZERO) <= 0) {
                priceMap.put(normalizedCode, unitPrice);
                priceMap.put(normalizedUpper, unitPrice);
                if (hasText(uom)) {
                    priceUomMap.put(normalizedUpper, uom);
                }
            }
        }

        Map<String, Map<String, Object>> tapeSpecMap = new HashMap<>();
        List<Map<String, Object>> specRows = jdbcTemplate.queryForList(
                "SELECT material_code AS materialCode, base_thickness AS baseThickness, base_material AS baseMaterial " +
                        "FROM tape_spec WHERE status = 1"
        );
        for (Map<String, Object> row : specRows) {
            String code = asString(row.get("materialCode"));
            if (!hasText(code)) {
                continue;
            }
            String key = code.trim().toUpperCase(Locale.ROOT);
            if (!tapeSpecMap.containsKey(key)) {
                tapeSpecMap.put(key, row);
            }
        }

        Map<String, BigDecimal> densityMap = new HashMap<>();
        List<Map<String, Object>> densityRows = jdbcTemplate.queryForList(
                "SELECT material_en_name AS materialEnName, material_cn_name AS materialCnName, density " +
                        "FROM material_density_library WHERE deleted = 0 AND is_active = 1"
        );
        for (Map<String, Object> row : densityRows) {
            BigDecimal density = toDecimal(row.get("density"));
            if (density.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            String en = asString(row.get("materialEnName"));
            String cn = asString(row.get("materialCnName"));
            if (hasText(en)) {
                densityMap.put(en.trim().toUpperCase(Locale.ROOT), density);
            }
            if (hasText(cn)) {
                densityMap.put(cn.trim().toUpperCase(Locale.ROOT), density);
            }
        }

        Map<String, BigDecimal> formulaUnitCostCache = new HashMap<>();

        String keywordText = hasText(keyword) ? keyword.trim().toUpperCase(Locale.ROOT) : "";
        List<Map<String, Object>> complete = new ArrayList<>();
        List<Map<String, Object>> issues = new ArrayList<>();

        BigDecimal totalStandardArea = BigDecimal.ZERO;
        BigDecimal totalStandardCost = BigDecimal.ZERO;
        BigDecimal totalMaterialCost = BigDecimal.ZERO;

        int formulaTotal = 0;
        int completeCount = 0;
        int incompleteCount = 0;
        int missingPriceItemCount = 0;

        for (Map<String, Object> formula : formulas) {
            formulaTotal++;
            Long formulaId = toLong(formula.get("id"));
            String materialCode = asString(formula.get("materialCode"));
            String productName = asString(formula.get("productName"));
            String formulaNo = asString(formula.get("formulaNo"));
            BigDecimal coatingArea = toDecimal(formula.get("coatingArea"));
            BigDecimal totalWeight = toDecimal(formula.get("totalWeight"));
            List<Map<String, Object>> formulaItems = formulaId == null ? Collections.emptyList() : itemMap.getOrDefault(formulaId, Collections.emptyList());
            boolean useKgBasis = coatingArea.compareTo(BigDecimal.ZERO) <= 0 && totalWeight.compareTo(BigDecimal.ZERO) > 0;

            BigDecimal baseThickness = BigDecimal.ZERO;
            BigDecimal baseDensity = BigDecimal.ZERO;
            String baseMaterial = "";
                Map<String, Object> specRow = hasText(materialCode)
                    ? tapeSpecMap.get(materialCode.toUpperCase(Locale.ROOT))
                    : null;
            if (specRow != null) {
                baseThickness = toDecimal(specRow.get("baseThickness"));
                baseMaterial = asString(specRow.get("baseMaterial"));
                if (hasText(baseMaterial)) {
                    baseDensity = resolveBaseDensity(baseMaterial, densityMap);
                }
            }

            List<String> problemList = new ArrayList<>();
            if (coatingArea.compareTo(BigDecimal.ZERO) <= 0 && !useKgBasis) {
                problemList.add("标准涂布面积(coatingArea)未配置或<=0");
            }
            if (totalWeight.compareTo(BigDecimal.ZERO) <= 0) {
                problemList.add("总重量(totalWeight)未配置或<=0");
            }
            if (formulaItems.isEmpty()) {
                problemList.add("配料明细为空");
            }

            BigDecimal materialCost = BigDecimal.ZERO;
            BigDecimal itemWeightSum = BigDecimal.ZERO;
            for (Map<String, Object> item : formulaItems) {
                String rawCode = asString(item.get("materialCode"));
                String rawName = asString(item.get("materialName"));
                BigDecimal weight = toDecimal(item.get("weight"));
                itemWeightSum = itemWeightSum.add(weight);
                if (!hasText(rawCode)) {
                    problemList.add("存在空物料编码明细: " + rawName);
                    continue;
                }
                String normalizedRawCode = rawCode.trim();
                boolean filmLikeItem = isFilmLikeItem(normalizedRawCode, rawName, baseMaterial);
                if (weight.compareTo(BigDecimal.ZERO) <= 0) {
                    // 薄膜在面积基准核算时，允许明细weight=0（计费基数由面积或面积换算kg决定）
                    if (!(filmLikeItem && !useKgBasis)) {
                        problemList.add("物料[" + rawCode + "]重量<=0");
                        continue;
                    }
                }
                BigDecimal unitPrice = resolveExactMaterialUnitPrice(
                        normalizedRawCode,
                        priceMap,
                        latestFormulaByMaterialCode,
                        itemMap,
                        formulaUnitCostCache,
                        new HashSet<>()
                );
                if (unitPrice == null || unitPrice.compareTo(BigDecimal.ZERO) <= 0) {
                    problemList.add("物料[" + rawCode + "]缺少基准单价(material_base_price/采购报价quotation_items/purchase_quotation_items/核算表精准匹配)");
                    missingPriceItemCount++;
                    continue;
                }
                BigDecimal chargeQty = weight;
                String codeUpper = normalizedRawCode.toUpperCase(Locale.ROOT);
                String priceUom = priceUomMap.get(codeUpper);
                boolean formulaDerivedIntermediate = latestFormulaByMaterialCode.containsKey(codeUpper);

                // 薄膜优先采用显式报价口径（KG/AREA），避免使用无单位均价
                if (filmLikeItem && !useKgBasis) {
                    BigDecimal kgPrice = latestKgPriceMap.get(codeUpper);
                    BigDecimal areaPrice = latestAreaPriceMap.get(codeUpper);
                    if (kgPrice != null && kgPrice.compareTo(BigDecimal.ZERO) > 0) {
                        unitPrice = kgPrice;
                        priceUom = "KG";
                    } else if (areaPrice != null && areaPrice.compareTo(BigDecimal.ZERO) > 0) {
                        unitPrice = areaPrice;
                        priceUom = "AREA";
                    }
                }

                if (!"KG".equals(priceUom) && !"AREA".equals(priceUom)) {
                    // 中间物料（由配方递归核算得到单价）按化工料口径处理：元/kg
                    if (formulaDerivedIntermediate && !filmLikeItem) {
                        priceUom = "KG";
                    } else if (!filmLikeItem) {
                        // 非薄膜项在历史数据中常缺失单位，按kg口径兜底，避免误拦截
                        priceUom = "KG";
                    } else {
                        problemList.add("物料[" + rawCode + "]单价单位无法判定（仅支持KG/㎡），请在报价中维护单位");
                        continue;
                    }
                }

                if ("KG".equals(priceUom)) {
                    if (!useKgBasis && filmLikeItem) {
                        if (baseDensity.compareTo(BigDecimal.ZERO) <= 0) {
                            baseDensity = resolveFilmDensity(baseMaterial, normalizedRawCode, rawName, densityMap);
                        }
                        BigDecimal thicknessForConvert = baseThickness.compareTo(BigDecimal.ZERO) > 0
                                ? baseThickness
                                : inferFilmThicknessUm(normalizedRawCode, rawName);
                        if (coatingArea.compareTo(BigDecimal.ZERO) <= 0) {
                            problemList.add("物料[" + rawCode + "]为KG报价，但标准面积<=0，无法㎡->kg换算");
                            continue;
                        }
                        if (thicknessForConvert.compareTo(BigDecimal.ZERO) <= 0 || baseDensity.compareTo(BigDecimal.ZERO) <= 0) {
                            problemList.add("物料[" + rawCode + "]为KG报价，但缺少基材厚度/密度，无法㎡->kg换算");
                            continue;
                        }
                        chargeQty = convertAreaToKg(coatingArea, thicknessForConvert, baseDensity);
                    } else {
                        // 非薄膜或KG基准时，按明细kg用量参与
                        chargeQty = weight;
                    }
                } else {
                    // AREA报价
                    if (filmLikeItem) {
                        if (coatingArea.compareTo(BigDecimal.ZERO) <= 0) {
                            problemList.add("物料[" + rawCode + "]为㎡报价，但标准面积<=0，无法参与计算");
                            continue;
                        }
                        chargeQty = coatingArea;
                    } else {
                        problemList.add("物料[" + rawCode + "]为㎡报价但不是薄膜项，无法参与计算");
                        continue;
                    }
                }
                materialCost = materialCost.add(chargeQty.multiply(unitPrice));
            }

            if (totalWeight.compareTo(BigDecimal.ZERO) > 0 && itemWeightSum.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal diffRatio = itemWeightSum.subtract(totalWeight).abs().divide(totalWeight, 6, RoundingMode.HALF_UP);
                if (diffRatio.compareTo(new BigDecimal("0.02")) > 0) {
                    problemList.add("明细重量合计与总重量偏差超过2%（明细=" + itemWeightSum.setScale(3, RoundingMode.HALF_UP).toPlainString()
                            + "kg, 总重=" + totalWeight.setScale(3, RoundingMode.HALF_UP).toPlainString() + "kg）");
                }
            }

            boolean matchKeyword = !hasText(keywordText)
                    || materialCode.toUpperCase(Locale.ROOT).contains(keywordText)
                    || productName.toUpperCase(Locale.ROOT).contains(keywordText)
                    || formulaNo.toUpperCase(Locale.ROOT).contains(keywordText);

            if (!problemList.isEmpty()) {
                incompleteCount++;
                if (matchKeyword) {
                    Map<String, Object> issueRow = new HashMap<>();
                    issueRow.put("formulaId", formulaId);
                    issueRow.put("materialCode", materialCode);
                    issueRow.put("productName", productName);
                    issueRow.put("formulaNo", formulaNo);
                    issueRow.put("problem", String.join("；", problemList));
                    issueRow.put("updateTime", formula.get("updateTime"));
                    issues.add(issueRow);
                }
                continue;
            }

                BigDecimal basisQty = useKgBasis ? totalWeight : coatingArea;
                BigDecimal materialUnitCost = basisQty.compareTo(BigDecimal.ZERO) <= 0
                    ? BigDecimal.ZERO
                    : materialCost.divide(basisQty, 6, RoundingMode.HALF_UP);
                BigDecimal preTaxUnitCost = useKgBasis
                    ? materialUnitCost
                    : materialUnitCost.add(electricUnitCost).add(laborUnitCost).add(freightUnitCost);
                BigDecimal taxUnitCost = useKgBasis ? BigDecimal.ZERO : preTaxUnitCost.multiply(taxRate);
            BigDecimal totalUnitCost = preTaxUnitCost.add(taxUnitCost);
                BigDecimal standardTotalCost = totalUnitCost.multiply(basisQty);

            completeCount++;
            totalStandardArea = totalStandardArea.add(coatingArea);
            totalStandardCost = totalStandardCost.add(standardTotalCost);
            totalMaterialCost = totalMaterialCost.add(materialCost);

            if (matchKeyword) {
                Map<String, Object> row = new HashMap<>();
                row.put("formulaId", formulaId);
                row.put("materialCode", materialCode);
                row.put("productName", productName);
                row.put("formulaNo", formulaNo);
                row.put("version", formula.get("version"));
                row.put("coatingArea", coatingArea.setScale(2, RoundingMode.HALF_UP));
                row.put("totalWeight", totalWeight.setScale(3, RoundingMode.HALF_UP));
                row.put("itemCount", formulaItems.size());
                row.put("materialCost", materialCost.setScale(2, RoundingMode.HALF_UP));
                row.put("materialUnitCost", materialUnitCost.setScale(4, RoundingMode.HALF_UP));
                row.put("electricUnitCost", (useKgBasis ? BigDecimal.ZERO : electricUnitCost).setScale(4, RoundingMode.HALF_UP));
                row.put("laborUnitCost", (useKgBasis ? BigDecimal.ZERO : laborUnitCost).setScale(4, RoundingMode.HALF_UP));
                row.put("freightUnitCost", (useKgBasis ? BigDecimal.ZERO : freightUnitCost).setScale(4, RoundingMode.HALF_UP));
                row.put("taxRate", taxRate.setScale(4, RoundingMode.HALF_UP));
                row.put("taxUnitCost", taxUnitCost.setScale(4, RoundingMode.HALF_UP));
                row.put("totalUnitCost", totalUnitCost.setScale(4, RoundingMode.HALF_UP));
                row.put("standardTotalCost", standardTotalCost.setScale(2, RoundingMode.HALF_UP));
                row.put("costBasis", useKgBasis ? "KG" : "AREA");
                row.put("unitCostUom", useKgBasis ? "元/kg" : "元/㎡");
                row.put("updateTime", formula.get("updateTime"));
                complete.add(row);
            }
        }

        applyFormulaSort(complete, sortField, sortOrder);

        int total = complete.size();
        int from = Math.min((current - 1) * size, total);
        int to = Math.min(from + size, total);

        Map<String, Object> summary = new HashMap<>();
        summary.put("formulaTotal", formulaTotal);
        summary.put("completeFormulaCount", completeCount);
        summary.put("incompleteFormulaCount", incompleteCount);
        summary.put("missingPriceItemCount", missingPriceItemCount);
        summary.put("totalStandardArea", totalStandardArea.setScale(2, RoundingMode.HALF_UP));
        summary.put("totalMaterialCost", totalMaterialCost.setScale(2, RoundingMode.HALF_UP));
        summary.put("totalStandardCost", totalStandardCost.setScale(2, RoundingMode.HALF_UP));
        summary.put("avgTotalUnitCost", totalStandardArea.compareTo(BigDecimal.ZERO) <= 0
                ? BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP)
                : totalStandardCost.divide(totalStandardArea, 4, RoundingMode.HALF_UP));

        Map<String, Object> data = new HashMap<>();
        data.put("month", normalizedMonth);
        data.put("factor", factor);
        data.put("summary", summary);
        data.put("records", complete.subList(from, to));
        data.put("total", total);
        data.put("current", current);
        data.put("size", size);
        data.put("issues", issues);
        data.put("issueTotal", issues.size());
        return data;
    }

    @Override
    public Map<String, Object> getFormulaCostFactor(String month) {
        ensureTables();
        String normalizedMonth = normalizeMonth(month);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT month_key AS month, electric_unit_cost AS electricUnitCost, labor_unit_cost AS laborUnitCost, " +
                        "freight_unit_cost AS freightUnitCost, tax_rate AS taxRate, remark " +
                        "FROM finance_formula_cost_factor WHERE is_deleted = 0 AND month_key = ? LIMIT 1",
                normalizedMonth
        );
        Map<String, Object> factor = new HashMap<>();
        factor.put("month", normalizedMonth);
        factor.put("electricUnitCost", BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP));
        factor.put("laborUnitCost", BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP));
        factor.put("freightUnitCost", BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP));
        factor.put("taxRate", BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP));
        factor.put("remark", "");
        if (!rows.isEmpty()) {
            Map<String, Object> row = rows.get(0);
            factor.put("electricUnitCost", toDecimal(row.get("electricUnitCost")).setScale(4, RoundingMode.HALF_UP));
            factor.put("laborUnitCost", toDecimal(row.get("laborUnitCost")).setScale(4, RoundingMode.HALF_UP));
            factor.put("freightUnitCost", toDecimal(row.get("freightUnitCost")).setScale(4, RoundingMode.HALF_UP));
            factor.put("taxRate", toDecimal(row.get("taxRate")).setScale(4, RoundingMode.HALF_UP));
            factor.put("remark", row.get("remark"));
        }
        return factor;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> saveFormulaCostFactor(Map<String, Object> payload) {
        ensureTables();
        String month = normalizeMonth(asString(payload.get("month")));
        BigDecimal electricUnitCost = toDecimal(payload.get("electricUnitCost"));
        BigDecimal laborUnitCost = toDecimal(payload.get("laborUnitCost"));
        BigDecimal freightUnitCost = toDecimal(payload.get("freightUnitCost"));
        BigDecimal taxRate = toDecimal(payload.get("taxRate"));
        String remark = asString(payload.get("remark"));

        Integer exists = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM finance_formula_cost_factor WHERE is_deleted = 0 AND month_key = ?",
                Integer.class,
                month
        );
        if (exists != null && exists > 0) {
            jdbcTemplate.update(
                    "UPDATE finance_formula_cost_factor SET electric_unit_cost = ?, labor_unit_cost = ?, freight_unit_cost = ?, tax_rate = ?, remark = ?, updated_by = ?, updated_at = NOW() " +
                            "WHERE month_key = ? AND is_deleted = 0",
                    electricUnitCost, laborUnitCost, freightUnitCost, taxRate, remark, getCurrentUsername(), month
            );
        } else {
            jdbcTemplate.update(
                    "INSERT INTO finance_formula_cost_factor(month_key, electric_unit_cost, labor_unit_cost, freight_unit_cost, tax_rate, remark, created_by, updated_by) " +
                            "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                    month, electricUnitCost, laborUnitCost, freightUnitCost, taxRate, remark, getCurrentUsername(), getCurrentUsername()
            );
        }
        return getFormulaCostFactor(month);
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
                            "CREATE TABLE IF NOT EXISTS finance_formula_cost_factor (" +
                                "id BIGINT PRIMARY KEY AUTO_INCREMENT," +
                                "month_key VARCHAR(7) NOT NULL," +
                                "electric_unit_cost DECIMAL(12,4) NOT NULL DEFAULT 0," +
                                "labor_unit_cost DECIMAL(12,4) NOT NULL DEFAULT 0," +
                                "freight_unit_cost DECIMAL(12,4) NOT NULL DEFAULT 0," +
                                "tax_rate DECIMAL(8,4) NOT NULL DEFAULT 0," +
                                "remark VARCHAR(500) NULL," +
                                "created_by VARCHAR(64) NULL," +
                                "updated_by VARCHAR(64) NULL," +
                                "created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP," +
                                "updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP," +
                                "is_deleted TINYINT(1) NOT NULL DEFAULT 0," +
                                "UNIQUE KEY uk_fin_formula_factor(month_key)," +
                                "INDEX idx_fin_formula_factor_del(is_deleted)" +
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

    private BigDecimal normalizeQuotationUnitPriceToKg(BigDecimal unitPrice, String unit, String specification) {
        if (unitPrice == null || unitPrice.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        String u = unit == null ? "" : unit.trim();
        if (isKgUnit(u)) {
            return unitPrice;
        }
        if (isPackUnit(u) || !hasText(u)) {
            BigDecimal kgPerPack = extractKgPerPack(specification);
            if (kgPerPack.compareTo(BigDecimal.ZERO) > 0) {
                return unitPrice.divide(kgPerPack, 6, RoundingMode.HALF_UP);
            }
        }
        return unitPrice;
    }

    private boolean isKgUnit(String unit) {
        if (!hasText(unit)) {
            return false;
        }
        String u = unit.trim();
        String upper = u.toUpperCase(Locale.ROOT);
        return "KG".equals(upper) || "KGS".equals(upper)
                || "公斤".equals(u) || "千克".equals(u);
    }

    private boolean isPackUnit(String unit) {
        if (!hasText(unit)) {
            return false;
        }
        String u = unit.trim();
        String lower = u.toLowerCase(Locale.ROOT);
        return "桶".equals(u) || "包".equals(u)
                || lower.contains("drum") || lower.contains("barrel") || lower.contains("bucket")
                || lower.contains("bag") || lower.contains("sack");
    }

    private BigDecimal extractKgPerPack(String specification) {
        if (!hasText(specification)) {
            return BigDecimal.ZERO;
        }
        String spec = specification.trim();
        Matcher strict = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*(?:kg|公斤|千克)\\s*/", Pattern.CASE_INSENSITIVE).matcher(spec);
        if (strict.find()) {
            return toDecimal(strict.group(1));
        }
        Matcher loose = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*(?:kg|公斤|千克)", Pattern.CASE_INSENSITIVE).matcher(spec);
        if (loose.find()) {
            return toDecimal(loose.group(1));
        }
        return BigDecimal.ZERO;
    }

    private String normalizePriceUom(String unit) {
        if (!hasText(unit)) {
            return "";
        }
        String u = unit.trim();
        String upper = u.toUpperCase(Locale.ROOT);
        if ("KG".equals(upper) || "KGS".equals(upper) || "公斤".equals(u) || "千克".equals(u)) {
            return "KG";
        }
        if ("㎡".equals(u) || "M²".equals(upper) || "M2".equals(upper) || "平米".equals(u) || "平方米".equals(u)) {
            return "AREA";
        }
        return "";
    }

    private boolean isFilmLikeItem(String materialCode, String materialName, String baseMaterial) {
        String code = materialCode == null ? "" : materialCode.trim().toUpperCase(Locale.ROOT);
        String name = materialName == null ? "" : materialName.trim().toUpperCase(Locale.ROOT);
        String base = baseMaterial == null ? "" : baseMaterial.trim().toUpperCase(Locale.ROOT);

        if (hasText(base) && (code.contains(base) || name.contains(base))) {
            return true;
        }

        String[] filmKeywords = new String[] {
                "PET", "PI", "BOPP", "OPP", "CPP", "PVC", "TPU", "OPS", "PEFOAM", "TISSUE", "FIBERGLASS", "薄膜", "膜"
        };
        for (String keyword : filmKeywords) {
            if (code.contains(keyword) || name.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private BigDecimal resolveBaseDensity(String baseMaterial, Map<String, BigDecimal> densityMap) {
        if (!hasText(baseMaterial) || densityMap == null || densityMap.isEmpty()) {
            return BigDecimal.ZERO;
        }
        String base = baseMaterial.trim().toUpperCase(Locale.ROOT);
        BigDecimal direct = densityMap.get(base);
        if (direct != null && direct.compareTo(BigDecimal.ZERO) > 0) {
            return direct;
        }

        if ("PP".equals(base)) {
            String[] aliases = new String[]{"BOPP", "OPP", "CPP"};
            for (String alias : aliases) {
                BigDecimal d = densityMap.get(alias);
                if (d != null && d.compareTo(BigDecimal.ZERO) > 0) {
                    return d;
                }
            }
        }

        if ("BOPP".equals(base) || "OPP".equals(base) || "CPP".equals(base)) {
            BigDecimal pp = densityMap.get("PP");
            if (pp != null && pp.compareTo(BigDecimal.ZERO) > 0) {
                return pp;
            }
        }

        if (base.contains("PET")) {
            BigDecimal pet = densityMap.get("PET");
            if (pet != null && pet.compareTo(BigDecimal.ZERO) > 0) {
                return pet;
            }
        }

        if (base.contains("OPS") || "PS".equals(base) || base.contains("聚苯乙烯")) {
            BigDecimal ps = densityMap.get("PS");
            if (ps != null && ps.compareTo(BigDecimal.ZERO) > 0) {
                return ps;
            }
            BigDecimal ops = densityMap.get("OPS");
            if (ops != null && ops.compareTo(BigDecimal.ZERO) > 0) {
                return ops;
            }
        }
        return BigDecimal.ZERO;
    }

    private BigDecimal resolveFilmDensity(String baseMaterial, String materialCode, String materialName, Map<String, BigDecimal> densityMap) {
        BigDecimal byBase = resolveBaseDensity(baseMaterial, densityMap);
        if (byBase.compareTo(BigDecimal.ZERO) > 0) {
            return byBase;
        }
        String text = (asString(materialCode) + " " + asString(materialName)).toUpperCase(Locale.ROOT);
        if (text.contains("PET")) {
            BigDecimal pet = densityMap.get("PET");
            if (pet != null && pet.compareTo(BigDecimal.ZERO) > 0) return pet;
        }
        if (text.contains("OPS") || text.contains("PS") || text.contains("聚苯乙烯")) {
            BigDecimal ps = densityMap.get("PS");
            if (ps != null && ps.compareTo(BigDecimal.ZERO) > 0) return ps;
            BigDecimal ops = densityMap.get("OPS");
            if (ops != null && ops.compareTo(BigDecimal.ZERO) > 0) return ops;
        }
        if (text.contains("BOPP") || text.contains("OPP") || text.contains("CPP") || text.contains("PP")) {
            BigDecimal pp = densityMap.get("PP");
            if (pp != null && pp.compareTo(BigDecimal.ZERO) > 0) return pp;
        }
        return BigDecimal.ZERO;
    }

    private BigDecimal inferFilmThicknessUm(String materialCode, String materialName) {
        String code = asString(materialCode).toUpperCase(Locale.ROOT);
        String name = asString(materialName).toUpperCase(Locale.ROOT);

        Matcher codeTD = Pattern.compile("(?:^|[-_])(?:T|D)(\\d+(?:\\.\\d+)?)$").matcher(code);
        if (codeTD.find()) {
            BigDecimal v = toDecimal(codeTD.group(1));
            if (v.compareTo(BigDecimal.ZERO) > 0) return v;
        }

        Matcher nameUm = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*(?:UM|ΜM|μM|µM|μm|µm)", Pattern.CASE_INSENSITIVE).matcher(name);
        if (nameUm.find()) {
            BigDecimal v = toDecimal(nameUm.group(1));
            if (v.compareTo(BigDecimal.ZERO) > 0) return v;
        }
        return BigDecimal.ZERO;
    }

    private BigDecimal convertAreaToKg(BigDecimal area, BigDecimal thicknessUm, BigDecimal density) {
        if (area == null || thicknessUm == null || density == null) {
            return BigDecimal.ZERO;
        }
        if (area.compareTo(BigDecimal.ZERO) <= 0
                || thicknessUm.compareTo(BigDecimal.ZERO) <= 0
                || density.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        // kg = area(㎡) * thickness(μm) * density(g/cm³) / 1000
        return area.multiply(thicknessUm)
                .multiply(density)
                .divide(new BigDecimal("1000"), 6, RoundingMode.HALF_UP);
    }

    private BigDecimal resolveExactMaterialUnitPrice(String materialCode,
                                                     Map<String, BigDecimal> directPriceMap,
                                                     Map<String, Map<String, Object>> latestFormulaByMaterialCode,
                                                     Map<Long, List<Map<String, Object>>> itemMap,
                                                     Map<String, BigDecimal> formulaUnitCostCache,
                                                     Set<String> visiting) {
        if (!hasText(materialCode)) {
            return BigDecimal.ZERO;
        }
        String normalizedCode = materialCode.trim();
        String upperCode = normalizedCode.toUpperCase(Locale.ROOT);

        BigDecimal direct = directPriceMap.get(normalizedCode);
        if (direct == null || direct.compareTo(BigDecimal.ZERO) <= 0) {
            direct = directPriceMap.get(upperCode);
        }
        if (direct != null && direct.compareTo(BigDecimal.ZERO) > 0) {
            return direct;
        }

        BigDecimal cached = formulaUnitCostCache.get(upperCode);
        if (cached != null && cached.compareTo(BigDecimal.ZERO) > 0) {
            return cached;
        }
        if (visiting.contains(upperCode)) {
            return BigDecimal.ZERO;
        }

        Map<String, Object> formula = latestFormulaByMaterialCode.get(upperCode);
        if (formula == null) {
            return BigDecimal.ZERO;
        }
        Long formulaId = toLong(formula.get("id"));
        if (formulaId == null) {
            return BigDecimal.ZERO;
        }

        BigDecimal coatingArea = toDecimal(formula.get("coatingArea"));
        BigDecimal totalWeight = toDecimal(formula.get("totalWeight"));
        boolean useKgBasis = coatingArea.compareTo(BigDecimal.ZERO) <= 0 && totalWeight.compareTo(BigDecimal.ZERO) > 0;
        BigDecimal basisQty = useKgBasis ? totalWeight : coatingArea;
        if (basisQty.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }

        List<Map<String, Object>> formulaItems = itemMap.getOrDefault(formulaId, Collections.emptyList());
        if (formulaItems.isEmpty()) {
            return BigDecimal.ZERO;
        }

        visiting.add(upperCode);
        BigDecimal materialCost = BigDecimal.ZERO;
        for (Map<String, Object> item : formulaItems) {
            String rawCode = asString(item.get("materialCode"));
            BigDecimal weight = toDecimal(item.get("weight"));
            if (!hasText(rawCode) || weight.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            BigDecimal unitPrice = resolveExactMaterialUnitPrice(rawCode,
                    directPriceMap,
                    latestFormulaByMaterialCode,
                    itemMap,
                    formulaUnitCostCache,
                    visiting);
            if (unitPrice == null || unitPrice.compareTo(BigDecimal.ZERO) <= 0) {
                visiting.remove(upperCode);
                return BigDecimal.ZERO;
            }
            materialCost = materialCost.add(weight.multiply(unitPrice));
        }
        visiting.remove(upperCode);

        if (materialCost.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal unitCost = materialCost.divide(basisQty, 6, RoundingMode.HALF_UP);
        if (unitCost.compareTo(BigDecimal.ZERO) > 0) {
            formulaUnitCostCache.put(upperCode, unitCost);
        }
        return unitCost;
    }

    private void applyFormulaSort(List<Map<String, Object>> records, String sortField, String sortOrder) {
        if (records == null || records.isEmpty() || !hasText(sortField) || !hasText(sortOrder)) {
            return;
        }

        String field = sortField.trim();
        Set<String> allowed = new HashSet<>(Arrays.asList(
                "materialCode", "productName", "formulaNo", "coatingArea",
                "materialUnitCost", "electricUnitCost", "laborUnitCost", "freightUnitCost",
                "taxRate", "totalUnitCost", "standardTotalCost", "materialCost", "totalWeight", "updateTime"
        ));
        if (!allowed.contains(field)) {
            return;
        }

        Comparator<Map<String, Object>> comparator;
        if ("materialCode".equals(field) || "productName".equals(field) || "formulaNo".equals(field) || "updateTime".equals(field)) {
            comparator = Comparator.comparing(
                    m -> asString(m.get(field)).toLowerCase(Locale.ROOT),
                    Comparator.nullsLast(String::compareTo)
            );
        } else {
            comparator = (a, b) -> toDecimal(a.get(field)).compareTo(toDecimal(b.get(field)));
        }

        if ("descending".equalsIgnoreCase(sortOrder) || "desc".equalsIgnoreCase(sortOrder)) {
            comparator = comparator.reversed();
        }
        records.sort(comparator);
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
