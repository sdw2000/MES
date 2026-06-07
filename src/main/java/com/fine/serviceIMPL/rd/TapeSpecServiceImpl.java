package com.fine.serviceIMPL.rd;

import com.fine.Dao.rd.TapeSpecMapper;
import com.fine.Utils.ResponseResult;
import com.fine.modle.rd.DictItem;
import com.fine.modle.rd.TapeSpec;
import com.fine.service.rd.TapeSpecService;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.PostConstruct;
import javax.servlet.http.HttpServletResponse;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.util.*;

/**
 * 胶带规格服务实现
 */
@Service
public class TapeSpecServiceImpl implements TapeSpecService {

    @Autowired
    private TapeSpecMapper tapeSpecMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final DataFormatter dataFormatter = new DataFormatter();

    @PostConstruct
    public void ensureExtraQcColumns() {
        String[] ddls = new String[] {
                "ALTER TABLE tape_spec ADD COLUMN IF NOT EXISTS extra_qc_item1_name VARCHAR(100) NULL COMMENT '扩展检测项目1名称'",
                "ALTER TABLE tape_spec ADD COLUMN IF NOT EXISTS extra_qc_item1_unit VARCHAR(30) NULL COMMENT '扩展检测项目1单位'",
                "ALTER TABLE tape_spec ADD COLUMN IF NOT EXISTS extra_qc_item1_standard VARCHAR(100) NULL COMMENT '扩展检测项目1标准值'",
                "ALTER TABLE tape_spec ADD COLUMN IF NOT EXISTS extra_qc_item2_name VARCHAR(100) NULL COMMENT '扩展检测项目2名称'",
                "ALTER TABLE tape_spec ADD COLUMN IF NOT EXISTS extra_qc_item2_unit VARCHAR(30) NULL COMMENT '扩展检测项目2单位'",
                "ALTER TABLE tape_spec ADD COLUMN IF NOT EXISTS extra_qc_item2_standard VARCHAR(100) NULL COMMENT '扩展检测项目2标准值'",
                "ALTER TABLE tape_spec ADD COLUMN IF NOT EXISTS extra_qc_item3_name VARCHAR(100) NULL COMMENT '扩展检测项目3名称'",
                "ALTER TABLE tape_spec ADD COLUMN IF NOT EXISTS extra_qc_item3_unit VARCHAR(30) NULL COMMENT '扩展检测项目3单位'",
                "ALTER TABLE tape_spec ADD COLUMN IF NOT EXISTS extra_qc_item3_standard VARCHAR(100) NULL COMMENT '扩展检测项目3标准值'",
                "ALTER TABLE tape_spec ADD COLUMN IF NOT EXISTS extra_qc_item4_name VARCHAR(100) NULL COMMENT '扩展检测项目4名称'",
                "ALTER TABLE tape_spec ADD COLUMN IF NOT EXISTS extra_qc_item4_unit VARCHAR(30) NULL COMMENT '扩展检测项目4单位'",
                "ALTER TABLE tape_spec ADD COLUMN IF NOT EXISTS extra_qc_item4_standard VARCHAR(100) NULL COMMENT '扩展检测项目4标准值'"
        };
        for (String ddl : ddls) {
            try {
                jdbcTemplate.execute(Objects.requireNonNull(ddl));
            } catch (Exception ignore) {
            }
        }
    }

    @Override
    public ResponseResult<?> getList(int page, int size, String materialCode, String productName,
                                     String colorCode, String baseMaterial, Integer status,
                                     String sortBy, String sortOrder) {
        int safePage = Math.max(page, 1);
        int safeSize = size <= 0 ? 20 : Math.min(size, 200);
        int offset = (safePage - 1) * safeSize;
        String normalizedBaseMaterial = normalizeBaseMaterialCode(baseMaterial);
        String orderBy = resolveTapeSpecSortColumn(sortBy);
        String direction = "asc".equalsIgnoreCase(sortOrder) ? "ASC" : "DESC";

        List<TapeSpec> list = tapeSpecMapper.selectList(
                materialCode, productName, colorCode, normalizedBaseMaterial, status,
                offset, safeSize, orderBy, direction
        );
        fillMissingColorNameForList(list);
        int total = tapeSpecMapper.selectCount(materialCode, productName, colorCode, normalizedBaseMaterial, status);

        Map<String, Object> result = new HashMap<>();
        result.put("records", list);
        result.put("total", total);
        result.put("page", safePage);
        result.put("size", safeSize);

        return new ResponseResult<>(20000, "查询成功", result);
    }

