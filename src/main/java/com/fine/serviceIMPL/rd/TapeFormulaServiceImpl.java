package com.fine.serviceIMPL.rd;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fine.Dao.rd.TapeFormulaMapper;
import com.fine.Utils.ResponseResult;
import com.fine.modle.rd.TapeFormula;
import com.fine.modle.rd.TapeFormulaItem;
import com.fine.modle.rd.TapeRawMaterial;
import com.fine.service.rd.TapeFormulaService;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletResponse;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.Base64;
import java.util.*;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * 配胶标准单服务实现
 */
@Service
public class TapeFormulaServiceImpl implements TapeFormulaService {

    private static final int SPEC_MAX_LENGTH = 255;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Pattern NUMBER_WITH_UNIT_PATTERN = Pattern.compile("^([+-]?\\d+(?:\\.\\d+)?)\\s*(.*)$");

    @Autowired
    private TapeFormulaMapper tapeFormulaMapper;

    @Override
    public ResponseResult<?> getList(int page, int size, String materialCode, String productName,
                                     String glueModel, Integer status) {
        int offset = (page - 1) * size;
        List<TapeFormula> list = tapeFormulaMapper.selectList(materialCode, productName, glueModel, status, offset, size);
        if (list != null) {
            for (TapeFormula one : list) {
                hydrateProcessSplitFields(one);
            }
        }
        int total = tapeFormulaMapper.selectCount(materialCode, productName, glueModel, status);

        Map<String, Object> result = new HashMap<>();
        result.put("records", list);
        result.put("total", total);
        result.put("page", page);
        result.put("size", size);

        return new ResponseResult<>(20000, "查询成功", result);
    }

    @Override
    public ResponseResult<?> getById(Long id) {
        TapeFormula formula = tapeFormulaMapper.selectById(id);
        if (formula == null) {
            return new ResponseResult<>(50000, "配方不存在");
        }
        hydrateProcessSplitFields(formula);
        // 加载原料明细
        List<TapeFormulaItem> items = tapeFormulaMapper.selectItemsByFormulaId(id);
        formula.setItems(items);
        return new ResponseResult<>(20000, "查询成功", formula);
    }

    @Override
    public ResponseResult<?> getByMaterialCode(String materialCode) {
        TapeFormula formula = tapeFormulaMapper.selectByMaterialCode(materialCode);
        if (formula == null) {
            return new ResponseResult<>(50000, "该产品料号暂无配方");
        }
        hydrateProcessSplitFields(formula);
        List<TapeFormulaItem> items = tapeFormulaMapper.selectItemsByFormulaId(formula.getId());
        formula.setItems(items);
        return new ResponseResult<>(20000, "查询成功", formula);
    }

    @Override
    @Transactional
    public ResponseResult<?> create(TapeFormula formula, String operator) {
        normalizeFormulaFields(formula);
        // 检查料号是否重复
        if (tapeFormulaMapper.checkMaterialCodeExists(formula.getMaterialCode(), 0L) > 0) {
            return new ResponseResult<>(50000, "该产品料号已存在配方");
        }

        hydrateByTapeSpec(formula);
        applyDefaultGlueDensityByType(formula);
        formula.setFormulaNo(nextFormulaNo());
        formula.setGlueModel(nextGlueModel());
        formula.setVersion("A/00");
        if (formula.getCreateDate() == null) {
            formula.setCreateDate(new Date());
        }
        formula.setProcessRemark(buildProcessRemark(formula));
        formula.setStatus(formula.getStatus() == null ? 1 : formula.getStatus());
        formula.setCreateBy(operator);
        
        // 计算总重量
        calculateTotalWeight(formula);
        
        tapeFormulaMapper.insert(formula);

        // 保存原料明细
        if (formula.getItems() != null && !formula.getItems().isEmpty()) {
            int sortOrder = 1;
            for (TapeFormulaItem item : formula.getItems()) {
                item.setFormulaId(formula.getId());
                item.setSortOrder(sortOrder++);
                tapeFormulaMapper.insertItem(item);
            }
        }

        return new ResponseResult<>(20000, "创建成功", formula);
    }

    @Override
    @Transactional
    public ResponseResult<?> update(TapeFormula formula, String operator) {
        if (formula.getId() == null) {
            return new ResponseResult<>(50000, "ID不能为空");
        }

        TapeFormula existing = tapeFormulaMapper.selectById(formula.getId());
        if (existing == null) {
            return new ResponseResult<>(50000, "配方不存在");
        }

        normalizeFormulaFields(formula);

        // 检查料号是否重复（排除自己）
        if (tapeFormulaMapper.checkMaterialCodeExists(formula.getMaterialCode(), formula.getId()) > 0) {
            return new ResponseResult<>(50000, "该产品料号已存在其他配方");
        }

        hydrateByTapeSpec(formula);
        applyDefaultGlueDensityByType(formula);
        formula.setFormulaNo(existing.getFormulaNo());
        formula.setGlueModel(existing.getGlueModel());
        formula.setVersion(nextVersion(existing.getVersion()));
        if (formula.getCreateDate() == null) {
            formula.setCreateDate(existing.getCreateDate() != null ? existing.getCreateDate() : new Date());
        }
        formula.setProcessRemark(buildProcessRemark(formula));
        formula.setUpdateBy(operator);
        
        // 计算总重量
        calculateTotalWeight(formula);
        
        tapeFormulaMapper.update(formula);

        // 删除旧的明细，重新插入
        tapeFormulaMapper.deleteItemsByFormulaId(formula.getId());
        if (formula.getItems() != null && !formula.getItems().isEmpty()) {
            int sortOrder = 1;
            for (TapeFormulaItem item : formula.getItems()) {
                item.setFormulaId(formula.getId());
                item.setSortOrder(sortOrder++);
                tapeFormulaMapper.insertItem(item);
            }
        }

        return new ResponseResult<>(20000, "更新成功");
    }

    @Override
    @Transactional
    public ResponseResult<?> delete(Long id) {
        // 明细会级联删除
        int rows = tapeFormulaMapper.deleteById(id);
        if (rows == 0) {
            return new ResponseResult<>(50000, "删除失败，记录不存在");
        }
        return new ResponseResult<>(20000, "删除成功");
    }

    @Override
    public ResponseResult<?> getRawMaterialList() {
        List<TapeRawMaterial> list = tapeFormulaMapper.selectAllRawMaterials();
        return new ResponseResult<>(20000, "查询成功", list);
    }

    @Override
    public ResponseResult<?> getRawMaterialPage(int page, int size, String materialCode, String materialName,
                                                String materialCategory, String materialType, Integer status,
                                                String releaseForceA, String releaseForceB) {
        int offset = (page - 1) * size;
        List<TapeRawMaterial> list = tapeFormulaMapper.selectRawMaterialPage(materialCode, materialName, materialCategory, materialType, status, releaseForceA, releaseForceB, offset, size);
        int total = tapeFormulaMapper.selectRawMaterialCount(materialCode, materialName, materialCategory, materialType, status, releaseForceA, releaseForceB);

        Map<String, Object> result = new HashMap<>();
        result.put("records", list);
        result.put("total", total);
        result.put("page", page);
        result.put("size", size);

        return new ResponseResult<>(20000, "查询成功", result);
    }

    @Override
    public ResponseResult<?> getRawMaterialById(Long id) {
        TapeRawMaterial material = tapeFormulaMapper.selectRawMaterialById(id);
        if (material == null) {
            return new ResponseResult<>(50000, "原材料不存在");
        }
        return new ResponseResult<>(20000, "查询成功", material);
    }

    @Override
    public ResponseResult<?> createRawMaterial(TapeRawMaterial material) {
        if (material.getMaterialCode() == null || material.getMaterialCode().trim().isEmpty()) {
            return new ResponseResult<>(50000, "物料编码不能为空");
        }
        if (material.getMaterialName() == null || material.getMaterialName().trim().isEmpty()) {
            return new ResponseResult<>(50000, "物料名称不能为空");
        }
        if (tapeFormulaMapper.checkRawMaterialCodeExists(material.getMaterialCode().trim(), 0L) > 0) {
            return new ResponseResult<>(50000, "物料编码已存在");
        }

        material.setMaterialCode(material.getMaterialCode().trim());
        material.setMaterialName(material.getMaterialName().trim());
        material.setSupplierCode(normalizeText(material.getSupplierCode()));
        material.setMaterialMajor(normalizeText(material.getMaterialMajor()));
        material.setMaterialCategoryRaw(normalizeText(material.getMaterialCategoryRaw()));
        material.setMaterialCategory(normalizeText(material.getMaterialCategory()));
        material.setMaterialType(normalizeText(material.getMaterialType()));
        material.setUnit(normalizeText(material.getUnit()));
        material.setSpec(normalizeText(material.getSpec()));
        material.setRemark(normalizeText(material.getRemark()));
        material.setPerformanceParams(normalizeText(material.getPerformanceParams()));
        material.setStatus(material.getStatus() == null ? 1 : material.getStatus());
        tapeFormulaMapper.insertRawMaterial(material);
        return new ResponseResult<>(20000, "创建成功", material);
    }

    @Override
    public ResponseResult<?> updateRawMaterial(TapeRawMaterial material) {
        if (material.getId() == null) {
            return new ResponseResult<>(50000, "ID不能为空");
        }
        if (material.getMaterialCode() == null || material.getMaterialCode().trim().isEmpty()) {
            return new ResponseResult<>(50000, "物料编码不能为空");
        }
        if (material.getMaterialName() == null || material.getMaterialName().trim().isEmpty()) {
            return new ResponseResult<>(50000, "物料名称不能为空");
        }
        if (tapeFormulaMapper.checkRawMaterialCodeExists(material.getMaterialCode().trim(), material.getId()) > 0) {
            return new ResponseResult<>(50000, "物料编码已存在");
        }

        material.setMaterialCode(material.getMaterialCode().trim());
        material.setMaterialName(material.getMaterialName().trim());
        material.setSupplierCode(normalizeText(material.getSupplierCode()));
        material.setMaterialMajor(normalizeText(material.getMaterialMajor()));
        material.setMaterialCategoryRaw(normalizeText(material.getMaterialCategoryRaw()));
        material.setMaterialCategory(normalizeText(material.getMaterialCategory()));
        material.setMaterialType(normalizeText(material.getMaterialType()));
        material.setUnit(normalizeText(material.getUnit()));
        material.setSpec(normalizeText(material.getSpec()));
        material.setRemark(normalizeText(material.getRemark()));
        material.setPerformanceParams(normalizeText(material.getPerformanceParams()));
        material.setStatus(material.getStatus() == null ? 1 : material.getStatus());

        tapeFormulaMapper.updateRawMaterial(material);
        return new ResponseResult<>(20000, "更新成功");
    }

