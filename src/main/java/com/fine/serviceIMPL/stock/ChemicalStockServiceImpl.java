package com.fine.serviceIMPL.stock;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fine.Dao.rd.TapeFormulaMapper;
import com.fine.Dao.stock.ChemicalStockMapper;
import com.fine.Dao.stock.ChemicalStockDetailMapper;
import com.fine.Dao.stock.ChemicalStockOutMapper;
import com.fine.Dao.stock.StockFlowLogMapper;
import com.fine.modle.rd.TapeRawMaterial;
import com.fine.model.stock.ChemicalStock;
import com.fine.model.stock.ChemicalStockDetail;
import com.fine.model.stock.ChemicalStockOut;
import com.fine.service.stock.ChemicalStockService;
import com.fine.service.stock.StockFlowLogService;
import com.fine.model.stock.StockFlowLog;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 化工原料库存服务实现类
 * @author Fine
 * @date 2026-01-15
 */
@Service
public class ChemicalStockServiceImpl implements ChemicalStockService {
    
    @Autowired
    private ChemicalStockMapper chemicalStockMapper;
    
    @Autowired
    private ChemicalStockDetailMapper chemicalStockDetailMapper;
    
    @Autowired
    private ChemicalStockOutMapper chemicalStockOutMapper;

    @Autowired
    private StockFlowLogService stockFlowLogService;

    @Autowired
    private TapeFormulaMapper tapeFormulaMapper;

    @Autowired
    private StockFlowLogMapper stockFlowLogMapper;
    
    @Override
    public List<ChemicalStock> getByType(String chemicalType) {
        List<ChemicalStock> list = chemicalStockMapper.selectByType(chemicalType);
        fillBucketCount(list);
        return list;
    }
    
    @Override
    public List<ChemicalStock> getAllChemicalStock() {
        QueryWrapper<ChemicalStock> wrapper = new QueryWrapper<>();
        wrapper.orderByDesc("create_time");
        List<ChemicalStock> list = chemicalStockMapper.selectList(wrapper);
        fillBucketCount(list);
        return list;
    }

    @Override
    public IPage<ChemicalStock> getChemicalStockPage(long current,
                                                     long size,
                                                     String chemicalType,
                                                     String materialCode,
                                                     String sortField,
                                                     String sortOrder) {
        Page<ChemicalStock> page = new Page<>(current, size);
        QueryWrapper<ChemicalStock> wrapper = new QueryWrapper<>();
        wrapper.eq(StringUtils.hasText(chemicalType), "chemical_type", chemicalType);
        if (StringUtils.hasText(materialCode)) {
            wrapper.like("material_code", materialCode.trim());
        }

        String sortColumn = resolveChemicalStockSortColumn(sortField);
        boolean asc = "ascending".equalsIgnoreCase(sortOrder) || "asc".equalsIgnoreCase(sortOrder);
        wrapper.orderBy(true, asc, sortColumn);
        wrapper.orderBy(true, asc, "id");
        IPage<ChemicalStock> result = chemicalStockMapper.selectPage(page, wrapper);
        fillBucketCount(result.getRecords());
        return result;
    }

    @Override
    public Map<String, Object> getChemicalStockStatistics(String chemicalType, String materialCode) {
        QueryWrapper<ChemicalStock> wrapper = new QueryWrapper<>();
        wrapper.select(
                "COUNT(1) AS totalTypes",
                "COALESCE(SUM(total_quantity), 0) AS totalQuantity",
                "COALESCE(SUM(available_quantity), 0) AS availableQuantity",
                "COALESCE(SUM(locked_quantity), 0) AS lockedQuantity"
        );
        wrapper.eq(StringUtils.hasText(chemicalType), "chemical_type", chemicalType);
        if (StringUtils.hasText(materialCode)) {
            wrapper.like("material_code", materialCode.trim());
        }

        List<Map<String, Object>> rows = chemicalStockMapper.selectMaps(wrapper);
        Map<String, Object> first = (rows == null || rows.isEmpty()) ? new HashMap<>() : rows.get(0);

        Map<String, Object> result = new HashMap<>();
        result.put("totalTypes", toDoubleValue(first.get("totalTypes")));
        result.put("totalQuantity", toDoubleValue(first.get("totalQuantity")));
        result.put("availableQuantity", toDoubleValue(first.get("availableQuantity")));
        result.put("lockedQuantity", toDoubleValue(first.get("lockedQuantity")));
        return result;
    }

    private String resolveChemicalStockSortColumn(String sortField) {
        if (!StringUtils.hasText(sortField)) {
            return "create_time";
        }
        switch (sortField.trim()) {
            case "materialCode":
                return "material_code";
            case "materialName":
                return "material_name";
            case "chemicalType":
                return "chemical_type";
            case "unitWeight":
                return "unit_weight";
            case "totalQuantity":
                return "total_quantity";
            case "availableQuantity":
                return "available_quantity";
            case "bucketCount":
                return "bucket_count";
            case "lockedQuantity":
                return "locked_quantity";
            case "safetyStock":
                return "safety_stock";
            case "status":
                return "status";
            case "updateTime":
                return "update_time";
            case "createTime":
                return "create_time";
            default:
                return "create_time";
        }
    }

    private Double toDoubleValue(Object value) {
        if (value == null) {
            return 0.0;
        }
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (Exception ignore) {
            return 0.0;
        }
    }
    
