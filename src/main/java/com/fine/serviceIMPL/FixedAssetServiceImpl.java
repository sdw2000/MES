package com.fine.serviceIMPL;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fine.Dao.FixedAssetDepreciationMapper;
import com.fine.Dao.FixedAssetMapper;
import com.fine.Utils.ResponseResult;
import com.fine.modle.FixedAsset;
import com.fine.modle.FixedAssetDepreciation;
import com.fine.service.FixedAssetService;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.servlet.http.HttpServletResponse;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class FixedAssetServiceImpl implements FixedAssetService {

    @Autowired
    private FixedAssetMapper fixedAssetMapper;

    @Autowired
    private FixedAssetDepreciationMapper fixedAssetDepreciationMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private volatile boolean tablesChecked = false;

    @Override
    public ResponseResult<?> listAssets(Map<String, Object> params) {
        ensureTables();
        String keyword = params == null ? "" : stringValue(params.get("keyword"));
        String status = params == null ? "" : stringValue(params.get("status"));

        LambdaQueryWrapper<FixedAsset> qw = new LambdaQueryWrapper<FixedAsset>()
                .eq(FixedAsset::getIsDeleted, 0)
                .orderByDesc(FixedAsset::getUpdatedAt)
                .orderByDesc(FixedAsset::getId)
                .last("LIMIT 500");

        if (hasText(status)) {
            qw.eq(FixedAsset::getStatus, status.trim());
        }
        if (hasText(keyword)) {
            String kw = keyword.trim();
            qw.and(w -> w.like(FixedAsset::getAssetCode, kw)
                    .or()
                    .like(FixedAsset::getAssetName, kw)
                    .or()
                    .like(FixedAsset::getCategory, kw)
                    .or()
                    .like(FixedAsset::getResponsiblePerson, kw));
        }

        List<FixedAsset> rows = fixedAssetMapper.selectList(qw);
        return new ResponseResult<>(200, "OK", rows);
    }

    @Override
    public ResponseResult<?> getAsset(Long id) {
        ensureTables();
        if (id == null) {
            return new ResponseResult<>(400, "id required", null);
        }
        FixedAsset asset = fixedAssetMapper.selectOne(new LambdaQueryWrapper<FixedAsset>()
                .eq(FixedAsset::getId, id)
                .eq(FixedAsset::getIsDeleted, 0)
                .last("LIMIT 1"));
        return new ResponseResult<>(200, "OK", asset);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResponseResult<?> createAsset(Map<String, Object> payload) {
        ensureTables();
        if (payload == null) {
            return new ResponseResult<>(400, "payload required", null);
        }
        String assetCode = stringValue(payload.get("asset_code"));
        String assetName = stringValue(payload.get("asset_name"));
        String purchaseDate = stringValue(payload.get("purchase_date"));
        String category = stringValue(payload.get("category"));

        if (!hasText(assetCode) || !hasText(assetName) || !hasText(purchaseDate)) {
            return new ResponseResult<>(400, "asset_code, asset_name, purchase_date required", null);
        }

        BigDecimal originalValue = toDecimal(payload.get("original_value"));
        BigDecimal salvageValue = toDecimal(payload.get("salvage_value"));
        Integer usefulLifeMonths = toInt(payload.get("useful_life_months"));

        if (originalValue == null || originalValue.compareTo(BigDecimal.ZERO) <= 0) {
            return new ResponseResult<>(400, "original_value must be positive", null);
        }
        if (salvageValue == null) {
            salvageValue = BigDecimal.ZERO;
        }
        if (salvageValue.compareTo(BigDecimal.ZERO) < 0 || salvageValue.compareTo(originalValue) > 0) {
            return new ResponseResult<>(400, "salvage_value invalid", null);
        }
        if (usefulLifeMonths == null || usefulLifeMonths <= 0) {
            return new ResponseResult<>(400, "useful_life_months must be > 0", null);
        }

        FixedAsset existing = fixedAssetMapper.selectOne(new LambdaQueryWrapper<FixedAsset>()
                .eq(FixedAsset::getAssetCode, assetCode)
                .eq(FixedAsset::getIsDeleted, 0)
                .last("LIMIT 1"));
        if (existing != null) {
            return new ResponseResult<>(400, "asset_code already exists", null);
        }

        FixedAsset asset = new FixedAsset();
        asset.setAssetCode(assetCode.trim());
        asset.setAssetName(assetName.trim());
        asset.setCategory(category);
        asset.setPurchaseDate(Date.valueOf(purchaseDate));
        asset.setOriginalValue(scale(originalValue));
        asset.setSalvageValue(scale(salvageValue));
        asset.setUsefulLifeMonths(usefulLifeMonths);
        asset.setDepreciationMethod(defaultIfBlank(stringValue(payload.get("depreciation_method")), "STRAIGHT_LINE"));
        asset.setAccumulatedDepreciation(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        asset.setNetValue(scale(originalValue));
        asset.setStatus("ACTIVE");
        asset.setLocation(stringValue(payload.get("location")));
        asset.setResponsiblePerson(stringValue(payload.get("responsible_person")));
        asset.setRemark(stringValue(payload.get("remark")));
        asset.setIsDeleted(0);

        fixedAssetMapper.insert(asset);

        Map<String, Object> data = new HashMap<>();
        data.put("id", asset.getId());
        return new ResponseResult<>(200, "created", data);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResponseResult<?> updateAsset(Long id, Map<String, Object> payload) {
        ensureTables();
        if (id == null) {
            return new ResponseResult<>(400, "id required", null);
        }
        FixedAsset asset = fixedAssetMapper.selectOne(new LambdaQueryWrapper<FixedAsset>()
                .eq(FixedAsset::getId, id)
                .eq(FixedAsset::getIsDeleted, 0)
                .last("LIMIT 1"));
        if (asset == null) {
            return new ResponseResult<>(404, "asset not found", null);
        }
        if ("DISPOSED".equals(asset.getStatus())) {
            return new ResponseResult<>(400, "disposed asset cannot be edited", null);
        }

        String assetName = stringValue(payload.get("asset_name"));
        String category = stringValue(payload.get("category"));
        String location = stringValue(payload.get("location"));
        String responsiblePerson = stringValue(payload.get("responsible_person"));
        String remark = stringValue(payload.get("remark"));

        if (hasText(assetName)) asset.setAssetName(assetName.trim());
        if (payload.containsKey("category")) asset.setCategory(category);
        if (payload.containsKey("location")) asset.setLocation(location);
        if (payload.containsKey("responsible_person")) asset.setResponsiblePerson(responsiblePerson);
        if (payload.containsKey("remark")) asset.setRemark(remark);

        fixedAssetMapper.updateById(asset);
        return new ResponseResult<>(200, "updated", asset);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResponseResult<?> depreciate(Map<String, Object> payload) {
        ensureTables();
        if (payload == null) {
            return new ResponseResult<>(400, "payload required", null);
        }
        Long assetId = toLong(payload.get("asset_id"));
        String periodMonth = stringValue(payload.get("period_month"));
        if (assetId == null || !hasText(periodMonth) || !periodMonth.matches("\\d{4}-\\d{2}")) {
            return new ResponseResult<>(400, "asset_id and period_month(yyyy-MM) required", null);
        }

        FixedAsset asset = fixedAssetMapper.selectOne(new LambdaQueryWrapper<FixedAsset>()
                .eq(FixedAsset::getId, assetId)
                .eq(FixedAsset::getIsDeleted, 0)
                .last("LIMIT 1"));
        if (asset == null) {
            return new ResponseResult<>(404, "asset not found", null);
        }
        if (!"ACTIVE".equals(asset.getStatus())) {
            return new ResponseResult<>(400, "asset is not ACTIVE", null);
        }

        String note = defaultIfBlank(stringValue(payload.get("note")), "system depreciation");
        FixedAssetDepreciation log = performDepreciation(asset, periodMonth, note);
        if (log == null) {
            return new ResponseResult<>(400, "period already depreciated or asset already fully depreciated", null);
        }

        Map<String, Object> data = new HashMap<>();
        data.put("asset", asset);
        data.put("depreciation", log);
        return new ResponseResult<>(200, "depreciated", data);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResponseResult<?> batchDepreciate(Map<String, Object> payload) {
        ensureTables();
        if (payload == null) {
            return new ResponseResult<>(400, "payload required", null);
        }
        String periodMonth = stringValue(payload.get("period_month"));
        if (!hasText(periodMonth) || !periodMonth.matches("\\d{4}-\\d{2}")) {
            return new ResponseResult<>(400, "period_month(yyyy-MM) required", null);
        }
        String note = defaultIfBlank(stringValue(payload.get("note")), "batch depreciation");

        List<FixedAsset> assets = fixedAssetMapper.selectList(new LambdaQueryWrapper<FixedAsset>()
                .eq(FixedAsset::getIsDeleted, 0)
                .eq(FixedAsset::getStatus, "ACTIVE")
                .orderByAsc(FixedAsset::getId));

        int success = 0;
        int skipped = 0;
        List<Map<String, Object>> details = new ArrayList<>();
        for (FixedAsset asset : assets) {
            FixedAssetDepreciation log = performDepreciation(asset, periodMonth, note);
            if (log == null) {
                skipped++;
                continue;
            }
            success++;
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("assetId", asset.getId());
            item.put("assetCode", asset.getAssetCode());
            item.put("depreciationAmount", log.getDepreciationAmount());
            item.put("netValueAfter", log.getNetValueAfter());
            details.add(item);
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("periodMonth", periodMonth);
        data.put("totalActiveAssets", assets.size());
        data.put("successCount", success);
        data.put("skippedCount", skipped);
        data.put("details", details);
        return new ResponseResult<>(200, "batch_depreciated", data);
    }

    @Override
    public ResponseResult<?> listDepreciations(Long assetId) {
        ensureTables();
        if (assetId == null) {
            return new ResponseResult<>(400, "assetId required", null);
        }
        List<FixedAssetDepreciation> rows = fixedAssetDepreciationMapper.selectList(new LambdaQueryWrapper<FixedAssetDepreciation>()
                .eq(FixedAssetDepreciation::getAssetId, assetId)
                .eq(FixedAssetDepreciation::getIsDeleted, 0)
                .orderByDesc(FixedAssetDepreciation::getPeriodMonth)
                .orderByDesc(FixedAssetDepreciation::getId));
        return new ResponseResult<>(200, "OK", rows);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResponseResult<?> disposeAsset(Long id, Map<String, Object> payload) {
        ensureTables();
        if (id == null) {
            return new ResponseResult<>(400, "id required", null);
        }
        FixedAsset asset = fixedAssetMapper.selectOne(new LambdaQueryWrapper<FixedAsset>()
                .eq(FixedAsset::getId, id)
                .eq(FixedAsset::getIsDeleted, 0)
                .last("LIMIT 1"));
        if (asset == null) {
            return new ResponseResult<>(404, "asset not found", null);
        }
        if ("DISPOSED".equals(asset.getStatus())) {
            return new ResponseResult<>(400, "asset already disposed", null);
        }

        String disposeDate = payload == null ? "" : stringValue(payload.get("dispose_date"));
        BigDecimal disposeAmount = payload == null ? null : toDecimal(payload.get("dispose_amount"));
        String remark = payload == null ? "" : stringValue(payload.get("remark"));
        if (!hasText(disposeDate)) {
            disposeDate = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
        }

        asset.setStatus("DISPOSED");
        asset.setDisposeDate(Date.valueOf(disposeDate));
        asset.setDisposeAmount(disposeAmount == null ? BigDecimal.ZERO : scale(disposeAmount));
        asset.setRemark(defaultIfBlank(remark, asset.getRemark()));
        fixedAssetMapper.updateById(asset);

        return new ResponseResult<>(200, "disposed", asset);
    }

    @Override
    public ResponseResult<?> reportSummary(String month) {
        ensureTables();
        String periodMonth = hasText(month) ? month.trim() : LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM"));

        List<FixedAsset> allAssets = fixedAssetMapper.selectList(new LambdaQueryWrapper<FixedAsset>()
                .eq(FixedAsset::getIsDeleted, 0));
        int totalAssets = allAssets.size();
        int activeAssets = 0;
        int disposedAssets = 0;
        BigDecimal totalOriginal = BigDecimal.ZERO;
        BigDecimal totalAccumulated = BigDecimal.ZERO;
        BigDecimal totalNet = BigDecimal.ZERO;
        for (FixedAsset asset : allAssets) {
            if ("DISPOSED".equals(asset.getStatus())) {
                disposedAssets++;
            } else {
                activeAssets++;
            }
            totalOriginal = totalOriginal.add(nvl(asset.getOriginalValue()));
            totalAccumulated = totalAccumulated.add(nvl(asset.getAccumulatedDepreciation()));
            totalNet = totalNet.add(nvl(asset.getNetValue()));
        }

        List<FixedAssetDepreciation> monthRows = fixedAssetDepreciationMapper.selectList(new LambdaQueryWrapper<FixedAssetDepreciation>()
                .eq(FixedAssetDepreciation::getIsDeleted, 0)
                .eq(FixedAssetDepreciation::getPeriodMonth, periodMonth));
        BigDecimal monthDepAmount = BigDecimal.ZERO;
        for (FixedAssetDepreciation row : monthRows) {
            monthDepAmount = monthDepAmount.add(nvl(row.getDepreciationAmount()));
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("month", periodMonth);
        data.put("totalAssets", totalAssets);
        data.put("activeAssets", activeAssets);
        data.put("disposedAssets", disposedAssets);
        data.put("totalOriginalValue", scale(totalOriginal));
        data.put("totalAccumulatedDepreciation", scale(totalAccumulated));
        data.put("totalNetValue", scale(totalNet));
        data.put("monthDepreciationAmount", scale(monthDepAmount));
        data.put("monthDepreciationCount", monthRows.size());
        return new ResponseResult<>(200, "OK", data);
    }

    @Override
    public ResponseResult<?> reportLedger(Map<String, Object> params) {
        ensureTables();
        List<FixedAsset> rows = queryAssets(params, false);
        return new ResponseResult<>(200, "OK", rows);
    }

    @Override
    public void exportReport(Map<String, Object> params, HttpServletResponse response) {
        ensureTables();
        String month = params == null ? "" : stringValue(params.get("month"));
        ResponseResult<?> summaryRes = reportSummary(month);
        @SuppressWarnings("unchecked")
        Map<String, Object> summary = summaryRes != null && summaryRes.getData() instanceof Map
                ? (Map<String, Object>) summaryRes.getData()
                : new HashMap<>();

        List<FixedAsset> ledgerRows = queryAssets(params, false);

        Workbook workbook = new XSSFWorkbook();
        try {
            Sheet summarySheet = workbook.createSheet("汇总");
            int r = 0;
            Row h = summarySheet.createRow(r++);
            h.createCell(0).setCellValue("指标");
            h.createCell(1).setCellValue("值");

            r = writeSummaryRow(summarySheet, r, "月份", summary.get("month"));
            r = writeSummaryRow(summarySheet, r, "资产总数", summary.get("totalAssets"));
            r = writeSummaryRow(summarySheet, r, "在用资产", summary.get("activeAssets"));
            r = writeSummaryRow(summarySheet, r, "已处置资产", summary.get("disposedAssets"));
            r = writeSummaryRow(summarySheet, r, "资产原值合计", summary.get("totalOriginalValue"));
            r = writeSummaryRow(summarySheet, r, "累计折旧合计", summary.get("totalAccumulatedDepreciation"));
            r = writeSummaryRow(summarySheet, r, "净值合计", summary.get("totalNetValue"));
            r = writeSummaryRow(summarySheet, r, "当月折旧金额", summary.get("monthDepreciationAmount"));

            Sheet ledger = workbook.createSheet("台账");
            int i = 0;
            Row hr = ledger.createRow(i++);
            hr.createCell(0).setCellValue("资产编码");
            hr.createCell(1).setCellValue("资产名称");
            hr.createCell(2).setCellValue("分类");
            hr.createCell(3).setCellValue("购置日期");
            hr.createCell(4).setCellValue("原值");
            hr.createCell(5).setCellValue("累计折旧");
            hr.createCell(6).setCellValue("净值");
            hr.createCell(7).setCellValue("寿命(月)");
            hr.createCell(8).setCellValue("状态");
            hr.createCell(9).setCellValue("存放地点");
            hr.createCell(10).setCellValue("责任人");
            hr.createCell(11).setCellValue("处置日期");
            hr.createCell(12).setCellValue("处置金额");
            hr.createCell(13).setCellValue("备注");

            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
            for (FixedAsset a : ledgerRows) {
                Row row = ledger.createRow(i++);
                row.createCell(0).setCellValue(stringValue(a.getAssetCode()));
                row.createCell(1).setCellValue(stringValue(a.getAssetName()));
                row.createCell(2).setCellValue(stringValue(a.getCategory()));
                row.createCell(3).setCellValue(a.getPurchaseDate() == null ? "" : sdf.format(a.getPurchaseDate()));
                row.createCell(4).setCellValue(nvl(a.getOriginalValue()).doubleValue());
                row.createCell(5).setCellValue(nvl(a.getAccumulatedDepreciation()).doubleValue());
                row.createCell(6).setCellValue(nvl(a.getNetValue()).doubleValue());
                row.createCell(7).setCellValue(a.getUsefulLifeMonths() == null ? 0 : a.getUsefulLifeMonths());
                row.createCell(8).setCellValue(stringValue(a.getStatus()));
                row.createCell(9).setCellValue(stringValue(a.getLocation()));
                row.createCell(10).setCellValue(stringValue(a.getResponsiblePerson()));
                row.createCell(11).setCellValue(a.getDisposeDate() == null ? "" : sdf.format(a.getDisposeDate()));
                row.createCell(12).setCellValue(nvl(a.getDisposeAmount()).doubleValue());
                row.createCell(13).setCellValue(stringValue(a.getRemark()));
            }

            for (int c = 0; c <= 13; c++) {
                ledger.autoSizeColumn(c);
            }
            summarySheet.autoSizeColumn(0);
            summarySheet.autoSizeColumn(1);

            String filename = "fixed-assets-report-" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) + ".xlsx";
            String encoded = URLEncoder.encode(filename, StandardCharsets.UTF_8.name()).replaceAll("\\+", "%20");
            response.setCharacterEncoding("UTF-8");
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setHeader("Content-Disposition", "attachment;filename*=UTF-8''" + encoded);
            OutputStream os = response.getOutputStream();
            workbook.write(os);
            os.flush();
        } catch (Exception e) {
            throw new RuntimeException("export fixed asset report failed: " + e.getMessage(), e);
        } finally {
            try {
                workbook.close();
            } catch (Exception ignore) {
            }
        }
    }

    private String stringValue(Object val) {
        return val == null ? "" : String.valueOf(val).trim();
    }

    private boolean hasText(String s) {
        return s != null && !s.trim().isEmpty();
    }

    private String defaultIfBlank(String val, String def) {
        return hasText(val) ? val : def;
    }

    private BigDecimal toDecimal(Object val) {
        if (val == null) return null;
        if (val instanceof BigDecimal) return (BigDecimal) val;
        if (val instanceof Number) return BigDecimal.valueOf(((Number) val).doubleValue());
        try {
            return new BigDecimal(String.valueOf(val).trim());
        } catch (Exception e) {
            return null;
        }
    }

    private Integer toInt(Object val) {
        if (val == null) return null;
        if (val instanceof Number) return ((Number) val).intValue();
        try {
            return Integer.parseInt(String.valueOf(val).trim());
        } catch (Exception e) {
            return null;
        }
    }

    private Long toLong(Object val) {
        if (val == null) return null;
        if (val instanceof Number) return ((Number) val).longValue();
        try {
            return Long.parseLong(String.valueOf(val).trim());
        } catch (Exception e) {
            return null;
        }
    }

    private BigDecimal nvl(BigDecimal val) {
        return val == null ? BigDecimal.ZERO : val;
    }

    private BigDecimal scale(BigDecimal val) {
        return nvl(val).setScale(2, RoundingMode.HALF_UP);
    }

    private FixedAssetDepreciation performDepreciation(FixedAsset asset, String periodMonth, String note) {
        Long assetId = asset.getId();
        FixedAssetDepreciation existing = fixedAssetDepreciationMapper.selectOne(new LambdaQueryWrapper<FixedAssetDepreciation>()
                .eq(FixedAssetDepreciation::getAssetId, assetId)
                .eq(FixedAssetDepreciation::getPeriodMonth, periodMonth)
                .eq(FixedAssetDepreciation::getIsDeleted, 0)
                .last("LIMIT 1"));
        if (existing != null) {
            return null;
        }

        BigDecimal original = nvl(asset.getOriginalValue());
        BigDecimal salvage = nvl(asset.getSalvageValue());
        BigDecimal accumulated = nvl(asset.getAccumulatedDepreciation());
        Integer life = asset.getUsefulLifeMonths() == null ? 0 : asset.getUsefulLifeMonths();
        if (life <= 0) {
            return null;
        }

        BigDecimal depreciableBase = original.subtract(salvage);
        if (depreciableBase.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }

        BigDecimal monthly = depreciableBase.divide(BigDecimal.valueOf(life), 2, RoundingMode.HALF_UP);
        BigDecimal remaining = depreciableBase.subtract(accumulated);
        if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        BigDecimal depreciationAmount = monthly.min(remaining);
        BigDecimal accumulatedAfter = accumulated.add(depreciationAmount);
        BigDecimal netAfter = original.subtract(accumulatedAfter).max(salvage);

        asset.setAccumulatedDepreciation(scale(accumulatedAfter));
        asset.setNetValue(scale(netAfter));
        fixedAssetMapper.updateById(asset);

        FixedAssetDepreciation log = new FixedAssetDepreciation();
        log.setAssetId(assetId);
        log.setPeriodMonth(periodMonth);
        log.setDepreciationAmount(scale(depreciationAmount));
        log.setAccumulatedAfter(scale(accumulatedAfter));
        log.setNetValueAfter(scale(netAfter));
        log.setVoucherNo("FA-DEP-" + periodMonth.replace("-", "") + "-" + assetId);
        log.setNote(note);
        log.setIsDeleted(0);
        fixedAssetDepreciationMapper.insert(log);
        return log;
    }

    private void ensureTables() {
        if (tablesChecked) {
            return;
        }
        synchronized (this) {
            if (tablesChecked) {
                return;
            }
            jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS fixed_asset (" +
                    "id BIGINT AUTO_INCREMENT PRIMARY KEY," +
                    "asset_code VARCHAR(64) NOT NULL," +
                    "asset_name VARCHAR(200) NOT NULL," +
                    "category VARCHAR(64) DEFAULT NULL," +
                    "purchase_date DATE NOT NULL," +
                    "original_value DECIMAL(18,2) NOT NULL," +
                    "salvage_value DECIMAL(18,2) DEFAULT 0," +
                    "useful_life_months INT NOT NULL," +
                    "depreciation_method VARCHAR(32) DEFAULT 'STRAIGHT_LINE'," +
                    "accumulated_depreciation DECIMAL(18,2) DEFAULT 0," +
                    "net_value DECIMAL(18,2) NOT NULL," +
                    "status VARCHAR(32) DEFAULT 'ACTIVE'," +
                    "location VARCHAR(200) DEFAULT NULL," +
                    "responsible_person VARCHAR(128) DEFAULT NULL," +
                    "dispose_date DATE DEFAULT NULL," +
                    "dispose_amount DECIMAL(18,2) DEFAULT NULL," +
                    "remark VARCHAR(500) DEFAULT NULL," +
                    "is_deleted TINYINT(1) DEFAULT 0," +
                    "created_at DATETIME DEFAULT CURRENT_TIMESTAMP," +
                    "updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP," +
                    "UNIQUE KEY uk_fixed_asset_code (asset_code)," +
                    "INDEX idx_fixed_asset_status (status)," +
                    "INDEX idx_fixed_asset_category (category)" +
                    ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");

            jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS fixed_asset_depreciation (" +
                    "id BIGINT AUTO_INCREMENT PRIMARY KEY," +
                    "asset_id BIGINT NOT NULL," +
                    "period_month VARCHAR(7) NOT NULL," +
                    "depreciation_amount DECIMAL(18,2) NOT NULL," +
                    "accumulated_after DECIMAL(18,2) NOT NULL," +
                    "net_value_after DECIMAL(18,2) NOT NULL," +
                    "voucher_no VARCHAR(64) DEFAULT NULL," +
                    "note VARCHAR(500) DEFAULT NULL," +
                    "is_deleted TINYINT(1) DEFAULT 0," +
                    "created_at DATETIME DEFAULT CURRENT_TIMESTAMP," +
                    "UNIQUE KEY uk_asset_period (asset_id, period_month)," +
                    "INDEX idx_fad_asset (asset_id)," +
                    "INDEX idx_fad_period (period_month)" +
                    ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");

            tablesChecked = true;
        }
    }

    private int writeSummaryRow(Sheet sheet, int rowNum, String label, Object value) {
        Row row = sheet.createRow(rowNum);
        row.createCell(0).setCellValue(label);
        row.createCell(1).setCellValue(value == null ? "" : String.valueOf(value));
        return rowNum + 1;
    }

    private List<FixedAsset> queryAssets(Map<String, Object> params, boolean limit500) {
        String keyword = params == null ? "" : stringValue(params.get("keyword"));
        String status = params == null ? "" : stringValue(params.get("status"));

        LambdaQueryWrapper<FixedAsset> qw = new LambdaQueryWrapper<FixedAsset>()
                .eq(FixedAsset::getIsDeleted, 0)
                .orderByDesc(FixedAsset::getUpdatedAt)
                .orderByDesc(FixedAsset::getId);
        if (limit500) {
            qw.last("LIMIT 500");
        }

        if (hasText(status)) {
            qw.eq(FixedAsset::getStatus, status.trim());
        }
        if (hasText(keyword)) {
            String kw = keyword.trim();
            qw.and(w -> w.like(FixedAsset::getAssetCode, kw)
                    .or()
                    .like(FixedAsset::getAssetName, kw)
                    .or()
                    .like(FixedAsset::getCategory, kw)
                    .or()
                    .like(FixedAsset::getResponsiblePerson, kw));
        }
        return fixedAssetMapper.selectList(qw);
    }
}
