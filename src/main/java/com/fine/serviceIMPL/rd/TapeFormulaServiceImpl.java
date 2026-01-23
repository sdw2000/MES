package com.fine.serviceIMPL.rd;

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

import javax.servlet.http.HttpServletResponse;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * 配胶标准单服务实现
 */
@Service
public class TapeFormulaServiceImpl implements TapeFormulaService {

    @Autowired
    private TapeFormulaMapper tapeFormulaMapper;

    @Override
    public ResponseResult<?> getList(int page, int size, String materialCode, String productName,
                                     String glueModel, Integer status) {
        int offset = (page - 1) * size;
        List<TapeFormula> list = tapeFormulaMapper.selectList(materialCode, productName, glueModel, status, offset, size);
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
        List<TapeFormulaItem> items = tapeFormulaMapper.selectItemsByFormulaId(formula.getId());
        formula.setItems(items);
        return new ResponseResult<>(20000, "查询成功", formula);
    }

    @Override
    @Transactional
    public ResponseResult<?> create(TapeFormula formula, String operator) {
        // 检查料号是否重复
        if (tapeFormulaMapper.checkMaterialCodeExists(formula.getMaterialCode(), 0L) > 0) {
            return new ResponseResult<>(50000, "该产品料号已存在配方");
        }

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

        // 检查料号是否重复（排除自己）
        if (tapeFormulaMapper.checkMaterialCodeExists(formula.getMaterialCode(), formula.getId()) > 0) {
            return new ResponseResult<>(50000, "该产品料号已存在其他配方");
        }

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
    public ResponseResult<?> createRawMaterial(TapeRawMaterial material) {
        material.setStatus(1);
        tapeFormulaMapper.insertRawMaterial(material);
        return new ResponseResult<>(20000, "创建成功", material);
    }

    @Override
    public ResponseResult<?> updateRawMaterial(TapeRawMaterial material) {
        tapeFormulaMapper.updateRawMaterial(material);
        return new ResponseResult<>(20000, "更新成功");
    }

    @Override
    public ResponseResult<?> deleteRawMaterial(Long id) {
        tapeFormulaMapper.deleteRawMaterial(id);
        return new ResponseResult<>(20000, "删除成功");
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
    }    private void createCell(Row row, int column, String value, CellStyle style) {
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
                    "胶水型号", "颜色代码", "涂胶厚度(μm)", "胶水密度(g/cm³)", "固含量(%)", 
                    "涂布数量(㎡)", "工艺备注", "编制人", "审核人", "批准人"};
            for (int i = 0; i < mainHeaders.length; i++) {
                Cell cell = header1.createCell(i);
                cell.setCellValue(mainHeaders[i]);
                mainSheet.setColumnWidth(i, 4500);
            }
            
            // 示例数据
            Row example1 = mainSheet.createRow(1);
            example1.createCell(0).setCellValue("1011-R02-1204-G01-0300");
            example1.createCell(1).setCellValue("16μm翠绿PET终止胶带");
            example1.createCell(2).setCellValue("107");
            example1.createCell(3).setCellValue("A/0");
            example1.createCell(4).setCellValue("2025-12-08");
            example1.createCell(5).setCellValue("YKLJ0801G01040300");
            example1.createCell(6).setCellValue("G01");
            example1.createCell(7).setCellValue(5);
            example1.createCell(8).setCellValue(1.1);
            example1.createCell(9).setCellValue("15±2");
            example1.createCell(10).setCellValue(24000);
            example1.createCell(11).setCellValue("温度：70 80 120 120 120 90 80 70，速度40m");
            example1.createCell(12).setCellValue("张三");
            example1.createCell(13).setCellValue("李四");
            example1.createCell(14).setCellValue("王五");
            
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
                    formula.setColorCode(getCellStringValue(row.getCell(6)));
                    formula.setCoatingThickness(getCellBigDecimalValue(row.getCell(7)));
                    formula.setGlueDensity(getCellBigDecimalValue(row.getCell(8)));
                    formula.setSolidContent(getCellStringValue(row.getCell(9)));
                    formula.setCoatingArea(getCellBigDecimalValue(row.getCell(10)));
                    formula.setProcessRemark(getCellStringValue(row.getCell(11)));
                    formula.setPreparedBy(getCellStringValue(row.getCell(12)));
                    formula.setReviewedBy(getCellStringValue(row.getCell(13)));
                    formula.setApprovedBy(getCellStringValue(row.getCell(14)));
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
                    "胶水型号", "颜色代码", "涂胶厚度(μm)", "胶水密度(g/cm³)", "固含量(%)", 
                    "涂布数量(㎡)", "工艺备注", "总重量(kg)", "编制人", "审核人", "批准人", "状态"};
            
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
                row.createCell(6).setCellValue(f.getColorCode() != null ? f.getColorCode() : "");
                row.createCell(7).setCellValue(f.getCoatingThickness() != null ? f.getCoatingThickness().doubleValue() : 0);
                row.createCell(8).setCellValue(f.getGlueDensity() != null ? f.getGlueDensity().doubleValue() : 0);
                row.createCell(9).setCellValue(f.getSolidContent() != null ? f.getSolidContent() : "");
                row.createCell(10).setCellValue(f.getCoatingArea() != null ? f.getCoatingArea().doubleValue() : 0);
                row.createCell(11).setCellValue(f.getProcessRemark() != null ? f.getProcessRemark() : "");
                row.createCell(12).setCellValue(f.getTotalWeight() != null ? f.getTotalWeight().doubleValue() : 0);
                row.createCell(13).setCellValue(f.getPreparedBy() != null ? f.getPreparedBy() : "");
                row.createCell(14).setCellValue(f.getReviewedBy() != null ? f.getReviewedBy() : "");
                row.createCell(15).setCellValue(f.getApprovedBy() != null ? f.getApprovedBy() : "");
                row.createCell(16).setCellValue(f.getStatus() != null && f.getStatus() == 1 ? "启用" : "禁用");
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
        cell.setCellType(CellType.STRING);
        String value = cell.getStringCellValue();
        return value != null ? value.trim() : null;
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
        if (cell == null) return 0;
        try {
            if (cell.getCellType() == CellType.NUMERIC) {
                return (int) cell.getNumericCellValue();
            } else if (cell.getCellType() == CellType.STRING) {
                String value = cell.getStringCellValue().trim();
                if (value.isEmpty()) return 0;
                return Integer.parseInt(value);
            }
        } catch (Exception e) {
            // ignore
        }
        return 0;
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
}