    @Override
    public ChemicalStock getById(Long id) {
        return chemicalStockMapper.selectById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ChemicalStock updateStock(Long id, ChemicalStock stock) {
        ChemicalStock existed = chemicalStockMapper.selectById(id);
        if (existed == null) {
            throw new RuntimeException("化工库存不存在，ID: " + id);
        }

        if (StringUtils.hasText(stock.getMaterialName())) {
            existed.setMaterialName(stock.getMaterialName().trim());
        }
        if (StringUtils.hasText(stock.getChemicalType())) {
            existed.setChemicalType(stock.getChemicalType().trim());
        }
        if (StringUtils.hasText(stock.getUnit())) {
            existed.setUnit(stock.getUnit().trim());
        }

        if (stock.getUnitWeight() != null) {
            existed.setUnitWeight(stock.getUnitWeight().compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : stock.getUnitWeight());
        }

        Double available = stock.getAvailableQuantity();
        Double locked = stock.getLockedQuantity();
        Double total = stock.getTotalQuantity();

        if (available != null) {
            existed.setAvailableQuantity(Math.max(available, 0.0));
        }
        if (locked != null) {
            existed.setLockedQuantity(Math.max(locked, 0.0));
        }
        if (total != null) {
            existed.setTotalQuantity(Math.max(total, 0.0));
        }

        Double finalAvailable = existed.getAvailableQuantity() == null ? 0.0 : existed.getAvailableQuantity();
        Double finalLocked = existed.getLockedQuantity() == null ? 0.0 : existed.getLockedQuantity();
        if (total == null) {
            existed.setTotalQuantity(finalAvailable + finalLocked);
        }

        if (stock.getBucketCount() != null) {
            existed.setBucketCount(Math.max(stock.getBucketCount(), 0.0));
        } else if (stock.getAvailableQuantity() != null || stock.getLockedQuantity() != null || stock.getTotalQuantity() != null) {
            existed.setBucketCount(existed.getTotalQuantity() == null ? 0.0 : existed.getTotalQuantity());
        }

        if (stock.getSafetyStock() != null) {
            existed.setSafetyStock(Math.max(stock.getSafetyStock(), 0.0));
        }
        if (StringUtils.hasText(stock.getStatus())) {
            existed.setStatus(stock.getStatus().trim());
        }
        existed.setRemark(stock.getRemark());
        existed.setUpdateTime(new Date());

        chemicalStockMapper.updateById(existed);
        return chemicalStockMapper.selectById(id);
    }
    
    @Override
    public List<ChemicalStockDetail> getDetailsByChemicalStockId(Long chemicalStockId) {
        return chemicalStockDetailMapper.selectByChemicalStockId(chemicalStockId);
    }
    
    @Override
    public List<ChemicalStockDetail> getAvailableDetails(Long chemicalStockId) {
        return chemicalStockDetailMapper.selectByStatus(chemicalStockId, "available");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ChemicalStockDetail createDetail(Long chemicalStockId, ChemicalStockDetail detail) {
        ChemicalStock stock = chemicalStockMapper.selectById(chemicalStockId);
        if (stock == null) {
            throw new RuntimeException("化工库存不存在，ID: " + chemicalStockId);
        }

        Date now = new Date();
        ChemicalStockDetail row = new ChemicalStockDetail();
        row.setChemicalStockId(chemicalStockId);
        row.setMaterialCode(StringUtils.hasText(stock.getMaterialCode()) ? stock.getMaterialCode().trim() : null);
        row.setBatchNo(detail.getBatchNo());
        // 如果桶号/包号为空字符串，则写入 null，避免触发唯一索引冲突
        row.setContainerNo(StringUtils.hasText(detail.getContainerNo()) ? detail.getContainerNo().trim() : null);
        row.setUnit(StringUtils.hasText(detail.getUnit()) ? detail.getUnit().trim() :
                (StringUtils.hasText(stock.getUnit()) ? stock.getUnit().trim() : "桶"));
        row.setWeight(detail.getWeight() != null ? detail.getWeight() : BigDecimal.ZERO);
        String resolvedPackUom = StringUtils.hasText(detail.getPackUom()) ? detail.getPackUom().trim() : row.getUnit();
        BigDecimal resolvedStdQtyPerPack = detail.getStdQtyPerPack() != null && detail.getStdQtyPerPack().compareTo(BigDecimal.ZERO) > 0
            ? detail.getStdQtyPerPack()
            : (row.getWeight() != null && row.getWeight().compareTo(BigDecimal.ZERO) > 0 ? row.getWeight() : BigDecimal.ONE);
        row.setPackUom(resolvedPackUom);
        row.setPackCount(detail.getPackCount() != null && detail.getPackCount() > 0 ? detail.getPackCount() : 1.0);
        row.setStdUom(StringUtils.hasText(detail.getStdUom()) ? detail.getStdUom().trim() : "kg");
        row.setStdQtyPerPack(resolvedStdQtyPerPack);
        row.setLocation(detail.getLocation());
        row.setSupplier(detail.getSupplier());
        row.setInboundDate(detail.getInboundDate() != null ? detail.getInboundDate() : now);
        row.setExpiryDate(detail.getExpiryDate());
        row.setIsOpened(detail.getIsOpened() != null ? detail.getIsOpened() : Boolean.FALSE);
        row.setDangerLevel(detail.getDangerLevel() != null ? detail.getDangerLevel() : 1);
        row.setStatus(StringUtils.hasText(detail.getStatus()) ? detail.getStatus().trim() : "available");
        row.setRemark(detail.getRemark());
        row.setCreateTime(now);
        row.setUpdateTime(now);

        chemicalStockDetailMapper.insert(row);
        refreshStockSummaryByDetails(chemicalStockId);
        return chemicalStockDetailMapper.selectById(row.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ChemicalStockDetail updateDetail(Long chemicalStockId, Long detailId, ChemicalStockDetail detail) {
        ChemicalStockDetail existed = chemicalStockDetailMapper.selectById(detailId);
        if (existed == null) {
            throw new RuntimeException("库存明细不存在，ID: " + detailId);
        }
        if (!chemicalStockId.equals(existed.getChemicalStockId())) {
            throw new RuntimeException("明细与库存不匹配，无法修改");
        }

        existed.setBatchNo(detail.getBatchNo());
        // 更新时若桶号/包号为空，则设为 null，防止多个空字符串违背唯一索引约束
        existed.setContainerNo(StringUtils.hasText(detail.getContainerNo()) ? detail.getContainerNo().trim() : null);
        if (StringUtils.hasText(detail.getUnit())) {
            existed.setUnit(detail.getUnit().trim());
        }
        existed.setWeight(detail.getWeight() != null ? detail.getWeight() : BigDecimal.ZERO);
        existed.setPackUom(StringUtils.hasText(detail.getPackUom()) ? detail.getPackUom().trim() : existed.getUnit());
        existed.setPackCount(detail.getPackCount() != null && detail.getPackCount() > 0 ? detail.getPackCount() : 1.0);
        existed.setStdUom(StringUtils.hasText(detail.getStdUom()) ? detail.getStdUom().trim() : "kg");
        existed.setStdQtyPerPack(detail.getStdQtyPerPack() != null && detail.getStdQtyPerPack().compareTo(BigDecimal.ZERO) > 0
            ? detail.getStdQtyPerPack()
            : (existed.getWeight() != null && existed.getWeight().compareTo(BigDecimal.ZERO) > 0 ? existed.getWeight() : BigDecimal.ONE));
        existed.setLocation(detail.getLocation());
        existed.setSupplier(detail.getSupplier());
        existed.setInboundDate(detail.getInboundDate());
        existed.setExpiryDate(detail.getExpiryDate());
        existed.setIsOpened(detail.getIsOpened());
        existed.setDangerLevel(detail.getDangerLevel());
        existed.setStatus(StringUtils.hasText(detail.getStatus()) ? detail.getStatus().trim() : "available");
        existed.setRemark(detail.getRemark());
        existed.setUpdateTime(new Date());

        chemicalStockDetailMapper.updateById(existed);
        refreshStockSummaryByDetails(chemicalStockId);
        return chemicalStockDetailMapper.selectById(detailId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteDetail(Long chemicalStockId, Long detailId) {
        ChemicalStockDetail existed = chemicalStockDetailMapper.selectById(detailId);
        if (existed == null) {
            return false;
        }
        if (!chemicalStockId.equals(existed.getChemicalStockId())) {
            throw new RuntimeException("明细与库存不匹配，无法删除");
        }

        int rows = chemicalStockDetailMapper.deleteById(detailId);
        refreshStockSummaryByDetails(chemicalStockId);
        return rows > 0;
    }
    
    @Override
    public List<ChemicalStockDetail> getExpiringSoon(Integer days) {
        return chemicalStockDetailMapper.selectExpiringSoon(days);
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean lockStock(Long chemicalStockId, Double lockQuantity, List<Long> detailIds) {
        // 1. 锁定总量表
        int rows = chemicalStockMapper.lockStock(chemicalStockId, lockQuantity);
        if (rows == 0) {
            throw new RuntimeException("库存不足，无法锁定");
        }
        
        // 2. 锁定明细
        if (detailIds != null && !detailIds.isEmpty()) {
            int detailRows = chemicalStockDetailMapper.batchUpdateStatus(detailIds, "locked");
            if (detailRows != detailIds.size()) {
                throw new RuntimeException("部分明细锁定失败");
            }
            // 明细发生变化后，主表必须按明细重算，避免出现数量不一致
            refreshStockSummaryByDetails(chemicalStockId);
        }
        
        return true;
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean unlockStock(Long chemicalStockId, Double unlockQuantity, List<Long> detailIds) {
        // 1. 解锁总量表
        int rows = chemicalStockMapper.unlockStock(chemicalStockId, unlockQuantity);
        if (rows == 0) {
            throw new RuntimeException("解锁失败，锁定库存不足");
        }
        
        // 2. 解锁明细
        if (detailIds != null && !detailIds.isEmpty()) {
            int detailRows = chemicalStockDetailMapper.batchUpdateStatus(detailIds, "available");
            if (detailRows != detailIds.size()) {
                throw new RuntimeException("部分明细解锁失败");
            }
            // 明细发生变化后，主表必须按明细重算，避免出现数量不一致
            refreshStockSummaryByDetails(chemicalStockId);
        }
        
        return true;
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean outbound(ChemicalStockOut chemicalStockOut, List<Long> detailIds) {
        // 1. 扣减总量
        int rows = chemicalStockMapper.deductStock(
            chemicalStockOut.getChemicalStockId(), 
            chemicalStockOut.getOutQuantity()
        );
        if (rows == 0) {
            throw new RuntimeException("出库失败，可用库存和锁定库存都不足");
        }
        
        // 2. 更新明细状态为已使用
        if (detailIds != null && !detailIds.isEmpty()) {
            int detailRows = chemicalStockDetailMapper.batchUpdateStatus(detailIds, "used");
            if (detailRows != detailIds.size()) {
                throw new RuntimeException("部分明细更新失败");
            }
            // 出库改变了明细状态，必须同步重算主表，确保可用数量与明细一致
            refreshStockSummaryByDetails(chemicalStockOut.getChemicalStockId());
        }
        
        // 3. 创建出库记录（兼容新表字段）
        Date now = new Date();
        chemicalStockOut.setCreateTime(now);
        if (chemicalStockOut.getOutboundTime() == null) {
            chemicalStockOut.setOutboundTime(now);
        }
        if (chemicalStockOut.getOutWeight() == null) {
            chemicalStockOut.setOutWeight(BigDecimal.ZERO);
        }

        ChemicalStock stock = chemicalStockMapper.selectById(chemicalStockOut.getChemicalStockId());
        if (stock != null) {
            if (!StringUtils.hasText(chemicalStockOut.getMaterialCode())) {
                chemicalStockOut.setMaterialCode(stock.getMaterialCode());
            }
            if (!StringUtils.hasText(chemicalStockOut.getCreateBy())) {
                chemicalStockOut.setCreateBy(StringUtils.hasText(chemicalStockOut.getOutboundBy())
                    ? chemicalStockOut.getOutboundBy()
                    : "SYSTEM");
            }
        }

        if (!StringUtils.hasText(chemicalStockOut.getOutboundNo())) {
            chemicalStockOut.setOutboundNo("CHOUT" + new java.text.SimpleDateFormat("yyMMddHHmmss").format(now)
                + "-" + chemicalStockOut.getChemicalStockId());
        }

        if (chemicalStockOut.getChemicalDetailId() == null) {
            if (detailIds != null && !detailIds.isEmpty()) {
                chemicalStockOut.setChemicalDetailId(detailIds.get(0));
            } else {
                chemicalStockOut.setChemicalDetailId(0L);
            }
        }

        ChemicalStockDetail firstDetail = null;
        if (!StringUtils.hasText(chemicalStockOut.getBatchNo())
            && chemicalStockOut.getChemicalDetailId() != null
            && chemicalStockOut.getChemicalDetailId() > 0) {
            firstDetail = chemicalStockDetailMapper.selectById(chemicalStockOut.getChemicalDetailId());
            if (firstDetail != null && StringUtils.hasText(firstDetail.getBatchNo())) {
                chemicalStockOut.setBatchNo(firstDetail.getBatchNo());
            }
        } else if (chemicalStockOut.getChemicalDetailId() != null && chemicalStockOut.getChemicalDetailId() > 0) {
            firstDetail = chemicalStockDetailMapper.selectById(chemicalStockOut.getChemicalDetailId());
        }

        String stockSpecSnapshot = resolveChemicalStockSpecSnapshot(stock, firstDetail);
        chemicalStockOut.setRemark(appendRemarkTokenIfMissing(chemicalStockOut.getRemark(), "stockSpec", stockSpecSnapshot));

        chemicalStockOutMapper.insert(chemicalStockOut);

        // 记录统一流水
        if (stock != null) {
                BigDecimal _usage = chemicalStockOut.getOutQuantity() != null
                    ? BigDecimal.valueOf(chemicalStockOut.getOutQuantity())
                    : BigDecimal.ZERO;
                String _unit = stock.getUnit() != null ? stock.getUnit() : "桶";
                BigDecimal _before = stock.getAvailableQuantity() != null
                    ? BigDecimal.valueOf(stock.getAvailableQuantity())
                    : BigDecimal.ZERO;
                BigDecimal _after = _before.subtract(_usage);
            stockFlowLogService.logStockChange(
                StockFlowLog.StockType.CHEMICAL.name(),
                stock.getId(),
                stock.getMaterialCode(), // 化工原料没有批次号，使用物料编码
                stock.getMaterialCode(),
                stock.getMaterialName(),
                StockFlowLog.OperationType.OUT.name(),
            _usage.negate(),
                _unit,
                _before,
                _after,
                StringUtils.hasText(chemicalStockOut.getOutboundNo())
                    ? chemicalStockOut.getOutboundNo()
                    : (chemicalStockOut.getScheduleId() != null ? chemicalStockOut.getScheduleId().toString() : "MANUAL_OUT"),
                chemicalStockOut.getOutboundBy() != null ? chemicalStockOut.getOutboundBy() : "SYSTEM",
                "化工原料出库"
            );
        }
        
        return true;
    }
    
    @Override
    public List<ChemicalStockOut> getOutboundByScheduleId(Long scheduleId) {
        return chemicalStockOutMapper.selectByScheduleId(scheduleId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> importExcel(MultipartFile file) {
        return importExcel(file, false);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> importExcel(MultipartFile file, boolean clearBeforeImport) {
        if (clearBeforeImport) {
            clearForReimport(true);
        }
        try (Workbook workbook = new XSSFWorkbook(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);
            Map<String, Integer> headerIndex = new HashMap<>();
            Row headerRow = sheet.getRow(0);
            if (headerRow != null) {
                for (int c = 0; c <= headerRow.getLastCellNum(); c++) {
                    String name = getCellValue(headerRow.getCell(c));
                    if (StringUtils.hasText(name)) {
                        headerIndex.put(name.replace("\u00A0", "").trim(), c);
                    }
                }
            }

            if (!isDetailImportMode(headerIndex)) {
                throw new RuntimeException("化工库存仅支持明细导入。请使用【化工库存导入模板-明细.xlsx】，至少包含：物料编号、批次号、桶号/包号、重量(kg)");
            }
            return importExcelByDetails(sheet, headerIndex);
        } catch (Exception ex) {
            throw new RuntimeException("导入化工库存失败: " + ex.getMessage(), ex);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> clearForReimport(boolean clearOutboundRecords) {
        Map<String, Object> result = new HashMap<>();

        int detailDeleted = chemicalStockDetailMapper.delete(new QueryWrapper<>());

        int outboundDeleted = 0;
        if (clearOutboundRecords) {
            outboundDeleted = chemicalStockOutMapper.delete(new QueryWrapper<>());
        }

        int stockDeleted = chemicalStockMapper.delete(new QueryWrapper<>());

        if (clearOutboundRecords) {
            QueryWrapper<StockFlowLog> flowQw = new QueryWrapper<>();
            flowQw.eq("stock_type", "CHEMICAL");
            stockFlowLogMapper.delete(flowQw);
        }

        result.put("detailDeleted", detailDeleted);
        result.put("outboundDeleted", outboundDeleted);
        result.put("stockDeleted", stockDeleted);
        result.put("clearOutboundRecords", clearOutboundRecords);
        return result;
    }

    private boolean isDetailImportMode(Map<String, Integer> headerIndex) {
        if (headerIndex == null || headerIndex.isEmpty()) {
            return false;
        }
        return containsHeader(headerIndex, "桶号/包号", "barrel_no", "containerNo")
                || containsHeader(headerIndex, "批次号", "batch_no", "batchNo")
                || containsHeader(headerIndex, "重量(kg)", "weight");
    }

    private boolean containsHeader(Map<String, Integer> headerIndex, String... names) {
        if (headerIndex == null || names == null) {
            return false;
        }
        for (String name : names) {
            if (headerIndex.containsKey(name)) {
                return true;
            }
        }
        return false;
    }

    private Map<String, Object> importExcelByDetails(Sheet sheet, Map<String, Integer> headerIndex) {
        Map<String, Object> result = new HashMap<>();
        int successCount = 0;
        int skipCount = 0;
        List<String> errors = new ArrayList<>();
        List<Map<String, Object>> skippedData = new ArrayList<>();

        List<Map<String, Object>> parsedRows = new ArrayList<>();
        Set<String> materialCodes = new LinkedHashSet<>();
        Set<String> dedupKeys = new HashSet<>();
        Map<String, TapeRawMaterial> normalizedRawMaterialMap = buildNormalizedRawMaterialMap();

        for (int i = 1; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (row == null) {
                continue;
            }
            try {
                String originalMaterialCode = getCellValue(getCellByHeader(row, headerIndex,
                        new String[]{"物料编号", "物料编码", "料号", "material_code"}, 0));
                if (!StringUtils.hasText(originalMaterialCode)) {
                    throw new RuntimeException("物料编号不能为空");
                }
                String materialCode = cleanMaterialCode(originalMaterialCode);

                TapeRawMaterial rawMaterial = resolveRawMaterialByCode(materialCode, normalizedRawMaterialMap);
                if (rawMaterial == null || !StringUtils.hasText(rawMaterial.getMaterialName())) {
                    throw new RuntimeException("原材料表中未找到该料号");
                }
                materialCode = cleanMaterialCode(rawMaterial.getMaterialCode());

                String batchNo = getCellValue(getCellByHeader(row, headerIndex,
                        new String[]{"批次号", "batch_no", "batchNo"}, 1));
                String containerNo = getCellValue(getCellByHeader(row, headerIndex,
                        new String[]{"桶号/包号", "barrel_no", "containerNo"}, 2));
                String unit = getCellValue(getCellByHeader(row, headerIndex,
                        new String[]{"单位", "unit"}, 3));
                BigDecimal weight = getDecimalCellValue(getCellByHeader(row, headerIndex,
                        new String[]{"重量(kg)", "重量", "weight"}, 4));
                String packUom = getCellValue(getCellByHeader(row, headerIndex,
                    new String[]{"包装单位", "pack_uom", "packUom"}, 5));
                Integer packCount = getIntCellValue(getCellByHeader(row, headerIndex,
                    new String[]{"包装数量", "pack_count", "packCount"}, 6));
                String stdUom = getCellValue(getCellByHeader(row, headerIndex,
                    new String[]{"标准单位", "std_uom", "stdUom"}, 7));
                BigDecimal stdQtyPerPack = getDecimalCellValue(getCellByHeader(row, headerIndex,
                    new String[]{"每包装标准量", "std_qty_per_pack", "stdQtyPerPack"}, 8));
                String location = getCellValue(getCellByHeader(row, headerIndex,
                    new String[]{"库位", "location"}, 9));
                String supplier = getCellValue(getCellByHeader(row, headerIndex,
                    new String[]{"供应商", "supplier"}, 10));
                String inboundDateText = getCellValue(getCellByHeader(row, headerIndex,
                    new String[]{"入库日期", "storage_date", "inboundDate"}, 11));
                String expiryDateText = getCellValue(getCellByHeader(row, headerIndex,
                    new String[]{"有效期至", "expiry_date", "expiryDate"}, 12));
                String openedText = getCellValue(getCellByHeader(row, headerIndex,
                    new String[]{"是否开封", "is_opened", "isOpened"}, 13));
                Integer dangerLevel = getIntCellValue(getCellByHeader(row, headerIndex,
                    new String[]{"危险等级", "danger_level", "dangerLevel"}, 14));
                String status = getCellValue(getCellByHeader(row, headerIndex,
                    new String[]{"状态", "status"}, 15));
                String remark = getCellValue(getCellByHeader(row, headerIndex,
                    new String[]{"备注", "remark"}, 16));

                if (weight == null || weight.compareTo(BigDecimal.ZERO) <= 0) {
                    throw new RuntimeException("重量必须大于0");
                }
                if (!StringUtils.hasText(batchNo)) {
                    batchNo = "IMP-" + materialCode;
                }
                if (!StringUtils.hasText(containerNo)) {
                    containerNo = materialCode + "-" + (i + 1);
                }
                String dedupKey = materialCode + "|" + batchNo + "|" + containerNo;
                if (!dedupKeys.add(dedupKey)) {
                    throw new RuntimeException("文件内存在重复明细(料号+批次+桶号): " + dedupKey);
                }

                Map<String, Object> parsed = new HashMap<>();
                parsed.put("materialCode", materialCode);
                parsed.put("materialName", rawMaterial.getMaterialName().trim());
                parsed.put("batchNo", batchNo.trim());
                parsed.put("containerNo", containerNo.trim());
                String resolvedUnit = StringUtils.hasText(unit) ? unit.trim() : (StringUtils.hasText(rawMaterial.getUnit()) ? rawMaterial.getUnit().trim() : "Kg");
                parsed.put("unit", resolvedUnit);
                parsed.put("weight", weight);
                parsed.put("packUom", StringUtils.hasText(packUom) ? packUom.trim() : resolvedUnit);
                parsed.put("packCount", packCount != null && packCount > 0 ? packCount : 1);
                parsed.put("stdUom", StringUtils.hasText(stdUom) ? stdUom.trim() : "kg");
                parsed.put("stdQtyPerPack", stdQtyPerPack != null && stdQtyPerPack.compareTo(BigDecimal.ZERO) > 0 ? stdQtyPerPack : weight);
                parsed.put("location", location);
                parsed.put("supplier", supplier);
                parsed.put("inboundDate", parseDateCell(inboundDateText));
                parsed.put("expiryDate", parseDateCell(expiryDateText));
                parsed.put("isOpened", parseBoolean(openedText));
                parsed.put("dangerLevel", dangerLevel == null ? 1 : dangerLevel);
                parsed.put("status", StringUtils.hasText(status) ? status.trim() : "available");
                parsed.put("remark", remark);
                parsedRows.add(parsed);
                materialCodes.add(materialCode);
            } catch (Exception ex) {
                skipCount++;
                Map<String, Object> skippedRow = new HashMap<>();
                String originalMaterialCode = getCellValue(getCellByHeader(row, headerIndex,
                    new String[]{"物料编号", "物料编码", "料号", "material_code"}, 0));
                skippedRow.put("行号", i + 1);
                skippedRow.put("原始物料编号", originalMaterialCode);
                skippedRow.put("清理后物料编号", cleanMaterialCode(originalMaterialCode));
                skippedRow.put("原因", "数据格式错误: " + ex.getMessage());
                skippedData.add(skippedRow);
            }
        }

        if (!parsedRows.isEmpty()) {
            Map<String, ChemicalStock> stockByCode = new HashMap<>();
            int mergedDuplicateStockCount = 0;
            for (String materialCode : materialCodes) {
                QueryWrapper<ChemicalStock> qw = new QueryWrapper<>();
                qw.eq("material_code", materialCode);
                qw.orderByAsc("id");
                List<ChemicalStock> existedStocks = chemicalStockMapper.selectList(qw);

                ChemicalStock stock = null;
                if (existedStocks != null && !existedStocks.isEmpty()) {
                    stock = existedStocks.get(0);
                    if (existedStocks.size() > 1) {
                        List<Long> duplicateStockIds = new ArrayList<>();
                        for (int idx = 1; idx < existedStocks.size(); idx++) {
                            ChemicalStock duplicate = existedStocks.get(idx);
                            if (duplicate != null && duplicate.getId() != null) {
                                duplicateStockIds.add(duplicate.getId());
                            }
                        }
                        if (!duplicateStockIds.isEmpty()) {
                            QueryWrapper<ChemicalStockDetail> dupDetailQw = new QueryWrapper<>();
                            dupDetailQw.in("stock_id", duplicateStockIds);
                            chemicalStockDetailMapper.delete(dupDetailQw);

                            QueryWrapper<ChemicalStockOut> dupOutQw = new QueryWrapper<>();
                            dupOutQw.in("chemical_stock_id", duplicateStockIds);
                            chemicalStockOutMapper.delete(dupOutQw);

                            QueryWrapper<ChemicalStock> dupStockQw = new QueryWrapper<>();
                            dupStockQw.in("id", duplicateStockIds);
                            chemicalStockMapper.delete(dupStockQw);

                            mergedDuplicateStockCount += duplicateStockIds.size();
                        }
                    }
                }

                if (stock == null) {
                    TapeRawMaterial rawMaterial = resolveRawMaterialByCode(materialCode, normalizedRawMaterialMap);
                    ChemicalStock created = new ChemicalStock();
                    created.setMaterialCode(materialCode);
                    created.setMaterialName(findMaterialName(parsedRows, materialCode));
                    created.setChemicalType(resolveChemicalType(rawMaterial));
                    created.setUnit(findMaterialUnit(parsedRows, materialCode));
                    created.setUnitWeight(findMaterialUnitWeight(parsedRows, materialCode));
                    created.setStatus("active");
                    created.setCreateBy("import");
                    created.setUpdateBy("import");
                    chemicalStockMapper.insert(created);
                    stock = created;
                }
                stockByCode.put(materialCode, stock);
            }

            List<Long> stockIds = new ArrayList<>();
            for (ChemicalStock stock : stockByCode.values()) {
                stockIds.add(stock.getId());
            }
            if (!stockIds.isEmpty()) {
                QueryWrapper<ChemicalStockDetail> delQw = new QueryWrapper<>();
                delQw.in("stock_id", stockIds);
                chemicalStockDetailMapper.delete(delQw);
            }

            for (Map<String, Object> parsed : parsedRows) {
                String materialCode = String.valueOf(parsed.get("materialCode"));
                ChemicalStock stock = stockByCode.get(materialCode);
                if (stock == null || stock.getId() == null) {
                    continue;
                }

                ChemicalStockDetail detail = new ChemicalStockDetail();
                detail.setChemicalStockId(stock.getId());
                detail.setMaterialCode(materialCode);
                detail.setBatchNo((String) parsed.get("batchNo"));
                detail.setContainerNo((String) parsed.get("containerNo"));
                detail.setUnit((String) parsed.get("unit"));
                detail.setWeight((BigDecimal) parsed.get("weight"));
                detail.setPackUom((String) parsed.get("packUom"));
                detail.setPackCount(parsed.get("packCount") == null ? null : ((Number) parsed.get("packCount")).doubleValue());
                detail.setStdUom((String) parsed.get("stdUom"));
                detail.setStdQtyPerPack((BigDecimal) parsed.get("stdQtyPerPack"));
                detail.setLocation((String) parsed.get("location"));
                detail.setSupplier((String) parsed.get("supplier"));
                detail.setInboundDate((Date) parsed.get("inboundDate"));
                detail.setExpiryDate((Date) parsed.get("expiryDate"));
                detail.setIsOpened((Boolean) parsed.get("isOpened"));
                detail.setDangerLevel((Integer) parsed.get("dangerLevel"));
                detail.setStatus((String) parsed.get("status"));
                detail.setRemark((String) parsed.get("remark"));
                detail.setCreateTime(new Date());
                detail.setUpdateTime(new Date());
                chemicalStockDetailMapper.insert(detail);
                successCount++;
            }

            for (ChemicalStock stock : stockByCode.values()) {
                refreshStockSummaryByDetails(stock.getId());
            }
            if (mergedDuplicateStockCount > 0) {
                errors.add("导入前自动清理重复化工主档 " + mergedDuplicateStockCount + " 条（按料号保留最早一条）");
            }
        }

        byte[] skippedExcel = null;
        if (!skippedData.isEmpty()) {
            skippedExcel = generateSkippedDataExcel(skippedData);
        }

        result.put("success", skipCount == 0);
        result.put("successCount", successCount);
        result.put("skipCount", skipCount);
        result.put("errors", errors);
        result.put("skippedExcel", skippedExcel != null ? java.util.Base64.getEncoder().encodeToString(skippedExcel) : null);
        return result;
    }

    private Map<String, TapeRawMaterial> buildNormalizedRawMaterialMap() {
        Map<String, TapeRawMaterial> map = new HashMap<>();
        List<TapeRawMaterial> raws = tapeFormulaMapper.selectAllRawMaterialsIncludingDisabled();
        if (raws == null) {
            return map;
        }
        for (TapeRawMaterial raw : raws) {
            if (raw == null || !StringUtils.hasText(raw.getMaterialCode())) {
                continue;
            }
            String key = normalizeMaterialCode(raw.getMaterialCode());
            if (!StringUtils.hasText(key)) {
                continue;
            }
            map.putIfAbsent(key, raw);
        }
        return map;
    }

    private TapeRawMaterial resolveRawMaterialByCode(String materialCode,
                                                     Map<String, TapeRawMaterial> normalizedRawMaterialMap) {
        String cleaned = cleanMaterialCode(materialCode);
        if (!StringUtils.hasText(cleaned)) {
            return null;
        }

        TapeRawMaterial exact = tapeFormulaMapper.selectRawMaterialByCode(cleaned);
        if (exact != null && StringUtils.hasText(exact.getMaterialName())) {
            return exact;
        }

        String noSpace = cleaned.replaceAll("\\s+", "");
        if (!noSpace.equals(cleaned)) {
            TapeRawMaterial noSpaceMatch = tapeFormulaMapper.selectRawMaterialByCode(noSpace);
            if (noSpaceMatch != null && StringUtils.hasText(noSpaceMatch.getMaterialName())) {
                return noSpaceMatch;
            }
        }

        String normalized = normalizeMaterialCode(cleaned);
        TapeRawMaterial normalizedMatch = normalizedRawMaterialMap.get(normalized);
        if (normalizedMatch != null && StringUtils.hasText(normalizedMatch.getMaterialName())) {
            return normalizedMatch;
        }
        return null;
    }

    private String cleanMaterialCode(String materialCode) {
        if (!StringUtils.hasText(materialCode)) {
            return "";
        }
        return materialCode
                .replace('\u00A0', ' ')
                .replace('\u3000', ' ')
                .trim();
    }

    private String normalizeMaterialCode(String materialCode) {
        String cleaned = cleanMaterialCode(materialCode);
        if (!StringUtils.hasText(cleaned)) {
            return "";
        }
        return cleaned
                .replaceAll("[‐‑‒–—―−－]", "-")
                .replaceAll("[：﹕∶]", ":")
                .replaceAll("[／]", "/")
                .replaceAll("\\s+", "")
                .toUpperCase(Locale.ROOT);
    }

    private String findMaterialName(List<Map<String, Object>> parsedRows, String materialCode) {
        for (Map<String, Object> row : parsedRows) {
            if (materialCode.equals(row.get("materialCode"))) {
                Object name = row.get("materialName");
                return name == null ? materialCode : String.valueOf(name);
            }
        }
        return materialCode;
    }

    private String findMaterialUnit(List<Map<String, Object>> parsedRows, String materialCode) {
        for (Map<String, Object> row : parsedRows) {
            if (materialCode.equals(row.get("materialCode"))) {
                Object unit = row.get("unit");
                return unit == null ? "Kg" : String.valueOf(unit);
            }
        }
        return "Kg";
    }

    private BigDecimal findMaterialUnitWeight(List<Map<String, Object>> parsedRows, String materialCode) {
        for (Map<String, Object> row : parsedRows) {
            if (materialCode.equals(row.get("materialCode"))) {
                Object weight = row.get("weight");
                if (weight instanceof BigDecimal) {
                    BigDecimal v = (BigDecimal) weight;
                    return v.compareTo(BigDecimal.ZERO) > 0 ? v : null;
                }
                if (weight != null) {
                    try {
                        BigDecimal v = new BigDecimal(String.valueOf(weight));
                        return v.compareTo(BigDecimal.ZERO) > 0 ? v : null;
                    } catch (Exception ignore) {
                        // ignore parse error
                    }
                }
            }
        }
        return null;
    }

    private Date parseDateCell(String text) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        String t = text.trim();
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd");
        sdf.setLenient(false);
        try {
            return sdf.parse(t);
        } catch (Exception ignore) {
            return null;
        }
    }

    private Boolean parseBoolean(String text) {
        if (!StringUtils.hasText(text)) {
            return false;
        }
        String t = text.trim().toLowerCase();
        return "1".equals(t) || "true".equals(t) || "是".equals(t) || "y".equals(t);
    }

    private byte[] generateSkippedDataExcel(List<Map<String, Object>> skippedData) {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("跳过数据");
            
            // 创建表头
            Row headerRow = sheet.createRow(0);
            String[] headers = {"行号", "原始物料编号", "清理后物料编号", "原因", "物料名称", "化工类型", "单位"};
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
            }
            
            // 填充数据
            for (int i = 0; i < skippedData.size(); i++) {
                Map<String, Object> rowData = skippedData.get(i);
                Row row = sheet.createRow(i + 1);
                row.createCell(0).setCellValue((Integer) rowData.get("行号"));
                row.createCell(1).setCellValue((String) rowData.getOrDefault("原始物料编号", ""));
                row.createCell(2).setCellValue((String) rowData.getOrDefault("清理后物料编号", ""));
                row.createCell(3).setCellValue((String) rowData.getOrDefault("原因", ""));
                row.createCell(4).setCellValue((String) rowData.getOrDefault("物料名称", ""));
                row.createCell(5).setCellValue((String) rowData.getOrDefault("化工类型", ""));
                row.createCell(6).setCellValue((String) rowData.getOrDefault("单位", ""));
            }
            
            // 自动调整列宽
            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }
            
            // 输出到字节数组
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            workbook.write(outputStream);
            return outputStream.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("生成跳过数据Excel失败: " + e.getMessage(), e);
        }
    }

    private Cell getCellByHeader(Row row, Map<String, Integer> headerIndex, String[] names, int fallbackIndex) {
        if (headerIndex != null && names != null) {
            for (String name : names) {
                Integer idx = headerIndex.get(name);
                if (idx != null) {
                    return row.getCell(idx);
                }
            }
        }
        return fallbackIndex >= 0 ? row.getCell(fallbackIndex) : null;
    }

    private String getCellValue(Cell cell) {
        if (cell == null) {
            return null;
        }
        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue().trim();
            case NUMERIC:
                double v = cell.getNumericCellValue();
                if (v == Math.floor(v)) {
                    return String.valueOf((long) v);
                }
                return String.valueOf(v);
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            default:
                return null;
        }
    }

    private Integer getIntCellValue(Cell cell) {
        String value = getCellValue(cell);
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return Integer.parseInt(value.split("\\.")[0]);
    }

    private BigDecimal getDecimalCellValue(Cell cell) {
        String value = getCellValue(cell);
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return new BigDecimal(value);
    }

    @SuppressWarnings("unused")
    private Integer defaultInt(Integer value) {
        return value == null ? 0 : value;
    }

    private String resolveChemicalType(TapeRawMaterial rawMaterial) {
        if (rawMaterial == null) {
            return "other";
        }
        String materialType = rawMaterial.getMaterialType() == null ? "" : rawMaterial.getMaterialType().trim().toLowerCase();
        if ("solvent".equals(materialType)) {
            return "solvent";
        }
        if ("additive".equals(materialType)) {
            return "additive";
        }
        if ("resin".equals(materialType) || "curing".equals(materialType)) {
            return "adhesive";
        }

        String category = rawMaterial.getMaterialCategory() == null ? "" : rawMaterial.getMaterialCategory().trim().toLowerCase();
        if ("chemical".equals(category)) {
            return "adhesive";
        }
        return "other";
    }

    private void fillBucketCount(List<ChemicalStock> list) {
        if (list == null || list.isEmpty()) {
            return;
        }
        for (ChemicalStock stock : list) {
            if (stock == null || stock.getId() == null) {
                continue;
            }
            Double persistedBucketCount = stock.getBucketCount();
            if (persistedBucketCount != null && persistedBucketCount > 0) {
                stock.setBucketCount(persistedBucketCount);
                continue;
            }
            List<ChemicalStockDetail> details = chemicalStockDetailMapper.selectByChemicalStockId(stock.getId());
            if (details == null || details.isEmpty()) {
                bootstrapDetailsFromSummary(stock);
                details = chemicalStockDetailMapper.selectByChemicalStockId(stock.getId());
            }
            double bucketCount = 0D;
            if (details != null) {
                for (ChemicalStockDetail d : details) {
                    if (d == null) {
                        continue;
                    }
                    String status = d.getStatus() == null ? "" : d.getStatus().trim().toLowerCase();
                    if (!"used".equals(status)) {
                        bucketCount += 1D;
                    }
                }
            }
            // 优先展示导入时持久化的原值
            if (persistedBucketCount != null && persistedBucketCount > 0) {
                stock.setBucketCount(persistedBucketCount);
            } else {
                stock.setBucketCount(bucketCount);
            }
        }
    }

    private void bootstrapDetailsFromSummary(ChemicalStock stock) {
        if (stock == null || stock.getId() == null) {
            return;
        }
        Integer targetBucketCount = (stock.getBucketCount() != null && stock.getBucketCount() > 0)
            ? stock.getBucketCount().intValue()
            : inferBucketCount(stock.getTotalQuantity(), stock.getUnitWeight());
        if (targetBucketCount == null || targetBucketCount <= 0) {
            return;
        }

        int availableQty = stock.getAvailableQuantity() == null ? 0 : stock.getAvailableQuantity().intValue();
        int lockedQty = stock.getLockedQuantity() == null ? 0 : stock.getLockedQuantity().intValue();
        BigDecimal perBucketWeight = stock.getUnitWeight() == null ? BigDecimal.ZERO : stock.getUnitWeight();
        int lockedBuckets = Math.max(lockedQty, 0);
        if (lockedBuckets < 0) {
            lockedBuckets = 0;
        }
        if (lockedBuckets > targetBucketCount) {
            lockedBuckets = targetBucketCount;
        }
        int availableBuckets = targetBucketCount - lockedBuckets;
        if (availableQty <= 0) {
            availableBuckets = 0;
            lockedBuckets = targetBucketCount;
        }

        Date now = new Date();
        String code = stock.getMaterialCode() == null ? "CHEM" : stock.getMaterialCode();
        for (int i = 1; i <= targetBucketCount; i++) {
            ChemicalStockDetail detail = new ChemicalStockDetail();
            detail.setChemicalStockId(stock.getId());
            detail.setMaterialCode(stock.getMaterialCode());
            detail.setBatchNo("INIT-" + code);
            detail.setContainerNo(String.format("%s-%03d", code, i));
            detail.setUnit(StringUtils.hasText(stock.getUnit()) ? stock.getUnit().trim() : "桶");
            detail.setPackUom(detail.getUnit());
            detail.setPackCount(1.0);
            detail.setStdUom("kg");
            detail.setStdQtyPerPack(perBucketWeight);
            detail.setWeight(perBucketWeight);
            detail.setInboundDate(now);
            detail.setIsOpened(false);
            detail.setDangerLevel(1);
            detail.setStatus(i <= availableBuckets ? "available" : "locked");
            detail.setRemark("系统根据汇总库存自动补建明细");
            detail.setCreateTime(now);
            detail.setUpdateTime(now);
            chemicalStockDetailMapper.insert(detail);
        }
    }

    private Integer inferBucketCount(Double totalQuantity, BigDecimal unitWeight) {
        if (totalQuantity == null || totalQuantity <= 0) {
            return 0;
        }
        // 2026-05: 化工库存汇总数量字段统一按“包装数量（桶/包）”语义维护
        return totalQuantity.intValue();
    }

    @SuppressWarnings("unused")
    private void rebuildImportDetails(ChemicalStock stock,
                                      Integer bucketCount,
                                      Double totalQuantity,
                                      Double availableQuantity,
                                      Double lockedQuantity,
                                      BigDecimal unitWeight) {
        if (stock == null || stock.getId() == null) {
            return;
        }

        QueryWrapper<ChemicalStockDetail> delQw = new QueryWrapper<>();
        delQw.eq("stock_id", stock.getId());
        chemicalStockDetailMapper.delete(delQw);

        int count = bucketCount == null ? 0 : bucketCount;
        if (count <= 0) {
            return;
        }

        BigDecimal perBucketWeight = unitWeight;
        if (perBucketWeight == null || perBucketWeight.compareTo(BigDecimal.ZERO) <= 0) {
            int tq = totalQuantity == null ? 0 : totalQuantity.intValue();
            perBucketWeight = count > 0 ? BigDecimal.valueOf(tq).divide(BigDecimal.valueOf(count), 2, BigDecimal.ROUND_HALF_UP) : BigDecimal.ZERO;
        }

        int aq = availableQuantity == null ? 0 : availableQuantity.intValue();
        int lq = lockedQuantity == null ? 0 : lockedQuantity.intValue();
        int lockedBuckets = Math.max(lq, 0);
        if (lockedBuckets < 0) {
            lockedBuckets = 0;
        }
        if (lockedBuckets > count) {
            lockedBuckets = count;
        }
        int availableBuckets = count - lockedBuckets;
        if (aq == 0) {
            availableBuckets = 0;
            lockedBuckets = count;
        }

        Date now = new Date();
        String code = stock.getMaterialCode() == null ? "CHEM" : stock.getMaterialCode();
        for (int i = 1; i <= count; i++) {
            ChemicalStockDetail detail = new ChemicalStockDetail();
            detail.setChemicalStockId(stock.getId());
            detail.setMaterialCode(stock.getMaterialCode());
            detail.setBatchNo("IMP-" + code);
            detail.setContainerNo(String.format("%s-%03d", code, i));
            detail.setUnit(StringUtils.hasText(stock.getUnit()) ? stock.getUnit().trim() : "桶");
            detail.setPackUom(detail.getUnit());
            detail.setPackCount(1.0);
            detail.setStdUom("kg");
            detail.setStdQtyPerPack(perBucketWeight);
            detail.setWeight(perBucketWeight);
            detail.setInboundDate(now);
            detail.setIsOpened(false);
            detail.setDangerLevel(1);
            detail.setStatus(i <= availableBuckets ? "available" : "locked");
            detail.setRemark("系统按导入桶数自动生成");
            detail.setCreateTime(now);
            detail.setUpdateTime(now);
            chemicalStockDetailMapper.insert(detail);
        }
    }

    private void refreshStockSummaryByDetails(Long chemicalStockId) {
        ChemicalStock stock = chemicalStockMapper.selectById(chemicalStockId);
        if (stock == null) {
            return;
        }

        List<ChemicalStockDetail> details = chemicalStockDetailMapper.selectByChemicalStockId(chemicalStockId);
        double availableCount = 0D;
        double lockedCount = 0D;
        BigDecimal sampleWeightSum = BigDecimal.ZERO;
        int sampleWeightCount = 0;
        double bucketCount = 0D;

        if (details != null) {
            for (ChemicalStockDetail d : details) {
                if (d == null) {
                    continue;
                }
                String status = d.getStatus() == null ? "" : d.getStatus().trim().toLowerCase();
                if ("used".equals(status)) {
                    continue;
                }

                if ("locked".equals(status)) {
                    lockedCount += 1D;
                } else {
                    availableCount += 1D;
                }

                BigDecimal weight = d.getWeight() == null ? BigDecimal.ZERO : d.getWeight();
                if (weight.compareTo(BigDecimal.ZERO) > 0) {
                    sampleWeightSum = sampleWeightSum.add(weight);
                    sampleWeightCount++;
                }
                bucketCount += 1D;
            }
        }

        double availableQty = availableCount;
        double lockedQty = lockedCount;
        double totalQty = availableQty + lockedQty;

        BigDecimal resolvedUnitWeight = null;
        if (sampleWeightCount > 0) {
            resolvedUnitWeight = sampleWeightSum
                    .divide(BigDecimal.valueOf(sampleWeightCount), 2, BigDecimal.ROUND_HALF_UP);
        }

        stock.setAvailableQuantity(availableQty);
        stock.setLockedQuantity(lockedQty);
        stock.setTotalQuantity(totalQty);
        stock.setAvailablePackCount(availableQty);
        stock.setLockedPackCount(lockedQty);
        stock.setTotalPackCount(totalQty);
        stock.setBucketCount(bucketCount);
        if (resolvedUnitWeight != null && resolvedUnitWeight.compareTo(BigDecimal.ZERO) > 0) {
            stock.setUnitWeight(resolvedUnitWeight);
        }

        Double safetyStock = stock.getSafetyStock();
        if (availableQty <= 0) {
            stock.setStatus("out_of_stock");
        } else if (safetyStock != null && safetyStock > 0 && availableQty < safetyStock) {
            stock.setStatus("low_stock");
        } else {
            stock.setStatus("active");
        }

        stock.setUpdateTime(new Date());
        chemicalStockMapper.updateById(stock);
    }

    private String resolveChemicalStockSpecSnapshot(ChemicalStock stock, ChemicalStockDetail detail) {
        if (detail != null && StringUtils.hasText(detail.getRemark())) {
            return detail.getRemark().trim();
        }
        if (detail != null && detail.getWeight() != null && detail.getWeight().compareTo(BigDecimal.ZERO) > 0) {
            String unit = StringUtils.hasText(detail.getUnit()) ? detail.getUnit().trim() : "桶";
            return detail.getWeight().setScale(2, BigDecimal.ROUND_HALF_UP).toPlainString() + "kg/" + unit;
        }
        if (stock != null && stock.getUnitWeight() != null && stock.getUnitWeight().compareTo(BigDecimal.ZERO) > 0) {
            String unit = StringUtils.hasText(stock.getUnit()) ? stock.getUnit().trim() : "桶";
            return stock.getUnitWeight().setScale(2, BigDecimal.ROUND_HALF_UP).toPlainString() + "kg/" + unit;
        }
        return null;
    }

    private String appendRemarkTokenIfMissing(String baseRemark, String key, String value) {
        if (!StringUtils.hasText(key) || !StringUtils.hasText(value)) {
            return baseRemark;
        }
        String base = StringUtils.hasText(baseRemark) ? baseRemark.trim() : "";
        String token = key.trim() + "=" + value.trim();
        if (!StringUtils.hasText(base)) {
            return token;
        }
        if (base.contains(token)) {
            return base;
        }
        return base + ";" + token;
    }
}
