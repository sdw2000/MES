package com.fine.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fine.Dao.CustomerMaterialMappingMapper;
import com.fine.Utils.ResponseResult;
import com.fine.entity.CustomerMaterialMapping;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.multipart.MultipartFile;
import javax.annotation.PostConstruct;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/sales/customer-material-mapping")
@PreAuthorize("hasAnyAuthority('admin','sales','finance')")
public class CustomerMaterialMappingController {

    private static final Pattern SPEC_NUMBER_PATTERN = Pattern.compile("\\d+(?:\\.\\d+)?");
    private static final DataFormatter EXCEL_DATA_FORMATTER = new DataFormatter();

    @Autowired
    private CustomerMaterialMappingMapper mappingMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @PostConstruct
    public void ensureWidthToleranceColumn() {
        try {
            Integer cnt = jdbcTemplate.queryForObject(
                    "SELECT COUNT(1) FROM information_schema.COLUMNS " +
                            "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'customer_material_mapping' AND COLUMN_NAME = 'width_tolerance'",
                    Integer.class
            );
            if (cnt != null && cnt == 0) {
                jdbcTemplate.execute("ALTER TABLE customer_material_mapping ADD COLUMN width_tolerance DECIMAL(10,3) NULL COMMENT '宽度公差(mm)' AFTER customer_width");
            }
        } catch (Exception ignored) {
        }
    }

