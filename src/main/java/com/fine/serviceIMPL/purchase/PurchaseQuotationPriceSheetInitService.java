package com.fine.serviceIMPL.purchase;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fine.Dao.purchase.PurchaseSupplierMapper;
import com.fine.Utils.ResponseResult;
import com.fine.modle.purchase.PurchaseQuotation;
import com.fine.modle.purchase.PurchaseQuotationItem;
import com.fine.modle.purchase.PurchaseSupplier;
import com.fine.service.purchase.PurchaseQuotationService;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class PurchaseQuotationPriceSheetInitService {

    private final PurchaseQuotationService quotationService;
    private final PurchaseSupplierMapper supplierMapper;

    public PurchaseQuotationPriceSheetInitService(PurchaseQuotationService quotationService,
                                                  PurchaseSupplierMapper supplierMapper) {
        this.quotationService = quotationService;
        this.supplierMapper = supplierMapper;
    }

    public ResponseResult<?> initializeFromPriceSheet(MultipartFile file, String operator) {
        if (file == null || file.isEmpty()) {
            return new ResponseResult<>(400, "请选择要导入的报价初始化文件");
        }

        Map<String, GroupedQuotation> grouped = new LinkedHashMap<>();
        List<String> warnings = new ArrayList<>();
        int rowCount = 0;

        try (InputStream inputStream = file.getInputStream();
             Workbook workbook = WorkbookFactory.create(inputStream)) {
            Sheet sheet = workbook.getSheetAt(0);
            if (sheet == null || sheet.getLastRowNum() < 1) {
                return new ResponseResult<>(400, "初始化文件为空或缺少数据行");
            }

            Map<String, Integer> headerIndex = resolveHeaders(sheet.getRow(0));
            if (!headerIndex.containsKey("supplierCode") || !headerIndex.containsKey("materialCode")
                    || !headerIndex.containsKey("unit") || !headerIndex.containsKey("unitPrice")) {
                return new ResponseResult<>(400, "表头至少需要包含：供应商代码、物料代码、单位、单价");
            }

            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) {
                    continue;
                }
                rowCount++;

                String supplierCode = readCellString(row, headerIndex.get("supplierCode"));
                String supplierName = readOptionalCellString(row, headerIndex.get("supplierName"));
                String materialCode = readCellString(row, headerIndex.get("materialCode"));
                String materialName = readOptionalCellString(row, headerIndex.get("materialName"));
                String unit = readCellString(row, headerIndex.get("unit"));
                BigDecimal unitPrice = readCellDecimal(row, headerIndex.get("unitPrice"));
                String specifications = readOptionalCellString(row, headerIndex.get("specifications"));
                String remark = readOptionalCellString(row, headerIndex.get("remark"));

                if (!StringUtils.hasText(supplierCode) || !StringUtils.hasText(materialCode) || unitPrice == null) {
                    warnings.add("第" + (i + 1) + "行缺少必要字段，已跳过");
                    continue;
                }

                GroupedQuotation groupedQuotation = grouped.computeIfAbsent(supplierCode, key -> new GroupedQuotation());
                groupedQuotation.supplierCode = supplierCode;
                if (!StringUtils.hasText(groupedQuotation.supplierName) && StringUtils.hasText(supplierName)) {
                    groupedQuotation.supplierName = supplierName;
                }

                PurchaseQuotationItem item = new PurchaseQuotationItem();
                item.setMaterialCode(materialCode);
                item.setMaterialName(materialName);
                item.setSpecifications(StringUtils.hasText(specifications) ? specifications : remark);
                item.setUnit(unit);
                item.setUnitPrice(unitPrice);
                item.setRemark(remark);
                groupedQuotation.items.add(item);
            }
        } catch (Exception e) {
            return new ResponseResult<>(500, "读取初始化文件失败：" + e.getMessage());
        }

        if (grouped.isEmpty()) {
            return new ResponseResult<>(400, "未识别到可初始化的数据");
        }

        int createdQuotations = 0;
        int createdItems = 0;
        List<String> failures = new ArrayList<>();

        for (GroupedQuotation groupedQuotation : grouped.values()) {
            try {
                PurchaseSupplier supplier = findSupplier(groupedQuotation.supplierCode);

                PurchaseQuotation quotation = new PurchaseQuotation();
                quotation.setSupplier(resolveSupplierDisplay(groupedQuotation, supplier));
                quotation.setContactPerson(supplier == null ? null : supplier.getPrimaryContactName());
                quotation.setContactPhone(supplier == null ? null : supplier.getPrimaryContactMobile());
                quotation.setQuotationDate(new Date());
                quotation.setValidUntil(new Date(System.currentTimeMillis() + 30L * 24 * 60 * 60 * 1000));
                quotation.setStatus("accepted");
                quotation.setRemark(buildRemark(groupedQuotation, operator, supplier));
                quotation.setItems(groupedQuotation.items);

                ResponseResult<?> result = quotationService.create(quotation);
                if (result != null && (result.getCode() == 200 || result.getCode() == 20000)) {
                    createdQuotations++;
                    createdItems += groupedQuotation.items.size();
                } else {
                    failures.add(groupedQuotation.supplierCode + " 初始化失败");
                }
            } catch (Exception ex) {
                failures.add(groupedQuotation.supplierCode + " 初始化失败：" + ex.getMessage());
            }
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("createdQuotations", createdQuotations);
        data.put("createdItems", createdItems);
        data.put("failedGroups", failures.size());
        data.put("warnings", warnings);
        data.put("failures", failures);
        data.put("totalRows", rowCount);
        return new ResponseResult<>(200, "报价初始化完成", data);
    }

    private PurchaseSupplier findSupplier(String supplierCode) {
        if (!StringUtils.hasText(supplierCode)) {
            return null;
        }
        return supplierMapper.selectOne(new LambdaQueryWrapper<PurchaseSupplier>()
                .eq(PurchaseSupplier::getIsDeleted, 0)
                .eq(PurchaseSupplier::getSupplierCode, supplierCode)
                .last("LIMIT 1"));
    }

    private String resolveSupplierDisplay(GroupedQuotation groupedQuotation, PurchaseSupplier supplier) {
        if (supplier != null && StringUtils.hasText(supplier.getSupplierName())) {
            return supplier.getSupplierName();
        }
        if (StringUtils.hasText(groupedQuotation.supplierName)) {
            return groupedQuotation.supplierName;
        }
        return groupedQuotation.supplierCode;
    }

    private String buildRemark(GroupedQuotation groupedQuotation, String operator, PurchaseSupplier supplier) {
        StringBuilder remark = new StringBuilder();
        remark.append("价格表初始化");
        if (StringUtils.hasText(groupedQuotation.supplierCode)) {
            remark.append(" | 供应商代码: ").append(groupedQuotation.supplierCode);
        }
        if (supplier != null && StringUtils.hasText(supplier.getSupplierName())) {
            remark.append(" | 供应商名称: ").append(supplier.getSupplierName());
        }
        if (StringUtils.hasText(operator)) {
            remark.append(" | 操作人: ").append(operator);
        }
        return remark.toString();
    }

    private Map<String, Integer> resolveHeaders(Row headerRow) {
        Map<String, Integer> headers = new LinkedHashMap<>();
        if (headerRow == null) {
            return headers;
        }
        for (int i = 0; i < headerRow.getLastCellNum(); i++) {
            String header = normalizeHeader(readCellString(headerRow, i));
            if (!StringUtils.hasText(header)) {
                continue;
            }
            if (header.contains("供应商") && (header.contains("代码") || header.contains("编码"))) {
                headers.putIfAbsent("supplierCode", i);
            } else if (header.contains("供应商") && header.contains("名称")) {
                headers.putIfAbsent("supplierName", i);
            } else if (header.contains("物料") && (header.contains("代码") || header.contains("编码") || header.contains("料号"))) {
                headers.putIfAbsent("materialCode", i);
            } else if (header.contains("物料") && header.contains("名称")) {
                headers.putIfAbsent("materialName", i);
            } else if (header.contains("规格")) {
                headers.putIfAbsent("specifications", i);
            } else if (header.equals("单位") || header.contains("单位")) {
                headers.putIfAbsent("unit", i);
            } else if (header.contains("单价")) {
                headers.putIfAbsent("unitPrice", i);
            } else if (header.contains("备注")) {
                headers.putIfAbsent("remark", i);
            }
        }
        return headers;
    }

    private String normalizeHeader(String value) {
        return value == null ? "" : value.replace("\u00A0", "").replace(" ", "").trim();
    }

    private String readCellString(Row row, Integer columnIndex) {
        if (row == null || columnIndex == null) {
            return null;
        }
        Cell cell = row.getCell(columnIndex);
        if (cell == null) {
            return null;
        }
        if (cell.getCellType() == CellType.NUMERIC) {
            double value = cell.getNumericCellValue();
            if (Math.floor(value) == value) {
                return String.valueOf((long) value);
            }
            return String.valueOf(value);
        }
        if (cell.getCellType() == CellType.BOOLEAN) {
            return String.valueOf(cell.getBooleanCellValue());
        }
        if (cell.getCellType() == CellType.FORMULA) {
            return cell.getCellFormula();
        }
        return cell.getStringCellValue() == null ? null : cell.getStringCellValue().trim();
    }

    private String readOptionalCellString(Row row, Integer columnIndex) {
        String value = readCellString(row, columnIndex);
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private BigDecimal readCellDecimal(Row row, Integer columnIndex) {
        String value = readCellString(row, columnIndex);
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return new BigDecimal(value.replace(",", "").trim());
        } catch (Exception ex) {
            return null;
        }
    }

    private static class GroupedQuotation {
        private String supplierCode;
        private String supplierName;
        private final List<PurchaseQuotationItem> items = new ArrayList<>();
    }
}