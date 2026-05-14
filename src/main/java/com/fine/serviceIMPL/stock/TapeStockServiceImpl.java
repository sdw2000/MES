package com.fine.serviceIMPL.stock;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fine.Dao.purchase.PurchaseReceiptMapper;
import com.fine.Dao.purchase.PurchaseOrderItemMapper;
import com.fine.Dao.purchase.PurchaseReceiptItemMapper;
import com.fine.Dao.purchase.PurchaseSupplierMapper;
import com.fine.Dao.production.SalesOrderMapper;
import com.fine.Dao.rd.TapeFormulaMapper;
import com.fine.Dao.rd.TapeSpecMapper;
import com.fine.Dao.SalesOrderItemMapper;
import com.fine.Dao.stock.*;
import com.fine.modle.SalesOrder;
import com.fine.modle.SalesOrderItem;
import com.fine.modle.PurchaseOrderItem;
import com.fine.modle.purchase.PurchaseReceipt;
import com.fine.modle.purchase.PurchaseReceiptItem;
import com.fine.modle.purchase.PurchaseSupplier;
import com.fine.modle.rd.TapeRawMaterial;
import com.fine.modle.rd.TapeSpec;
import com.fine.modle.stock.*;
import com.fine.model.stock.ChemicalStock;
import com.fine.model.stock.ChemicalStockDetail;
import com.fine.model.stock.FilmStock;
import com.fine.model.stock.FilmStockDetail;
import com.fine.model.stock.PackageStock;
import com.fine.model.stock.PackageStockDetail;
import com.fine.service.stock.StockFlowLogService;
import com.fine.model.stock.StockFlowLog;
import com.fine.service.stock.TapeStockService;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 胶带库存服务实现
 */
@Service
public class TapeStockServiceImpl implements TapeStockService {

    private static final String RETURN_WAREHOUSE_LOCATION = "退货专仓";
    private static final String SLITTING_PENDING_OUTBOUND_LOCATION = "成品待出库区";
    private static final String SALES_RETURN_TAG = "[SALES_RETURN]";
    private static final String PURCHASE_RECEIPT_TAG = "[PURCHASE_RECEIPT]";
    private static final String PURCHASE_LABEL_QR_SEQ_PREFIX = "mes:purchase:label:seq:";
    private static final int TAPE_STOCK_REMARK_MAX_LEN = 180;
    
    @Autowired
    private TapeStockMapper stockMapper;
    
    @Autowired
    private TapeInboundRequestMapper inboundMapper;
    
    @Autowired
    private TapeOutboundRequestMapper outboundMapper;
    
    @Autowired
    private TapeStockLogMapper logMapper;

    @Autowired
    private ScheduleMaterialLockMapper scheduleMaterialLockMapper;

    @Autowired
    private TapeSpecMapper tapeSpecMapper;

    @Autowired
    private StockFlowLogService stockFlowLogService;

    @Autowired
    private PurchaseReceiptItemMapper purchaseReceiptItemMapper;

    @Autowired
    private PurchaseReceiptMapper purchaseReceiptMapper;

    @Autowired
    private PurchaseOrderItemMapper purchaseOrderItemMapper;

    @Autowired
    private PurchaseSupplierMapper purchaseSupplierMapper;

    @Autowired
    private SalesOrderMapper salesOrderMapper;

    @Autowired
    private SalesOrderItemMapper salesOrderItemMapper;

    @Autowired
    private RedisTemplate<Object, Object> redisTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ChemicalStockMapper chemicalStockMapper;

    @Autowired
    private ChemicalStockDetailMapper chemicalStockDetailMapper;

    @Autowired
    private FilmStockMapper filmStockMapper;

    @Autowired
    private FilmStockDetailMapper filmStockDetailMapper;

    @Autowired
    private PackageStockMapper packageStockMapper;

    @Autowired
    private PackageStockDetailMapper packageStockDetailMapper;

    @Autowired
    private TapeFormulaMapper tapeFormulaMapper;

    private static final Map<String, ImportTaskState> IMPORT_TASKS = new ConcurrentHashMap<>();

    private static class ImportTaskState {
        private String taskId;
        private String status; // PENDING/RUNNING/SUCCESS/FAILED
        private int totalRows;
        private int processedRows;
        private int successCount;
        private int failCount;
        private String message;
        private String startedAt;
        private String finishedAt;
        private List<String> errors = new ArrayList<>();
        private byte[] failedExcelBytes;
    }

    private static class ByteArrayMultipartFile implements MultipartFile {
        private final String name;
        private final String originalFilename;
        private final String contentType;
        private final byte[] content;

        ByteArrayMultipartFile(String name, String originalFilename, String contentType, byte[] content) {
            this.name = name;
            this.originalFilename = originalFilename;
            this.contentType = contentType;
            this.content = content == null ? new byte[0] : content;
        }

        @Override
        @NonNull
        public String getName() { return java.util.Objects.requireNonNull(name); }

        @Override
        public String getOriginalFilename() { return originalFilename; }

        @Override
        public String getContentType() { return contentType; }

        @Override
        public boolean isEmpty() { return content.length == 0; }

        @Override
        public long getSize() { return content.length; }

        @Override
        @NonNull
        public byte[] getBytes() { return java.util.Objects.requireNonNull(content); }

        @Override
        @NonNull
        public InputStream getInputStream() { return new ByteArrayInputStream(content); }

        @Override
        public void transferTo(@NonNull java.io.File dest) throws IOException, IllegalStateException {
            java.nio.file.Files.write(dest.toPath(), content);
        }
    }
    