    @Override
    public ResponseResult<?> getUnproducedStatsPage(int page, int size, String materialCode, String sortBy, String sortOrder) {
        int safePage = Math.max(page, 1);
        int safeSize = size <= 0 ? 20 : Math.min(size, 200);
        int offset = (safePage - 1) * safeSize;
        String keyword = materialCode == null ? null : materialCode.trim();

        String pendingSubSql =
                "SELECT soi.material_code AS material_code, " +
                "ROUND(SUM(CASE " +
                "  WHEN IFNULL(soi.remaining_qty, (soi.rolls - IFNULL(soi.scheduled_qty, 0))) > 0 THEN " +
            "    CASE WHEN IFNULL(soi.width, 0) > 0 AND IFNULL(soi.length, 0) > 0 THEN " +
            "      (soi.width / 1000.0) * soi.length * IFNULL(soi.remaining_qty, (soi.rolls - IFNULL(soi.scheduled_qty, 0))) " +
            "    ELSE 0 END " +
                "  ELSE 0 " +
                "END), 2) AS unproduced_area " +
                "FROM sales_order_items soi " +
                "JOIN sales_orders o ON soi.order_id = o.id " +
                "LEFT JOIN ( " +
                "  SELECT m1.* FROM manual_schedule m1 " +
                "  INNER JOIN (SELECT order_detail_id, MAX(id) AS max_id FROM manual_schedule GROUP BY order_detail_id) m2 " +
                "    ON m1.order_detail_id = m2.order_detail_id AND m1.id = m2.max_id " +
                ") ms ON ms.order_detail_id = soi.id " +
                "WHERE (o.status IS NULL OR LOWER(o.status) NOT IN ('completed','cancelled','canceled','closed')) " +
                "  AND o.is_deleted = 0 AND soi.is_deleted = 0 " +
                "  AND LOWER(IFNULL(soi.production_status, 'not_started')) <> 'completed' " +
                "  AND IFNULL(soi.delivered_qty, 0) < IFNULL(soi.rolls, 0) " +
                "  AND IFNULL(soi.remaining_qty, (soi.rolls - IFNULL(soi.scheduled_qty, 0))) > 0 " +
                "  AND (ms.packaging_date IS NULL AND ms.slitting_schedule_date IS NULL) ";

        List<Object> countArgs = new ArrayList<>();
        if (keyword != null && !keyword.isEmpty()) {
            pendingSubSql += " AND soi.material_code LIKE CONCAT('%', ?, '%') ";
            countArgs.add(keyword);
        }
        pendingSubSql += "GROUP BY soi.material_code";

        String countSql = "SELECT COUNT(1) FROM (" + pendingSubSql + ") p " +
            "LEFT JOIN (SELECT material_code, ROUND(COALESCE(SUM(available_area), 0), 2) AS stock_area FROM tape_stock WHERE status = 1 GROUP BY material_code) s " +
            "  ON CONVERT(s.material_code USING utf8mb4) COLLATE utf8mb4_unicode_ci = CONVERT(p.material_code USING utf8mb4) COLLATE utf8mb4_unicode_ci " +
            "WHERE IFNULL(p.unproduced_area, 0) > IFNULL(s.stock_area, 0)";
        Long totalObj = jdbcTemplate.queryForObject(countSql, Long.class, countArgs.toArray());
        long total = totalObj == null ? 0L : totalObj;

        List<Map<String, Object>> records = Collections.emptyList();
        if (total > 0) {
            String sortColumn = resolveUnproducedSortColumn(sortBy);
            String direction = "desc".equalsIgnoreCase(sortOrder) ? "DESC" : "ASC";

            String dataSql =
                    "SELECT " +
                    "  p.material_code AS materialCode, " +
                    "  COALESCE(NULLIF(ts.product_name, ''), p.material_code) AS productName, " +
                    "  ROUND(IFNULL(p.unproduced_area, 0), 2) AS oweArea, " +
                    "  ROUND(IFNULL(p.unproduced_area, 0), 2) AS unproducedArea, " +
                    "  ROUND(IFNULL(s.stock_area, 0), 2) AS stockArea, " +
                    "  ROUND(GREATEST(IFNULL(p.unproduced_area, 0) - IFNULL(s.stock_area, 0), 0), 2) AS shortageArea, " +
                    "  ROUND(IFNULL(p.unproduced_area, 0) - IFNULL(s.stock_area, 0), 2) AS gapArea " +
                    "FROM (" + pendingSubSql + ") p " +
                    "LEFT JOIN ( " +
                    "  SELECT material_code, COALESCE(MAX(product_name), '') AS product_name " +
                    "  FROM tape_spec GROUP BY material_code " +
                    ") ts ON CONVERT(ts.material_code USING utf8mb4) COLLATE utf8mb4_unicode_ci = " +
                    "       CONVERT(p.material_code USING utf8mb4) COLLATE utf8mb4_unicode_ci " +
                    "LEFT JOIN ( " +
                    "  SELECT material_code, ROUND(COALESCE(SUM(available_area), 0), 2) AS stock_area " +
                    "  FROM tape_stock WHERE status = 1 GROUP BY material_code " +
                    ") s ON CONVERT(s.material_code USING utf8mb4) COLLATE utf8mb4_unicode_ci = " +
                    "      CONVERT(p.material_code USING utf8mb4) COLLATE utf8mb4_unicode_ci " +
                    "WHERE IFNULL(p.unproduced_area, 0) > IFNULL(s.stock_area, 0) " +
                    "ORDER BY " + sortColumn + " " + direction + ", p.material_code ASC " +
                    "LIMIT ?, ?";

            List<Object> dataArgs = new ArrayList<>(countArgs);
            dataArgs.add(offset);
            dataArgs.add(safeSize);
            records = jdbcTemplate.queryForList(dataSql, dataArgs.toArray());
        }

        Map<String, Object> result = new HashMap<>();
        result.put("records", records);
        result.put("total", total);
        result.put("page", safePage);
        result.put("size", safeSize);
        return new ResponseResult<>(20000, "查询成功", result);
    }

    @Override
    public ResponseResult<?> getById(Long id) {
        TapeSpec spec = tapeSpecMapper.selectById(id);
        if (spec == null) {
            return new ResponseResult<>(50000, "规格不存在");
        }
        normalizeBaseMaterialField(spec);
        return new ResponseResult<>(20000, "查询成功", spec);
    }

    @Override
    public ResponseResult<?> getByMaterialCode(String materialCode) {
        TapeSpec spec = tapeSpecMapper.selectByMaterialCode(materialCode);
        if (spec == null) {
            return new ResponseResult<>(50000, "料号不存在");
        }
        normalizeBaseMaterialField(spec);
        return new ResponseResult<>(20000, "查询成功", spec);
    }