    @Override
    public ResponseResult<?> deleteRawMaterial(Long id) {
        tapeFormulaMapper.deleteRawMaterial(id);
        return new ResponseResult<>(20000, "删除成功");
    }

    @Override
    public void exportRawMaterials(HttpServletResponse response, String materialCode, String materialName,
                                   String materialCategory, String materialType, Integer status,
                                   String releaseForceA, String releaseForceB) {
        try {
            List<TapeRawMaterial> list = tapeFormulaMapper.selectRawMaterialPage(materialCode, materialName, materialCategory, materialType, status, releaseForceA, releaseForceB, 0, 100000);

            Workbook workbook = new XSSFWorkbook();
            Sheet sheet = workbook.createSheet("原材料表");

                String[] headers = {
                    "序号", "供应商代码", "物料编码", "物料大类", "物料类别(原始)", "物料名称", "物料类别(系统)", "物料类型", "单位", "规格说明", "备注",
                    "厚度", "电晕值", "抗拉强度", "伸长率", "颜色", "外观", "固含量", "粘度", "剥离强度", "离型力A", "离型力B",
                    "状态"
                };
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
                int width = 4200;
                if (i == 9 || i == 10) {
                    width = 7000;
                } else if (i >= 11 && i <= 21) {
                    width = 4500;
                }
                sheet.setColumnWidth(i, width);
            }

            int rowNum = 1;
            for (TapeRawMaterial material : list) {
                Row row = sheet.createRow(rowNum);
                Map<String, Object> performanceMap = parsePerformanceParamsMap(material.getPerformanceParams());
                row.createCell(0).setCellValue(rowNum);
                row.createCell(1).setCellValue(material.getSupplierCode() != null ? material.getSupplierCode() : "");
                row.createCell(2).setCellValue(material.getMaterialCode() != null ? material.getMaterialCode() : "");
                row.createCell(3).setCellValue(material.getMaterialMajor() != null ? material.getMaterialMajor() : "");
                row.createCell(4).setCellValue(material.getMaterialCategoryRaw() != null ? material.getMaterialCategoryRaw() : "");
                row.createCell(5).setCellValue(material.getMaterialName() != null ? material.getMaterialName() : "");
                row.createCell(6).setCellValue(material.getMaterialCategory() != null ? material.getMaterialCategory() : "");
                row.createCell(7).setCellValue(material.getMaterialType() != null ? material.getMaterialType() : "");
                row.createCell(8).setCellValue(material.getUnit() != null ? material.getUnit() : "");
                row.createCell(9).setCellValue(material.getSpec() != null ? material.getSpec() : "");
                row.createCell(10).setCellValue(material.getRemark() != null ? material.getRemark() : "");
                row.createCell(11).setCellValue(readPerfText(performanceMap, "thickness", "widthInspection"));
                row.createCell(12).setCellValue(readPerfText(performanceMap, "coronaValue", "coronaBothSides"));
                row.createCell(13).setCellValue(readPerfText(performanceMap, "tensileStrength", "thicknessTensile", "transverseTensile"));
                row.createCell(14).setCellValue(readPerfText(performanceMap, "elongation", "thicknessElongation", "transverseElongation"));
                row.createCell(15).setCellValue(readPerfText(performanceMap, "color"));
                row.createCell(16).setCellValue(readPerfText(performanceMap, "appearance", "surface"));
                row.createCell(17).setCellValue(readPerfText(performanceMap, "solidContent"));
                row.createCell(18).setCellValue(readPerfText(performanceMap, "viscosity", "viscosityValue"));
                row.createCell(19).setCellValue(readPerfText(performanceMap, "peelStrength"));
                row.createCell(20).setCellValue(readPerfText(performanceMap, "releaseForceA", "releaseForcea"));
                row.createCell(21).setCellValue(readPerfText(performanceMap, "releaseForceB", "releaseForceb"));
                row.createCell(22).setCellValue(material.getStatus() != null && material.getStatus() == 1 ? "启用" : "禁用");
                rowNum++;
            }

            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setHeader("Content-Disposition", "attachment;filename=" +
                    URLEncoder.encode("研发原材料表.xlsx", "UTF-8"));

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
    @Transactional
    public ResponseResult<?> importRawMaterials(MultipartFile file) {
        List<String> errors = new ArrayList<>();
        int successCount = 0;
        int failCount = 0;
        int supplierCodeIdx = -1;
        int materialCodeIdxNew = -1;
        int materialMajorIdx = -1;
        int materialCategoryIdx = -1;
        int materialTypeIdx = -1;
        int materialNameIdxNew = -1;
        int unitIdxNew = -1;
        int specIdx = -1;
        int perfIdx = -1;
        int thicknessIdx = -1;
        int coronaValueIdx = -1;
        int tensileStrengthIdx = -1;
        int elongationIdx = -1;
        int colorIdx = -1;
        int appearanceIdx = -1;
        int solidContentIdx = -1;
        int viscosityIdx = -1;
        int peelStrengthIdx = -1;
        int releaseForceAIdx = -1;
        int releaseForceBIdx = -1;
        int statusIdx = -1;
        int remarkIdxNew = -1;

        try {
            Workbook workbook = WorkbookFactory.create(file.getInputStream());
            Sheet sheet = workbook.getSheetAt(0);
            Row headerRow = sheet.getRow(0);
            Map<String, Integer> headerIndexMap = buildHeaderIndexMap(headerRow);

            supplierCodeIdx = findColumnIndex(headerIndexMap, "供应商代码", "供应商编码", "供应商");
            materialCodeIdxNew = findColumnIndex(headerIndexMap, "物料代码", "物料编码", "编码");
            materialMajorIdx = findColumnIndex(headerIndexMap, "物料大类", "大类", "材料大类");
            materialCategoryIdx = findColumnIndex(headerIndexMap, "物料类别", "类别", "物料分类", "材料类别", "物料类别(原始)", "物料类别（原始）");
            materialTypeIdx = findColumnIndex(headerIndexMap, "物料类型", "类型", "物料子类", "材料类型", "品类");
            materialNameIdxNew = findColumnIndex(headerIndexMap, "物料名称", "名称", "材料名称");
            unitIdxNew = findColumnIndex(headerIndexMap, "单位", "计量单位");
            specIdx = findColumnIndex(headerIndexMap, "规格说明", "规格");
            perfIdx = findColumnIndex(headerIndexMap, "性能参数(JSON/范围)", "性能参数", "性能参数（JSON/范围）");
            thicknessIdx = findColumnIndex(headerIndexMap, "厚度", "厚度(μm)", "厚度(um)", "thickness");
            coronaValueIdx = findColumnIndex(headerIndexMap, "电晕值", "电晕值(dyne)", "coronaValue");
            tensileStrengthIdx = findColumnIndex(headerIndexMap, "抗拉强度", "抗拉强度(N/15mm)", "tensileStrength");
            elongationIdx = findColumnIndex(headerIndexMap, "伸长率", "伸长率(%)", "elongation");
            colorIdx = findColumnIndex(headerIndexMap, "颜色", "color");
            appearanceIdx = findColumnIndex(headerIndexMap, "外观", "appearance", "surface");
            solidContentIdx = findColumnIndex(headerIndexMap, "固含量", "固含量(%)", "solidContent");
            viscosityIdx = findColumnIndex(headerIndexMap, "粘度", "黏度", "粘度(cps)", "viscosity", "viscosityValue");
            peelStrengthIdx = findColumnIndex(headerIndexMap, "剥离强度", "剥离强度(N/25mm)", "peelStrength");
            releaseForceAIdx = findColumnIndex(headerIndexMap, "离型力A", "离型力a", "releaseForceA", "releaseforcea");
            releaseForceBIdx = findColumnIndex(headerIndexMap, "离型力B", "离型力b", "releaseForceB", "releaseforceb");
            statusIdx = findColumnIndex(headerIndexMap, "状态", "status");
            remarkIdxNew = findColumnIndex(headerIndexMap, "备注");

            if (materialCodeIdxNew < 0 || materialNameIdxNew < 0) {
                return new ResponseResult<>(50000, "导入失败：缺少必填表头【物料代码/物料编码】或【物料名称】");
            }

            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) {
                    continue;
                }

                String materialCode;
                String materialName;

                materialCode = normalizeText(getCellStringValue(getCellByIndex(row, materialCodeIdxNew)));
                materialName = normalizeText(getCellStringValue(getCellByIndex(row, materialNameIdxNew)));

                if (materialCode == null && materialName == null) {
                    continue;
                }

                try {
                    if (materialCode == null) {
                        throw new IllegalArgumentException("物料编码不能为空");
                    }
                    if (materialName == null) {
                        throw new IllegalArgumentException("物料名称不能为空");
                    }

                    TapeRawMaterial material = new TapeRawMaterial();
                    material.setMaterialCode(materialCode);
                    material.setMaterialName(materialName);

                    String supplierCode = normalizeText(getCellStringValue(getCellByIndex(row, supplierCodeIdx)));
                    String materialMajor = normalizeText(getCellStringValue(getCellByIndex(row, materialMajorIdx)));
                    String materialCategoryText = normalizeText(getCellStringValue(getCellByIndex(row, materialCategoryIdx)));
                    String materialTypeText = normalizeText(getCellStringValue(getCellByIndex(row, materialTypeIdx)));
                    String unit = normalizeText(getCellStringValue(getCellByIndex(row, unitIdxNew)));
                    String specText = normalizeText(getCellStringValue(getCellByIndex(row, specIdx)));
                        String performanceText = normalizeText(getCellStringValue(getCellByIndex(row, perfIdx)));
                        String thicknessText = normalizeText(getCellStringValue(getCellByIndex(row, thicknessIdx)));
                        String coronaValueText = normalizeText(getCellStringValue(getCellByIndex(row, coronaValueIdx)));
                        String tensileStrengthText = normalizeText(getCellStringValue(getCellByIndex(row, tensileStrengthIdx)));
                        String elongationText = normalizeText(getCellStringValue(getCellByIndex(row, elongationIdx)));
                        String colorText = normalizeText(getCellStringValue(getCellByIndex(row, colorIdx)));
                        String appearanceText = normalizeText(getCellStringValue(getCellByIndex(row, appearanceIdx)));
                        String solidContentText = normalizeText(getCellStringValue(getCellByIndex(row, solidContentIdx)));
                        String viscosityText = normalizeText(getCellStringValue(getCellByIndex(row, viscosityIdx)));
                        String peelStrengthText = normalizeText(getCellStringValue(getCellByIndex(row, peelStrengthIdx)));
                        String releaseForceAText = normalizeText(getCellStringValue(getCellByIndex(row, releaseForceAIdx)));
                        String releaseForceBText = normalizeText(getCellStringValue(getCellByIndex(row, releaseForceBIdx)));
                    String remarkText = normalizeText(getCellStringValue(getCellByIndex(row, remarkIdxNew)));
                    String statusText = normalizeText(getCellStringValue(getCellByIndex(row, statusIdx)));

                        String mergedPerformanceParams = mergeImportedPerformanceParams(
                            performanceText,
                            thicknessText,
                            coronaValueText,
                            tensileStrengthText,
                            elongationText,
                            colorText,
                            appearanceText,
                            solidContentText,
                            viscosityText,
                            peelStrengthText,
                            releaseForceAText,
                            releaseForceBText
                        );

                    // 按导入表字段原样写库：不做归类推断、不做类型映射
                    material.setSupplierCode(supplierCode);
                    material.setMaterialMajor(materialMajor);
                    material.setMaterialCategoryRaw(materialCategoryText);
                    material.setMaterialCategory(materialCategoryText);
                    material.setMaterialType(materialTypeText);
                    material.setUnit(unit);
                    material.setSpec(specText);
                        material.setPerformanceParams(mergedPerformanceParams);
                    material.setRemark(remarkText);
                    material.setStatus(parseStatusValue(statusText));

                    TapeRawMaterial existing = tapeFormulaMapper.selectRawMaterialByCode(materialCode);
                    if (existing == null) {
                        tapeFormulaMapper.insertRawMaterial(material);
                    } else {
                        material.setId(existing.getId());
                        tapeFormulaMapper.updateRawMaterial(material);
                    }
                    successCount++;
                } catch (Exception ex) {
                    String err = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
                    errors.add("第" + (i + 1) + "行（物料编码=" + safeText(materialCode) + "）：" + err);
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
        Map<String, Integer> headerMapping = new LinkedHashMap<>();
        headerMapping.put("供应商代码", supplierCodeIdx);
        headerMapping.put("物料代码", materialCodeIdxNew);
        headerMapping.put("物料名称", materialNameIdxNew);
        headerMapping.put("物料大类", materialMajorIdx);
        headerMapping.put("物料类别", materialCategoryIdx);
        headerMapping.put("物料类型", materialTypeIdx);
        headerMapping.put("单位", unitIdxNew);
        headerMapping.put("规格说明", specIdx);
        headerMapping.put("性能参数", perfIdx);
        headerMapping.put("厚度", thicknessIdx);
        headerMapping.put("电晕值", coronaValueIdx);
        headerMapping.put("抗拉强度", tensileStrengthIdx);
        headerMapping.put("伸长率", elongationIdx);
        headerMapping.put("颜色", colorIdx);
        headerMapping.put("外观", appearanceIdx);
        headerMapping.put("固含量", solidContentIdx);
        headerMapping.put("粘度", viscosityIdx);
        headerMapping.put("剥离强度", peelStrengthIdx);
        headerMapping.put("离型力A", releaseForceAIdx);
        headerMapping.put("离型力B", releaseForceBIdx);
        headerMapping.put("备注", remarkIdxNew);
        headerMapping.put("状态", statusIdx);
        result.put("headerMapping", headerMapping);
        return new ResponseResult<>(20000, "导入完成", result);
    }

    @Override
    public void downloadRawMaterialTemplate(HttpServletResponse response) {
        try {
            Workbook workbook = new XSSFWorkbook();
            Sheet sheet = workbook.createSheet("研发原材料初始化模板");

            String[] headers = {
                "供应商代码", "物料代码", "物料大类", "物料类别(原始)", "物料类型", "物料名称", "单位", "规格说明", "备注",
                "厚度", "电晕值", "抗拉强度", "伸长率", "颜色", "外观", "固含量", "粘度", "剥离强度", "离型力A", "离型力B",
                "状态"
            };
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
                int width = 4200;
                if (i == 7 || i == 8) {
                    width = 7000;
                } else if (i >= 9 && i <= 19) {
                    width = 4500;
                }
                sheet.setColumnWidth(i, width);
            }

            Row sample = sheet.createRow(1);
            sample.createCell(0).setCellValue("BDJ001");
            sample.createCell(1).setCellValue("7058");
            sample.createCell(2).setCellValue("化工料");
            sample.createCell(3).setCellValue("胶水");
            sample.createCell(4).setCellValue("resin");
            sample.createCell(5).setCellValue("7058");
            sample.createCell(6).setCellValue("kg");
            sample.createCell(7).setCellValue("橡胶 180kg/桶");
            sample.createCell(8).setCellValue("主剂");
            sample.createCell(9).setCellValue("");
            sample.createCell(10).setCellValue("");
            sample.createCell(11).setCellValue("");
            sample.createCell(12).setCellValue("");
            sample.createCell(13).setCellValue("浅黄色");
            sample.createCell(14).setCellValue("透明液体");
            sample.createCell(15).setCellValue("52~56%");
            sample.createCell(16).setCellValue("1200~1800cps");
            sample.createCell(17).setCellValue("");
            sample.createCell(18).setCellValue("");
            sample.createCell(19).setCellValue("");
            sample.createCell(20).setCellValue("启用");

            Row sample2 = sheet.createRow(2);
            sample2.createCell(0).setCellValue("BDJ001");
            sample2.createCell(1).setCellValue("708");
            sample2.createCell(2).setCellValue("化工料");
            sample2.createCell(3).setCellValue("固化剂");
            sample2.createCell(4).setCellValue("curing");
            sample2.createCell(5).setCellValue("708");
            sample2.createCell(6).setCellValue("kg");
            sample2.createCell(7).setCellValue("1%");
            sample2.createCell(8).setCellValue("辅料");
            sample2.createCell(9).setCellValue("");
            sample2.createCell(10).setCellValue("");
            sample2.createCell(11).setCellValue("");
            sample2.createCell(12).setCellValue("");
            sample2.createCell(13).setCellValue("无色");
            sample2.createCell(14).setCellValue("液体");
            sample2.createCell(15).setCellValue("");
            sample2.createCell(16).setCellValue("200~400cps");
            sample2.createCell(17).setCellValue("");
            sample2.createCell(18).setCellValue("");
            sample2.createCell(19).setCellValue("");
            sample2.createCell(20).setCellValue("启用");

            Row sample3 = sheet.createRow(3);
            sample3.createCell(0).setCellValue("BH00");
            sample3.createCell(1).setCellValue("PETM-T23");
            sample3.createCell(2).setCellValue("原膜");
            sample3.createCell(3).setCellValue("PET膜");
            sample3.createCell(4).setCellValue("additive");
            sample3.createCell(5).setCellValue("PETM-T23");
            sample3.createCell(6).setCellValue("m²");
            sample3.createCell(7).setCellValue("23μm");
            sample3.createCell(8).setCellValue("双面电晕");
            sample3.createCell(9).setCellValue("23μm");
            sample3.createCell(10).setCellValue("≥48dyne");
            sample3.createCell(11).setCellValue("120~150N/15mm");
            sample3.createCell(12).setCellValue("80~120%");
            sample3.createCell(13).setCellValue("透明");
            sample3.createCell(14).setCellValue("无杂质");
            sample3.createCell(15).setCellValue("");
            sample3.createCell(16).setCellValue("");
            sample3.createCell(17).setCellValue("5~7N/25mm");
            sample3.createCell(18).setCellValue("20~35gf/in");
            sample3.createCell(19).setCellValue("30~45gf/in");
            sample3.createCell(20).setCellValue("启用");

            Row sample4 = sheet.createRow(4);
            sample4.createCell(0).setCellValue("CP001");
            sample4.createCell(1).setCellValue("YKLJ0101");
            sample4.createCell(2).setCellValue("化工料");
            sample4.createCell(3).setCellValue("胶水");
            sample4.createCell(4).setCellValue("resin");
            sample4.createCell(5).setCellValue("YKLJ0101");
            sample4.createCell(6).setCellValue("kg");
            sample4.createCell(7).setCellValue("6019");
            sample4.createCell(8).setCellValue("压敏胶");
            sample4.createCell(9).setCellValue("");
            sample4.createCell(10).setCellValue("");
            sample4.createCell(11).setCellValue("");
            sample4.createCell(12).setCellValue("");
            sample4.createCell(13).setCellValue("乳白");
            sample4.createCell(14).setCellValue("均匀膏状");
            sample4.createCell(15).setCellValue("48~52%");
            sample4.createCell(16).setCellValue("1500~2200cps");
            sample4.createCell(17).setCellValue("");
            sample4.createCell(18).setCellValue("");
            sample4.createCell(19).setCellValue("");
            sample4.createCell(20).setCellValue("启用");

            Sheet mappingSheet = workbook.createSheet("字段映射说明");
            mappingSheet.setColumnWidth(0, 24000);
            String[] notes = {
                    "【初始化模板字段说明】",
                    "1) 模板顺序固定：供应商代码、物料代码、物料大类、物料类别(原始)、物料类型、物料名称、单位、规格说明、备注、十一项性能参数、状态。",
                    "2) 导入键为【物料代码】：存在则更新，不存在则新增。",
                    "3) 十一项性能参数列：厚度、电晕值、抗拉强度、伸长率、颜色、外观、固含量、粘度、剥离强度、离型力A、离型力B。",
                    "4) 性能参数可填写值（如 红色）、区间（如 120~150N/15mm）或阈值（如 ≥48dyne / ≤2200cps）。",
                    "5) 仍兼容旧列【性能参数(JSON/范围)】：当十一项列为空时将回退使用旧列。",
                    "6) 状态支持：启用/禁用/1/0，默认启用。"
            };
            for (int i = 0; i < notes.length; i++) {
                Row r = mappingSheet.createRow(i);
                r.createCell(0).setCellValue(notes[i]);
            }

            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setHeader("Content-Disposition", "attachment;filename=" +
                    URLEncoder.encode("研发原材料导入模板.xlsx", "UTF-8"));

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
    public void exportFormula(HttpServletResponse response, Long id) {
        try {
            TapeFormula formula = tapeFormulaMapper.selectById(id);
            if (formula == null) return;
            
            List<TapeFormulaItem> items = tapeFormulaMapper.selectItemsByFormulaId(id);
            formula.setItems(items);

            Workbook workbook = new XSSFWorkbook();
            Sheet sheet = workbook.createSheet("配胶标准单");

            // 设置列宽
            for (int i = 0; i < 10; i++) {
                sheet.setColumnWidth(i, 3500);
            }

            // 创建样式
            CellStyle headerStyle = workbook.createCellStyle();
            headerStyle.setAlignment(HorizontalAlignment.CENTER);
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerFont.setFontHeightInPoints((short) 14);
            headerStyle.setFont(headerFont);

            CellStyle titleStyle = workbook.createCellStyle();
            titleStyle.setAlignment(HorizontalAlignment.CENTER);
            Font titleFont = workbook.createFont();
            titleFont.setBold(true);
            titleFont.setFontHeightInPoints((short) 18);
            titleStyle.setFont(titleFont);

            CellStyle normalStyle = workbook.createCellStyle();
            normalStyle.setBorderBottom(BorderStyle.THIN);
            normalStyle.setBorderTop(BorderStyle.THIN);
            normalStyle.setBorderLeft(BorderStyle.THIN);
            normalStyle.setBorderRight(BorderStyle.THIN);

            int rowNum = 0;

            // 标题行：文件编号、版次、制定日期
            Row row0 = sheet.createRow(rowNum++);
            createCell(row0, 0, "文件编号", normalStyle);
            createCell(row0, 1, formula.getFormulaNo(), normalStyle);
            createCell(row0, 3, "版次", normalStyle);
            createCell(row0, 4, formula.getVersion(), normalStyle);
            createCell(row0, 6, "制定日期", normalStyle);
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
            createCell(row0, 7, formula.getCreateDate() != null ? sdf.format(formula.getCreateDate()) : "", normalStyle);

            // 公司名称
            Row row1 = sheet.createRow(rowNum++);
            Cell companyCell = row1.createCell(0);
            companyCell.setCellValue("东莞方恩电子材料科技有限公司");
            companyCell.setCellStyle(titleStyle);
            sheet.addMergedRegion(new CellRangeAddress(1, 1, 0, 9));

            // 配胶标准单标题
            Row row2 = sheet.createRow(rowNum++);
            Cell titleCell = row2.createCell(0);
            titleCell.setCellValue("配胶标准单");
            titleCell.setCellStyle(headerStyle);
            sheet.addMergedRegion(new CellRangeAddress(2, 2, 0, 9));

            // 产品信息行
            Row row3 = sheet.createRow(rowNum++);
            createCell(row3, 0, "产品名称", normalStyle);
            createCell(row3, 1, formula.getProductName(), normalStyle);
            createCell(row3, 5, "产品型号", normalStyle);
            createCell(row3, 6, formula.getMaterialCode(), normalStyle);

            Row row4 = sheet.createRow(rowNum++);
            createCell(row4, 0, "胶水型号", normalStyle);
            createCell(row4, 1, formula.getGlueModel(), normalStyle);
            createCell(row4, 5, "颜色", normalStyle);
            createCell(row4, 6, formula.getColorCode(), normalStyle);

            Row row5 = sheet.createRow(rowNum++);
            createCell(row5, 0, "涂胶厚度(μm)", normalStyle);
            createCell(row5, 1, formula.getCoatingThickness() != null ? formula.getCoatingThickness().toString() : "", normalStyle);
            createCell(row5, 5, "胶水密度(g/cm³)", normalStyle);
            createCell(row5, 6, formula.getGlueDensity() != null ? formula.getGlueDensity().toString() : "", normalStyle);

            Row row6 = sheet.createRow(rowNum++);
            createCell(row6, 0, "固含量(%)", normalStyle);
            createCell(row6, 1, formula.getSolidContent(), normalStyle);
            createCell(row6, 5, "涂布数量(㎡)", normalStyle);
            createCell(row6, 6, formula.getCoatingArea() != null ? formula.getCoatingArea().toString() : "", normalStyle);

            // 备注
            Row row7 = sheet.createRow(rowNum++);
            createCell(row7, 0, "备注", normalStyle);
            createCell(row7, 1, formula.getProcessRemark(), normalStyle);

            // 原料表头
            rowNum++;
            Row itemHeader = sheet.createRow(rowNum++);
            createCell(itemHeader, 0, "物料代码", normalStyle);
            createCell(itemHeader, 1, "物料名称", normalStyle);
            createCell(itemHeader, 2, "Kg/桶", normalStyle);
            createCell(itemHeader, 3, "比例(%)", normalStyle);
            createCell(itemHeader, 4, "备注", normalStyle);

            // 原料明细
            if (items != null) {
                for (TapeFormulaItem item : items) {
                    Row itemRow = sheet.createRow(rowNum++);
                    createCell(itemRow, 0, item.getMaterialCode(), normalStyle);
                    createCell(itemRow, 1, item.getMaterialName(), normalStyle);
                    createCell(itemRow, 2, item.getWeight() != null ? item.getWeight().toString() : "", normalStyle);
                    createCell(itemRow, 3, item.getRatio() != null ? item.getRatio().toString() + "%" : "/", normalStyle);
                    createCell(itemRow, 4, item.getRemark(), normalStyle);
                }
            }

            // 总重量
            Row totalRow = sheet.createRow(rowNum++);
            createCell(totalRow, 0, "总重量(kg)", normalStyle);
            createCell(totalRow, 2, formula.getTotalWeight() != null ? formula.getTotalWeight().toString() : "", normalStyle);

            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setHeader("Content-Disposition", "attachment;filename=" +
                    URLEncoder.encode("配胶标准单_" + formula.getMaterialCode() + ".xlsx", "UTF-8"));

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
    public ResponseResult<?> getPrintData(Long id) {
        TapeFormula formula = tapeFormulaMapper.selectById(id);
        if (formula == null) {
            return new ResponseResult<>(50000, "配方不存在");
        }
        List<TapeFormulaItem> items = tapeFormulaMapper.selectItemsByFormulaId(id);
        formula.setItems(items);
        return new ResponseResult<>(20000, "查询成功", formula);
    }

    @Override
    @Transactional
    public ResponseResult<?> initializeRawMaterials() {
        Map<String, Object> result = new HashMap<>();
        result.put("insertCount", 0);
        result.put("updateCount", 0);
        result.put("totalCount", 0);
        return new ResponseResult<>(20000, "请使用Excel导入进行初始化", result);
    }

    @Override
    @Transactional
    public ResponseResult<?> resequenceAll(String operator) {
        List<TapeFormula> all = tapeFormulaMapper.selectAllOrderById();
        if (all == null || all.isEmpty()) {
            return new ResponseResult<>(20000, "无可重编数据", Collections.emptyMap());
        }

        int formulaSeq = 1;
        int glueSeq = 1;
        int count = 0;
        for (TapeFormula one : all) {
            if (one == null || one.getId() == null) {
                continue;
            }
            String formulaNo = formatFormulaNo(formulaSeq++);
            String glueModel = formatGlueModel(glueSeq++);
            String version = "A/00";
            tapeFormulaMapper.updateSequenceFieldsById(one.getId(), formulaNo, glueModel, version, operator);
            count++;
        }

        Map<String, Object> data = new HashMap<>();
        data.put("updated", count);
        data.put("formulaStart", "F00001");
        data.put("glueModelStart", "GLU-00001");
        data.put("version", "A/00");
        return new ResponseResult<>(20000, "重编完成", data);
    }

    // ========== 私有辅助方法 ==========

    private void calculateTotalWeight(TapeFormula formula) {
        if (formula.getItems() != null && !formula.getItems().isEmpty()) {
            BigDecimal total = BigDecimal.ZERO;
            for (TapeFormulaItem item : formula.getItems()) {
                if (item.getWeight() != null) {
                    total = total.add(item.getWeight());
                }
            }
            formula.setTotalWeight(total);
        }
    }

    private void normalizeFormulaFields(TapeFormula formula) {
        if (formula == null) {
            return;
        }
        formula.setMaterialCode(normalizeText(formula.getMaterialCode()));
        formula.setProductName(normalizeText(formula.getProductName()));
        formula.setColorCode(normalizeText(formula.getColorCode()));
        formula.setGlueType(normalizeText(formula.getGlueType()));
        formula.setSolidContent(normalizeText(formula.getSolidContent()));
        formula.setProcessTemperature(normalizeText(formula.getProcessTemperature()));
        formula.setProcessSpeed(normalizeText(formula.getProcessSpeed()));
        formula.setProcessRemark(normalizeText(formula.getProcessRemark()));
        formula.setPreparedBy(normalizeText(formula.getPreparedBy()));
        formula.setReviewedBy(normalizeText(formula.getReviewedBy()));
        formula.setApprovedBy(normalizeText(formula.getApprovedBy()));
        formula.setRemark(normalizeText(formula.getRemark()));
    }

    private void hydrateByTapeSpec(TapeFormula formula) {
        if (formula == null || formula.getMaterialCode() == null) {
            return;
        }
        com.fine.modle.rd.TapeSpec spec = tapeFormulaMapper.selectTapeSpecLiteByCode(formula.getMaterialCode());
        if (spec == null) {
            return;
        }
        if (spec.getProductName() != null) {
            formula.setProductName(spec.getProductName().trim());
        }
        if (spec.getGlueThickness() != null) {
            formula.setCoatingThickness(spec.getGlueThickness());
        }
        if ((formula.getColorCode() == null || formula.getColorCode().isEmpty()) && spec.getColorCode() != null) {
            formula.setColorCode(spec.getColorCode().trim());
        }
    }

    private void applyDefaultGlueDensityByType(TapeFormula formula) {
        if (formula == null || formula.getGlueDensity() != null) {
            return;
        }
        String type = formula.getGlueType();
        if (type == null) {
            return;
        }
        switch (type.trim()) {
            case "亚克力":
                formula.setGlueDensity(new BigDecimal("1.05"));
                break;
            case "橡胶":
                formula.setGlueDensity(new BigDecimal("0.98"));
                break;
            case "硅胶":
                formula.setGlueDensity(new BigDecimal("1.10"));
                break;
            case "PU胶":
                formula.setGlueDensity(new BigDecimal("1.12"));
                break;
            default:
                break;
        }
    }

    private String buildProcessRemark(TapeFormula formula) {
        String temp = normalizeText(formula.getProcessTemperature());
        String speed = normalizeText(formula.getProcessSpeed());
        String remark = normalizeText(formula.getProcessRemark());

        List<String> parts = new ArrayList<>();
        if (temp != null) {
            parts.add("温度=" + temp);
        }
        if (speed != null) {
            parts.add("车速=" + speed);
        }
        if (remark != null && !remark.contains("温度=") && !remark.contains("车速=")) {
            parts.add("备注=" + remark);
        }
        return parts.isEmpty() ? remark : String.join(";", parts);
    }

    private String nextFormulaNo() {
        Integer max = tapeFormulaMapper.selectMaxFormulaNoSeq();
        int seq = (max == null ? 0 : max) + 1;
        return formatFormulaNo(seq);
    }

    private String nextGlueModel() {
        Integer max = tapeFormulaMapper.selectMaxGlueModelSeq();
        int seq = (max == null ? 0 : max) + 1;
        return formatGlueModel(seq);
    }

    private String formatFormulaNo(int seq) {
        return String.format("F%05d", Math.max(seq, 1));
    }

    private String formatGlueModel(int seq) {
        return String.format("GLU-%05d", Math.max(seq, 1));
    }

    private String nextVersion(String currentVersion) {
        String text = normalizeText(currentVersion);
        int current = 0;
        if (text != null) {
            int idx = text.indexOf('/');
            String n = idx >= 0 ? text.substring(idx + 1) : text;
            try {
                current = Integer.parseInt(n.replaceAll("[^0-9]", ""));
            } catch (Exception ignore) {
                current = 0;
            }
        }
        return "A/" + String.format("%02d", current + 1);
    }

    private void hydrateProcessSplitFields(TapeFormula formula) {
        if (formula == null || formula.getProcessRemark() == null) {
            return;
        }
        String text = formula.getProcessRemark();
        if ((formula.getProcessTemperature() == null || formula.getProcessTemperature().trim().isEmpty())) {
            String temp = extractTokenValue(text, "温度");
            if (temp != null) {
                formula.setProcessTemperature(temp);
            }
        }
        if ((formula.getProcessSpeed() == null || formula.getProcessSpeed().trim().isEmpty())) {
            String speed = extractTokenValue(text, "车速");
            if (speed != null) {
                formula.setProcessSpeed(speed);
            }
        }
    }

    private String extractTokenValue(String text, String key) {
        if (text == null || key == null || key.trim().isEmpty()) {
            return null;
        }
        String[] pieces = text.split(";");
        for (String piece : pieces) {
            String p = piece == null ? "" : piece.trim();
            if (p.startsWith(key + "=")) {
                String v = p.substring((key + "=").length()).trim();
                return v.isEmpty() ? null : v;
            }
        }
        return null;
    }

    private void createCell(Row row, int column, String value, CellStyle style) {
        Cell cell = row.createCell(column);
        cell.setCellValue(value != null ? value : "");
        if (style != null) {
            cell.setCellStyle(style);
        }
    }

    @Override
    public void downloadTemplate(HttpServletResponse response) {
        try {
            Workbook workbook = new XSSFWorkbook();
            
            // Sheet1: 配方主表模板
            Sheet mainSheet = workbook.createSheet("配方主表");
            Row header1 = mainSheet.createRow(0);
            String[] mainHeaders = {"产品料号*", "产品名称*", "文件编号", "版次", "制定日期", 
                    "胶水型号", "胶水类型", "颜色代码", "涂胶厚度(μm)", "胶水密度(g/cm³)", "固含量(%)", 
                    "涂布数量(㎡)", "工艺温度", "工艺车速", "工艺备注", "编制人", "审核人", "批准人"};
            for (int i = 0; i < mainHeaders.length; i++) {
                Cell cell = header1.createCell(i);
                cell.setCellValue(mainHeaders[i]);
                mainSheet.setColumnWidth(i, 4500);
            }
            
            // 示例数据
            Row example1 = mainSheet.createRow(1);
            example1.createCell(0).setCellValue("1011-R02-1204-G01-0300");
            example1.createCell(1).setCellValue("16μm翠绿PET终止胶带");
            example1.createCell(2).setCellValue("F00001");
            example1.createCell(3).setCellValue("A/00");
            example1.createCell(4).setCellValue("2025-12-08");
            example1.createCell(5).setCellValue("GLU-00001");
            example1.createCell(6).setCellValue("亚克力");
            example1.createCell(7).setCellValue("G01");
            example1.createCell(8).setCellValue(5);
            example1.createCell(9).setCellValue(1.1);
            example1.createCell(10).setCellValue("15±2");
            example1.createCell(11).setCellValue(24000);
            example1.createCell(12).setCellValue("70 80 120 120 120 90 80 70");
            example1.createCell(13).setCellValue("40m/min");
            example1.createCell(14).setCellValue("可附加工艺说明");
            example1.createCell(15).setCellValue("张三");
            example1.createCell(16).setCellValue("李四");
            example1.createCell(17).setCellValue("王五");
            
            // Sheet2: 原料明细模板
            Sheet itemSheet = workbook.createSheet("原料明细");
            Row header2 = itemSheet.createRow(0);
            String[] itemHeaders = {"产品料号*", "物料代码*", "物料名称", "重量(Kg/桶)", "比例(%)", "备注", "排序"};
            for (int i = 0; i < itemHeaders.length; i++) {
                Cell cell = header2.createCell(i);
                cell.setCellValue(itemHeaders[i]);
                itemSheet.setColumnWidth(i, 4500);
            }
            
            // 原料明细示例
            String[][] itemExamples = {
                {"1011-R02-1204-G01-0300", "YKLJ0801", "主树脂", "76.0000", "", "/", "1"},
                {"1011-R02-1204-G01-0300", "YSYZ0201", "溶剂", "49.4000", "65", "/", "2"},
                {"1011-R02-1204-G01-0300", "G728-UJ", "助剂", "5.3200", "7", "", "3"},
                {"1011-R02-1204-G01-0300", "9002", "固化剂", "0.3040", "0.4", "要分开加进胶水", "4"},
                {"1011-R02-1204-G01-0300", "FY-45", "固化剂", "0.3040", "0.4", "", "5"}
            };
            for (int i = 0; i < itemExamples.length; i++) {
                Row row = itemSheet.createRow(i + 1);
                for (int j = 0; j < itemExamples[i].length; j++) {
                    row.createCell(j).setCellValue(itemExamples[i][j]);
                }
            }
            
            // Sheet3: 填写说明
            Sheet helpSheet = workbook.createSheet("填写说明");
            String[] helpTexts = {
                "【配胶标准单导入模板说明】",
                "",
                "一、配方主表（Sheet1）",
                "   - 产品料号*：必填，唯一标识，与原料明细通过此字段关联",
                "   - 产品名称*：必填，产品描述",
                "   - 文件编号：选填，如107",
                "   - 版次：选填，如A/0",
                "   - 制定日期：选填，格式yyyy-MM-dd",
                "   - 胶水型号：选填",
                "   - 颜色代码：选填，如G01",
                "   - 涂胶厚度(μm)：选填，数字",
                "   - 胶水密度(g/cm³)：选填，数字",
                "   - 固含量(%)：选填，可填范围如15±2",
                "   - 涂布数量(㎡)：选填，数字",
                "   - 工艺备注：选填，温度速度等工艺参数",
                "   - 编制人/审核人/批准人：选填",
                "",
                "二、原料明细（Sheet2）",
                "   - 产品料号*：必填，与配方主表的产品料号对应",
                "   - 物料代码*：必填，原料代码",
                "   - 物料名称：选填，原料名称",
                "   - 重量(Kg/桶)：选填，数字",
                "   - 比例(%)：选填，数字",
                "   - 备注：选填，稀释说明等",
                "   - 排序：选填，数字，决定原料显示顺序",
                "",
                "三、注意事项",
                "   1. 带*号为必填项",
                "   2. 产品料号必须唯一，如已存在会更新配方",
                "   3. 一个产品料号可以对应多行原料明细",
                "   4. 总重量会根据原料明细自动计算"
            };
            for (int i = 0; i < helpTexts.length; i++) {
                Row row = helpSheet.createRow(i);
                row.createCell(0).setCellValue(helpTexts[i]);
            }
            helpSheet.setColumnWidth(0, 20000);
            
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setHeader("Content-Disposition", "attachment;filename=" +
                    URLEncoder.encode("配胶标准单导入模板.xlsx", "UTF-8"));
            
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
    @Transactional
    public ResponseResult<?> importFormula(org.springframework.web.multipart.MultipartFile file, String operator) {
        try {
            Workbook workbook = WorkbookFactory.create(file.getInputStream());
            
            // 读取配方主表
            Sheet mainSheet = workbook.getSheet("配方主表");
            if (mainSheet == null) {
                mainSheet = workbook.getSheetAt(0);
            }
            
            // 读取原料明细表
            Sheet itemSheet = workbook.getSheet("原料明细");
            if (itemSheet == null && workbook.getNumberOfSheets() > 1) {
                itemSheet = workbook.getSheetAt(1);
            }
            
            // 先解析原料明细，按产品料号分组
            Map<String, List<TapeFormulaItem>> itemMap = new HashMap<>();
            if (itemSheet != null) {
                for (int i = 1; i <= itemSheet.getLastRowNum(); i++) {
                    Row row = itemSheet.getRow(i);
                    if (row == null) continue;
                    
                    String materialCode = getCellStringValue(row.getCell(0));
                    if (materialCode == null || materialCode.isEmpty()) continue;
                    
                    TapeFormulaItem item = new TapeFormulaItem();
                    item.setMaterialCode(getCellStringValue(row.getCell(1)));
                    item.setMaterialName(getCellStringValue(row.getCell(2)));
                    item.setWeight(getCellBigDecimalValue(row.getCell(3)));
                    item.setRatio(getCellBigDecimalValue(row.getCell(4)));
                    item.setRemark(getCellStringValue(row.getCell(5)));
                    item.setSortOrder(getCellIntValue(row.getCell(6)));
                    
                    itemMap.computeIfAbsent(materialCode, k -> new ArrayList<>()).add(item);
                }
            }
            
            // 解析并保存配方主表
            int successCount = 0;
            int updateCount = 0;
            List<String> errors = new ArrayList<>();
            
            for (int i = 1; i <= mainSheet.getLastRowNum(); i++) {
                Row row = mainSheet.getRow(i);
                if (row == null) continue;
                
                String materialCode = getCellStringValue(row.getCell(0));
                String productName = getCellStringValue(row.getCell(1));
                
                if (materialCode == null || materialCode.isEmpty()) {
                    continue; // 跳过空行
                }
                if (productName == null || productName.isEmpty()) {
                    errors.add("第" + (i + 1) + "行：产品名称不能为空");
                    continue;
                }
                
                try {
                    TapeFormula formula = new TapeFormula();
                    formula.setMaterialCode(materialCode);
                    formula.setProductName(productName);
                    formula.setFormulaNo(getCellStringValue(row.getCell(2)));
                    formula.setVersion(getCellStringValue(row.getCell(3)));
                    formula.setCreateDate(getCellDateValue(row.getCell(4)));
                    formula.setGlueModel(getCellStringValue(row.getCell(5)));
                    formula.setGlueType(getCellStringValue(row.getCell(6)));
                    formula.setColorCode(getCellStringValue(row.getCell(7)));
                    formula.setCoatingThickness(getCellBigDecimalValue(row.getCell(8)));
                    formula.setGlueDensity(getCellBigDecimalValue(row.getCell(9)));
                    formula.setSolidContent(getCellStringValue(row.getCell(10)));
                    formula.setCoatingArea(getCellBigDecimalValue(row.getCell(11)));
                    formula.setProcessTemperature(getCellStringValue(row.getCell(12)));
                    formula.setProcessSpeed(getCellStringValue(row.getCell(13)));
                    formula.setProcessRemark(getCellStringValue(row.getCell(14)));
                    formula.setPreparedBy(getCellStringValue(row.getCell(15)));
                    formula.setReviewedBy(getCellStringValue(row.getCell(16)));
                    formula.setApprovedBy(getCellStringValue(row.getCell(17)));
                    formula.setStatus(1);
                    
                    // 设置原料明细
                    List<TapeFormulaItem> items = itemMap.get(materialCode);
                    if (items != null) {
                        formula.setItems(items);
                    }
                    
                    // 检查是否已存在
                    TapeFormula existing = tapeFormulaMapper.selectByMaterialCode(materialCode);
                    if (existing != null) {
                        formula.setId(existing.getId());
                        update(formula, operator);
                        updateCount++;
                    } else {
                        create(formula, operator);
                        successCount++;
                    }
                    
                } catch (Exception e) {
                    errors.add("第" + (i + 1) + "行导入失败：" + e.getMessage());
                }
            }
            
            workbook.close();
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("insertCount", successCount);
            result.put("updateCount", updateCount);
            result.put("errors", errors);
            
            String message = String.format("导入完成：新增%d条，更新%d条", successCount, updateCount);
            if (!errors.isEmpty()) {
                message += "，" + errors.size() + "条失败";
            }
            
            return new ResponseResult<>(20000, message, result);
            
        } catch (Exception e) {
            e.printStackTrace();
            return new ResponseResult<>(50000, "导入失败：" + e.getMessage());
        }
    }

    @Override
    public void exportAllFormula(HttpServletResponse response) {
        try {
            // 获取所有配方
            List<TapeFormula> list = tapeFormulaMapper.selectList(null, null, null, null, 0, 10000);
            
            Workbook workbook = new XSSFWorkbook();
            
            // Sheet1: 配方主表
            Sheet mainSheet = workbook.createSheet("配方主表");
            Row header1 = mainSheet.createRow(0);
            String[] mainHeaders = {"产品料号", "产品名称", "文件编号", "版次", "制定日期", 
                    "胶水型号", "胶水类型", "颜色代码", "涂胶厚度(μm)", "胶水密度(g/cm³)", "固含量(%)", 
                    "涂布数量(㎡)", "工艺温度", "工艺车速", "工艺备注", "总重量(kg)", "编制人", "审核人", "批准人", "状态"};
            
            CellStyle headerStyle = workbook.createCellStyle();
            headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);
            
            for (int i = 0; i < mainHeaders.length; i++) {
                Cell cell = header1.createCell(i);
                cell.setCellValue(mainHeaders[i]);
                cell.setCellStyle(headerStyle);
                mainSheet.setColumnWidth(i, 4500);
            }
            
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
            for (int i = 0; i < list.size(); i++) {
                TapeFormula f = list.get(i);
                Row row = mainSheet.createRow(i + 1);
                row.createCell(0).setCellValue(f.getMaterialCode() != null ? f.getMaterialCode() : "");
                row.createCell(1).setCellValue(f.getProductName() != null ? f.getProductName() : "");
                row.createCell(2).setCellValue(f.getFormulaNo() != null ? f.getFormulaNo() : "");
                row.createCell(3).setCellValue(f.getVersion() != null ? f.getVersion() : "");
                row.createCell(4).setCellValue(f.getCreateDate() != null ? sdf.format(f.getCreateDate()) : "");
                row.createCell(5).setCellValue(f.getGlueModel() != null ? f.getGlueModel() : "");
                row.createCell(6).setCellValue(f.getGlueType() != null ? f.getGlueType() : "");
                row.createCell(7).setCellValue(f.getColorCode() != null ? f.getColorCode() : "");
                row.createCell(8).setCellValue(f.getCoatingThickness() != null ? f.getCoatingThickness().doubleValue() : 0);
                row.createCell(9).setCellValue(f.getGlueDensity() != null ? f.getGlueDensity().doubleValue() : 0);
                row.createCell(10).setCellValue(f.getSolidContent() != null ? f.getSolidContent() : "");
                row.createCell(11).setCellValue(f.getCoatingArea() != null ? f.getCoatingArea().doubleValue() : 0);
                row.createCell(12).setCellValue(f.getProcessTemperature() != null ? f.getProcessTemperature() : "");
                row.createCell(13).setCellValue(f.getProcessSpeed() != null ? f.getProcessSpeed() : "");
                row.createCell(14).setCellValue(f.getProcessRemark() != null ? f.getProcessRemark() : "");
                row.createCell(15).setCellValue(f.getTotalWeight() != null ? f.getTotalWeight().doubleValue() : 0);
                row.createCell(16).setCellValue(f.getPreparedBy() != null ? f.getPreparedBy() : "");
                row.createCell(17).setCellValue(f.getReviewedBy() != null ? f.getReviewedBy() : "");
                row.createCell(18).setCellValue(f.getApprovedBy() != null ? f.getApprovedBy() : "");
                row.createCell(19).setCellValue(f.getStatus() != null && f.getStatus() == 1 ? "启用" : "禁用");
            }
            
            // Sheet2: 原料明细
            Sheet itemSheet = workbook.createSheet("原料明细");
            Row header2 = itemSheet.createRow(0);
            String[] itemHeaders = {"产品料号", "物料代码", "物料名称", "重量(Kg/桶)", "比例(%)", "备注", "排序"};
            for (int i = 0; i < itemHeaders.length; i++) {
                Cell cell = header2.createCell(i);
                cell.setCellValue(itemHeaders[i]);
                cell.setCellStyle(headerStyle);
                itemSheet.setColumnWidth(i, 4500);
            }
            
            int itemRowNum = 1;
            for (TapeFormula f : list) {
                List<TapeFormulaItem> items = tapeFormulaMapper.selectItemsByFormulaId(f.getId());
                if (items != null) {
                    for (TapeFormulaItem item : items) {
                        Row row = itemSheet.createRow(itemRowNum++);
                        row.createCell(0).setCellValue(f.getMaterialCode() != null ? f.getMaterialCode() : "");
                        row.createCell(1).setCellValue(item.getMaterialCode() != null ? item.getMaterialCode() : "");
                        row.createCell(2).setCellValue(item.getMaterialName() != null ? item.getMaterialName() : "");
                        row.createCell(3).setCellValue(item.getWeight() != null ? item.getWeight().doubleValue() : 0);
                        row.createCell(4).setCellValue(item.getRatio() != null ? item.getRatio().doubleValue() : 0);
                        row.createCell(5).setCellValue(item.getRemark() != null ? item.getRemark() : "");
                        row.createCell(6).setCellValue(item.getSortOrder() != null ? item.getSortOrder() : 0);
                    }
                }
            }
            
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setHeader("Content-Disposition", "attachment;filename=" +
                    URLEncoder.encode("配胶标准单数据.xlsx", "UTF-8"));
            
            OutputStream out = response.getOutputStream();
            workbook.write(out);
            out.flush();
            out.close();
            workbook.close();
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ========== Excel读取辅助方法 ==========
    
    private String getCellStringValue(Cell cell) {
        if (cell == null) return null;
        try {
            if (cell.getCellType() == CellType.STRING) {
                String value = cell.getStringCellValue();
                return value != null ? value.trim() : null;
            }
            if (cell.getCellType() == CellType.NUMERIC) {
                return BigDecimal.valueOf(cell.getNumericCellValue()).stripTrailingZeros().toPlainString();
            }
            if (cell.getCellType() == CellType.BOOLEAN) {
                return String.valueOf(cell.getBooleanCellValue());
            }
            return cell.toString().trim();
        } catch (Exception e) {
            return null;
        }
    }
    
    private BigDecimal getCellBigDecimalValue(Cell cell) {
        if (cell == null) return null;
        try {
            if (cell.getCellType() == CellType.NUMERIC) {
                return BigDecimal.valueOf(cell.getNumericCellValue());
            } else if (cell.getCellType() == CellType.STRING) {
                String value = cell.getStringCellValue().trim();
                if (value.isEmpty()) return null;
                return new BigDecimal(value);
            }
        } catch (Exception e) {
            // ignore
        }
        return null;
    }
    
    private Integer getCellIntValue(Cell cell) {
        if (cell == null) return null;
        try {
            if (cell.getCellType() == CellType.NUMERIC) {
                return (int) cell.getNumericCellValue();
            } else if (cell.getCellType() == CellType.STRING) {
                String value = cell.getStringCellValue().trim();
                if (value.isEmpty()) return null;
                return Integer.parseInt(value);
            }
        } catch (Exception e) {
            // ignore
        }
        return null;
    }

    private Cell getCellByIndex(Row row, int index) {
        if (row == null || index < 0) {
            return null;
        }
        return row.getCell(index);
    }

    private Integer parseStatusValue(String statusText) {
        String text = normalizeText(statusText);
        if (text == null) {
            return 1;
        }
        if ("0".equals(text) || "禁用".equals(text)) {
            return 0;
        }
        if ("1".equals(text) || "启用".equals(text)) {
            return 1;
        }
        return 1;
    }

    private String normalizeText(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    @SuppressWarnings("unused")
    private String parseMaterialType(String rawType) {
        String type = normalizeText(rawType);
        if (type == null) {
            return "resin";
        }
        if ("resin".equalsIgnoreCase(type) || "树脂".equals(type)) {
            return "resin";
        }
        if ("solvent".equalsIgnoreCase(type) || "溶剂".equals(type)) {
            return "solvent";
        }
        if ("additive".equalsIgnoreCase(type) || "助剂".equals(type)) {
            return "additive";
        }
        if ("curing".equalsIgnoreCase(type) || "固化剂".equals(type)) {
            return "curing";
        }
        // 非标准类型保持原样，确保与导入表一一对应
        return type;
    }

    private String parseMaterialCategory(String rawCategory, String unitText, String specText) {
        String category = normalizeText(rawCategory);
        if (category != null) {
            if (category.contains("薄膜") || category.contains("原膜") || category.contains("PET膜") || category.equalsIgnoreCase("film")) {
                return "film";
            }
            if (category.contains("化工") || category.contains("原料") || category.equalsIgnoreCase("chemical")) {
                return "chemical";
            }
        }
        String unit = normalizeText(unitText);
        String spec = normalizeText(specText);
        if ((unit != null && unit.contains("m²")) || (spec != null && (spec.contains("卷") || spec.contains("PET膜") || spec.contains("原膜")))) {
            return "film";
        }
        return "chemical";
    }

    private String normalizeMaterialCategory(String category, String unitText) {
        String value = normalizeText(category);
        if (value != null) {
            if (value.contains("薄膜") || value.contains("原膜") || value.contains("PET膜") || value.equalsIgnoreCase("film")) {
                return "film";
            }
            if (value.contains("化工") || value.equalsIgnoreCase("chemical")) {
                return "chemical";
            }
        }
        String unit = normalizeText(unitText);
        if (unit != null && unit.contains("m²")) {
            return "film";
        }
        return "chemical";
    }

    private String safeText(String value) {
        return value == null ? "" : value;
    }

    private Map<String, Object> parsePerformanceParamsMap(String performanceParams) {
        String text = normalizeText(performanceParams);
        if (text == null) {
            return Collections.emptyMap();
        }
        try {
            Map<String, Object> map = OBJECT_MAPPER.readValue(text, new TypeReference<Map<String, Object>>() {});
            return map == null ? Collections.emptyMap() : map;
        } catch (Exception ex) {
            return Collections.emptyMap();
        }
    }

    private String readPerfText(Map<String, Object> perfMap, String key, String... aliases) {
        Object value = null;
        if (perfMap != null) {
            value = perfMap.get(key);
            if (value == null && aliases != null) {
                for (String alias : aliases) {
                    value = perfMap.get(alias);
                    if (value != null) {
                        break;
                    }
                }
            }
        }
        return formatPerfValue(value);
    }

    private String formatPerfValue(Object value) {
        if (value == null) {
            return "";
        }
        if (!(value instanceof Map)) {
            String text = normalizeText(String.valueOf(value));
            return text == null ? "" : text;
        }

        Map<?, ?> map = (Map<?, ?>) value;
        String standardValue = normalizeText(objectToString(map.get("standardValue")));
        String min = normalizeText(objectToString(map.get("min")));
        String max = normalizeText(objectToString(map.get("max")));
        String unit = normalizeText(objectToString(map.get("unit")));
        String judgeMode = normalizeText(objectToString(map.get("judgeMode")));
        String suffix = unit == null ? "" : unit;

        if (("value".equalsIgnoreCase(judgeMode) || (standardValue != null && min == null && max == null)) && standardValue != null) {
            return standardValue + suffix;
        }
        if (("min".equalsIgnoreCase(judgeMode) || (min != null && max == null)) && min != null) {
            return "≥" + min + suffix;
        }
        if (("max".equalsIgnoreCase(judgeMode) || (max != null && min == null)) && max != null) {
            return "≤" + max + suffix;
        }
        if (min != null && max != null) {
            return min + "~" + max + suffix;
        }
        if (standardValue != null) {
            return standardValue + suffix;
        }
        return "";
    }

    private String mergeImportedPerformanceParams(String legacyPerformanceText,
                                                  String thickness,
                                                  String coronaValue,
                                                  String tensileStrength,
                                                  String elongation,
                                                  String color,
                                                  String appearance,
                                                  String solidContent,
                                                  String viscosity,
                              String peelStrength,
                              String releaseForceA,
                              String releaseForceB) {
        boolean hasNewPerfColumns = normalizeText(thickness) != null
                || normalizeText(coronaValue) != null
                || normalizeText(tensileStrength) != null
                || normalizeText(elongation) != null
                || normalizeText(color) != null
                || normalizeText(appearance) != null
                || normalizeText(solidContent) != null
                || normalizeText(viscosity) != null
            || normalizeText(peelStrength) != null
            || normalizeText(releaseForceA) != null
            || normalizeText(releaseForceB) != null;

        String legacy = normalizeText(legacyPerformanceText);
        if (!hasNewPerfColumns) {
            return legacy;
        }

        Map<String, Object> root = new LinkedHashMap<>();
        if (legacy != null) {
            try {
                Map<String, Object> old = OBJECT_MAPPER.readValue(legacy, new TypeReference<Map<String, Object>>() {});
                if (old != null) {
                    root.putAll(old);
                }
            } catch (Exception ignore) {
                // 非JSON旧值忽略，按新九列生成
            }
        }

        root.remove("thickness");
        root.remove("coronaValue");
        root.remove("tensileStrength");
        root.remove("elongation");
        root.remove("color");
        root.remove("appearance");
        root.remove("solidContent");
        root.remove("viscosity");
        root.remove("peelStrength");
        root.remove("releaseForceA");
        root.remove("releaseForceB");

        upsertImportedPerfValue(root, "thickness", "厚度", "μm", thickness);
        upsertImportedPerfValue(root, "coronaValue", "电晕值", "dyne", coronaValue);
        upsertImportedPerfValue(root, "tensileStrength", "抗拉强度", "N/15mm", tensileStrength);
        upsertImportedPerfValue(root, "elongation", "伸长率", "%", elongation);
        upsertImportedPerfValue(root, "color", "颜色", "", color);
        upsertImportedPerfValue(root, "appearance", "外观", "", appearance);
        upsertImportedPerfValue(root, "solidContent", "固含量", "%", solidContent);
        upsertImportedPerfValue(root, "viscosity", "粘度", "cps", viscosity);
        upsertImportedPerfValue(root, "peelStrength", "剥离强度", "N/25mm", peelStrength);
        upsertImportedPerfValue(root, "releaseForceA", "离型力A", "gf/in", releaseForceA);
        upsertImportedPerfValue(root, "releaseForceB", "离型力B", "gf/in", releaseForceB);

        if (root.isEmpty()) {
            return null;
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(root);
        } catch (Exception ex) {
            return legacy;
        }
    }

    private void upsertImportedPerfValue(Map<String, Object> root, String key, String label, String defaultUnit, String rawText) {
        String text = normalizeText(rawText);
        if (text == null) {
            return;
        }
        Map<String, String> parsed = parseImportedPerfRange(text, defaultUnit);
        parsed.put("label", label);
        String currentUnit = normalizeText(parsed.get("unit"));
        parsed.put("unit", currentUnit == null ? defaultUnit : currentUnit);
        root.put(key, parsed);
    }

    private Map<String, String> parseImportedPerfRange(String rawText, String defaultUnit) {
        String text = normalizeText(rawText);
        Map<String, String> result = new LinkedHashMap<>();
        if (text == null) {
            return result;
        }

        Matcher plusMinus = Pattern.compile("^([+-]?\\d+(?:\\.\\d+)?)\\s*±\\s*([+-]?\\d+(?:\\.\\d+)?)\\s*(.*)$").matcher(text);
        if (plusMinus.matches()) {
            BigDecimal center = toBigDecimal(plusMinus.group(1));
            BigDecimal tolerance = toBigDecimal(plusMinus.group(2));
            if (center != null && tolerance != null) {
                result.put("standardValue", center.stripTrailingZeros().toPlainString());
                result.put("min", center.subtract(tolerance).stripTrailingZeros().toPlainString());
                result.put("max", center.add(tolerance).stripTrailingZeros().toPlainString());
                result.put("unit", normalizeText(plusMinus.group(3)) == null ? defaultUnit : normalizeText(plusMinus.group(3)));
                result.put("judgeMode", "range");
                result.put("remark", "");
                return result;
            }
        }

        Matcher range = Pattern.compile("^([+-]?\\d+(?:\\.\\d+)?)\\s*[~～-]\\s*([+-]?\\d+(?:\\.\\d+)?)\\s*(.*)$").matcher(text);
        if (range.matches()) {
            result.put("standardValue", "");
            result.put("min", normalizeText(range.group(1)));
            result.put("max", normalizeText(range.group(2)));
            result.put("unit", normalizeText(range.group(3)) == null ? defaultUnit : normalizeText(range.group(3)));
            result.put("judgeMode", "range");
            result.put("remark", "");
            return result;
        }

        Matcher slashRange = Pattern.compile("^([+-]?\\d+(?:\\.\\d+)?)\\s*/\\s*([+-]?\\d+(?:\\.\\d+)?)\\s*(.*)$").matcher(text);
        if (slashRange.matches()) {
            result.put("standardValue", "");
            result.put("min", normalizeText(slashRange.group(1)));
            result.put("max", normalizeText(slashRange.group(2)));
            result.put("unit", normalizeText(slashRange.group(3)) == null ? defaultUnit : normalizeText(slashRange.group(3)));
            result.put("judgeMode", "range");
            result.put("remark", "");
            return result;
        }

        Matcher ge = Pattern.compile("^[≥>]\\s*([+-]?\\d+(?:\\.\\d+)?)\\s*(.*)$").matcher(text);
        if (ge.matches()) {
            result.put("standardValue", "");
            result.put("min", normalizeText(ge.group(1)));
            result.put("max", "");
            result.put("unit", normalizeText(ge.group(2)) == null ? defaultUnit : normalizeText(ge.group(2)));
            result.put("judgeMode", "min");
            result.put("remark", "");
            return result;
        }

        Matcher le = Pattern.compile("^[≤<]\\s*([+-]?\\d+(?:\\.\\d+)?)\\s*(.*)$").matcher(text);
        if (le.matches()) {
            result.put("standardValue", "");
            result.put("min", "");
            result.put("max", normalizeText(le.group(1)));
            result.put("unit", normalizeText(le.group(2)) == null ? defaultUnit : normalizeText(le.group(2)));
            result.put("judgeMode", "max");
            result.put("remark", "");
            return result;
        }

        Matcher numberWithUnit = NUMBER_WITH_UNIT_PATTERN.matcher(text);
        if (numberWithUnit.matches()) {
            result.put("standardValue", normalizeText(numberWithUnit.group(1)));
            String parsedUnit = normalizeText(numberWithUnit.group(2));
            result.put("unit", parsedUnit == null ? defaultUnit : parsedUnit);
        } else {
            result.put("standardValue", text);
            result.put("unit", defaultUnit);
        }
        result.put("min", "");
        result.put("max", "");
        result.put("judgeMode", "value");
        result.put("remark", "");
        return result;
    }

    private BigDecimal toBigDecimal(String value) {
        String text = normalizeText(value);
        if (text == null) {
            return null;
        }
        try {
            return new BigDecimal(text);
        } catch (Exception ex) {
            return null;
        }
    }

    @SuppressWarnings("unused")
    private void enrichRawMaterialMeta(List<TapeRawMaterial> list) {
        if (list == null || list.isEmpty()) {
            return;
        }
        for (TapeRawMaterial material : list) {
            enrichRawMaterialMeta(material);
        }
    }

    private void enrichRawMaterialMeta(TapeRawMaterial material) {
        if (material == null) {
            return;
        }
        String spec = normalizeText(material.getSpec());
        String major = normalizeText(material.getMaterialMajor());
        if (major == null) {
            major = extractSpecToken(spec, "物料大类");
        }
        String typeText = normalizeText(material.getMaterialCategoryRaw());
        if (typeText == null) {
            typeText = extractSpecToken(spec, "物料类别");
        }
        String perfB64 = extractSpecToken(spec, "性能参数B64");

        String category = normalizeMaterialCategory(material.getMaterialCategory(), material.getUnit());
        if (category == null || category.trim().isEmpty()) {
            category = parseMaterialCategory(major, material.getUnit(), spec);
        }

        String type = normalizeText(material.getMaterialType());
        if (typeText != null) {
            type = normalizeMaterialTypeValue(typeText);
        } else if (type == null) {
            type = normalizeMaterialTypeValue(null);
        }

        material.setMaterialCategory(category);
        material.setMaterialType(type);

        String perf = normalizeText(material.getPerformanceParams());
        if (perf == null) {
            perf = decodePerformanceParams(perfB64);
        }
        material.setPerformanceParams(perf);
    }

    private String normalizeMaterialTypeValue(String rawType) {
        String type = normalizeText(rawType);
        if (type == null) {
            return "resin";
        }
        if ("resin".equalsIgnoreCase(type) || "树脂".equals(type)) return "resin";
        if ("solvent".equalsIgnoreCase(type) || "溶剂".equals(type)) return "solvent";
        if ("additive".equalsIgnoreCase(type) || "助剂".equals(type)) return "additive";
        if ("curing".equalsIgnoreCase(type) || "固化剂".equals(type)) return "curing";

        // 保留导入原值，不做模糊归一，避免与原始表不一致
        return type;
    }

    @SuppressWarnings("unused")
    private String mergeSpecWithMeta(String spec, String materialCategory, String materialType, String performanceParams) {
        String merged = normalizeText(spec);
        if (merged != null && merged.length() > SPEC_MAX_LENGTH) {
            merged = merged.substring(0, SPEC_MAX_LENGTH);
        }
        return merged;
    }

    @SuppressWarnings("unused")
    private String encodePerformanceParams(String performanceParams) {
        String text = normalizeText(performanceParams);
        if (text == null) {
            return null;
        }
        try {
            String compact = compactPerformanceParams(text);
            String compactEncoded = compact == null ? null : ("Q1:" + compact);

            String rawB64 = Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8));

            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            try (GZIPOutputStream gzip = new GZIPOutputStream(bos)) {
                gzip.write(text.getBytes(StandardCharsets.UTF_8));
            }
            String gzipB64 = Base64.getEncoder().encodeToString(bos.toByteArray());
            String gzipEncoded = "GZ:" + gzipB64;

            String best = gzipEncoded.length() < rawB64.length() ? gzipEncoded : rawB64;
            if (compactEncoded != null && compactEncoded.length() < best.length()) {
                best = compactEncoded;
            }
            return best;
        } catch (Exception e) {
            return null;
        }
    }

    private String decodePerformanceParams(String encoded) {
        String text = normalizeText(encoded);
        if (text == null) {
            return null;
        }
        try {
            if (text.startsWith("Q1:")) {
                return normalizeText(expandCompactPerformanceParams(text.substring(3)));
            }
            if (text.startsWith("GZ:")) {
                byte[] compressed = Base64.getDecoder().decode(text.substring(3));
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                try (GZIPInputStream gzipIn = new GZIPInputStream(new ByteArrayInputStream(compressed))) {
                    byte[] buffer = new byte[512];
                    int n;
                    while ((n = gzipIn.read(buffer)) >= 0) {
                        bos.write(buffer, 0, n);
                    }
                }
                return normalizeText(bos.toString(StandardCharsets.UTF_8.name()));
            }

            byte[] bytes = Base64.getDecoder().decode(text);
            return normalizeText(new String(bytes, StandardCharsets.UTF_8));
        } catch (Exception e) {
            return null;
        }
    }

    private String compactPerformanceParams(String jsonText) {
        try {
            Map<String, Object> root = OBJECT_MAPPER.readValue(jsonText, new TypeReference<Map<String, Object>>() {});
            if (root == null || root.isEmpty()) {
                return null;
            }

            Map<String, String> keyMap = new LinkedHashMap<>();
            keyMap.put("solidContent", "sc");
            keyMap.put("peelStrength", "ps");
            keyMap.put("widthInspection", "wi");
            keyMap.put("thicknessTensile", "tt");
            keyMap.put("transverseTensile", "vt");
            keyMap.put("thicknessElongation", "te");
            keyMap.put("transverseElongation", "ve");
            keyMap.put("coronaBothSides", "cb");

            List<String> segments = new ArrayList<>();
            for (Map.Entry<String, String> e : keyMap.entrySet()) {
                Object v = root.get(e.getKey());
                if (!(v instanceof Map)) {
                    continue;
                }
                Map<?, ?> item = (Map<?, ?>) v;
                String standardValue = normalizeText(objectToString(item.get("standardValue")));
                String min = normalizeText(objectToString(item.get("min")));
                String max = normalizeText(objectToString(item.get("max")));
                String unit = normalizeText(objectToString(item.get("unit")));
                String judgeMode = normalizeText(objectToString(item.get("judgeMode")));
                String remark = normalizeText(objectToString(item.get("remark")));

                if (standardValue == null && min == null && max == null && unit == null && judgeMode == null && remark == null) {
                    continue;
                }

                segments.add(String.join("~",
                        e.getValue(),
                        urlEncodePart(standardValue),
                        urlEncodePart(min),
                        urlEncodePart(max),
                        urlEncodePart(unit),
                        urlEncodePart(judgeMode),
                        urlEncodePart(remark)));
            }
            return segments.isEmpty() ? null : String.join("|", segments);
        } catch (Exception ex) {
            return null;
        }
    }

    private String expandCompactPerformanceParams(String compactText) {
        String text = normalizeText(compactText);
        if (text == null) {
            return null;
        }
        Map<String, String> reverseMap = new HashMap<>();
        reverseMap.put("sc", "solidContent");
        reverseMap.put("ps", "peelStrength");
        reverseMap.put("wi", "widthInspection");
        reverseMap.put("tt", "thicknessTensile");
        reverseMap.put("vt", "transverseTensile");
        reverseMap.put("te", "thicknessElongation");
        reverseMap.put("ve", "transverseElongation");
        reverseMap.put("cb", "coronaBothSides");

        Map<String, Object> root = new LinkedHashMap<>();
        try {
            String[] segments = text.split("\\|");
            for (String seg : segments) {
                String s = normalizeText(seg);
                if (s == null) continue;
                String[] p = s.split("~", -1);
                if (p.length < 1) continue;
                String fullKey = reverseMap.get(normalizeText(p[0]));
                if (fullKey == null) continue;

                Map<String, String> item = new LinkedHashMap<>();
                putIfPresent(item, "standardValue", urlDecodePart(safePart(p, 1)));
                putIfPresent(item, "min", urlDecodePart(safePart(p, 2)));
                putIfPresent(item, "max", urlDecodePart(safePart(p, 3)));
                putIfPresent(item, "unit", urlDecodePart(safePart(p, 4)));
                putIfPresent(item, "judgeMode", urlDecodePart(safePart(p, 5)));
                putIfPresent(item, "remark", urlDecodePart(safePart(p, 6)));
                if (!item.isEmpty()) {
                    root.put(fullKey, item);
                }
            }
            return root.isEmpty() ? null : OBJECT_MAPPER.writeValueAsString(root);
        } catch (Exception ex) {
            return null;
        }
    }

    private String objectToString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String safePart(String[] parts, int index) {
        if (parts == null || index < 0 || index >= parts.length) {
            return null;
        }
        return parts[index];
    }

    private void putIfPresent(Map<String, String> target, String key, String value) {
        String normalized = normalizeText(value);
        if (normalized != null) {
            target.put(key, normalized);
        }
    }

    private String urlEncodePart(String value) {
        String text = normalizeText(value);
        if (text == null) {
            return "";
        }
        try {
            return URLEncoder.encode(text, StandardCharsets.UTF_8.name());
        } catch (Exception e) {
            return "";
        }
    }

    private String urlDecodePart(String value) {
        String text = normalizeText(value);
        if (text == null) {
            return null;
        }
        try {
            return java.net.URLDecoder.decode(text, StandardCharsets.UTF_8.name());
        } catch (Exception e) {
            return text;
        }
    }

    @SuppressWarnings("unused")
    private String materialTypeDisplay(String materialType) {
        if (materialType == null) {
            return "树脂";
        }
        switch (materialType) {
            case "resin":
                return "树脂";
            case "solvent":
                return "溶剂";
            case "additive":
                return "助剂";
            case "curing":
                return "固化剂";
            default:
                return materialType;
        }
    }

    private String extractSpecToken(String spec, String key) {
        String text = normalizeText(spec);
        String normalizedKey = normalizeText(key);
        if (text == null || normalizedKey == null) {
            return null;
        }
        String[] parts = text.split("；");
        for (String part : parts) {
            String p = normalizeText(part);
            if (p != null && p.startsWith(normalizedKey + ":")) {
                return normalizeText(p.substring((normalizedKey + ":").length()));
            }
        }
        return null;
    }

    @SuppressWarnings("unused")
    private String upsertSpecToken(String spec, String key, String value) {
        String normalizedKey = normalizeText(key);
        String normalizedValue = normalizeText(value);
        if (normalizedKey == null) {
            return normalizeText(spec);
        }

        List<String> parts = new ArrayList<>();
        String text = normalizeText(spec);
        if (text != null) {
            for (String part : text.split("；")) {
                String p = normalizeText(part);
                if (p == null) continue;
                if (!p.startsWith(normalizedKey + ":")) {
                    parts.add(p);
                }
            }
        }
        if (normalizedValue != null) {
            parts.add(normalizedKey + ":" + normalizedValue);
        }
        return String.join("；", parts);
    }
    
    private Date getCellDateValue(Cell cell) {
        if (cell == null) return null;
        try {
            if (cell.getCellType() == CellType.NUMERIC) {
                return cell.getDateCellValue();
            } else if (cell.getCellType() == CellType.STRING) {
                String value = cell.getStringCellValue().trim();
                if (value.isEmpty()) return null;
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
                return sdf.parse(value);
            }
        } catch (Exception e) {
            // ignore
        }
        return null;
    }

    private Map<String, Integer> buildHeaderIndexMap(Row headerRow) {
        Map<String, Integer> result = new HashMap<>();
        if (headerRow == null) {
            return result;
        }
        short lastCellNum = headerRow.getLastCellNum();
        for (int i = 0; i < lastCellNum; i++) {
            String header = normalizeText(getCellStringValue(headerRow.getCell(i)));
            if (header != null) {
                result.put(normalizeHeaderKey(header), i);
            }
        }
        return result;
    }

    private int findColumnIndex(Map<String, Integer> headerIndexMap, String... headerNames) {
        if (headerNames == null) {
            return -1;
        }
        for (String headerName : headerNames) {
            Integer idx = headerIndexMap.get(normalizeHeaderKey(headerName));
            if (idx != null) {
                return idx;
            }
        }
        return -1;
    }

    private String normalizeHeaderKey(String key) {
        String text = normalizeText(key);
        if (text == null) {
            return null;
        }
        return text
                .replace("（", "(")
                .replace("）", ")")
                .replace("　", "")
                .replace(" ", "")
                .toLowerCase();
    }

}