    // ============= 库存管理 =============
      @Override
    public IPage<TapeStock> getStockPage(int page, int size, String qrCode, String materialCode, String rollType, String location) {
        LambdaQueryWrapper<TapeStock> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TapeStock::getStatus, 1);
        // 二维码/批次号查询
        if (StringUtils.hasText(qrCode)) {
            wrapper.and(w -> w.like(TapeStock::getQrCode, qrCode)
                    .or().like(TapeStock::getBatchNo, qrCode));
        }
        if (StringUtils.hasText(materialCode)) {
            wrapper.like(TapeStock::getMaterialCode, materialCode);
        }
        // 卷类型查询
        if (StringUtils.hasText(rollType)) {
            wrapper.eq(TapeStock::getRollType, rollType);
        }
        if (StringUtils.hasText(location)) {
            wrapper.eq(TapeStock::getLocation, location);
        }
        wrapper.orderByAsc(TapeStock::getProdDate);
        Page<TapeStock> pageParam = new Page<>(page, size);
        pageParam.setOptimizeCountSql(false);
        return stockMapper.selectPage(pageParam, wrapper);
    }
    
    @Override
    public List<TapeStock> getStockSummary() {
        // 先归一化面积字段，避免空值导致前端无数据
        try {
            stockMapper.normalizeAreaFields();
        } catch (Exception ignored) {
            // 忽略非关键错误，继续查询
        }
        return stockMapper.selectSummaryByMaterial((String) null, true);  // 默认包含退货专仓
    }

    @Override
    public List<TapeStock> getStockSummary(String location) {
        // 先归一化面积字段，避免空值导致前端无数据
        try {
            stockMapper.normalizeAreaFields();
        } catch (Exception ignored) {
            // 忽略非关键错误，继续查询
        }
        return stockMapper.selectSummaryByMaterial(location, true);  // 默认包含退货专仓
    }

    @Override
    public List<TapeStock> getStockSummary(Boolean includeReturnWarehouse) {
        // 先归一化面积字段，避免空值导致前端无数据
        try {
            stockMapper.normalizeAreaFields();
        } catch (Exception ignored) {
            // 忽略非关键错误，继续查询
        }
        return stockMapper.selectSummaryByMaterial((String) null, includeReturnWarehouse);
    }

    @Override
    public IPage<TapeStock> getStockSummaryPage(int page, int size, String materialCode) {
        try {
            stockMapper.normalizeAreaFields();
        } catch (Exception ignored) {
        }
        Page<TapeStock> pageParam = new Page<>(page, size);
        pageParam.setOptimizeCountSql(false);
        long offset = (pageParam.getCurrent() - 1) * pageParam.getSize();
        List<TapeStock> records = stockMapper.selectSummaryByMaterialPageList(offset, pageParam.getSize(), materialCode, (String) null, true);  // 默认包含退货专仓
        Long total = stockMapper.countSummaryByMaterial(materialCode, (String) null, true);  // 默认包含退货专仓
        pageParam.setRecords(records);
        pageParam.setTotal(total != null ? total : 0);
        return pageParam;
    }

    @Override
    public IPage<TapeStock> getStockSummaryPage(int page, int size, String materialCode, String location) {
        try {
            stockMapper.normalizeAreaFields();
        } catch (Exception ignored) {
        }
        Page<TapeStock> pageParam = new Page<>(page, size);
        pageParam.setOptimizeCountSql(false);
        long offset = (pageParam.getCurrent() - 1) * pageParam.getSize();
        List<TapeStock> records = stockMapper.selectSummaryByMaterialPageList(offset, pageParam.getSize(), materialCode, location, true);  // 默认包含退货专仓
        Long total = stockMapper.countSummaryByMaterial(materialCode, location, true);  // 默认包含退货专仓
        pageParam.setRecords(records);
        pageParam.setTotal(total != null ? total : 0);
        return pageParam;
    }

    @Override
    public IPage<TapeStock> getStockSummaryPage(int page, int size, String materialCode, Boolean includeReturnWarehouse) {
        try {
            stockMapper.normalizeAreaFields();
        } catch (Exception ignored) {
        }
        Page<TapeStock> pageParam = new Page<>(page, size);
        pageParam.setOptimizeCountSql(false);
        long offset = (pageParam.getCurrent() - 1) * pageParam.getSize();
        List<TapeStock> records = stockMapper.selectSummaryByMaterialPageList(offset, pageParam.getSize(), materialCode, (String) null, includeReturnWarehouse);
        Long total = stockMapper.countSummaryByMaterial(materialCode, (String) null, includeReturnWarehouse);
        pageParam.setRecords(records);
        pageParam.setTotal(total != null ? total : 0);
        return pageParam;
    }
    
    @Override
    public List<TapeStock> getStockByMaterialFIFO(String materialCode) {
        try {
            stockMapper.normalizeAreaFields();
        } catch (Exception ignored) {
        }
        return stockMapper.selectByMaterialCodeFIFO(materialCode);
    }

    @Override
    public List<TapeStock> searchStockByMaterialKeyword(String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return new ArrayList<>();
        }
        try {
            stockMapper.normalizeAreaFields();
        } catch (Exception ignored) {
        }
        return stockMapper.selectByMaterialKeywordFIFO(keyword.trim());
    }

    @Override
    public IPage<TapeStock> getStockByMaterialPage(int page, int size, String materialCode) {
        try {
            stockMapper.normalizeAreaFields();
        } catch (Exception ignored) {
        }
        Page<TapeStock> pageParam = new Page<>(page, size);
        pageParam.setOptimizeCountSql(false);
        return stockMapper.selectByMaterialCodePage(pageParam, materialCode, (String) null, true, "prod_date ASC, id ASC");  // 默认包含退货专仓
    }

    @Override
    public IPage<TapeStock> getStockByMaterialPage(int page, int size, String materialCode, String location) {
        try {
            stockMapper.normalizeAreaFields();
        } catch (Exception ignored) {
        }
        Page<TapeStock> pageParam = new Page<>(page, size);
        pageParam.setOptimizeCountSql(false);
        return stockMapper.selectByMaterialCodePage(pageParam, materialCode, location, true, "prod_date ASC, id ASC");  // 默认包含退货专仓
    }

    @Override
    public IPage<TapeStock> getStockByMaterialPage(int page, int size, String materialCode, Boolean includeReturnWarehouse) {
        return getStockByMaterialPage(page, size, materialCode, includeReturnWarehouse, null, null);
    }

    @Override
    public IPage<TapeStock> getStockByMaterialPage(int page, int size, String materialCode, Boolean includeReturnWarehouse,
                                                   String sortField, String sortOrder) {
        try {
            stockMapper.normalizeAreaFields();
        } catch (Exception ignored) {
        }
        Page<TapeStock> pageParam = new Page<>(page, size);
        pageParam.setOptimizeCountSql(false);
        String orderByClause = buildMaterialPageOrderByClause(sortField, sortOrder);
        return stockMapper.selectByMaterialCodePage(pageParam, materialCode, (String) null, includeReturnWarehouse, orderByClause);
    }

    private String buildMaterialPageOrderByClause(String sortField, String sortOrder) {
        String direction = "ascending".equalsIgnoreCase(sortOrder) ? "ASC" : "DESC";
        String sf = sortField == null ? "" : sortField.trim();
        switch (sf) {
            case "sequenceNo":
                return "sequence_no " + direction + ", id ASC";
            case "qrCode":
                return "qr_code " + direction + ", id ASC";
            case "batchNo":
                return "batch_no " + direction + ", id ASC";
            case "rollType":
                return "roll_type " + direction + ", id ASC";
            case "specDesc":
                return "spec_desc " + direction + ", id ASC";
            case "totalRolls":
                return "total_rolls " + direction + ", id ASC";
            case "location":
                return "location " + direction + ", id ASC";
            case "prodDate":
                return "prod_date " + direction + ", id ASC";
            case "availableArea":
                return "available_area " + direction + ", id ASC";
            default:
                return "prod_date ASC, id ASC";
        }
    }
    
    @Override
    public TapeStock getStockById(Long id) {
        return stockMapper.selectById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TapeStock stocktake(Long stockId, Integer actualRolls, BigDecimal actualSqm, String operator, String reason) {
        if (stockId == null) {
            throw new RuntimeException("库存ID不能为空");
        }
        if (actualRolls == null || actualRolls < 0) {
            throw new RuntimeException("盘点卷数必须大于等于0");
        }

        TapeStock stock = stockMapper.selectById(stockId);
        if (stock == null) {
            throw new RuntimeException("库存不存在");
        }

        int beforeRolls = stock.getTotalRolls() == null ? 0 : stock.getTotalRolls();
        BigDecimal beforeSqm = stock.getTotalSqm() == null ? BigDecimal.ZERO : stock.getTotalSqm();
        BigDecimal reserved = stock.getReservedArea() == null ? BigDecimal.ZERO : stock.getReservedArea();
        BigDecimal consumed = stock.getConsumedArea() == null ? BigDecimal.ZERO : stock.getConsumedArea();

        BigDecimal finalSqm;
        if (actualSqm != null) {
            if (actualSqm.compareTo(BigDecimal.ZERO) < 0) {
                throw new RuntimeException("盘点平米必须大于等于0");
            }
            finalSqm = actualSqm;
        } else if (beforeRolls > 0) {
            finalSqm = beforeSqm
                    .divide(BigDecimal.valueOf(beforeRolls), 8, BigDecimal.ROUND_HALF_UP)
                    .multiply(BigDecimal.valueOf(actualRolls));
        } else {
            finalSqm = BigDecimal.ZERO;
        }

        finalSqm = finalSqm.setScale(2, BigDecimal.ROUND_HALF_UP);
        BigDecimal minRequired = reserved.add(consumed).setScale(2, BigDecimal.ROUND_HALF_UP);
        if (finalSqm.compareTo(minRequired) < 0) {
            throw new RuntimeException("盘点后总平米不能小于已锁定+已消耗平米(" + minRequired.toPlainString() + ")");
        }

        BigDecimal available = finalSqm.subtract(minRequired).setScale(2, BigDecimal.ROUND_HALF_UP);
        int changeRolls = actualRolls - beforeRolls;

        stock.setTotalRolls(actualRolls);
        stock.setTotalSqm(finalSqm);
        stock.setAvailableArea(available);
        stock.setStatus(actualRolls > 0 ? 1 : 0);
        stockMapper.updateById(stock);

        String finalOperator = StringUtils.hasText(operator) ? operator.trim() : "system";
        String finalReason = StringUtils.hasText(reason) ? reason.trim() : "库存盘点";
        String remark = "库存盘点：卷数 " + beforeRolls + " -> " + actualRolls +
                "，平米 " + beforeSqm.setScale(2, BigDecimal.ROUND_HALF_UP).toPlainString() + " -> " + finalSqm.toPlainString() +
                "；原因：" + finalReason;

        saveStockLog(stock.getId(), stock.getBatchNo(), stock.getMaterialCode(), stock.getProductName(),
                TapeStockLog.TYPE_ADJUST, changeRolls, beforeRolls, actualRolls,
                "STOCKTAKE-" + stock.getId(), finalOperator, remark);

        return stockMapper.selectById(stockId);
    }
    
    @Override
    public TapeStock getStockByBatchNo(String batchNo) {
        return stockMapper.selectByBatchNo(batchNo);
    }
    
    @Override
    public Map<String, Object> importExcel(MultipartFile file) {
        return importExcelInternal(file, null);
    }

    @Override
    public Map<String, Object> importExcelAsync(MultipartFile file) {
        Map<String, Object> data = new HashMap<>();
        try {
            final byte[] fileBytes = file.getBytes();
            final String taskId = UUID.randomUUID().toString().replace("-", "");

            ImportTaskState state = new ImportTaskState();
            state.taskId = taskId;
            state.status = "PENDING";
            state.message = "任务已创建，等待执行";
            IMPORT_TASKS.put(taskId, state);

            CompletableFuture.runAsync(() -> {
                state.status = "RUNNING";
                state.startedAt = LocalDateTime.now().toString();
                state.message = "正在导入中";
                try {
                        MultipartFile memoryFile = new ByteArrayMultipartFile(
                            "file", "import.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", fileBytes);
                    Map<String, Object> result = importExcelInternal(memoryFile, state);
                    state.successCount = getInt(result.get("successCount"));
                    state.failCount = getInt(result.get("failCount"));
                    state.message = "导入完成：成功" + state.successCount + "条，失败/跳过" + state.failCount + "条";
                    state.status = "SUCCESS";
                } catch (Exception ex) {
                    state.status = "FAILED";
                    state.message = "导入失败: " + ex.getMessage();
                    state.errors.add(state.message);
                } finally {
                    state.finishedAt = LocalDateTime.now().toString();
                }
            });

            data.put("taskId", taskId);
            data.put("status", "PENDING");
            data.put("message", "异步导入任务已创建");
            return data;
        } catch (Exception e) {
            data.put("status", "FAILED");
            data.put("message", "创建异步导入任务失败: " + e.getMessage());
            return data;
        }
    }

    @Override
    public Map<String, Object> getImportTaskStatus(String taskId) {
        ImportTaskState state = IMPORT_TASKS.get(taskId);
        Map<String, Object> data = new HashMap<>();
        if (state == null) {
            data.put("exists", false);
            data.put("status", "NOT_FOUND");
            data.put("message", "任务不存在或已过期");
            return data;
        }
        data.put("exists", true);
        data.put("taskId", state.taskId);
        data.put("status", state.status);
        data.put("totalRows", state.totalRows);
        data.put("processedRows", state.processedRows);
        data.put("successCount", state.successCount);
        data.put("failCount", state.failCount);
        data.put("message", state.message);
        data.put("startedAt", state.startedAt);
        data.put("finishedAt", state.finishedAt);
        data.put("errors", state.errors);
        data.put("hasFailedFile", state.failedExcelBytes != null && state.failedExcelBytes.length > 0);
        return data;
    }

    @Override
    public byte[] getImportTaskFailedExcel(String taskId) {
        ImportTaskState state = IMPORT_TASKS.get(taskId);
        if (state == null) {
            return null;
        }
        return state.failedExcelBytes;
    }

    private Map<String, Object> importExcelInternal(MultipartFile file, ImportTaskState taskState) {
        Map<String, Object> result = new HashMap<>();
        int successCount = 0;
        int failCount = 0;
        List<String> errors = new ArrayList<>();
        List<Map<String, Object>> skippedData = new ArrayList<>();
        
        try (Workbook workbook = new XSSFWorkbook(file.getInputStream())) {
                Sheet sheet = workbook.getSheetAt(0);

                // 读取表头，支持按列名映射
                Map<String, Integer> headerIndex = new HashMap<>();
                Row headerRow = sheet.getRow(0);
                if (headerRow != null) {
                for (int c = 0; c <= headerRow.getLastCellNum(); c++) {
                    Cell cell = headerRow.getCell(c);
                    String name = getCellValue(cell);
                    if (name != null && !name.isEmpty()) {
                    headerIndex.put(normalizeHeaderName(name), c);
                    }
                }
                }

                if (taskState != null) {
                    taskState.totalRows = Math.max(sheet.getLastRowNum(), 0);
                }
            
                for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                if (taskState != null) {
                    taskState.processedRows = i;
                }
                Row row = sheet.getRow(i);
                if (row == null) continue;
                
                try {
                    TapeStock stock = new TapeStock();
                    // 基础字段（支持表头映射）
                    String rawMaterialCode = getCellValue(getCellByHeader(row, headerIndex,
                        new String[]{"料号", "物料编码", "产品编码", "material_code"}, -1));
                    String cleanedMaterialCode = sanitizeMaterialCode(rawMaterialCode);
                    stock.setMaterialCode(cleanedMaterialCode);

                    // 产品名称不从Excel导入，统一使用研发料号(tape_spec)对应名称
                    TapeSpec tapeSpec = null;
                    if (StringUtils.hasText(cleanedMaterialCode)) {
                        tapeSpec = tapeSpecMapper.selectByMaterialCode(cleanedMaterialCode);
                    }
                    if (tapeSpec == null) {
                        failCount++;
                        Map<String, Object> skippedRow = new HashMap<>();
                        skippedRow.put("行号", i + 1);
                        skippedRow.put("原始料号", rawMaterialCode);
                        skippedRow.put("清理后料号", cleanedMaterialCode);
                        skippedRow.put("原因", "研发料号不存在（tape_spec未维护）");
                        skippedData.add(skippedRow);
                        continue;
                    }

                    stock.setProductName(tapeSpec.getProductName());
                    stock.setBatchNo(getCellValue(getCellByHeader(row, headerIndex,
                        new String[]{"生产批次号", "批次号", "batch_no"}, -1)));
                    
                    // 历史数据导入：不处理二维码字段，统一由系统自动生成
                    stock.setQrCode(null);
                    String rollType = getCellValue(getCellByHeader(row, headerIndex,
                        new String[]{"卷类型", "卷种", "roll_type"}, -1));
                    if (rollType != null && !rollType.isEmpty()) {
                    stock.setRollType(normalizeRollType(rollType));
                    } else {
                    // 支持“是否母卷”字段
                    String isMother = getCellValue(getCellByHeader(row, headerIndex,
                        new String[]{"是否母卷", "母卷"}, -1));
                    if (StringUtils.hasText(isMother) && ("是".equals(isMother) || "Y".equalsIgnoreCase(isMother))) {
                        stock.setRollType("母卷");
                    } else if (StringUtils.hasText(isMother) && ("否".equals(isMother) || "N".equalsIgnoreCase(isMother))) {
                        stock.setRollType("复卷");
                    } else {
                        stock.setRollType("母卷");
                    }
                    }
                    
                    // 规格信息
                    stock.setThickness(getIntCellValue(getCellByHeader(row, headerIndex,
                        new String[]{"厚度", "厚度μ", "厚度μm", "厚度um", "厚度(μ)", "厚度（μ）", "厚度(μm)", "厚度（μm）", "厚度(um)", "厚度（um）", "thickness"}, -1)));
                    stock.setWidth(getIntCellValue(getCellByHeader(row, headerIndex,
                        new String[]{"宽度", "宽度mm", "宽度(mm)", "宽度（mm）", "width"}, -1)));
                    stock.setLength(getIntCellValue(getCellByHeader(row, headerIndex,
                        new String[]{"长度", "长度(M)", "长度（M）", "长度m", "length"}, -1)));
                    Integer totalRolls = getIntCellValue(getCellByHeader(row, headerIndex,
                        new String[]{"总数", "总数(卷)", "总数（卷）", "库存卷数", "卷数", "库存卷", "rolls"}, -1));
                    stock.setTotalRolls(totalRolls);
                    stock.setLocation(getCellValue(getCellByHeader(row, headerIndex,
                        new String[]{"卡板位", "卡板数", "库位", "location"}, -1)));
                    stock.setProdYear(getIntCellValue(getCellByHeader(row, headerIndex,
                        new String[]{"生产年份", "生产年", "年"}, -1)));
                    stock.setProdMonth(getIntCellValue(getCellByHeader(row, headerIndex,
                        new String[]{"生产月份", "生产月", "月"}, -1)));
                    stock.setProdDay(getIntCellValue(getCellByHeader(row, headerIndex,
                        new String[]{"生产日", "日"}, -1)));
                    LocalDate prodDate = parseDateCellValue(getCellByHeader(row, headerIndex,
                        new String[]{"生产日期", "生产日期日", "日期", "入库日期", "prod_date"}, -1));
                    if (prodDate != null) {
                        stock.setProdDate(prodDate);
                        stock.setProdYear(prodDate.getYear());
                        stock.setProdMonth(prodDate.getMonthValue());
                        stock.setProdDay(prodDate.getDayOfMonth());
                    }
                    String reasonText = getCellValue(getCellByHeader(row, headerIndex,
                        new String[]{"原因", "备注", "remark"}, -1));
                    String numberText = getCellValue(getCellByHeader(row, headerIndex,
                        new String[]{"数字号", "数字号", "序号", "sequence_no"}, -1));
                    stock.setRemark(composeRemark(numberText, reasonText));

                    // 可选：序号字段，仅支持sequence_no，不再使用“数字号/数字号”写入序号
                    Integer sequenceNo = getIntCellValue(getCellByHeader(row, headerIndex,
                        new String[]{"sequence_no"}, -1));
                    if (sequenceNo != null && sequenceNo > 0) {
                        stock.setSequenceNo(sequenceNo);
                    }

                    // 必填校验
                    if (!StringUtils.hasText(stock.getMaterialCode())) {
                        throw new RuntimeException("料号不能为空");
                    }
                    if (!StringUtils.hasText(stock.getBatchNo())) {
                        throw new RuntimeException("生产批次号不能为空");
                    }
                    String originalBatchNo = stock.getBatchNo().trim();
                    stock.setBatchNo(originalBatchNo);
                    if (stock.getThickness() == null || stock.getWidth() == null || stock.getLength() == null) {
                        throw new RuntimeException("规格(厚度/宽度/长度)不能为空");
                    }
                    if (stock.getTotalRolls() == null) {
                        stock.setTotalRolls(1);
                    }
                    normalizeHistoricalRolls(stock);
                    if (stock.getTotalRolls() <= 0) {
                        throw new RuntimeException("库存卷数必须大于0");
                    }

                    TapeStock rowStock = new TapeStock();
                    rowStock.setMaterialCode(stock.getMaterialCode());
                    rowStock.setProductName(stock.getProductName());
                    rowStock.setBatchNo(stock.getBatchNo());
                    rowStock.setParentBatchNo(stock.getParentBatchNo());
                    rowStock.setRollType(stock.getRollType());
                    rowStock.setThickness(stock.getThickness());
                    rowStock.setWidth(stock.getWidth());
                    rowStock.setLength(stock.getLength());
                    rowStock.setOriginalLength(stock.getOriginalLength());
                    rowStock.setCurrentLength(stock.getCurrentLength());
                    // 导入改为“一行Excel=一行库存”，卷数保留在totalRolls字段
                    rowStock.setTotalRolls(stock.getTotalRolls());
                    rowStock.setLocation(stock.getLocation());
                    rowStock.setSpecDesc(stock.getSpecDesc());
                    rowStock.setProdYear(stock.getProdYear());
                    rowStock.setProdMonth(stock.getProdMonth());
                    rowStock.setProdDay(stock.getProdDay());
                    rowStock.setProdDate(stock.getProdDate());
                    rowStock.setRemark(stock.getRemark());
                    rowStock.setStatus(1);
                    rowStock.initLength();
                    rowStock.generateSpecDesc();
                    safeGenerateProdDate(rowStock);
                    applyProdDateFallbackFromBatchNo(rowStock);
                    rowStock.calculateTotalSqm();
                    rowStock.setReservedArea(BigDecimal.ZERO);
                    rowStock.setConsumedArea(BigDecimal.ZERO);
                    BigDecimal totalSqm = rowStock.getTotalSqm() != null ? rowStock.getTotalSqm() : BigDecimal.ZERO;
                    rowStock.setAvailableArea(totalSqm);

                    // 历史导入自动生成二维码（不读取Excel二维码）
                    Integer maxSeq = stockMapper.selectMaxSequenceNoByBatchNo(stock.getBatchNo());
                    int nextSeq = (maxSeq != null ? maxSeq : 0) + 1;
                    if (stock.getSequenceNo() != null && stock.getSequenceNo() > 0) {
                        nextSeq = stock.getSequenceNo();
                    }
                    rowStock.setSequenceNo(nextSeq);
                    rowStock.generateQrCode();

                    // 兜底防重：若已存在则顺延序号直到唯一
                    int guard = 0;
                    while (stockMapper.selectByQrCode(rowStock.getQrCode()) != null) {
                        guard++;
                        rowStock.setSequenceNo(rowStock.getSequenceNo() + 1);
                        rowStock.generateQrCode();
                        if (guard > 10000) {
                            throw new RuntimeException("生成唯一二维码失败: " + stock.getBatchNo());
                        }
                    }
                    stockMapper.insert(rowStock);
                    successCount++;

                    // 记录统一流水 - Excel导入
                    String _unit = "卷";
                    BigDecimal _change = BigDecimal.valueOf(rowStock.getTotalRolls());
                    BigDecimal _before = BigDecimal.ZERO;
                    BigDecimal _after = BigDecimal.valueOf(rowStock.getTotalRolls());
                    stockFlowLogService.logStockChange(
                        StockFlowLog.StockType.TAPE.name(),
                        rowStock.getId(),
                        rowStock.getBatchNo(),
                        rowStock.getMaterialCode(),
                        rowStock.getProductName(),
                        StockFlowLog.OperationType.IN.name(),
                        _change,
                        _unit,
                        _before,
                        _after,
                        "EXCEL_IMPORT",
                        "SYSTEM",
                        "Excel导入库存"
                    );

                    continue;
                } catch (Exception e) {
                    failCount++;
                    errors.add("第" + (i + 1) + "行导入失败: " + e.getMessage());
                    Map<String, Object> skippedRow = new HashMap<>();
                    skippedRow.put("行号", i + 1);
                    skippedRow.put("原始料号", getCellValue(getCellByHeader(row, headerIndex,
                        new String[]{"料号", "物料编码", "产品编码", "material_code"}, 0)));
                    skippedRow.put("清理后料号", sanitizeMaterialCode(getCellValue(getCellByHeader(row, headerIndex,
                        new String[]{"料号", "物料编码", "产品编码", "material_code"}, 0))));
                    skippedRow.put("原因", e.getMessage());
                    skippedData.add(skippedRow);
                }

                if (taskState != null) {
                    taskState.successCount = successCount;
                    taskState.failCount = failCount;
                    taskState.errors = errors.size() > 300 ? errors.subList(0, 300) : new ArrayList<>(errors);
                    taskState.message = "处理中：已处理" + taskState.processedRows + "/" + taskState.totalRows + "行";
                }
            }
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "文件解析失败: " + e.getMessage());
            return result;
        }

        // 归一化面积字段，确保可用面积正确
        try {
            stockMapper.normalizeAreaFields();
        } catch (Exception ignored) {
        }
        
        byte[] skippedExcel = null;
        if (!skippedData.isEmpty()) {
            skippedExcel = generateSkippedDataExcel(skippedData);
        }

        result.put("success", true);
        result.put("successCount", successCount);
        result.put("failCount", failCount);
        result.put("skipCount", failCount);
        result.put("errors", errors);
        result.put("skippedExcel", skippedExcel != null ? Base64.getEncoder().encodeToString(skippedExcel) : null);

        if (taskState != null) {
            taskState.successCount = successCount;
            taskState.failCount = failCount;
            taskState.errors = errors.size() > 500 ? errors.subList(0, 500) : new ArrayList<>(errors);
            taskState.failedExcelBytes = skippedExcel;
        }
        return result;
    }

    private int getInt(Object val) {
        if (val == null) return 0;
        try {
            return Integer.parseInt(String.valueOf(val));
        } catch (Exception ex) {
            return 0;
        }
    }
    
    @Override
    public List<TapeStock> exportStock(String materialCode, String location) {
        LambdaQueryWrapper<TapeStock> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TapeStock::getStatus, 1);
        if (StringUtils.hasText(materialCode)) {
            wrapper.like(TapeStock::getMaterialCode, materialCode);
        }
        if (StringUtils.hasText(location)) {
            wrapper.eq(TapeStock::getLocation, location);
        }
        wrapper.orderByAsc(TapeStock::getProdDate);
        return stockMapper.selectList(wrapper);
    }
    
    // ============= 入库申请 =============
    
    @Override
    public IPage<TapeInboundRequest> getInboundPage(int page, int size, Integer status, String materialCode, String sourceType,
                                                    Long receiptId, Long itemId, String keyword) {
        LambdaQueryWrapper<TapeInboundRequest> wrapper = new LambdaQueryWrapper<>();
        if (status != null) {
            wrapper.eq(TapeInboundRequest::getStatus, status);
        }
        if (StringUtils.hasText(materialCode)) {
            wrapper.like(TapeInboundRequest::getMaterialCode, materialCode);
        }
        if (receiptId != null && receiptId > 0 && itemId != null && itemId > 0) {
            wrapper.like(TapeInboundRequest::getRemark, "[PURCHASE_RECEIPT]|receiptId=" + receiptId + "|itemId=" + itemId + "|");
        } else if (receiptId != null && receiptId > 0) {
            wrapper.like(TapeInboundRequest::getRemark, "[PURCHASE_RECEIPT]|receiptId=" + receiptId + "|");
        } else if (itemId != null && itemId > 0) {
            wrapper.like(TapeInboundRequest::getRemark, "|itemId=" + itemId + "|");
        }
        if (StringUtils.hasText(keyword)) {
            String k = keyword.trim();
            wrapper.and(w -> w.like(TapeInboundRequest::getRequestNo, k)
                .or().like(TapeInboundRequest::getBatchNo, k)
                .or().like(TapeInboundRequest::getCustomerBatchNo, k)
                .or().like(TapeInboundRequest::getMaterialCode, k)
                .or().like(TapeInboundRequest::getProductName, k)
                .or().like(TapeInboundRequest::getRemark, k));
        }
        String sourceKey = StringUtils.hasText(sourceType) ? sourceType.trim().toUpperCase() : "";
        if (StringUtils.hasText(sourceKey)) {
            if ("PURCHASE_RECEIVING".equals(sourceKey)) {
                wrapper.and(w -> w.like(TapeInboundRequest::getRemark, "[PURCHASE_RECEIPT]")
                    .or().like(TapeInboundRequest::getApplyDept, "采购"));
            } else if ("SALES_RETURN".equals(sourceKey)) {
                wrapper.like(TapeInboundRequest::getRemark, "[SALES_RETURN]");
            } else if ("PROD_PACKAGING".equals(sourceKey)) {
                wrapper.and(w -> w.like(TapeInboundRequest::getApplyDept, "生产")
                    .and(x -> x.like(TapeInboundRequest::getRemark, "PROCESS=SLITTING")
                        .or().like(TapeInboundRequest::getRemark, "PROCESS=SLIT")
                        .or().like(TapeInboundRequest::getRemark, "PROCESS=REWINDING")
                        .or().like(TapeInboundRequest::getRemark, "PROCESS=PACKAGING")
                        .or().like(TapeInboundRequest::getBatchNo, "-SLITTING-")
                        .or().like(TapeInboundRequest::getBatchNo, "-SLIT-")));
            } else if ("PROD_COATING".equals(sourceKey)) {
                wrapper.and(w -> w
                    // 1) 明确标记为涂布工序
                    .like(TapeInboundRequest::getRemark, "PROCESS=COATING")
                    .or().like(TapeInboundRequest::getRemark, "process=COATING")
                    .or().like(TapeInboundRequest::getRemark, "process=coating")
                    .or().like(TapeInboundRequest::getRemark, "PROCESS = COATING")
                    .or().like(TapeInboundRequest::getRemark, "process = COATING")
                    // 2) 历史/人工数据按部门或requestNo兜底
                    .or().like(TapeInboundRequest::getApplyDept, "涂布")
                    .or().like(TapeInboundRequest::getRequestNo, "MANUAL-COATING-")
                    .or().like(TapeInboundRequest::getRemark, "MANUAL-COATING")
                );
            } else if ("PRODUCTION".equals(sourceKey)) {
                wrapper.like(TapeInboundRequest::getApplyDept, "生产");
            } else if ("OTHER".equals(sourceKey)) {
                wrapper.and(w -> w.notLike(TapeInboundRequest::getApplyDept, "生产")
                    .and(x -> x.notLike(TapeInboundRequest::getApplyDept, "采购")
                        .notLike(TapeInboundRequest::getRemark, "[PURCHASE_RECEIPT]")
                        .notLike(TapeInboundRequest::getRemark, "[SALES_RETURN]")));
            }
        }
        // 历史数据中 create_time 可能为空，若仅按 create_time 排序会导致分页顺序不稳定，
        // 小程序“涂布入库”第一页可能看不到刚报工生成的单据。
        // 这里改为按 apply_time + id 倒序，确保最新申请稳定出现在前页。
        wrapper.orderByDesc(TapeInboundRequest::getApplyTime)
            .orderByDesc(TapeInboundRequest::getId);
        Page<TapeInboundRequest> pageParam = new Page<>(page, size);
        pageParam.setOptimizeCountSql(false);
        IPage<TapeInboundRequest> result = inboundMapper.selectPage(pageParam, wrapper);
        if (result != null && result.getRecords() != null) {
            Map<Long, LocalDate> expectedDateCache = new HashMap<>();
            for (TapeInboundRequest record : result.getRecords()) {
                normalizeInboundDisplayFields(record);
                applyInboundPlanTimeForDisplay(record, expectedDateCache);
            }
        }
        return result;
    }

    @Override
    public Map<String, Object> preparePurchaseInboundLabelPrint(Long inboundId, Map<String, Object> payload, String operator) {
        if (inboundId == null || inboundId <= 0) {
            throw new RuntimeException("入库申请ID不能为空");
        }
        TapeInboundRequest inbound = inboundMapper.selectById(inboundId);
        if (inbound == null) {
            throw new RuntimeException("入库申请不存在");
        }

        normalizeInboundDisplayFields(inbound);
        if (!isPurchaseReceiptInbound(inbound)) {
            throw new RuntimeException("仅支持采购收货来源的入库申请打印标签");
        }

        String materialCode = StringUtils.hasText(inbound.getMaterialCode()) ? inbound.getMaterialCode().trim() : "";
        String materialName = StringUtils.hasText(inbound.getProductName()) ? inbound.getProductName().trim() : materialCode;
        String specDesc = StringUtils.hasText(inbound.getSpecDesc()) ? inbound.getSpecDesc().trim() : "";
        String qtyUnit = StringUtils.hasText(inbound.getQtyUnit()) ? inbound.getQtyUnit().trim() : "";
        String supplierCode = resolvePurchaseLabelSupplierCode(payload, inbound, materialCode);
        String customerCode = resolvePurchaseLabelCustomerCode(payload, inbound, supplierCode);

        String materialCategory = resolvePurchaseLabelCategory(materialCode, materialName, specDesc, qtyUnit);
        // 包材类（纸箱/纸管/PE管）：每个入仓规格只打1张；其余类别延续“按数量/可手工调整”模式
        boolean packagingLike = "PACKAGING".equalsIgnoreCase(materialCategory)
            || "PAPER_BOX_TUBE".equalsIgnoreCase(materialCategory);
        boolean allowManualCopies = !packagingLike;

        LocalDate productionDate = resolvePurchaseLabelProductionDate(payload, inbound);
        String incomingBatchNo = resolvePurchaseLabelBatchNo(payload, inbound);
        String customerBatchNo = incomingBatchNo;

        // 来料标签打印时：将填写的来料批次号回写到 customer_batch_no，
        // 入库列表“客户批次号”按该字段展示。
        if (StringUtils.hasText(incomingBatchNo)) {
            String current = normalizeCustomerBatchNo(inbound.getCustomerBatchNo());
            if (!incomingBatchNo.equals(current)) {
                inbound.setCustomerBatchNo(incomingBatchNo);
                inbound.setRemark(appendInboundRemarkToken(inbound.getRemark(), "customerBatchNo", incomingBatchNo));
                inboundMapper.updateById(inbound);
            }
        }

        int quantity = resolvePurchaseLabelQuantity(payload, inbound);
        int requestedCopies = resolvePurchaseLabelCopies(payload);
        int finalCopies;
        if (packagingLike) {
            finalCopies = 1;
        } else {
            // 默认按“数量”打印；若前端指定了 copies，则优先使用 copies
            finalCopies = Math.max(1, Math.min(200, requestedCopies > 0 ? requestedCopies : quantity));
        }

        List<Map<String, Object>> labels = new ArrayList<>();
        for (int i = 0; i < finalCopies; i++) {
            int seq = i + 1;
            String seqText = String.format("%03d", seq);
            // 二维码格式：来料批次号-流水号
            String qrCode = incomingBatchNo + "-" + seqText;
            // 兼容两类模板：
            // 1) productionDate 作为“日期字段”时，传 ISO 日期
            // 2) productionDateYYMMDD/printDate 作为“文本字段”时，传 yyMMdd
            String productionDateIso = productionDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            String productionDateText = productionDate.format(DateTimeFormatter.ofPattern("yyMMdd"));
            String printDateText = productionDateText;
            // 兼容既有 BarTender 模板：groupNo 常用于“供应/客户代码位”
            String groupNo = customerCode;

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("groupNo", groupNo);
            data.put("batchNo", incomingBatchNo);
            data.put("incomingBatchNo", incomingBatchNo);
            data.put("customerBatchNo", customerBatchNo);
            // 恢复：materialCode 始终输出真实料号（避免把供应商名/码写到物料代码位）
            data.put("materialCode", materialCode);
            data.put("supplierCode", supplierCode);
            data.put("customerCode", customerCode);
            data.put("materialName", materialName);
            data.put("spec", specDesc);
            data.put("specDesc", specDesc);
            data.put("productionDate", productionDateIso);
            data.put("productionDateYYMMDD", productionDateText);
            data.put("printDate", printDateText);
            data.put("printDateIso", productionDateIso);
            data.put("qty", quantity);
            data.put("quantity", quantity);
            data.put("qtyUnit", qtyUnit);
            // 与 BarTender 模板字段对齐：QRcode
            data.put("QRcode", qrCode);
            data.put("qrContent", qrCode);
            data.put("shelfLife", "");
            data.put("ShelfLife", "");
            data.put("requestNo", inbound.getRequestNo());
            data.put("applicant", inbound.getApplicant());
            data.put("operator", StringUtils.hasText(operator) ? operator.trim() : "");
            data.put("labelIndex", i + 1);
            data.put("labelCopies", finalCopies);
            data.put("dailySeq", seq);
            labels.add(data);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("inboundId", inbound.getId());
        result.put("requestNo", inbound.getRequestNo());
        result.put("materialCode", materialCode);
        result.put("supplierCode", supplierCode);
        result.put("customerCode", customerCode);
        result.put("materialName", materialName);
        result.put("specDesc", specDesc);
        result.put("qtyUnit", qtyUnit);
        result.put("productionDate", productionDate.toString());
        result.put("incomingBatchNo", incomingBatchNo);
        result.put("customerBatchNo", customerBatchNo);
        result.put("quantity", quantity);
        result.put("materialCategory", materialCategory);
        result.put("allowManualCopies", allowManualCopies);
        result.put("recommendedCopies", packagingLike ? 1 : Math.max(1, quantity));
        result.put("finalCopies", finalCopies);
        result.put("labels", labels);
        return result;
    }

    private String resolvePurchaseLabelCustomerCode(Map<String, Object> payload,
                                                    TapeInboundRequest inbound,
                                                    String fallbackSupplierCode) {
        if (payload != null && payload.get("customerCode") != null) {
            String text = String.valueOf(payload.get("customerCode")).trim();
            if (StringUtils.hasText(text)) {
                return normalizePartyCode(text);
            }
        }

        String fromRemark = extractInboundTokenFromRemark(inbound == null ? "" : inbound.getRemark(), "customerCode");
        if (StringUtils.hasText(fromRemark)) {
            return normalizePartyCode(fromRemark.trim());
        }

        // 当前采购收货链路没有稳定客户代码来源时，回退到供应商代码，保证模板字段不空
        return StringUtils.hasText(fallbackSupplierCode) ? normalizePartyCode(fallbackSupplierCode.trim()) : "";
    }

    private String normalizePartyCode(String codeOrName) {
        if (!StringUtils.hasText(codeOrName)) {
            return "";
        }
        String value = codeOrName.trim();
        String alias = extractInnerAlias(value);
        if (StringUtils.hasText(alias)) {
            return alias;
        }
        return value;
    }

    private String resolvePurchaseLabelSupplierCode(Map<String, Object> payload,
                                                    TapeInboundRequest inbound,
                                                    String fallbackMaterialCode) {
        // 1) 前端显式传入优先
        if (payload != null && payload.get("supplierCode") != null) {
            String text = String.valueOf(payload.get("supplierCode")).trim();
            if (StringUtils.hasText(text)) {
                return normalizeSupplierCode(text);
            }
        }

        // 2) remark 中的 supplierCode token
        String fromRemark = extractInboundTokenFromRemark(inbound == null ? "" : inbound.getRemark(), "supplierCode");
        if (StringUtils.hasText(fromRemark)) {
            return normalizeSupplierCode(fromRemark.trim());
        }

        // 2.1) 采购收货 remark 常见 supplier token
        String supplierToken = extractInboundTokenFromRemark(inbound == null ? "" : inbound.getRemark(), "supplier");
        if (StringUtils.hasText(supplierToken)) {
            return normalizeSupplierCode(supplierToken.trim());
        }

        // 3) 通过 receiptId 回溯采购收货主单 supplier 字段（常见：供应商名称(代码)）
        Long receiptId = parseLong(extractInboundTokenFromRemark(inbound == null ? "" : inbound.getRemark(), "receiptId"));
        if (receiptId != null && receiptId > 0) {
            PurchaseReceipt receipt = purchaseReceiptMapper.selectById(receiptId);
            if (receipt != null && StringUtils.hasText(receipt.getSupplier())) {
                return normalizeSupplierCode(receipt.getSupplier().trim());
            }
        }

        // 4) 兜底：用料号，避免模板字段为空
        return StringUtils.hasText(fallbackMaterialCode) ? fallbackMaterialCode.trim() : "";
    }

    private String normalizeSupplierCode(String supplierCodeOrName) {
        if (!StringUtils.hasText(supplierCodeOrName)) {
            return "";
        }
        String value = supplierCodeOrName.trim();

        String alias = extractInnerAlias(value);
        if (StringUtils.hasText(alias)) {
            return alias;
        }

        try {
            LambdaQueryWrapper<PurchaseSupplier> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(PurchaseSupplier::getIsDeleted, 0)
                    .and(w -> w.eq(PurchaseSupplier::getSupplierCode, value)
                            .or().eq(PurchaseSupplier::getSupplierName, value)
                            .or().eq(PurchaseSupplier::getShortName, value))
                    .last("LIMIT 1");
            PurchaseSupplier supplier = purchaseSupplierMapper.selectOne(wrapper);
            if (supplier != null && StringUtils.hasText(supplier.getSupplierCode())) {
                return supplier.getSupplierCode().trim();
            }
        } catch (Exception ignored) {
        }

        return value;
    }

    private String extractInnerAlias(String text) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        String value = text.trim();
        int left = Math.max(value.lastIndexOf('('), value.lastIndexOf('（'));
        int right = Math.max(value.lastIndexOf(')'), value.lastIndexOf('）'));
        if (left >= 0 && right > left) {
            return value.substring(left + 1, right).trim();
        }
        return "";
    }
    
    @Override
    @Transactional
    public TapeInboundRequest createInboundRequest(TapeInboundRequest request) {
        normalizeInboundDisplayFields(request);
        if (!StringUtils.hasText(request.getQtyUnit())) {
            request.setQtyUnit("卷");
        }
        String customerBatchNo = resolveInboundCustomerBatchNo(request);
        request.setCustomerBatchNo(customerBatchNo);
        request.setRemark(appendInboundRemarkToken(request.getRemark(), "customerBatchNo", customerBatchNo));

        // 生成单号
        String requestNo = inboundMapper.generateRequestNo();
        if (requestNo == null) {
            requestNo = "IN" + java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd").format(LocalDateTime.now()) + "0001";
        }
        request.setRequestNo(requestNo);
        request.setApplyTime(LocalDateTime.now());
        request.setStatus(TapeInboundRequest.STATUS_PENDING);
        
        // 生成规格描述和生产日期
        // 采购收货来源优先保留原始规格文本，避免单位被系统强制改写。
        if (!isPurchaseReceiptInbound(request)
                && request.getThickness() != null && request.getWidth() != null && request.getLength() != null) {
            request.setSpecDesc(request.getThickness() + "μm*" + request.getWidth() + "mm*" + request.getLength() + "m");
        }
        if (request.getProdYear() != null && request.getProdMonth() != null && request.getProdDay() != null) {
            int fullYear = request.getProdYear() < 100 ? 2000 + request.getProdYear() : request.getProdYear();
            request.setProdDate(LocalDate.of(fullYear, request.getProdMonth(), request.getProdDay()));
        }
        
        inboundMapper.insert(request);

        // 分切成品：点击入库即生效（自动审批，不要求扫码与库位）
        if (isSlittingFinishedInbound(request)) {
            String autoAuditor = StringUtils.hasText(request.getApplicant()) ? request.getApplicant() : "system";
            doApproveInbound(request.getId(), true, autoAuditor, "分切成品自动直入库", null, null);
            return inboundMapper.selectById(request.getId());
        }

        return request;
    }
    
    @Override
    @Transactional
    public void approveInbound(Long id, boolean approved, String auditor, String auditRemark, String scannedRollCode, String scannedLocation) {
        doApproveInbound(id, approved, auditor, auditRemark, scannedRollCode, scannedLocation);
    }

    @Override
    public Map<String, Object> approveInboundByRollCodes(List<String> rollCodes, String auditor, String auditRemark, String scannedLocation) {
        if (rollCodes == null || rollCodes.isEmpty()) {
            throw new RuntimeException("请先录入母卷号");
        }
        if (!StringUtils.hasText(scannedLocation)) {
            throw new RuntimeException("请先扫码卡板位");
        }

        List<String> normalized = rollCodes.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .distinct()
                .collect(java.util.stream.Collectors.toList());
        if (normalized.isEmpty()) {
            throw new RuntimeException("请先录入有效母卷号");
        }

        List<Map<String, Object>> failed = new ArrayList<>();
        List<String> approvedNos = new ArrayList<>();
        int successCount = 0;

        for (String rollCode : normalized) {
            try {
                LambdaQueryWrapper<TapeInboundRequest> wrapper = new LambdaQueryWrapper<>();
                wrapper.eq(TapeInboundRequest::getBatchNo, rollCode)
                        .eq(TapeInboundRequest::getStatus, TapeInboundRequest.STATUS_PENDING)
                        .orderByDesc(TapeInboundRequest::getId)
                        .last("LIMIT 1");
                TapeInboundRequest req = inboundMapper.selectOne(wrapper);
                if (req == null) {
                    Map<String, Object> fail = new HashMap<>();
                    fail.put("rollCode", rollCode);
                    fail.put("reason", "未找到待审批入库申请");
                    failed.add(fail);
                    continue;
                }

                doApproveInbound(req.getId(), true, auditor, auditRemark, rollCode, scannedLocation);
                successCount++;
                approvedNos.add(req.getRequestNo());
            } catch (Exception ex) {
                Map<String, Object> fail = new HashMap<>();
                fail.put("rollCode", rollCode);
                fail.put("reason", ex.getMessage());
                failed.add(fail);
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("total", normalized.size());
        result.put("successCount", successCount);
        result.put("failCount", normalized.size() - successCount);
        result.put("approvedRequestNos", approvedNos);
        result.put("failed", failed);
        return result;
    }

    private void doApproveInbound(Long id, boolean approved, String auditor, String auditRemark, String scannedRollCode, String scannedLocation) {
        TapeInboundRequest request = inboundMapper.selectById(id);
        if (request == null) {
            throw new RuntimeException("入库申请不存在");
        }
        if (request.getStatus() != TapeInboundRequest.STATUS_PENDING) {
            // 幂等处理：重复点击同一审批动作时，直接视为已完成，避免前端出现误报失败
            if (approved && request.getStatus() == TapeInboundRequest.STATUS_APPROVED) {
                return;
            }
            if (!approved && request.getStatus() == TapeInboundRequest.STATUS_REJECTED) {
                return;
            }
            throw new RuntimeException("该申请已处理");
        }
        
        request.setAuditor(auditor);
        request.setAuditTime(LocalDateTime.now());
        request.setAuditRemark(auditRemark);

        boolean salesReturnInbound = isSalesReturnInbound(request);
        boolean slittingFinishedInbound = isSlittingFinishedInbound(request);
        boolean strictReportSequenceInbound = isCoatingOrRewindingInbound(request);
        
        if (approved) {
            if (salesReturnInbound) {
                request.setLocation(RETURN_WAREHOUSE_LOCATION);
            } else if (slittingFinishedInbound) {
                // 分切成品直接入库：无需扫码/库位审核
                if (!StringUtils.hasText(request.getLocation()) || "待上架".equals(request.getLocation().trim())) {
                    request.setLocation(SLITTING_PENDING_OUTBOUND_LOCATION);
                }
            } else {
                // 其他入库（来料/涂布/复卷等）：必须入有效库位
                if (StringUtils.hasText(scannedLocation)) {
                    request.setLocation(scannedLocation.trim());
                }
                if (!StringUtils.hasText(request.getLocation()) || "待上架".equals(request.getLocation().trim())) {
                    throw new RuntimeException("请扫码卡板位后再审批通过");
                }

                // 卷号扫码改为可选校验：若已扫码则必须与申请批次一致
                String scannedCode = scannedRollCode == null ? "" : scannedRollCode.trim();
                if (StringUtils.hasText(scannedCode)) {
                    String expectedRollCode = request.getBatchNo() == null ? "" : request.getBatchNo().trim();
                    if (StringUtils.hasText(expectedRollCode) && !expectedRollCode.equalsIgnoreCase(scannedCode)) {
                        throw new RuntimeException("扫码母卷号与申请批次不一致，禁止入库");
                    }
                }
            }

            request.setStatus(TapeInboundRequest.STATUS_APPROVED);

            int rolls = request.getRolls() != null ? request.getRolls() : 0;
            if (rolls <= 0) {
                throw new RuntimeException("入库卷数必须大于0");
            }

                boolean needAutoFulfill = true;
                boolean purchaseBatchAggregateInbound = shouldAggregatePurchaseInboundByBatch(request);
                String purchaseInboundCategory = isPurchaseReceiptInbound(request)
                    ? resolvePurchaseInboundCategory(request)
                    : "";
            String inboundStockRemark = mergeRemarkWithCustomerBatch(request.getRemark(), request.getCustomerBatchNo());

            // 写入库存：
            // - 分切成品：按申请单聚合一条（totalRolls=申请卷数）
            // - 其他类型：仍按每卷一条
            Integer requestSequenceNo = request.getSequenceNo();
            if (strictReportSequenceInbound && (requestSequenceNo == null || requestSequenceNo <= 0)) {
                throw new RuntimeException("工序入库数字号缺失，必须与报工数字号一致");
            }
            Integer maxSeq = stockMapper.selectMaxSequenceNoByBatchNo(request.getBatchNo());
            int seq = maxSeq != null ? maxSeq : 0;
            if (requestSequenceNo != null && requestSequenceNo > 0) {
                seq = requestSequenceNo - 1;
            }

            if (isPurchaseReceiptInbound(request) && shouldRoutePurchaseInboundToRawWarehouse(purchaseInboundCategory)) {
                routePurchaseInboundToRawWarehouse(request, rolls, auditor, inboundStockRemark, purchaseInboundCategory);
                needAutoFulfill = false;
            } else if (slittingFinishedInbound) {
                // 分切成品按“批次+规格+卷数”入库（单线逻辑，不做逐卷数字号）
                LambdaQueryWrapper<TapeStock> mergeWrapper = new LambdaQueryWrapper<>();
                mergeWrapper.eq(TapeStock::getStatus, 1)
                        .eq(TapeStock::getMaterialCode, request.getMaterialCode())
                        .eq(TapeStock::getBatchNo, request.getBatchNo())
                        .eq(TapeStock::getRollType, "分切卷")
                        .eq(TapeStock::getLocation, request.getLocation())
                        .eq(TapeStock::getThickness, request.getThickness())
                        .eq(TapeStock::getWidth, request.getWidth())
                        .eq(TapeStock::getLength, request.getLength())
                        .last("LIMIT 1");
                TapeStock stock = stockMapper.selectOne(mergeWrapper);

                if (stock == null) {
                    stock = new TapeStock();
                    stock.setMaterialCode(request.getMaterialCode());
                    stock.setProductName(request.getProductName());
                    stock.setBatchNo(request.getBatchNo());
                    stock.setSequenceNo(null);
                    stock.setRollType("分切卷");
                    stock.setThickness(request.getThickness());
                    stock.setWidth(request.getWidth());
                    stock.setLength(request.getLength());
                    stock.setOriginalLength(request.getLength());
                    stock.setCurrentLength(request.getLength());
                    stock.setTotalRolls(rolls);
                    stock.setLocation(request.getLocation());
                    stock.setSpecDesc(request.getSpecDesc());
                    stock.setProdYear(request.getProdYear());
                    stock.setProdMonth(request.getProdMonth());
                    stock.setProdDay(request.getProdDay());
                    stock.setProdDate(request.getProdDate());
                    stock.setRemark(inboundStockRemark);
                    stock.setStatus(1);
                    stock.initLength();
                    stock.generateSpecDesc();
                    stock.generateProdDate();
                    stock.calculateTotalSqm();
                    stock.setReservedArea(BigDecimal.ZERO);
                    stock.setConsumedArea(BigDecimal.ZERO);
                    BigDecimal totalSqm = stock.getTotalSqm() != null ? stock.getTotalSqm() : BigDecimal.ZERO;
                    stock.setAvailableArea(totalSqm);
                    stock.generateQrCode();
                    stockMapper.insert(stock);

                    saveStockLog(stock.getId(), stock.getBatchNo(), stock.getMaterialCode(),
                            stock.getProductName(), TapeStockLog.TYPE_IN, rolls,
                            0, rolls, request.getRequestNo(), auditor,
                            "分切成品入库-聚合入库");

                    String _unit_in = "卷";
                    BigDecimal _change_in = BigDecimal.valueOf(rolls);
                    BigDecimal _before_in = BigDecimal.ZERO;
                    BigDecimal _after_in = BigDecimal.valueOf(rolls);
                    stockFlowLogService.logStockChange(
                            StockFlowLog.StockType.TAPE.name(),
                            stock.getId(),
                            stock.getBatchNo(),
                            stock.getMaterialCode(),
                            stock.getProductName(),
                            StockFlowLog.OperationType.IN.name(),
                            _change_in,
                            _unit_in,
                            _before_in,
                            _after_in,
                            request.getRequestNo(),
                            auditor,
                            "分切成品入库-聚合入库"
                    );
                } else {
                    int beforeRolls = stock.getTotalRolls() == null ? 0 : stock.getTotalRolls();
                    int afterRolls = beforeRolls + rolls;
                    stock.setTotalRolls(afterRolls);
                    stock.setRemark(inboundStockRemark);
                    stock.calculateTotalSqm();
                    BigDecimal totalSqm = stock.getTotalSqm() == null ? BigDecimal.ZERO : stock.getTotalSqm();
                    BigDecimal reserved = stock.getReservedArea() == null ? BigDecimal.ZERO : stock.getReservedArea();
                    BigDecimal consumed = stock.getConsumedArea() == null ? BigDecimal.ZERO : stock.getConsumedArea();
                    stock.setAvailableArea(totalSqm.subtract(reserved).subtract(consumed));
                    stockMapper.updateById(stock);

                    saveStockLog(stock.getId(), stock.getBatchNo(), stock.getMaterialCode(),
                            stock.getProductName(), TapeStockLog.TYPE_IN, rolls,
                            beforeRolls, afterRolls, request.getRequestNo(), auditor,
                            "分切成品入库-聚合追加");

                    String _unit_in = "卷";
                    BigDecimal _change_in = BigDecimal.valueOf(rolls);
                    BigDecimal _before_in = BigDecimal.valueOf(beforeRolls);
                    BigDecimal _after_in = BigDecimal.valueOf(afterRolls);
                    stockFlowLogService.logStockChange(
                            StockFlowLog.StockType.TAPE.name(),
                            stock.getId(),
                            stock.getBatchNo(),
                            stock.getMaterialCode(),
                            stock.getProductName(),
                            StockFlowLog.OperationType.IN.name(),
                            _change_in,
                            _unit_in,
                            _before_in,
                            _after_in,
                            request.getRequestNo(),
                            auditor,
                            "分切成品入库-聚合追加"
                    );
                }

                if (needAutoFulfill) {
                    needAutoFulfill = autoFulfillPendingLocks(stock, request.getRequestNo(), auditor);
                }
                } else if (purchaseBatchAggregateInbound) {
                // 采购收货中的管芯/纸箱类：按批次聚合一条库存
                LambdaQueryWrapper<TapeStock> mergeWrapper = new LambdaQueryWrapper<>();
                mergeWrapper.eq(TapeStock::getStatus, 1)
                    .eq(TapeStock::getMaterialCode, request.getMaterialCode())
                    .eq(TapeStock::getBatchNo, request.getBatchNo())
                    .eq(TapeStock::getRollType, "母卷")
                    .eq(TapeStock::getLocation, request.getLocation())
                    .eq(TapeStock::getThickness, request.getThickness())
                    .eq(TapeStock::getWidth, request.getWidth())
                    .eq(TapeStock::getLength, request.getLength())
                    .last("LIMIT 1");
                TapeStock stock = stockMapper.selectOne(mergeWrapper);

                if (stock == null) {
                    seq += 1;
                    stock = new TapeStock();
                    stock.setMaterialCode(request.getMaterialCode());
                    stock.setProductName(request.getProductName());
                    stock.setBatchNo(request.getBatchNo());
                    stock.setSequenceNo(seq);
                    stock.setRollType("母卷");
                    stock.setThickness(request.getThickness());
                    stock.setWidth(request.getWidth());
                    stock.setLength(request.getLength());
                    stock.setOriginalLength(request.getLength());
                    stock.setCurrentLength(request.getLength());
                    stock.setTotalRolls(rolls);
                    stock.setLocation(request.getLocation());
                    stock.setSpecDesc(request.getSpecDesc());
                    stock.setProdYear(request.getProdYear());
                    stock.setProdMonth(request.getProdMonth());
                    stock.setProdDay(request.getProdDay());
                    stock.setProdDate(request.getProdDate());
                    stock.setRemark(inboundStockRemark);
                    stock.setStatus(1);
                    stock.initLength();
                    stock.generateSpecDesc();
                    stock.generateProdDate();
                    stock.calculateTotalSqm();
                    stock.setReservedArea(BigDecimal.ZERO);
                    stock.setConsumedArea(BigDecimal.ZERO);
                    BigDecimal totalSqm = stock.getTotalSqm() != null ? stock.getTotalSqm() : BigDecimal.ZERO;
                    stock.setAvailableArea(totalSqm);
                    stock.generateQrCode();
                    stockMapper.insert(stock);

                    saveStockLog(stock.getId(), stock.getBatchNo(), stock.getMaterialCode(),
                        stock.getProductName(), TapeStockLog.TYPE_IN, rolls,
                        0, rolls, request.getRequestNo(), auditor,
                        "采购收货入库-按批次聚合入库");

                    String _unit_in = "卷";
                    BigDecimal _change_in = BigDecimal.valueOf(rolls);
                    BigDecimal _before_in = BigDecimal.ZERO;
                    BigDecimal _after_in = BigDecimal.valueOf(rolls);
                    stockFlowLogService.logStockChange(
                        StockFlowLog.StockType.TAPE.name(),
                        stock.getId(),
                        stock.getBatchNo(),
                        stock.getMaterialCode(),
                        stock.getProductName(),
                        StockFlowLog.OperationType.IN.name(),
                        _change_in,
                        _unit_in,
                        _before_in,
                        _after_in,
                        request.getRequestNo(),
                        auditor,
                        "采购收货入库-按批次聚合入库"
                    );
                } else {
                    int beforeRolls = stock.getTotalRolls() == null ? 0 : stock.getTotalRolls();
                    int afterRolls = beforeRolls + rolls;
                    stock.setTotalRolls(afterRolls);
                    stock.setRemark(inboundStockRemark);
                    stock.calculateTotalSqm();
                    BigDecimal totalSqm = stock.getTotalSqm() == null ? BigDecimal.ZERO : stock.getTotalSqm();
                    BigDecimal reserved = stock.getReservedArea() == null ? BigDecimal.ZERO : stock.getReservedArea();
                    BigDecimal consumed = stock.getConsumedArea() == null ? BigDecimal.ZERO : stock.getConsumedArea();
                    stock.setAvailableArea(totalSqm.subtract(reserved).subtract(consumed));
                    stockMapper.updateById(stock);

                    saveStockLog(stock.getId(), stock.getBatchNo(), stock.getMaterialCode(),
                        stock.getProductName(), TapeStockLog.TYPE_IN, rolls,
                        beforeRolls, afterRolls, request.getRequestNo(), auditor,
                        "采购收货入库-按批次聚合追加");

                    String _unit_in = "卷";
                    BigDecimal _change_in = BigDecimal.valueOf(rolls);
                    BigDecimal _before_in = BigDecimal.valueOf(beforeRolls);
                    BigDecimal _after_in = BigDecimal.valueOf(afterRolls);
                    stockFlowLogService.logStockChange(
                        StockFlowLog.StockType.TAPE.name(),
                        stock.getId(),
                        stock.getBatchNo(),
                        stock.getMaterialCode(),
                        stock.getProductName(),
                        StockFlowLog.OperationType.IN.name(),
                        _change_in,
                        _unit_in,
                        _before_in,
                        _after_in,
                        request.getRequestNo(),
                        auditor,
                        "采购收货入库-按批次聚合追加"
                    );
                }

                if (needAutoFulfill) {
                    needAutoFulfill = autoFulfillPendingLocks(stock, request.getRequestNo(), auditor);
                }
                } else {
                for (int i = 0; i < rolls; i++) {
                seq += 1;
                String originalBatchNo = request.getBatchNo();
                String inboundBatchNo = StringUtils.hasText(originalBatchNo) ? originalBatchNo.trim() : null;
                if (!StringUtils.hasText(inboundBatchNo)) {
                    throw new RuntimeException("入库批次号不能为空");
                }
                String inboundRollType = (salesReturnInbound || slittingFinishedInbound) ? "分切卷" : "母卷";

                TapeStock stock = null;
                if (StringUtils.hasText(inboundBatchNo)) {
                    LambdaQueryWrapper<TapeStock> batchOnlyWrapper = new LambdaQueryWrapper<>();
                    batchOnlyWrapper.eq(TapeStock::getStatus, 1)
                        .eq(TapeStock::getBatchNo, inboundBatchNo)
                        .last("LIMIT 1");
                    stock = stockMapper.selectOne(batchOnlyWrapper);
                    if (stock != null && StringUtils.hasText(stock.getMaterialCode())
                        && StringUtils.hasText(request.getMaterialCode())
                        && !stock.getMaterialCode().trim().equalsIgnoreCase(request.getMaterialCode().trim())) {
                    throw new RuntimeException("批次号已存在且对应料号不一致，禁止入库: " + inboundBatchNo);
                    }
                }

                if (stock == null) {
                    LambdaQueryWrapper<TapeStock> mergeWrapper = new LambdaQueryWrapper<>();
                    mergeWrapper.eq(TapeStock::getStatus, 1)
                        .eq(TapeStock::getMaterialCode, request.getMaterialCode())
                        .eq(TapeStock::getBatchNo, inboundBatchNo)
                        .eq(TapeStock::getRollType, inboundRollType)
                        .eq(TapeStock::getLocation, request.getLocation())
                        .eq(TapeStock::getThickness, request.getThickness())
                        .eq(TapeStock::getWidth, request.getWidth())
                        .eq(TapeStock::getLength, request.getLength())
                        .last("LIMIT 1");
                    stock = stockMapper.selectOne(mergeWrapper);
                }

                int beforeRolls;
                int afterRolls;
                if (stock == null) {
                    stock = new TapeStock();
                    stock.setMaterialCode(request.getMaterialCode());
                    stock.setProductName(request.getProductName());
                    stock.setBatchNo(inboundBatchNo);
                    stock.setParentBatchNo(null);
                    stock.setSequenceNo(seq);
                    stock.setRollType(inboundRollType);
                    stock.setThickness(request.getThickness());
                    stock.setWidth(request.getWidth());
                    stock.setLength(request.getLength());
                    stock.setOriginalLength(request.getLength());
                    stock.setCurrentLength(request.getLength());
                    stock.setTotalRolls(1);
                    stock.setLocation(request.getLocation());
                    stock.setSpecDesc(request.getSpecDesc());
                    stock.setProdYear(request.getProdYear());
                    stock.setProdMonth(request.getProdMonth());
                    stock.setProdDay(request.getProdDay());
                    stock.setProdDate(request.getProdDate());
                    stock.setRemark(inboundStockRemark);
                    stock.setStatus(1);
                    stock.initLength();
                    stock.generateSpecDesc();
                    stock.generateProdDate();
                    stock.calculateTotalSqm();
                    stock.setReservedArea(BigDecimal.ZERO);
                    stock.setConsumedArea(BigDecimal.ZERO);
                    BigDecimal totalSqm = stock.getTotalSqm() != null ? stock.getTotalSqm() : BigDecimal.ZERO;
                    stock.setAvailableArea(totalSqm);
                    stock.generateQrCode();
                    stockMapper.insert(stock);
                    beforeRolls = 0;
                    afterRolls = 1;
                } else {
                    beforeRolls = stock.getTotalRolls() == null ? 0 : stock.getTotalRolls();
                    afterRolls = beforeRolls + 1;
                    stock.setTotalRolls(afterRolls);
                    stock.setRemark(inboundStockRemark);
                    stock.calculateTotalSqm();
                    BigDecimal totalSqm = stock.getTotalSqm() == null ? BigDecimal.ZERO : stock.getTotalSqm();
                    BigDecimal reserved = stock.getReservedArea() == null ? BigDecimal.ZERO : stock.getReservedArea();
                    BigDecimal consumed = stock.getConsumedArea() == null ? BigDecimal.ZERO : stock.getConsumedArea();
                    stock.setAvailableArea(totalSqm.subtract(reserved).subtract(consumed));
                    stockMapper.updateById(stock);
                }

                // 记录流水（每卷）
                saveStockLog(stock.getId(), stock.getBatchNo(), stock.getMaterialCode(),
                        stock.getProductName(), TapeStockLog.TYPE_IN, 1,
                    beforeRolls, afterRolls, request.getRequestNo(), auditor,
                    salesReturnInbound
                        ? "销售退货入库-进入退货专仓"
                        : (slittingFinishedInbound ? "分切成品入库-进入成品待出库区" : "入库审批通过-单卷入库"));

                // 记录统一流水
                String _unit_in = "卷";
                BigDecimal _change_in = BigDecimal.ONE;
                BigDecimal _before_in = BigDecimal.valueOf(beforeRolls);
                BigDecimal _after_in = BigDecimal.valueOf(afterRolls);
                stockFlowLogService.logStockChange(
                    StockFlowLog.StockType.TAPE.name(),
                    stock.getId(),
                    stock.getBatchNo(),
                    stock.getMaterialCode(),
                    stock.getProductName(),
                    StockFlowLog.OperationType.IN.name(),
                    _change_in,
                    _unit_in,
                    _before_in,
                    _after_in,
                    request.getRequestNo(),
                    auditor,
                        salesReturnInbound
                            ? "销售退货入库-进入退货专仓"
                            : (slittingFinishedInbound ? "分切成品入库-进入成品待出库区" : "入库审批通过-单卷入库")
                );

                // 入库后自动回填待补锁（仅预留，不做实际消耗）
                if (needAutoFulfill) {
                    needAutoFulfill = autoFulfillPendingLocks(stock, request.getRequestNo(), auditor);
                }
            }
            }
        } else {
            request.setStatus(TapeInboundRequest.STATUS_REJECTED);
        }
        
        inboundMapper.updateById(request);
        syncPurchaseReceiptFromInboundRequest(request);
    }

    private void syncPurchaseReceiptFromInboundRequest(TapeInboundRequest request) {
        if (!isPurchaseReceiptInbound(request)) {
            return;
        }
        Long receiptId = parseLong(extractInboundTokenFromRemark(request == null ? null : request.getRemark(), "receiptId"));
        if (receiptId == null || receiptId <= 0) {
            return;
        }

        PurchaseReceipt receipt = purchaseReceiptMapper.selectById(receiptId);
        if (receipt == null || Integer.valueOf(1).equals(receipt.getIsDeleted())) {
            return;
        }

        String receiptToken = PURCHASE_RECEIPT_TAG + "|receiptId=" + receiptId + "|";

        Long pendingCount = inboundMapper.selectCount(
                new LambdaQueryWrapper<TapeInboundRequest>()
                        .like(TapeInboundRequest::getRemark, receiptToken)
                        .eq(TapeInboundRequest::getStatus, TapeInboundRequest.STATUS_PENDING)
        );
        Long approvedCount = inboundMapper.selectCount(
                new LambdaQueryWrapper<TapeInboundRequest>()
                        .like(TapeInboundRequest::getRemark, receiptToken)
                        .eq(TapeInboundRequest::getStatus, TapeInboundRequest.STATUS_APPROVED)
        );

        long pending = pendingCount == null ? 0L : pendingCount;
        long approved = approvedCount == null ? 0L : approvedCount;

        String targetStatus;
        if (approved > 0 && pending > 0) {
            targetStatus = "receiving";
        } else if (approved > 0) {
            targetStatus = "received";
        } else {
            targetStatus = "planned";
        }

        receipt.setStatus(targetStatus);
        receipt.setReceivedDate("received".equalsIgnoreCase(targetStatus) ? LocalDate.now() : null);
        receipt.setUpdatedAt(LocalDateTime.now());
        String updater = request == null ? "warehouse" : request.getAuditor();
        if (!StringUtils.hasText(updater)) {
            updater = "warehouse";
        }
        receipt.setUpdatedBy(updater);
        purchaseReceiptMapper.updateById(receipt);
    }

    private boolean isSalesReturnInbound(TapeInboundRequest request) {
        if (request == null) {
            return false;
        }
        String remark = request.getRemark();
        return StringUtils.hasText(remark) && remark.contains(SALES_RETURN_TAG);
    }

    private boolean isSlittingFinishedInbound(TapeInboundRequest request) {
        if (request == null) {
            return false;
        }
        String remark = request.getRemark();
        if (!StringUtils.hasText(remark)) {
            return false;
        }
        String upper = remark.toUpperCase();
        return upper.contains("PROCESS=SLITTING") || upper.contains("PROCESS=SLIT");
    }

    private boolean isCoatingOrRewindingInbound(TapeInboundRequest request) {
        if (request == null) {
            return false;
        }
        String remark = request.getRemark();
        if (!StringUtils.hasText(remark)) {
            return false;
        }
        String upper = remark.toUpperCase(Locale.ROOT);
        return upper.contains("PROCESS=COATING")
                || upper.contains("PROCESS=REWINDING")
                || upper.contains("PROCESS=REWIND");
    }

    private boolean isPurchaseReceiptInbound(TapeInboundRequest request) {
        if (request == null) {
            return false;
        }
        String remark = request.getRemark();
        if (StringUtils.hasText(remark) && remark.contains(PURCHASE_RECEIPT_TAG)) {
            return true;
        }
        String applyDept = request.getApplyDept();
        return StringUtils.hasText(applyDept) && applyDept.contains("采购");
    }

    /**
     * 采购收货入库策略：
    * - 管芯/纸箱类（包材仓）：按批次聚合入库
     * - 化工/薄膜/离型膜/离型纸/泡棉等：按数量拆分单卷入库
     */
    private boolean shouldAggregatePurchaseInboundByBatch(TapeInboundRequest request) {
        if (!isPurchaseReceiptInbound(request)) {
            return false;
        }
        String category = resolvePurchaseInboundCategory(request);
        return "PACKAGING".equals(category) || "PAPER_BOX_TUBE".equals(category);
    }

    private String resolvePurchaseInboundCategory(TapeInboundRequest request) {
        if (request == null) {
            return "GENERAL";
        }
        String recalculated = resolvePurchaseLabelCategory(
                request.getMaterialCode(),
                request.getProductName(),
                request.getSpecDesc(),
                request.getQtyUnit());

        String fromRemark = extractInboundTokenFromRemark(request.getRemark(), "inboundCategory");
        if (StringUtils.hasText(fromRemark)) {
            String tokenCategory = fromRemark.trim().toUpperCase(Locale.ROOT);
            // 历史兼容：PEG/PE管曾被错误打成 CHEMICAL/PAPER_BOX_TUBE，这里以重算结果兜底修正。
            if (("CHEMICAL".equals(tokenCategory) || "PAPER_BOX_TUBE".equals(tokenCategory))
                    && "PACKAGING".equals(recalculated)) {
                return "PACKAGING";
            }
            // 历史上存在inboundCategory=GENERAL但实际是泡棉/薄膜/化工的场景，
            // 这里对GENERAL做一次兜底重算，避免误入胶带仓。
            if (!"GENERAL".equals(tokenCategory)) {
                return tokenCategory;
            }
        }
        if (StringUtils.hasText(recalculated)) {
            return recalculated.trim().toUpperCase(Locale.ROOT);
        }
        return "GENERAL";
    }

    private String resolvePurchaseLabelCategory(String materialCode, String materialName, String specDesc, String qtyUnit) {
        String code = materialCode == null ? "" : materialCode.trim().toUpperCase();
        String name = materialName == null ? "" : materialName.trim();
        String spec = specDesc == null ? "" : specDesc.trim();
        String unit = qtyUnit == null ? "" : qtyUnit.trim();

        // 统一口径：PEG/PE管类进入包材仓
        if (isPegTubeMaterial(materialCode, materialName, specDesc)) {
            return "PACKAGING";
        }

        if (isPaperBoxLikeMaterial(materialCode, materialName, specDesc)
                || isTubeLikeMaterial(materialCode, materialName, specDesc)
                || "箱".equals(unit)
                || "支".equals(unit)
                || "个".equals(unit)) {
            return "PACKAGING";
        }

        try {
            TapeRawMaterial rawMaterial = tapeFormulaMapper.selectRawMaterialByCode(code);
            if (rawMaterial != null) {
                String category = normalizeLower(rawMaterial.getMaterialCategory());
                String categoryRaw = normalizeLower(rawMaterial.getMaterialCategoryRaw());
                String type = normalizeLower(rawMaterial.getMaterialType());
                String rawUnit = normalizeLower(rawMaterial.getUnit());

                if (containsAny(category, "film", "薄膜", "原膜") || containsAny(categoryRaw, "film", "薄膜", "原膜")) {
                    return "FILM";
                }
                if (containsAny(category, "foam", "泡棉")
                        || containsAny(categoryRaw, "foam", "泡棉")
                        || containsAny(type, "foam", "泡棉")) {
                    return "FOAM";
                }
                if (containsAny(category, "chemical", "化工")
                        || containsAny(categoryRaw, "chemical", "化工")
                        || containsAny(type, "solvent", "additive", "resin", "curing", "胶", "胶水", "溶剂", "助剂", "树脂", "固化")
                        || containsAny(rawUnit, "kg", "公斤", "千克", "桶", "包", "drum", "barrel")) {
                    return "CHEMICAL";
                }
            }
        } catch (Exception ignored) {
        }

        if (code.startsWith("LX") || name.contains("离型膜") || name.contains("离型纸") || spec.contains("离型")) {
            return "RELEASE_FILM_PAPER";
        }
        if (code.startsWith("PM") || name.contains("泡棉") || spec.contains("泡棉")) {
            return "FOAM";
        }
        if (code.startsWith("LMR") || code.startsWith("HHFT") || code.startsWith("RH")
                || name.contains("胶") || name.contains("胶水") || spec.contains("胶") || spec.contains("胶水")) {
            return "CHEMICAL";
        }
        if (code.startsWith("M") || name.contains("薄膜") || name.contains("膜") || spec.contains("PET") || spec.contains("BOPP")) {
            return "FILM";
        }
        if (name.contains("化工")
                || unit.equalsIgnoreCase("kg")
                || unit.equals("KG")
                || unit.contains("桶")
                || unit.contains("包")
                || "DRUM".equalsIgnoreCase(unit)
                || "BARREL".equalsIgnoreCase(unit)
                || "BUCKET".equalsIgnoreCase(unit)) {
            return "CHEMICAL";
        }
        return "GENERAL";
    }

    private String normalizeLower(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private boolean containsAny(String source, String... keywords) {
        if (!StringUtils.hasText(source) || keywords == null || keywords.length == 0) {
            return false;
        }
        for (String keyword : keywords) {
            if (StringUtils.hasText(keyword) && source.contains(keyword.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private boolean shouldRoutePurchaseInboundToRawWarehouse(String category) {
        String c = category == null ? "" : category.trim().toUpperCase(Locale.ROOT);
        return "CHEMICAL".equals(c)
                || "FILM".equals(c)
                || "RELEASE_FILM_PAPER".equals(c)
                || "FOAM".equals(c)
            || "PACKAGING".equals(c)
            // 兼容历史 token
                || "PAPER_BOX_TUBE".equals(c);
    }

    private void routePurchaseInboundToRawWarehouse(TapeInboundRequest request,
                                                    int rolls,
                                                    String auditor,
                                                    String inboundStockRemark,
                                                    String category) {
        String c = category == null ? "" : category.trim().toUpperCase(Locale.ROOT);
        if ("CHEMICAL".equals(c)) {
            inboundToChemicalWarehouse(request, rolls, auditor, inboundStockRemark);
            return;
        }
        if ("PACKAGING".equals(c) || "PAPER_BOX_TUBE".equals(c)) {
            inboundToPackageWarehouse(request, rolls, auditor, inboundStockRemark);
            return;
        }
        inboundToFilmWarehouse(request, rolls, auditor, inboundStockRemark);
    }

    private void inboundToPackageWarehouse(TapeInboundRequest request,
                                           int rolls,
                                           String auditor,
                                           String inboundStockRemark) {
        String materialCode = request.getMaterialCode() == null ? "" : request.getMaterialCode().trim();
        String materialName = request.getProductName() == null || request.getProductName().trim().isEmpty()
                ? materialCode : request.getProductName().trim();
        if (!StringUtils.hasText(materialCode)) {
            throw new RuntimeException("采购来料入包材仓失败：料号为空");
        }

        PackageStock stock = ensurePackageStockForInbound(request, auditor);
        String stdUom = "PCS";
        String packUom = normalizeInboundQtyUnit(request.getQtyUnit());
        if (!StringUtils.hasText(packUom) || "数量".equals(packUom)) {
            packUom = "个";
        }
        BigDecimal stdQtyPerPack = BigDecimal.ONE.setScale(3, BigDecimal.ROUND_HALF_UP);
        int inboundPackCount = resolveInboundPackCount(request, rolls, stdUom, stdQtyPerPack);
        int beforeQty = stock.getAvailableQuantity() == null ? 0 : stock.getAvailableQuantity();

        Date now = new Date();
        int maxSeq = queryPackageDetailMaxSeq(stock.getId(), request.getBatchNo());
        int seq = maxSeq + 1;

        PackageStockDetail detail = new PackageStockDetail();
        detail.setPackageStockId(stock.getId());
        detail.setMaterialCode(materialCode);
        detail.setBatchNo(request.getBatchNo());
        detail.setContainerNo(buildPackageInboundContainerNo(request, seq));
        detail.setPackUom(packUom);
        detail.setPackCount(Math.max(inboundPackCount, 1));
        detail.setStdUom(stdUom);
        detail.setStdQtyPerPack(stdQtyPerPack);
        detail.setQuantity(stdQtyPerPack.multiply(BigDecimal.valueOf(Math.max(inboundPackCount, 1))));
        detail.setWarehouse("包材仓");
        detail.setLocation(request.getLocation());
        detail.setSupplier("采购来料");
        detail.setInboundDate(now);
        detail.setStatus("available");
        detail.setRemark(inboundStockRemark);
        detail.setCreateBy(auditor);
        detail.setCreateTime(now);
        detail.setUpdateBy(auditor);
        detail.setUpdateTime(now);
        detail.setIsDeleted(0);
        packageStockDetailMapper.insert(detail);

        refreshPackageSummaryByDetails(stock.getId(), auditor);
        PackageStock latest = packageStockMapper.selectById(stock.getId());
        int afterQty = latest != null && latest.getAvailableQuantity() != null ? latest.getAvailableQuantity() : beforeQty + inboundPackCount;

        stockFlowLogService.logStockChange(
                StockFlowLog.StockType.PACKAGE.name(),
                stock.getId(),
                request.getBatchNo(),
                materialCode,
                materialName,
                StockFlowLog.OperationType.IN.name(),
                BigDecimal.valueOf(Math.max(inboundPackCount, 1)),
                stdUom,
                BigDecimal.valueOf(beforeQty),
                BigDecimal.valueOf(afterQty),
                request.getRequestNo(),
                auditor,
                "采购收货入库-包材仓"
        );
    }

    private void inboundToChemicalWarehouse(TapeInboundRequest request,
                                            int rolls,
                                            String auditor,
                                            String inboundStockRemark) {
        String materialCode = request.getMaterialCode() == null ? "" : request.getMaterialCode().trim();
        String materialName = request.getProductName() == null || request.getProductName().trim().isEmpty()
                ? materialCode : request.getProductName().trim();
        if (!StringUtils.hasText(materialCode)) {
            throw new RuntimeException("采购来料入化工仓失败：料号为空");
        }

        boolean pegChemical = isPegChemicalMaterial(
            request.getMaterialCode(),
            request.getProductName(),
            request.getSpecDesc());

        BigDecimal unitWeight = pegChemical
            ? BigDecimal.ONE.setScale(3, BigDecimal.ROUND_HALF_UP)
            : resolveInboundChemicalUnitWeightKg(request);
        String unit = pegChemical ? "PCS" : resolveInboundChemicalUnit(request);
        String packUom = pegChemical ? "PCS" : resolveInboundChemicalPackUom(request, unit);
        String stdUom = pegChemical ? "PCS" : "kg";
        BigDecimal stdQtyPerPack = pegChemical
            ? BigDecimal.ONE.setScale(3, BigDecimal.ROUND_HALF_UP)
            : resolveInboundChemicalStdQtyPerPackKg(request, unitWeight);
        int inboundPackCount = resolveInboundPackCount(request, rolls, stdUom, stdQtyPerPack);
        ChemicalStock stock = ensureChemicalStockForInbound(materialCode, materialName, unit, unitWeight, auditor);

        int beforeQty = stock.getAvailableQuantity() == null ? 0 : stock.getAvailableQuantity();
        Date now = new Date();
        int maxSeq = queryChemicalDetailMaxSeq(stock.getId(), request.getBatchNo());
        for (int i = 0; i < inboundPackCount; i++) {
            int seq = maxSeq + i + 1;
            String containerNo = buildChemicalInboundContainerNo(request, seq);

            ChemicalStockDetail detail = new ChemicalStockDetail();
            detail.setChemicalStockId(stock.getId());
            detail.setMaterialCode(materialCode);
            detail.setBatchNo(request.getBatchNo());
            detail.setContainerNo(containerNo);
            detail.setUnit(unit);
            detail.setPackUom(packUom);
            detail.setPackCount(1);
            detail.setStdUom(stdUom);
            detail.setStdQtyPerPack(stdQtyPerPack);
            detail.setWeight(stdQtyPerPack);
            detail.setLocation(request.getLocation());
            detail.setSupplier("采购来料");
            detail.setInboundDate(now);
            detail.setIsOpened(Boolean.FALSE);
            detail.setDangerLevel(1);
            detail.setStatus("available");
            detail.setRemark(inboundStockRemark);
            detail.setCreateTime(now);
            detail.setUpdateTime(now);
            chemicalStockDetailMapper.insert(detail);
        }

        refreshChemicalSummaryByDetails(stock.getId(), auditor);
        ChemicalStock latest = chemicalStockMapper.selectById(stock.getId());
        int afterQty = latest != null && latest.getAvailableQuantity() != null ? latest.getAvailableQuantity() : beforeQty + rolls;

        stockFlowLogService.logStockChange(
                StockFlowLog.StockType.CHEMICAL.name(),
                stock.getId(),
                request.getBatchNo(),
                materialCode,
                materialName,
                StockFlowLog.OperationType.IN.name(),
                BigDecimal.valueOf(inboundPackCount),
                unit,
                BigDecimal.valueOf(beforeQty),
                BigDecimal.valueOf(afterQty),
                request.getRequestNo(),
                auditor,
                "采购收货入库-化工仓"
        );
    }

    private void inboundToFilmWarehouse(TapeInboundRequest request,
                                        int rolls,
                                        String auditor,
                                        String inboundStockRemark) {
        String materialCode = request.getMaterialCode() == null ? "" : request.getMaterialCode().trim();
        String materialName = request.getProductName() == null || request.getProductName().trim().isEmpty()
                ? materialCode : request.getProductName().trim();
        if (!StringUtils.hasText(materialCode)) {
            throw new RuntimeException("采购来料入薄膜仓失败：料号为空");
        }

        // 采购收货场景下，优先按规格文本解析厚宽长，避免历史脏数据导致宽度/长度反写
        if (isPurchaseReceiptInbound(request)) {
            applyFilmDimensionsFromSpec(request);
        }

        FilmStock stock = ensureFilmStockForInbound(request, auditor);
        boolean pipeLike = isTubeLikeMaterial(request.getMaterialCode(), request.getProductName(), request.getSpecDesc())
                || "支".equals(normalizeInboundQtyUnit(request.getQtyUnit()))
                || "个".equals(normalizeInboundQtyUnit(request.getQtyUnit()))
                || "PCS".equalsIgnoreCase(normalizeInboundQtyUnit(request.getQtyUnit()));

        BigDecimal areaPerRoll = pipeLike
                ? BigDecimal.ZERO.setScale(3, BigDecimal.ROUND_HALF_UP)
                : resolveInboundFilmAreaPerRoll(request);
        BigDecimal stdQtyPerPack = pipeLike
                ? BigDecimal.ONE.setScale(3, BigDecimal.ROUND_HALF_UP)
                : resolveInboundFilmStdQtyPerPack(request, areaPerRoll);
        String stdUom = pipeLike ? "PCS" : "㎡";
        String packUom = pipeLike ? "支" : "卷";
        int inboundPackCount = resolveInboundPackCount(request, rolls, stdUom, stdQtyPerPack);
        BigDecimal beforeArea = stock.getAvailableArea() == null ? BigDecimal.ZERO : stock.getAvailableArea();

        Date now = new Date();
        int maxSeq = queryFilmDetailMaxSeq(stock.getId(), request.getBatchNo());
        if (pipeLike) {
            // 管类/支数物料：单条明细聚合记录，不按每支拆分
            int seq = maxSeq + 1;
            FilmStockDetail detail = new FilmStockDetail();
            detail.setFilmStockId(stock.getId());
            detail.setMaterialCode(materialCode);
            detail.setBatchNo(request.getBatchNo());
            detail.setRollNo(buildFilmInboundRollNo(request, seq));
            detail.setPackUom(packUom);
            detail.setPackCount(Math.max(inboundPackCount, 1));
            detail.setStdUom(stdUom);
            detail.setStdQtyPerPack(stdQtyPerPack);
            detail.setThickness(request.getThickness() == null ? null : BigDecimal.valueOf(request.getThickness()));
            detail.setWidth(request.getWidth());
            detail.setLength(request.getLength());
            detail.setOriginalLengthM(request.getLength() == null ? null : BigDecimal.valueOf(request.getLength()));
            detail.setCurrentLengthM(request.getLength() == null ? null : BigDecimal.valueOf(request.getLength()));
            detail.setArea(BigDecimal.ZERO.setScale(3, BigDecimal.ROUND_HALF_UP));
            detail.setQcStatus("qualified");
            detail.setWarehouse("薄膜仓");
            detail.setLocation(request.getLocation());
            detail.setSupplier("采购来料");
            detail.setInboundDate(now);
            detail.setStatus("available");
            detail.setRemark(inboundStockRemark);
            detail.setCreateBy(auditor);
            detail.setCreateTime(now);
            detail.setUpdateBy(auditor);
            detail.setUpdateTime(now);
            detail.setIsDeleted(0);
            filmStockDetailMapper.insert(detail);
        } else {
            for (int i = 0; i < inboundPackCount; i++) {
                int seq = maxSeq + i + 1;
                FilmStockDetail detail = new FilmStockDetail();
                detail.setFilmStockId(stock.getId());
                detail.setMaterialCode(materialCode);
                detail.setBatchNo(request.getBatchNo());
                detail.setRollNo(buildFilmInboundRollNo(request, seq));
                detail.setPackUom(packUom);
                detail.setPackCount(1);
                detail.setStdUom(stdUom);
                detail.setStdQtyPerPack(stdQtyPerPack);
                detail.setThickness(request.getThickness() == null ? null : BigDecimal.valueOf(request.getThickness()));
                detail.setWidth(request.getWidth());
                detail.setLength(request.getLength());
                detail.setOriginalLengthM(request.getLength() == null ? null : BigDecimal.valueOf(request.getLength()));
                detail.setCurrentLengthM(request.getLength() == null ? null : BigDecimal.valueOf(request.getLength()));
                detail.setArea(areaPerRoll);
                detail.setQcStatus("qualified");
                detail.setWarehouse("薄膜仓");
                detail.setLocation(request.getLocation());
                detail.setSupplier("采购来料");
                detail.setInboundDate(now);
                detail.setStatus("available");
                detail.setRemark(inboundStockRemark);
                detail.setCreateBy(auditor);
                detail.setCreateTime(now);
                detail.setUpdateBy(auditor);
                detail.setUpdateTime(now);
                detail.setIsDeleted(0);
                filmStockDetailMapper.insert(detail);
            }
        }

        refreshFilmSummaryByDetails(stock.getId(), auditor);
        FilmStock latest = filmStockMapper.selectById(stock.getId());
        BigDecimal afterArea = latest != null && latest.getAvailableArea() != null
            ? latest.getAvailableArea() : beforeArea.add(stdQtyPerPack.multiply(BigDecimal.valueOf(inboundPackCount)));

        stockFlowLogService.logStockChange(
                StockFlowLog.StockType.FILM.name(),
                stock.getId(),
                request.getBatchNo(),
                materialCode,
                materialName,
                StockFlowLog.OperationType.IN.name(),
            stdQtyPerPack.multiply(BigDecimal.valueOf(inboundPackCount)).setScale(3, BigDecimal.ROUND_HALF_UP),
                "㎡",
                beforeArea.setScale(3, BigDecimal.ROUND_HALF_UP),
                afterArea.setScale(3, BigDecimal.ROUND_HALF_UP),
                request.getRequestNo(),
                auditor,
                "采购收货入库-薄膜仓"
        );
    }

    private void applyFilmDimensionsFromSpec(TapeInboundRequest request) {
        if (request == null || !StringUtils.hasText(request.getSpecDesc())) {
            return;
        }
        String spec = request.getSpecDesc().trim();
        List<Integer> nums = new ArrayList<>();
        Matcher matcher = Pattern.compile("(\\d+(?:\\.\\d+)?)").matcher(spec);
        while (matcher.find() && nums.size() < 3) {
            try {
                int v = new BigDecimal(matcher.group(1)).setScale(0, BigDecimal.ROUND_HALF_UP).intValue();
                if (v > 0) {
                    nums.add(v);
                }
            } catch (Exception ignored) {
            }
        }

        if (nums.size() >= 3) {
            // 统一口径：三段规格=厚*宽*长
            request.setThickness(nums.get(0));
            request.setWidth(nums.get(1));
            request.setLength(nums.get(2));
            return;
        }

        if (nums.size() == 2) {
            String lower = spec.toLowerCase(Locale.ROOT);
            boolean hasUm = lower.contains("μm") || lower.contains("um");
            boolean hasMm = lower.contains("mm");
            boolean hasMeter = lower.matches(".*\\d+(?:\\.\\d+)?\\s*m(?!m).*" );

            // 两段规格：宽(mm)*长(m)
            if (hasMm && hasMeter) {
                request.setWidth(nums.get(0));
                request.setLength(nums.get(1));
                return;
            }
            // 两段规格：厚(μm)*宽(mm)
            if (hasUm && hasMm) {
                request.setThickness(nums.get(0));
                request.setWidth(nums.get(1));
            }
        }
    }

    private ChemicalStock ensureChemicalStockForInbound(String materialCode,
                                                        String materialName,
                                                        String unit,
                                                        BigDecimal unitWeight,
                                                        String auditor) {
        QueryWrapper<ChemicalStock> qw = new QueryWrapper<>();
        qw.eq("material_code", materialCode).orderByAsc("id").last("LIMIT 1");
        ChemicalStock stock = chemicalStockMapper.selectOne(qw);
        if (stock != null) {
            if (!StringUtils.hasText(stock.getMaterialName())) {
                stock.setMaterialName(materialName);
            }
            if (StringUtils.hasText(unit)
                    && (!StringUtils.hasText(stock.getUnit()) || !unit.trim().equals(stock.getUnit().trim()))) {
                stock.setUnit(unit);
            }
            if (unitWeight != null && unitWeight.compareTo(BigDecimal.ZERO) > 0
                    && (stock.getUnitWeight() == null || stock.getUnitWeight().compareTo(BigDecimal.ZERO) <= 0
                    || stock.getUnitWeight().compareTo(unitWeight) != 0)) {
                stock.setUnitWeight(unitWeight);
            }
            stock.setUpdateBy(auditor);
            stock.setUpdateTime(new Date());
            chemicalStockMapper.updateById(stock);
            return stock;
        }

        ChemicalStock created = new ChemicalStock();
        created.setMaterialCode(materialCode);
        created.setMaterialName(materialName);
        created.setChemicalType("adhesive");
        created.setUnit(unit);
        created.setUnitWeight(unitWeight);
        created.setTotalQuantity(0);
        created.setAvailableQuantity(0);
        created.setLockedQuantity(0);
        created.setSafetyStock(0);
        created.setStatus("active");
        created.setRemark("auto-create-by-purchase-inbound");
        created.setCreateBy(auditor);
        created.setUpdateBy(auditor);
        created.setCreateTime(new Date());
        created.setUpdateTime(new Date());
        chemicalStockMapper.insert(created);
        return created;
    }

    private FilmStock ensureFilmStockForInbound(TapeInboundRequest request, String auditor) {
        QueryWrapper<FilmStock> qw = new QueryWrapper<>();
        qw.eq("material_code", request.getMaterialCode()).orderByAsc("id").last("LIMIT 1");
        FilmStock stock = filmStockMapper.selectOne(qw);
        String inboundSpec = toSingleSpec(normalizeSpecDesc(request == null ? null : request.getSpecDesc()));
        if (stock != null) {
            if (!StringUtils.hasText(stock.getMaterialName()) && StringUtils.hasText(request.getProductName())) {
                stock.setMaterialName(request.getProductName().trim());
            }
            // 采购收货以采购单规格为准：有有效厚度时覆盖主档，避免沿用历史错误值
            if (request.getThickness() != null && request.getThickness() > 0) {
                stock.setThickness(BigDecimal.valueOf(request.getThickness()));
            }
            // 采购收货以采购单规格为准：有有效宽度时覆盖主档
            if (request.getWidth() != null && request.getWidth() > 0) {
                stock.setWidth(request.getWidth());
            }
            // 采购收货以采购单规格文本为准：有有效规格时覆盖主档，修正历史脏数据
            if (StringUtils.hasText(inboundSpec)) {
                stock.setSpecDesc(inboundSpec);
            }
            stock.setUpdateBy(auditor);
            stock.setUpdateTime(new Date());
            filmStockMapper.updateById(stock);
            return stock;
        }

        FilmStock created = new FilmStock();
        created.setMaterialCode(request.getMaterialCode());
        created.setMaterialName(StringUtils.hasText(request.getProductName()) ? request.getProductName().trim() : request.getMaterialCode());
        created.setSpecDesc(StringUtils.hasText(inboundSpec) ? inboundSpec : request.getSpecDesc());
        created.setThickness(request.getThickness() == null ? null : BigDecimal.valueOf(request.getThickness()));
        created.setWidth(request.getWidth());
        created.setTotalArea(BigDecimal.ZERO);
        created.setAvailableArea(BigDecimal.ZERO);
        created.setLockedArea(BigDecimal.ZERO);
        created.setTotalRolls(0);
        created.setAvailableRolls(0);
        created.setLockedRolls(0);
        created.setSafetyStock(BigDecimal.ZERO);
        created.setStatus("active");
        created.setRemark("auto-create-by-purchase-inbound");
        created.setCreateBy(auditor);
        created.setUpdateBy(auditor);
        created.setCreateTime(new Date());
        created.setUpdateTime(new Date());
        filmStockMapper.insert(created);
        return created;
    }

    private PackageStock ensurePackageStockForInbound(TapeInboundRequest request, String auditor) {
        QueryWrapper<PackageStock> qw = new QueryWrapper<>();
        qw.eq("material_code", request.getMaterialCode()).orderByAsc("id").last("LIMIT 1");
        PackageStock stock = packageStockMapper.selectOne(qw);
        String inboundSpec = toSingleSpec(normalizeSpecDesc(request == null ? null : request.getSpecDesc()));
        String normalizedUnit = normalizeInboundQtyUnit(request == null ? null : request.getQtyUnit());
        if (!StringUtils.hasText(normalizedUnit) || "数量".equals(normalizedUnit)) {
            normalizedUnit = "PCS";
        }

        if (stock != null) {
            if (!StringUtils.hasText(stock.getMaterialName()) && StringUtils.hasText(request.getProductName())) {
                stock.setMaterialName(request.getProductName().trim());
            }
            if (StringUtils.hasText(inboundSpec)) {
                stock.setSpecDesc(inboundSpec);
            }
            if (StringUtils.hasText(normalizedUnit)) {
                stock.setUnit(normalizedUnit);
            }
            stock.setUpdateBy(auditor);
            stock.setUpdateTime(new Date());
            packageStockMapper.updateById(stock);
            return stock;
        }

        PackageStock created = new PackageStock();
        created.setMaterialCode(request.getMaterialCode());
        created.setMaterialName(StringUtils.hasText(request.getProductName()) ? request.getProductName().trim() : request.getMaterialCode());
        created.setSpecDesc(StringUtils.hasText(inboundSpec) ? inboundSpec : request.getSpecDesc());
        created.setUnit(normalizedUnit);
        created.setTotalQuantity(0);
        created.setAvailableQuantity(0);
        created.setLockedQuantity(0);
        created.setTotalPackCount(0);
        created.setAvailablePackCount(0);
        created.setLockedPackCount(0);
        created.setSafetyStock(0);
        created.setStatus("active");
        created.setRemark("auto-create-by-purchase-inbound");
        created.setCreateBy(auditor);
        created.setUpdateBy(auditor);
        created.setCreateTime(new Date());
        created.setUpdateTime(new Date());
        packageStockMapper.insert(created);
        return created;
    }

    private String resolveInboundChemicalUnit(TapeInboundRequest request) {
        if (isPurchaseReceiptInbound(request)) {
            String purchaseUnit = normalizeInboundQtyUnit(resolvePurchaseInboundQtyUnit(request));
            if ("kg".equalsIgnoreCase(purchaseUnit) || "公斤".equals(purchaseUnit) || "千克".equals(purchaseUnit)) {
                return "桶";
            }
            if (StringUtils.hasText(purchaseUnit) && !"数量".equals(purchaseUnit)) {
                return purchaseUnit;
            }
        }

        String unit = normalizeInboundQtyUnit(request == null ? null : request.getQtyUnit());
        if (!StringUtils.hasText(unit)) {
            return "桶";
        }
        if ("ROLL".equalsIgnoreCase(unit) || "RL".equalsIgnoreCase(unit) || "卷".equals(unit)) {
            return "桶";
        }
        if ("kg".equalsIgnoreCase(unit) || "KG".equalsIgnoreCase(unit) || "公斤".equals(unit) || "千克".equals(unit)) {
            return "桶";
        }
        return unit;
    }

    private String resolveInboundChemicalPackUom(TapeInboundRequest request, String fallback) {
        String unit = normalizeInboundQtyUnit(request == null ? null : request.getQtyUnit());
        if (StringUtils.hasText(unit)) {
            String u = unit.trim();
            if ("包".equals(u)) {
                return "包";
            }
            if ("桶".equals(u)
                    || "kg".equalsIgnoreCase(u)
                    || "KG".equalsIgnoreCase(u)
                    || "公斤".equals(u)
                    || "千克".equals(u)) {
                return "桶";
            }
        }
        return StringUtils.hasText(fallback) ? fallback.trim() : "桶";
    }

    private BigDecimal resolveInboundChemicalStdQtyPerPackKg(TapeInboundRequest request, BigDecimal fallback) {
        BigDecimal bySpec = extractSpecNumber(request == null ? null : request.getSpecDesc(), "kg");
        if (bySpec != null && bySpec.compareTo(BigDecimal.ZERO) > 0) {
            return bySpec.setScale(3, BigDecimal.ROUND_HALF_UP);
        }
        if (fallback != null && fallback.compareTo(BigDecimal.ZERO) > 0) {
            return fallback.setScale(3, BigDecimal.ROUND_HALF_UP);
        }
        return BigDecimal.ONE.setScale(3, BigDecimal.ROUND_HALF_UP);
    }

    private BigDecimal resolveInboundChemicalUnitWeightKg(TapeInboundRequest request) {
        String spec = request == null ? null : request.getSpecDesc();
        if (StringUtils.hasText(spec)) {
            Matcher m = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*kg\\s*/", Pattern.CASE_INSENSITIVE).matcher(spec);
            if (m.find()) {
                try {
                    BigDecimal v = new BigDecimal(m.group(1));
                    if (v.compareTo(BigDecimal.ZERO) > 0) {
                        return v.setScale(3, BigDecimal.ROUND_HALF_UP);
                    }
                } catch (Exception ignore) {
                }
            }

            if (isPurchaseReceiptInbound(request)) {
                Matcher plain = Pattern.compile("^\\s*(\\d+(?:\\.\\d+)?)\\s*$").matcher(spec);
                if (plain.find()) {
                    try {
                        BigDecimal v = new BigDecimal(plain.group(1));
                        if (v.compareTo(BigDecimal.ZERO) > 0) {
                            return v.setScale(3, BigDecimal.ROUND_HALF_UP);
                        }
                    } catch (Exception ignore) {
                    }
                }
            }
        }

        if (isPurchaseReceiptInbound(request)) {
            BigDecimal byReceipt = resolveInboundChemicalUnitWeightFromReceipt(request);
            if (byReceipt != null && byReceipt.compareTo(BigDecimal.ZERO) > 0) {
                return byReceipt.setScale(3, BigDecimal.ROUND_HALF_UP);
            }
        }
        return BigDecimal.ONE;
    }

    private BigDecimal resolveInboundChemicalUnitWeightFromReceipt(TapeInboundRequest request) {
        if (request == null) {
            return null;
        }
        String remark = request.getRemark();
        if (!StringUtils.hasText(remark) || !remark.contains(PURCHASE_RECEIPT_TAG)) {
            return null;
        }
        try {
            Long itemId = parseLong(extractInboundTokenFromRemark(remark, "itemId"));
            if (itemId == null || itemId <= 0) {
                return null;
            }
            PurchaseReceiptItem item = purchaseReceiptItemMapper.selectById(itemId);
            if (item == null) {
                return null;
            }

            Integer receivedQty = item.getReceivedQty();
            if (receivedQty == null || receivedQty <= 0) {
                receivedQty = item.getExpectedQty();
            }
            if (receivedQty == null || receivedQty <= 0) {
                return null;
            }

            BigDecimal totalKg = null;
            if (item.getStockQty() != null && item.getStockQty().compareTo(BigDecimal.ZERO) > 0) {
                String stockUom = normalizeInboundQtyUnit(item.getStockUomCode());
                if ("kg".equalsIgnoreCase(stockUom) || "公斤".equals(stockUom) || "千克".equals(stockUom)) {
                    totalKg = item.getStockQty();
                }
            }
            if ((totalKg == null || totalKg.compareTo(BigDecimal.ZERO) <= 0)
                    && item.getPriceQty() != null && item.getPriceQty().compareTo(BigDecimal.ZERO) > 0) {
                String priceUom = normalizeInboundQtyUnit(item.getPriceUomCode());
                if ("kg".equalsIgnoreCase(priceUom) || "公斤".equals(priceUom) || "千克".equals(priceUom)) {
                    totalKg = item.getPriceQty();
                }
            }
            if ((totalKg == null || totalKg.compareTo(BigDecimal.ZERO) <= 0)
                    && item.getPurchaseQty() != null && item.getPurchaseQty().compareTo(BigDecimal.ZERO) > 0) {
                String purchaseUom = normalizeInboundQtyUnit(item.getPurchaseUomCode());
                if ("kg".equalsIgnoreCase(purchaseUom) || "公斤".equals(purchaseUom) || "千克".equals(purchaseUom)) {
                    totalKg = item.getPurchaseQty();
                }
            }
            if (totalKg == null || totalKg.compareTo(BigDecimal.ZERO) <= 0) {
                return null;
            }
            return totalKg.divide(BigDecimal.valueOf(receivedQty), 3, RoundingMode.HALF_UP);
        } catch (Exception ignore) {
            return null;
        }
    }

    private BigDecimal resolveInboundFilmAreaPerRoll(TapeInboundRequest request) {
        Integer width = request == null ? null : request.getWidth();
        Integer length = request == null ? null : request.getLength();
        if (width != null && width > 0 && length != null && length > 0) {
            return new BigDecimal(width)
                    .divide(new BigDecimal("1000"), 8, BigDecimal.ROUND_HALF_UP)
                    .multiply(new BigDecimal(length))
                    .setScale(3, BigDecimal.ROUND_HALF_UP);
        }
        return BigDecimal.ZERO.setScale(3, BigDecimal.ROUND_HALF_UP);
    }

    private BigDecimal resolveInboundFilmStdQtyPerPack(TapeInboundRequest request, BigDecimal fallbackAreaPerRoll) {
        BigDecimal bySpec = extractSpecNumber(request == null ? null : request.getSpecDesc(), "㎡");
        if (bySpec == null || bySpec.compareTo(BigDecimal.ZERO) <= 0) {
            bySpec = extractSpecNumber(request == null ? null : request.getSpecDesc(), "m2");
        }
        if (bySpec != null && bySpec.compareTo(BigDecimal.ZERO) > 0) {
            return bySpec.setScale(3, BigDecimal.ROUND_HALF_UP);
        }
        if (fallbackAreaPerRoll != null && fallbackAreaPerRoll.compareTo(BigDecimal.ZERO) > 0) {
            return fallbackAreaPerRoll.setScale(3, BigDecimal.ROUND_HALF_UP);
        }
        return BigDecimal.ONE.setScale(3, BigDecimal.ROUND_HALF_UP);
    }

    private int resolveInboundPackCount(TapeInboundRequest request,
                                        int rawQty,
                                        String stdUom,
                                        BigDecimal stdQtyPerPack) {
        int qty = Math.max(rawQty, 0);
        if (qty <= 0) {
            return 0;
        }
        String unit = normalizeInboundQtyUnit(request == null ? null : request.getQtyUnit());
        if (!StringUtils.hasText(unit)) {
            return qty;
        }
        if ("kg".equalsIgnoreCase(unit)
                || "KG".equalsIgnoreCase(unit)
                || "公斤".equals(unit)
                || "千克".equals(unit)
                || "㎡".equals(unit)
                || "m2".equalsIgnoreCase(unit)
                || "平方".equals(unit)) {
            if (stdQtyPerPack == null || stdQtyPerPack.compareTo(BigDecimal.ZERO) <= 0) {
                return qty;
            }
            BigDecimal requiredStd = BigDecimal.valueOf(qty);
            return requiredStd.divide(stdQtyPerPack, 0, RoundingMode.CEILING).intValue();
        }
        return qty;
    }

    private BigDecimal extractSpecNumber(String specDesc, String unitToken) {
        if (!StringUtils.hasText(specDesc) || !StringUtils.hasText(unitToken)) {
            return null;
        }
        String normalizedSpec = specDesc.replace("M²", "㎡").replace("m²", "㎡");
        Matcher matcher = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*" + Pattern.quote(unitToken), Pattern.CASE_INSENSITIVE)
                .matcher(normalizedSpec);
        if (!matcher.find()) {
            return null;
        }
        try {
            return new BigDecimal(matcher.group(1));
        } catch (Exception ignore) {
            return null;
        }
    }

    private int queryChemicalDetailMaxSeq(Long stockId, String batchNo) {
        if (stockId == null) {
            return 0;
        }
        QueryWrapper<ChemicalStockDetail> qw = new QueryWrapper<>();
        qw.eq("stock_id", stockId)
                .eq(StringUtils.hasText(batchNo), "batch_no", batchNo)
                .orderByDesc("id")
                .last("LIMIT 200");
        List<ChemicalStockDetail> list = chemicalStockDetailMapper.selectList(qw);
        int max = 0;
        for (ChemicalStockDetail one : list) {
            if (one == null || !StringUtils.hasText(one.getContainerNo())) {
                continue;
            }
            Matcher m = Pattern.compile("-(\\d{1,6})$").matcher(one.getContainerNo().trim());
            if (m.find()) {
                try {
                    max = Math.max(max, Integer.parseInt(m.group(1)));
                } catch (Exception ignore) {
                }
            }
        }
        return max;
    }

    private int queryFilmDetailMaxSeq(Long stockId, String batchNo) {
        if (stockId == null) {
            return 0;
        }
        QueryWrapper<FilmStockDetail> qw = new QueryWrapper<>();
        qw.eq("stock_id", stockId)
                .eq(StringUtils.hasText(batchNo), "batch_no", batchNo)
                .eq("is_deleted", 0)
                .orderByDesc("id")
                .last("LIMIT 200");
        List<FilmStockDetail> list = filmStockDetailMapper.selectList(qw);
        int max = 0;
        for (FilmStockDetail one : list) {
            if (one == null || !StringUtils.hasText(one.getRollNo())) {
                continue;
            }
            Matcher m = Pattern.compile("-(\\d{1,6})$").matcher(one.getRollNo().trim());
            if (m.find()) {
                try {
                    max = Math.max(max, Integer.parseInt(m.group(1)));
                } catch (Exception ignore) {
                }
            }
        }
        return max;
    }

    private int queryPackageDetailMaxSeq(Long stockId, String batchNo) {
        if (stockId == null) {
            return 0;
        }
        QueryWrapper<PackageStockDetail> qw = new QueryWrapper<>();
        qw.eq("stock_id", stockId)
                .eq(StringUtils.hasText(batchNo), "batch_no", batchNo)
                .eq("is_deleted", 0)
                .orderByDesc("id")
                .last("LIMIT 200");
        List<PackageStockDetail> list = packageStockDetailMapper.selectList(qw);
        int max = 0;
        for (PackageStockDetail one : list) {
            if (one == null || !StringUtils.hasText(one.getContainerNo())) {
                continue;
            }
            Matcher m = Pattern.compile("-(\\d{1,6})$").matcher(one.getContainerNo().trim());
            if (m.find()) {
                try {
                    max = Math.max(max, Integer.parseInt(m.group(1)));
                } catch (Exception ignore) {
                }
            }
        }
        return max;
    }

    private String buildChemicalInboundContainerNo(TapeInboundRequest request, int seq) {
        String base = StringUtils.hasText(request.getBatchNo()) ? request.getBatchNo().trim() : request.getMaterialCode();
        return base + "-" + String.format("%03d", Math.max(seq, 1));
    }

    private String buildFilmInboundRollNo(TapeInboundRequest request, int seq) {
        String base = StringUtils.hasText(request.getBatchNo()) ? request.getBatchNo().trim() : request.getMaterialCode();
        return base + "-" + String.format("%03d", Math.max(seq, 1));
    }

    private String buildPackageInboundContainerNo(TapeInboundRequest request, int seq) {
        String base = StringUtils.hasText(request.getBatchNo()) ? request.getBatchNo().trim() : request.getMaterialCode();
        return base + "-" + String.format("%03d", Math.max(seq, 1));
    }

    private void refreshChemicalSummaryByDetails(Long stockId, String auditor) {
        ChemicalStock stock = chemicalStockMapper.selectById(stockId);
        if (stock == null) {
            return;
        }
        QueryWrapper<ChemicalStockDetail> qw = new QueryWrapper<>();
        qw.eq("stock_id", stockId);
        List<ChemicalStockDetail> details = chemicalStockDetailMapper.selectList(qw);

        int available = 0;
        int locked = 0;
        BigDecimal weightSum = BigDecimal.ZERO;
        int weightCount = 0;
        for (ChemicalStockDetail d : details) {
            if (d == null) {
                continue;
            }
            String st = d.getStatus() == null ? "" : d.getStatus().trim().toLowerCase(Locale.ROOT);
            if ("used".equals(st)) {
                continue;
            }
            if ("locked".equals(st)) {
                locked++;
            } else {
                available++;
            }
            if (d.getWeight() != null && d.getWeight().compareTo(BigDecimal.ZERO) > 0) {
                weightSum = weightSum.add(d.getWeight());
                weightCount++;
            }
        }

        stock.setAvailableQuantity(available);
        stock.setLockedQuantity(locked);
        stock.setTotalQuantity(available + locked);
        stock.setAvailablePackCount(available);
        stock.setLockedPackCount(locked);
        stock.setTotalPackCount(available + locked);
        stock.setBucketCount(available + locked);
        if (weightCount > 0) {
            stock.setUnitWeight(weightSum.divide(BigDecimal.valueOf(weightCount), 2, BigDecimal.ROUND_HALF_UP));
        }
        Integer safety = stock.getSafetyStock() == null ? 0 : stock.getSafetyStock();
        if (available <= 0) {
            stock.setStatus("out_of_stock");
        } else if (safety > 0 && available < safety) {
            stock.setStatus("low_stock");
        } else {
            stock.setStatus("active");
        }
        stock.setUpdateBy(StringUtils.hasText(auditor) ? auditor : "system");
        stock.setUpdateTime(new Date());
        chemicalStockMapper.updateById(stock);
    }

    private void refreshFilmSummaryByDetails(Long stockId, String auditor) {
        FilmStock stock = filmStockMapper.selectById(stockId);
        if (stock == null) {
            return;
        }
        QueryWrapper<FilmStockDetail> qw = new QueryWrapper<>();
        qw.eq("stock_id", stockId).eq("is_deleted", 0);
        List<FilmStockDetail> details = filmStockDetailMapper.selectList(qw);

        BigDecimal totalArea = BigDecimal.ZERO;
        BigDecimal availableArea = BigDecimal.ZERO;
        BigDecimal lockedArea = BigDecimal.ZERO;
        int totalRolls = 0;
        int availableRolls = 0;
        int lockedRolls = 0;

        for (FilmStockDetail d : details) {
            if (d == null) {
                continue;
            }
            BigDecimal area = d.getArea() == null ? BigDecimal.ZERO : d.getArea();
            int packCount = d.getPackCount() != null && d.getPackCount() > 0 ? d.getPackCount() : 1;
            totalArea = totalArea.add(area);
            totalRolls += packCount;
            String st = d.getStatus() == null ? "" : d.getStatus().trim().toLowerCase(Locale.ROOT);
            if ("locked".equals(st)) {
                lockedArea = lockedArea.add(area);
                lockedRolls += packCount;
            } else if ("available".equals(st)) {
                availableArea = availableArea.add(area);
                availableRolls += packCount;
            }
        }

        stock.setTotalArea(totalArea.setScale(3, BigDecimal.ROUND_HALF_UP));
        stock.setAvailableArea(availableArea.setScale(3, BigDecimal.ROUND_HALF_UP));
        stock.setLockedArea(lockedArea.setScale(3, BigDecimal.ROUND_HALF_UP));
        stock.setTotalRolls(totalRolls);
        stock.setAvailableRolls(availableRolls);
        stock.setLockedRolls(lockedRolls);
        stock.setAvailablePackCount(availableRolls);
        stock.setLockedPackCount(lockedRolls);
        stock.setTotalPackCount(totalRolls);
        BigDecimal safety = stock.getSafetyStock() == null ? BigDecimal.ZERO : stock.getSafetyStock();
        if (availableArea.compareTo(BigDecimal.ZERO) <= 0) {
            stock.setStatus("out_of_stock");
        } else if (safety.compareTo(BigDecimal.ZERO) > 0 && availableArea.compareTo(safety) < 0) {
            stock.setStatus("low_stock");
        } else {
            stock.setStatus("active");
        }
        stock.setUpdateBy(StringUtils.hasText(auditor) ? auditor : "system");
        stock.setUpdateTime(new Date());
        filmStockMapper.updateById(stock);
    }

    private void refreshPackageSummaryByDetails(Long stockId, String auditor) {
        PackageStock stock = packageStockMapper.selectById(stockId);
        if (stock == null) {
            return;
        }

        QueryWrapper<PackageStockDetail> qw = new QueryWrapper<>();
        qw.eq("stock_id", stockId).eq("is_deleted", 0);
        List<PackageStockDetail> details = packageStockDetailMapper.selectList(qw);

        int total = 0;
        int available = 0;
        int locked = 0;
        for (PackageStockDetail d : details) {
            if (d == null) {
                continue;
            }
            int packCount = d.getPackCount() != null && d.getPackCount() > 0 ? d.getPackCount() : 1;
            total += packCount;
            String st = d.getStatus() == null ? "" : d.getStatus().trim().toLowerCase(Locale.ROOT);
            if ("locked".equals(st)) {
                locked += packCount;
            } else if (!"used".equals(st)) {
                available += packCount;
            }
        }

        stock.setTotalQuantity(total);
        stock.setAvailableQuantity(available);
        stock.setLockedQuantity(locked);
        stock.setTotalPackCount(total);
        stock.setAvailablePackCount(available);
        stock.setLockedPackCount(locked);
        Integer safety = stock.getSafetyStock() == null ? 0 : stock.getSafetyStock();
        if (available <= 0) {
            stock.setStatus("out_of_stock");
        } else if (safety > 0 && available < safety) {
            stock.setStatus("low_stock");
        } else {
            stock.setStatus("active");
        }
        stock.setUpdateBy(StringUtils.hasText(auditor) ? auditor : "system");
        stock.setUpdateTime(new Date());
        packageStockMapper.updateById(stock);
    }

    private LocalDate resolvePurchaseLabelProductionDate(Map<String, Object> payload, TapeInboundRequest inbound) {
        if (payload != null && payload.get("productionDate") != null) {
            String text = String.valueOf(payload.get("productionDate")).trim();
            if (text.length() >= 10) {
                LocalDate first10 = parseLocalDateSafe(text.substring(0, 10));
                if (first10 != null) {
                    return first10;
                }
            }
            LocalDate parsed = parseLocalDateSafe(text);
            if (parsed != null) {
                return parsed;
            }
        }
        if (inbound != null && inbound.getProdDate() != null) {
            return inbound.getProdDate();
        }
        return LocalDate.now();
    }

    private String resolvePurchaseLabelBatchNo(Map<String, Object> payload, TapeInboundRequest inbound) {
        if (payload != null && payload.get("incomingBatchNo") != null) {
            String text = String.valueOf(payload.get("incomingBatchNo")).trim();
            if (StringUtils.hasText(text)) {
                return text;
            }
        }
        String customerBatchNo = inbound == null ? "" : normalizeCustomerBatchNo(inbound.getCustomerBatchNo());
        if (StringUtils.hasText(customerBatchNo)) {
            return customerBatchNo;
        }
        String batchNo = inbound == null ? "" : String.valueOf(inbound.getBatchNo() == null ? "" : inbound.getBatchNo()).trim();
        if (StringUtils.hasText(batchNo)) {
            return batchNo;
        }
        String requestNo = inbound == null ? "" : String.valueOf(inbound.getRequestNo() == null ? "" : inbound.getRequestNo()).trim();
        return StringUtils.hasText(requestNo) ? requestNo : "PURCHASE-INBOUND";
    }

    private int resolvePurchaseLabelQuantity(Map<String, Object> payload, TapeInboundRequest inbound) {
        int fallback = (inbound != null && inbound.getRolls() != null && inbound.getRolls() > 0) ? inbound.getRolls() : 1;
        if (payload == null || payload.get("quantity") == null) {
            return fallback;
        }
        Integer parsed = parsePositiveInt(payload.get("quantity"));
        return parsed != null ? parsed : fallback;
    }

    private int resolvePurchaseLabelCopies(Map<String, Object> payload) {
        if (payload == null || payload.get("copies") == null) {
            return 1;
        }
        Integer parsed = parsePositiveInt(payload.get("copies"));
        return parsed != null ? parsed : 1;
    }

    private Integer parsePositiveInt(Object value) {
        if (value == null) {
            return null;
        }
        try {
            int v;
            if (value instanceof Number) {
                v = ((Number) value).intValue();
            } else {
                v = Integer.parseInt(String.valueOf(value).trim());
            }
            return v > 0 ? v : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private LocalDate parseLocalDateSafe(String text) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        String raw = text.trim();
        try {
            return LocalDate.parse(raw, DateTimeFormatter.ISO_LOCAL_DATE);
        } catch (Exception ignored) {
        }
        try {
            return LocalDate.parse(raw, DateTimeFormatter.BASIC_ISO_DATE);
        } catch (Exception ignored) {
        }
        return null;
    }

    @SuppressWarnings("unused")
    private long nextPurchaseLabelDailySeq(String ymd) {
        String key = PURCHASE_LABEL_QR_SEQ_PREFIX + ymd;
        Long next = redisTemplate.opsForValue().increment(key);
        if (next == null || next <= 0) {
            throw new RuntimeException("生成二维码流水失败");
        }
        Long ttl = redisTemplate.getExpire(key, TimeUnit.SECONDS);
        if (ttl == null || ttl < 0) {
            redisTemplate.expire(key, 3, TimeUnit.DAYS);
        }
        return next;
    }

    private String normalizeInboundQtyUnit(String raw) {
        if (!StringUtils.hasText(raw)) {
            return "";
        }
        String text = raw.trim();
        String upper = text.toUpperCase();
        if ("ROLL".equals(upper) || "RL".equals(upper) || "卷".equals(text)) {
            return "卷";
        }
        if ("BOX".equals(upper) || "CTN".equals(upper) || "箱".equals(text) || text.contains("纸箱")) {
            return "箱";
        }
        if ("PCS".equals(upper) || "PC".equals(upper) || "EA".equals(upper) || "个".equals(text)) {
            return "个";
        }
        if ("KG".equals(upper) || "KGS".equals(upper) || "公斤".equals(text) || "千克".equals(text)) {
            return "kg";
        }
        if ("M2".equals(upper) || "M²".equals(upper) || "㎡".equals(text) || "平方米".equals(text)) {
            return "㎡";
        }
        if ("数量".equals(text)) {
            return "数量";
        }
        return text;
    }

    private void normalizeInboundDisplayFields(TapeInboundRequest request) {
        if (request == null) {
            return;
        }

        String normalizedQtyUnit = normalizeInboundQtyUnit(request.getQtyUnit());
        if (!StringUtils.hasText(normalizedQtyUnit)) {
            normalizedQtyUnit = normalizeInboundQtyUnit(extractInboundTokenFromRemark(request.getRemark(), "qtyUnit"));
        }
        if (isPurchaseReceiptInbound(request)) {
            // 采购收货按采购实际计件单位回填（如：管类“支”）。
            String purchaseUnit = resolvePurchaseInboundQtyUnit(request);
            if (StringUtils.hasText(purchaseUnit)) {
                normalizedQtyUnit = purchaseUnit;
            }
        }
        if (!StringUtils.hasText(normalizedQtyUnit)) {
            if (isSalesReturnInbound(request) || isSlittingFinishedInbound(request)) {
                normalizedQtyUnit = "卷";
            } else if (isPurchaseReceiptInbound(request)) {
                normalizedQtyUnit = "卷";
            }
        }
        if (StringUtils.hasText(normalizedQtyUnit)) {
            request.setQtyUnit(normalizedQtyUnit);
        }

        String customerBatchNo = resolveInboundCustomerBatchNo(request);
        if (StringUtils.hasText(customerBatchNo)) {
            request.setCustomerBatchNo(customerBatchNo);
        }

        String materialCode = request.getMaterialCode() == null ? "" : request.getMaterialCode().trim();
        String currentProductName = request.getProductName() == null ? "" : request.getProductName().trim();
        boolean genericAutoName = StringUtils.hasText(currentProductName) && currentProductName.toUpperCase().contains("自动入库");
        boolean resolvedBySpec = false;
        if (StringUtils.hasText(materialCode) && (!StringUtils.hasText(currentProductName) || genericAutoName)) {
            try {
                TapeSpec spec = tapeSpecMapper.selectByMaterialCode(materialCode);
                if (spec != null && StringUtils.hasText(spec.getProductName())) {
                    request.setProductName(spec.getProductName().trim());
                    resolvedBySpec = true;
                }
            } catch (Exception ignored) {
            }
            if (!resolvedBySpec) {
                request.setProductName(materialCode);
            }
        }

        String normalizedSpecDesc = normalizeSpecDesc(request.getSpecDesc());
        if (StringUtils.hasText(normalizedSpecDesc)) {
            request.setSpecDesc(normalizedSpecDesc);
            fillDimensionsFromSpecDescIfMissing(request, normalizedSpecDesc);
        }

        fillDimensionsFromPurchaseSourceIfMissing(request);
        // 采购收货规格必须来自采购链路，禁止再用历史库存兜底，避免“串规格”。
        if (!isPurchaseReceiptInbound(request)) {
            fillDimensionsFromLatestStockIfMissing(request);
        }

        String finalSpecDesc = normalizeSpecDesc(request.getSpecDesc());
        if (request.getThickness() != null && request.getWidth() != null && request.getLength() != null
                && request.getThickness() > 0 && request.getWidth() > 0 && request.getLength() > 0) {
            // 采购来料：保留采购端原始规格文本（如 6mm*77mm*1080m）。
            // 其他来源：沿用系统模板规格。
            if (isPurchaseReceiptInbound(request)) {
                if (!StringUtils.hasText(finalSpecDesc)) {
                    request.setSpecDesc(request.getThickness() + "μm*" + request.getWidth() + "mm*" + request.getLength() + "m");
                } else {
                    request.setSpecDesc(finalSpecDesc);
                }
            } else {
                request.setSpecDesc(request.getThickness() + "μm*" + request.getWidth() + "mm*" + request.getLength() + "m");
            }
        }
    }

    private void fillDimensionsFromPurchaseSourceIfMissing(TapeInboundRequest request) {
        if (request == null) {
            return;
        }
        String remark = StringUtils.hasText(request.getRemark()) ? request.getRemark() : "";
        if (!remark.contains(PURCHASE_RECEIPT_TAG)) {
            return;
        }

        try {
            PurchaseReceiptItem receiptItem = null;
            String receiptItemSpec = "";
            Long itemId = parseLong(extractInboundTokenFromRemark(remark, "itemId"));
            if (itemId != null && itemId > 0) {
                receiptItem = purchaseReceiptItemMapper.selectById(itemId);
                if (receiptItem != null) {
                    // 非卷单位场景不直接用kg/㎡数量，优先按规格段数/PO卷数回填卷数。
                    request.setRolls(resolvePurchaseInboundRolls(receiptItem, null));

                    String spec = toSingleSpec(normalizeSpecDesc(receiptItem.getSpecification()));
                    if (StringUtils.hasText(spec)) {
                        receiptItemSpec = spec;
                        // 采购收货优先以到货单规格原文展示（用于纠正历史误写的单位）。
                        request.setSpecDesc(spec);
                        fillDimensionsFromSpecDescIfMissing(request, spec);
                        // 命中收货明细后仍继续看PO，用于纠正“收货行被写成多规格拼接”的情况。
                    }
                }
            }

            String purchaseOrderNo = extractInboundTokenFromRemark(remark, "purchaseOrderNo");
            String materialCode = StringUtils.hasText(request.getMaterialCode()) ? request.getMaterialCode().trim() : null;
            if (!StringUtils.hasText(materialCode)) {
                return;
            }

            IPage<PurchaseOrderItem> poPage = purchaseOrderItemMapper.selectItems(new Page<>(1, 10), purchaseOrderNo, materialCode);
            List<PurchaseOrderItem> poItems = poPage == null ? Collections.emptyList() : poPage.getRecords();
            if (poItems == null || poItems.isEmpty()) {
                return;
            }

            PurchaseOrderItem poItem = pickBestPurchaseOrderItem(poItems, receiptItemSpec, request);
            if (poItem == null) {
                return;
            }

            Integer t = toPositiveInt(poItem.getThickness());
            Integer w = toPositiveInt(poItem.getWidth());
            Integer l = toPositiveInt(poItem.getLength());
            request.setRolls(resolvePurchaseInboundRolls(receiptItem, poItem));
            if ((request.getThickness() == null || request.getThickness() <= 0) && t != null) {
                request.setThickness(t);
            }
            if ((request.getWidth() == null || request.getWidth() <= 0) && w != null) {
                request.setWidth(w);
            }
            if ((request.getLength() == null || request.getLength() <= 0) && l != null) {
                request.setLength(l);
            }

            String poSpec = toSingleSpec(normalizeSpecDesc(poItem.getFilmSpecRaw()));
            if (!StringUtils.hasText(poSpec)) {
                poSpec = toSingleSpec(normalizeSpecDesc(poItem.getRawSpec()));
            }
            if (StringUtils.hasText(poSpec)) {
                request.setSpecDesc(poSpec);
                // 采购收货必须以采购单规格为准：PO命中时强制覆盖，避免先前错误来源(如收货单规格)锁死尺寸
                fillDimensionsFromSpecDesc(request, poSpec, true);
            }
        } catch (Exception ignored) {
        }
    }

    private PurchaseOrderItem pickBestPurchaseOrderItem(List<PurchaseOrderItem> poItems, String receiptItemSpec, TapeInboundRequest request) {
        if (poItems == null || poItems.isEmpty()) {
            return null;
        }

        String targetSpec = StringUtils.hasText(receiptItemSpec)
                ? toSingleSpec(normalizeSpecDesc(receiptItemSpec))
                : toSingleSpec(normalizeSpecDesc(request == null ? null : request.getSpecDesc()));
        String targetSpecKey = canonicalSpec(targetSpec);

        PurchaseOrderItem best = poItems.get(0);
        int bestScore = Integer.MIN_VALUE;

        for (PurchaseOrderItem item : poItems) {
            int score = 0;
            String poSpec = toSingleSpec(normalizeSpecDesc(item == null ? null : item.getFilmSpecRaw()));
            if (!StringUtils.hasText(poSpec)) {
                poSpec = toSingleSpec(normalizeSpecDesc(item == null ? null : item.getRawSpec()));
            }
            String poSpecKey = canonicalSpec(poSpec);

            if (StringUtils.hasText(targetSpecKey) && StringUtils.hasText(poSpecKey)) {
                if (targetSpecKey.equals(poSpecKey)) {
                    score += 100;
                } else if (targetSpecKey.contains(poSpecKey) || poSpecKey.contains(targetSpecKey)) {
                    score += 60;
                }
            }

            Integer t = toPositiveInt(item == null ? null : item.getThickness());
            Integer w = toPositiveInt(item == null ? null : item.getWidth());
            Integer l = toPositiveInt(item == null ? null : item.getLength());
            if (t != null && w != null && l != null) {
                score += 10;
                if (request != null
                        && request.getThickness() != null && request.getThickness() > 0
                        && request.getWidth() != null && request.getWidth() > 0
                        && request.getLength() != null && request.getLength() > 0
                        && t.equals(request.getThickness())
                        && w.equals(request.getWidth())
                        && l.equals(request.getLength())) {
                    score += 80;
                }
            }

            if (StringUtils.hasText(poSpecKey)) {
                score += 5;
            }

            if (score > bestScore) {
                bestScore = score;
                best = item;
            }
        }

        return best;
    }

    private String canonicalSpec(String spec) {
        if (!StringUtils.hasText(spec)) {
            return "";
        }
        return spec.replace('\u00A0', ' ')
                .replace('\u3000', ' ')
                .replaceAll("\\s+", "")
                .toUpperCase();
    }

    private void fillDimensionsFromLatestStockIfMissing(TapeInboundRequest request) {
        if (request == null || !isInboundSpecMissing(request)) {
            return;
        }
        if (!StringUtils.hasText(request.getMaterialCode())) {
            return;
        }

        try {
            List<TapeStock> history = stockMapper.selectList(
                    new LambdaQueryWrapper<TapeStock>()
                            .eq(TapeStock::getMaterialCode, request.getMaterialCode().trim())
                            .orderByDesc(TapeStock::getId)
                            .last("LIMIT 20")
            );
            if (history == null || history.isEmpty()) {
                return;
            }
            for (TapeStock stock : history) {
                if ((request.getThickness() == null || request.getThickness() <= 0)
                        && stock.getThickness() != null && stock.getThickness() > 0) {
                    request.setThickness(stock.getThickness());
                }
                if ((request.getWidth() == null || request.getWidth() <= 0)
                        && stock.getWidth() != null && stock.getWidth() > 0) {
                    request.setWidth(stock.getWidth());
                }
                if ((request.getLength() == null || request.getLength() <= 0)
                        && stock.getLength() != null && stock.getLength() > 0) {
                    request.setLength(stock.getLength());
                }
                if (!StringUtils.hasText(normalizeSpecDesc(request.getSpecDesc()))) {
                    String stockSpec = normalizeSpecDesc(stock.getSpecDesc());
                    if (StringUtils.hasText(stockSpec)) {
                        request.setSpecDesc(stockSpec);
                        fillDimensionsFromSpecDescIfMissing(request, stockSpec);
                    }
                }
                if (!isInboundSpecMissing(request)) {
                    return;
                }
            }
        } catch (Exception ignored) {
        }
    }

    private boolean isInboundSpecMissing(TapeInboundRequest request) {
        if (request == null) {
            return false;
        }
        boolean missingDims = request.getThickness() == null || request.getThickness() <= 0
                || request.getWidth() == null || request.getWidth() <= 0
                || request.getLength() == null || request.getLength() <= 0;
        boolean missingSpec = !StringUtils.hasText(normalizeSpecDesc(request.getSpecDesc()));
        return missingDims || missingSpec;
    }

    private String toSingleSpec(String specDesc) {
        if (!StringUtils.hasText(specDesc)) {
            return "";
        }
        String[] parts = specDesc.split("[，,;；\\n\\r]+");
        for (String part : parts) {
            String normalized = normalizeSpecDesc(part);
            if (StringUtils.hasText(normalized)) {
                return normalized;
            }
        }
        return normalizeSpecDesc(specDesc);
    }

    private int countSpecSegments(String specDesc) {
        if (!StringUtils.hasText(specDesc)) {
            return 0;
        }
        String[] parts = specDesc.split("[，,;；\\n\\r]+");
        int count = 0;
        for (String part : parts) {
            if (StringUtils.hasText(normalizeSpecDesc(part))) {
                count++;
            }
        }
        return count;
    }

    private Integer resolvePurchaseInboundRolls(PurchaseReceiptItem item, PurchaseOrderItem poItem) {
        Integer current = item == null ? null : item.getReceivedQty();
        if (item != null && current != null && current > 0) {
            String stockUom = item.getStockUomCode();
            String purchaseUom = item.getPurchaseUomCode();
            String priceUom = item.getPriceUomCode();
            String unit = item.getUnit();
            boolean isRollUnit = "卷".equals(normalizeInboundQtyUnit(stockUom))
                    || "卷".equals(normalizeInboundQtyUnit(purchaseUom))
                    || "卷".equals(normalizeInboundQtyUnit(priceUom))
                    || "卷".equals(normalizeInboundQtyUnit(unit));
            if (isRollUnit) {
                return current;
            }
        }

        if (poItem != null && poItem.getRolls() != null && poItem.getRolls() > 0) {
            return poItem.getRolls();
        }

        int fromSpec = countSpecSegments(item == null ? null : item.getSpecification());
        if (fromSpec <= 0) {
            fromSpec = countSpecSegments(poItem == null ? null : poItem.getFilmSpecRaw());
        }
        if (fromSpec <= 0) {
            fromSpec = countSpecSegments(poItem == null ? null : poItem.getRawSpec());
        }
        if (fromSpec > 0) {
            return fromSpec;
        }
        return 1;
    }

    private String normalizeSpecDesc(String specDesc) {
        if (!StringUtils.hasText(specDesc)) {
            return "";
        }
        String spec = specDesc.trim();
        if ("-".equals(spec) || "--".equals(spec) || "—".equals(spec) || "/".equals(spec)) {
            return "";
        }
        return spec;
    }

    private String resolvePurchaseInboundQtyUnit(TapeInboundRequest request) {
        if (request == null) {
            return "";
        }
        String remark = StringUtils.hasText(request.getRemark()) ? request.getRemark() : "";
        if (!remark.contains(PURCHASE_RECEIPT_TAG)) {
            return "";
        }
        try {
            Long itemId = parseLong(extractInboundTokenFromRemark(remark, "itemId"));
            PurchaseReceiptItem item = (itemId == null || itemId <= 0) ? null : purchaseReceiptItemMapper.selectById(itemId);
            if (item == null) {
                return "";
            }

            String materialCode = request.getMaterialCode();
            String materialName = StringUtils.hasText(item.getMaterialName()) ? item.getMaterialName() : request.getProductName();
            String specDesc = StringUtils.hasText(item.getSpecification()) ? item.getSpecification() : request.getSpecDesc();
            // 纸箱类来料统一展示“个”，避免被 DRUM/桶 等上游单位覆盖。
            if (isPaperBoxLikeMaterial(materialCode, materialName, specDesc)) {
                return "个";
            }

            String[] candidates = new String[] {
                    item.getUnit(),
                    item.getStockUomCode(),
                    item.getPurchaseUomCode(),
                    item.getPriceUomCode()
            };
            for (String c : candidates) {
                String unit = normalizeInboundQtyUnit(c);
                if (!StringUtils.hasText(unit)) {
                    continue;
                }
                if ("kg".equals(unit) || "㎡".equals(unit)) {
                    continue;
                }
                if ("个".equals(unit) && isTubeLikeMaterial(
                        materialCode,
                        materialName,
                        specDesc)) {
                    return "支";
                }
                return unit;
            }

            if (isTubeLikeMaterial(
                    materialCode,
                    materialName,
                    specDesc)) {
                return "支";
            }
            return "卷";
        } catch (Exception ignored) {
            return "";
        }
    }

    private boolean isPaperBoxLikeMaterial(String materialCode, String materialName, String specDesc) {
        String code = materialCode == null ? "" : materialCode.trim().toUpperCase();
        String name = materialName == null ? "" : materialName.trim();
        String spec = specDesc == null ? "" : specDesc.trim();
        return code.startsWith("ZX") || name.contains("纸箱") || name.contains("箱") || spec.contains("纸箱");
    }

    private boolean isTubeLikeMaterial(String materialCode, String materialName, String specDesc) {
        if (isPegTubeMaterial(materialCode, materialName, specDesc)) {
            return true;
        }
        String name = materialName == null ? "" : materialName.trim();
        String spec = specDesc == null ? "" : specDesc.trim();
        return name.contains("管") || spec.contains("管");
    }

    private boolean isPegTubeMaterial(String materialCode, String materialName, String specDesc) {
        String code = materialCode == null ? "" : materialCode.trim().toUpperCase(Locale.ROOT);
        String name = materialName == null ? "" : materialName.trim().toUpperCase(Locale.ROOT);
        String spec = specDesc == null ? "" : specDesc.trim().toUpperCase(Locale.ROOT);
        return code.startsWith("PEG") || name.contains("PEG") || spec.contains("PEG") || name.contains("PE管") || spec.contains("PE管");
    }

    private boolean isPegChemicalMaterial(String materialCode, String materialName, String specDesc) {
        String code = materialCode == null ? "" : materialCode.trim().toUpperCase(Locale.ROOT);
        String name = materialName == null ? "" : materialName.trim().toUpperCase(Locale.ROOT);
        String spec = specDesc == null ? "" : specDesc.trim().toUpperCase(Locale.ROOT);
        return code.startsWith("PEG") || name.contains("PEG") || spec.contains("PEG");
    }

    private String extractInboundTokenFromRemark(String remark, String key) {
        if (!StringUtils.hasText(remark) || !StringUtils.hasText(key)) {
            return "";
        }
        String escapedKey = Pattern.quote(key);
        Matcher m = Pattern.compile("(?:^|[|,;\\s])" + escapedKey + "=([^|,;]+)", Pattern.CASE_INSENSITIVE).matcher(remark);
        if (!m.find()) {
            return "";
        }
        return m.group(1) == null ? "" : m.group(1).trim();
    }

    private String normalizeCustomerBatchNo(String raw) {
        if (!StringUtils.hasText(raw)) {
            return "";
        }
        return raw.replace('\u00A0', ' ').replace('\u3000', ' ').trim();
    }

    private String resolveInboundCustomerBatchNo(TapeInboundRequest request) {
        if (request == null) {
            return "";
        }
        String fromField = normalizeCustomerBatchNo(request.getCustomerBatchNo());
        if (StringUtils.hasText(fromField)) {
            return fromField;
        }
        String fromToken = normalizeCustomerBatchNo(extractInboundTokenFromRemark(request.getRemark(), "customerBatchNo"));
        if (!StringUtils.hasText(fromToken)) {
            fromToken = normalizeCustomerBatchNo(extractInboundTokenFromRemark(request.getRemark(), "incomingBatchNo"));
        }
        if (StringUtils.hasText(fromToken)) {
            return fromToken;
        }
        return normalizeCustomerBatchNo(request.getBatchNo());
    }

    private String appendInboundRemarkToken(String remark, String key, String value) {
        String v = normalizeCustomerBatchNo(value);
        if (!StringUtils.hasText(v)) {
            return remark;
        }
        String r = normalizeTapeStockRemark(remark);
        Pattern existsPattern = Pattern.compile("(?:^|[|,;\\s])" + Pattern.quote(key) + "=");
        if (existsPattern.matcher(r).find()) {
            return r;
        }
        if (!StringUtils.hasText(r)) {
            return normalizeTapeStockRemark(key + "=" + v);
        }
        return normalizeTapeStockRemark(r + "|" + key + "=" + v);
    }

    private String mergeRemarkWithCustomerBatch(String remark, String customerBatchNo) {
        return normalizeTapeStockRemark(appendInboundRemarkToken(remark, "customerBatchNo", customerBatchNo));
    }

    /**
     * tape_stock.remark 字段较短（部分环境为 VARCHAR(200)），
     * 入库场景 remark 会叠加多个 token，需统一清洗并截断以避免 Data too long。
     */
    private String normalizeTapeStockRemark(String remark) {
        if (!StringUtils.hasText(remark)) {
            return "";
        }
        String normalized = remark
            .replaceAll("[\\r\\n\\t]+", " ")
            .replaceAll("\\s{2,}", " ")
            .trim();
        if (normalized.length() > TAPE_STOCK_REMARK_MAX_LEN) {
            return normalized.substring(0, TAPE_STOCK_REMARK_MAX_LEN);
        }
        return normalized;
    }

    private Long parseLong(String text) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        try {
            return Long.parseLong(text.trim());
        } catch (Exception ignored) {
            return null;
        }
    }

    private void applyInboundPlanTimeForDisplay(TapeInboundRequest request, Map<Long, LocalDate> expectedDateCache) {
        if (request == null || !isPurchaseReceiptInbound(request)) {
            return;
        }
        Long receiptId = parseLong(extractInboundTokenFromRemark(request.getRemark(), "receiptId"));
        if (receiptId == null || receiptId <= 0) {
            return;
        }
        LocalDate expectedDate = expectedDateCache == null ? null : expectedDateCache.get(receiptId);
        if (expectedDate == null) {
            try {
                PurchaseReceipt receipt = purchaseReceiptMapper.selectById(receiptId);
                expectedDate = receipt == null ? null : receipt.getExpectedDate();
                if (expectedDateCache != null) {
                    expectedDateCache.put(receiptId, expectedDate);
                }
            } catch (Exception ignored) {
            }
        }
        if (expectedDate != null) {
            request.setApplyTime(expectedDate.atTime(8, 0));
        }
    }

    private Integer toPositiveInt(BigDecimal value) {
        if (value == null || value.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        try {
            int v = value.setScale(0, BigDecimal.ROUND_HALF_UP).intValue();
            return v > 0 ? v : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private void fillDimensionsFromSpecDescIfMissing(TapeInboundRequest request, String specDesc) {
        fillDimensionsFromSpecDesc(request, specDesc, false);
    }

    private void fillDimensionsFromSpecDesc(TapeInboundRequest request, String specDesc, boolean forceOverride) {
        if (request == null || !StringUtils.hasText(specDesc)) {
            return;
        }
        List<Integer> nums = new ArrayList<>();
        Matcher matcher = Pattern.compile("(\\d+(?:\\.\\d+)?)").matcher(specDesc);
        while (matcher.find() && nums.size() < 3) {
            try {
                int v = new BigDecimal(matcher.group(1)).setScale(0, BigDecimal.ROUND_HALF_UP).intValue();
                if (v > 0) {
                    nums.add(v);
                }
            } catch (Exception ignored) {
            }
        }
        if (nums.size() < 3) {
            return;
        }
        if (forceOverride || request.getThickness() == null || request.getThickness() <= 0) {
            request.setThickness(nums.get(0));
        }
        if (forceOverride || request.getWidth() == null || request.getWidth() <= 0) {
            request.setWidth(nums.get(1));
        }
        if (forceOverride || request.getLength() == null || request.getLength() <= 0) {
            request.setLength(nums.get(2));
        }
    }

    /**
     * @return 是否仍可能存在待补锁（true=仍有待补锁，后续新卷可继续尝试；false=已无待补锁，可短路）
     */
    private boolean autoFulfillPendingLocks(TapeStock stock, String sourceDocNo, String operator) {
        if (stock == null || !StringUtils.hasText(stock.getMaterialCode())) {
            return false;
        }
        BigDecimal remain = stock.getTotalSqm() == null ? BigDecimal.ZERO : stock.getTotalSqm();
        if (remain.compareTo(BigDecimal.ZERO) <= 0) {
            return false;
        }

        boolean hasPendingSupply = true;

        while (remain.compareTo(BigDecimal.ZERO) > 0) {
            LambdaQueryWrapper<ScheduleMaterialLock> pendingQ = new LambdaQueryWrapper<>();
            pendingQ.eq(ScheduleMaterialLock::getLockStatus, ScheduleMaterialLock.LockStatus.PENDING_SUPPLY)
                    .eq(ScheduleMaterialLock::getMaterialCode, stock.getMaterialCode())
                    .orderByAsc(ScheduleMaterialLock::getLockedTime)
                    .orderByAsc(ScheduleMaterialLock::getId)
                    .last("LIMIT 1");
            ScheduleMaterialLock pending = scheduleMaterialLockMapper.selectOne(pendingQ);
            if (pending == null) {
                hasPendingSupply = false;
                break;
            }

            BigDecimal required = pending.getRequiredArea() == null ? BigDecimal.ZERO : pending.getRequiredArea();
            BigDecimal already = pending.getLockedArea() == null ? BigDecimal.ZERO : pending.getLockedArea();
            BigDecimal need = required.subtract(already);
            if (need.compareTo(BigDecimal.ZERO) <= 0) {
                pending.setLockStatus(ScheduleMaterialLock.LockStatus.FULFILLED);
                pending.setReleasedTime(LocalDateTime.now());
                scheduleMaterialLockMapper.updateById(pending);
                continue;
            }

            BigDecimal lockArea = remain.min(need).setScale(2, BigDecimal.ROUND_HALF_UP);
            if (lockArea.compareTo(BigDecimal.ZERO) <= 0) {
                break;
            }

            boolean reserved = false;
            for (int i = 0; i < 3; i++) {
                TapeStock current = stockMapper.selectById(stock.getId());
                if (current == null) {
                    break;
                }
                BigDecimal available = current.getAvailableArea() == null ? BigDecimal.ZERO : current.getAvailableArea();
                if (available.compareTo(lockArea) < 0) {
                    lockArea = available.setScale(2, BigDecimal.ROUND_HALF_UP);
                }
                if (lockArea.compareTo(BigDecimal.ZERO) <= 0) {
                    break;
                }
                Integer version = current.getVersion() == null ? 0 : current.getVersion();
                int ok = stockMapper.updateReservedAreaWithVersion(current.getId(), lockArea, version);
                if (ok > 0) {
                    reserved = true;
                    break;
                }
            }
            if (!reserved) {
                break;
            }

            ScheduleMaterialLock lock = new ScheduleMaterialLock();
            lock.setScheduleId(pending.getScheduleId());
            lock.setOrderId(pending.getOrderId());
            lock.setOrderNo(pending.getOrderNo());
            lock.setMaterialCode(pending.getMaterialCode());
            lock.setFilmStockId(stock.getId());
            lock.setFilmStockDetailId(stock.getId());
            lock.setRollCode(StringUtils.hasText(stock.getQrCode()) ? stock.getQrCode() : stock.getBatchNo());
            lock.setLockedArea(lockArea);
            lock.setRequiredArea(lockArea);
            lock.setLockStatus(ScheduleMaterialLock.LockStatus.LOCKED);
            lock.setLockedTime(LocalDateTime.now());
            lock.setLockedByUserId(1L);
            lock.setVersion(1);
                lock.setRemark("source=inbound-auto-lock;pendingId=" + pending.getId()
                    + ";sourceDoc=" + (sourceDocNo == null ? "" : sourceDocNo)
                    + ";op=" + (operator == null ? "system" : operator)
                    + ";ts=" + LocalDateTime.now());
            scheduleMaterialLockMapper.insert(lock);

            pending.setLockedArea(already.add(lockArea).setScale(2, BigDecimal.ROUND_HALF_UP));
            if (pending.getLockedArea().compareTo(required) >= 0) {
                pending.setLockStatus(ScheduleMaterialLock.LockStatus.FULFILLED);
                pending.setReleasedTime(LocalDateTime.now());
            }
            scheduleMaterialLockMapper.updateById(pending);

            remain = remain.subtract(lockArea);
        }

        return hasPendingSupply;
    }
    
    @Override
    @Transactional
    public void cancelInbound(Long id) {
        TapeInboundRequest request = inboundMapper.selectById(id);
        if (request == null) {
            throw new RuntimeException("入库申请不存在");
        }
        if (request.getStatus() != TapeInboundRequest.STATUS_PENDING) {
            throw new RuntimeException("只能取消待审批的申请");
        }
        request.setStatus(TapeInboundRequest.STATUS_CANCELLED);
        inboundMapper.updateById(request);
    }
    
    @Override
    public int countPendingInbound() {
        return inboundMapper.countPending();
    }
    
    // ============= 出库申请 =============
    
    @Override
    public IPage<TapeOutboundRequest> getOutboundPage(int page, int size, Integer status, String materialCode, String orderNo) {
        LambdaQueryWrapper<TapeOutboundRequest> wrapper = new LambdaQueryWrapper<>();
        if (status != null) {
            wrapper.eq(TapeOutboundRequest::getStatus, status);
        }
        if (StringUtils.hasText(materialCode)) {
            wrapper.like(TapeOutboundRequest::getMaterialCode, materialCode);
        }
        if (StringUtils.hasText(orderNo)) {
            wrapper.like(TapeOutboundRequest::getOrderNo, orderNo.trim());
        }
        wrapper.orderByDesc(TapeOutboundRequest::getCreateTime);
        Page<TapeOutboundRequest> pageParam = new Page<>(page, size);
        pageParam.setOptimizeCountSql(false);
        IPage<TapeOutboundRequest> result = outboundMapper.selectPage(pageParam, wrapper);
        if (result != null && result.getRecords() != null) {
            Set<Long> stockIds = new HashSet<>();
            for (TapeOutboundRequest record : result.getRecords()) {
                if (record != null && record.getStockId() != null) {
                    stockIds.add(record.getStockId());
                }
            }
            Map<Long, TapeStock> stockMap = new HashMap<>();
            if (!stockIds.isEmpty()) {
                List<TapeStock> stocks = stockMapper.selectBatchIds(stockIds);
                if (stocks != null) {
                    for (TapeStock stock : stocks) {
                        if (stock != null && stock.getId() != null) {
                            stockMap.put(stock.getId(), stock);
                        }
                    }
                }
            }
            for (TapeOutboundRequest record : result.getRecords()) {
                if (record != null && record.getStockId() != null) {
                    TapeStock stock = stockMap.get(record.getStockId());
                    if (stock != null) {
                        record.setSequenceNo(stock.getSequenceNo());
                    }
                }
                normalizeOutboundDisplayFields(record);
            }
        }
        return result;
    }

    @Override
    public Map<String, Object> getUnifiedOutboundPage(int page, int size, Integer status, String materialCode, String bizType, String orderNo) {
        int safePage = Math.max(page, 1);
        int safeSize = size <= 0 ? 20 : Math.min(size, 200);
        String type = bizType == null ? "" : bizType.trim();
        String material = materialCode == null ? "" : materialCode.trim();
        String order = orderNo == null ? "" : orderNo.trim();

        List<String> segments = new ArrayList<>();
        List<Object> args = new ArrayList<>();

        boolean includeTape = !StringUtils.hasText(type) || "TAPE_PRODUCT".equalsIgnoreCase(type);
        boolean includeRaw = !StringUtils.hasText(type) || "RAW_MATERIAL".equalsIgnoreCase(type);
        if (StringUtils.hasText(order)) {
            // 订单号检索仅作用于胶带产品出库
            includeRaw = false;
        }

        if (includeTape) {
            StringBuilder tapeSql = new StringBuilder();
            tapeSql.append("SELECT ")
                    .append("tor.id AS id, ")
                    .append("tor.request_no AS requestNo, ")
                    .append("tor.material_code AS materialCode, ")
                    .append("IFNULL(tor.product_name, '') AS productName, ")
                    .append("IFNULL(tor.batch_no, '-') AS batchNo, ")
                    .append("IFNULL(tor.spec_desc, '-') AS specDesc, ")
                    .append("tor.rolls AS rolls, ")
                    .append("tor.available_rolls AS availableRolls, ")
                    .append("IFNULL(tor.applicant, '') AS applicant, ")
                    .append("IFNULL(tor.apply_dept, '') AS applyDept, ")
                    .append("DATE_FORMAT(tor.apply_time, '%Y-%m-%d %H:%i:%s') AS applyTime, ")
                    .append("tor.status AS status, ")
                    .append("IFNULL(tor.remark, '') AS remark, ")
                    .append("IFNULL(tor.order_no, '') AS orderNo, ")
                    .append("tor.order_item_id AS orderItemId, ")
                    .append("tor.delivery_notice_id AS deliveryNoticeId, ")
                    .append("IFNULL(tor.delivery_notice_no, '') AS deliveryNoticeNo, ")
                    .append("IFNULL(tor.biz_type, 'MANUAL') AS outboundBizType, ")
                    .append("'TAPE_PRODUCT' AS bizType, ")
                    .append("'胶带产品' AS bizTypeLabel, ")
                    .append("NULL AS sourceKind, ")
                    .append("NULL AS outArea, ")
                    .append("NULL AS outWeight, ")
                    .append("CONCAT(IFNULL(tor.rolls, 0), '卷') AS qtyText, ")
                    .append("ts.sequence_no AS sequenceNo, ")
                    .append("UNIX_TIMESTAMP(COALESCE(tor.apply_time, tor.create_time)) AS sortTimeVal ")
                    .append("FROM tape_outbound_request tor ")
                    .append("LEFT JOIN tape_stock ts ON ts.id = tor.stock_id ")
                    .append("WHERE 1=1 ");
            if (status != null) {
                tapeSql.append(" AND tor.status = ? ");
                args.add(status);
            }
            if (StringUtils.hasText(material)) {
                tapeSql.append(" AND tor.material_code LIKE ? ");
                args.add("%" + material + "%");
            }
            if (StringUtils.hasText(order)) {
                tapeSql.append(" AND tor.order_no LIKE ? ");
                args.add("%" + order + "%");
            }
            segments.add(tapeSql.toString());
        }

        if (includeRaw && (status == null || status == 1)) {
            StringBuilder filmSql = new StringBuilder();
            filmSql.append("SELECT ")
                    .append("fso.id AS id, ")
                    .append("fso.out_no AS requestNo, ")
                    .append("fso.material_code AS materialCode, ")
                    .append("IFNULL(NULLIF(fs.material_name, ''), fso.material_code) AS productName, ")
                    .append("IFNULL(fso.batch_no, '-') AS batchNo, ")
                    .append("IFNULL(NULLIF(fs.spec_desc, ''), CONCAT(IFNULL(fs.thickness, '-'), 'μm*', IFNULL(fs.width, '-'), 'mm')) AS specDesc, ")
                    .append("NULL AS rolls, ")
                    .append("CONCAT('>=', ROUND(IFNULL(fso.out_area, 0), 2), '㎡') AS availableRolls, ")
                    .append("IFNULL(fso.operator, '') AS applicant, ")
                    .append("'原材料仓' AS applyDept, ")
                    .append("DATE_FORMAT(COALESCE(fso.out_date, fso.create_time), '%Y-%m-%d %H:%i:%s') AS applyTime, ")
                    .append("1 AS status, ")
                    .append("IFNULL(fso.remark, '') AS remark, ")
                    .append("'' AS orderNo, ")
                    .append("NULL AS orderItemId, ")
                    .append("NULL AS deliveryNoticeId, ")
                    .append("'' AS deliveryNoticeNo, ")
                    .append("'RAW_MATERIAL' AS outboundBizType, ")
                    .append("'RAW_MATERIAL' AS bizType, ")
                    .append("'原材料' AS bizTypeLabel, ")
                    .append("'FILM' AS sourceKind, ")
                    .append("fso.out_area AS outArea, ")
                    .append("NULL AS outWeight, ")
                    .append("CONCAT(ROUND(IFNULL(fso.out_area, 0), 2), '㎡') AS qtyText, ")
                    .append("NULL AS sequenceNo, ")
                    .append("UNIX_TIMESTAMP(COALESCE(fso.out_date, fso.create_time)) AS sortTimeVal ")
                    .append("FROM film_stock_out fso ")
                    .append("LEFT JOIN film_stock fs ON fs.id = fso.stock_id ")
                    .append("WHERE 1=1 ");
            if (StringUtils.hasText(material)) {
                filmSql.append(" AND fso.material_code LIKE ? ");
                args.add("%" + material + "%");
            }
            segments.add(filmSql.toString());

            StringBuilder chemicalSql = new StringBuilder();
            chemicalSql.append("SELECT ")
                    .append("cso.id AS id, ")
                    .append("cso.out_no AS requestNo, ")
                    .append("cso.material_code AS materialCode, ")
                    .append("IFNULL(NULLIF(cs.material_name, ''), cso.material_code) AS productName, ")
                    .append("IFNULL(cso.batch_no, '-') AS batchNo, ")
                    .append("IFNULL(NULLIF(CONCAT(ROUND(IFNULL(cs.unit_weight, 0), 2), 'kg/', IFNULL(NULLIF(cs.unit, ''), '桶')), ''), '化工原材料') AS specDesc, ")
                    .append("NULL AS rolls, ")
                    .append("CONCAT('>=', ROUND(IFNULL(cso.out_weight, 0), 3), 'kg') AS availableRolls, ")
                    .append("IFNULL(cso.operator, '') AS applicant, ")
                    .append("'原材料仓' AS applyDept, ")
                    .append("DATE_FORMAT(COALESCE(cso.out_date, cso.create_time), '%Y-%m-%d %H:%i:%s') AS applyTime, ")
                    .append("1 AS status, ")
                    .append("IFNULL(cso.remark, '') AS remark, ")
                    .append("'' AS orderNo, ")
                    .append("NULL AS orderItemId, ")
                    .append("NULL AS deliveryNoticeId, ")
                    .append("'' AS deliveryNoticeNo, ")
                    .append("'RAW_MATERIAL' AS outboundBizType, ")
                    .append("'RAW_MATERIAL' AS bizType, ")
                    .append("'原材料' AS bizTypeLabel, ")
                    .append("'CHEMICAL' AS sourceKind, ")
                    .append("NULL AS outArea, ")
                    .append("cso.out_weight AS outWeight, ")
                    .append("CONCAT(ROUND(IFNULL(cso.out_weight, 0), 3), 'kg') AS qtyText, ")
                    .append("NULL AS sequenceNo, ")
                    .append("UNIX_TIMESTAMP(COALESCE(cso.out_date, cso.create_time)) AS sortTimeVal ")
                    .append("FROM chemical_stock_out cso ")
                    .append("LEFT JOIN chemical_stock cs ON cs.id = cso.stock_id ")
                    .append("WHERE 1=1 ");
            if (StringUtils.hasText(material)) {
                chemicalSql.append(" AND cso.material_code LIKE ? ");
                args.add("%" + material + "%");
            }
            segments.add(chemicalSql.toString());
        }

        Map<String, Object> data = new HashMap<>();
        if (segments.isEmpty()) {
            data.put("records", Collections.emptyList());
            data.put("total", 0L);
            data.put("current", safePage);
            data.put("size", safeSize);
            data.put("pages", 0L);
            return data;
        }

        String unionSql = String.join(" UNION ALL ", segments);
        String countSql = "SELECT COUNT(1) FROM (" + unionSql + ") t";
        Long total = jdbcTemplate.queryForObject(countSql, Long.class, args.toArray());
        long totalVal = total == null ? 0L : total;

        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add((safePage - 1L) * safeSize);
        pageArgs.add(safeSize);
        String pageSql = "SELECT * FROM (" + unionSql + ") t ORDER BY t.sortTimeVal DESC, t.id DESC LIMIT ?, ?";
        List<Map<String, Object>> records = jdbcTemplate.queryForList(pageSql, pageArgs.toArray());

        long pages = safeSize <= 0 ? 0L : (long) Math.ceil((double) totalVal / (double) safeSize);
        data.put("records", records == null ? Collections.emptyList() : records);
        data.put("total", totalVal);
        data.put("current", safePage);
        data.put("size", safeSize);
        data.put("pages", pages);
        return data;
    }
    
    @Override
    @Transactional
    public TapeOutboundRequest createOutboundRequest(TapeOutboundRequest request) {
        // 检查库存
        TapeStock stock = stockMapper.selectById(request.getStockId());
        if (stock == null) {
            throw new RuntimeException("库存记录不存在");
        }
        normalizeOutboundOrderFields(request);
        validateOutboundOrderRelation(request, stock);
        if (request.getRolls() == null || request.getRolls() <= 0) {
            request.setRolls(1);
        }
        if (request.getRolls() == null || request.getRolls() <= 0) {
            request.setRolls(1);
        }
        if (stock.getTotalRolls() == null || stock.getTotalRolls() < request.getRolls()) {
            throw new RuntimeException("库存不足，当前可用: " + (stock.getTotalRolls() == null ? 0 : stock.getTotalRolls()) + " 卷");
        }
        
        // 生成单号
        String requestNo = outboundMapper.generateRequestNo();
        if (requestNo == null) {
            requestNo = "OUT" + java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd").format(LocalDateTime.now()) + "0001";
        }
        request.setRequestNo(requestNo);
        request.setMaterialCode(stock.getMaterialCode());
        request.setProductName(resolveProductNameByMaterialCode(stock.getMaterialCode(), stock.getProductName()));
        request.setBatchNo(stock.getBatchNo());
        request.setSpecDesc(stock.getSpecDesc());
        request.setAvailableRolls(stock.getTotalRolls());
        request.setApplyTime(LocalDateTime.now());
        request.setStatus(TapeOutboundRequest.STATUS_PENDING);
        if (!StringUtils.hasText(request.getBizType())) {
            request.setBizType(TapeOutboundRequest.BIZ_TYPE_MANUAL);
        }
        
        outboundMapper.insert(request);
        return request;
    }

    @Override
    @Transactional
    public TapeOutboundRequest updateOutboundRequest(Long id, Integer rolls, String applyDept, String remark) {
        TapeOutboundRequest request = outboundMapper.selectById(id);
        if (request == null) {
            throw new RuntimeException("出库申请不存在");
        }
        if (request.getStatus() == null || request.getStatus() != TapeOutboundRequest.STATUS_PENDING) {
            throw new RuntimeException("仅待审批申请可修改");
        }

        Integer newRolls = rolls;
        if (newRolls == null || newRolls <= 0) {
            newRolls = request.getRolls() == null ? 1 : request.getRolls();
        }
        if (newRolls <= 0) {
            newRolls = 1;
        }

        TapeStock stock = stockMapper.selectById(request.getStockId());
        if (stock == null) {
            throw new RuntimeException("库存记录不存在");
        }
        int available = stock.getTotalRolls() == null ? 0 : stock.getTotalRolls();
        if (available < newRolls) {
            throw new RuntimeException("库存不足，当前可用: " + available + " 卷");
        }

        request.setRolls(newRolls);
        request.setAvailableRolls(available);
        request.setApplyDept(applyDept);
        request.setRemark(remark);
        request.setMaterialCode(stock.getMaterialCode());
        request.setProductName(resolveProductNameByMaterialCode(stock.getMaterialCode(), stock.getProductName()));
        request.setBatchNo(stock.getBatchNo());
        request.setSpecDesc(stock.getSpecDesc());

        outboundMapper.updateById(request);
        return request;
    }
    
    @Override
    @Transactional
    public List<TapeOutboundRequest> createOutboundRequestFIFO(String materialCode, int totalRolls,
                                                                String applicant, String applyDept, String remark,
                                                                String orderNo, Long orderItemId, String bizType) {
        List<TapeStock> stocks = stockMapper.selectByMaterialCodeFIFO(materialCode);
        if (stocks.isEmpty()) {
            throw new RuntimeException("该料号无可用库存");
        }
        
        // 计算总可用
        int totalAvailable = stocks.stream()
            .map(TapeStock::getTotalRolls)
            .filter(Objects::nonNull)
            .filter(v -> v > 0)
            .mapToInt(Integer::intValue)
            .sum();
        if (totalAvailable < totalRolls) {
            throw new RuntimeException("库存不足，当前可用: " + totalAvailable + " 卷，需要: " + totalRolls + " 卷");
        }
        
        // FIFO分配
        List<TapeOutboundRequest> requests = new ArrayList<>();
        int remaining = totalRolls;
        
        for (TapeStock stock : stocks) {
            if (remaining <= 0) break;

            if (stock.getTotalRolls() == null || stock.getTotalRolls() <= 0) {
                continue;
            }

            int allocate = Math.min(remaining, stock.getTotalRolls());
            TapeOutboundRequest request = new TapeOutboundRequest();
            request.setStockId(stock.getId());
            request.setRolls(allocate);
            request.setApplicant(applicant);
            request.setApplyDept(applyDept);
            request.setRemark(remark);
            request.setOrderNo(orderNo);
            request.setOrderItemId(orderItemId);
            request.setBizType(StringUtils.hasText(bizType) ? bizType.trim() : TapeOutboundRequest.BIZ_TYPE_MANUAL);
            
            requests.add(createOutboundRequest(request));
            remaining -= allocate;
        }
        
        return requests;
    }

    private void normalizeOutboundOrderFields(TapeOutboundRequest request) {
        if (request == null) {
            return;
        }
        request.setOrderNo(trimToNull(request.getOrderNo()));
        request.setDeliveryNoticeNo(trimToNull(request.getDeliveryNoticeNo()));
        request.setBizType(trimToNull(request.getBizType()));
    }

    private void validateOutboundOrderRelation(TapeOutboundRequest request, TapeStock stock) {
        if (request == null || stock == null) {
            return;
        }
        String orderNo = trimToNull(request.getOrderNo());
        Long orderItemId = request.getOrderItemId();

        if (orderItemId == null && !StringUtils.hasText(orderNo)) {
            return;
        }

        if (orderItemId != null) {
            SalesOrderItem item = salesOrderItemMapper.selectById(orderItemId);
            if (item == null || (item.getIsDeleted() != null && item.getIsDeleted() == 1)) {
                throw new RuntimeException("关联订单明细不存在或已删除");
            }
            if (item.getOrderId() == null) {
                throw new RuntimeException("关联订单明细缺少订单ID");
            }

            SalesOrder order = salesOrderMapper.selectById(item.getOrderId());
            if (order == null || (order.getIsDeleted() != null && order.getIsDeleted() == 1)) {
                throw new RuntimeException("关联订单不存在或已删除");
            }
            String canonicalOrderNo = trimToNull(order.getOrderNo());
            if (!StringUtils.hasText(canonicalOrderNo)) {
                throw new RuntimeException("关联订单缺少订单号");
            }

            if (StringUtils.hasText(orderNo) && !canonicalOrderNo.equalsIgnoreCase(orderNo)) {
                throw new RuntimeException("订单号与订单明细不一致");
            }
            request.setOrderNo(canonicalOrderNo);
            request.setOrderItemId(item.getId());
            // 临时策略：出库申请不管控“订单明细与物料对应关系”及订单明细卷数配额。
            // 仅保留订单/订单明细存在性与订单号一致性校验，避免阻塞实际出库流程。
            return;
        }

        Long orderId = null;
        try {
            orderId = jdbcTemplate.queryForObject(
                    "SELECT id FROM sales_orders WHERE is_deleted = 0 AND order_no = ? LIMIT 1",
                    Long.class,
                    orderNo);
        } catch (Exception ignored) {
        }
        if (orderId == null) {
            throw new RuntimeException("订单号不存在: " + orderNo);
        }
        // 临时策略：仅校验订单号存在，不校验订单号下料号匹配与料号配额。
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String t = value.trim();
        return t.isEmpty() ? null : t;
    }

    private void normalizeOutboundDisplayFields(TapeOutboundRequest request) {
        if (request == null) {
            return;
        }
        String resolvedName = resolveProductNameByMaterialCode(request.getMaterialCode(), request.getProductName());
        request.setProductName(resolvedName);
    }

    private String resolveProductNameByMaterialCode(String materialCode, String fallbackName) {
        String code = materialCode == null ? "" : materialCode.trim();
        if (StringUtils.hasText(code)) {
            try {
                TapeSpec spec = tapeSpecMapper.selectByMaterialCode(code);
                if (spec != null && StringUtils.hasText(spec.getProductName())) {
                    return spec.getProductName().trim();
                }
            } catch (Exception ignored) {
            }
            return code;
        }
        return StringUtils.hasText(fallbackName) ? fallbackName.trim() : "";
    }
    
    @Override
    @Transactional
    public void approveOutbound(Long id, boolean approved, String auditor, String auditRemark, String scannedRollCode) {
        TapeOutboundRequest request = outboundMapper.selectById(id);
        if (request == null) {
            throw new RuntimeException("出库申请不存在");
        }
        if (request.getStatus() != TapeOutboundRequest.STATUS_PENDING) {
            throw new RuntimeException("该申请已处理");
        }
        
        request.setAuditor(auditor);
        request.setAuditTime(LocalDateTime.now());
        request.setAuditRemark(auditRemark);
        
        if (approved) {
            // 再次检查库存
            TapeStock stock = stockMapper.selectById(request.getStockId());
            if (stock == null || stock.getTotalRolls() == null || stock.getTotalRolls() < 1) {
                throw new RuntimeException("库存不足，无法完成出库");
            }
            normalizeOutboundOrderFields(request);
            validateOutboundOrderRelation(request, stock);
            String scannedCode = scannedRollCode == null ? "" : scannedRollCode.trim();
            String expectQr = stock.getQrCode() == null ? "" : stock.getQrCode().trim();
            String expectBatch = stock.getBatchNo() == null ? "" : stock.getBatchNo().trim();
            // 出库扫码改为可选：
            // - 未扫码：允许直接按申请审批通过
            // - 已扫码：仍执行卷码/批次一致性校验，防止误扫错扫
            if (StringUtils.hasText(scannedCode)) {
                boolean matched = (!expectQr.isEmpty() && expectQr.equalsIgnoreCase(scannedCode))
                        || (!expectBatch.isEmpty() && expectBatch.equalsIgnoreCase(scannedCode));
                if (!matched) {
                    throw new RuntimeException("扫码卷号与库存批次/二维码不一致，禁止出库");
                }
            }
            if (request.getRolls() == null || request.getRolls() <= 0) {
                request.setRolls(1);
            }
            if (stock.getTotalRolls() < request.getRolls()) {
                throw new RuntimeException("库存不足，当前可用: " + stock.getTotalRolls() + " 卷");
            }
            
            request.setStatus(TapeOutboundRequest.STATUS_APPROVED);
            
            // 扣减库存
            int beforeRolls = stock.getTotalRolls();
            int afterRolls = beforeRolls - request.getRolls();
            stock.setTotalRolls(afterRolls);
            stock.calculateTotalSqm();
            BigDecimal totalSqmAfterOut = stock.getTotalSqm() == null ? BigDecimal.ZERO : stock.getTotalSqm();
            BigDecimal reservedAfterOut = stock.getReservedArea() == null ? BigDecimal.ZERO : stock.getReservedArea();
            BigDecimal consumedAfterOut = stock.getConsumedArea() == null ? BigDecimal.ZERO : stock.getConsumedArea();
            BigDecimal availableAfterOut = totalSqmAfterOut.subtract(reservedAfterOut).subtract(consumedAfterOut);
            if (availableAfterOut.compareTo(BigDecimal.ZERO) < 0) {
                availableAfterOut = BigDecimal.ZERO;
            }
            stock.setAvailableArea(availableAfterOut);
            if (afterRolls == 0) {
                stock.setStatus(0); // 标记为已清空
            }
            stockMapper.updateById(stock);
            
            // 记录流水
            saveStockLog(stock.getId(), stock.getBatchNo(), stock.getMaterialCode(),
                    stock.getProductName(), TapeStockLog.TYPE_OUT, -request.getRolls(),
                    beforeRolls, afterRolls, request.getRequestNo(), auditor, "出库审批通过");

            // 记录统一流水
            String _unit_out = "卷";
            BigDecimal _change_out = BigDecimal.valueOf(-request.getRolls());
            BigDecimal _before_out = BigDecimal.valueOf(beforeRolls);
            BigDecimal _after_out = BigDecimal.valueOf(afterRolls);
            stockFlowLogService.logStockChange(
                StockFlowLog.StockType.TAPE.name(),
                stock.getId(),
                stock.getBatchNo(),
                stock.getMaterialCode(),
                stock.getProductName(),
                StockFlowLog.OperationType.OUT.name(),
                _change_out,
                _unit_out,
                _before_out,
                _after_out,
                request.getRequestNo(),
                auditor,
                "出库审批通过"
            );
        } else {
            request.setStatus(TapeOutboundRequest.STATUS_REJECTED);
        }
        
        outboundMapper.updateById(request);
    }

    @Override
    @Transactional
    public Map<String, Object> approveOutboundByRollCodes(List<String> rollCodes, String auditor, String auditRemark) {
        List<String> normalized = rollCodes == null ? new ArrayList<>() : rollCodes.stream()
                .filter(Objects::nonNull)
                .map(Object::toString)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .distinct()
                .collect(java.util.stream.Collectors.toList());
        if (normalized.isEmpty()) {
            throw new RuntimeException("请先录入有效卷号");
        }

        List<Map<String, Object>> failed = new ArrayList<>();
        List<String> approvedNos = new ArrayList<>();
        int successCount = 0;

        for (String rollCode : normalized) {
            try {
                LambdaQueryWrapper<TapeStock> stockWrapper = new LambdaQueryWrapper<>();
                stockWrapper.eq(TapeStock::getStatus, 1)
                        .and(w -> w.eq(TapeStock::getQrCode, rollCode)
                                .or().eq(TapeStock::getBatchNo, rollCode))
                        .orderByDesc(TapeStock::getId)
                        .last("LIMIT 1");
                TapeStock stock = stockMapper.selectOne(stockWrapper);
                if (stock == null) {
                    Map<String, Object> fail = new HashMap<>();
                    fail.put("rollCode", rollCode);
                    fail.put("reason", "未找到对应库存");
                    failed.add(fail);
                    continue;
                }

                LambdaQueryWrapper<TapeOutboundRequest> wrapper = new LambdaQueryWrapper<>();
                wrapper.eq(TapeOutboundRequest::getStockId, stock.getId())
                        .eq(TapeOutboundRequest::getStatus, TapeOutboundRequest.STATUS_PENDING)
                        .orderByDesc(TapeOutboundRequest::getId)
                        .last("LIMIT 1");
                TapeOutboundRequest req = outboundMapper.selectOne(wrapper);
                if (req == null) {
                    Map<String, Object> fail = new HashMap<>();
                    fail.put("rollCode", rollCode);
                    fail.put("reason", "未找到待审批出库申请");
                    failed.add(fail);
                    continue;
                }

                approveOutbound(req.getId(), true, auditor, auditRemark, rollCode);
                successCount++;
                approvedNos.add(req.getRequestNo());
            } catch (Exception ex) {
                Map<String, Object> fail = new HashMap<>();
                fail.put("rollCode", rollCode);
                fail.put("reason", ex.getMessage());
                failed.add(fail);
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("total", normalized.size());
        result.put("successCount", successCount);
        result.put("failCount", normalized.size() - successCount);
        result.put("approvedRequestNos", approvedNos);
        result.put("failed", failed);
        return result;
    }
    
    @Override
    @Transactional
    public void cancelOutbound(Long id) {
        TapeOutboundRequest request = outboundMapper.selectById(id);
        if (request == null) {
            throw new RuntimeException("出库申请不存在");
        }
        if (request.getStatus() != TapeOutboundRequest.STATUS_PENDING) {
            throw new RuntimeException("只能取消待审批的申请");
        }
        request.setStatus(TapeOutboundRequest.STATUS_CANCELLED);
        outboundMapper.updateById(request);
    }
    
    @Override
    public int countPendingOutbound() {
        return outboundMapper.countPending();
    }
    
    // ============= 库存流水 =============
    
    @Override
    public IPage<TapeStockLog> getStockLogPage(int page, int size, String type, String materialCode, String batchNo) {
        LambdaQueryWrapper<TapeStockLog> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(type)) {
            wrapper.eq(TapeStockLog::getType, type);
        }
        if (StringUtils.hasText(materialCode)) {
            wrapper.like(TapeStockLog::getMaterialCode, materialCode);
        }
        if (StringUtils.hasText(batchNo)) {
            wrapper.like(TapeStockLog::getBatchNo, batchNo);
        }
        wrapper.orderByDesc(TapeStockLog::getCreateTime);
        Page<TapeStockLog> pageParam = new Page<>(page, size);
        pageParam.setOptimizeCountSql(false); // 禁用COUNT优化，避免生成错误的SQL
        return logMapper.selectPage(pageParam, wrapper);
    }

    @Override
    public IPage<TapeStockLog> getOutboundLogSummaryPage(int page, int size, String materialCode, String batchNo) {
        QueryWrapper<TapeStockLog> wrapper = new QueryWrapper<>();
        wrapper.select(
                "MAX(id) AS id",
                "'OUT' AS type",
                "material_code AS material_code",
                "MAX(product_name) AS product_name",
                "batch_no AS batch_no",
                "SUM(change_rolls) AS change_rolls",
                "MIN(before_rolls) AS before_rolls",
                "MAX(after_rolls) AS after_rolls",
                "ref_no AS ref_no",
                "MAX(operator) AS operator",
                "MAX(remark) AS remark",
                "MAX(create_time) AS create_time"
        );
        wrapper.eq("type", TapeStockLog.TYPE_OUT);
        if (StringUtils.hasText(materialCode)) {
            wrapper.like("material_code", materialCode.trim());
        }
        if (StringUtils.hasText(batchNo)) {
            wrapper.like("batch_no", batchNo.trim());
        }
        wrapper.groupBy("ref_no", "material_code", "batch_no");
        wrapper.orderByDesc("MAX(create_time)");

        Page<Map<String, Object>> mapPage = new Page<>(page, size);
        mapPage.setOptimizeCountSql(false);
        IPage<Map<String, Object>> grouped = logMapper.selectMapsPage(mapPage, wrapper);

        Page<TapeStockLog> result = new Page<>(grouped.getCurrent(), grouped.getSize(), grouped.getTotal());
        List<TapeStockLog> records = new ArrayList<>();
        for (Map<String, Object> row : grouped.getRecords()) {
            TapeStockLog log = new TapeStockLog();
            log.setId(parseLongObj(row.get("id")));
            log.setType(TapeStockLog.TYPE_OUT);
            log.setMaterialCode(stringObj(row.get("material_code")));
            log.setProductName(stringObj(row.get("product_name")));
            log.setBatchNo(stringObj(row.get("batch_no")));
            log.setChangeRolls(parseIntObj(row.get("change_rolls")));
            log.setBeforeRolls(parseIntObj(row.get("before_rolls")));
            log.setAfterRolls(parseIntObj(row.get("after_rolls")));
            log.setRefNo(stringObj(row.get("ref_no")));
            log.setOperator(stringObj(row.get("operator")));
            log.setRemark(stringObj(row.get("remark")));
            log.setCreateTime(parseDateTimeObj(row.get("create_time")));
            records.add(log);
        }
        result.setRecords(records);
        return result;
    }

    private String stringObj(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private Integer parseIntObj(Object value) {
        if (value == null) {
            return 0;
        }
        try {
            if (value instanceof Number) {
                return ((Number) value).intValue();
            }
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception ignored) {
            return 0;
        }
    }

    private Long parseLongObj(Object value) {
        if (value == null) {
            return null;
        }
        try {
            if (value instanceof Number) {
                return ((Number) value).longValue();
            }
            return Long.parseLong(String.valueOf(value));
        } catch (Exception ignored) {
            return null;
        }
    }

    private LocalDateTime parseDateTimeObj(Object value) {
        if (value == null) {
            return null;
        }
        try {
            if (value instanceof LocalDateTime) {
                return (LocalDateTime) value;
            }
            if (value instanceof java.sql.Timestamp) {
                return ((java.sql.Timestamp) value).toLocalDateTime();
            }
            if (value instanceof java.util.Date) {
                return LocalDateTime.ofInstant(((java.util.Date) value).toInstant(), ZoneId.systemDefault());
            }
            return LocalDateTime.parse(String.valueOf(value).replace(" ", "T"));
        } catch (Exception ignored) {
            return null;
        }
    }
    
    @Override
    public List<TapeStockLog> exportStockLog(String type, String materialCode, String startDate, String endDate) {
        LambdaQueryWrapper<TapeStockLog> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(type)) {
            wrapper.eq(TapeStockLog::getType, type);
        }
        if (StringUtils.hasText(materialCode)) {
            wrapper.like(TapeStockLog::getMaterialCode, materialCode);
        }
        if (StringUtils.hasText(startDate)) {
            wrapper.ge(TapeStockLog::getCreateTime, LocalDate.parse(startDate).atStartOfDay());
        }
        if (StringUtils.hasText(endDate)) {
            wrapper.le(TapeStockLog::getCreateTime, LocalDate.parse(endDate).plusDays(1).atStartOfDay());
        }
        wrapper.orderByDesc(TapeStockLog::getCreateTime);
        return logMapper.selectList(wrapper);
    }

    @Override
    @Transactional
    public Map<String, Object> migrateMisroutedPurchaseInboundToRawWarehouse(String auditor) {
        String operator = StringUtils.hasText(auditor) ? auditor.trim() : "system";
        Map<String, Object> result = new LinkedHashMap<>();
        List<String> skipped = new ArrayList<>();
        List<String> migrated = new ArrayList<>();

        LambdaQueryWrapper<TapeInboundRequest> requestQuery = new LambdaQueryWrapper<>();
        requestQuery.eq(TapeInboundRequest::getStatus, TapeInboundRequest.STATUS_APPROVED)
                .like(TapeInboundRequest::getRemark, PURCHASE_RECEIPT_TAG)
                .orderByAsc(TapeInboundRequest::getId);
        List<TapeInboundRequest> requests = inboundMapper.selectList(requestQuery);

        int scanned = 0;
        int migratedReq = 0;
        int migratedTapeRows = 0;

        for (TapeInboundRequest request : requests) {
            if (request == null || !StringUtils.hasText(request.getRequestNo())) {
                continue;
            }
            scanned++;

            String category = resolvePurchaseInboundCategory(request);
            if (!shouldRoutePurchaseInboundToRawWarehouse(category)) {
                continue;
            }

            String migratedFlag = extractInboundTokenFromRemark(request.getRemark(), "rawMigrated");
            if (StringUtils.hasText(migratedFlag)) {
                skipped.add(request.getRequestNo() + ":已迁移(" + migratedFlag + ")");
                continue;
            }

            LambdaQueryWrapper<TapeStockLog> inLogQuery = new LambdaQueryWrapper<>();
            inLogQuery.eq(TapeStockLog::getRefNo, request.getRequestNo())
                    .eq(TapeStockLog::getType, TapeStockLog.TYPE_IN)
                    .gt(TapeStockLog::getChangeRolls, 0)
                    .orderByAsc(TapeStockLog::getId);
            List<TapeStockLog> inboundLogs = logMapper.selectList(inLogQuery);
            if (inboundLogs == null || inboundLogs.isEmpty()) {
                skipped.add(request.getRequestNo() + ":未找到胶带入库流水");
                continue;
            }

            Set<Long> stockIdSet = new LinkedHashSet<>();
            for (TapeStockLog log : inboundLogs) {
                if (log != null && log.getStockId() != null && log.getStockId() > 0) {
                    stockIdSet.add(log.getStockId());
                }
            }
            if (stockIdSet.isEmpty()) {
                skipped.add(request.getRequestNo() + ":未找到对应胶带库存ID");
                continue;
            }

            List<Long> stockIds = new ArrayList<>(stockIdSet);
            List<TapeStock> tapeStocks = stockMapper.selectBatchIds(stockIds);
            if (tapeStocks == null || tapeStocks.isEmpty()) {
                skipped.add(request.getRequestNo() + ":胶带库存记录不存在");
                continue;
            }

            List<TapeStock> activeStocks = new ArrayList<>();
            int migrateRolls = 0;
            for (TapeStock stock : tapeStocks) {
                if (stock == null || !Objects.equals(stock.getStatus(), 1)) {
                    continue;
                }
                int stockRolls = stock.getTotalRolls() == null ? 0 : stock.getTotalRolls();
                if (stockRolls <= 0) {
                    continue;
                }
                if ((stock.getReservedArea() != null && stock.getReservedArea().compareTo(BigDecimal.ZERO) > 0)
                        || (stock.getConsumedArea() != null && stock.getConsumedArea().compareTo(BigDecimal.ZERO) > 0)) {
                    skipped.add(request.getRequestNo() + ":库存已锁定/已消耗，跳过迁移");
                    activeStocks.clear();
                    break;
                }
                activeStocks.add(stock);
                migrateRolls += stockRolls;
            }
            if (activeStocks.isEmpty() || migrateRolls <= 0) {
                continue;
            }

            LambdaQueryWrapper<TapeStockLog> outCheck = new LambdaQueryWrapper<>();
            outCheck.in(TapeStockLog::getStockId, stockIds)
                    .eq(TapeStockLog::getType, TapeStockLog.TYPE_OUT)
                    .last("LIMIT 1");
            TapeStockLog outLog = logMapper.selectOne(outCheck);
            if (outLog != null) {
                skipped.add(request.getRequestNo() + ":存在出库流水，禁止自动迁移");
                continue;
            }

            String migrationRemark = mergeRemarkWithCustomerBatch(request.getRemark(), request.getCustomerBatchNo())
                    + ";[MIGRATED_FROM_TAPE]|requestNo=" + request.getRequestNo();
            if ("CHEMICAL".equalsIgnoreCase(category)) {
                inboundToChemicalWarehouse(request, migrateRolls, operator, migrationRemark);
            } else {
                inboundToFilmWarehouse(request, migrateRolls, operator, migrationRemark);
            }

            String migrationTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
            for (TapeStock stock : activeStocks) {
                int beforeRolls = stock.getTotalRolls() == null ? 0 : stock.getTotalRolls();
                stock.setTotalRolls(0);
                stock.setTotalSqm(BigDecimal.ZERO);
                stock.setAvailableArea(BigDecimal.ZERO);
                stock.setReservedArea(BigDecimal.ZERO);
                stock.setConsumedArea(BigDecimal.ZERO);
                stock.setStatus(0);
                stock.setRemark("MIGRATED_TO_" + category + "|requestNo=" + request.getRequestNo());
                stockMapper.updateById(stock);

                saveStockLog(stock.getId(), stock.getBatchNo(), stock.getMaterialCode(), stock.getProductName(),
                        TapeStockLog.TYPE_ADJUST, -beforeRolls, beforeRolls, 0,
                        request.getRequestNo(), operator, "历史纠偏：迁移到" + category + "原料仓");

                stockFlowLogService.logStockChange(
                        StockFlowLog.StockType.TAPE.name(),
                        stock.getId(),
                        stock.getBatchNo(),
                        stock.getMaterialCode(),
                        stock.getProductName(),
                        StockFlowLog.OperationType.ADJUST.name(),
                        BigDecimal.valueOf(-beforeRolls),
                        "卷",
                        BigDecimal.valueOf(beforeRolls),
                        BigDecimal.ZERO,
                        request.getRequestNo(),
                        operator,
                        "历史纠偏：迁移到" + category + "原料仓"
                );
                migratedTapeRows++;
            }

            request.setRemark(appendInboundRemarkToken(request.getRemark(), "rawMigrated", category + "@" + migrationTime));
            inboundMapper.updateById(request);

            migratedReq++;
            migrated.add(request.getRequestNo() + "->" + category + ", rolls=" + migrateRolls + ", rows=" + activeStocks.size());
        }

        result.put("scannedRequests", scanned);
        result.put("migratedRequests", migratedReq);
        result.put("migratedTapeRows", migratedTapeRows);
        result.put("migrated", migrated);
        result.put("skipped", skipped);
        return result;
    }

    @Override
    @Transactional
    public Map<String, Object> mergeHistoricalSlittingFinishedStock() {
        Map<String, Object> result = new HashMap<>();

        LambdaQueryWrapper<TapeStockLog> logQ = new LambdaQueryWrapper<>();
        logQ.eq(TapeStockLog::getType, TapeStockLog.TYPE_IN)
                .like(TapeStockLog::getRemark, "分切成品入库")
                .isNotNull(TapeStockLog::getRefNo)
                .orderByAsc(TapeStockLog::getId);
        List<TapeStockLog> logs = logMapper.selectList(logQ);

        Map<String, Set<Long>> refStockIds = new LinkedHashMap<>();
        for (TapeStockLog log : logs) {
            if (log == null || log.getStockId() == null) {
                continue;
            }
            String refNo = log.getRefNo() == null ? "" : log.getRefNo().trim();
            if (!StringUtils.hasText(refNo) || !refNo.startsWith("IN")) {
                continue;
            }
            refStockIds.computeIfAbsent(refNo, k -> new LinkedHashSet<>()).add(log.getStockId());
        }

        int mergedRefs = 0;
        int mergedGroups = 0;
        int mergedRows = 0;

        for (Map.Entry<String, Set<Long>> refEntry : refStockIds.entrySet()) {
            String refNo = refEntry.getKey();
            Set<Long> ids = refEntry.getValue();
            if (ids == null || ids.size() <= 1) {
                continue;
            }

            List<TapeStock> allStocks = stockMapper.selectBatchIds(ids);
            if (allStocks == null || allStocks.size() <= 1) {
                continue;
            }

            List<TapeStock> candidates = new ArrayList<>();
            for (TapeStock s : allStocks) {
                if (s == null) {
                    continue;
                }
                if (!Objects.equals(s.getStatus(), 1)) {
                    continue;
                }
                if (!"分切卷".equals(s.getRollType())) {
                    continue;
                }
                if (!SLITTING_PENDING_OUTBOUND_LOCATION.equals(s.getLocation())) {
                    continue;
                }
                if (s.getTotalRolls() == null || s.getTotalRolls() <= 0) {
                    continue;
                }
                candidates.add(s);
            }
            if (candidates.size() <= 1) {
                continue;
            }

            Map<String, List<TapeStock>> grouped = new LinkedHashMap<>();
            for (TapeStock s : candidates) {
                String key = (s.getMaterialCode() == null ? "" : s.getMaterialCode()) + "|"
                        + (s.getProductName() == null ? "" : s.getProductName()) + "|"
                        + (s.getThickness() == null ? 0 : s.getThickness()) + "|"
                        + (s.getWidth() == null ? 0 : s.getWidth()) + "|"
                        + (s.getLength() == null ? 0 : s.getLength()) + "|"
                        + (s.getLocation() == null ? "" : s.getLocation()) + "|"
                        + (s.getProdDate() == null ? "" : s.getProdDate().toString());
                grouped.computeIfAbsent(key, k -> new ArrayList<>()).add(s);
            }

            boolean refMerged = false;
            for (List<TapeStock> group : grouped.values()) {
                if (group == null || group.size() <= 1) {
                    continue;
                }

                group.sort(Comparator.comparing(TapeStock::getId));
                TapeStock keeper = group.get(0);

                int beforeKeeperRolls = keeper.getTotalRolls() == null ? 0 : keeper.getTotalRolls();
                BigDecimal sumTotalSqm = BigDecimal.ZERO;
                BigDecimal sumAvailable = BigDecimal.ZERO;
                BigDecimal sumReserved = BigDecimal.ZERO;
                BigDecimal sumConsumed = BigDecimal.ZERO;
                int sumRolls = 0;

                for (TapeStock s : group) {
                    sumRolls += (s.getTotalRolls() == null ? 0 : s.getTotalRolls());
                    sumTotalSqm = sumTotalSqm.add(s.getTotalSqm() == null ? BigDecimal.ZERO : s.getTotalSqm());
                    sumAvailable = sumAvailable.add(s.getAvailableArea() == null ? BigDecimal.ZERO : s.getAvailableArea());
                    sumReserved = sumReserved.add(s.getReservedArea() == null ? BigDecimal.ZERO : s.getReservedArea());
                    sumConsumed = sumConsumed.add(s.getConsumedArea() == null ? BigDecimal.ZERO : s.getConsumedArea());
                }

                keeper.setTotalRolls(sumRolls);
                keeper.setTotalSqm(sumTotalSqm.setScale(2, BigDecimal.ROUND_HALF_UP));
                keeper.setAvailableArea(sumAvailable.setScale(2, BigDecimal.ROUND_HALF_UP));
                keeper.setReservedArea(sumReserved.setScale(2, BigDecimal.ROUND_HALF_UP));
                keeper.setConsumedArea(sumConsumed.setScale(2, BigDecimal.ROUND_HALF_UP));
                keeper.setStatus(1);
                stockMapper.updateById(keeper);

                int deactivated = 0;
                for (int i = 1; i < group.size(); i++) {
                    TapeStock s = group.get(i);
                    int before = s.getTotalRolls() == null ? 0 : s.getTotalRolls();
                    s.setTotalRolls(0);
                    s.setTotalSqm(BigDecimal.ZERO);
                    s.setAvailableArea(BigDecimal.ZERO);
                    s.setReservedArea(BigDecimal.ZERO);
                    s.setConsumedArea(BigDecimal.ZERO);
                    s.setStatus(0);
                    stockMapper.updateById(s);
                    saveStockLog(s.getId(), s.getBatchNo(), s.getMaterialCode(), s.getProductName(),
                            TapeStockLog.TYPE_ADJUST, -before, before, 0,
                            refNo, "system", "历史分切成品聚合：并入库存ID=" + keeper.getId());
                    deactivated++;
                }

                int delta = sumRolls - beforeKeeperRolls;
                if (delta != 0) {
                    saveStockLog(keeper.getId(), keeper.getBatchNo(), keeper.getMaterialCode(), keeper.getProductName(),
                            TapeStockLog.TYPE_ADJUST, delta, beforeKeeperRolls, sumRolls,
                            refNo, "system", "历史分切成品聚合：合并" + group.size() + "条为1条");
                }

                mergedGroups++;
                mergedRows += deactivated;
                refMerged = true;
            }

            if (refMerged) {
                mergedRefs++;
            }
        }

        result.put("candidateRefs", refStockIds.size());
        result.put("mergedRefs", mergedRefs);
        result.put("mergedGroups", mergedGroups);
        result.put("deactivatedRows", mergedRows);
        return result;
    }
    
    // ============= 私有方法 =============
    
    private void saveStockLog(Long stockId, String batchNo, String materialCode, String productName,
                              String type, int changeRolls, int beforeRolls, int afterRolls,
                              String refNo, String operator, String remark) {
        TapeStockLog log = new TapeStockLog();
        log.setStockId(stockId);
        log.setBatchNo(batchNo);
        log.setMaterialCode(materialCode);
        log.setProductName(productName);
        log.setType(type);
        log.setChangeRolls(changeRolls);
        log.setBeforeRolls(beforeRolls);
        log.setAfterRolls(afterRolls);
        log.setRefNo(refNo);
        log.setOperator(operator);
        log.setRemark(remark);
        logMapper.insert(log);
    }


    private String getCellValue(Cell cell) {
        if (cell == null) return null;
        DataFormatter formatter = new DataFormatter();
        String text = formatter.formatCellValue(cell);
        return text == null ? null : text.trim();
    }

    private String sanitizeMaterialCode(String materialCode) {
        if (!StringUtils.hasText(materialCode)) {
            return null;
        }
        return materialCode
                .replace("\u00A0", "")
                .replace("\u3000", "")
                .replaceAll("\\s+", "")
                // 中文横线统一替换为半角-
                .replace('—', '-')
                .replace('－', '-')
                .replace('–', '-')
                .replace('﹣', '-')
                .trim();
    }

    private String composeRemark(String numberText, String reasonText) {
        String n = StringUtils.hasText(numberText) ? numberText.trim() : "";
        String r = StringUtils.hasText(reasonText) ? reasonText.trim() : "";
        if (StringUtils.hasText(n) && StringUtils.hasText(r)) {
            return "数字号:" + n + "；原因:" + r;
        }
        if (StringUtils.hasText(n)) {
            return "数字号:" + n;
        }
        if (StringUtils.hasText(r)) {
            return "原因:" + r;
        }
        return null;
    }

    @SuppressWarnings("unused")
    private String appendRemarkWithLimit(String origin, String append, int maxLen) {
        String base = origin == null ? "" : origin;
        String extra = append == null ? "" : append;
        String merged = base + extra;
        if (maxLen > 0 && merged.length() > maxLen) {
            return merged.substring(0, maxLen);
        }
        return merged;
    }

    private byte[] generateSkippedDataExcel(List<Map<String, Object>> skippedData) {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("跳过数据");
            Row headerRow = sheet.createRow(0);
            String[] headers = {"行号", "原始料号", "清理后料号", "原因"};
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
            }

            for (int i = 0; i < skippedData.size(); i++) {
                Map<String, Object> rowData = skippedData.get(i);
                Row row = sheet.createRow(i + 1);
                row.createCell(0).setCellValue(String.valueOf(rowData.getOrDefault("行号", "")));
                row.createCell(1).setCellValue(String.valueOf(rowData.getOrDefault("原始料号", "")));
                row.createCell(2).setCellValue(String.valueOf(rowData.getOrDefault("清理后料号", "")));
                row.createCell(3).setCellValue(String.valueOf(rowData.getOrDefault("原因", "")));
            }

            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            workbook.write(outputStream);
            return outputStream.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("生成跳过数据Excel失败: " + e.getMessage(), e);
        }
    }
    
    private Integer getIntCellValue(Cell cell) {
        if (cell == null) return null;
        try {
            if (cell.getCellType() == CellType.NUMERIC) {
                return (int) cell.getNumericCellValue();
            }
            String value = new DataFormatter().formatCellValue(cell).trim();
            if (!StringUtils.hasText(value)) {
                return null;
            }
            try {
                return Integer.parseInt(value);
            } catch (Exception ignored) {
            }

            // 兼容 40.0、21μm、1,000 等格式
            String cleaned = value
                    .replace(",", "")
                    .replace("，", "")
                    .replaceAll("[^0-9.\\-]", "");
            if (!StringUtils.hasText(cleaned)) {
                return null;
            }
            if (cleaned.contains(".")) {
                return (int) Math.round(Double.parseDouble(cleaned));
            }
            return Integer.parseInt(cleaned);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 历史导入日期容错：生产日期缺失或非法时置空，不中断导入。
     */
    private void safeGenerateProdDate(TapeStock stock) {
        if (stock == null) {
            return;
        }
        Integer y = stock.getProdYear();
        Integer m = stock.getProdMonth();
        Integer d = stock.getProdDay();
        if (y == null || m == null || d == null) {
            stock.setProdDate(null);
            return;
        }
        try {
            stock.generateProdDate();
        } catch (Exception ex) {
            stock.setProdYear(null);
            stock.setProdMonth(null);
            stock.setProdDay(null);
            stock.setProdDate(null);
        }
    }

    /**
     * 历史导入容错：部分历史模板把“长度/面积”等字段放到了“总数(卷)”列，
     * 会导致 total_sqm = 宽*长*卷数 被额外放大。
     */
    private void normalizeHistoricalRolls(TapeStock stock) {
        if (stock == null || stock.getTotalRolls() == null || stock.getWidth() == null || stock.getLength() == null) {
            return;
        }
        if (stock.getTotalRolls() <= 1) {
            return;
        }
        // 规则1：卷数异常大（历史导入中常见把长度/面积填入卷数）
        if (stock.getTotalRolls() >= 1000) {
            stock.setTotalRolls(1);
            return;
        }

        // 规则2：卷数与长度一致，通常是列错位（长度被读到了卷数）
        if (Objects.equals(stock.getTotalRolls(), stock.getLength())) {
            stock.setTotalRolls(1);
            return;
        }

        double perRollSqm = (stock.getWidth() / 1000.0) * stock.getLength();
        if (perRollSqm <= 0) {
            return;
        }
        // 规则3：卷数约等于单卷平米（面积被误放到卷数列）
        if (Math.abs(stock.getTotalRolls() - perRollSqm) <= 1.0d) {
            stock.setTotalRolls(1);
        }
    }

    private Cell getCellByHeader(Row row, Map<String, Integer> headerIndex, String[] headerNames, int fallbackIndex) {
        if (row == null) return null;
        if (headerIndex != null && headerNames != null) {
            for (String name : headerNames) {
                if (!StringUtils.hasText(name)) continue;
                Integer idx = headerIndex.get(normalizeHeaderName(name));
                if (idx != null && idx >= 0) {
                    return row.getCell(idx);
                }
            }
        }
        if (fallbackIndex >= 0) {
            return row.getCell(fallbackIndex);
        }
        return null;
    }

    private String normalizeHeaderName(String name) {
        if (!StringUtils.hasText(name)) {
            return "";
        }
        return name
                .replace("\u00A0", "")
                .replace("\u3000", "")
                .replace("（", "(")
                .replace("）", ")")
                .replaceAll("\\s+", "")
                .trim()
                .toLowerCase();
    }

    private LocalDate parseDateCellValue(Cell cell) {
        if (cell == null) {
            return null;
        }
        try {
            if (cell.getCellType() == CellType.NUMERIC) {
                double v = cell.getNumericCellValue();
                if (DateUtil.isCellDateFormatted(cell)) {
                    return cell.getDateCellValue().toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDate();
                }
                // 兼容“通用格式”下的Excel日期序列号
                if (v > 20000 && v < 80000) {
                    return DateUtil.getJavaDate(v).toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDate();
                }
            }
            String text = getCellValue(cell);
            if (!StringUtils.hasText(text)) {
                return null;
            }
            String value = text.trim();

            // 去掉时间部分（如 2025/11/27 00:00:00）
            int spaceIdx = value.indexOf(' ');
            if (spaceIdx > 0) {
                value = value.substring(0, spaceIdx).trim();
            }

            // 兼容中文日期 2025年11月27日
            value = value.replace("年", "-").replace("月", "-").replace("日", "");
            // 兼容 yyyy/M/d、yyyy-M-d、yyyy.M.d
            String normalized = value.replace('.', '-').replace('/', '-');

            String[] patterns = new String[]{"yyyy-M-d", "yyyy-MM-dd", "yy-M-d", "yy-MM-dd"};
            for (String p : patterns) {
                try {
                    return LocalDate.parse(normalized, DateTimeFormatter.ofPattern(p));
                } catch (Exception ignored) {
                }
            }
            return null;
        } catch (Exception ex) {
            return null;
        }
    }

    /**
     * 历史导入兜底：当生产日期为空时，从批次号前6位按 yyMMdd 反推日期。
     * 例如：2512063A09 -> 2025-12-06。
     */
    private void applyProdDateFallbackFromBatchNo(TapeStock stock) {
        if (stock == null || stock.getProdDate() != null) {
            return;
        }
        String sourceBatchNo = StringUtils.hasText(stock.getParentBatchNo()) ? stock.getParentBatchNo() : stock.getBatchNo();
        if (!StringUtils.hasText(sourceBatchNo)) {
            return;
        }
        Matcher matcher = Pattern.compile("^(\\d{6})").matcher(sourceBatchNo.trim());
        if (!matcher.find()) {
            return;
        }
        try {
            LocalDate inferred = LocalDate.parse(matcher.group(1), DateTimeFormatter.ofPattern("yyMMdd"));
            stock.setProdDate(inferred);
            stock.setProdYear(inferred.getYear());
            stock.setProdMonth(inferred.getMonthValue());
            stock.setProdDay(inferred.getDayOfMonth());
        } catch (Exception ignored) {
            // 忽略无法解析的批次号，保持为空
        }
    }

    private String normalizeRollType(String rollType) {
        if (!StringUtils.hasText(rollType)) {
            return rollType;
        }
        String v = rollType.trim();
        if ("是".equals(v) || "Y".equalsIgnoreCase(v) || "YES".equalsIgnoreCase(v) || "true".equalsIgnoreCase(v)) {
            return "母卷";
        }
        if ("否".equals(v) || "N".equalsIgnoreCase(v) || "NO".equalsIgnoreCase(v) || "false".equalsIgnoreCase(v)) {
            return "复卷";
        }
        return v;
    }

}
