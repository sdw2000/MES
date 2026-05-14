package com.fine.serviceIMPL.finance;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class FinanceInventoryPriceService {

    private static final ZoneId SHANGHAI_ZONE = ZoneId.of("Asia/Shanghai");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Value("${finance.inventory-price.recalc-cron:0 0 21 * * ?}")
    private String recalcCron;

    private volatile boolean tablesReady = false;

    @Scheduled(cron = "${finance.inventory-price.recalc-cron:0 0 21 * * ?}", zone = "Asia/Shanghai")
    public void scheduledRecalcToday() {
        recalcByDate(LocalDate.now(SHANGHAI_ZONE));
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> recalcByDate(LocalDate bizDate) {
        ensureTables();
        normalizeFilmAreaUnitPriceByLatestQuote();
        LocalDate date = bizDate == null ? LocalDate.now(SHANGHAI_ZONE) : bizDate;
        String day = date.format(DateTimeFormatter.ISO_DATE);

        List<Map<String, Object>> changedRows = jdbcTemplate.queryForList(
                "SELECT m.material_code AS materialCode, MAX(m.material_name) AS materialName, MAX(m.uom) AS uom, " +
                        "SUM(m.in_qty) AS inQty, SUM(m.in_amount) AS inAmount, SUM(m.out_qty) AS outQty " +
                        "FROM (" +
            "  SELECT fsd.material_code, IFNULL(fs.material_name, fsd.material_code) AS material_name, " +
                "         CASE WHEN UPPER(fsd.material_code) LIKE 'PEG-%' THEN '支' ELSE '㎡' END AS uom, " +
                "         SUM(CASE WHEN UPPER(fsd.material_code) LIKE 'PEG-%' THEN IFNULL(fsd.pack_count, 1) ELSE IFNULL(fsd.area, 0) END) AS in_qty, " +
                "         SUM(CASE WHEN UPPER(fsd.material_code) LIKE 'PEG-%' THEN IFNULL(fsd.pack_count, 1) * IFNULL(fsd.unit_price, 0) ELSE IFNULL(fsd.area, 0) * IFNULL(fsd.unit_price, 0) END) AS in_amount, " +
                "         0 AS out_qty " +
                "  FROM film_stock_detail fsd " +
                "  LEFT JOIN film_stock fs ON fs.id = fsd.stock_id " +
                "  WHERE fsd.is_deleted = 0 AND fsd.storage_date = ? GROUP BY fsd.material_code " +
                        "  UNION ALL " +
                "  SELECT csd.material_code, IFNULL(cs.material_name, csd.material_code) AS material_name, 'kg' AS uom, " +
                        "         SUM(IFNULL(weight, 0)) AS in_qty, SUM(IFNULL(weight, 0) * IFNULL(unit_price, 0)) AS in_amount, 0 AS out_qty " +
                "  FROM chemical_stock_detail csd " +
                "  LEFT JOIN chemical_stock cs ON cs.id = csd.stock_id " +
                "  WHERE csd.is_deleted = 0 AND csd.storage_date = ? GROUP BY csd.material_code " +
                        "  UNION ALL " +
                "  SELECT fso.material_code, IFNULL(fs.material_name, fso.material_code) AS material_name, " +
                    "         CASE WHEN UPPER(fso.material_code) LIKE 'PEG-%' THEN '支' ELSE '㎡' END AS uom, " +
                    "         0 AS in_qty, 0 AS in_amount, " +
                    "         SUM(CASE WHEN UPPER(fso.material_code) LIKE 'PEG-%' THEN IFNULL(fso.out_area, 0) / NULLIF(IFNULL(fsd.area, 0), 0) * IFNULL(fsd.pack_count, 1) ELSE IFNULL(fso.out_area, 0) END) AS out_qty " +
                "  FROM film_stock_out fso " +
                "  LEFT JOIN film_stock fs ON fs.id = fso.stock_id " +
                "  LEFT JOIN film_stock_detail fsd ON fsd.id = fso.detail_id " +
                "  WHERE DATE(fso.out_date) = ? GROUP BY fso.material_code " +
                        "  UNION ALL " +
                "  SELECT cso.material_code, IFNULL(cs.material_name, cso.material_code) AS material_name, 'kg' AS uom, " +
                        "         0 AS in_qty, 0 AS in_amount, SUM(IFNULL(out_weight, 0)) AS out_qty " +
                "  FROM chemical_stock_out cso " +
                "  LEFT JOIN chemical_stock cs ON cs.id = cso.stock_id " +
                "  WHERE DATE(cso.out_date) = ? GROUP BY cso.material_code " +
                        ") m GROUP BY m.material_code HAVING SUM(m.in_qty) > 0 OR SUM(m.out_qty) > 0",
                day, day, day, day
        );

        int success = 0;
        List<String> failed = new ArrayList<>();

        for (Map<String, Object> row : changedRows) {
            String materialCode = asString(row.get("materialCode"));
            if (!hasText(materialCode)) {
                continue;
            }
            String materialName = asString(row.get("materialName"));
            String uom = normalizeUom(asString(row.get("uom")));
            BigDecimal inQty = toDecimal(row.get("inQty"));
            BigDecimal inAmount = toDecimal(row.get("inAmount"));
            BigDecimal outQty = toDecimal(row.get("outQty"));

            boolean ok = updateOneWithOptimisticLock(day, materialCode, materialName, uom, inQty, inAmount, outQty);
            if (ok) {
                success++;
            } else {
                failed.add(materialCode);
            }
        }

        if (date.equals(YearMonth.from(date).atEndOfMonth())) {
            snapshotMonthEnd(date);
        }

        // 报价变化可能不伴随当日出入库，仍需刷新“最新库存金额”以反映最新单价
        refreshLatestFromCurrentStock(day);

        Map<String, Object> data = new HashMap<>();
        data.put("bizDate", day);
        data.put("processed", changedRows.size());
        data.put("success", success);
        data.put("failed", failed);
        data.put("cron", recalcCron);
        return data;
    }

    private void refreshLatestFromCurrentStock(String bizDate) {
        jdbcTemplate.execute("DROP TEMPORARY TABLE IF EXISTS tmp_fin_inventory_snapshot_latest_refresh");
        jdbcTemplate.execute(
            "CREATE TEMPORARY TABLE tmp_fin_inventory_snapshot_latest_refresh AS " +
                "SELECT m.material_code, MAX(m.material_name) AS material_name, MAX(m.uom) AS uom, " +
                "ROUND(SUM(m.qty), 6) AS stock_qty, ROUND(SUM(m.amount), 6) AS stock_amount, " +
                "CASE WHEN SUM(m.qty) > 0 THEN ROUND(SUM(m.amount)/SUM(m.qty), 6) ELSE 0 END AS avg_unit_price " +
                "FROM (" +
                "  SELECT fsd.material_code, COALESCE(MAX(fs.material_name), fsd.material_code) AS material_name, " +
                "         CASE WHEN UPPER(fsd.material_code) LIKE 'PEG-%' THEN '支' ELSE '㎡' END AS uom, " +
                "         SUM(CASE WHEN UPPER(fsd.material_code) LIKE 'PEG-%' THEN IFNULL(fsd.pack_count,1) ELSE IFNULL(fsd.area,0) END) AS qty, " +
                "         SUM(CASE WHEN UPPER(fsd.material_code) LIKE 'PEG-%' THEN IFNULL(fsd.pack_count,1) * IFNULL(fsd.unit_price,0) ELSE IFNULL(fsd.area,0) * IFNULL(fsd.unit_price,0) END) AS amount " +
                "  FROM film_stock_detail fsd LEFT JOIN film_stock fs ON fs.id = fsd.stock_id " +
                "  WHERE fsd.is_deleted=0 AND fsd.status IN ('available','locked') GROUP BY fsd.material_code " +
                "  UNION ALL " +
                "  SELECT csd.material_code, COALESCE(MAX(cs.material_name), csd.material_code) AS material_name, 'kg' AS uom, " +
                "         SUM(IFNULL(csd.remaining_weight, IFNULL(csd.weight,0))) AS qty, " +
                "         SUM(IFNULL(csd.remaining_weight, IFNULL(csd.weight,0)) * IFNULL(csd.unit_price,0)) AS amount " +
                "  FROM chemical_stock_detail csd LEFT JOIN chemical_stock cs ON cs.id = csd.stock_id " +
                "  WHERE csd.is_deleted=0 AND csd.status IN ('available','locked') GROUP BY csd.material_code " +
                ") m GROUP BY m.material_code"
        );

        jdbcTemplate.update("DELETE FROM finance_inventory_price_latest");
        jdbcTemplate.update(
            "INSERT INTO finance_inventory_price_latest(material_code, material_name, uom, stock_qty, avg_unit_price, stock_amount, version, last_recalc_time, last_inbound_date) " +
                "SELECT material_code, material_name, uom, stock_qty, avg_unit_price, stock_amount, 1, NOW(), ? FROM tmp_fin_inventory_snapshot_latest_refresh",
            bizDate
        );
    }

        @Transactional(rollbackFor = Exception.class)
        public Map<String, Object> initFromCurrentStock(LocalDate bizDate) {
        ensureTables();
            normalizeFilmAreaUnitPriceByLatestQuote();
        LocalDate date = bizDate == null ? LocalDate.now(SHANGHAI_ZONE) : bizDate;
        String day = date.format(DateTimeFormatter.ISO_DATE);

        jdbcTemplate.execute("DROP TEMPORARY TABLE IF EXISTS tmp_fin_inventory_snapshot");
        jdbcTemplate.execute(
            "CREATE TEMPORARY TABLE tmp_fin_inventory_snapshot AS " +
                "SELECT m.material_code, MAX(m.material_name) AS material_name, MAX(m.uom) AS uom, " +
                "ROUND(SUM(m.qty), 6) AS stock_qty, ROUND(SUM(m.amount), 6) AS stock_amount, " +
                "CASE WHEN SUM(m.qty) > 0 THEN ROUND(SUM(m.amount)/SUM(m.qty), 6) ELSE 0 END AS avg_unit_price " +
                "FROM (" +
                "  SELECT fsd.material_code, COALESCE(MAX(fs.material_name), fsd.material_code) AS material_name, " +
                "         CASE WHEN UPPER(fsd.material_code) LIKE 'PEG-%' THEN '支' ELSE '㎡' END AS uom, " +
                "         SUM(CASE WHEN UPPER(fsd.material_code) LIKE 'PEG-%' THEN IFNULL(fsd.pack_count,1) ELSE IFNULL(fsd.area,0) END) AS qty, " +
                "         SUM(CASE WHEN UPPER(fsd.material_code) LIKE 'PEG-%' THEN IFNULL(fsd.pack_count,1) * IFNULL(fsd.unit_price,0) ELSE IFNULL(fsd.area,0) * IFNULL(fsd.unit_price,0) END) AS amount " +
                "  FROM film_stock_detail fsd LEFT JOIN film_stock fs ON fs.id = fsd.stock_id " +
                "  WHERE fsd.is_deleted=0 AND fsd.status IN ('available','locked') GROUP BY fsd.material_code " +
                "  UNION ALL " +
                "  SELECT csd.material_code, COALESCE(MAX(cs.material_name), csd.material_code) AS material_name, 'kg' AS uom, " +
                "         SUM(IFNULL(csd.remaining_weight, IFNULL(csd.weight,0))) AS qty, " +
                "         SUM(IFNULL(csd.remaining_weight, IFNULL(csd.weight,0)) * IFNULL(csd.unit_price,0)) AS amount " +
                "  FROM chemical_stock_detail csd LEFT JOIN chemical_stock cs ON cs.id = csd.stock_id " +
                "  WHERE csd.is_deleted=0 AND csd.status IN ('available','locked') GROUP BY csd.material_code " +
                ") m GROUP BY m.material_code"
        );

        jdbcTemplate.update("DELETE FROM finance_inventory_price_latest");
        jdbcTemplate.update(
            "INSERT INTO finance_inventory_price_latest(material_code, material_name, uom, stock_qty, avg_unit_price, stock_amount, version, last_recalc_time, last_inbound_date) " +
                "SELECT material_code, material_name, uom, stock_qty, avg_unit_price, stock_amount, 1, NOW(), ? FROM tmp_fin_inventory_snapshot",
            day
        );

        jdbcTemplate.update("DELETE FROM finance_inventory_price_change WHERE biz_date = ?", day);
        jdbcTemplate.update(
            "INSERT INTO finance_inventory_price_change(biz_date, material_code, material_name, uom, old_stock_qty, old_stock_amount, old_unit_price, in_qty, in_amount, out_qty, new_stock_qty, new_stock_amount, new_unit_price, recalc_time) " +
                "SELECT ?, material_code, material_name, uom, 0, 0, 0, stock_qty, stock_amount, 0, stock_qty, stock_amount, avg_unit_price, NOW() " +
                "FROM tmp_fin_inventory_snapshot",
            day
        );

        jdbcTemplate.update(
            "INSERT INTO material_base_price(material_code, material_name, recent_3m_total_quantity, recent_3m_total_amount, avg_unit_price, stats_updated_at) " +
                "SELECT material_code, material_name, 0, 0, avg_unit_price, NOW() FROM tmp_fin_inventory_snapshot " +
                "ON DUPLICATE KEY UPDATE material_name = VALUES(material_name), avg_unit_price = VALUES(avg_unit_price), stats_updated_at = NOW()"
        );

        Long count = jdbcTemplate.queryForObject("SELECT COUNT(1) FROM tmp_fin_inventory_snapshot", Long.class);
        Long priced = jdbcTemplate.queryForObject("SELECT COUNT(1) FROM tmp_fin_inventory_snapshot WHERE avg_unit_price > 0", Long.class);
        Long zero = jdbcTemplate.queryForObject("SELECT COUNT(1) FROM tmp_fin_inventory_snapshot WHERE avg_unit_price <= 0", Long.class);

        Map<String, Object> data = new HashMap<>();
        data.put("bizDate", day);
        data.put("snapshotRows", count == null ? 0 : count);
        data.put("pricedRows", priced == null ? 0 : priced);
        data.put("zeroPriceRows", zero == null ? 0 : zero);
        return data;
        }

    public Map<String, Object> getLatestPage(String keyword, Integer pageNum, Integer pageSize, String sortField, String sortOrder) {
        ensureTables();
        int current = pageNum == null || pageNum < 1 ? 1 : pageNum;
        int size = pageSize == null || pageSize < 1 ? 20 : Math.min(pageSize, 200);
        int offset = (current - 1) * size;

        StringBuilder where = new StringBuilder(" WHERE 1=1 ");
        List<Object> args = new ArrayList<>();
        if (hasText(keyword)) {
            where.append(" AND (material_code LIKE ? OR material_name LIKE ?) ");
            String k = "%" + keyword.trim() + "%";
            args.add(k);
            args.add(k);
        }

        String orderBy = buildLatestOrderBy(sortField, sortOrder);

        Long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM finance_inventory_price_latest " + where,
                Long.class,
                args.toArray()
        );
        BigDecimal totalStockAmount = jdbcTemplate.queryForObject(
            "SELECT IFNULL(SUM(stock_amount), 0) FROM finance_inventory_price_latest " + where,
            BigDecimal.class,
            args.toArray()
        );

        List<Object> listArgs = new ArrayList<>(args);
        listArgs.add(offset);
        listArgs.add(size);
        List<Map<String, Object>> records = jdbcTemplate.queryForList(
                "SELECT id, material_code AS materialCode, material_name AS materialName, uom, stock_qty AS stockQty, " +
                        "avg_unit_price AS avgUnitPrice, stock_amount AS stockAmount, version, last_recalc_time AS lastRecalcTime " +
                        "FROM finance_inventory_price_latest " + where +
                " ORDER BY " + orderBy + " LIMIT ?, ?",
                listArgs.toArray()
        );

        Map<String, Object> data = new HashMap<>();
        data.put("records", records);
        data.put("total", total == null ? 0 : total);
        data.put("totalStockAmount", totalStockAmount == null ? BigDecimal.ZERO : totalStockAmount);
        data.put("current", current);
        data.put("size", size);
        return data;
    }

    public Map<String, Object> getChangePage(String bizDate, String keyword, Integer pageNum, Integer pageSize, String sortField, String sortOrder) {
        ensureTables();
        int current = pageNum == null || pageNum < 1 ? 1 : pageNum;
        int size = pageSize == null || pageSize < 1 ? 20 : Math.min(pageSize, 200);
        int offset = (current - 1) * size;

        StringBuilder where = new StringBuilder(" WHERE 1=1 ");
        List<Object> args = new ArrayList<>();
        if (hasText(bizDate)) {
            where.append(" AND biz_date = ? ");
            args.add(bizDate.trim());
        }
        if (hasText(keyword)) {
            where.append(" AND (material_code LIKE ? OR material_name LIKE ?) ");
            String k = "%" + keyword.trim() + "%";
            args.add(k);
            args.add(k);
        }

        String orderBy = buildChangeOrderBy(sortField, sortOrder);

        Long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM finance_inventory_price_change " + where,
                Long.class,
                args.toArray()
        );

        List<Object> listArgs = new ArrayList<>(args);
        listArgs.add(offset);
        listArgs.add(size);
        List<Map<String, Object>> records = jdbcTemplate.queryForList(
                "SELECT id, biz_date AS bizDate, material_code AS materialCode, material_name AS materialName, uom, " +
                        "old_stock_qty AS oldStockQty, old_stock_amount AS oldStockAmount, old_unit_price AS oldUnitPrice, " +
                        "in_qty AS inQty, in_amount AS inAmount, out_qty AS outQty, new_stock_qty AS newStockQty, " +
                        "new_stock_amount AS newStockAmount, new_unit_price AS newUnitPrice, recalc_time AS recalcTime " +
                        "FROM finance_inventory_price_change " + where +
                " ORDER BY " + orderBy + " LIMIT ?, ?",
                listArgs.toArray()
        );

        Map<String, Object> data = new HashMap<>();
        data.put("records", records);
        data.put("total", total == null ? 0 : total);
        data.put("current", current);
        data.put("size", size);
        return data;
    }

    public List<Map<String, Object>> exportLatest(String keyword, String sortField, String sortOrder) {
        ensureTables();
        StringBuilder where = new StringBuilder(" WHERE 1=1 ");
        List<Object> args = new ArrayList<>();
        if (hasText(keyword)) {
            where.append(" AND (material_code LIKE ? OR material_name LIKE ?) ");
            String k = "%" + keyword.trim() + "%";
            args.add(k);
            args.add(k);
        }
        String orderBy = buildLatestOrderBy(sortField, sortOrder);
        return jdbcTemplate.queryForList(
                "SELECT material_code AS materialCode, material_name AS materialName, uom, stock_qty AS stockQty, " +
                        "avg_unit_price AS avgUnitPrice, stock_amount AS stockAmount, version, " +
                        "last_recalc_time AS lastRecalcTime, last_inbound_date AS lastInboundDate " +
                        "FROM finance_inventory_price_latest " + where + " ORDER BY " + orderBy,
                args.toArray()
        );
    }

    public List<Map<String, Object>> exportChange(String bizDate, String keyword, String sortField, String sortOrder) {
        ensureTables();
        StringBuilder where = new StringBuilder(" WHERE 1=1 ");
        List<Object> args = new ArrayList<>();
        if (hasText(bizDate)) {
            where.append(" AND biz_date = ? ");
            args.add(bizDate.trim());
        }
        if (hasText(keyword)) {
            where.append(" AND (material_code LIKE ? OR material_name LIKE ?) ");
            String k = "%" + keyword.trim() + "%";
            args.add(k);
            args.add(k);
        }
        String orderBy = buildChangeOrderBy(sortField, sortOrder);
        return jdbcTemplate.queryForList(
                "SELECT biz_date AS bizDate, material_code AS materialCode, material_name AS materialName, uom, " +
                        "old_stock_qty AS oldStockQty, old_stock_amount AS oldStockAmount, old_unit_price AS oldUnitPrice, " +
                        "in_qty AS inQty, in_amount AS inAmount, out_qty AS outQty, new_stock_qty AS newStockQty, " +
                        "new_stock_amount AS newStockAmount, new_unit_price AS newUnitPrice, recalc_time AS recalcTime " +
                        "FROM finance_inventory_price_change " + where + " ORDER BY " + orderBy,
                args.toArray()
        );
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> importLatestCsv(MultipartFile file) {
        ensureTables();
        if (file == null || file.isEmpty()) {
            throw new RuntimeException("导入文件不能为空");
        }
        int successCount = 0;
        int skipCount = 0;
        List<String> errors = new ArrayList<>();

        try (BufferedReader br = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            int lineNo = 0;
            while ((line = br.readLine()) != null) {
                lineNo++;
                if (!hasText(line)) {
                    continue;
                }
                if (lineNo == 1 && line.charAt(0) == '\uFEFF') {
                    line = line.substring(1);
                }
                List<String> cells = parseCsvLine(line);
                if (cells.isEmpty()) {
                    continue;
                }
                if (lineNo == 1 && isLatestHeader(cells)) {
                    continue;
                }

                String materialCode = cell(cells, 0);
                if (!hasText(materialCode)) {
                    skipCount++;
                    continue;
                }
                try {
                    String materialName = cell(cells, 1);
                    String uom = normalizeUom(cell(cells, 2));
                    BigDecimal stockQty = parseDecimal(cell(cells, 3));
                    BigDecimal avgUnitPrice = parseDecimal(cell(cells, 4));
                    BigDecimal stockAmount = parseDecimal(cell(cells, 5));
                    Integer version = parseInt(cell(cells, 6));
                    Timestamp recalcTime = parseDateTime(cell(cells, 7));
                    LocalDate inboundDate = parseDate(cell(cells, 8));

                    if (stockAmount.compareTo(BigDecimal.ZERO) <= 0 && stockQty.compareTo(BigDecimal.ZERO) > 0 && avgUnitPrice.compareTo(BigDecimal.ZERO) > 0) {
                        stockAmount = stockQty.multiply(avgUnitPrice).setScale(6, RoundingMode.HALF_UP);
                    }

                    jdbcTemplate.update(
                            "INSERT INTO finance_inventory_price_latest(material_code, material_name, uom, stock_qty, avg_unit_price, stock_amount, version, last_recalc_time, last_inbound_date) " +
                                    "VALUES (?, ?, ?, ?, ?, ?, ?, COALESCE(?, NOW()), ?) " +
                                    "ON DUPLICATE KEY UPDATE material_name = VALUES(material_name), uom = VALUES(uom), stock_qty = VALUES(stock_qty), " +
                                    "avg_unit_price = VALUES(avg_unit_price), stock_amount = VALUES(stock_amount), version = VALUES(version), " +
                                    "last_recalc_time = COALESCE(VALUES(last_recalc_time), last_recalc_time), " +
                                    "last_inbound_date = COALESCE(VALUES(last_inbound_date), last_inbound_date)",
                            materialCode.trim(),
                            hasText(materialName) ? materialName.trim() : materialCode.trim(),
                            hasText(uom) ? uom : "kg",
                            stockQty,
                            avgUnitPrice,
                            stockAmount,
                            version == null ? 0 : Math.max(version, 0),
                            recalcTime,
                            inboundDate
                    );

                    jdbcTemplate.update(
                            "INSERT INTO material_base_price(material_code, material_name, recent_3m_total_quantity, recent_3m_total_amount, avg_unit_price, stats_updated_at) " +
                                    "VALUES (?, ?, 0, 0, ?, NOW()) " +
                                    "ON DUPLICATE KEY UPDATE material_name = VALUES(material_name), avg_unit_price = VALUES(avg_unit_price), stats_updated_at = NOW()",
                            materialCode.trim(),
                            hasText(materialName) ? materialName.trim() : materialCode.trim(),
                            avgUnitPrice
                    );
                    successCount++;
                } catch (Exception ex) {
                    errors.add("第" + lineNo + "行导入失败: " + ex.getMessage());
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("读取导入文件失败: " + e.getMessage(), e);
        }

        Map<String, Object> data = new HashMap<>();
        data.put("successCount", successCount);
        data.put("skipCount", skipCount);
        data.put("errorCount", errors.size());
        data.put("errors", errors);
        return data;
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> importChangeCsv(MultipartFile file) {
        ensureTables();
        if (file == null || file.isEmpty()) {
            throw new RuntimeException("导入文件不能为空");
        }
        int successCount = 0;
        int skipCount = 0;
        List<String> errors = new ArrayList<>();

        try (BufferedReader br = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            int lineNo = 0;
            while ((line = br.readLine()) != null) {
                lineNo++;
                if (!hasText(line)) {
                    continue;
                }
                if (lineNo == 1 && line.charAt(0) == '\uFEFF') {
                    line = line.substring(1);
                }
                List<String> cells = parseCsvLine(line);
                if (cells.isEmpty()) {
                    continue;
                }
                if (lineNo == 1 && isChangeHeader(cells)) {
                    continue;
                }

                String materialCode = cell(cells, 1);
                String bizDate = cell(cells, 0);
                if (!hasText(materialCode) || !hasText(bizDate)) {
                    skipCount++;
                    continue;
                }
                try {
                    jdbcTemplate.update(
                            "INSERT INTO finance_inventory_price_change(biz_date, material_code, material_name, uom, old_stock_qty, old_stock_amount, old_unit_price, in_qty, in_amount, out_qty, new_stock_qty, new_stock_amount, new_unit_price, recalc_time) " +
                                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, COALESCE(?, NOW()))",
                            parseDate(bizDate),
                            materialCode.trim(),
                            hasText(cell(cells, 2)) ? cell(cells, 2).trim() : materialCode.trim(),
                            hasText(cell(cells, 3)) ? normalizeUom(cell(cells, 3)) : "kg",
                            parseDecimal(cell(cells, 4)),
                            parseDecimal(cell(cells, 5)),
                            parseDecimal(cell(cells, 6)),
                            parseDecimal(cell(cells, 7)),
                            parseDecimal(cell(cells, 8)),
                            parseDecimal(cell(cells, 9)),
                            parseDecimal(cell(cells, 10)),
                            parseDecimal(cell(cells, 11)),
                            parseDecimal(cell(cells, 12)),
                            parseDateTime(cell(cells, 13))
                    );
                    successCount++;
                } catch (Exception ex) {
                    errors.add("第" + lineNo + "行导入失败: " + ex.getMessage());
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("读取导入文件失败: " + e.getMessage(), e);
        }

        Map<String, Object> data = new HashMap<>();
        data.put("successCount", successCount);
        data.put("skipCount", skipCount);
        data.put("errorCount", errors.size());
        data.put("errors", errors);
        return data;
    }

    private String buildLatestOrderBy(String sortField, String sortOrder) {
        Map<String, String> fieldMap = new HashMap<>();
        fieldMap.put("materialCode", "material_code");
        fieldMap.put("materialName", "material_name");
        fieldMap.put("uom", "uom");
        fieldMap.put("stockQty", "stock_qty");
        fieldMap.put("avgUnitPrice", "avg_unit_price");
        fieldMap.put("stockAmount", "stock_amount");
        fieldMap.put("version", "version");
        fieldMap.put("lastRecalcTime", "last_recalc_time");

        String col = fieldMap.get(sortField);
        if (!hasText(col)) {
            return "last_recalc_time DESC, id DESC";
        }
        String direction = normalizeSortOrder(sortOrder);
        return col + " " + direction + ", id DESC";
    }

    private String buildChangeOrderBy(String sortField, String sortOrder) {
        Map<String, String> fieldMap = new HashMap<>();
        fieldMap.put("bizDate", "biz_date");
        fieldMap.put("materialCode", "material_code");
        fieldMap.put("materialName", "material_name");
        fieldMap.put("oldStockQty", "old_stock_qty");
        fieldMap.put("oldUnitPrice", "old_unit_price");
        fieldMap.put("inQty", "in_qty");
        fieldMap.put("inAmount", "in_amount");
        fieldMap.put("outQty", "out_qty");
        fieldMap.put("newStockQty", "new_stock_qty");
        fieldMap.put("newStockAmount", "new_stock_amount");
        fieldMap.put("newUnitPrice", "new_unit_price");
        fieldMap.put("recalcTime", "recalc_time");

        String col = fieldMap.get(sortField);
        if (!hasText(col)) {
            return "recalc_time DESC, id DESC";
        }
        String direction = normalizeSortOrder(sortOrder);
        return col + " " + direction + ", id DESC";
    }

    private String normalizeSortOrder(String sortOrder) {
        if (!hasText(sortOrder)) {
            return "DESC";
        }
        String v = sortOrder.trim().toLowerCase(Locale.ROOT);
        if ("ascending".equals(v) || "asc".equals(v)) {
            return "ASC";
        }
        return "DESC";
    }

    private boolean isLatestHeader(List<String> cells) {
        String first = cell(cells, 0).toLowerCase(Locale.ROOT);
        return first.contains("material") || first.contains("料号");
    }

    private boolean isChangeHeader(List<String> cells) {
        String first = cell(cells, 0).toLowerCase(Locale.ROOT);
        return first.contains("biz") || first.contains("业务日期");
    }

    private String cell(List<String> cells, int idx) {
        if (idx < 0 || idx >= cells.size()) {
            return "";
        }
        return cells.get(idx) == null ? "" : cells.get(idx).trim();
    }

    private List<String> parseCsvLine(String line) {
        List<String> values = new ArrayList<>();
        if (line == null) {
            return values;
        }
        StringBuilder sb = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    sb.append('"');
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (c == ',' && !inQuotes) {
                values.add(sb.toString());
                sb.setLength(0);
            } else {
                sb.append(c);
            }
        }
        values.add(sb.toString());
        return values;
    }

    private BigDecimal parseDecimal(String text) {
        if (!hasText(text)) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(text.trim());
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }

    private Integer parseInt(String text) {
        if (!hasText(text)) {
            return null;
        }
        try {
            return Integer.parseInt(text.trim());
        } catch (Exception e) {
            return null;
        }
    }

    private LocalDate parseDate(String text) {
        if (!hasText(text)) {
            return null;
        }
        try {
            return LocalDate.parse(text.trim());
        } catch (Exception e) {
            return null;
        }
    }

    private Timestamp parseDateTime(String text) {
        if (!hasText(text)) {
            return null;
        }
        String value = text.trim().replace('T', ' ');
        try {
            return Timestamp.valueOf(value);
        } catch (Exception e) {
            try {
                return Timestamp.valueOf(value + " 00:00:00");
            } catch (Exception ex) {
                return null;
            }
        }
    }

    private boolean updateOneWithOptimisticLock(String bizDate,
                                                String materialCode,
                                                String materialName,
                                                String uom,
                                                BigDecimal inQty,
                                                BigDecimal inAmount,
                                                BigDecimal outQty) {
        for (int i = 0; i < 3; i++) {
            List<Map<String, Object>> latestRows = jdbcTemplate.queryForList(
                    "SELECT id, stock_qty AS stockQty, stock_amount AS stockAmount, avg_unit_price AS avgUnitPrice, version " +
                            "FROM finance_inventory_price_latest WHERE material_code = ? LIMIT 1",
                    materialCode
            );

            BigDecimal oldQty = BigDecimal.ZERO;
            BigDecimal oldAmount = BigDecimal.ZERO;
            BigDecimal oldPrice = BigDecimal.ZERO;
            int oldVersion = 0;
            Long id = null;
            if (!latestRows.isEmpty()) {
                Map<String, Object> old = latestRows.get(0);
                id = toLong(old.get("id"));
                oldQty = toDecimal(old.get("stockQty"));
                oldAmount = toDecimal(old.get("stockAmount"));
                oldPrice = toDecimal(old.get("avgUnitPrice"));
                oldVersion = toInt(old.get("version"));
            }

            BigDecimal outAmount = outQty.multiply(oldPrice);
            BigDecimal newQty = oldQty.add(inQty).subtract(outQty);
            BigDecimal newAmount = oldAmount.add(inAmount).subtract(outAmount);
            if (newQty.compareTo(BigDecimal.ZERO) < 0) {
                newQty = BigDecimal.ZERO;
            }
            if (newAmount.compareTo(BigDecimal.ZERO) < 0) {
                newAmount = BigDecimal.ZERO;
            }
            BigDecimal newPrice = newQty.compareTo(BigDecimal.ZERO) <= 0
                    ? BigDecimal.ZERO
                    : newAmount.divide(newQty, 6, RoundingMode.HALF_UP);

            int affected;
            if (id == null) {
                try {
                    affected = jdbcTemplate.update(
                            "INSERT INTO finance_inventory_price_latest(material_code, material_name, uom, stock_qty, avg_unit_price, stock_amount, version, last_recalc_time, last_inbound_date) " +
                                    "VALUES (?, ?, ?, ?, ?, ?, 0, NOW(), ?)",
                            materialCode, materialName, uom, newQty, newPrice, newAmount, bizDate
                    );
                } catch (Exception e) {
                    continue;
                }
            } else {
                affected = jdbcTemplate.update(
                        "UPDATE finance_inventory_price_latest SET material_name = ?, uom = ?, stock_qty = ?, avg_unit_price = ?, stock_amount = ?, " +
                                "version = version + 1, last_recalc_time = NOW(), last_inbound_date = ? WHERE id = ? AND version = ?",
                        materialName, uom, newQty, newPrice, newAmount, bizDate, id, oldVersion
                );
            }

            if (affected > 0) {
                jdbcTemplate.update(
                        "INSERT INTO finance_inventory_price_change(biz_date, material_code, material_name, uom, old_stock_qty, old_stock_amount, old_unit_price, in_qty, in_amount, out_qty, new_stock_qty, new_stock_amount, new_unit_price, recalc_time) " +
                                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW())",
                        bizDate, materialCode, materialName, uom, oldQty, oldAmount, oldPrice, inQty, inAmount, outQty, newQty, newAmount, newPrice
                );

                jdbcTemplate.update(
                        "INSERT INTO material_base_price(material_code, material_name, recent_3m_total_quantity, recent_3m_total_amount, avg_unit_price, stats_updated_at) " +
                                "VALUES (?, ?, ?, ?, ?, NOW()) " +
                                "ON DUPLICATE KEY UPDATE material_name = VALUES(material_name), avg_unit_price = VALUES(avg_unit_price), stats_updated_at = NOW()",
                        materialCode, materialName, newQty, newAmount, newPrice
                );
                return true;
            }
        }
        return false;
    }

    private void normalizeFilmAreaUnitPriceByLatestQuote() {
        jdbcTemplate.execute("DROP TEMPORARY TABLE IF EXISTS tmp_latest_kg_quote");
        jdbcTemplate.execute("DROP TEMPORARY TABLE IF EXISTS tmp_latest_quote_all");
        jdbcTemplate.execute("DROP TEMPORARY TABLE IF EXISTS tmp_latest_any_quote");
        jdbcTemplate.execute("DROP TEMPORARY TABLE IF EXISTS tmp_material_has_kg_quote");
        jdbcTemplate.execute(
            "CREATE TEMPORARY TABLE tmp_latest_quote_all AS " +
                "SELECT t.material_code, t.unit_price, t.unit " +
                "FROM (" +
                "  SELECT x.material_code, x.unit_price, x.unit, " +
                "         ROW_NUMBER() OVER (PARTITION BY x.material_code ORDER BY x.quote_time DESC, x.id DESC) AS rn " +
                "  FROM (" +
                "    SELECT qi.id, qi.material_code, qi.unit_price, qi.unit, " +
                "           COALESCE(qi.updated_at, qi.created_at, q.updated_at, q.created_at, CONCAT(q.quotation_date,' 00:00:00')) AS quote_time " +
                "    FROM quotation_items qi " +
                "    LEFT JOIN quotations q ON q.id = qi.quotation_id " +
                "    WHERE qi.is_deleted=0 AND IFNULL(q.is_deleted,0)=0 " +
                "      AND qi.material_code IS NOT NULL AND qi.material_code<>'' " +
                "      AND qi.unit_price IS NOT NULL AND qi.unit_price>0 " +
                "    UNION ALL " +
                "    SELECT pqi.id, pqi.material_code, pqi.unit_price, pqi.unit, " +
                "           COALESCE(pqi.updated_at, pqi.created_at) AS quote_time " +
                "    FROM purchase_quotation_items pqi " +
                "    WHERE pqi.is_deleted=0 " +
                "      AND pqi.material_code IS NOT NULL AND pqi.material_code<>'' " +
                "      AND pqi.unit_price IS NOT NULL AND pqi.unit_price>0 " +
                "  ) x" +
                ") t WHERE t.rn=1"
        );

            jdbcTemplate.execute(
                "CREATE TEMPORARY TABLE tmp_latest_kg_quote AS " +
                "SELECT material_code, unit_price AS kg_price " +
                "FROM tmp_latest_quote_all " +
                "WHERE UPPER(REPLACE(IFNULL(unit,''),' ','')) IN ('KG','KGS','公斤','千克')"
            );

            // 化工库存：按kg最新报价直接同步库存单价
            jdbcTemplate.update(
                "UPDATE chemical_stock_detail csd " +
                "JOIN tmp_latest_kg_quote q ON q.material_code = csd.material_code " +
                "SET csd.unit_price = q.kg_price, csd.update_time = NOW() " +
                "WHERE csd.is_deleted=0 AND csd.status IN ('available','locked')"
            );

        // 通用膜料：当最新报价单位为㎡时，直接按㎡价回填（PEG按支计价，不在此处理）
        jdbcTemplate.update(
            "UPDATE film_stock_detail fsd " +
                "JOIN tmp_latest_quote_all q ON q.material_code = fsd.material_code " +
                "SET fsd.unit_price = q.unit_price, fsd.update_time = NOW() " +
                "WHERE fsd.is_deleted=0 AND fsd.status IN ('available','locked') " +
                "  AND UPPER(fsd.material_code) NOT LIKE 'PEG-%' " +
                "  AND UPPER(fsd.material_code) <> 'NPZ-D105' " +
                "  AND UPPER(REPLACE(IFNULL(q.unit,''),' ','')) IN ('㎡','M2','平米','平方米')"
        );

        // 离型膜（LXM/LXZ）按报价单价直接作为㎡价，不做kg换算
        jdbcTemplate.update(
            "UPDATE film_stock_detail fsd " +
                "JOIN tmp_latest_quote_all q ON q.material_code = fsd.material_code " +
                "SET fsd.unit_price = q.unit_price, fsd.update_time = NOW() " +
                "WHERE fsd.is_deleted=0 AND fsd.status IN ('available','locked') " +
                "  AND UPPER(fsd.material_code) NOT LIKE 'PEG-%' " +
                "  AND UPPER(fsd.material_code) <> 'NPZ-D105' " +
                "  AND (UPPER(fsd.material_code) LIKE '%LXM%' OR UPPER(fsd.material_code) LIKE '%LXZ%')"
        );

        // 非离型膜：仅当“最新报价单位”为kg时才换算为㎡单价
        jdbcTemplate.update(
            "UPDATE film_stock_detail fsd " +
                "JOIN tmp_latest_quote_all q ON q.material_code = fsd.material_code " +
                "SET fsd.unit_price = ROUND(q.unit_price * IFNULL(fsd.thickness,0) * (CASE " +
                "  WHEN UPPER(fsd.material_code) LIKE 'PET%' THEN 1.38 " +
                "  WHEN UPPER(fsd.material_code) LIKE 'PI%' OR UPPER(fsd.material_code) LIKE 'PIM%' THEN 1.42 " +
                "  WHEN UPPER(fsd.material_code) LIKE 'BOPP%' OR UPPER(fsd.material_code) LIKE 'OPP%' OR UPPER(fsd.material_code) LIKE 'CPP%' OR UPPER(fsd.material_code) LIKE 'PP%' THEN 0.90 " +
                "  WHEN UPPER(fsd.material_code) LIKE 'PE%' THEN 0.92 " +
                "  WHEN UPPER(fsd.material_code) LIKE 'PVC%' THEN 1.35 " +
                "  WHEN UPPER(fsd.material_code) LIKE 'OPS%' OR UPPER(fsd.material_code) LIKE 'PS%' THEN 1.05 " +
                "  ELSE NULL END) / 1000, 4), " +
                "fsd.update_time = NOW() " +
                "WHERE fsd.is_deleted=0 AND fsd.status IN ('available','locked') " +
                "  AND UPPER(fsd.material_code) NOT LIKE 'PEG-%' " +
                "  AND UPPER(fsd.material_code) <> 'NPZ-D105' " +
                "  AND UPPER(fsd.material_code) NOT LIKE '%LXM%' " +
                "  AND UPPER(fsd.material_code) NOT LIKE '%LXZ%' " +
                "  AND UPPER(REPLACE(IFNULL(q.unit,''),' ','')) IN ('KG','KGS','公斤','千克') " +
                "  AND IFNULL(fsd.thickness,0) > 0 " +
                "  AND (CASE " +
                "  WHEN UPPER(fsd.material_code) LIKE 'PET%' THEN 1.38 " +
                "  WHEN UPPER(fsd.material_code) LIKE 'PI%' OR UPPER(fsd.material_code) LIKE 'PIM%' THEN 1.42 " +
                "  WHEN UPPER(fsd.material_code) LIKE 'BOPP%' OR UPPER(fsd.material_code) LIKE 'OPP%' OR UPPER(fsd.material_code) LIKE 'CPP%' OR UPPER(fsd.material_code) LIKE 'PP%' THEN 0.90 " +
                "  WHEN UPPER(fsd.material_code) LIKE 'PE%' THEN 0.92 " +
                "  WHEN UPPER(fsd.material_code) LIKE 'PVC%' THEN 1.35 " +
                "  WHEN UPPER(fsd.material_code) LIKE 'OPS%' OR UPPER(fsd.material_code) LIKE 'PS%' THEN 1.05 " +
                "  ELSE NULL END) IS NOT NULL"
        );

                jdbcTemplate.execute(
                    "CREATE TEMPORARY TABLE tmp_latest_any_quote AS " +
                        "SELECT t.material_code, t.unit_price, t.unit " +
                        "FROM (" +
                        "  SELECT x.material_code, x.unit_price, x.unit, " +
                        "         ROW_NUMBER() OVER (PARTITION BY x.material_code ORDER BY x.quote_time DESC, x.id DESC) AS rn " +
                        "  FROM (" +
                        "    SELECT qi.id, qi.material_code, qi.unit_price, qi.unit, " +
                        "           COALESCE(qi.updated_at, qi.created_at, q.updated_at, q.created_at, CONCAT(q.quotation_date,' 00:00:00')) AS quote_time " +
                        "    FROM quotation_items qi " +
                        "    LEFT JOIN quotations q ON q.id = qi.quotation_id " +
                        "    WHERE qi.is_deleted=0 AND IFNULL(q.is_deleted,0)=0 " +
                        "      AND qi.material_code IS NOT NULL AND qi.material_code<>'' " +
                        "      AND qi.unit_price IS NOT NULL AND qi.unit_price>0 " +
                        "    UNION ALL " +
                        "    SELECT pqi.id, pqi.material_code, pqi.unit_price, pqi.unit, " +
                        "           COALESCE(pqi.updated_at, pqi.created_at) AS quote_time " +
                        "    FROM purchase_quotation_items pqi " +
                        "    WHERE pqi.is_deleted=0 " +
                        "      AND pqi.material_code IS NOT NULL AND pqi.material_code<>'' " +
                        "      AND pqi.unit_price IS NOT NULL AND pqi.unit_price>0 " +
                        "  ) x" +
                        ") t WHERE t.rn=1"
                );

                    // 历史存在kg报价的料号集合（用于识别“单位误录为㎡”的场景）
                    jdbcTemplate.execute(
                        "CREATE TEMPORARY TABLE tmp_material_has_kg_quote AS " +
                        "SELECT DISTINCT material_code FROM (" +
                        "  SELECT qi.material_code, qi.unit " +
                        "  FROM quotation_items qi " +
                        "  LEFT JOIN quotations q ON q.id = qi.quotation_id " +
                        "  WHERE qi.is_deleted=0 AND IFNULL(q.is_deleted,0)=0 " +
                        "    AND qi.material_code IS NOT NULL AND qi.material_code<>'' " +
                        "  UNION ALL " +
                        "  SELECT pqi.material_code, pqi.unit " +
                        "  FROM purchase_quotation_items pqi " +
                        "  WHERE pqi.is_deleted=0 " +
                        "    AND pqi.material_code IS NOT NULL AND pqi.material_code<>'' " +
                        ") t " +
                        "WHERE UPPER(REPLACE(IFNULL(unit,''),' ','')) IN ('KG','KGS','公斤','千克')"
                    );

        jdbcTemplate.update(
                "UPDATE film_stock_detail fsd " +
                        "JOIN tmp_latest_kg_quote q ON q.material_code = fsd.material_code " +
                        "SET fsd.unit_price = ROUND(q.kg_price * IFNULL(fsd.thickness,0) * (CASE " +
                        "  WHEN UPPER(fsd.material_code) LIKE 'PET%' THEN 1.38 " +
                        "  WHEN UPPER(fsd.material_code) LIKE 'PI%' OR UPPER(fsd.material_code) LIKE 'PIM%' THEN 1.42 " +
                        "  WHEN UPPER(fsd.material_code) LIKE 'BOPP%' OR UPPER(fsd.material_code) LIKE 'OPP%' OR UPPER(fsd.material_code) LIKE 'CPP%' OR UPPER(fsd.material_code) LIKE 'PP%' THEN 0.90 " +
                        "  WHEN UPPER(fsd.material_code) LIKE 'PE%' THEN 0.92 " +
                        "  WHEN UPPER(fsd.material_code) LIKE 'PVC%' THEN 1.35 " +
                        "  WHEN UPPER(fsd.material_code) LIKE 'OPS%' OR UPPER(fsd.material_code) LIKE 'PS%' THEN 1.05 " +
                        "  ELSE NULL END) / 1000, 4), " +
                        "fsd.update_time = NOW() " +
                        "WHERE fsd.is_deleted=0 AND fsd.status IN ('available','locked') " +
                        "  AND UPPER(fsd.material_code) NOT LIKE 'PEG-%' " +
                        "  AND UPPER(fsd.material_code) <> 'NPZ-D105' " +
                        "  AND IFNULL(fsd.thickness,0) > 0 " +
                        "  AND (CASE " +
                        "  WHEN UPPER(fsd.material_code) LIKE 'PET%' THEN 1.38 " +
                        "  WHEN UPPER(fsd.material_code) LIKE 'PI%' OR UPPER(fsd.material_code) LIKE 'PIM%' THEN 1.42 " +
                        "  WHEN UPPER(fsd.material_code) LIKE 'BOPP%' OR UPPER(fsd.material_code) LIKE 'OPP%' OR UPPER(fsd.material_code) LIKE 'CPP%' OR UPPER(fsd.material_code) LIKE 'PP%' THEN 0.90 " +
                        "  WHEN UPPER(fsd.material_code) LIKE 'PE%' THEN 0.92 " +
                        "  WHEN UPPER(fsd.material_code) LIKE 'PVC%' THEN 1.35 " +
                        "  WHEN UPPER(fsd.material_code) LIKE 'OPS%' OR UPPER(fsd.material_code) LIKE 'PS%' THEN 1.05 " +
                        "  ELSE NULL END) IS NOT NULL"
        );

                // 通用兜底：若最新报价单位是㎡但该料号历史存在kg报价且㎡价异常偏高，则按kg口径换算为㎡价
                jdbcTemplate.update(
                    "UPDATE film_stock_detail fsd " +
                        "JOIN tmp_latest_any_quote q ON q.material_code = fsd.material_code " +
                        "JOIN tmp_material_has_kg_quote hk ON hk.material_code = fsd.material_code " +
                        "SET fsd.unit_price = ROUND(q.unit_price * IFNULL(fsd.thickness,0) * (CASE " +
                        "  WHEN UPPER(fsd.material_code) LIKE 'PET%' THEN 1.38 " +
                        "  WHEN UPPER(fsd.material_code) LIKE 'PI%' OR UPPER(fsd.material_code) LIKE 'PIM%' THEN 1.42 " +
                        "  WHEN UPPER(fsd.material_code) LIKE 'BOPP%' OR UPPER(fsd.material_code) LIKE 'OPP%' OR UPPER(fsd.material_code) LIKE 'CPP%' OR UPPER(fsd.material_code) LIKE 'PP%' THEN 0.90 " +
                        "  WHEN UPPER(fsd.material_code) LIKE 'PE%' THEN 0.92 " +
                        "  WHEN UPPER(fsd.material_code) LIKE 'PVC%' THEN 1.35 " +
                        "  WHEN UPPER(fsd.material_code) LIKE 'OPS%' OR UPPER(fsd.material_code) LIKE 'PS%' THEN 1.05 " +
                        "  ELSE NULL END) / 1000, 4), " +
                        "    fsd.update_time = NOW() " +
                        "WHERE fsd.is_deleted=0 AND fsd.status IN ('available','locked') " +
                        "  AND UPPER(fsd.material_code) NOT LIKE 'PEG-%' " +
                        "  AND UPPER(fsd.material_code) <> 'NPZ-D105' " +
                        "  AND IFNULL(fsd.thickness,0) > 0 " +
                        "  AND UPPER(REPLACE(IFNULL(q.unit,''),' ','')) IN ('㎡','M2','平米','平方米') " +
                        "  AND q.unit_price >= 10 " +
                        "  AND (CASE " +
                        "  WHEN UPPER(fsd.material_code) LIKE 'PET%' THEN 1.38 " +
                        "  WHEN UPPER(fsd.material_code) LIKE 'PI%' OR UPPER(fsd.material_code) LIKE 'PIM%' THEN 1.42 " +
                        "  WHEN UPPER(fsd.material_code) LIKE 'BOPP%' OR UPPER(fsd.material_code) LIKE 'OPP%' OR UPPER(fsd.material_code) LIKE 'CPP%' OR UPPER(fsd.material_code) LIKE 'PP%' THEN 0.90 " +
                        "  WHEN UPPER(fsd.material_code) LIKE 'PE%' THEN 0.92 " +
                        "  WHEN UPPER(fsd.material_code) LIKE 'PVC%' THEN 1.35 " +
                        "  WHEN UPPER(fsd.material_code) LIKE 'OPS%' OR UPPER(fsd.material_code) LIKE 'PS%' THEN 1.05 " +
                        "  ELSE NULL END) IS NOT NULL"
                );

                // 兜底：OPS/PS 系列若最新报价单位被误录为㎡且价格异常偏高（如44），按kg价换算为㎡价
                jdbcTemplate.update(
                    "UPDATE film_stock_detail fsd " +
                        "JOIN tmp_latest_any_quote q ON q.material_code = fsd.material_code " +
                        "SET fsd.unit_price = ROUND(q.unit_price * IFNULL(fsd.thickness,0) * 1.05 / 1000, 4), " +
                        "    fsd.update_time = NOW() " +
                        "WHERE fsd.is_deleted=0 AND fsd.status IN ('available','locked') " +
                        "  AND UPPER(fsd.material_code) NOT LIKE 'PEG-%' " +
                        "  AND UPPER(fsd.material_code) <> 'NPZ-D105' " +
                        "  AND (UPPER(fsd.material_code) LIKE 'OPS%' OR UPPER(fsd.material_code) LIKE 'PS%') " +
                        "  AND IFNULL(fsd.thickness,0) > 0 " +
                        "  AND UPPER(REPLACE(IFNULL(q.unit,''),' ','')) IN ('㎡','M2','平米','平方米') " +
                        "  AND q.unit_price >= 10"
                );

                    // 兜底：PETLXM/PETLXZ 系列若最新报价单位被误录为㎡且价格异常偏高（如13.7），按kg价换算为㎡价
                    jdbcTemplate.update(
                        "UPDATE film_stock_detail fsd " +
                        "JOIN tmp_latest_any_quote q ON q.material_code = fsd.material_code " +
                        "SET fsd.unit_price = ROUND(q.unit_price * IFNULL(fsd.thickness,0) * 1.38 / 1000, 4), " +
                        "    fsd.update_time = NOW() " +
                        "WHERE fsd.is_deleted=0 AND fsd.status IN ('available','locked') " +
                        "  AND UPPER(fsd.material_code) NOT LIKE 'PEG-%' " +
                        "  AND UPPER(fsd.material_code) <> 'NPZ-D105' " +
                        "  AND (UPPER(fsd.material_code) LIKE 'PETLXM%' OR UPPER(fsd.material_code) LIKE 'PETLXZ%') " +
                        "  AND IFNULL(fsd.thickness,0) > 0 " +
                        "  AND UPPER(REPLACE(IFNULL(q.unit,''),' ','')) IN ('㎡','M2','平米','平方米') " +
                        "  AND q.unit_price >= 10"
                    );
    }

    private void snapshotMonthEnd(LocalDate date) {
        String monthKey = YearMonth.from(date).toString();
        jdbcTemplate.update("DELETE FROM finance_inventory_price_month_snapshot WHERE month_key = ?", monthKey);
        jdbcTemplate.update(
                "INSERT INTO finance_inventory_price_month_snapshot(month_key, material_code, material_name, uom, stock_qty, avg_unit_price, stock_amount, snapshot_time) " +
                        "SELECT ?, material_code, material_name, uom, stock_qty, avg_unit_price, stock_amount, NOW() FROM finance_inventory_price_latest",
                monthKey
        );
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
                    "CREATE TABLE IF NOT EXISTS finance_inventory_price_latest (" +
                            "id BIGINT PRIMARY KEY AUTO_INCREMENT," +
                            "material_code VARCHAR(100) NOT NULL," +
                            "material_name VARCHAR(200) NULL," +
                            "uom VARCHAR(20) NOT NULL DEFAULT 'kg'," +
                            "stock_qty DECIMAL(18,6) NOT NULL DEFAULT 0," +
                            "avg_unit_price DECIMAL(18,6) NOT NULL DEFAULT 0," +
                            "stock_amount DECIMAL(18,6) NOT NULL DEFAULT 0," +
                            "version INT NOT NULL DEFAULT 0," +
                            "last_inbound_date DATE NULL," +
                            "last_recalc_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP," +
                            "UNIQUE KEY uk_fin_inv_price_latest(material_code)," +
                            "INDEX idx_fin_inv_price_latest_time(last_recalc_time)" +
                            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4"
            );

            jdbcTemplate.execute(
                    "CREATE TABLE IF NOT EXISTS finance_inventory_price_change (" +
                            "id BIGINT PRIMARY KEY AUTO_INCREMENT," +
                            "biz_date DATE NOT NULL," +
                            "material_code VARCHAR(100) NOT NULL," +
                            "material_name VARCHAR(200) NULL," +
                            "uom VARCHAR(20) NOT NULL DEFAULT 'kg'," +
                            "old_stock_qty DECIMAL(18,6) NOT NULL DEFAULT 0," +
                            "old_stock_amount DECIMAL(18,6) NOT NULL DEFAULT 0," +
                            "old_unit_price DECIMAL(18,6) NOT NULL DEFAULT 0," +
                            "in_qty DECIMAL(18,6) NOT NULL DEFAULT 0," +
                            "in_amount DECIMAL(18,6) NOT NULL DEFAULT 0," +
                            "out_qty DECIMAL(18,6) NOT NULL DEFAULT 0," +
                            "new_stock_qty DECIMAL(18,6) NOT NULL DEFAULT 0," +
                            "new_stock_amount DECIMAL(18,6) NOT NULL DEFAULT 0," +
                            "new_unit_price DECIMAL(18,6) NOT NULL DEFAULT 0," +
                            "recalc_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP," +
                            "INDEX idx_fin_inv_price_chg_1(biz_date, material_code)," +
                            "INDEX idx_fin_inv_price_chg_2(recalc_time)" +
                            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4"
            );

            jdbcTemplate.execute(
                    "CREATE TABLE IF NOT EXISTS finance_inventory_price_month_snapshot (" +
                            "id BIGINT PRIMARY KEY AUTO_INCREMENT," +
                            "month_key VARCHAR(7) NOT NULL," +
                            "material_code VARCHAR(100) NOT NULL," +
                            "material_name VARCHAR(200) NULL," +
                            "uom VARCHAR(20) NOT NULL DEFAULT 'kg'," +
                            "stock_qty DECIMAL(18,6) NOT NULL DEFAULT 0," +
                            "avg_unit_price DECIMAL(18,6) NOT NULL DEFAULT 0," +
                            "stock_amount DECIMAL(18,6) NOT NULL DEFAULT 0," +
                            "snapshot_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP," +
                            "UNIQUE KEY uk_fin_inv_month_snap(month_key, material_code)," +
                            "INDEX idx_fin_inv_month_snap_1(month_key)" +
                            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4"
            );

            tablesReady = true;
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private String asString(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private BigDecimal toDecimal(Object value) {
        if (value == null) return BigDecimal.ZERO;
        if (value instanceof BigDecimal) return (BigDecimal) value;
        try {
            return new BigDecimal(String.valueOf(value).trim());
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }

    private Long toLong(Object value) {
        if (value == null) return null;
        if (value instanceof Number) return ((Number) value).longValue();
        try {
            return Long.parseLong(String.valueOf(value).trim());
        } catch (Exception e) {
            return null;
        }
    }

    private int toInt(Object value) {
        if (value == null) return 0;
        if (value instanceof Number) return ((Number) value).intValue();
        try {
            return Integer.parseInt(String.valueOf(value).trim());
        } catch (Exception e) {
            return 0;
        }
    }

    private String normalizeUom(String uom) {
        String u = asString(uom).toLowerCase(Locale.ROOT);
        if ("㎡".equals(u) || "m2".equals(u) || "m²".equals(u) || "sqm".equals(u)) return "㎡";
        if ("支".equals(u) || "pcs".equals(u) || "pc".equals(u) || "piece".equals(u) || "卷".equals(u)) return "支";
        return "kg";
    }
}
