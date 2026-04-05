package com.fine.serviceIMPL;

import com.alibaba.excel.EasyExcel;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fine.Dao.CustomerMapper;
import com.fine.Dao.QuotationItemMapper;
import com.fine.Dao.QuotationItemVersionMapper;
import com.fine.Dao.QuotationMapper;
import com.fine.Dao.TapeMapper;
import com.fine.Utils.QuotationDataListener;
import com.fine.Utils.ResponseResult;
import com.fine.modle.Customer;
import com.fine.modle.LoginUser;
import com.fine.modle.Quotation;
import com.fine.modle.QuotationItem;
import com.fine.modle.QuotationItemVersion;
import com.fine.modle.QuotationItemVersionQuery;
import com.fine.modle.Tape;
import com.fine.service.QuotationService;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class QuotationServiceImpl extends ServiceImpl<QuotationMapper, Quotation> implements QuotationService {

    private static final int REMINDER_DAYS = 7;
    private static final String ORDER_BASELINE_MARK = "INIT_FROM_SALES_ORDER_LATEST_PRICE";

    @Autowired
    private QuotationMapper quotationMapper;
    @Autowired
    private TapeMapper tapeMapper;
    @Autowired
    private QuotationItemMapper quotationItemMapper;
    @Autowired
    private QuotationItemVersionMapper quotationItemVersionMapper;
    @Autowired
    private CustomerMapper customerMapper;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final AtomicBoolean enhancedSchemaReady = new AtomicBoolean(false);

    @Override
    public ResponseResult<?> getAllQuotations() {
        try {
            ensureEnhancedQuotationSchema();
            LoginUser loginUser = getLoginUser();
            LambdaQueryWrapper<Quotation> queryWrapper = new LambdaQueryWrapper<>();
            queryWrapper.eq(Quotation::getIsDeleted, 0).orderByDesc(Quotation::getCreatedAt);

            if (loginUser != null && !hasRole(loginUser, "admin")) {
                List<String> allowedCustomers = getAllowedCustomerKeys(getCurrentUserId(loginUser));
                if (allowedCustomers == null || allowedCustomers.isEmpty()) {
                    Map<String, Object> empty = new HashMap<>();
                    empty.put("data", Collections.emptyList());
                    empty.put("reminders", buildReminderSummary(Collections.emptyList()));
                    return new ResponseResult<>(200, "获取报价单列表成功", empty);
                }
                queryWrapper.in(Quotation::getCustomer, allowedCustomers);
            }

            List<Quotation> quotations = quotationMapper.selectList(queryWrapper);
            enrichQuotationsForList(quotations);

            Map<String, Object> data = new HashMap<>();
            data.put("data", quotations);
            data.put("reminders", buildReminderSummary(quotations));
            return new ResponseResult<>(200, "获取报价单列表成功", data);
        } catch (Exception e) {
            e.printStackTrace();
            return new ResponseResult<>(500, "获取报价单列表失败: " + e.getMessage(), null);
        }
    }

    @Override
    public ResponseResult<?> getQuotationPage(int current, int size, String customerKeyword) {
        try {
            ensureEnhancedQuotationSchema();
            int safeCurrent = Math.max(current, 1);
            int safeSize = size <= 0 ? 10 : Math.min(size, 200);
            String keyword = normalizeText(customerKeyword);

            LoginUser loginUser = getLoginUser();
            LambdaQueryWrapper<Quotation> queryWrapper = new LambdaQueryWrapper<>();
            queryWrapper.eq(Quotation::getIsDeleted, 0).orderByDesc(Quotation::getCreatedAt);

            if (loginUser != null && !hasRole(loginUser, "admin")) {
                List<String> allowedCustomers = getAllowedCustomerKeys(getCurrentUserId(loginUser));
                if (allowedCustomers == null || allowedCustomers.isEmpty()) {
                    Map<String, Object> empty = new HashMap<>();
                    empty.put("records", Collections.emptyList());
                    empty.put("total", 0);
                    empty.put("current", safeCurrent);
                    empty.put("size", safeSize);
                    empty.put("reminders", buildReminderSummary(Collections.emptyList()));
                    return new ResponseResult<>(200, "获取报价单分页列表成功", empty);
                }
                queryWrapper.in(Quotation::getCustomer, allowedCustomers);
            }

            if (keyword != null) {
                List<Customer> keywordMatchedCustomers = customerMapper.selectList(
                        new LambdaQueryWrapper<Customer>()
                                .eq(Customer::getIsDeleted, 0)
                                .and(w -> w.like(Customer::getCustomerCode, keyword)
                                        .or()
                                        .like(Customer::getCustomerName, keyword)
                                        .or()
                                        .like(Customer::getShortName, keyword)));

                List<String> keywordMatchedCodes = new ArrayList<>();
                for (Customer customer : keywordMatchedCustomers) {
                    if (customer != null && hasText(customer.getCustomerCode())) {
                        keywordMatchedCodes.add(customer.getCustomerCode().trim());
                    }
                }

                queryWrapper.and(w -> {
                    w.like(Quotation::getCustomer, keyword);
                    if (!keywordMatchedCodes.isEmpty()) {
                        w.or().in(Quotation::getCustomer, keywordMatchedCodes);
                    }
                });
            }

            Page<Quotation> page = new Page<>(safeCurrent, safeSize);
            IPage<Quotation> resultPage = quotationMapper.selectPage(page, queryWrapper);
            List<Quotation> records = resultPage.getRecords() == null ? new ArrayList<>() : resultPage.getRecords();
            enrichQuotationsForList(records);

            Map<String, Object> data = new HashMap<>();
            data.put("records", records);
            data.put("total", resultPage.getTotal());
            data.put("current", resultPage.getCurrent());
            data.put("size", resultPage.getSize());
            data.put("reminders", buildReminderSummary(records));
            return new ResponseResult<>(200, "获取报价单分页列表成功", data);
        } catch (Exception e) {
            e.printStackTrace();
            return new ResponseResult<>(500, "获取报价单分页列表失败: " + e.getMessage(), null);
        }
    }

    @Override
    public ResponseResult<?> getQuotationById(Long quotationId) {
        try {
            ensureEnhancedQuotationSchema();
            Quotation quotation = quotationMapper.selectById(quotationId);
            if (quotation == null || isDeleted(quotation.getIsDeleted())) {
                return new ResponseResult<>(404, "报价单不存在", null);
            }
            if (!canAccessQuotation(getLoginUser(), quotation)) {
                return new ResponseResult<>(403, "无权限访问该报价单", null);
            }
            enrichQuotation(quotation);
            return new ResponseResult<>(200, "获取报价单详情成功", quotation);
        } catch (Exception e) {
            e.printStackTrace();
            return new ResponseResult<>(500, "获取报价单详情失败: " + e.getMessage(), null);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResponseResult<?> createQuotation(Quotation quotation) {
        try {
            ensureEnhancedQuotationSchema();
            if (quotation == null) {
                return new ResponseResult<>(400, "报价数据不能为空", null);
            }
            if (!canAccessQuotation(getLoginUser(), quotation)) {
                return new ResponseResult<>(403, "无权限创建该客户的报价单", null);
            }
            String customer = normalizeCodeToken(quotation.getCustomer());
            if (customer == null) {
                return new ResponseResult<>(400, "客户不能为空", null);
            }
            if (quotation.getItems() == null || quotation.getItems().isEmpty()) {
                return new ResponseResult<>(400, "请至少填写一条报价明细", null);
            }

            Date now = new Date();
            String currentUser = getCurrentUsername();

            quotation.setQuotationNo(generateQuotationNo());
            quotation.setCustomer(customer);
            quotation.setContactPerson(normalizeText(quotation.getContactPerson()));
            quotation.setContactPhone(normalizeText(quotation.getContactPhone()));
            quotation.setSourceSampleNo(normalizeText(quotation.getSourceSampleNo()));
            quotation.setPricingUnit(normalizeQuotationUnit(quotation.getPricingUnit()));
            // pricing metadata (front-end may supply priceStatus / needsPricing)
            quotation.setPriceStatus(normalizeText(quotation.getPriceStatus()) == null ? "PRICED" : normalizeText(quotation.getPriceStatus()));
            quotation.setNeedsPricing(quotation.getNeedsPricing() == null ? Boolean.FALSE : quotation.getNeedsPricing());
            quotation.setQuotationDate(defaultQuotationDate(quotation.getQuotationDate(), now));
            quotation.setValidUntil(defaultValidUntil(quotation.getQuotationDate(), quotation.getValidUntil()));
            quotation.setStatus(resolveQuotationStatus(quotation.getStatus(), quotation.getValidUntil()));
            quotation.setRemark(normalizeText(quotation.getRemark()));
            quotation.setCreatedBy(currentUser);
            quotation.setUpdatedBy(currentUser);
            quotation.setCreatedAt(now);
            quotation.setUpdatedAt(now);
            quotation.setIsDeleted(0);
            quotationMapper.insert(quotation);

            List<QuotationItem> savedItems = saveQuotationItems(quotation, quotation.getItems(), currentUser, now);
            quotation.setItems(savedItems);
            applyExpiryInfo(quotation);
            return new ResponseResult<>(200, "创建报价单成功", quotation);
        } catch (Exception e) {
            e.printStackTrace();
            return new ResponseResult<>(500, "创建报价单失败: " + e.getMessage(), null);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResponseResult<?> updateQuotation(Quotation quotation) {
        try {
            ensureEnhancedQuotationSchema();
            if (quotation == null || quotation.getId() == null) {
                return new ResponseResult<>(400, "报价单ID不能为空", null);
            }

            Quotation existingQuotation = quotationMapper.selectById(quotation.getId());
            if (existingQuotation == null || isDeleted(existingQuotation.getIsDeleted())) {
                return new ResponseResult<>(404, "报价单不存在", null);
            }
            LoginUser loginUser = getLoginUser();
            if (!canAccessQuotation(loginUser, existingQuotation) || !canAccessQuotation(loginUser, quotation)) {
                return new ResponseResult<>(403, "无权限更新该客户的报价单", null);
            }
            if (quotation.getItems() == null || quotation.getItems().isEmpty()) {
                return new ResponseResult<>(400, "请至少填写一条报价明细", null);
            }

            Date now = new Date();
            String currentUser = getCurrentUsername();
            existingQuotation.setCustomer(normalizeCodeToken(quotation.getCustomer()));
            existingQuotation.setContactPerson(normalizeText(quotation.getContactPerson()));
            existingQuotation.setContactPhone(normalizeText(quotation.getContactPhone()));
            existingQuotation.setSourceSampleNo(normalizeText(quotation.getSourceSampleNo()));
            existingQuotation.setPricingUnit(normalizeQuotationUnit(quotation.getPricingUnit()));
            // update pricing metadata if provided
            if (quotation.getPriceStatus() != null) {
                existingQuotation.setPriceStatus(normalizeText(quotation.getPriceStatus()));
            }
            if (quotation.getNeedsPricing() != null) {
                existingQuotation.setNeedsPricing(quotation.getNeedsPricing());
            }
            existingQuotation.setQuotationDate(defaultQuotationDate(quotation.getQuotationDate(), existingQuotation.getQuotationDate() != null ? existingQuotation.getQuotationDate() : now));
            existingQuotation.setValidUntil(defaultValidUntil(existingQuotation.getQuotationDate(), quotation.getValidUntil()));
            existingQuotation.setStatus(resolveQuotationStatus(quotation.getStatus(), existingQuotation.getValidUntil()));
            existingQuotation.setRemark(normalizeText(quotation.getRemark()));
            existingQuotation.setUpdatedBy(currentUser);
            existingQuotation.setUpdatedAt(now);
            quotationMapper.updateById(existingQuotation);

            LambdaQueryWrapper<QuotationItem> deleteWrapper = new LambdaQueryWrapper<>();
            deleteWrapper.eq(QuotationItem::getQuotationId, existingQuotation.getId()).eq(QuotationItem::getIsDeleted, 0);
            quotationItemMapper.delete(deleteWrapper);

            List<QuotationItem> savedItems = saveQuotationItems(existingQuotation, quotation.getItems(), currentUser, now);
            existingQuotation.setItems(savedItems);
            applyExpiryInfo(existingQuotation);
            return new ResponseResult<>(200, "更新报价单成功", existingQuotation);
        } catch (Exception e) {
            e.printStackTrace();
            return new ResponseResult<>(500, "更新报价单失败: " + e.getMessage(), null);
        }
    }

    @Override
    public ResponseResult<?> getQuotationItemVersionHistory(QuotationItemVersionQuery query) {
        try {
            ensureEnhancedQuotationSchema();
            if (query == null || normalizeCodeToken(query.getCustomer()) == null || normalizeCodeToken(query.getMaterialCode()) == null) {
                return new ResponseResult<>(400, "客户和物料代码不能为空", null);
            }
            String specKey = buildSpecKey(normalizeCodeToken(query.getMaterialCode()), query.getSpecification(), query.getModel(), query.getColorCode(), query.getLength(), query.getWidth(), query.getThickness());
            LambdaQueryWrapper<QuotationItemVersion> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(QuotationItemVersion::getCustomer, normalizeCodeToken(query.getCustomer()))
                    .eq(QuotationItemVersion::getSpecKey, specKey)
                    .orderByDesc(QuotationItemVersion::getVersionNo)
                    .orderByDesc(QuotationItemVersion::getCreatedAt);
            List<QuotationItemVersion> versions = quotationItemVersionMapper.selectList(wrapper);
            return new ResponseResult<>(200, "获取报价版本记录成功", versions);
        } catch (Exception e) {
            e.printStackTrace();
            return new ResponseResult<>(500, "获取报价版本记录失败: " + e.getMessage(), null);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResponseResult<?> deleteQuotation(Long quotationId) {
        try {
            ensureEnhancedQuotationSchema();
            Quotation quotation = quotationMapper.selectById(quotationId);
            if (quotation == null || isDeleted(quotation.getIsDeleted())) {
                return new ResponseResult<>(404, "报价单不存在", null);
            }
            if (!canAccessQuotation(getLoginUser(), quotation)) {
                return new ResponseResult<>(403, "无权限删除该报价单", null);
            }
            quotationMapper.deleteById(quotationId);
            LambdaQueryWrapper<QuotationItem> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(QuotationItem::getQuotationId, quotationId).eq(QuotationItem::getIsDeleted, 0);
            quotationItemMapper.delete(wrapper);
            return new ResponseResult<>(200, "删除成功", null);
        } catch (Exception e) {
            e.printStackTrace();
            return new ResponseResult<>(500, "删除报价单失败: " + e.getMessage(), null);
        }
    }

    @Override
    public void save(MultipartFile file) {
        try {
            EasyExcel.read(file.getInputStream(), Quotation.class, new QuotationDataListener(quotationMapper)).sheet().doRead();
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Error processing file", e);
        }
    }

    @Override
    public ResponseResult<?> fetchQueryList(String customerCode, String shortName, int page, int size) {
        return new ResponseResult<>(20000, "此方法已废弃，请使用 /quotation/list 接口", null);
    }

    @Override
    public ResponseResult<?> searchTableByKeyWord(String keyword) {
        QueryWrapper<Tape> queryWrapper = new QueryWrapper<>();
        queryWrapper.like("part_number", keyword);
        List<Tape> list = tapeMapper.selectList(queryWrapper);
        Map<String, Object> data = new HashMap<>();
        data.put("data", list);
        return new ResponseResult<>(20000, "查询成功", data);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResponseResult<?> insert(Quotation quotation) {
        return createQuotation(quotation);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResponseResult<?> deleteQuotationDetails(String quotationDetailId, String id) {
        try {
            Long detailId = Long.parseLong(quotationDetailId);
            QuotationItem item = quotationItemMapper.selectById(detailId);
            if (item != null) {
                quotationItemMapper.deleteById(detailId);
            }
            return new ResponseResult<>(20000, "删除明细成功", null);
        } catch (Exception e) {
            e.printStackTrace();
            return new ResponseResult<>(50000, "删除明细失败: " + e.getMessage(), null);
        }
    }

    @Override
    public ResponseResult<?> getOrdersQuotationByNumble(String id) {
        try {
            return getQuotationById(Long.parseLong(id));
        } catch (Exception e) {
            e.printStackTrace();
            return new ResponseResult<>(50000, "获取报价单失败", null);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResponseResult<?> deleteQuotation(String id) {
        try {
            return deleteQuotation(Long.parseLong(id));
        } catch (Exception e) {
            e.printStackTrace();
            return new ResponseResult<>(50000, "报价单不存在", null);
        }
    }

    @Override
    public String getCategoryByMaterialCode(String materialCode) {
        if (materialCode == null || materialCode.isEmpty()) {
            return "其他";
        }
        if (materialCode.startsWith("1011") || materialCode.contains("PET") || materialCode.contains("胶带")) {
            return "胶带";
        }
        if (materialCode.contains("膜") || materialCode.contains("FILM")) {
            return "薄膜";
        }
        if (materialCode.contains("胶水") || materialCode.contains("GLUE")) {
            return "胶水";
        }
        return "其他";
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResponseResult<?> importFromExcel(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return new ResponseResult<>(400, "请选择文件");
        }

        try {
            ensureEnhancedQuotationSchema();
            try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
                Sheet sheet = workbook.getSheetAt(0);
                Row headerRow = sheet.getRow(0);
                Map<String, Integer> headerIndex = buildHeaderIndexMap(headerRow);

                if (isHistoryOrderImport(headerIndex)) {
                    return importFromHistoryOrders(sheet, headerIndex);
                }
                return importFromStandardTemplate(sheet, headerIndex);
            }
        } catch (Exception e) {
            return new ResponseResult<>(500, "导入失败：" + e.getMessage());
        }
    }

    private ResponseResult<?> importFromStandardTemplate(Sheet sheet, Map<String, Integer> headerIndex) {
        Map<String, Object> result = new HashMap<>();
        int successCount = 0;
        int failCount = 0;
        StringBuilder errorMsg = new StringBuilder();
        Map<String, Quotation> quotationCache = new HashMap<>();

        String currentUser = getCurrentUsername();
        Date now = new Date();
        int lastRow = sheet.getLastRowNum();

        int colQuotationNo = resolveColumnIndex(headerIndex, 0, "报价单号", "报价单号(可选)", "quotationno");
        int colCustomer = resolveColumnIndex(headerIndex, 1, "客户名称", "客户", "customer");
        int colContactPerson = resolveColumnIndex(headerIndex, 2, "联系人", "contactperson");
        int colContactPhone = resolveColumnIndex(headerIndex, 3, "联系电话", "contactphone");
        int colQuotationDate = resolveColumnIndex(headerIndex, 4, "报价日期", "quotationdate");
        int colValidUntil = resolveColumnIndex(headerIndex, 5, "有效期至", "validuntil");
        int colPricingUnit = resolveColumnIndex(headerIndex, 6, "报价单位", "报价单位(㎡/m/卷)", "pricingunit");
        int colStatus = resolveColumnIndex(headerIndex, 7, "状态", "status");
        int colQuotationRemark = resolveColumnIndex(headerIndex, 8, "报价备注", "remark");
        int colMaterialCode = resolveColumnIndex(headerIndex, 9, "物料代码", "料号", "materialcode");
        int colMaterialName = resolveColumnIndex(headerIndex, 10, "物料名称", "品名", "materialname");
        int colLength = resolveColumnIndex(headerIndex, 11, "长度", "长度(mm)", "长度(m)", "length");
        int colWidth = resolveColumnIndex(headerIndex, 12, "宽度", "宽度(mm)", "width");
        int colThickness = resolveColumnIndex(headerIndex, 13, "厚度", "厚度(μm)", "厚度(um)", "thickness");
        int colUnit = resolveColumnIndex(headerIndex, 14, "单位", "unit");
        int colUnitPrice = resolveColumnIndex(headerIndex, 15, "单价", "unitprice");
        int colItemRemark = resolveColumnIndex(headerIndex, 16, "明细备注", "itemremark");

        for (int i = 1; i <= lastRow; i++) {
            Row row = sheet.getRow(i);
            if (row == null) {
                continue;
            }
            try {
                String importQuotationNo = normalizeQuotationNo(getCellStringValue(row.getCell(colQuotationNo)));
                String customer = normalizeText(getCellStringValue(row.getCell(colCustomer)));
                if (customer == null) {
                    failCount++;
                    errorMsg.append("第").append(i + 1).append("行：客户名称不能为空\n");
                    continue;
                }

                Quotation quotation = null;
                if (importQuotationNo != null) {
                    quotation = quotationCache.get(importQuotationNo);
                    if (quotation == null) {
                        LambdaQueryWrapper<Quotation> wrapper = new LambdaQueryWrapper<>();
                        wrapper.eq(Quotation::getQuotationNo, importQuotationNo).eq(Quotation::getIsDeleted, 0).last("LIMIT 1");
                        quotation = quotationMapper.selectOne(wrapper);
                    }
                }

                if (quotation == null) {
                    quotation = new Quotation();
                    quotation.setQuotationNo(importQuotationNo != null ? importQuotationNo : generateQuotationNo());
                    quotation.setCustomer(customer);
                    quotation.setContactPerson(normalizeText(getCellStringValue(row.getCell(colContactPerson))));
                    quotation.setContactPhone(normalizeText(getCellStringValue(row.getCell(colContactPhone))));
                    quotation.setQuotationDate(defaultQuotationDate(parseDateCell(row.getCell(colQuotationDate)), now));
                    quotation.setValidUntil(defaultValidUntil(quotation.getQuotationDate(), parseDateCell(row.getCell(colValidUntil))));
                    quotation.setPricingUnit(normalizeQuotationUnit(getCellStringValue(row.getCell(colPricingUnit))));
                    quotation.setStatus(resolveQuotationStatus(getCellStringValue(row.getCell(colStatus)), quotation.getValidUntil()));
                    quotation.setRemark(normalizeText(getCellStringValue(row.getCell(colQuotationRemark))));
                    quotation.setCreatedBy(currentUser);
                    quotation.setUpdatedBy(currentUser);
                    quotation.setCreatedAt(now);
                    quotation.setUpdatedAt(now);
                    quotation.setIsDeleted(0);
                    quotationMapper.insert(quotation);
                    successCount++;
                    if (importQuotationNo != null) {
                        quotationCache.put(importQuotationNo, quotation);
                    }
                }

                String materialCode = normalizeText(getCellStringValue(row.getCell(colMaterialCode)));
                String materialName = normalizeText(getCellStringValue(row.getCell(colMaterialName)));
                if (materialCode == null && materialName == null) {
                    continue;
                }

                QuotationItem item = new QuotationItem();
                item.setMaterialCode(materialCode);
                item.setMaterialName(materialName);
                item.setLength(getCellDecimalValue(row.getCell(colLength)));
                item.setWidth(getCellDecimalValue(row.getCell(colWidth)));
                item.setThickness(getCellDecimalValue(row.getCell(colThickness)));
                item.setUnit(normalizeText(getCellStringValue(row.getCell(colUnit))));
                item.setUnitPrice(getCellDecimalValue(row.getCell(colUnitPrice)));
                item.setRemark(normalizeText(getCellStringValue(row.getCell(colItemRemark))));
                saveQuotationItems(quotation, Collections.singletonList(item), currentUser, now);
            } catch (Exception e) {
                failCount++;
                errorMsg.append("第").append(i + 1).append("行：").append(e.getMessage()).append("\n");
            }
        }

        result.put("mode", "standard");
        result.put("success", failCount == 0);
        result.put("successCount", successCount);
        result.put("failCount", failCount);
        result.put("message", failCount > 0 ? errorMsg.toString() : "导入成功");
        return new ResponseResult<>(200, "导入完成", result);
    }

    private ResponseResult<?> importFromHistoryOrders(Sheet sheet, Map<String, Integer> headerIndex) {
        Map<String, Object> result = new HashMap<>();
        int quotationCount = 0;
        int itemCount = 0;
        int failCount = 0;
        int totalRowCount = 0;
        int duplicateSkipCount = 0;
        int missingRequiredCount = 0;
        StringBuilder errorMsg = new StringBuilder();

        String currentUser = getCurrentUsername();
        Date now = new Date();
        int lastRow = sheet.getLastRowNum();

        int colCustomer = resolveColumnIndex(headerIndex, 1, "客户编码", "客户名称", "客户", "customer");
        int colQuotationDate = resolveColumnIndex(headerIndex, 3, "下单日期", "订单日期", "报价日期", "quotationdate");
        int colMaterialCode = resolveColumnIndex(headerIndex, 8, "料号", "物料代码", "materialcode");
        int colMaterialName = resolveColumnIndex(headerIndex, 9, "品名", "物料名称", "materialname");
        int colColorCode = resolveColumnIndex(headerIndex, 10, "颜色代码", "颜色", "colorcode");
        int colThickness = resolveColumnIndex(headerIndex, 11, "厚度(μm)", "厚度", "thickness");
        int colWidth = resolveColumnIndex(headerIndex, 12, "宽度(mm)", "宽度", "width");
        int colLength = resolveColumnIndex(headerIndex, 13, "长度(m)", "长度", "length");
        int colUnitPrice = resolveColumnIndex(headerIndex, 15, "单价", "unitprice");
        int colRemark = resolveColumnIndex(headerIndex, 17, "明细备注", "订单备注", "remark");

        Map<String, Quotation> quotationByGroup = new HashMap<>();
        Map<String, Boolean> itemDedup = new HashMap<>();

        for (int i = 1; i <= lastRow; i++) {
            Row row = sheet.getRow(i);
            if (row == null) {
                continue;
            }
            totalRowCount++;
            try {
                String customer = normalizeText(getCellStringValue(row.getCell(colCustomer)));
                Date quotationDate = parseDateCell(row.getCell(colQuotationDate));
                String materialCode = normalizeText(getCellStringValue(row.getCell(colMaterialCode)));

                if (customer == null || quotationDate == null || materialCode == null) {
                    failCount++;
                    missingRequiredCount++;
                    errorMsg.append("第").append(i + 1).append("行：客户、下单日期、料号不能为空\n");
                    continue;
                }

                String quotationGroupKey = customer + "|" + new SimpleDateFormat("yyyy-MM-dd").format(quotationDate);
                Quotation quotation = quotationByGroup.get(quotationGroupKey);
                if (quotation == null) {
                    quotation = new Quotation();
                    quotation.setQuotationNo(generateQuotationNo());
                    quotation.setCustomer(customer);
                    quotation.setQuotationDate(quotationDate);
                    quotation.setValidUntil(defaultValidUntil(quotationDate, null));
                    quotation.setStatus("accepted");
                    quotation.setPricingUnit("㎡");
                    quotation.setRemark("历史订单初始化导入");
                    quotation.setCreatedBy(currentUser);
                    quotation.setUpdatedBy(currentUser);
                    quotation.setCreatedAt(now);
                    quotation.setUpdatedAt(now);
                    quotation.setIsDeleted(0);
                    quotationMapper.insert(quotation);
                    quotationByGroup.put(quotationGroupKey, quotation);
                    quotationCount++;
                }

                BigDecimal thickness = getCellDecimalValue(row.getCell(colThickness));
                BigDecimal width = getCellDecimalValue(row.getCell(colWidth));
                BigDecimal length = getCellDecimalValue(row.getCell(colLength));

                String itemKey = quotationGroupKey + "|"
                        + materialCode + "|"
                        + decimalText(thickness) + "|"
                        + decimalText(width) + "|"
                        + decimalText(length);
                if (itemDedup.containsKey(itemKey)) {
                    duplicateSkipCount++;
                    continue;
                }

                QuotationItem item = new QuotationItem();
                item.setMaterialCode(materialCode);
                item.setMaterialName(normalizeText(getCellStringValue(row.getCell(colMaterialName))));
                item.setColorCode(normalizeText(getCellStringValue(row.getCell(colColorCode))));
                item.setThickness(thickness);
                item.setWidth(width);
                item.setLength(length);
                item.setUnit("㎡");
                item.setUnitPrice(getCellDecimalValue(row.getCell(colUnitPrice)));
                item.setRemark(normalizeText(getCellStringValue(row.getCell(colRemark))));

                saveQuotationItems(quotation, Collections.singletonList(item), currentUser, now);
                itemDedup.put(itemKey, Boolean.TRUE);
                itemCount++;
            } catch (Exception e) {
                failCount++;
                errorMsg.append("第").append(i + 1).append("行：").append(e.getMessage()).append("\n");
            }
        }

        result.put("mode", "history-order");
        result.put("success", failCount == 0);
        result.put("totalRowCount", totalRowCount);
        result.put("successCount", quotationCount);
        result.put("itemCount", itemCount);
        result.put("duplicateSkipCount", duplicateSkipCount);
        result.put("missingRequiredCount", missingRequiredCount);
        result.put("failCount", failCount);
        result.put("message", failCount > 0 ? errorMsg.toString() : "历史订单初始化导入成功");
        return new ResponseResult<>(200, "导入完成", result);
    }

    private boolean isHistoryOrderImport(Map<String, Integer> headerIndex) {
        if (headerIndex == null || headerIndex.isEmpty()) {
            return false;
        }
        return hasAnyHeader(headerIndex, "订单号", "订单号(可空)")
                && hasAnyHeader(headerIndex, "客户编码", "客户名称")
                && hasAnyHeader(headerIndex, "下单日期", "订单日期")
                && hasAnyHeader(headerIndex, "料号")
                && hasAnyHeader(headerIndex, "单价");
    }

    private boolean hasAnyHeader(Map<String, Integer> headerIndex, String... aliases) {
        if (aliases == null) {
            return false;
        }
        for (String alias : aliases) {
            if (headerIndex.containsKey(normalizeHeader(alias))) {
                return true;
            }
        }
        return false;
    }

    private Map<String, Integer> buildHeaderIndexMap(Row headerRow) {
        Map<String, Integer> map = new HashMap<>();
        if (headerRow == null) {
            return map;
        }
        short lastCell = headerRow.getLastCellNum();
        for (int i = 0; i < lastCell; i++) {
            String header = normalizeHeader(getCellStringValue(headerRow.getCell(i)));
            if (header != null) {
                map.put(header, i);
            }
        }
        return map;
    }

    private int resolveColumnIndex(Map<String, Integer> headerIndex, int defaultIndex, String... aliases) {
        if (headerIndex != null && aliases != null) {
            for (String alias : aliases) {
                Integer index = headerIndex.get(normalizeHeader(alias));
                if (index != null) {
                    return index;
                }
            }
        }
        return defaultIndex;
    }

    private String normalizeHeader(String header) {
        String text = normalizeText(header);
        if (text == null) {
            return null;
        }
        return text.toLowerCase().replace("（", "(").replace("）", ")").replace("μ", "u").replace(" ", "");
    }

    @Override
    public ResponseResult<?> exportQuotations() {
        try {
            ensureEnhancedQuotationSchema();
            LambdaQueryWrapper<Quotation> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(Quotation::getIsDeleted, 0).orderByDesc(Quotation::getCreatedAt);
            List<Quotation> quotations = quotationMapper.selectList(wrapper);
            List<Map<String, Object>> exportData = new ArrayList<>();
            for (Quotation quotation : quotations) {
                List<QuotationItem> items = loadActiveItems(quotation.getId(), quotation.getCustomer());
                if (items.isEmpty()) {
                    exportData.add(buildExportRow(quotation, null));
                    continue;
                }
                for (QuotationItem item : items) {
                    exportData.add(buildExportRow(quotation, item));
                }
            }
            return new ResponseResult<>(200, "导出成功", exportData);
        } catch (Exception e) {
            e.printStackTrace();
            return new ResponseResult<>(500, "导出失败：" + e.getMessage());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResponseResult<?> initializeFromLatestSalesOrders(String operator) {
        try {
            ensureEnhancedQuotationSchema();

            String currentUser = normalizeText(operator);
            if (currentUser == null) {
                currentUser = getCurrentUsername();
            }
            Date now = new Date();

            // 1) 清理旧的“初始化报价基线”
            LambdaQueryWrapper<Quotation> oldInitWrapper = new LambdaQueryWrapper<>();
            oldInitWrapper.eq(Quotation::getIsDeleted, 0)
                    .eq(Quotation::getSourceSampleNo, ORDER_BASELINE_MARK);
            List<Quotation> oldInitQuotations = quotationMapper.selectList(oldInitWrapper);
            int deletedBaselineQuotations = 0;
            int deletedBaselineItems = 0;
            if (oldInitQuotations != null && !oldInitQuotations.isEmpty()) {
                for (Quotation oldQ : oldInitQuotations) {
                    if (oldQ == null || oldQ.getId() == null) {
                        continue;
                    }
                    LambdaQueryWrapper<QuotationItem> itemDeleteWrapper = new LambdaQueryWrapper<>();
                    itemDeleteWrapper.eq(QuotationItem::getQuotationId, oldQ.getId())
                            .eq(QuotationItem::getIsDeleted, 0);
                    deletedBaselineItems += quotationItemMapper.delete(itemDeleteWrapper);
                    deletedBaselineQuotations += quotationMapper.deleteById(oldQ.getId());
                }
            }

            // 2) 抽取每个“客户+料号+厚度+宽度+长度”的最新订单单价
            String sql = "SELECT customer, material_code, material_name, color_code, thickness, width, length, unit_price, order_date FROM ("
                    + "  SELECT so.customer AS customer, soi.material_code AS material_code, soi.material_name AS material_name, "
                    + "         soi.color_code AS color_code, soi.thickness AS thickness, soi.width AS width, soi.length AS length, "
                    + "         soi.unit_price AS unit_price, so.order_date AS order_date, so.id AS order_id, soi.id AS item_id, "
                    + "         ROW_NUMBER() OVER (PARTITION BY so.customer, soi.material_code, "
                    + "                                   COALESCE(soi.thickness, 0), COALESCE(soi.width, 0), COALESCE(soi.length, 0) "
                    + "                            ORDER BY so.order_date DESC, so.id DESC, soi.id DESC) AS rn "
                    + "  FROM sales_orders so "
                    + "  INNER JOIN sales_order_items soi ON soi.order_id = so.id "
                    + "  WHERE so.is_deleted = 0 AND soi.is_deleted = 0 AND soi.unit_price IS NOT NULL"
                    + ") t WHERE t.rn = 1 ORDER BY customer ASC, order_date DESC";

            List<Map<String, Object>> latestRows = jdbcTemplate.queryForList(sql);
            if (latestRows == null || latestRows.isEmpty()) {
                Map<String, Object> emptyResult = new LinkedHashMap<>();
                emptyResult.put("deletedBaselineQuotations", deletedBaselineQuotations);
                emptyResult.put("deletedBaselineItems", deletedBaselineItems);
                emptyResult.put("createdQuotations", 0);
                emptyResult.put("createdItems", 0);
                emptyResult.put("message", "销售订单中无可用于初始化的单价数据");
                return new ResponseResult<>(200, "初始化完成（无可用数据）", emptyResult);
            }

            // 3) 生成基线报价（每个客户一张报价单）
            Map<String, Quotation> quotationByCustomer = new LinkedHashMap<>();
            int createdQuotationCount = 0;
            int createdItemCount = 0;
            Set<String> dedup = new HashSet<>();

            for (Map<String, Object> row : latestRows) {
                String customer = normalizeText(valueToString(row.get("customer")));
                String materialCode = normalizeText(valueToString(row.get("material_code")));
                BigDecimal unitPrice = toBigDecimal(row.get("unit_price"));
                if (customer == null || materialCode == null || unitPrice == null) {
                    continue;
                }

                BigDecimal thickness = toBigDecimal(row.get("thickness"));
                BigDecimal width = toBigDecimal(row.get("width"));
                BigDecimal length = toBigDecimal(row.get("length"));
                String specKey = customer + "|" + materialCode + "|" + decimalText(thickness) + "|" + decimalText(width) + "|" + decimalText(length);
                if (dedup.contains(specKey)) {
                    continue;
                }
                dedup.add(specKey);

                Quotation quotation = quotationByCustomer.get(customer);
                if (quotation == null) {
                    quotation = new Quotation();
                    quotation.setQuotationNo(generateQuotationNo());
                    quotation.setCustomer(customer);
                    Date qDate = toDate(toLocalDate(toDateObj(row.get("order_date"))));
                    quotation.setQuotationDate(qDate != null ? qDate : now);
                    // accepted 状态默认长期有效（不参与到期提醒）
                    quotation.setValidUntil(null);
                    quotation.setStatus("accepted");
                    quotation.setSourceSampleNo(ORDER_BASELINE_MARK);
                    quotation.setRemark("系统初始化报价基线（来源：销售订单最新下单时间单价）");
                    quotation.setCreatedBy(currentUser);
                    quotation.setUpdatedBy(currentUser);
                    quotation.setCreatedAt(now);
                    quotation.setUpdatedAt(now);
                    quotation.setIsDeleted(0);
                    quotationMapper.insert(quotation);
                    quotationByCustomer.put(customer, quotation);
                    createdQuotationCount++;
                }

                QuotationItem item = new QuotationItem();
                item.setMaterialCode(materialCode);
                item.setMaterialName(normalizeText(valueToString(row.get("material_name"))));
                item.setColorCode(normalizeText(valueToString(row.get("color_code"))));
                item.setThickness(thickness);
                item.setWidth(width);
                item.setLength(length);
                item.setUnit("㎡");
                item.setUnitPrice(normalizeDecimal(unitPrice));
                item.setRemark("初始化基线：按订单最新下单时间价格生成");

                List<QuotationItem> saved = saveQuotationItems(quotation, Collections.singletonList(item), currentUser, now);
                createdItemCount += saved == null ? 0 : saved.size();
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("deletedBaselineQuotations", deletedBaselineQuotations);
            result.put("deletedBaselineItems", deletedBaselineItems);
            result.put("createdQuotations", createdQuotationCount);
            result.put("createdItems", createdItemCount);
            result.put("baselineMark", ORDER_BASELINE_MARK);
            result.put("message", "初始化报价基线完成");
            return new ResponseResult<>(200, "初始化报价基线完成", result);
        } catch (Exception e) {
            e.printStackTrace();
            return new ResponseResult<>(500, "初始化报价基线失败: " + e.getMessage(), null);
        }
    }

    private String valueToString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value == null) {
            return null;
        }
        try {
            if (value instanceof BigDecimal) {
                return normalizeDecimal((BigDecimal) value);
            }
            if (value instanceof Number) {
                return normalizeDecimal(BigDecimal.valueOf(((Number) value).doubleValue()));
            }
            String text = normalizeText(String.valueOf(value));
            if (text == null) {
                return null;
            }
            return normalizeDecimal(new BigDecimal(text.replace(",", "")));
        } catch (Exception e) {
            return null;
        }
    }

    private Date toDateObj(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Date) {
            return (Date) value;
        }
        try {
            String text = normalizeText(String.valueOf(value));
            if (text == null) {
                return null;
            }
            return new SimpleDateFormat("yyyy-MM-dd").parse(text);
        } catch (Exception e) {
            return null;
        }
    }

    private void enrichQuotation(Quotation quotation) {
        if (quotation == null) {
            return;
        }
        quotation.setItems(loadActiveItems(quotation.getId(), quotation.getCustomer()));
        applyExpiryInfo(quotation);
    }

    private List<QuotationItem> loadActiveItems(Long quotationId, String customer) {
        LambdaQueryWrapper<QuotationItem> itemWrapper = new LambdaQueryWrapper<>();
        itemWrapper.eq(QuotationItem::getQuotationId, quotationId)
                .eq(QuotationItem::getIsDeleted, 0)
            .orderByDesc(QuotationItem::getMaterialCode)
            .orderByAsc(QuotationItem::getId);
        List<QuotationItem> items = quotationItemMapper.selectList(itemWrapper);
        if (items == null) {
            return new ArrayList<>();
        }
        for (QuotationItem item : items) {
            item.setVersionNo(findLatestVersionNo(customer, item));
        }
        return items;
    }

    private void enrichQuotationsForList(List<Quotation> quotations) {
        if (quotations == null || quotations.isEmpty()) {
            return;
        }

        Map<Long, Quotation> quotationById = new LinkedHashMap<>();
        List<Long> quotationIds = new ArrayList<>();
        for (Quotation quotation : quotations) {
            if (quotation == null || quotation.getId() == null) {
                continue;
            }
            quotationById.put(quotation.getId(), quotation);
            quotationIds.add(quotation.getId());
            quotation.setItems(new ArrayList<>());
        }

        if (quotationIds.isEmpty()) {
            for (Quotation quotation : quotations) {
                applyExpiryInfo(quotation);
            }
            return;
        }

        LambdaQueryWrapper<QuotationItem> itemWrapper = new LambdaQueryWrapper<>();
        itemWrapper.in(QuotationItem::getQuotationId, quotationIds)
                .eq(QuotationItem::getIsDeleted, 0)
            .orderByAsc(QuotationItem::getQuotationId)
            .orderByDesc(QuotationItem::getMaterialCode)
            .orderByAsc(QuotationItem::getId);
        List<QuotationItem> allItems = quotationItemMapper.selectList(itemWrapper);

        Map<String, Map<String, Integer>> latestVersionMap = loadLatestVersionMapForItems(allItems, quotationById);
        Map<Long, List<QuotationItem>> itemsByQuotationId = new LinkedHashMap<>();

        if (allItems != null) {
            for (QuotationItem item : allItems) {
                if (item == null || item.getQuotationId() == null) {
                    continue;
                }
                Quotation quotation = quotationById.get(item.getQuotationId());
                String customer = quotation == null ? null : normalizeText(quotation.getCustomer());
                String specKey = buildSpecKey(item.getMaterialCode(), item.getSpecification(), item.getModel(), item.getColorCode(), item.getLength(), item.getWidth(), item.getThickness());
                Integer versionNo = 0;
                if (customer != null) {
                    Map<String, Integer> customerVersionMap = latestVersionMap.get(customer);
                    if (customerVersionMap != null) {
                        versionNo = customerVersionMap.getOrDefault(specKey, 0);
                    }
                }
                item.setVersionNo(versionNo);
                itemsByQuotationId.computeIfAbsent(item.getQuotationId(), key -> new ArrayList<>()).add(item);
            }
        }

        for (Quotation quotation : quotations) {
            if (quotation == null) {
                continue;
            }
            List<QuotationItem> items = quotation.getId() == null
                    ? new ArrayList<>()
                    : itemsByQuotationId.getOrDefault(quotation.getId(), new ArrayList<>());
            quotation.setItems(items);
            applyExpiryInfo(quotation);
        }
    }

    private Map<String, Map<String, Integer>> loadLatestVersionMapForItems(List<QuotationItem> items, Map<Long, Quotation> quotationById) {
        Map<String, Set<String>> specKeysByCustomer = new LinkedHashMap<>();
        if (items != null) {
            for (QuotationItem item : items) {
                if (item == null || item.getQuotationId() == null) {
                    continue;
                }
                Quotation quotation = quotationById.get(item.getQuotationId());
                String customer = quotation == null ? null : normalizeText(quotation.getCustomer());
                if (customer == null) {
                    continue;
                }
                String specKey = buildSpecKey(item.getMaterialCode(), item.getSpecification(), item.getModel(), item.getColorCode(), item.getLength(), item.getWidth(), item.getThickness());
                specKeysByCustomer.computeIfAbsent(customer, key -> new HashSet<>()).add(specKey);
            }
        }

        Map<String, Map<String, Integer>> result = new LinkedHashMap<>();
        for (Map.Entry<String, Set<String>> entry : specKeysByCustomer.entrySet()) {
            String customer = entry.getKey();
            Set<String> specKeys = entry.getValue();
            if (customer == null || specKeys == null || specKeys.isEmpty()) {
                continue;
            }

            LambdaQueryWrapper<QuotationItemVersion> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(QuotationItemVersion::getCustomer, customer)
                    .in(QuotationItemVersion::getSpecKey, specKeys)
                    .orderByDesc(QuotationItemVersion::getVersionNo);
            List<QuotationItemVersion> versions = quotationItemVersionMapper.selectList(wrapper);
            Map<String, Integer> versionMap = new HashMap<>();
            if (versions != null) {
                for (QuotationItemVersion version : versions) {
                    if (version == null) {
                        continue;
                    }
                    String specKey = normalizeText(version.getSpecKey());
                    if (specKey == null || versionMap.containsKey(specKey)) {
                        continue;
                    }
                    versionMap.put(specKey, version.getVersionNo() == null ? 0 : version.getVersionNo());
                }
            }
            result.put(customer, versionMap);
        }
        return result;
    }

    private List<QuotationItem> saveQuotationItems(Quotation quotation, List<QuotationItem> items, String currentUser, Date now) {
        List<QuotationItem> savedItems = new ArrayList<>();
        for (QuotationItem item : items) {
            if (isBlankItem(item)) {
                continue;
            }
            normalizeQuotationItem(item);
            item.setId(null);
            item.setQuotationId(quotation.getId());
            item.setCreatedBy(currentUser);
            item.setUpdatedBy(currentUser);
            item.setCreatedAt(now);
            item.setUpdatedAt(now);
            item.setIsDeleted(0);
            Integer versionNo = resolveVersionNo(quotation, item, currentUser, now);
            item.setVersionNo(versionNo);
            quotationItemMapper.insert(item);
            savedItems.add(item);
        }
        return savedItems;
    }

    private Integer resolveVersionNo(Quotation quotation, QuotationItem item, String currentUser, Date now) {
        String specKey = buildSpecKey(item.getMaterialCode(), item.getSpecification(), item.getModel(), item.getColorCode(), item.getLength(), item.getWidth(), item.getThickness());
        QuotationItemVersion latestVersion = findLatestVersion(quotation.getCustomer(), specKey);
        if (latestVersion == null || latestVersion.getVersionNo() == null) {
            return createVersionSnapshot(quotation, item, currentUser, now, specKey, 1);
        }
        if (isSameUnitPrice(latestVersion.getUnitPrice(), item.getUnitPrice())) {
            return latestVersion.getVersionNo();
        }
        return createVersionSnapshot(quotation, item, currentUser, now, specKey, latestVersion.getVersionNo() + 1);
    }

    private Integer createVersionSnapshot(Quotation quotation, QuotationItem item, String currentUser, Date now, String specKey, Integer versionNo) {
        QuotationItemVersion version = new QuotationItemVersion();
        version.setQuotationId(quotation.getId());
        version.setQuotationItemId(item.getId());
        version.setQuotationNo(quotation.getQuotationNo());
        version.setCustomer(quotation.getCustomer());
        version.setMaterialCode(item.getMaterialCode());
        version.setMaterialName(item.getMaterialName());
        version.setSpecification(item.getSpecification());
        version.setModel(item.getModel());
        version.setColorCode(item.getColorCode());
        version.setLength(item.getLength());
        version.setWidth(item.getWidth());
        version.setThickness(item.getThickness());
        version.setUnit(item.getUnit());
        version.setUnitPrice(item.getUnitPrice());
        version.setQuotationDate(quotation.getQuotationDate());
        version.setValidUntil(quotation.getValidUntil());
        version.setQuotationStatus(quotation.getStatus());
        version.setVersionNo(versionNo);
        version.setSpecKey(specKey);
        version.setSourceSampleNo(item.getSampleNo() != null ? item.getSampleNo() : quotation.getSourceSampleNo());
        version.setCreatedBy(currentUser);
        version.setCreatedAt(now);
        quotationItemVersionMapper.insert(version);
        return versionNo;
    }

    private Integer findLatestVersionNo(String customer, QuotationItem item) {
        return findLatestVersionNo(customer, buildSpecKey(item.getMaterialCode(), item.getSpecification(), item.getModel(), item.getColorCode(), item.getLength(), item.getWidth(), item.getThickness()));
    }

    private Integer findLatestVersionNo(String customer, String specKey) {
        QuotationItemVersion latest = findLatestVersion(customer, specKey);
        return latest == null || latest.getVersionNo() == null ? 0 : latest.getVersionNo();
    }

    private QuotationItemVersion findLatestVersion(String customer, String specKey) {
        if (normalizeText(customer) == null || normalizeText(specKey) == null) {
            return null;
        }
        LambdaQueryWrapper<QuotationItemVersion> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(QuotationItemVersion::getCustomer, normalizeText(customer))
                .eq(QuotationItemVersion::getSpecKey, normalizeText(specKey))
                .orderByDesc(QuotationItemVersion::getVersionNo)
                .last("LIMIT 1");
        return quotationItemVersionMapper.selectOne(wrapper);
    }

    private boolean isSameUnitPrice(BigDecimal left, BigDecimal right) {
        if (left == null && right == null) {
            return true;
        }
        if (left == null || right == null) {
            return false;
        }
        return left.compareTo(right) == 0;
    }

    private void applyExpiryInfo(Quotation quotation) {
        if (quotation == null) {
            return;
        }
        if (!needsValidityReminder(quotation.getStatus())) {
            quotation.setExpiryStatus("normal");
            quotation.setDaysToExpire(null);
            return;
        }
        LocalDate validUntil = toLocalDate(quotation.getValidUntil());
        if (validUntil == null) {
            quotation.setExpiryStatus("normal");
            quotation.setDaysToExpire(null);
            return;
        }
        long days = ChronoUnit.DAYS.between(LocalDate.now(), validUntil);
        quotation.setDaysToExpire((int) days);
        if (days < 0) {
            quotation.setExpiryStatus("expired");
        } else if (days <= REMINDER_DAYS) {
            quotation.setExpiryStatus("expiring");
        } else {
            quotation.setExpiryStatus("normal");
        }
    }

    private Map<String, Object> buildReminderSummary(List<Quotation> quotations) {
        List<Map<String, Object>> reminderItems = new ArrayList<>();
        int expiredCount = 0;
        int expiringCount = 0;
        for (Quotation quotation : quotations) {
            if (!"expired".equals(quotation.getExpiryStatus()) && !"expiring".equals(quotation.getExpiryStatus())) {
                continue;
            }
            if ("expired".equals(quotation.getExpiryStatus())) {
                expiredCount++;
            } else {
                expiringCount++;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("quotationId", quotation.getId());
            row.put("quotationNo", quotation.getQuotationNo());
            row.put("customer", quotation.getCustomer());
            row.put("validUntil", quotation.getValidUntil());
            row.put("daysToExpire", quotation.getDaysToExpire());
            row.put("status", quotation.getExpiryStatus());
            reminderItems.add(row);
        }
        Map<String, Object> reminderSummary = new LinkedHashMap<>();
        reminderSummary.put("expiredCount", expiredCount);
        reminderSummary.put("expiringCount", expiringCount);
        reminderSummary.put("items", reminderItems);
        return reminderSummary;
    }

    private Map<String, Object> buildExportRow(Quotation quotation, QuotationItem item) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("quotationNo", quotation.getQuotationNo());
        map.put("customer", quotation.getCustomer());
        map.put("contactPerson", quotation.getContactPerson());
        map.put("contactPhone", quotation.getContactPhone());
        map.put("sourceSampleNo", quotation.getSourceSampleNo());
        map.put("quotationDate", quotation.getQuotationDate());
        map.put("validUntil", quotation.getValidUntil());
        map.put("pricingUnit", quotation.getPricingUnit());
        map.put("status", convertStatusToText(quotation.getStatus()));
        map.put("remark", quotation.getRemark());
        map.put("materialCode", item == null ? "" : item.getMaterialCode());
        map.put("materialName", item == null ? "" : item.getMaterialName());
        map.put("specification", item == null ? "" : item.getSpecification());
        map.put("model", item == null ? "" : item.getModel());
        map.put("colorCode", item == null ? "" : item.getColorCode());
        map.put("length", item == null ? null : item.getLength());
        map.put("width", item == null ? null : item.getWidth());
        map.put("thickness", item == null ? null : item.getThickness());
        map.put("unit", item == null ? "" : item.getUnit());
        map.put("unitPrice", item == null ? null : item.getUnitPrice());
        map.put("versionNo", item == null ? null : item.getVersionNo());
        map.put("sampleNo", item == null ? "" : item.getSampleNo());
        map.put("itemRemark", item == null ? "" : item.getRemark());
        return map;
    }

    private boolean isBlankItem(QuotationItem item) {
        return item == null || (normalizeText(item.getMaterialCode()) == null
                && normalizeText(item.getMaterialName()) == null
                && normalizeText(item.getSpecification()) == null
                && item.getLength() == null
                && item.getWidth() == null
                && item.getThickness() == null
                && item.getUnitPrice() == null);
    }

    private void normalizeQuotationItem(QuotationItem item) {
        item.setMaterialCode(normalizeCodeToken(item.getMaterialCode()));
        item.setMaterialName(normalizeText(item.getMaterialName()));
        item.setSpecification(normalizeText(item.getSpecification()));
        item.setModel(normalizeText(item.getModel()));
        item.setColorCode(normalizeText(item.getColorCode()));
        item.setUnit(normalizeQuotationUnit(item.getUnit()));
        item.setSampleNo(normalizeText(item.getSampleNo()));
        item.setRemark(normalizeText(item.getRemark()));
        item.setLength(normalizeDecimal(item.getLength()));
        item.setWidth(normalizeWidthDecimal(item.getWidth()));
        item.setThickness(normalizeDecimal(item.getThickness()));
        item.setUnitPrice(normalizeDecimal(item.getUnitPrice()));
    }

    private String normalizeQuotationUnit(String unit) {
        String normalized = normalizeText(unit);
        if (normalized == null) {
            return "㎡";
        }
        if ("米".equalsIgnoreCase(normalized) || "m".equalsIgnoreCase(normalized)) {
            return "m";
        }
        if ("平方米".equalsIgnoreCase(normalized)
                || "m²".equalsIgnoreCase(normalized)
                || "m2".equalsIgnoreCase(normalized)
                || "㎡".equals(normalized)) {
            return "㎡";
        }
        if ("卷".equals(normalized)) {
            return "卷";
        }
        return "㎡";
    }

    private String normalizeCodeToken(String value) {
        String normalized = normalizeText(value);
        if (normalized == null) {
            return null;
        }
        String cleaned = normalized
                .replace("\u00A0", "")
                .replace("\u3000", "")
                .replaceAll("\\s+", "");
        return cleaned.isEmpty() ? null : cleaned;
    }

    private BigDecimal normalizeWidthDecimal(BigDecimal value) {
        if (value == null) {
            return null;
        }
        return value.setScale(1, BigDecimal.ROUND_HALF_UP);
    }

    private BigDecimal normalizeDecimal(BigDecimal value) {
        return value == null ? null : value.stripTrailingZeros();
    }

    private String resolveQuotationStatus(String requestedStatus, Date validUntil) {
        if (validUntil != null && needsValidityReminder(requestedStatus)) {
            LocalDate date = toLocalDate(validUntil);
            if (date != null && date.isBefore(LocalDate.now())) {
                return "expired";
            }
        }
        String status = normalizeText(requestedStatus);
        return status == null ? "draft" : status;
    }

    private boolean needsValidityReminder(String status) {
        return !"accepted".equalsIgnoreCase(status) && !"rejected".equalsIgnoreCase(status);
    }

    private String buildSpecKey(String materialCode, String specification, String model, String colorCode,
                                BigDecimal length, BigDecimal width, BigDecimal thickness) {
        List<String> parts = new ArrayList<>();
        parts.add(normalizeText(materialCode) == null ? "" : normalizeText(materialCode));
        parts.add(normalizeText(specification) == null ? "" : normalizeText(specification));
        parts.add(normalizeText(model) == null ? "" : normalizeText(model));
        parts.add(normalizeText(colorCode) == null ? "" : normalizeText(colorCode));
        parts.add(decimalText(length));
        parts.add(decimalText(width));
        parts.add(decimalText(thickness));
        return String.join("|", parts);
    }

    private String decimalText(BigDecimal value) {
        return value == null ? "" : normalizeDecimal(value).toPlainString();
    }

    private void ensureEnhancedQuotationSchema() {
        if (enhancedSchemaReady.get()) {
            return;
        }
        synchronized (enhancedSchemaReady) {
            if (enhancedSchemaReady.get()) {
                return;
            }
            jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS price_rule ("
                    + "id BIGINT NOT NULL AUTO_INCREMENT,"
                    + "customer_code VARCHAR(64) NOT NULL,"
                    + "material_code VARCHAR(100) NOT NULL,"
                    + "material_name VARCHAR(200) NULL,"
                    + "color_code VARCHAR(100) NULL,"
                    + "thickness DECIMAL(18,6) NULL,"
                    + "width DECIMAL(18,6) NULL,"
                    + "length DECIMAL(18,6) NULL,"
                    + "unit VARCHAR(20) NOT NULL,"
                    + "price DECIMAL(18,6) NOT NULL,"
                    + "standard_sqm_price DECIMAL(18,6) NULL,"
                    + "priority INT DEFAULT 0,"
                    + "effective_from DATE NULL,"
                    + "effective_to DATE NULL,"
                    + "status TINYINT(1) DEFAULT 1,"
                    + "remark VARCHAR(255) NULL,"
                    + "created_by VARCHAR(100) NULL,"
                    + "updated_by VARCHAR(100) NULL,"
                    + "created_at DATETIME DEFAULT CURRENT_TIMESTAMP,"
                    + "updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,"
                    + "is_deleted TINYINT(1) DEFAULT 0,"
                    + "PRIMARY KEY (id),"
                    + "INDEX idx_price_rule_customer_material_unit (customer_code, material_code, unit),"
                    + "INDEX idx_price_rule_customer_material_spec (customer_code, material_code, thickness, width, length, unit),"
                    + "INDEX idx_price_rule_status (status, is_deleted)"
                    + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='价格规则表'");
                    addColumnIfMissing("price_rule", "standard_sqm_price", "ALTER TABLE price_rule ADD COLUMN standard_sqm_price DECIMAL(18,6) NULL COMMENT '标准平米价' AFTER price");
            jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS quotation_item_versions ("
                    + "id BIGINT NOT NULL AUTO_INCREMENT,"
                    + "quotation_id BIGINT NULL,"
                    + "quotation_item_id BIGINT NULL,"
                    + "quotation_no VARCHAR(64) NULL,"
                    + "customer VARCHAR(200) NOT NULL,"
                    + "material_code VARCHAR(100) NULL,"
                    + "material_name VARCHAR(200) NULL,"
                    + "specification VARCHAR(255) NULL,"
                    + "model VARCHAR(100) NULL,"
                    + "color_code VARCHAR(100) NULL,"
                    + "length DECIMAL(18,6) NULL,"
                    + "width DECIMAL(18,6) NULL,"
                    + "thickness DECIMAL(18,6) NULL,"
                    + "unit VARCHAR(20) NULL,"
                    + "unit_price DECIMAL(18,6) NULL,"
                    + "quotation_date DATE NULL,"
                    + "valid_until DATE NULL,"
                    + "quotation_status VARCHAR(20) NULL,"
                    + "version_no INT NOT NULL,"
                    + "spec_key VARCHAR(255) NOT NULL,"
                    + "source_sample_no VARCHAR(64) NULL,"
                    + "created_by VARCHAR(100) NULL,"
                    + "created_at DATETIME DEFAULT CURRENT_TIMESTAMP,"
                    + "PRIMARY KEY (id),"
                    + "INDEX idx_qiv_customer_spec (customer, spec_key),"
                    + "INDEX idx_qiv_quote_item (quotation_item_id),"
                    + "INDEX idx_qiv_quote (quotation_id)"
                    + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='报价明细版本历史'");
            addColumnIfMissing("quotations", "source_sample_no", "ALTER TABLE quotations ADD COLUMN source_sample_no VARCHAR(64) NULL COMMENT '来源送样单号' AFTER contact_phone");
            addColumnIfMissing("quotations", "created_by", "ALTER TABLE quotations ADD COLUMN created_by VARCHAR(100) NULL COMMENT '创建人' AFTER remark");
            addColumnIfMissing("quotations", "updated_by", "ALTER TABLE quotations ADD COLUMN updated_by VARCHAR(100) NULL COMMENT '修改人' AFTER created_by");
            addColumnIfMissing("quotations", "created_at", "ALTER TABLE quotations ADD COLUMN created_at DATETIME NULL COMMENT '创建时间' AFTER updated_by");
            addColumnIfMissing("quotations", "updated_at", "ALTER TABLE quotations ADD COLUMN updated_at DATETIME NULL COMMENT '修改时间' AFTER created_at");
            addColumnIfMissing("quotations", "price_status", "ALTER TABLE quotations ADD COLUMN price_status VARCHAR(32) NULL COMMENT '价格状态 PENDING/PRICED' AFTER remark");
            addColumnIfMissing("quotations", "needs_pricing", "ALTER TABLE quotations ADD COLUMN needs_pricing TINYINT(1) NULL DEFAULT 0 COMMENT '是否需要定价(0/1)' AFTER price_status");
            addColumnIfMissing("quotations", "pricing_unit", "ALTER TABLE quotations ADD COLUMN pricing_unit VARCHAR(20) NULL COMMENT '报价单位' AFTER remark");
            addColumnIfMissing("quotation_items", "specification", "ALTER TABLE quotation_items ADD COLUMN specification VARCHAR(255) NULL COMMENT '规格' AFTER material_name");
            addColumnIfMissing("quotation_items", "model", "ALTER TABLE quotation_items ADD COLUMN model VARCHAR(100) NULL COMMENT '型号' AFTER specification");
            addColumnIfMissing("quotation_items", "color_code", "ALTER TABLE quotation_items ADD COLUMN color_code VARCHAR(100) NULL COMMENT '颜色' AFTER model");
            addColumnIfMissing("quotation_items", "applied_rule_id", "ALTER TABLE quotation_items ADD COLUMN applied_rule_id BIGINT NULL COMMENT '匹配规则ID' AFTER unit_price");
            addColumnIfMissing("quotation_items", "match_path", "ALTER TABLE quotation_items ADD COLUMN match_path VARCHAR(255) NULL COMMENT '匹配路径说明' AFTER applied_rule_id");
            addColumnIfMissing("quotation_items", "sample_no", "ALTER TABLE quotation_items ADD COLUMN sample_no VARCHAR(64) NULL COMMENT '来源送样单号' AFTER unit");
            addColumnIfMissing("quotation_items", "created_by", "ALTER TABLE quotation_items ADD COLUMN created_by VARCHAR(100) NULL COMMENT '创建人' AFTER remark");
            addColumnIfMissing("quotation_items", "updated_by", "ALTER TABLE quotation_items ADD COLUMN updated_by VARCHAR(100) NULL COMMENT '修改人' AFTER created_by");
            addColumnIfMissing("quotation_items", "created_at", "ALTER TABLE quotation_items ADD COLUMN created_at DATETIME NULL COMMENT '创建时间' AFTER updated_by");
            addColumnIfMissing("quotation_items", "updated_at", "ALTER TABLE quotation_items ADD COLUMN updated_at DATETIME NULL COMMENT '修改时间' AFTER created_at");
            enhancedSchemaReady.set(true);
        }
    }

    private void addColumnIfMissing(String tableName, String columnName, String alterSql) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = ? AND column_name = ?",
                Integer.class,
                tableName,
                columnName
        );
        if ((count == null || count == 0) && alterSql != null && !alterSql.trim().isEmpty()) {
            jdbcTemplate.execute(alterSql);
        }
    }

    private Date parseDateCell(Cell cell) {
        if (cell == null) {
            return null;
        }
        try {
            if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
                return cell.getDateCellValue();
            }
            String text = getCellStringValue(cell);
            if (text == null) {
                return null;
            }
            return new SimpleDateFormat("yyyy-MM-dd").parse(text);
        } catch (Exception e) {
            return null;
        }
    }

    private BigDecimal getCellDecimalValue(Cell cell) {
        if (cell == null) {
            return null;
        }
        try {
            if (cell.getCellType() == CellType.NUMERIC) {
                return BigDecimal.valueOf(cell.getNumericCellValue());
            }
            if (cell.getCellType() == CellType.STRING) {
                String value = normalizeText(cell.getStringCellValue());
                if (value == null) {
                    return null;
                }
                value = value.replace(",", "");
                return new BigDecimal(value);
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    private String normalizeQuotationNo(String quotationNo) {
        String normalized = normalizeText(quotationNo);
        if (normalized == null) {
            return null;
        }
        String upperValue = normalized.toUpperCase();
        Matcher legacyMatcher = Pattern.compile("^QT-(\\d{8})-(\\d{3})$").matcher(upperValue);
        if (legacyMatcher.matches()) {
            return "QT-" + legacyMatcher.group(1).substring(2) + "-" + legacyMatcher.group(2);
        }
        return upperValue;
    }

    private String getCurrentUsername() {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.getPrincipal() instanceof LoginUser) {
                LoginUser loginUser = (LoginUser) authentication.getPrincipal();
                if (loginUser.getUser() != null) {
                    return loginUser.getUser().getUsername();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "系统";
    }

    private String generateQuotationNo() {
        String today = new SimpleDateFormat("yyMMdd").format(new Date());
        String prefix = "QT-" + today + "-";
        Integer maxSeq = jdbcTemplate.queryForObject(
                "SELECT COALESCE(MAX(CAST(SUBSTRING_INDEX(quotation_no, '-', -1) AS UNSIGNED)), 0) " +
                        "FROM quotations WHERE quotation_no LIKE ? AND quotation_no REGEXP ?",
                Integer.class,
                prefix + "%",
                "^QT-[0-9]{6}-[0-9]+$"
        );
        int sequence = (maxSeq == null ? 0 : maxSeq) + 1;
        return prefix + String.format("%03d", sequence);
    }

    private LoginUser getLoginUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof LoginUser) {
            return (LoginUser) authentication.getPrincipal();
        }
        return null;
    }

    private boolean hasRole(LoginUser loginUser, String role) {
        return loginUser != null && loginUser.getPermissions() != null && loginUser.getPermissions().contains(role);
    }

    private Long getCurrentUserId(LoginUser loginUser) {
        return loginUser != null && loginUser.getUser() != null ? loginUser.getUser().getId() : null;
    }

    private boolean canAccessQuotation(LoginUser loginUser, Quotation quotation) {
        if (quotation == null) {
            return true;
        }
        if (loginUser == null) {
            return false;
        }
        if (hasRole(loginUser, "admin")) {
            return true;
        }
        Long userId = getCurrentUserId(loginUser);
        if (userId == null) {
            return false;
        }
        List<String> allowedCustomers = getAllowedCustomerKeys(userId);
        return allowedCustomers != null && allowedCustomers.contains(quotation.getCustomer());
    }

    private List<String> getAllowedCustomerKeys(Long userId) {
        if (userId == null) {
            return new ArrayList<>();
        }
        List<String> codes = customerMapper.selectCustomerCodesByOwner(userId);
        return codes != null ? codes : new ArrayList<>();
    }

    private boolean isDeleted(Integer isDeleted) {
        return isDeleted != null && isDeleted == 1;
    }

    private String normalizeText(String text) {
        if (text == null) {
            return null;
        }
        String trimmed = text.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String convertStatusToText(String status) {
        if (status == null) {
            return "草稿";
        }
        switch (status) {
            case "draft":
                return "草稿";
            case "submitted":
                return "已提交";
            case "accepted":
                return "已接受";
            case "rejected":
                return "已拒绝";
            case "expired":
                return "已过期";
            default:
                return status;
        }
    }

    private Date defaultQuotationDate(Date quotationDate, Date fallback) {
        return quotationDate != null ? quotationDate : fallback;
    }

    private Date defaultValidUntil(Date quotationDate, Date validUntil) {
        return validUntil;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private LocalDate toLocalDate(Date date) {
        if (date == null) {
            return null;
        }
        // 兼容 java.sql.Date（其 toInstant 在部分 JDK 会抛 UnsupportedOperationException）
        if (date instanceof java.sql.Date) {
            return ((java.sql.Date) date).toLocalDate();
        }
        return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
    }

    private Date toDate(LocalDate localDate) {
        if (localDate == null) {
            return null;
        }
        return Date.from(localDate.atStartOfDay(ZoneId.systemDefault()).toInstant());
    }

    private String getCellStringValue(Cell cell) {
        if (cell == null) {
            return null;
        }
        try {
            if (cell.getCellType() == CellType.STRING) {
                return normalizeText(cell.getStringCellValue());
            }
            if (cell.getCellType() == CellType.NUMERIC) {
                if (DateUtil.isCellDateFormatted(cell)) {
                    return new SimpleDateFormat("yyyy-MM-dd").format(cell.getDateCellValue());
                }
                double value = cell.getNumericCellValue();
                long longValue = (long) value;
                return Double.compare(value, longValue) == 0
                        ? String.valueOf(longValue)
                        : BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
            }
            if (cell.getCellType() == CellType.BOOLEAN) {
                return String.valueOf(cell.getBooleanCellValue());
            }
            if (cell.getCellType() == CellType.FORMULA) {
                return normalizeText(cell.getCellFormula());
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public boolean saveBatch(Collection<Quotation> entityList, int batchSize) {
        return super.saveBatch(entityList, batchSize);
    }

    @Override
    public boolean saveOrUpdateBatch(Collection<Quotation> entityList, int batchSize) {
        return super.saveOrUpdateBatch(entityList, batchSize);
    }

    @Override
    public boolean updateBatchById(Collection<Quotation> entityList, int batchSize) {
        return super.updateBatchById(entityList, batchSize);
    }

    @Override
    public boolean saveOrUpdate(Quotation entity) {
        return super.saveOrUpdate(entity);
    }

    @Override
    public Quotation getOne(Wrapper<Quotation> queryWrapper, boolean throwEx) {
        return super.getOne(queryWrapper, throwEx);
    }

    @Override
    public Map<String, Object> getMap(Wrapper<Quotation> queryWrapper) {
        return super.getMap(queryWrapper);
    }

    @Override
    public <V> V getObj(Wrapper<Quotation> queryWrapper, Function<? super Object, V> mapper) {
        return super.getObj(queryWrapper, mapper);
    }
}
