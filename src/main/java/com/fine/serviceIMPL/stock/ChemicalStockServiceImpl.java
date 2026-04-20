package com.fine.serviceIMPL.stock;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fine.Dao.rd.TapeFormulaMapper;
import com.fine.Dao.stock.ChemicalStockMapper;
import com.fine.Dao.stock.ChemicalStockDetailMapper;
import com.fine.Dao.stock.ChemicalStockOutMapper;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
    public IPage<ChemicalStock> getChemicalStockPage(long current, long size, String chemicalType) {
        Page<ChemicalStock> page = new Page<>(current, size);
        QueryWrapper<ChemicalStock> wrapper = new QueryWrapper<>();
        wrapper.eq(StringUtils.hasText(chemicalType), "chemical_type", chemicalType);
        wrapper.orderByDesc("create_time");
        IPage<ChemicalStock> result = chemicalStockMapper.selectPage(page, wrapper);
        fillBucketCount(result.getRecords());
        return result;
    }
    
    @Override
    public ChemicalStock getById(Long id) {
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
        row.setContainerNo(detail.getContainerNo());
        row.setUnit(StringUtils.hasText(detail.getUnit()) ? detail.getUnit().trim() :
                (StringUtils.hasText(stock.getUnit()) ? stock.getUnit().trim() : "桶"));
        row.setWeight(detail.getWeight() != null ? detail.getWeight() : BigDecimal.ZERO);
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
        existed.setContainerNo(detail.getContainerNo());
        if (StringUtils.hasText(detail.getUnit())) {
            existed.setUnit(detail.getUnit().trim());
        }
        existed.setWeight(detail.getWeight() != null ? detail.getWeight() : BigDecimal.ZERO);
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
    public boolean lockStock(Long chemicalStockId, Integer lockQuantity, List<Long> detailIds) {
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
        }
        
        return true;
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean unlockStock(Long chemicalStockId, Integer unlockQuantity, List<Long> detailIds) {
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
            throw new RuntimeException("出库失败，锁定库存不足");
        }
        
        // 2. 更新明细状态为已使用
        if (detailIds != null && !detailIds.isEmpty()) {
            int detailRows = chemicalStockDetailMapper.batchUpdateStatus(detailIds, "used");
            if (detailRows != detailIds.size()) {
                throw new RuntimeException("部分明细更新失败");
            }
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

        if (!StringUtils.hasText(chemicalStockOut.getBatchNo())
            && chemicalStockOut.getChemicalDetailId() != null
            && chemicalStockOut.getChemicalDetailId() > 0) {
            ChemicalStockDetail firstDetail = chemicalStockDetailMapper.selectById(chemicalStockOut.getChemicalDetailId());
            if (firstDetail != null && StringUtils.hasText(firstDetail.getBatchNo())) {
                chemicalStockOut.setBatchNo(firstDetail.getBatchNo());
            }
        }

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
                chemicalStockOut.getScheduleId() != null ? chemicalStockOut.getScheduleId().toString() : "MANUAL_OUT",
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
        Map<String, Object> result = new HashMap<>();
        int successCount = 0;
        int skipCount = 0;
        java.util.List<String> errors = new java.util.ArrayList<>();
        List<Map<String, Object>> skippedData = new ArrayList<>();

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

                    // 保留料号格式（仅去首尾空白）
                    String cleanedMaterialCode = originalMaterialCode.replace("\u00A0", " ").trim();

                    // 物料名称、化工类型统一从原材料表获取
                    TapeRawMaterial rawMaterial = tapeFormulaMapper.selectRawMaterialByCode(cleanedMaterialCode);
                    if (rawMaterial == null || !StringUtils.hasText(rawMaterial.getMaterialName())) {
                        skipCount++;
                        Map<String, Object> skippedRow = new HashMap<>();
                        skippedRow.put("行号", i + 1);
                        skippedRow.put("原始物料编号", originalMaterialCode);
                        skippedRow.put("清理后物料编号", cleanedMaterialCode);
                        skippedRow.put("原因", "原材料表中未找到该料号或物料名称为空");
                        // 添加其他字段用于参考
                        skippedRow.put("物料名称", getCellValue(getCellByHeader(row, headerIndex,
                                new String[]{"物料名称", "material_name", "名称"}, 1)));
                        skippedRow.put("化工类型", getCellValue(getCellByHeader(row, headerIndex,
                                new String[]{"化工类型", "chemical_type", "类型"}, 2)));
                        skippedRow.put("单位", getCellValue(getCellByHeader(row, headerIndex,
                                new String[]{"单位", "unit"}, 3)));
                        skippedData.add(skippedRow);
                        continue;
                    }

                        String materialName = rawMaterial.getMaterialName().trim();

                        String chemicalType = resolveChemicalType(rawMaterial);
                    String unit = getCellValue(getCellByHeader(row, headerIndex,
                            new String[]{"单位", "unit"}, 3));
                    BigDecimal unitWeight = getDecimalCellValue(getCellByHeader(row, headerIndex,
                            new String[]{"单桶重量", "单桶重量(kg)", "unit_weight"}, 4));

                    Integer totalQuantity = defaultInt(getIntCellValue(getCellByHeader(row, headerIndex,
                            new String[]{"总数量", "总重量", "total_quantity", "total_weight"}, 5)));
                    Integer availableQuantity = getIntCellValue(getCellByHeader(row, headerIndex,
                            new String[]{"可用数量", "available_quantity"}, 6));
                        Integer bucketCount = getIntCellValue(getCellByHeader(row, headerIndex,
                            new String[]{"桶数", "总桶数", "bucket_count", "container_count"}, -1));
                    Integer lockedQuantity = getIntCellValue(getCellByHeader(row, headerIndex,
                            new String[]{"锁定数量", "locked_quantity"}, 7));
                    Integer safetyStock = getIntCellValue(getCellByHeader(row, headerIndex,
                            new String[]{"安全库存", "safety_stock"}, 8));
                    String status = getCellValue(getCellByHeader(row, headerIndex,
                            new String[]{"状态", "status"}, 9));
                    String remark = getCellValue(getCellByHeader(row, headerIndex,
                            new String[]{"备注", "remark"}, 10));

                    if (availableQuantity == null) {
                        availableQuantity = totalQuantity;
                    }
                    if (lockedQuantity == null) {
                        lockedQuantity = 0;
                    }
                    if (!StringUtils.hasText(unit)) {
                        unit = StringUtils.hasText(rawMaterial.getUnit()) ? rawMaterial.getUnit().trim() : "Kg";
                    }
                    if (!StringUtils.hasText(status)) {
                        status = "active";
                    }
                    if (bucketCount == null || bucketCount < 0) {
                        bucketCount = inferBucketCount(totalQuantity, unitWeight);
                    }

                    com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<ChemicalStock> qw = new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<>();
                    qw.eq("material_code", cleanedMaterialCode);
                    ChemicalStock exist = chemicalStockMapper.selectOne(qw);

                    ChemicalStock stock = exist == null ? new ChemicalStock() : exist;
                    stock.setMaterialCode(cleanedMaterialCode);
                    stock.setMaterialName(materialName);
                    stock.setChemicalType(chemicalType);
                    stock.setUnit(unit);
                    stock.setUnitWeight(unitWeight);
                    stock.setBucketCount(bucketCount);
                    stock.setTotalQuantity(totalQuantity);
                    stock.setAvailableQuantity(availableQuantity);
                    stock.setLockedQuantity(lockedQuantity);
                    stock.setSafetyStock(safetyStock);
                    stock.setStatus(status);
                    stock.setRemark(remark);
                    stock.setUpdateBy("import");

                    if (exist == null) {
                        stock.setCreateBy("import");
                        chemicalStockMapper.insert(stock);

                        // 记录统一流水 - Excel导入
                        String _unit_imp = (stock.getUnit() != null && !stock.getUnit().isEmpty()) ? stock.getUnit() : "桶";
                        BigDecimal _inQty = stock.getTotalQuantity() != null
                                ? BigDecimal.valueOf(stock.getTotalQuantity())
                                : BigDecimal.ZERO;
                        stockFlowLogService.logStockChange(
                            StockFlowLog.StockType.CHEMICAL.name(),
                            stock.getId(),
                            stock.getMaterialCode(),
                            stock.getMaterialCode(),
                            stock.getMaterialName(),
                            StockFlowLog.OperationType.IN.name(),
                            _inQty,
                            _unit_imp,
                            BigDecimal.ZERO,
                            _inQty,
                            "EXCEL_IMPORT",
                            "SYSTEM",
                            "化学品Excel导入"
                        );
                    } else {
                        chemicalStockMapper.updateById(stock);
                    }

                    rebuildImportDetails(stock, bucketCount, totalQuantity, availableQuantity, lockedQuantity, unitWeight);
                    successCount++;
                } catch (Exception ex) {
                    skipCount++;
                    Map<String, Object> skippedRow = new HashMap<>();
                    skippedRow.put("行号", i + 1);
                    skippedRow.put("原始物料编号", getCellValue(getCellByHeader(row, headerIndex,
                            new String[]{"物料编号", "物料编码", "料号", "material_code"}, 0)));
                    skippedRow.put("清理后物料编号", "");
                    skippedRow.put("原因", "数据格式错误: " + ex.getMessage());
                    skippedData.add(skippedRow);
                }
            }
        } catch (Exception ex) {
            throw new RuntimeException("导入化工库存失败: " + ex.getMessage(), ex);
        }

        // 生成跳过数据的Excel
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
            Integer persistedBucketCount = stock.getBucketCount();
            if (persistedBucketCount != null && persistedBucketCount > 0) {
                stock.setBucketCount(persistedBucketCount);
                continue;
            }
            List<ChemicalStockDetail> details = chemicalStockDetailMapper.selectByChemicalStockId(stock.getId());
            if (details == null || details.isEmpty()) {
                bootstrapDetailsFromSummary(stock);
                details = chemicalStockDetailMapper.selectByChemicalStockId(stock.getId());
            }
            int bucketCount = 0;
            if (details != null) {
                for (ChemicalStockDetail d : details) {
                    if (d == null) {
                        continue;
                    }
                    String status = d.getStatus() == null ? "" : d.getStatus().trim().toLowerCase();
                    if (!"used".equals(status)) {
                        bucketCount++;
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
            ? stock.getBucketCount()
            : inferBucketCount(stock.getTotalQuantity(), stock.getUnitWeight());
        if (targetBucketCount == null || targetBucketCount <= 0) {
            return;
        }

        int availableQty = stock.getAvailableQuantity() == null ? 0 : stock.getAvailableQuantity();
        int lockedQty = stock.getLockedQuantity() == null ? 0 : stock.getLockedQuantity();
        BigDecimal perBucketWeight = stock.getUnitWeight() == null ? BigDecimal.ZERO : stock.getUnitWeight();
        int lockedBuckets = 0;
        if (perBucketWeight.compareTo(BigDecimal.ZERO) > 0 && lockedQty > 0) {
            lockedBuckets = BigDecimal.valueOf(lockedQty).divide(perBucketWeight, 0, BigDecimal.ROUND_HALF_UP).intValue();
        }
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

    private Integer inferBucketCount(Integer totalQuantity, BigDecimal unitWeight) {
        if (totalQuantity == null || totalQuantity <= 0 || unitWeight == null || unitWeight.compareTo(BigDecimal.ZERO) <= 0) {
            return 0;
        }
        return BigDecimal.valueOf(totalQuantity)
                .divide(unitWeight, 0, BigDecimal.ROUND_HALF_UP)
                .intValue();
    }

    private void rebuildImportDetails(ChemicalStock stock,
                                      Integer bucketCount,
                                      Integer totalQuantity,
                                      Integer availableQuantity,
                                      Integer lockedQuantity,
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
            int tq = totalQuantity == null ? 0 : totalQuantity;
            perBucketWeight = count > 0 ? BigDecimal.valueOf(tq).divide(BigDecimal.valueOf(count), 2, BigDecimal.ROUND_HALF_UP) : BigDecimal.ZERO;
        }

        int aq = availableQuantity == null ? 0 : availableQuantity;
        int lq = lockedQuantity == null ? 0 : lockedQuantity;
        int lockedBuckets = 0;
        if (perBucketWeight.compareTo(BigDecimal.ZERO) > 0 && lq > 0) {
            lockedBuckets = BigDecimal.valueOf(lq).divide(perBucketWeight, 0, BigDecimal.ROUND_HALF_UP).intValue();
        }
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
        BigDecimal availableWeight = BigDecimal.ZERO;
        BigDecimal lockedWeight = BigDecimal.ZERO;
        int bucketCount = 0;

        if (details != null) {
            for (ChemicalStockDetail d : details) {
                if (d == null) {
                    continue;
                }
                String status = d.getStatus() == null ? "" : d.getStatus().trim().toLowerCase();
                if ("used".equals(status)) {
                    continue;
                }

                BigDecimal weight = d.getWeight() == null ? BigDecimal.ZERO : d.getWeight();
                if ("locked".equals(status)) {
                    lockedWeight = lockedWeight.add(weight);
                } else {
                    availableWeight = availableWeight.add(weight);
                }
                bucketCount++;
            }
        }

        int availableQty = availableWeight.setScale(0, BigDecimal.ROUND_HALF_UP).intValue();
        int lockedQty = lockedWeight.setScale(0, BigDecimal.ROUND_HALF_UP).intValue();
        int totalQty = availableQty + lockedQty;

        stock.setAvailableQuantity(availableQty);
        stock.setLockedQuantity(lockedQty);
        stock.setTotalQuantity(totalQty);
        stock.setBucketCount(bucketCount);

        Integer safetyStock = stock.getSafetyStock();
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
}
