package com.fine.serviceIMPL.rd;

import com.fine.Dao.rd.TapeSpecMapper;
import com.fine.Utils.ResponseResult;
import com.fine.modle.rd.DictItem;
import com.fine.modle.rd.TapeSpec;
import com.fine.service.rd.TapeSpecService;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

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

    private final DataFormatter dataFormatter = new DataFormatter();

    @Override
    public ResponseResult<?> getList(int page, int size, String materialCode, String productName,
                                     String colorCode, String baseMaterial, Integer status) {
        int offset = (page - 1) * size;
        List<TapeSpec> list = tapeSpecMapper.selectList(materialCode, productName, colorCode, baseMaterial, status, offset, size);
        int total = tapeSpecMapper.selectCount(materialCode, productName, colorCode, baseMaterial, status);

        Map<String, Object> result = new HashMap<>();
        result.put("records", list);
        result.put("total", total);
        result.put("page", page);
        result.put("size", size);

        return new ResponseResult<>(20000, "查询成功", result);
    }

    @Override
    public ResponseResult<?> getById(Long id) {
        TapeSpec spec = tapeSpecMapper.selectById(id);
        if (spec == null) {
            return new ResponseResult<>(50000, "规格不存在");
        }
        return new ResponseResult<>(20000, "查询成功", spec);
    }

    @Override
    public ResponseResult<?> getByMaterialCode(String materialCode) {
        TapeSpec spec = tapeSpecMapper.selectByMaterialCode(materialCode);
        if (spec == null) {
            return new ResponseResult<>(50000, "料号不存在");
        }
        return new ResponseResult<>(20000, "查询成功", spec);
    }

    @Override
    public ResponseResult<?> create(TapeSpec spec, String operator) {
        // 检查料号是否重复
        if (tapeSpecMapper.checkMaterialCodeExists(spec.getMaterialCode(), 0L) > 0) {
            return new ResponseResult<>(50000, "料号已存在");
        }

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
            List<TapeSpec> list = tapeSpecMapper.selectList(materialCode, productName, colorCode, baseMaterial, null, 0, 10000);

            Workbook workbook = new XSSFWorkbook();
            Sheet sheet = workbook.createSheet("胶带规格");

            // 表头
                String[] headers = {"序号", "产品名称", "胶带料号", "颜色代码", "颜色名称", "基材厚度/μm", "基材材质",
                    "胶水材质", "胶水厚度/μm", "初粘/#", "总厚度/μm", "厚度波动/μm",
                    "剥离力/N/25mm", "解卷力/N/25mm", "耐温/℃/0.5H", "状态"};

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
                row.createCell(15).setCellValue(spec.getStatus() == 1 ? "启用" : "禁用");
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

            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;

                try {
                    TapeSpec spec = new TapeSpec();
                    spec.setProductName(getCellStringValue(row.getCell(1)));
                    spec.setMaterialCode(getCellStringValue(row.getCell(2)));
                    spec.setColorCode(getCellStringValue(row.getCell(3)));
                    spec.setColorName(getCellStringValue(row.getCell(4)));
                    spec.setBaseThickness(getCellDecimalValue(row.getCell(5)));
                    spec.setBaseMaterial(getCellStringValue(row.getCell(6)));
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

                    String statusText = getCellStringValue(row.getCell(15));
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
                    "剥离力/N/25mm", "解卷力/N/25mm", "耐温/℃/0.5H", "状态"};

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
            sampleRow.createCell(15).setCellValue("启用");

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
}