    @GetMapping("/page")
    public ResponseResult<?> page(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "20") Integer pageSize,
            @RequestParam(required = false) String customerCode,
            @RequestParam(required = false) String customerName,
            @RequestParam(required = false) String materialCode,
            @RequestParam(required = false) String customerSpec,
            @RequestParam(required = false) BigDecimal thickness,
            @RequestParam(required = false) BigDecimal width,
            @RequestParam(required = false) BigDecimal length,
            @RequestParam(required = false) BigDecimal customerThickness,
            @RequestParam(required = false) BigDecimal customerWidth,
                @RequestParam(required = false) BigDecimal widthTolerance,
            @RequestParam(required = false) BigDecimal customerLength,
            @RequestParam(required = false) Integer isActive
    ) {
        try {
            QueryWrapper<CustomerMaterialMapping> wrapper = new QueryWrapper<>();
            if (customerCode != null && !customerCode.trim().isEmpty()) {
                wrapper.eq("customer_code", customerCode.trim());
            }
            if (customerName != null && !customerName.trim().isEmpty()) {
                String keyword = "%" + customerName.trim() + "%";
                List<String> matchedCustomerCodes = jdbcTemplate.queryForList(
                        "SELECT customer_code FROM customers " +
                                "WHERE is_deleted = 0 AND (customer_name LIKE ? OR short_name LIKE ? OR customer_code LIKE ?)",
                        String.class,
                        keyword,
                        keyword,
                        keyword
                );
                if (matchedCustomerCodes == null || matchedCustomerCodes.isEmpty()) {
                    Page<CustomerMaterialMapping> emptyPage = new Page<>(pageNum, pageSize);
                    emptyPage.setRecords(new ArrayList<>());
                    emptyPage.setTotal(0L);
                    return ResponseResult.success(emptyPage);
                }
                wrapper.in("customer_code", matchedCustomerCodes);
            }
            if (materialCode != null && !materialCode.trim().isEmpty()) {
                wrapper.eq("material_code", materialCode.trim());
            }
            if (customerSpec != null && !customerSpec.trim().isEmpty()) {
                wrapper.like("customer_spec", customerSpec.trim());
            }
            if (thickness != null) {
                wrapper.eq("thickness", thickness);
            }
            if (width != null) {
                wrapper.eq("width", width);
            }
            if (length != null) {
                wrapper.eq("length", length);
            }
            if (customerThickness != null) {
                wrapper.eq("customer_thickness", customerThickness);
            }
            if (customerWidth != null) {
                wrapper.eq("customer_width", customerWidth);
            }
            if (widthTolerance != null) {
                wrapper.eq("width_tolerance", widthTolerance);
            }
            if (customerLength != null) {
                wrapper.eq("customer_length", customerLength);
            }
            if (isActive != null) {
                wrapper.eq("is_active", isActive);
            }
            wrapper.orderByDesc("update_time").orderByDesc("id");

            Page<CustomerMaterialMapping> page = new Page<>(pageNum, pageSize);
            Page<CustomerMaterialMapping> result = mappingMapper.selectPage(page, wrapper);
            return ResponseResult.success(result);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseResult.error("查询客户物料映射失败: " + e.getMessage());
        }
    }

    @PostMapping
    @Transactional(rollbackFor = Exception.class)
    public ResponseResult<?> save(@RequestBody CustomerMaterialMapping body) {
        try {
            if (body == null) {
                return ResponseResult.error("请求体不能为空");
            }
            String customerCode = body.getCustomerCode() == null ? "" : body.getCustomerCode().trim();
            String materialCode = body.getMaterialCode() == null ? "" : body.getMaterialCode().trim();

            if (customerCode.isEmpty()) {
                return ResponseResult.error("customerCode不能为空");
            }
            if (materialCode.isEmpty()) {
                return ResponseResult.error("materialCode不能为空");
            }
            if (body.getThickness() == null) {
                return ResponseResult.error("thickness不能为空");
            }
            if (body.getWidth() == null) {
                return ResponseResult.error("width不能为空");
            }
            if (body.getLength() == null) {
                return ResponseResult.error("length不能为空");
            }

            LocalDateTime now = LocalDateTime.now();
            String user = body.getUpdateBy() == null || body.getUpdateBy().trim().isEmpty() ? "system" : body.getUpdateBy().trim();

            CustomerMaterialMapping entity;
            if (body.getId() != null) {
                entity = mappingMapper.selectById(body.getId());
                if (entity == null) {
                    return ResponseResult.error("记录不存在");
                }
            } else {
                QueryWrapper<CustomerMaterialMapping> dupWrapper = new QueryWrapper<>();
                dupWrapper.eq("customer_code", customerCode)
                        .eq("material_code", materialCode)
                    .eq("thickness", body.getThickness())
                    .eq("width", body.getWidth())
                    .eq("length", body.getLength())
                        .last("limit 1");
                entity = mappingMapper.selectOne(dupWrapper);
                if (entity == null) {
                    entity = new CustomerMaterialMapping();
                    entity.setCreateBy(user);
                    entity.setCreateTime(now);
                }
            }

            entity.setCustomerCode(customerCode);
            entity.setMaterialCode(materialCode);
            entity.setThickness(body.getThickness());
            entity.setWidth(body.getWidth());
            entity.setLength(body.getLength());
            entity.setCustomerThickness(body.getCustomerThickness() == null ? body.getThickness() : body.getCustomerThickness());
            entity.setCustomerWidth(body.getCustomerWidth() == null ? body.getWidth() : body.getCustomerWidth());
            entity.setWidthTolerance(body.getWidthTolerance());
            entity.setCustomerLength(body.getCustomerLength() == null ? body.getLength() : body.getCustomerLength());
            entity.setCustomerMaterialCode(body.getCustomerMaterialCode() == null ? null : body.getCustomerMaterialCode().trim());
            entity.setCustomerMaterialName(body.getCustomerMaterialName() == null ? null : body.getCustomerMaterialName().trim());
                entity.setCustomerSpec(trimToNull(body.getCustomerSpec()));
            entity.setIsActive(body.getIsActive() == null ? 1 : body.getIsActive());
            entity.setRemark(body.getRemark());
            entity.setUpdateBy(user);
            entity.setUpdateTime(now);

            if (entity.getId() == null) {
                mappingMapper.insert(entity);
            } else {
                mappingMapper.updateById(entity);
            }
            return ResponseResult.success("保存成功", entity);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseResult.error("保存客户物料映射失败: " + e.getMessage());
        }
    }

    @PostMapping("/batch-save")
    @Transactional(rollbackFor = Exception.class)
    public ResponseResult<?> batchSave(@RequestBody List<CustomerMaterialMapping> list) {
        try {
            if (list == null || list.isEmpty()) {
                return ResponseResult.error("导入数据为空");
            }

            LocalDateTime now = LocalDateTime.now();
            int saved = 0;
            for (CustomerMaterialMapping body : list) {
                if (body == null) continue;

                String customerCode = body.getCustomerCode() == null ? "" : body.getCustomerCode().trim();
                String materialCode = body.getMaterialCode() == null ? "" : body.getMaterialCode().trim();
                if (customerCode.isEmpty() || materialCode.isEmpty() || body.getThickness() == null || body.getWidth() == null || body.getLength() == null) {
                    continue;
                }

                String user = body.getUpdateBy() == null || body.getUpdateBy().trim().isEmpty() ? "system" : body.getUpdateBy().trim();

                QueryWrapper<CustomerMaterialMapping> dupWrapper = new QueryWrapper<>();
                dupWrapper.eq("customer_code", customerCode)
                        .eq("material_code", materialCode)
                    .eq("thickness", body.getThickness())
                    .eq("width", body.getWidth())
                    .eq("length", body.getLength())
                        .last("limit 1");
                CustomerMaterialMapping entity = mappingMapper.selectOne(dupWrapper);
                if (entity == null) {
                    entity = new CustomerMaterialMapping();
                    entity.setCreateBy(user);
                    entity.setCreateTime(now);
                }

                entity.setCustomerCode(customerCode);
                entity.setMaterialCode(materialCode);
                entity.setThickness(body.getThickness());
                entity.setWidth(body.getWidth());
                entity.setLength(body.getLength());
                entity.setCustomerThickness(body.getCustomerThickness() == null ? body.getThickness() : body.getCustomerThickness());
                entity.setCustomerWidth(body.getCustomerWidth() == null ? body.getWidth() : body.getCustomerWidth());
                entity.setWidthTolerance(body.getWidthTolerance());
                entity.setCustomerLength(body.getCustomerLength() == null ? body.getLength() : body.getCustomerLength());
                entity.setCustomerMaterialCode(body.getCustomerMaterialCode() == null ? null : body.getCustomerMaterialCode().trim());
                entity.setCustomerMaterialName(body.getCustomerMaterialName() == null ? null : body.getCustomerMaterialName().trim());
                entity.setCustomerSpec(trimToNull(body.getCustomerSpec()));
                entity.setIsActive(body.getIsActive() == null ? 1 : body.getIsActive());
                entity.setRemark(body.getRemark());
                entity.setUpdateBy(user);
                entity.setUpdateTime(now);

                if (entity.getId() == null) {
                    mappingMapper.insert(entity);
                } else {
                    mappingMapper.updateById(entity);
                }
                saved++;
            }

            return ResponseResult.success("批量保存成功", saved);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseResult.error("批量保存客户物料映射失败: " + e.getMessage());
        }
    }

        /**
         * 按客户模板结构批量导入：
         * 常见列：客户代码、客户物料代码、我司料号、客户规格、我司规格（可混用英文列名）
         * 导入时自动解析规格字符串并拆分到 thickness/width/length 与 customerThickness/customerWidth/customerLength。
         */
    @PostMapping("/batch-import-structured")
    @Transactional(rollbackFor = Exception.class)
    public ResponseResult<?> batchImportStructured(@RequestBody List<Map<String, Object>> rows) {
        return doStructuredImport(rows, null);
    }

    /**
     * 文件导入（兼容 Excel/WPS）：支持 .xlsx/.xls/.csv
     */
    @PostMapping("/batch-import-structured-file")
    @Transactional(rollbackFor = Exception.class)
    public ResponseResult<?> batchImportStructuredFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) String operator) {
        try {
            if (file == null || file.isEmpty()) {
                return ResponseResult.error("上传文件为空");
            }

            String originalFilename = file.getOriginalFilename();
            String filename = originalFilename == null ? "" : originalFilename.toLowerCase(Locale.ROOT);
            List<Map<String, Object>> rows;
            if (filename.endsWith(".xlsx") || filename.endsWith(".xls")) {
                rows = parseExcelRows(file);
            } else if (filename.endsWith(".csv")) {
                rows = parseCsvRows(file);
            } else if (filename.endsWith(".et")) {
                return ResponseResult.error("暂不支持直接导入 .et，请在WPS中另存为 .xlsx 后再导入");
            } else {
                return ResponseResult.error("不支持的文件类型，请上传 .xlsx/.xls/.csv");
            }

            return doStructuredImport(rows, operator);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseResult.error("文件导入失败: " + e.getMessage());
        }
    }

    private ResponseResult<?> doStructuredImport(List<Map<String, Object>> rows, String defaultOperator) {
        try {
            if (rows == null || rows.isEmpty()) {
                return ResponseResult.error("导入数据为空");
            }

            LocalDateTime now = LocalDateTime.now();
            int inserted = 0;
            int updated = 0;
            int skipped = 0;
            List<String> errors = new ArrayList<>();

            for (int i = 0; i < rows.size(); i++) {
            Map<String, Object> row = rows.get(i);
            if (row == null || row.isEmpty()) {
                skipped++;
                continue;
            }

            String customerCode = firstNotBlank(row,
                "customerCode", "客户代码", "客户编码", "客户");
            String materialCode = firstNotBlank(row,
                "materialCode", "我司料号", "物料代码", "料号");
            String customerMaterialCode = firstNotBlank(row,
                "customerMaterialCode", "客户物料代码", "客户料号");
            String customerMaterialName = firstNotBlank(row,
                "customerMaterialName", "客户物料名称", "客户材料名称", "客户品名", "客户名称");
            String remark = firstNotBlank(row, "remark", "备注");
            String operator = firstNotBlank(row, "updateBy", "operator", "操作人");
            if (operator == null || operator.trim().isEmpty()) {
                operator = (defaultOperator == null || defaultOperator.trim().isEmpty()) ? "system" : defaultOperator.trim();
            }

            String customerSpec = firstNotBlank(row,
                "customerSpec", "customerSpecText", "客户标签规格", "客户规格");
            String materialSpec = firstNotBlank(row,
                "materialSpec", "spec", "specText", "我司规格", "规格");

            BigDecimal[] customerDims = parseSpecDimensions(customerSpec);
            BigDecimal[] materialDims = parseSpecDimensions(materialSpec);

            BigDecimal thickness = firstPositive(
                materialDims[0],
                toBigDecimal(row.get("thickness")),
                toBigDecimal(row.get("厚度"))
            );
            BigDecimal width = firstPositive(
                materialDims[1],
                toBigDecimal(row.get("width")),
                toBigDecimal(row.get("宽度"))
            );
            BigDecimal length = firstPositive(
                materialDims[2],
                toBigDecimal(row.get("length")),
                toBigDecimal(row.get("长度"))
            );

            BigDecimal customerThickness = firstPositive(
                customerDims[0],
                toBigDecimal(row.get("customerThickness")),
                toBigDecimal(row.get("客户厚度")),
                thickness
            );
            BigDecimal customerWidth = firstPositive(
                customerDims[1],
                toBigDecimal(row.get("customerWidth")),
                toBigDecimal(row.get("客户宽度")),
                width
            );
            BigDecimal customerLength = firstPositive(
                customerDims[2],
                toBigDecimal(row.get("customerLength")),
                toBigDecimal(row.get("客户长度")),
                length
            );

            BigDecimal widthTolerance = firstPositive(
                toBigDecimal(row.get("widthTolerance")),
                toBigDecimal(row.get("宽度公差")),
                toBigDecimal(row.get("宽度公差(mm)")),
                toBigDecimal(row.get("宽度公差（mm）"))
            );

            if (customerCode == null || customerCode.isEmpty() || materialCode == null || materialCode.isEmpty()
                || thickness == null || width == null || length == null) {
                skipped++;
                errors.add("第" + (i + 1) + "行缺少必填项（客户代码/我司料号/我司规格）");
                continue;
            }

            QueryWrapper<CustomerMaterialMapping> dupWrapper = new QueryWrapper<>();
            dupWrapper.eq("customer_code", customerCode)
                .eq("material_code", materialCode)
                .eq("thickness", thickness)
                .eq("width", width)
                .eq("length", length)
                .last("limit 1");

            CustomerMaterialMapping entity = mappingMapper.selectOne(dupWrapper);
            boolean isInsert = false;
            if (entity == null) {
                entity = new CustomerMaterialMapping();
                entity.setCreateBy(operator);
                entity.setCreateTime(now);
                isInsert = true;
            }

            entity.setCustomerCode(customerCode);
            entity.setMaterialCode(materialCode);
            entity.setThickness(thickness);
            entity.setWidth(width);
            entity.setLength(length);
            entity.setCustomerThickness(customerThickness == null ? thickness : customerThickness);
            entity.setCustomerWidth(customerWidth == null ? width : customerWidth);
            entity.setWidthTolerance(widthTolerance);
            entity.setCustomerLength(customerLength == null ? length : customerLength);
            entity.setCustomerMaterialCode(customerMaterialCode);
            entity.setCustomerMaterialName(customerMaterialName);
            entity.setCustomerSpec(trimToNull(customerSpec));
            entity.setIsActive(1);
            entity.setRemark(remark);
            entity.setUpdateBy(operator);
            entity.setUpdateTime(now);

            if (isInsert) {
                mappingMapper.insert(entity);
                inserted++;
            } else {
                mappingMapper.updateById(entity);
                updated++;
            }
            }

            Map<String, Object> result = new HashMap<>();
            result.put("total", rows.size());
            result.put("inserted", inserted);
            result.put("updated", updated);
            result.put("skipped", skipped);
            result.put("errors", errors.size() > 20 ? errors.subList(0, 20) : errors);
            result.put("errorCount", errors.size());
            return ResponseResult.success("导入完成", result);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseResult.error("结构化导入失败: " + e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    @Transactional(rollbackFor = Exception.class)
    public ResponseResult<?> delete(@PathVariable Long id) {
        try {
            if (id == null || id <= 0) {
                return ResponseResult.error("id不合法");
            }
            int affected = mappingMapper.deleteById(id);
            if (affected <= 0) {
                return ResponseResult.error("记录不存在或已删除");
            }
            return ResponseResult.success("删除成功", null);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseResult.error("删除客户物料映射失败: " + e.getMessage());
        }
    }

    /**
     * 导入模板定义（用于前端下载模板时与后端导入规则保持一致）
     */
    @GetMapping("/import-template")
    public ResponseResult<?> importTemplate() {
        try {
            List<String> headers = new ArrayList<>();
            headers.add("客户代码");
            headers.add("客户物料代码");
            headers.add("我司料号");
            headers.add("客户规格");
            headers.add("我司规格");
            headers.add("客户物料名称");
            headers.add("客户标签规格");
            headers.add("宽度公差(mm)");
            headers.add("备注");

            Map<String, Object> sample = new HashMap<>();
            sample.put("客户代码", "ZZWB1001");
            sample.put("客户物料代码", "58.01.01.1154");
            sample.put("我司料号", "S201-2525-C03-0600");
            sample.put("客户规格", "50μm*5mm*33m");
            sample.put("我司规格", "50μm*5mm*33m");
            sample.put("客户物料名称", "示例客户品名");
            sample.put("客户标签规格", "50μm*5mm*33m");
            sample.put("宽度公差(mm)", "0.50");
            sample.put("备注", "示例数据");

            Map<String, Object> result = new HashMap<>();
            result.put("mode", "structured-spec-v1");
            result.put("headers", headers);
            result.put("requiredHeaders", java.util.Arrays.asList("客户代码", "我司料号", "我司规格"));
            result.put("sample", sample);
            return ResponseResult.success(result);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseResult.error("获取导入模板失败: " + e.getMessage());
        }
    }

    /**
     * 打印匹配接口：优先“客户+料号+厚度+宽度+长度”精确匹配；
     * 未命中时回退“客户+料号+厚度”，再回退“客户+料号”最新启用配置。
     */
    @GetMapping("/match")
    @PreAuthorize("hasAnyAuthority('admin','sales','finance','production','packaging','plan','warehouse','quality','rd')")
    public ResponseResult<?> match(
            @RequestParam String customerCode,
            @RequestParam String materialCode,
            @RequestParam(required = false) BigDecimal thickness,
            @RequestParam(required = false) BigDecimal width,
            @RequestParam(required = false) BigDecimal length
    ) {
        try {
            String c = customerCode == null ? "" : customerCode.trim();
            String m = materialCode == null ? "" : materialCode.trim();
            if (c.isEmpty() || m.isEmpty()) {
                return ResponseResult.success(null);
            }

            CustomerMaterialMapping hit = null;

            if (thickness != null && width != null && length != null) {
                QueryWrapper<CustomerMaterialMapping> exact = new QueryWrapper<>();
                exact.eq("customer_code", c)
                        .eq("material_code", m)
                        .eq("thickness", thickness)
                        .eq("width", width)
                        .eq("length", length)
                        .eq("is_active", 1)
                        .orderByDesc("update_time")
                        .orderByDesc("id")
                        .last("limit 1");
                hit = mappingMapper.selectOne(exact);
            }

            if (hit == null && thickness != null) {
                QueryWrapper<CustomerMaterialMapping> byThickness = new QueryWrapper<>();
                byThickness.eq("customer_code", c)
                        .eq("material_code", m)
                        .eq("thickness", thickness)
                        .eq("is_active", 1)
                        .orderByDesc("update_time")
                        .orderByDesc("id")
                        .last("limit 1");
                hit = mappingMapper.selectOne(byThickness);
            }

            if (hit == null) {
                QueryWrapper<CustomerMaterialMapping> fallback = new QueryWrapper<>();
                fallback.eq("customer_code", c)
                        .eq("material_code", m)
                        .eq("is_active", 1)
                        .orderByDesc("update_time")
                        .orderByDesc("id")
                        .last("limit 1");
                hit = mappingMapper.selectOne(fallback);
            }

            return ResponseResult.success(hit);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseResult.error("匹配客户物料映射失败: " + e.getMessage());
        }
    }

    @GetMapping("/all")
    public ResponseResult<List<CustomerMaterialMapping>> all(@RequestParam(required = false) Integer isActive) {
        try {
            QueryWrapper<CustomerMaterialMapping> wrapper = new QueryWrapper<>();
            if (isActive != null) {
                wrapper.eq("is_active", isActive);
            }
            wrapper.orderByDesc("update_time").orderByDesc("id");
            return ResponseResult.success(mappingMapper.selectList(wrapper));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseResult.error("查询客户物料映射失败: " + e.getMessage());
        }
    }

    private String firstNotBlank(Map<String, Object> row, String... keys) {
        if (row == null || keys == null) {
            return null;
        }
        for (String key : keys) {
            if (key == null) continue;
            Object value = row.get(key);
            if (value == null) continue;
            String text = String.valueOf(value).trim();
            if (!text.isEmpty()) {
                return text;
            }
        }
        return null;
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value == null) {
            return null;
        }
        try {
            String text = String.valueOf(value).trim();
            if (text.isEmpty()) {
                return null;
            }
            return new BigDecimal(text);
        } catch (Exception e) {
            return null;
        }
    }

    private BigDecimal[] parseSpecDimensions(String spec) {
        BigDecimal[] out = new BigDecimal[]{null, null, null};
        if (spec == null || spec.trim().isEmpty()) {
            return out;
        }
        Matcher matcher = SPEC_NUMBER_PATTERN.matcher(spec);
        List<BigDecimal> nums = new ArrayList<>();
        while (matcher.find()) {
            try {
                nums.add(new BigDecimal(matcher.group()));
            } catch (Exception ignored) {
            }
        }
        if (nums.size() >= 3) {
            out[0] = nums.get(0);
            out[1] = nums.get(1);
            out[2] = nums.get(2);
        }
        return out;
    }

    private BigDecimal firstPositive(BigDecimal... values) {
        if (values == null) {
            return null;
        }
        for (BigDecimal value : values) {
            if (value != null && value.compareTo(BigDecimal.ZERO) > 0) {
                return value;
            }
        }
        return null;
    }

    private String trimToNull(String text) {
        if (text == null) {
            return null;
        }
        String v = text.trim();
        return v.isEmpty() ? null : v;
    }

    private List<Map<String, Object>> parseExcelRows(MultipartFile file) throws Exception {
        List<Map<String, Object>> rows = new ArrayList<>();
        try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
            Sheet sheet = workbook.getNumberOfSheets() > 0 ? workbook.getSheetAt(0) : null;
            if (sheet == null) {
                return rows;
            }

            Row headerRow = sheet.getRow(sheet.getFirstRowNum());
            if (headerRow == null) {
                return rows;
            }
            List<String> headers = new ArrayList<>();
            int headerLastCell = headerRow.getLastCellNum();
            for (int i = 0; i < headerLastCell; i++) {
                headers.add(readCellAsText(headerRow.getCell(i)));
            }

            for (int r = sheet.getFirstRowNum() + 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null) continue;
                Map<String, Object> map = new HashMap<>();
                boolean hasValue = false;
                for (int c = 0; c < headers.size(); c++) {
                    String key = headers.get(c);
                    if (key == null || key.trim().isEmpty()) continue;
                    String value = readCellAsText(row.getCell(c));
                    if (value != null && !value.trim().isEmpty()) {
                        hasValue = true;
                    }
                    map.put(key.trim(), value == null ? "" : value.trim());
                }
                if (hasValue) {
                    rows.add(map);
                }
            }
        }
        return rows;
    }

    private List<Map<String, Object>> parseCsvRows(MultipartFile file) throws Exception {
        List<Map<String, Object>> rows = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String headerLine = reader.readLine();
            if (headerLine == null) {
                return rows;
            }
            List<String> headers = parseCsvLine(removeBom(headerLine));
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                List<String> cols = parseCsvLine(line);
                Map<String, Object> map = new HashMap<>();
                boolean hasValue = false;
                for (int i = 0; i < headers.size(); i++) {
                    String key = headers.get(i) == null ? "" : headers.get(i).trim();
                    if (key.isEmpty()) continue;
                    String value = i < cols.size() ? cols.get(i) : "";
                    if (value != null && !value.trim().isEmpty()) {
                        hasValue = true;
                    }
                    map.put(key, value == null ? "" : value.trim());
                }
                if (hasValue) {
                    rows.add(map);
                }
            }
        }
        return rows;
    }

    private String readCellAsText(Cell cell) {
        if (cell == null) return "";
        return EXCEL_DATA_FORMATTER.formatCellValue(cell);
    }

    private String removeBom(String text) {
        if (text == null || text.isEmpty()) return text;
        if (text.charAt(0) == '\uFEFF') {
            return text.substring(1);
        }
        return text;
    }

    private List<String> parseCsvLine(String line) {
        List<String> result = new ArrayList<>();
        if (line == null) {
            return result;
        }
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (ch == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (ch == ',' && !inQuotes) {
                result.add(current.toString());
                current.setLength(0);
            } else {
                current.append(ch);
            }
        }
        result.add(current.toString());
        return result;
    }

    /**
     * 一次性历史初始化：从历史销售订单提取客户+料号+厚度+宽度+长度，写入映射表。
     * 规则：
     * 1) customer_material_code 默认=material_code
     * 2) customer_material_name 默认=material_name
     * 3) 若已存在同键(customer+material+thickness+width+length)，则仅在为空时补值，保留人工维护结果
     */
    @PostMapping("/init-from-history")
    @Transactional(rollbackFor = Exception.class)
    public ResponseResult<?> initFromHistory(@RequestBody(required = false) Map<String, Object> body) {
        try {
            String operator = body != null && body.get("operator") != null ? String.valueOf(body.get("operator")).trim() : "system";
            String customerCode = body != null && body.get("customerCode") != null ? String.valueOf(body.get("customerCode")).trim() : "";

            StringBuilder sql = new StringBuilder();
                sql.append("SELECT so.customer AS customer_code, soi.material_code, COALESCE(ts.product_name, '') AS material_name, soi.thickness, soi.width, soi.length ")
                    .append("FROM sales_order_items soi ")
                    .append("JOIN sales_orders so ON so.id = soi.order_id ")
                    .append("LEFT JOIN tape_spec ts ON ts.material_code = soi.material_code ")
                    .append("WHERE so.is_deleted = 0 AND soi.is_deleted = 0 ")
                    .append("AND so.customer IS NOT NULL AND TRIM(so.customer) <> '' ")
                    .append("AND soi.material_code IS NOT NULL AND TRIM(soi.material_code) <> '' ")
                    .append("AND soi.thickness IS NOT NULL ")
                    .append("AND soi.width IS NOT NULL ")
                    .append("AND soi.length IS NOT NULL ");

            List<Object> params = new ArrayList<>();
            if (!customerCode.isEmpty()) {
                sql.append("AND so.customer = ? ");
                params.add(customerCode);
            }
            sql.append("GROUP BY so.customer, soi.material_code, ts.product_name, soi.thickness, soi.width, soi.length");

            String querySql = Objects.requireNonNull(sql.toString(), "query sql");
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(querySql, params.toArray());

            int inserted = 0;
            int updated = 0;
            int skipped = 0;
            LocalDateTime now = LocalDateTime.now();

            for (Map<String, Object> row : rows) {
                String c = row.get("customer_code") == null ? "" : String.valueOf(row.get("customer_code")).trim();
                String m = row.get("material_code") == null ? "" : String.valueOf(row.get("material_code")).trim();
                String materialName = row.get("material_name") == null ? "" : String.valueOf(row.get("material_name")).trim();
                BigDecimal t = row.get("thickness") == null ? null : new BigDecimal(String.valueOf(row.get("thickness")));
                BigDecimal w = row.get("width") == null ? null : new BigDecimal(String.valueOf(row.get("width")));
                BigDecimal l = row.get("length") == null ? null : new BigDecimal(String.valueOf(row.get("length")));

                if (c.isEmpty() || m.isEmpty() || t == null || w == null || l == null) {
                    skipped++;
                    continue;
                }

                QueryWrapper<CustomerMaterialMapping> dupWrapper = new QueryWrapper<>();
                dupWrapper.eq("customer_code", c)
                        .eq("material_code", m)
                    .eq("thickness", t)
                    .eq("width", w)
                    .eq("length", l)
                        .last("limit 1");
                CustomerMaterialMapping entity = mappingMapper.selectOne(dupWrapper);

                if (entity == null) {
                    entity = new CustomerMaterialMapping();
                    entity.setCustomerCode(c);
                    entity.setMaterialCode(m);
                    entity.setThickness(t);
                    entity.setWidth(w);
                    entity.setLength(l);
                    entity.setCustomerThickness(t);
                    entity.setCustomerWidth(w);
                    entity.setWidthTolerance(null);
                    entity.setCustomerLength(l);
                    entity.setCustomerMaterialCode(m);
                    entity.setCustomerMaterialName(materialName.isEmpty() ? null : materialName);
                        entity.setCustomerSpec(null);
                    entity.setIsActive(1);
                    entity.setRemark("历史订单初始化");
                    entity.setCreateBy(operator);
                    entity.setCreateTime(now);
                    entity.setUpdateBy(operator);
                    entity.setUpdateTime(now);
                    mappingMapper.insert(entity);
                    inserted++;
                } else {
                    boolean changed = false;
                    if (entity.getCustomerMaterialCode() == null || entity.getCustomerMaterialCode().trim().isEmpty()) {
                        entity.setCustomerMaterialCode(m);
                        changed = true;
                    }
                    if ((entity.getCustomerMaterialName() == null || entity.getCustomerMaterialName().trim().isEmpty()) && !materialName.isEmpty()) {
                        entity.setCustomerMaterialName(materialName);
                        changed = true;
                    }
                    if (entity.getCustomerThickness() == null) {
                        entity.setCustomerThickness(t);
                        changed = true;
                    }
                    if (entity.getCustomerWidth() == null) {
                        entity.setCustomerWidth(w);
                        changed = true;
                    }
                    if (entity.getCustomerLength() == null) {
                        entity.setCustomerLength(l);
                        changed = true;
                    }
                    if (entity.getIsActive() == null) {
                        entity.setIsActive(1);
                        changed = true;
                    }
                    if (changed) {
                        entity.setUpdateBy(operator);
                        entity.setUpdateTime(now);
                        if (entity.getRemark() == null || entity.getRemark().trim().isEmpty()) {
                            entity.setRemark("历史订单初始化补全");
                        }
                        mappingMapper.updateById(entity);
                        updated++;
                    } else {
                        skipped++;
                    }
                }
            }

            Map<String, Object> result = new HashMap<>();
            result.put("completed", true);
            result.put("total", rows.size());
            result.put("inserted", inserted);
            result.put("updated", updated);
            result.put("skipped", skipped);
            result.put("customerCode", customerCode);
            return ResponseResult.success("历史初始化完成", result);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseResult.error("历史初始化失败: " + e.getMessage());
        }
    }
}
