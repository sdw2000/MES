package com.fine.serviceIMPL.purchase;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fine.Dao.purchase.PurchaseSupplierContactMapper;
import com.fine.Dao.purchase.PurchaseSupplierMapper;
import com.fine.Utils.ResponseResult;
import com.fine.modle.purchase.PurchaseSupplier;
import com.fine.modle.purchase.PurchaseSupplierContact;
import com.fine.service.purchase.PurchaseSupplierService;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletResponse;
import java.io.OutputStream;
import java.util.Date;
import java.util.List;
import java.net.URLEncoder;

@Service
public class PurchaseSupplierServiceImpl extends ServiceImpl<PurchaseSupplierMapper, PurchaseSupplier> implements PurchaseSupplierService {

    private static final Logger log = LoggerFactory.getLogger(PurchaseSupplierServiceImpl.class);

    private final PurchaseSupplierContactMapper contactMapper;

    public PurchaseSupplierServiceImpl(PurchaseSupplierContactMapper contactMapper) {
        this.contactMapper = contactMapper;
    }

    @Override
    public ResponseResult<?> listSuppliers(String keyword, Integer page, Integer size) {
        Page<PurchaseSupplier> p = new Page<>(page == null ? 1 : page, size == null ? 20 : size);
        LambdaQueryWrapper<PurchaseSupplier> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PurchaseSupplier::getIsDeleted, 0);
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(PurchaseSupplier::getSupplierName, keyword)
                    .or().like(PurchaseSupplier::getSupplierCode, keyword)
                    .or().like(PurchaseSupplier::getShortName, keyword));
        }
        wrapper.orderByDesc(PurchaseSupplier::getCreatedAt);
        Page<PurchaseSupplier> result = this.page(p, wrapper);
        return ResponseResult.success(result);
    }

    @Override
    public ResponseResult<?> saveSupplier(PurchaseSupplier supplier) {
        Date now = new Date();
        if (supplier.getId() == null) {
            supplier.setCreatedAt(now);
            supplier.setUpdatedAt(now);
            supplier.setIsDeleted(0);
            this.save(supplier);
        } else {
            supplier.setUpdatedAt(now);
            this.updateById(supplier);
        }

        // 保存联系人（先清除旧的再插入新的）
        // 仅当请求中传入了有效联系人数据时才操作联系人表，避免导入等场景因联系人表缺失导致失败
        if (supplier.getId() != null && hasValidContacts(supplier.getContacts())) {
            try {
                contactMapper.deleteBySupplierId(supplier.getId());
                for (PurchaseSupplierContact c : supplier.getContacts()) {
                    c.setSupplierId(supplier.getId());
                    c.setIsDeleted(0);
                    c.setCreatedAt(now);
                    c.setUpdatedAt(now);
                    if (c.getIsPrimary() == null) c.setIsPrimary(0);
                    if (c.getIsDecisionMaker() == null) c.setIsDecisionMaker(0);
                    contactMapper.insert(c);
                }
            } catch (Exception ex) {
                String msg = ex.getMessage() == null ? "" : ex.getMessage();
                if (msg.contains("purchase_supplier_contacts") || msg.contains("doesn't exist")) {
                    log.warn("联系人表不存在，已跳过联系人保存。supplierId={}", supplier.getId());
                } else {
                    throw ex;
                }
            }
        }

        return ResponseResult.success(supplier);
    }

    @Override
    public ResponseResult<?> deleteSupplier(Long id) {
        PurchaseSupplier supplier = this.getById(id);
        if (supplier == null) {
            return new ResponseResult<>(404, "供应商不存在");
        }
        try {
            contactMapper.deleteBySupplierId(id);
        } catch (Exception ex) {
            String msg = ex.getMessage() == null ? "" : ex.getMessage();
            if (!msg.contains("purchase_supplier_contacts") && !msg.contains("doesn't exist")) {
                throw ex;
            }
            log.warn("联系人表不存在，已跳过关联联系人删除。supplierId={}", id);
        }
        this.removeById(id);
        return ResponseResult.success();
    }

    @Override
    public ResponseResult<?> getSupplierDetail(Long id) {
        PurchaseSupplier supplier = this.getById(id);
        if (supplier == null || (supplier.getIsDeleted() != null && supplier.getIsDeleted() == 1)) {
            return new ResponseResult<>(404, "供应商不存在");
        }
        List<PurchaseSupplierContact> contacts;
        try {
            contacts = contactMapper.selectBySupplierId(id);
        } catch (Exception ex) {
            String msg = ex.getMessage() == null ? "" : ex.getMessage();
            if (msg.contains("purchase_supplier_contacts") || msg.contains("doesn't exist")) {
                log.warn("联系人表不存在，返回空联系人列表。supplierId={}", id);
                contacts = java.util.Collections.emptyList();
            } else {
                throw ex;
            }
        }
        supplier.setContacts(contacts);
        return ResponseResult.success(supplier);
    }

    @Override
    public void exportSuppliers(HttpServletResponse response, String keyword) {
        try {
            LambdaQueryWrapper<PurchaseSupplier> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(PurchaseSupplier::getIsDeleted, 0);
            if (StringUtils.hasText(keyword)) {
                wrapper.and(w -> w.like(PurchaseSupplier::getSupplierName, keyword)
                        .or().like(PurchaseSupplier::getSupplierCode, keyword)
                        .or().like(PurchaseSupplier::getShortName, keyword));
            }
            wrapper.orderByDesc(PurchaseSupplier::getCreatedAt);
            List<PurchaseSupplier> list = this.list(wrapper);

            Workbook workbook = new XSSFWorkbook();
            Sheet sheet = workbook.createSheet("供应商表");
            String[] headers = {"供应商编码", "供应商名称", "简称", "联系人", "联系电话", "邮箱", "地址", "税号", "开户行", "账号", "状态(active/inactive)", "备注"};
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
                sheet.setColumnWidth(i, i == 6 ? 6000 : 4200);
            }

            int rowNum = 1;
            for (PurchaseSupplier supplier : list) {
                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(nvl(supplier.getSupplierCode()));
                row.createCell(1).setCellValue(nvl(supplier.getSupplierName()));
                row.createCell(2).setCellValue(nvl(supplier.getShortName()));
                row.createCell(3).setCellValue(nvl(supplier.getPrimaryContactName()));
                row.createCell(4).setCellValue(nvl(supplier.getPrimaryContactMobile()));
                row.createCell(5).setCellValue(nvl(supplier.getContactEmail()));
                row.createCell(6).setCellValue(nvl(supplier.getContactAddress()));
                row.createCell(7).setCellValue(nvl(supplier.getTaxNo()));
                row.createCell(8).setCellValue(nvl(supplier.getBankName()));
                row.createCell(9).setCellValue(nvl(supplier.getBankAccount()));
                row.createCell(10).setCellValue("inactive".equalsIgnoreCase(supplier.getStatus()) ? "inactive" : "active");
                row.createCell(11).setCellValue(nvl(supplier.getRemark()));
            }

            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setHeader("Content-Disposition", "attachment;filename=" + URLEncoder.encode("供应商表.xlsx", "UTF-8"));
            OutputStream out = response.getOutputStream();
            workbook.write(out);
            out.flush();
            out.close();
            workbook.close();
        } catch (Exception e) {
            throw new RuntimeException("导出供应商失败：" + e.getMessage(), e);
        }
    }

    @Override
    public void downloadTemplate(HttpServletResponse response) {
        try {
            Workbook workbook = new XSSFWorkbook();
            Sheet sheet = workbook.createSheet("供应商导入模板");
            String[] headers = {"供应商编码*", "供应商名称*", "简称", "联系人", "联系电话", "邮箱", "地址", "税号", "开户行", "账号", "状态(active/inactive)", "备注"};
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
                sheet.setColumnWidth(i, i == 6 ? 6000 : 4200);
            }

            Row sample = sheet.createRow(1);
            sample.createCell(0).setCellValue("SUP-001");
            sample.createCell(1).setCellValue("示例供应商有限公司");
            sample.createCell(2).setCellValue("示例供应商");
            sample.createCell(3).setCellValue("张三");
            sample.createCell(4).setCellValue("13800138000");
            sample.createCell(5).setCellValue("demo@supplier.com");
            sample.createCell(6).setCellValue("广东省深圳市南山区");
            sample.createCell(7).setCellValue("91440300XXXXXX");
            sample.createCell(8).setCellValue("中国银行深圳分行");
            sample.createCell(9).setCellValue("622202***********");
            sample.createCell(10).setCellValue("active");
            sample.createCell(11).setCellValue("采购初始化示例");

            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setHeader("Content-Disposition", "attachment;filename=" + URLEncoder.encode("供应商导入模板.xlsx", "UTF-8"));
            OutputStream out = response.getOutputStream();
            workbook.write(out);
            out.flush();
            out.close();
            workbook.close();
        } catch (Exception e) {
            throw new RuntimeException("下载模板失败：" + e.getMessage(), e);
        }
    }

    @Override
    public ResponseResult<?> importSuppliers(MultipartFile file) {
        int successCount = 0;
        int failCount = 0;
        List<String> errors = new java.util.ArrayList<>();
        try {
            Workbook workbook = WorkbookFactory.create(file.getInputStream());
            Sheet sheet = workbook.getSheetAt(0);
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) {
                    continue;
                }
                String supplierCode = normalizeCell(getCellStringValue(row.getCell(0)));
                String supplierName = normalizeCell(getCellStringValue(row.getCell(1)));
                if (!StringUtils.hasText(supplierCode) && !StringUtils.hasText(supplierName)) {
                    continue;
                }
                try {
                    if (!StringUtils.hasText(supplierCode)) {
                        throw new IllegalArgumentException("供应商编码不能为空");
                    }
                    if (!StringUtils.hasText(supplierName)) {
                        throw new IllegalArgumentException("供应商名称不能为空");
                    }

                    PurchaseSupplier supplier = new PurchaseSupplier();
                    supplier.setSupplierCode(supplierCode);
                    supplier.setSupplierName(supplierName);
                    supplier.setShortName(normalizeCell(getCellStringValue(row.getCell(2))));
                    supplier.setPrimaryContactName(normalizeCell(getCellStringValue(row.getCell(3))));
                    supplier.setPrimaryContactMobile(normalizeCell(getCellStringValue(row.getCell(4))));
                    supplier.setContactEmail(normalizeCell(getCellStringValue(row.getCell(5))));
                    supplier.setContactAddress(normalizeCell(getCellStringValue(row.getCell(6))));
                    supplier.setTaxNo(normalizeCell(getCellStringValue(row.getCell(7))));
                    supplier.setBankName(normalizeCell(getCellStringValue(row.getCell(8))));
                    supplier.setBankAccount(normalizeCell(getCellStringValue(row.getCell(9))));
                    String status = normalizeCell(getCellStringValue(row.getCell(10)));
                    supplier.setStatus("inactive".equalsIgnoreCase(status) ? "inactive" : "active");
                    supplier.setRemark(normalizeCell(getCellStringValue(row.getCell(11))));
                    supplier.setContacts(java.util.Collections.emptyList());

                    PurchaseSupplier existing = this.lambdaQuery().eq(PurchaseSupplier::getSupplierCode, supplierCode).eq(PurchaseSupplier::getIsDeleted, 0).one();
                    if (existing == null) {
                        saveSupplier(supplier);
                    } else {
                        supplier.setId(existing.getId());
                        saveSupplier(supplier);
                    }
                    successCount++;
                } catch (Exception ex) {
                    failCount++;
                    errors.add("第" + (i + 1) + "行（" + safeText(supplierCode) + "）:" + (ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage()));
                }
            }
            workbook.close();
        } catch (Exception e) {
            return new ResponseResult<>(50000, "导入失败：" + e.getMessage());
        }

        java.util.Map<String, Object> result = new java.util.HashMap<>();
        result.put("successCount", successCount);
        result.put("failCount", failCount);
        result.put("errors", errors);
        return new ResponseResult<>(20000, "导入完成", result);
    }

    private boolean hasValidContacts(List<PurchaseSupplierContact> contacts) {
        if (contacts == null || contacts.isEmpty()) {
            return false;
        }
        for (PurchaseSupplierContact c : contacts) {
            if (c == null) continue;
            if (StringUtils.hasText(c.getContactName())
                    || StringUtils.hasText(c.getContactPhone())
                    || StringUtils.hasText(c.getContactEmail())
                    || StringUtils.hasText(c.getContactWechat())) {
                return true;
            }
        }
        return false;
    }

    private String nvl(String value) {
        return value == null ? "" : value;
    }

    private String normalizeCell(String value) {
        return value == null ? null : value.trim();
    }

    private String getCellStringValue(Cell cell) {
        if (cell == null) {
            return null;
        }
        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue();
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    return new java.text.SimpleDateFormat("yyyy-MM-dd").format(cell.getDateCellValue());
                }
                double value = cell.getNumericCellValue();
                if (value == Math.rint(value)) {
                    return String.valueOf((long) value);
                }
                return String.valueOf(value);
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            case FORMULA:
                try {
                    return cell.getStringCellValue();
                } catch (Exception ignore) {
                    return String.valueOf(cell.getNumericCellValue());
                }
            default:
                return null;
        }
    }

    private String safeText(String value) {
        return value == null ? "" : value;
    }
}
