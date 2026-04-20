package com.fine.serviceIMPL.stock;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fine.Dao.rd.TapeSpecMapper;
import com.fine.Dao.stock.*;
import com.fine.modle.rd.TapeSpec;
import com.fine.modle.stock.*;
import com.fine.service.stock.StockFlowLogService;
import com.fine.model.stock.StockFlowLog;
import com.fine.service.stock.TapeStockService;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
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
        public String getName() { return name; }

        @Override
        public String getOriginalFilename() { return originalFilename; }

        @Override
        public String getContentType() { return contentType; }

        @Override
        public boolean isEmpty() { return content.length == 0; }

        @Override
        public long getSize() { return content.length; }

        @Override
        public byte[] getBytes() { return content; }

        @Override
        public InputStream getInputStream() { return new ByteArrayInputStream(content); }

        @Override
        public void transferTo(java.io.File dest) throws IOException, IllegalStateException {
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
        return stockMapper.selectByMaterialCodePage(pageParam, materialCode, (String) null, true);  // 默认包含退货专仓
    }

    @Override
    public IPage<TapeStock> getStockByMaterialPage(int page, int size, String materialCode, String location) {
        try {
            stockMapper.normalizeAreaFields();
        } catch (Exception ignored) {
        }
        Page<TapeStock> pageParam = new Page<>(page, size);
        pageParam.setOptimizeCountSql(false);
        return stockMapper.selectByMaterialCodePage(pageParam, materialCode, location, true);  // 默认包含退货专仓
    }

    @Override
    public IPage<TapeStock> getStockByMaterialPage(int page, int size, String materialCode, Boolean includeReturnWarehouse) {
        try {
            stockMapper.normalizeAreaFields();
        } catch (Exception ignored) {
        }
        Page<TapeStock> pageParam = new Page<>(page, size);
        pageParam.setOptimizeCountSql(false);
        return stockMapper.selectByMaterialCodePage(pageParam, materialCode, (String) null, includeReturnWarehouse);
    }
    
    @Override
    public TapeStock getStockById(Long id) {
        return stockMapper.selectById(id);
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
                    String inboundBatchNo = buildInboundRollBatchNo(originalBatchNo, null, 1);
                    stock.setBatchNo(inboundBatchNo);
                    if (!Objects.equals(originalBatchNo, inboundBatchNo)) {
                        stock.setParentBatchNo(originalBatchNo);
                    }
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
    public IPage<TapeInboundRequest> getInboundPage(int page, int size, Integer status, String materialCode) {
        LambdaQueryWrapper<TapeInboundRequest> wrapper = new LambdaQueryWrapper<>();
        if (status != null) {
            wrapper.eq(TapeInboundRequest::getStatus, status);
        }
        if (StringUtils.hasText(materialCode)) {
            wrapper.like(TapeInboundRequest::getMaterialCode, materialCode);
        }
        wrapper.orderByDesc(TapeInboundRequest::getCreateTime);
        Page<TapeInboundRequest> pageParam = new Page<>(page, size);
        pageParam.setOptimizeCountSql(false);
        IPage<TapeInboundRequest> result = inboundMapper.selectPage(pageParam, wrapper);
        if (result != null && result.getRecords() != null) {
            for (TapeInboundRequest record : result.getRecords()) {
                normalizeInboundDisplayFields(record);
            }
        }
        return result;
    }
    
    @Override
    @Transactional
    public TapeInboundRequest createInboundRequest(TapeInboundRequest request) {
        normalizeInboundDisplayFields(request);

        // 生成单号
        String requestNo = inboundMapper.generateRequestNo();
        if (requestNo == null) {
            requestNo = "IN" + java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd").format(LocalDateTime.now()) + "0001";
        }
        request.setRequestNo(requestNo);
        request.setApplyTime(LocalDateTime.now());
        request.setStatus(TapeInboundRequest.STATUS_PENDING);
        
        // 生成规格描述和生产日期
        if (request.getThickness() != null && request.getWidth() != null && request.getLength() != null) {
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

            // 写入库存：
            // - 分切成品：按申请单聚合一条（totalRolls=申请卷数）
            // - 其他类型：仍按每卷一条
            Integer maxSeq = stockMapper.selectMaxSequenceNoByBatchNo(request.getBatchNo());
            int seq = maxSeq != null ? maxSeq : 0;

            if (slittingFinishedInbound) {
                seq += 1;
                TapeStock stock = new TapeStock();
                stock.setMaterialCode(request.getMaterialCode());
                stock.setProductName(request.getProductName());
                stock.setBatchNo(buildInboundRollBatchNo(request.getBatchNo(), request.getRequestNo(), seq));
                stock.setSequenceNo(seq);
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

                if (needAutoFulfill) {
                    needAutoFulfill = autoFulfillPendingLocks(stock, request.getRequestNo(), auditor);
                }
            } else {
            for (int i = 0; i < rolls; i++) {
                seq += 1;
                TapeStock stock = new TapeStock();
                stock.setMaterialCode(request.getMaterialCode());
                stock.setProductName(request.getProductName());
                stock.setBatchNo(buildInboundRollBatchNo(request.getBatchNo(), request.getRequestNo(), seq));
                stock.setSequenceNo(seq);
                stock.setRollType((salesReturnInbound || slittingFinishedInbound) ? "分切卷" : "母卷");
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

                // 记录流水（每卷）
                saveStockLog(stock.getId(), stock.getBatchNo(), stock.getMaterialCode(),
                        stock.getProductName(), TapeStockLog.TYPE_IN, 1,
                    0, 1, request.getRequestNo(), auditor,
                    salesReturnInbound
                        ? "销售退货入库-进入退货专仓"
                        : (slittingFinishedInbound ? "分切成品入库-进入成品待出库区" : "入库审批通过-单卷入库"));

                // 记录统一流水
                String _unit_in = "卷";
                BigDecimal _change_in = BigDecimal.ONE;
                BigDecimal _before_in = BigDecimal.ZERO;
                BigDecimal _after_in = BigDecimal.ONE;
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

    private void normalizeInboundDisplayFields(TapeInboundRequest request) {
        if (request == null) {
            return;
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

        if (StringUtils.hasText(request.getSpecDesc())) {
            fillDimensionsFromSpecDescIfMissing(request, request.getSpecDesc().trim());
        }

        if (request.getThickness() != null && request.getWidth() != null && request.getLength() != null
                && request.getThickness() > 0 && request.getWidth() > 0 && request.getLength() > 0) {
            request.setSpecDesc(request.getThickness() + "μm*" + request.getWidth() + "mm*" + request.getLength() + "m");
        }
    }

    private void fillDimensionsFromSpecDescIfMissing(TapeInboundRequest request, String specDesc) {
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
        if (request.getThickness() == null || request.getThickness() <= 0) {
            request.setThickness(nums.get(0));
        }
        if (request.getWidth() == null || request.getWidth() <= 0) {
            request.setWidth(nums.get(1));
        }
        if (request.getLength() == null || request.getLength() <= 0) {
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
    public IPage<TapeOutboundRequest> getOutboundPage(int page, int size, Integer status, String materialCode) {
        LambdaQueryWrapper<TapeOutboundRequest> wrapper = new LambdaQueryWrapper<>();
        if (status != null) {
            wrapper.eq(TapeOutboundRequest::getStatus, status);
        }
        if (StringUtils.hasText(materialCode)) {
            wrapper.like(TapeOutboundRequest::getMaterialCode, materialCode);
        }
        wrapper.orderByDesc(TapeOutboundRequest::getCreateTime);
        Page<TapeOutboundRequest> pageParam = new Page<>(page, size);
        pageParam.setOptimizeCountSql(false);
        return outboundMapper.selectPage(pageParam, wrapper);
    }
    
    @Override
    @Transactional
    public TapeOutboundRequest createOutboundRequest(TapeOutboundRequest request) {
        // 检查库存
        TapeStock stock = stockMapper.selectById(request.getStockId());
        if (stock == null) {
            throw new RuntimeException("库存记录不存在");
        }
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
        request.setProductName(stock.getProductName());
        request.setBatchNo(stock.getBatchNo());
        request.setSpecDesc(stock.getSpecDesc());
        request.setAvailableRolls(stock.getTotalRolls());
        request.setApplyTime(LocalDateTime.now());
        request.setStatus(TapeOutboundRequest.STATUS_PENDING);
        
        outboundMapper.insert(request);
        return request;
    }
    
    @Override
    @Transactional
    public List<TapeOutboundRequest> createOutboundRequestFIFO(String materialCode, int totalRolls,
                                                                String applicant, String applyDept, String remark) {
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
            
            requests.add(createOutboundRequest(request));
            remaining -= allocate;
        }
        
        return requests;
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
            String scannedCode = scannedRollCode == null ? "" : scannedRollCode.trim();
            String expectQr = stock.getQrCode() == null ? "" : stock.getQrCode().trim();
            String expectBatch = stock.getBatchNo() == null ? "" : stock.getBatchNo().trim();
            // 兼容历史无码库存：当库存无二维码且未扫码时，允许按批次号回退校验出库
            if (!StringUtils.hasText(scannedCode)) {
                if (StringUtils.hasText(expectQr)) {
                    throw new RuntimeException("请先扫码卷号进行出库");
                }
                if (!StringUtils.hasText(expectBatch)) {
                    throw new RuntimeException("库存缺少二维码和批次号，无法校验出库");
                }
                scannedCode = expectBatch;
            }
            boolean matched = (!expectQr.isEmpty() && expectQr.equalsIgnoreCase(scannedCode))
                    || (!expectBatch.isEmpty() && expectBatch.equalsIgnoreCase(scannedCode));
            if (!matched) {
                throw new RuntimeException("扫码卷号与库存批次/二维码不一致，禁止出库");
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

    /**
     * 入库审批时按“批次号 + 全局序列号”生成唯一批次号，避免每卷重复查重带来的性能开销。
     */
    private String buildInboundRollBatchNo(String batchNo, String requestNo, int sequenceNo) {
        String base = StringUtils.hasText(batchNo) ? batchNo.trim() : (StringUtils.hasText(requestNo) ? requestNo.trim() : "INBOUND");
        if (sequenceNo <= 1) {
            return base;
        }
        return base + "-" + String.format("%03d", sequenceNo);
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