    @Override
    public ResponseResult<?> suggestByMaterialCode(String keyword, Integer limit) {
        String normalized = keyword == null ? "" : keyword.trim().toUpperCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            return new ResponseResult<>(20000, "查询成功", Collections.emptyList());
        }
        int safeLimit = (limit == null || limit <= 0) ? 5 : Math.min(limit, 5);
        List<TapeSpec> list = tapeSpecMapper.selectTopByMaterialCodePrefix(normalized, safeLimit);
        fillMissingColorNameForList(list);
        return new ResponseResult<>(20000, "查询成功", list == null ? Collections.emptyList() : list);
    }

    @Override
    public ResponseResult<?> create(TapeSpec spec, String operator) {
        // 检查料号是否重复
        if (tapeSpecMapper.checkMaterialCodeExists(spec.getMaterialCode(), 0L) > 0) {
            return new ResponseResult<>(50000, "料号已存在");
        }

        normalizeBaseMaterialField(spec);
        normalizeColorFields(spec, buildColorDictMap());
        spec.setStatus(spec.getStatus() == null ? 1 : spec.getStatus());
        spec.setCreateBy(operator);
        tapeSpecMapper.insert(spec);

        return new ResponseResult<>(20000, "创建成功", spec);
    }

    @Override
    public ResponseResult<?> update(TapeSpec spec, String operator) {
        if (spec.getId() == null) {
            return new ResponseResult<>(50000, "ID不能为空");
        }

        // 检查料号是否重复（排除自己）
        if (tapeSpecMapper.checkMaterialCodeExists(spec.getMaterialCode(), spec.getId()) > 0) {
            return new ResponseResult<>(50000, "料号已存在");
        }

        normalizeBaseMaterialField(spec);
        normalizeColorFields(spec, buildColorDictMap());
        spec.setUpdateBy(operator);
        tapeSpecMapper.update(spec);

        return new ResponseResult<>(20000, "更新成功");
    }

    @Override
    public ResponseResult<?> delete(Long id) {
        int rows = tapeSpecMapper.deleteById(id);
        if (rows == 0) {
            return new ResponseResult<>(50000, "删除失败，记录不存在");
        }
        return new ResponseResult<>(20000, "删除成功");
    }

    @Override
    public ResponseResult<?> getAllEnabled() {
        List<TapeSpec> list = tapeSpecMapper.selectAllEnabled();
        return new ResponseResult<>(20000, "查询成功", list);
    }

    @Override
    public ResponseResult<?> getColorDict() {
        List<DictItem> list = tapeSpecMapper.selectColorDict();
        return new ResponseResult<>(20000, "查询成功", list);
    }

    @Override
    public ResponseResult<?> getColorDictList(String keyword, Integer status, int page, int size) {
        int safePage = Math.max(page, 1);
        int safeSize = size <= 0 ? 20 : Math.min(size, 200);
        int offset = (safePage - 1) * safeSize;
        List<DictItem> list = tapeSpecMapper.selectColorDictAllPaged(keyword, status, offset, safeSize);
        int total = tapeSpecMapper.selectColorDictAllCount(keyword, status);

        Map<String, Object> result = new HashMap<>();
        result.put("records", list == null ? Collections.emptyList() : list);
        result.put("total", total);
        result.put("page", safePage);
        result.put("size", safeSize);
        return new ResponseResult<>(20000, "查询成功", result);
    }

    @Override
    public ResponseResult<?> createColorDict(DictItem item, String operator) {
        if (item == null || item.getCode() == null || item.getCode().trim().isEmpty()) {
            return new ResponseResult<>(50000, "颜色代码不能为空");
        }
        if (item.getName() == null || item.getName().trim().isEmpty()) {
            return new ResponseResult<>(50000, "颜色名称不能为空");
        }

        String code = item.getCode().trim().toUpperCase(Locale.ROOT);
        item.setCode(code);
        item.setName(item.getName().trim());
        if (item.getStatus() == null) {
            item.setStatus(1);
        }
        if (tapeSpecMapper.checkColorCodeExistsInDict(code, 0L) > 0) {
            return new ResponseResult<>(50000, "颜色代码已存在");
        }
        tapeSpecMapper.insertColorDict(item);
        return new ResponseResult<>(20000, "创建成功", item);
    }

    @Override
    public ResponseResult<?> updateColorDict(DictItem item, String operator) {
        if (item == null || item.getId() == null) {
            return new ResponseResult<>(50000, "ID不能为空");
        }
        if (item.getCode() == null || item.getCode().trim().isEmpty()) {
            return new ResponseResult<>(50000, "颜色代码不能为空");
        }
        if (item.getName() == null || item.getName().trim().isEmpty()) {
            return new ResponseResult<>(50000, "颜色名称不能为空");
        }

        String code = item.getCode().trim().toUpperCase(Locale.ROOT);
        item.setCode(code);
        item.setName(item.getName().trim());
        if (tapeSpecMapper.checkColorCodeExistsInDict(code, item.getId()) > 0) {
            return new ResponseResult<>(50000, "颜色代码已存在");
        }
        int rows = tapeSpecMapper.updateColorDict(item);
        if (rows <= 0) {
            return new ResponseResult<>(50000, "更新失败，记录不存在");
        }
        return new ResponseResult<>(20000, "更新成功");
    }

    @Override
    public ResponseResult<?> deleteColorDict(Long id) {
        if (id == null) {
            return new ResponseResult<>(50000, "ID不能为空");
        }
        int rows = tapeSpecMapper.deleteColorDictById(id);
        if (rows <= 0) {
            return new ResponseResult<>(50000, "删除失败，记录不存在");
        }
        return new ResponseResult<>(20000, "删除成功");
    }

    @Override
    public ResponseResult<?> getBaseMaterialDict() {
        List<DictItem> list = tapeSpecMapper.selectMaterialDict("base");
        return new ResponseResult<>(20000, "查询成功", list);
    }

    @Override
    public ResponseResult<?> getGlueMaterialDict() {
        List<DictItem> list = tapeSpecMapper.selectMaterialDict("glue");
        return new ResponseResult<>(20000, "查询成功", list);
    }

    @Override
    public void exportExcel(HttpServletResponse response, String materialCode, String productName,
                            String colorCode, String baseMaterial) {
        try {
            List<TapeSpec> list = tapeSpecMapper.selectList(materialCode, productName, colorCode, baseMaterial, null, 0, 10000, "create_time", "DESC");
            fillMissingColorNameForList(list);

            Workbook workbook = new XSSFWorkbook();
            Sheet sheet = workbook.createSheet("胶带规格");

            // 表头
                String[] headers = {"序号", "产品名称", "胶带料号", "颜色代码", "颜色名称", "基材厚度/μm", "基材材质",
                    "胶水材质", "胶水厚度/μm", "初粘/#", "总厚度/μm", "厚度波动/μm",
                    "剥离力/N/25mm", "解卷力/N/25mm", "耐温/℃/0.5H",
                    "扩展项目1", "扩展单位1", "扩展标准1",
                    "扩展项目2", "扩展单位2", "扩展标准2",
                    "扩展项目3", "扩展单位3", "扩展标准3",
                    "扩展项目4", "扩展单位4", "扩展标准4",
                    "状态"};

            Row headerRow = sheet.createRow(0);
            CellStyle headerStyle = workbook.createCellStyle();
            headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);

            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
                sheet.setColumnWidth(i, 4000);
            }

            // 数据行
            int rowNum = 1;
            for (TapeSpec spec : list) {
                Row row = sheet.createRow(rowNum);
                row.createCell(0).setCellValue(rowNum);
                row.createCell(1).setCellValue(spec.getProductName() != null ? spec.getProductName() : "");
                row.createCell(2).setCellValue(spec.getMaterialCode() != null ? spec.getMaterialCode() : "");
                row.createCell(3).setCellValue(spec.getColorCode() != null ? spec.getColorCode() : "");
                row.createCell(4).setCellValue(spec.getColorName() != null ? spec.getColorName() : "");
                row.createCell(5).setCellValue(spec.getBaseThickness() != null ? spec.getBaseThickness().doubleValue() : 0);
                row.createCell(6).setCellValue(spec.getBaseMaterial() != null ? spec.getBaseMaterial() : "");
                row.createCell(7).setCellValue(spec.getGlueMaterial() != null ? spec.getGlueMaterial() : "");
                row.createCell(8).setCellValue(spec.getGlueThickness() != null ? spec.getGlueThickness().doubleValue() : 0);
                row.createCell(9).setCellValue(spec.getInitialTackDisplay());
                row.createCell(10).setCellValue(spec.getTotalThickness() != null ? spec.getTotalThickness().doubleValue() : 0);
                row.createCell(11).setCellValue(spec.getThicknessRangeDisplay());
                row.createCell(12).setCellValue(spec.getPeelStrengthDisplay());
                row.createCell(13).setCellValue(spec.getUnwindForceDisplay());
                row.createCell(14).setCellValue(spec.getHeatResistanceDisplay());
                row.createCell(15).setCellValue(spec.getExtraQcItem1Name() != null ? spec.getExtraQcItem1Name() : "");
                row.createCell(16).setCellValue(spec.getExtraQcItem1Unit() != null ? spec.getExtraQcItem1Unit() : "");
                row.createCell(17).setCellValue(spec.getExtraQcItem1Standard() != null ? spec.getExtraQcItem1Standard() : "");
                row.createCell(18).setCellValue(spec.getExtraQcItem2Name() != null ? spec.getExtraQcItem2Name() : "");
                row.createCell(19).setCellValue(spec.getExtraQcItem2Unit() != null ? spec.getExtraQcItem2Unit() : "");
                row.createCell(20).setCellValue(spec.getExtraQcItem2Standard() != null ? spec.getExtraQcItem2Standard() : "");
                row.createCell(21).setCellValue(spec.getExtraQcItem3Name() != null ? spec.getExtraQcItem3Name() : "");
                row.createCell(22).setCellValue(spec.getExtraQcItem3Unit() != null ? spec.getExtraQcItem3Unit() : "");
                row.createCell(23).setCellValue(spec.getExtraQcItem3Standard() != null ? spec.getExtraQcItem3Standard() : "");
                row.createCell(24).setCellValue(spec.getExtraQcItem4Name() != null ? spec.getExtraQcItem4Name() : "");
                row.createCell(25).setCellValue(spec.getExtraQcItem4Unit() != null ? spec.getExtraQcItem4Unit() : "");
                row.createCell(26).setCellValue(spec.getExtraQcItem4Standard() != null ? spec.getExtraQcItem4Standard() : "");
                row.createCell(27).setCellValue(spec.getStatus() == 1 ? "启用" : "禁用");
                rowNum++;
            }

            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setHeader("Content-Disposition", "attachment;filename=" +
                    URLEncoder.encode("胶带规格数据.xlsx", "UTF-8"));

            OutputStream out = response.getOutputStream();
            workbook.write(out);
            out.flush();
            out.close();
            workbook.close();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public ResponseResult<?> importExcel(MultipartFile file, String operator) {
        List<String> errors = new ArrayList<>();
        int successCount = 0;
        int failCount = 0;

        try {
            Workbook workbook = WorkbookFactory.create(file.getInputStream());
            Sheet sheet = workbook.getSheetAt(0);
            Map<String, String> colorDictMap = buildColorDictMap();

            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;

                try {
                    TapeSpec spec = new TapeSpec();
                    spec.setProductName(getCellStringValue(row.getCell(1)));
                    spec.setMaterialCode(getCellStringValue(row.getCell(2)));
                    spec.setColorCode(getCellStringValue(row.getCell(3)));
                    spec.setColorName(getCellStringValue(row.getCell(4)));
                    normalizeColorFields(spec, colorDictMap);
                    spec.setBaseThickness(getCellDecimalValue(row.getCell(5)));
                    spec.setBaseMaterial(getCellStringValue(row.getCell(6)));
                    normalizeBaseMaterialField(spec);
                    spec.setGlueMaterial(getCellStringValue(row.getCell(7)));
                    spec.setGlueThickness(getCellDecimalValue(row.getCell(8)));

                    // 解析初粘（支持范围值如"2~6"、"≤4"、"≥3"）
                    parseRangeValue(getCellStringValue(row.getCell(9)), spec, "initialTack");

                    spec.setTotalThickness(getCellDecimalValue(row.getCell(10)));

                    // 解析厚度波动范围
                    parseThicknessRange(getCellStringValue(row.getCell(11)), spec);

                    // 解析剥离力
                    parseRangeValue(getCellStringValue(row.getCell(12)), spec, "peelStrength");

                    // 解析解卷力
                    parseRangeValue(getCellStringValue(row.getCell(13)), spec, "unwindForce");

                    // 解析耐温
                    parseRangeValue(getCellStringValue(row.getCell(14)), spec, "heatResistance");

                    // 扩展检测项目（名称/单位/标准值）
                    spec.setExtraQcItem1Name(getCellStringValue(row.getCell(15)));
                    spec.setExtraQcItem1Unit(getCellStringValue(row.getCell(16)));
                    spec.setExtraQcItem1Standard(getCellStringValue(row.getCell(17)));
                    spec.setExtraQcItem2Name(getCellStringValue(row.getCell(18)));
                    spec.setExtraQcItem2Unit(getCellStringValue(row.getCell(19)));
                    spec.setExtraQcItem2Standard(getCellStringValue(row.getCell(20)));
                    spec.setExtraQcItem3Name(getCellStringValue(row.getCell(21)));
                    spec.setExtraQcItem3Unit(getCellStringValue(row.getCell(22)));
                    spec.setExtraQcItem3Standard(getCellStringValue(row.getCell(23)));
                    spec.setExtraQcItem4Name(getCellStringValue(row.getCell(24)));
                    spec.setExtraQcItem4Unit(getCellStringValue(row.getCell(25)));
                    spec.setExtraQcItem4Standard(getCellStringValue(row.getCell(26)));

                    String statusText = getCellStringValue(row.getCell(27));
                    if ("禁用".equals(statusText) || "0".equals(statusText)) {
                        spec.setStatus(0);
                    } else {
                        spec.setStatus(1);
                    }
                    spec.setCreateBy(operator);

                    if (spec.getMaterialCode() == null || spec.getMaterialCode().isEmpty()) {
                        errors.add("第" + (i + 1) + "行：料号不能为空");
                        failCount++;
                        continue;
                    }

                    // 检查是否存在，存在则更新
                    TapeSpec existing = tapeSpecMapper.selectByMaterialCode(spec.getMaterialCode());
                    if (existing != null) {
                        spec.setId(existing.getId());
                        spec.setUpdateBy(operator);
                        tapeSpecMapper.update(spec);
                    } else {
                        tapeSpecMapper.insert(spec);
                    }
                    successCount++;

                } catch (Exception e) {
                    String message = e.getMessage();
                    if (message == null || message.trim().isEmpty()) {
                        message = e.getClass().getSimpleName();
                    }
                    String materialCode = getCellStringValue(row.getCell(2));
                    String productName = getCellStringValue(row.getCell(1));
                    errors.add("第" + (i + 1) + "行（料号=" + safeText(materialCode) + "，产品=" + safeText(productName) + "）：" + message);
                    failCount++;
                }
            }

            workbook.close();

        } catch (Exception e) {
            return new ResponseResult<>(50000, "导入失败：" + e.getMessage());
        }

        Map<String, Object> result = new HashMap<>();
        result.put("successCount", successCount);
        result.put("failCount", failCount);
        result.put("errors", errors);

        return new ResponseResult<>(20000, "导入完成", result);
    }

    @Override
    public void downloadTemplate(HttpServletResponse response) {
        try {
            Workbook workbook = new XSSFWorkbook();
            Sheet sheet = workbook.createSheet("胶带规格导入模板");

                String[] headers = {"序号", "产品名称", "胶带料号", "颜色代码", "颜色名称", "基材厚度/μm", "基材材质",
                    "胶水材质", "胶水厚度/μm", "初粘/#", "总厚度/μm", "厚度波动/μm",
                    "剥离力/N/25mm", "解卷力/N/25mm", "耐温/℃/0.5H",
                    "扩展项目1", "扩展单位1", "扩展标准1",
                    "扩展项目2", "扩展单位2", "扩展标准2",
                    "扩展项目3", "扩展单位3", "扩展标准3",
                    "扩展项目4", "扩展单位4", "扩展标准4",
                    "状态"};

            Row headerRow = sheet.createRow(0);
            CellStyle headerStyle = workbook.createCellStyle();
            headerStyle.setFillForegroundColor(IndexedColors.LIGHT_BLUE.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerFont.setColor(IndexedColors.WHITE.getIndex());
            headerStyle.setFont(headerFont);

            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
                sheet.setColumnWidth(i, 4500);
            }

            // 示例数据行
            Row sampleRow = sheet.createRow(1);
            sampleRow.createCell(0).setCellValue(1);
            sampleRow.createCell(1).setCellValue("12μ无机翠绿PET胶带");
            sampleRow.createCell(2).setCellValue("1011-R02-0903-G03-0300");
            sampleRow.createCell(3).setCellValue("G03");
            sampleRow.createCell(4).setCellValue("翠绿");
            sampleRow.createCell(5).setCellValue(9);
            sampleRow.createCell(6).setCellValue("PET");
            sampleRow.createCell(7).setCellValue("亚克力");
            sampleRow.createCell(8).setCellValue(3);
            sampleRow.createCell(9).setCellValue("2~6");
            sampleRow.createCell(10).setCellValue(12);
            sampleRow.createCell(11).setCellValue("10~14");
            sampleRow.createCell(12).setCellValue("2~4.5");
            sampleRow.createCell(13).setCellValue("0.5~1.5");
            sampleRow.createCell(14).setCellValue("≥110");
            sampleRow.createCell(15).setCellValue("附着力");
            sampleRow.createCell(16).setCellValue("N");
            sampleRow.createCell(17).setCellValue("≥3.5");
            sampleRow.createCell(18).setCellValue("外观");
            sampleRow.createCell(19).setCellValue("-");
            sampleRow.createCell(20).setCellValue("无气泡");
            sampleRow.createCell(21).setCellValue("");
            sampleRow.createCell(22).setCellValue("");
            sampleRow.createCell(23).setCellValue("");
            sampleRow.createCell(24).setCellValue("");
            sampleRow.createCell(25).setCellValue("");
            sampleRow.createCell(26).setCellValue("");
            sampleRow.createCell(27).setCellValue("启用");

            // 说明行
            Row noteRow = sheet.createRow(3);
            noteRow.createCell(0).setCellValue("说明：");
            noteRow.createCell(1).setCellValue("范围值格式：2~6（范围）、≤4（小于等于）、≥3（大于等于）");

            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setHeader("Content-Disposition", "attachment;filename=" +
                    URLEncoder.encode("胶带规格导入模板.xlsx", "UTF-8"));

            OutputStream out = response.getOutputStream();
            workbook.write(out);
            out.flush();
            out.close();
            workbook.close();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public ResponseResult<?> checkQuality(String materialCode, String paramName, BigDecimal measuredValue) {
        TapeSpec spec = tapeSpecMapper.selectByMaterialCode(materialCode);
        if (spec == null) {
            return new ResponseResult<>(50000, "料号不存在");
        }

        Map<String, Object> result = new HashMap<>();
        result.put("materialCode", materialCode);
        result.put("paramName", paramName);
        result.put("measuredValue", measuredValue);

        BigDecimal min = null, max = null;
        String type = "range";
        String paramLabel = "";

        switch (paramName) {
            case "totalThickness":
                min = spec.getTotalThicknessMin();
                max = spec.getTotalThicknessMax();
                paramLabel = "总厚度";
                break;
            case "peelStrength":
                min = spec.getPeelStrengthMin();
                max = spec.getPeelStrengthMax();
                type = spec.getPeelStrengthType();
                paramLabel = "剥离力";
                break;
            case "unwindForce":
                min = spec.getUnwindForceMin();
                max = spec.getUnwindForceMax();
                type = spec.getUnwindForceType();
                paramLabel = "解卷力";
                break;
            case "heatResistance":
                min = spec.getHeatResistance();
                type = spec.getHeatResistanceType();
                paramLabel = "耐温";
                break;
            case "initialTack":
                min = spec.getInitialTackMin();
                max = spec.getInitialTackMax();
                type = spec.getInitialTackType();
                paramLabel = "初粘";
                break;
            default:
                return new ResponseResult<>(50000, "未知参数：" + paramName);
        }

        boolean pass = checkRange(measuredValue, min, max, type);
        result.put("pass", pass);
        result.put("paramLabel", paramLabel);
        result.put("specMin", min);
        result.put("specMax", max);
        result.put("specType", type);
        result.put("message", pass ? "合格" : "不合格");

        return new ResponseResult<>(20000, "校验完成", result);
    }

    // ========== 私有辅助方法 ==========

    private boolean checkRange(BigDecimal value, BigDecimal min, BigDecimal max, String type) {
        if (value == null) return false;
        if (type == null) type = "range";

        switch (type) {
            case "gte":
                return min == null || value.compareTo(min) >= 0;
            case "lte":
                return max == null || value.compareTo(max) <= 0;
            case "range":
            default:
                boolean minOk = min == null || value.compareTo(min) >= 0;
                boolean maxOk = max == null || value.compareTo(max) <= 0;
                return minOk && maxOk;
        }
    }

    private Map<String, String> buildColorDictMap() {
        List<DictItem> dictItems = tapeSpecMapper.selectColorDictAll(null, 1);
        Map<String, String> map = new HashMap<>();
        if (dictItems == null || dictItems.isEmpty()) {
            return map;
        }
        for (DictItem item : dictItems) {
            if (item == null || item.getCode() == null) {
                continue;
            }
            String code = item.getCode().trim().toUpperCase(Locale.ROOT);
            if (code.isEmpty()) {
                continue;
            }
            String name = item.getName() == null ? null : item.getName().trim();
            if (name != null && !name.isEmpty()) {
                map.put(code, name);
            }
        }
        return map;
    }

    private void normalizeColorFields(TapeSpec spec, Map<String, String> colorDictMap) {
        if (spec == null) {
            return;
        }
        String colorCode = spec.getColorCode() == null ? null : spec.getColorCode().trim();
        if (colorCode == null || colorCode.isEmpty()) {
            return;
        }
        colorCode = colorCode.toUpperCase(Locale.ROOT);
        spec.setColorCode(colorCode);

        String currentName = spec.getColorName() == null ? null : spec.getColorName().trim();
        if (currentName == null || currentName.isEmpty() || currentName.equalsIgnoreCase(colorCode)) {
            String mapped = colorDictMap == null ? null : colorDictMap.get(colorCode);
            if (mapped != null && !mapped.trim().isEmpty()) {
                spec.setColorName(mapped.trim());
            }
        } else {
            spec.setColorName(currentName);
        }
    }

    private void fillMissingColorNameForList(List<TapeSpec> list) {
        if (list == null || list.isEmpty()) {
            return;
        }
        Map<String, String> colorDictMap = buildColorDictMap();
        for (TapeSpec spec : list) {
            normalizeBaseMaterialField(spec);
            normalizeColorFields(spec, colorDictMap);
        }
    }

    private void normalizeBaseMaterialField(TapeSpec spec) {
        if (spec == null) {
            return;
        }
        spec.setBaseMaterial(normalizeBaseMaterialCode(spec.getBaseMaterial()));
    }

    private String normalizeBaseMaterialCode(String raw) {
        if (raw == null) {
            return null;
        }
        String value = raw.trim();
        if (value.isEmpty()) {
            return value;
        }

        String upper = value.toUpperCase(Locale.ROOT)
                .replace(" ", "")
                .replace("-", "")
                .replace("_", "");

        if ("PI聚酰亚胺".equals(value) || "聚酰亚胺".equals(value) || "PI".equals(upper)) return "PI";
        if ("PP聚丙烯".equals(value) || "聚丙烯".equals(value) || "PP".equals(upper)) return "PP";

        if (value.contains("泡棉") || "PEFOAM".equals(upper)) return "PEFOAM";
        if (value.contains("美纹纸") || value.contains("离型纸") || value.contains("TISSUE")) return "TISSUE";
        if (value.contains("玻纤布") || value.contains("玻璃纤维") || "FIBERGLASS".equals(upper) || "GLASSFIBER".equals(upper)) return "FIBERGLASS";

        if (value.contains("PET") || "PET".equals(upper)) return "PET";
        if (value.contains("BOPP") || "BOPP".equals(upper)) return "BOPP";
        if (value.contains("OPP") || "OPP".equals(upper)) return "OPP";
        if (value.contains("CPP") || "CPP".equals(upper)) return "CPP";
        if (value.contains("OPS") || "OPS".equals(upper)) return "OPS";
        if (value.contains("PVC") || "PVC".equals(upper)) return "PVC";
        if (value.contains("TPU") || "TPU".equals(upper)) return "TPU";

        return value.toUpperCase(Locale.ROOT);
    }

    private String getCellStringValue(Cell cell) {
        if (cell == null) return null;
        String value = dataFormatter.formatCellValue(cell);
        if (value == null) {
            return null;
        }
        value = value.trim();
        return value.isEmpty() ? null : value;
    }

    private BigDecimal getCellDecimalValue(Cell cell) {
        String raw = getCellStringValue(cell);
        return parseFlexibleNumber(raw, null, null);
    }

    /**
     * 解析范围值字符串（如"2~6"、"≤4"、"≥3"）
     */
    private void parseRangeValue(String value, TapeSpec spec, String field) {
        if (value == null || value.isEmpty()) return;

        value = normalizeComparisonSymbols(value.trim());
        BigDecimal min = null, max = null;
        String type = "range";

        if (value.startsWith("≤") || value.startsWith("<=") || value.startsWith("<")) {
            type = "lte";
            max = parseFlexibleNumber(value.replace("≤", "").replace("<=", "").replace("<", "").trim(), field, "最大值");
        } else if (value.startsWith("≥") || value.startsWith(">=") || value.startsWith(">")) {
            type = "gte";
            min = parseFlexibleNumber(value.replace("≥", "").replace(">=", "").replace(">", "").trim(), field, "最小值");
        } else if (value.contains("~") || value.contains("～") || value.contains("-")) {
            String[] parts = value.split("[~～\\-]");
            if (parts.length == 2) {
                min = parseFlexibleNumber(parts[0].trim(), field, "最小值");
                max = parseFlexibleNumber(parts[1].trim(), field, "最大值");
            } else {
                throw new IllegalArgumentException(getFieldLabel(field) + "格式错误，示例：2~6 或 ≤4 或 ≥3");
            }
        } else {
            // 单值
            min = parseFlexibleNumber(value, field, "值");
            max = min;
        }

        switch (field) {
            case "initialTack":
                spec.setInitialTackMin(min);
                spec.setInitialTackMax(max);
                spec.setInitialTackType(type);
                break;
            case "peelStrength":
                spec.setPeelStrengthMin(min);
                spec.setPeelStrengthMax(max);
                spec.setPeelStrengthType(type);
                break;
            case "unwindForce":
                spec.setUnwindForceMin(min);
                spec.setUnwindForceMax(max);
                spec.setUnwindForceType(type);
                break;
            case "heatResistance":
                spec.setHeatResistance(min);
                spec.setHeatResistanceType(type);
                break;
        }
    }

    /**
     * 解析厚度波动范围（如"10~14"）
     */
    private void parseThicknessRange(String value, TapeSpec spec) {
        if (value == null || value.isEmpty()) return;
        value = value.trim();

        if (value.contains("~") || value.contains("～") || value.contains("-")) {
            String[] parts = value.split("[~～\\-]");
            if (parts.length == 2) {
                spec.setTotalThicknessMin(parseFlexibleNumber(parts[0].trim(), "totalThickness", "最小值"));
                spec.setTotalThicknessMax(parseFlexibleNumber(parts[1].trim(), "totalThickness", "最大值"));
            } else {
                throw new IllegalArgumentException("厚度波动格式错误，示例：10~14");
            }
        } else {
            throw new IllegalArgumentException("厚度波动格式错误，示例：10~14");
        }
    }

    private BigDecimal parseFlexibleNumber(String value, String field, String part) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        String normalized = value.trim()
                .replace("μ", "")
                .replace("µ", "")
                .replace("℃", "")
                .replace("°C", "")
                .replace("°", "")
                .replace("N/25mm", "")
                .replace("n/25mm", "")
                .replace("#", "")
                .replace("g", "")
                .replace("G", "")
                .replace("，", "")
                .replace(",", "")
                .trim();
        try {
            return new BigDecimal(normalized);
        } catch (Exception e) {
            String label = getFieldLabel(field);
            String suffix = (part == null || part.isEmpty()) ? "" : part;
            throw new IllegalArgumentException(label + suffix + "不是有效数字：" + value);
        }
    }

    private String getFieldLabel(String field) {
        if (field == null) {
            return "数值";
        }
        switch (field) {
            case "initialTack":
                return "初粘";
            case "peelStrength":
                return "剥离力";
            case "unwindForce":
                return "解卷力";
            case "heatResistance":
                return "耐温";
            case "totalThickness":
                return "厚度波动";
            default:
                return "数值";
        }
    }

    private String safeText(String value) {
        return (value == null || value.trim().isEmpty()) ? "空" : value;
    }

    private String normalizeComparisonSymbols(String value) {
        return value
                .replace("＞", ">")
                .replace("＜", "<")
                .replace("≥", ">=")
                .replace("≤", "<=");
    }

    private String resolveUnproducedSortColumn(String sortBy) {
        if (sortBy == null || sortBy.trim().isEmpty()) {
            return "p.unproduced_area";
        }
        String key = sortBy.trim();
        if ("materialCode".equalsIgnoreCase(key) || "material_code".equalsIgnoreCase(key)) {
            return "p.material_code";
        }
        if ("stockArea".equalsIgnoreCase(key) || "stock_area".equalsIgnoreCase(key)) {
            return "s.stock_area";
        }
        if ("oweArea".equalsIgnoreCase(key) || "owe_area".equalsIgnoreCase(key)
                || "unproducedArea".equalsIgnoreCase(key) || "unproduced_area".equalsIgnoreCase(key)) {
            return "p.unproduced_area";
        }
        if ("shortageArea".equalsIgnoreCase(key) || "shortage_area".equalsIgnoreCase(key)) {
            return "(GREATEST(IFNULL(p.unproduced_area, 0) - IFNULL(s.stock_area, 0), 0))";
        }
        if ("gapArea".equalsIgnoreCase(key) || "gap_area".equalsIgnoreCase(key)) {
            return "(IFNULL(p.unproduced_area, 0) - IFNULL(s.stock_area, 0))";
        }
        return "p.unproduced_area";
    }

    private String resolveTapeSpecSortColumn(String sortBy) {
        if (sortBy == null || sortBy.trim().isEmpty()) {
            return "create_time";
        }
        String key = sortBy.trim();
        if ("materialCode".equalsIgnoreCase(key) || "material_code".equalsIgnoreCase(key)) return "material_code";
        if ("productName".equalsIgnoreCase(key) || "product_name".equalsIgnoreCase(key)) return "product_name";
        if ("colorCode".equalsIgnoreCase(key) || "color_code".equalsIgnoreCase(key)) return "color_code";
        if ("baseThickness".equalsIgnoreCase(key) || "base_thickness".equalsIgnoreCase(key)) return "base_thickness";
        if ("baseMaterial".equalsIgnoreCase(key) || "base_material".equalsIgnoreCase(key)) return "base_material";
        if ("glueMaterial".equalsIgnoreCase(key) || "glue_material".equalsIgnoreCase(key)) return "glue_material";
        if ("glueThickness".equalsIgnoreCase(key) || "glue_thickness".equalsIgnoreCase(key)) return "glue_thickness";
        if ("totalThickness".equalsIgnoreCase(key) || "total_thickness".equalsIgnoreCase(key)) return "total_thickness";
        if ("totalThicknessMin".equalsIgnoreCase(key) || "total_thickness_min".equalsIgnoreCase(key)) return "total_thickness_min";
        if ("initialTackMin".equalsIgnoreCase(key) || "initial_tack_min".equalsIgnoreCase(key)) return "initial_tack_min";
        if ("peelStrengthMin".equalsIgnoreCase(key) || "peel_strength_min".equalsIgnoreCase(key)) return "peel_strength_min";
        if ("extraQcItem3Standard".equalsIgnoreCase(key) || "extra_qc_item3_standard".equalsIgnoreCase(key)) return "extra_qc_item3_standard";
        if ("status".equalsIgnoreCase(key)) return "status";
        if ("updateTime".equalsIgnoreCase(key) || "update_time".equalsIgnoreCase(key)) return "update_time";
        if ("createTime".equalsIgnoreCase(key) || "create_time".equalsIgnoreCase(key)) return "create_time";
        return "create_time";
    }
}
