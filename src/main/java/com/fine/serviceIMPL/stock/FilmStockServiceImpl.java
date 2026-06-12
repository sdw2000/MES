package com.fine.serviceIMPL.stock;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fine.Dao.rd.TapeFormulaMapper;
import com.fine.Dao.stock.FilmStockMapper;
import com.fine.Dao.stock.FilmStockDetailMapper;
import com.fine.Dao.stock.FilmStockOutMapper;
import com.fine.Dao.stock.StockFlowLogMapper;
import com.fine.modle.rd.TapeRawMaterial;
import com.fine.model.stock.FilmStock;
import com.fine.model.stock.FilmStockDetail;
import com.fine.model.stock.FilmStockOut;
import com.fine.service.stock.FilmStockService;
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
import java.util.*;
import java.util.stream.Collectors;

/**
 * 薄膜库存服务实现类
 * @author Fine
 * @date 2026-01-15
 */
@Service
public class FilmStockServiceImpl implements FilmStockService {
    
    @Autowired
    private FilmStockMapper filmStockMapper;
    
    @Autowired
    private FilmStockDetailMapper filmStockDetailMapper;
    
    @Autowired
    private FilmStockOutMapper filmStockOutMapper;

    @Autowired
    private StockFlowLogService stockFlowLogService;

    @Autowired
    private TapeFormulaMapper tapeFormulaMapper;

    @Autowired
    private StockFlowLogMapper stockFlowLogMapper;
    
    @Override
    public List<FilmStock> getBySpec(Integer thickness, Integer width) {
        return filmStockMapper.selectBySpec(thickness, width);
    }
    
    @Override
    public List<FilmStock> getAllFilmStock() {
        QueryWrapper<FilmStock> wrapper = new QueryWrapper<>();
        wrapper.eq("is_deleted", 0);
        wrapper.orderByDesc("create_time");
        return filmStockMapper.selectList(wrapper);
    }

    @Override
    public IPage<FilmStock> getFilmStockPage(long current,
                                             long size,
                                             Integer thickness,
                                             String materialCode,
                                             String sortField,
                                             String sortOrder) {
        Page<FilmStock> page = new Page<>(current, size);
        QueryWrapper<FilmStock> wrapper = new QueryWrapper<>();
        wrapper.eq("is_deleted", 0);
        wrapper.eq(thickness != null, "thickness", thickness);
        if (StringUtils.hasText(materialCode)) {
            wrapper.like("material_code", materialCode.trim());
        }

        String sortColumn = resolveFilmStockSortColumn(sortField);
        boolean asc = "ascending".equalsIgnoreCase(sortOrder) || "asc".equalsIgnoreCase(sortOrder);
        wrapper.orderBy(true, asc, sortColumn);
        wrapper.orderBy(true, asc, "id");
        return filmStockMapper.selectPage(page, wrapper);
    }

    @Override
    public Map<String, Object> getFilmStockStatistics(Integer thickness, String materialCode) {
        QueryWrapper<FilmStock> wrapper = new QueryWrapper<>();
        wrapper.eq("is_deleted", 0);
        wrapper.eq(thickness != null, "thickness", thickness);
        if (StringUtils.hasText(materialCode)) {
            wrapper.like("material_code", materialCode.trim());
        }

        List<FilmStock> list = filmStockMapper.selectList(wrapper);
        BigDecimal totalArea = BigDecimal.ZERO;
        BigDecimal availableArea = BigDecimal.ZERO;
        BigDecimal lockedArea = BigDecimal.ZERO;
        for (FilmStock item : list) {
            if (item == null || isPipeLikeStock(item)) {
                continue;
            }
            totalArea = totalArea.add(item.getTotalArea() == null ? BigDecimal.ZERO : item.getTotalArea());
            availableArea = availableArea.add(item.getAvailableArea() == null ? BigDecimal.ZERO : item.getAvailableArea());
            lockedArea = lockedArea.add(item.getLockedArea() == null ? BigDecimal.ZERO : item.getLockedArea());
        }

        Map<String, Object> result = new HashMap<>();
        result.put("totalTypes", list == null ? 0 : list.size());
        result.put("totalArea", totalArea.setScale(2, BigDecimal.ROUND_HALF_UP));
        result.put("availableArea", availableArea.setScale(2, BigDecimal.ROUND_HALF_UP));
        result.put("lockedArea", lockedArea.setScale(2, BigDecimal.ROUND_HALF_UP));
        return result;
    }

    private boolean isPipeLikeStock(FilmStock stock) {
        if (stock == null) {
            return false;
        }
        String code = stock.getMaterialCode() == null ? "" : stock.getMaterialCode().trim().toUpperCase();
        String name = stock.getMaterialName() == null ? "" : stock.getMaterialName().trim().toUpperCase();
        String spec = stock.getSpecDesc() == null ? "" : stock.getSpecDesc().trim().toUpperCase();
        return code.startsWith("PEG") || name.contains("管") || spec.contains("管");
    }

    private String resolveFilmStockSortColumn(String sortField) {
        if (!StringUtils.hasText(sortField)) {
            return "create_time";
        }
        switch (sortField.trim()) {
            case "materialCode":
                return "material_code";
            case "materialName":
                return "material_name";
            case "thickness":
                return "thickness";
            case "totalArea":
                return "total_area";
            case "availableArea":
                return "available_area";
            case "lockedArea":
                return "locked_area";
            case "totalRolls":
                return "total_rolls";
            case "availableRolls":
                return "available_rolls";
            case "lockedRolls":
                return "locked_rolls";
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
    
    @Override
    public FilmStock getById(Long id) {
        return filmStockMapper.selectById(id);
    }
    
    @Override
    public List<FilmStockDetail> getDetailsByFilmStockId(Long filmStockId) {
        return filmStockDetailMapper.selectByFilmStockId(filmStockId);
    }
    
    @Override
    public List<FilmStockDetail> getAvailableDetails(Long filmStockId) {
        return filmStockDetailMapper.selectByStatus(filmStockId, "available");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FilmStockDetail createDetail(Long filmStockId, FilmStockDetail detail) {
        FilmStock stock = filmStockMapper.selectById(filmStockId);
        if (stock == null) {
            throw new RuntimeException("薄膜库存不存在，ID: " + filmStockId);
        }

        Date now = new Date();
        FilmStockDetail row = new FilmStockDetail();
        row.setFilmStockId(filmStockId);
        row.setMaterialCode(stock.getMaterialCode());
        row.setBatchNo(detail.getBatchNo());
        row.setRollNo(detail.getRollNo());
        row.setThickness(detail.getThickness() != null ? detail.getThickness() : stock.getThickness());
        row.setWidth(detail.getWidth() != null ? detail.getWidth() : stock.getWidth());
        row.setLength(detail.getLength());
        row.setArea(detail.getArea() != null ? detail.getArea() : BigDecimal.ZERO);
        row.setPackUom(StringUtils.hasText(detail.getPackUom()) ? detail.getPackUom().trim() : "卷");
        row.setPackCount(detail.getPackCount() != null && detail.getPackCount() > 0 ? detail.getPackCount() : 1);
        row.setStdUom(StringUtils.hasText(detail.getStdUom()) ? detail.getStdUom().trim() : "㎡");
        row.setStdQtyPerPack(detail.getStdQtyPerPack() != null && detail.getStdQtyPerPack().compareTo(BigDecimal.ZERO) > 0
            ? detail.getStdQtyPerPack()
            : row.getArea());
        row.setQcStatus(StringUtils.hasText(detail.getQcStatus()) ? detail.getQcStatus().trim() : "qualified");
        row.setLocation(detail.getLocation());
        row.setSupplier(detail.getSupplier());
        row.setInboundDate(detail.getInboundDate() != null ? detail.getInboundDate() : now);
        row.setStatus(StringUtils.hasText(detail.getStatus()) ? detail.getStatus().trim() : "available");
        row.setRemark(detail.getRemark());
        row.setCreateBy("manual");
        row.setCreateTime(now);
        row.setUpdateBy("manual");
        row.setUpdateTime(now);
        row.setIsDeleted(0);

        filmStockDetailMapper.insert(row);
        refreshStockSummaryByDetails(filmStockId);
        return filmStockDetailMapper.selectById(row.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FilmStockDetail updateDetail(Long filmStockId, Long detailId, FilmStockDetail detail) {
        FilmStockDetail existed = filmStockDetailMapper.selectById(detailId);
        if (existed == null || existed.getIsDeleted() != null && existed.getIsDeleted() == 1) {
            throw new RuntimeException("库存明细不存在，ID: " + detailId);
        }
        if (!filmStockId.equals(existed.getFilmStockId())) {
            throw new RuntimeException("明细与库存不匹配，无法修改");
        }

        existed.setBatchNo(detail.getBatchNo());
        existed.setRollNo(detail.getRollNo());
        existed.setThickness(detail.getThickness());
        existed.setWidth(detail.getWidth());
        existed.setLength(detail.getLength());
        existed.setArea(detail.getArea() != null ? detail.getArea() : BigDecimal.ZERO);
        existed.setPackUom(StringUtils.hasText(detail.getPackUom()) ? detail.getPackUom().trim()
            : (StringUtils.hasText(existed.getPackUom()) ? existed.getPackUom() : "卷"));
        existed.setPackCount(detail.getPackCount() != null && detail.getPackCount() > 0
            ? detail.getPackCount()
            : (existed.getPackCount() != null && existed.getPackCount() > 0 ? existed.getPackCount() : 1));
        existed.setStdUom(StringUtils.hasText(detail.getStdUom()) ? detail.getStdUom().trim()
            : (StringUtils.hasText(existed.getStdUom()) ? existed.getStdUom() : "㎡"));
        existed.setStdQtyPerPack(detail.getStdQtyPerPack() != null && detail.getStdQtyPerPack().compareTo(BigDecimal.ZERO) > 0
            ? detail.getStdQtyPerPack()
            : existed.getArea());
        existed.setQcStatus(StringUtils.hasText(detail.getQcStatus()) ? detail.getQcStatus().trim() : "pending");
        existed.setLocation(detail.getLocation());
        existed.setSupplier(detail.getSupplier());
        existed.setInboundDate(detail.getInboundDate());
        existed.setStatus(StringUtils.hasText(detail.getStatus()) ? detail.getStatus().trim() : "available");
        existed.setRemark(detail.getRemark());
        existed.setUpdateBy("manual");
        existed.setUpdateTime(new Date());

        filmStockDetailMapper.updateById(existed);
        refreshStockSummaryByDetails(filmStockId);
        return filmStockDetailMapper.selectById(detailId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteDetail(Long filmStockId, Long detailId) {
        FilmStockDetail existed = filmStockDetailMapper.selectById(detailId);
        if (existed == null || existed.getIsDeleted() != null && existed.getIsDeleted() == 1) {
            return false;
        }
        if (!filmStockId.equals(existed.getFilmStockId())) {
            throw new RuntimeException("明细与库存不匹配，无法删除");
        }

        String status = existed.getStatus() == null ? "" : existed.getStatus().trim().toLowerCase();
        if ("locked".equals(status)) {
            throw new RuntimeException("该明细处于锁定状态，禁止删除，请先解锁");
        }
        if ("used".equals(status)) {
            throw new RuntimeException("该明细已使用，禁止删除");
        }

        existed.setIsDeleted(1);
        existed.setUpdateBy("manual");
        existed.setUpdateTime(new Date());
        int rows = filmStockDetailMapper.updateById(existed);
        refreshStockSummaryByDetails(filmStockId);
        return rows > 0;
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean lockStock(Long filmStockId, BigDecimal lockArea, Integer lockRolls, List<Long> detailIds) {
        // 1. 锁定总量表
        int rows = filmStockMapper.lockStock(filmStockId, lockArea, lockRolls);
        if (rows == 0) {
            throw new RuntimeException("库存不足，无法锁定");
        }
        
        // 2. 锁定明细
        if (detailIds != null && !detailIds.isEmpty()) {
            int detailRows = filmStockDetailMapper.batchUpdateStatus(detailIds, "locked");
            if (detailRows != detailIds.size()) {
                throw new RuntimeException("部分明细锁定失败");
            }
        }
        
        return true;
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean unlockStock(Long filmStockId, BigDecimal unlockArea, Integer unlockRolls, List<Long> detailIds) {
        // 1. 解锁总量表
        int rows = filmStockMapper.unlockStock(filmStockId, unlockArea, unlockRolls);
        if (rows == 0) {
            throw new RuntimeException("解锁失败，锁定库存不足");
        }
        
        // 2. 解锁明细
        if (detailIds != null && !detailIds.isEmpty()) {
            int detailRows = filmStockDetailMapper.batchUpdateStatus(detailIds, "available");
            if (detailRows != detailIds.size()) {
                throw new RuntimeException("部分明细解锁失败");
            }
        }
        
        return true;
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean outbound(FilmStockOut filmStockOut, List<Long> detailIds) {
        // 1. 扣减总量
        int rows = filmStockMapper.deductStock(
            filmStockOut.getFilmStockId(), 
            filmStockOut.getOutArea(), 
            filmStockOut.getOutRolls()
        );
        if (rows == 0) {
            throw new RuntimeException("出库失败，可用库存和锁定库存都不足");
        }
        
        // 2. 更新明细状态为已使用
        if (detailIds != null && !detailIds.isEmpty()) {
            int detailRows = filmStockDetailMapper.batchUpdateStatus(detailIds, "used");
            if (detailRows != detailIds.size()) {
                throw new RuntimeException("部分明细更新失败");
            }
        }
        
        // 3. 创建出库记录
        Date now = new Date();
        filmStockOut.setCreateTime(now);
        if (filmStockOut.getOutboundTime() == null) {
            filmStockOut.setOutboundTime(now);
        }

        FilmStock stock = filmStockMapper.selectById(filmStockOut.getFilmStockId());
        if (stock != null) {
            if (!StringUtils.hasText(filmStockOut.getMaterialCode())) {
                filmStockOut.setMaterialCode(stock.getMaterialCode());
            }
        }

        if (!StringUtils.hasText(filmStockOut.getCreateBy())) {
            filmStockOut.setCreateBy(StringUtils.hasText(filmStockOut.getOutboundBy())
                ? filmStockOut.getOutboundBy()
                : "SYSTEM");
        }

        if (!StringUtils.hasText(filmStockOut.getOutboundNo())) {
            filmStockOut.setOutboundNo("FMOUT" + new java.text.SimpleDateFormat("yyMMddHHmmss").format(now)
                + "-" + filmStockOut.getFilmStockId());
        }

        if (filmStockOut.getFilmDetailId() == null) {
            if (detailIds != null && !detailIds.isEmpty()) {
                filmStockOut.setFilmDetailId(detailIds.get(0));
            } else {
                filmStockOut.setFilmDetailId(0L);
            }
        }

        if (!StringUtils.hasText(filmStockOut.getBatchNo())
            && filmStockOut.getFilmDetailId() != null
            && filmStockOut.getFilmDetailId() > 0) {
            FilmStockDetail firstDetail = filmStockDetailMapper.selectById(filmStockOut.getFilmDetailId());
            if (firstDetail != null) {
                if (StringUtils.hasText(firstDetail.getBatchNo())) {
                    filmStockOut.setBatchNo(firstDetail.getBatchNo());
                }
                if (StringUtils.hasText(firstDetail.getRollNo())) {
                    filmStockOut.setRollNo(firstDetail.getRollNo());
                }
            }
        }

        if (!StringUtils.hasText(filmStockOut.getBatchNo())) {
            filmStockOut.setBatchNo((stock != null && StringUtils.hasText(stock.getMaterialCode()))
                ? stock.getMaterialCode()
                : ("FILM-" + filmStockOut.getFilmStockId()));
        }

        filmStockOutMapper.insert(filmStockOut);

        // 记录统一流水
        if (stock != null) {
            BigDecimal _outArea = filmStockOut.getOutArea() != null ? filmStockOut.getOutArea() : BigDecimal.ZERO;
            BigDecimal _total = stock.getTotalArea() != null ? stock.getTotalArea() : BigDecimal.ZERO;
            BigDecimal _before = _total.add(_outArea);
            BigDecimal _after = _total;
            String _unit = "㎡";
            stockFlowLogService.logStockChange(
                StockFlowLog.StockType.FILM.name(),
                stock.getId(),
                stock.getMaterialCode(), // 薄膜没有批次号，使用物料编码
                stock.getMaterialCode(),
                stock.getMaterialName(),
                StockFlowLog.OperationType.OUT.name(),
                _outArea,
                _unit,
                _before, // before
                _after, // after
                StringUtils.hasText(filmStockOut.getOutboundNo())
                    ? filmStockOut.getOutboundNo()
                    : (filmStockOut.getScheduleId() != null ? filmStockOut.getScheduleId().toString() : "MANUAL_OUT"),
                filmStockOut.getOutboundBy() != null ? filmStockOut.getOutboundBy() : "SYSTEM",
                "薄膜出库"
            );
        }
        
        return true;
    }
    
    @Override
    public List<FilmStockOut> getOutboundByScheduleId(Long scheduleId) {
        return filmStockOutMapper.selectByScheduleId(scheduleId);
    }
    
    @Override
    public List<Map<String, Object>> getAvailableWidths(Integer thickness) {
        QueryWrapper<FilmStock> wrapper = new QueryWrapper<>();
        
        // 只查询有可用库存的
        wrapper.eq("is_deleted", 0);
        wrapper.gt("available_area", 0);
        wrapper.gt("available_rolls", 0);
        
        // 如果指定厚度，按厚度筛选
        if (thickness != null) {
            wrapper.eq("thickness", thickness);
        }
        
        wrapper.orderByAsc("width");
        List<FilmStock> stocks = filmStockMapper.selectList(wrapper);
        
        // 按宽度分组汇总
        Map<Integer, List<FilmStock>> widthGroupMap = stocks.stream()
                .filter(s -> s.getWidth() != null)
                .collect(Collectors.groupingBy(FilmStock::getWidth));
        
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map.Entry<Integer, List<FilmStock>> entry : widthGroupMap.entrySet()) {
            Integer width = entry.getKey();
            List<FilmStock> list = entry.getValue();
            
            // 汇总该宽度的总可用面积和卷数
            BigDecimal totalAvailableArea = list.stream()
                    .map(FilmStock::getAvailableArea)
                    .filter(Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            
            double totalAvailableRolls = list.stream()
                    .map(FilmStock::getAvailableRolls)
                    .filter(Objects::nonNull)
                    .mapToDouble(Double::doubleValue)
                    .sum();
            
            // 获取该宽度下的不同厚度
            List<BigDecimal> thicknessList = list.stream()
                    .map(FilmStock::getThickness)
                    .filter(Objects::nonNull)
                    .distinct()
                    .sorted()
                    .collect(Collectors.toList());
            
            Map<String, Object> widthInfo = new HashMap<>();
            widthInfo.put("width", width);
            widthInfo.put("availableArea", totalAvailableArea);
            widthInfo.put("availableRolls", totalAvailableRolls);
            widthInfo.put("thicknessList", thicknessList);
            widthInfo.put("label", width + "mm (可用: " + totalAvailableArea + "㎡, " + totalAvailableRolls + "卷)");
            
            result.add(widthInfo);
        }
        
        // 按宽度排序
        result.sort((a, b) -> {
            Integer w1 = (Integer) a.get("width");
            Integer w2 = (Integer) b.get("width");
            return w1.compareTo(w2);
        });
        
        return result;
    }
    
    @Override
    public Map<String, Object> getStockDetailBySpec(Integer width, Integer thickness) {
        QueryWrapper<FilmStock> wrapper = new QueryWrapper<>();
        wrapper.eq("width", width);
        wrapper.eq("is_deleted", 0);
        if (thickness != null) {
            wrapper.eq("thickness", thickness);
        }
        wrapper.gt("available_area", 0);
        
        List<FilmStock> stocks = filmStockMapper.selectList(wrapper);
        
        if (stocks.isEmpty()) {
            Map<String, Object> empty = new HashMap<>();
            empty.put("width", width);
            empty.put("thickness", thickness);
            empty.put("availableArea", BigDecimal.ZERO);
            empty.put("availableRolls", 0);
            empty.put("stocks", new ArrayList<>());
            return empty;
        }
        
        // 汇总可用库存
        BigDecimal totalAvailableArea = stocks.stream()
                .map(FilmStock::getAvailableArea)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        double totalAvailableRolls = stocks.stream()
                .map(FilmStock::getAvailableRolls)
                .filter(Objects::nonNull)
                .mapToDouble(Double::doubleValue)
                .sum();
        
        Map<String, Object> detail = new HashMap<>();
        detail.put("width", width);
        detail.put("thickness", thickness);
        detail.put("availableArea", totalAvailableArea);
        detail.put("availableRolls", totalAvailableRolls);
        detail.put("stocks", stocks);
        
        return detail;
    }
    
    @Override
    public boolean checkStockAvailability(Integer width, Integer thickness, Double requiredArea) {
        Map<String, Object> detail = getStockDetailBySpec(width, thickness);
        BigDecimal availableArea = (BigDecimal) detail.get("availableArea");
        
        if (availableArea == null) {
            return false;
        }
        
        return availableArea.compareTo(new BigDecimal(requiredArea)) >= 0;
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
                Map<String, Object> result = new HashMap<>();
                int successCount = 0;
                int skipCount = 0;
                List<String> errors = new ArrayList<>();
                List<Map<String, Object>> skippedData = new ArrayList<>();
                Map<String, TapeRawMaterial> normalizedRawMaterialMap = buildNormalizedRawMaterialMap();

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

                    if (isDetailImportMode(headerIndex)) {
                        return importExcelByDetails(sheet, headerIndex);
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

                            // 按原材料代码表入库：支持常见格式差异（空白/全角横杠等）
                            String cleanedMaterialCode = cleanMaterialCode(originalMaterialCode);

                            // 从原材料代码表获取标准物料
                            TapeRawMaterial rawMaterial = resolveRawMaterialByCode(cleanedMaterialCode, normalizedRawMaterialMap);
                            if (rawMaterial == null || !StringUtils.hasText(rawMaterial.getMaterialName())) {
                                skipCount++;
                                Map<String, Object> skippedRow = new HashMap<>();
                                skippedRow.put("行号", i + 1);
                                skippedRow.put("原始物料编号", originalMaterialCode);
                                skippedRow.put("清理后物料编号", cleanedMaterialCode);
                                skippedRow.put("原因", "原材料代码表中未找到对应物料名称");
                                // 添加其他字段用于参考
                                skippedRow.put("物料名称", getCellValue(getCellByHeader(row, headerIndex,
                                        new String[]{"物料名称", "material_name", "名称"}, 1)));
                                skippedRow.put("厚度", getCellValue(getCellByHeader(row, headerIndex,
                                        new String[]{"厚度", "厚度(μm)", "thickness"}, 2)));
                                skippedRow.put("宽度", getCellValue(getCellByHeader(row, headerIndex,
                                        new String[]{"宽度", "宽度(mm)", "width"}, 3)));
                                skippedData.add(skippedRow);
                                continue;
                            }

                            String materialName = rawMaterial.getMaterialName().trim();
                            cleanedMaterialCode = cleanMaterialCode(rawMaterial.getMaterialCode());

                            BigDecimal thickness = getDecimalCellValue(getCellByHeader(row, headerIndex,
                                    new String[]{"厚度", "厚度(μm)", "thickness"}, 2));
                            Integer width = getIntCellValue(getCellByHeader(row, headerIndex,
                                    new String[]{"宽度", "宽度(mm)", "width"}, 3));
                            String specDesc = getCellValue(getCellByHeader(row, headerIndex,
                                    new String[]{"规格描述", "spec_desc"}, 4));
                                specDesc = normalizeFilmSpecDesc(specDesc, thickness, width);

                            BigDecimal totalArea = defaultDecimal(getDecimalCellValue(getCellByHeader(row, headerIndex,
                                    new String[]{"总面积", "总面积(㎡)", "total_area"}, 5)));
                            BigDecimal availableArea = getDecimalCellValue(getCellByHeader(row, headerIndex,
                                    new String[]{"可用面积", "可用面积(㎡)", "available_area"}, 6));
                            BigDecimal lockedArea = getDecimalCellValue(getCellByHeader(row, headerIndex,
                                    new String[]{"锁定面积", "锁定面积(㎡)", "locked_area"}, 7));

                            Double totalRolls = defaultDouble(getDoubleCellValue(getCellByHeader(row, headerIndex,
                                    new String[]{"总卷数", "total_rolls"}, 8)));
                            Double availableRolls = getDoubleCellValue(getCellByHeader(row, headerIndex,
                                    new String[]{"可用卷数", "available_rolls"}, 9));
                            Double lockedRolls = getDoubleCellValue(getCellByHeader(row, headerIndex,
                                    new String[]{"锁定卷数", "locked_rolls"}, 10));

                            BigDecimal safetyStock = getDecimalCellValue(getCellByHeader(row, headerIndex,
                                    new String[]{"安全库存", "安全库存(㎡)", "safety_stock"}, 11));
                            String status = getCellValue(getCellByHeader(row, headerIndex,
                                    new String[]{"状态", "status"}, 12));
                            String remark = getCellValue(getCellByHeader(row, headerIndex,
                                    new String[]{"备注", "remark"}, 13));

                            if (availableArea == null) {
                                availableArea = totalArea;
                            }
                            if (lockedArea == null) {
                                lockedArea = BigDecimal.ZERO;
                            }
                            if (availableRolls == null) {
                                availableRolls = totalRolls;
                            }
                            if (lockedRolls == null) {
                                lockedRolls = 0.0;
                            }
                            if (!StringUtils.hasText(status)) {
                                status = "active";
                            }
                            status = normalizeStockStatus(status);

                            QueryWrapper<FilmStock> qw = new QueryWrapper<>();
                            qw.eq("material_code", cleanedMaterialCode);
                            qw.orderByAsc("id");
                            List<FilmStock> existedStocks = filmStockMapper.selectList(qw);
                            FilmStock exist = null;
                            if (existedStocks != null && !existedStocks.isEmpty()) {
                                exist = existedStocks.get(0);
                                if (existedStocks.size() > 1) {
                                    List<Long> duplicateStockIds = new ArrayList<>();
                                    for (int idx = 1; idx < existedStocks.size(); idx++) {
                                        FilmStock duplicate = existedStocks.get(idx);
                                        if (duplicate != null && duplicate.getId() != null) {
                                            duplicateStockIds.add(duplicate.getId());
                                        }
                                    }
                                    if (!duplicateStockIds.isEmpty()) {
                                        QueryWrapper<FilmStockDetail> dupDetailQw = new QueryWrapper<>();
                                        dupDetailQw.in("stock_id", duplicateStockIds);
                                        filmStockDetailMapper.delete(dupDetailQw);

                                        QueryWrapper<FilmStockOut> dupOutQw = new QueryWrapper<>();
                                        dupOutQw.in("stock_id", duplicateStockIds);
                                        filmStockOutMapper.delete(dupOutQw);

                                        QueryWrapper<FilmStock> dupStockQw = new QueryWrapper<>();
                                        dupStockQw.in("id", duplicateStockIds);
                                        filmStockMapper.delete(dupStockQw);

                                        errors.add("导入前自动清理重复薄膜主档 " + duplicateStockIds.size() + " 条（料号=" + cleanedMaterialCode + "）");
                                    }
                                }
                            }

                            FilmStock stock = exist == null ? new FilmStock() : exist;
                            stock.setMaterialCode(cleanedMaterialCode);
                            stock.setMaterialName(materialName);
                            if (exist == null) {
                                stock.setThickness(thickness);
                                stock.setWidth(width);
                                stock.setSpecDesc(StringUtils.hasText(specDesc) ? specDesc.trim() : null);
                            } else {
                                // 同料号多行导入时，后续空值不覆盖历史有效规格，避免“规格被清空”
                                if (thickness != null && thickness.compareTo(BigDecimal.ZERO) > 0) {
                                    stock.setThickness(thickness);
                                }
                                if (width != null && width > 0) {
                                    stock.setWidth(width);
                                }
                                if (StringUtils.hasText(specDesc)) {
                                    stock.setSpecDesc(specDesc.trim());
                                }
                            }
                            if (exist == null) {
                                stock.setTotalArea(totalArea);
                                stock.setAvailableArea(availableArea);
                                stock.setLockedArea(lockedArea);
                                stock.setTotalRolls(totalRolls);
                                stock.setAvailableRolls(availableRolls);
                                stock.setLockedRolls(lockedRolls);
                            } else {
                                stock.setTotalArea(nz(exist.getTotalArea()).add(nz(totalArea)));
                                stock.setAvailableArea(nz(exist.getAvailableArea()).add(nz(availableArea)));
                                stock.setLockedArea(nz(exist.getLockedArea()).add(nz(lockedArea)));
                                stock.setTotalRolls(nzd(exist.getTotalRolls()) + nzd(totalRolls));
                                stock.setAvailableRolls(nzd(exist.getAvailableRolls()) + nzd(availableRolls));
                                stock.setLockedRolls(nzd(exist.getLockedRolls()) + nzd(lockedRolls));
                            }
                            stock.setSafetyStock(safetyStock);
                            stock.setStatus(status);
                            stock.setRemark(remark);
                            stock.setUpdateBy("import");
                            if (exist == null) {
                                stock.setCreateBy("import");
                                filmStockMapper.insert(stock);

                                // 记录统一流水 - Excel导入
                                BigDecimal _totArea = stock.getTotalArea() != null ? stock.getTotalArea() : BigDecimal.ZERO;
                                stockFlowLogService.logStockChange(
                                    StockFlowLog.StockType.FILM.name(),
                                    stock.getId(),
                                    stock.getMaterialCode(), // 薄膜没有批次号，使用物料编码
                                    stock.getMaterialCode(),
                                    stock.getMaterialName(),
                                    StockFlowLog.OperationType.IN.name(),
                                    _totArea,
                                    "㎡",
                                    BigDecimal.ZERO,
                                    _totArea,
                                    "EXCEL_IMPORT",
                                    "SYSTEM",
                                    "薄膜Excel导入"
                                );
                            } else {
                                filmStockMapper.updateById(stock);
                            }

                            // 导入时按卷数拆分生成明细，保留规格与卷信息
                            appendImportDetailsByRoll(stock, totalArea, totalRolls == null ? null : totalRolls.intValue(), width, thickness, "import-rolls");
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
                    throw new RuntimeException("导入薄膜库存失败: " + ex.getMessage(), ex);
                }

                // 生成跳过数据的Excel
                byte[] skippedExcel = null;
                if (!skippedData.isEmpty()) {
                    skippedExcel = generateSkippedDataExcel(skippedData);
                    errors.addAll(buildSkipReasonSummary(skippedData, 10));
                }

                result.put("success", skipCount == 0);
                result.put("successCount", successCount);
                result.put("skipCount", skipCount);
                result.put("errors", errors);
                result.put("skippedExcel", skippedExcel != null ? java.util.Base64.getEncoder().encodeToString(skippedExcel) : null);
                return result;
            }

            @Override
            @Transactional(rollbackFor = Exception.class)
            public Map<String, Object> clearForReimport(boolean clearOutboundRecords) {
                Map<String, Object> result = new HashMap<>();

                int detailDeleted = filmStockDetailMapper.delete(new QueryWrapper<>());

                int outboundDeleted = 0;
                if (clearOutboundRecords) {
                    outboundDeleted = filmStockOutMapper.delete(new QueryWrapper<>());
                }

                int stockDeleted = filmStockMapper.delete(new QueryWrapper<>());

                if (clearOutboundRecords) {
                    QueryWrapper<StockFlowLog> flowQw = new QueryWrapper<>();
                    flowQw.eq("stock_type", "FILM");
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
                return containsHeader(headerIndex, "卷号", "roll_no", "rollNo")
                        || containsHeader(headerIndex, "长度(m)", "长度", "length")
                        || containsHeader(headerIndex, "批次号", "batch_no", "batchNo");
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
                Map<String, TapeRawMaterial> normalizedRawMaterialMap = buildNormalizedRawMaterialMap();

                List<Map<String, Object>> parsedRows = new ArrayList<>();
                Set<String> materialCodes = new LinkedHashSet<>();

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
                            throw new RuntimeException("原材料代码表中未找到该料号");
                        }
                        materialCode = cleanMaterialCode(rawMaterial.getMaterialCode());

                        String batchNo = getCellValue(getCellByHeader(row, headerIndex,
                                new String[]{"批次号", "batch_no", "batchNo"}, 1));
                        String rollNo = getCellValue(getCellByHeader(row, headerIndex,
                                new String[]{"卷号", "roll_no", "rollNo"}, 2));
                        BigDecimal thickness = getDecimalCellValue(getCellByHeader(row, headerIndex,
                                new String[]{"厚度", "厚度(μm)", "thickness"}, 3));
                        Integer width = getIntCellValue(getCellByHeader(row, headerIndex,
                                new String[]{"宽度", "宽度(mm)", "width"}, 4));
                        Integer length = getIntCellValue(getCellByHeader(row, headerIndex,
                                new String[]{"长度", "长度(m)", "length"}, 5));
                        BigDecimal area = getDecimalCellValue(getCellByHeader(row, headerIndex,
                                new String[]{"面积", "面积(㎡)", "area"}, 6));
                        String packUom = getCellValue(getCellByHeader(row, headerIndex,
                            new String[]{"包装单位", "pack_uom", "packUom"}, 7));
                        Integer packCount = getIntCellValue(getCellByHeader(row, headerIndex,
                            new String[]{"包装数量", "pack_count", "packCount"}, 8));
                        String stdUom = getCellValue(getCellByHeader(row, headerIndex,
                            new String[]{"标准单位", "std_uom", "stdUom"}, 9));
                        BigDecimal stdQtyPerPack = getDecimalCellValue(getCellByHeader(row, headerIndex,
                            new String[]{"每包装标准量", "std_qty_per_pack", "stdQtyPerPack"}, 10));
                        String qcStatus = getCellValue(getCellByHeader(row, headerIndex,
                            new String[]{"质检状态", "quality_status", "qcStatus"}, 11));
                        String location = getCellValue(getCellByHeader(row, headerIndex,
                            new String[]{"库位", "location"}, 12));
                        String supplier = getCellValue(getCellByHeader(row, headerIndex,
                            new String[]{"供应商", "supplier"}, 13));
                        String inboundDateText = getCellValue(getCellByHeader(row, headerIndex,
                            new String[]{"入库日期", "storage_date", "inboundDate"}, 14));
                        String status = getCellValue(getCellByHeader(row, headerIndex,
                            new String[]{"状态", "status"}, 15));
                        String remark = getCellValue(getCellByHeader(row, headerIndex,
                            new String[]{"备注", "remark"}, 16));

                        if (area == null && width != null && width > 0 && length != null && length > 0) {
                            area = BigDecimal.valueOf(width)
                                    .divide(BigDecimal.valueOf(1000), 6, BigDecimal.ROUND_HALF_UP)
                                    .multiply(BigDecimal.valueOf(length))
                                    .setScale(2, BigDecimal.ROUND_HALF_UP);
                        }
                        if (area == null) {
                            area = BigDecimal.ZERO;
                        }

                        if (!StringUtils.hasText(batchNo)) {
                            batchNo = "IMP-" + materialCode;
                        }
                        if (!StringUtils.hasText(rollNo)) {
                            rollNo = "IMP-" + materialCode + "-" + (i + 1);
                        }
                        // 允许文件内出现重复卷号：在真正入库时通过 ensureUniqueRollNo() 自动改写为全局唯一卷号

                        Date inboundDate = parseDateCell(inboundDateText);

                        Map<String, Object> parsed = new HashMap<>();
                        parsed.put("materialCode", materialCode);
                        parsed.put("materialName", rawMaterial.getMaterialName().trim());
                        parsed.put("batchNo", batchNo.trim());
                        parsed.put("rollNo", rollNo.trim());
                        parsed.put("thickness", thickness);
                        parsed.put("width", width);
                        parsed.put("length", length);
                        parsed.put("area", area);
                        parsed.put("packUom", StringUtils.hasText(packUom) ? packUom.trim() : "卷");
                        parsed.put("packCount", packCount != null && packCount > 0 ? packCount : 1);
                        parsed.put("stdUom", StringUtils.hasText(stdUom) ? stdUom.trim() : "㎡");
                        parsed.put("stdQtyPerPack", stdQtyPerPack != null && stdQtyPerPack.compareTo(BigDecimal.ZERO) > 0 ? stdQtyPerPack : area);
                        parsed.put("qcStatus", StringUtils.hasText(qcStatus) ? qcStatus.trim() : "qualified");
                        parsed.put("location", location);
                        parsed.put("supplier", supplier);
                        parsed.put("inboundDate", inboundDate == null ? new Date() : inboundDate);
                        parsed.put("status", normalizeDetailStatus(status));
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
                    Map<String, FilmStock> stockByCode = new HashMap<>();
                    int mergedDuplicateStockCount = 0;
                    for (String materialCode : materialCodes) {
                        QueryWrapper<FilmStock> qw = new QueryWrapper<>();
                        qw.eq("material_code", materialCode);
                        qw.orderByAsc("id");
                        List<FilmStock> existedStocks = filmStockMapper.selectList(qw);
                        FilmStock stock = null;
                        if (existedStocks != null && !existedStocks.isEmpty()) {
                            stock = existedStocks.get(0);
                            if (existedStocks.size() > 1) {
                                List<Long> duplicateStockIds = new ArrayList<>();
                                for (int idx = 1; idx < existedStocks.size(); idx++) {
                                    FilmStock duplicate = existedStocks.get(idx);
                                    if (duplicate != null && duplicate.getId() != null) {
                                        duplicateStockIds.add(duplicate.getId());
                                    }
                                }
                                if (!duplicateStockIds.isEmpty()) {
                                    QueryWrapper<FilmStockDetail> dupDetailQw = new QueryWrapper<>();
                                    dupDetailQw.in("stock_id", duplicateStockIds);
                                    filmStockDetailMapper.delete(dupDetailQw);

                                    QueryWrapper<FilmStockOut> dupOutQw = new QueryWrapper<>();
                                    dupOutQw.in("stock_id", duplicateStockIds);
                                    filmStockOutMapper.delete(dupOutQw);

                                    QueryWrapper<FilmStock> dupStockQw = new QueryWrapper<>();
                                    dupStockQw.in("id", duplicateStockIds);
                                    filmStockMapper.delete(dupStockQw);

                                    mergedDuplicateStockCount += duplicateStockIds.size();
                                }
                            }
                        }
                        if (stock == null) {
                            FilmStock created = new FilmStock();
                            created.setMaterialCode(materialCode);
                            created.setMaterialName(findMaterialName(parsedRows, materialCode));
                            created.setStatus("active");
                            created.setCreateBy("import");
                            created.setUpdateBy("import");
                            filmStockMapper.insert(created);
                            stock = created;
                        }
                        stockByCode.put(materialCode, stock);
                    }

                    List<Long> stockIds = new ArrayList<>();
                    for (FilmStock stock : stockByCode.values()) {
                        stockIds.add(stock.getId());
                    }
                    if (!stockIds.isEmpty()) {
                        QueryWrapper<FilmStockDetail> delQw = new QueryWrapper<>();
                        delQw.in("stock_id", stockIds);
                        filmStockDetailMapper.delete(delQw);
                    }

                    Set<String> reservedRollNos = new HashSet<>();
                    int rollSeq = 1;

                    for (Map<String, Object> parsed : parsedRows) {
                        String materialCode = String.valueOf(parsed.get("materialCode"));
                        FilmStock stock = stockByCode.get(materialCode);
                        if (stock == null || stock.getId() == null) {
                            continue;
                        }

                        FilmStockDetail detail = new FilmStockDetail();
                        detail.setFilmStockId(stock.getId());
                        detail.setMaterialCode(materialCode);
                        detail.setBatchNo((String) parsed.get("batchNo"));
                        String incomingRollNo = (String) parsed.get("rollNo");
                        detail.setRollNo(ensureUniqueRollNo(incomingRollNo, stock.getId(), rollSeq++, reservedRollNos));
                        detail.setThickness((BigDecimal) parsed.get("thickness"));
                        detail.setWidth((Integer) parsed.get("width"));
                        detail.setLength((Integer) parsed.get("length"));
                        detail.setArea((BigDecimal) parsed.get("area"));
                        detail.setPackUom((String) parsed.get("packUom"));
                        Object packCountObj = parsed.get("packCount");
                        detail.setPackCount(packCountObj instanceof Number ? ((Number) packCountObj).doubleValue() : null);
                        detail.setStdUom((String) parsed.get("stdUom"));
                        detail.setStdQtyPerPack((BigDecimal) parsed.get("stdQtyPerPack"));
                        detail.setQcStatus((String) parsed.get("qcStatus"));
                        detail.setLocation((String) parsed.get("location"));
                        detail.setSupplier((String) parsed.get("supplier"));
                        detail.setInboundDate((Date) parsed.get("inboundDate"));
                        detail.setStatus((String) parsed.get("status"));
                        detail.setRemark((String) parsed.get("remark"));
                        detail.setCreateBy("import");
                        detail.setCreateTime(new Date());
                        detail.setUpdateBy("import");
                        detail.setUpdateTime(new Date());
                        detail.setIsDeleted(0);
                        filmStockDetailMapper.insert(detail);
                        successCount++;
                    }

                    for (FilmStock stock : stockByCode.values()) {
                        refreshStockSummaryByDetails(stock.getId());
                    }
                    if (mergedDuplicateStockCount > 0) {
                        errors.add("导入前自动清理重复薄膜主档 " + mergedDuplicateStockCount + " 条（按料号保留最早一条）");
                    }
                }

                byte[] skippedExcel = null;
                if (!skippedData.isEmpty()) {
                    skippedExcel = generateSkippedDataExcel(skippedData);
                    errors.addAll(buildSkipReasonSummary(skippedData, 10));
                }

                result.put("success", skipCount == 0);
                result.put("successCount", successCount);
                result.put("skipCount", skipCount);
                result.put("errors", errors);
                result.put("skippedExcel", skippedExcel != null ? java.util.Base64.getEncoder().encodeToString(skippedExcel) : null);
                return result;
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

            private String ensureUniqueRollNo(String preferredRollNo,
                                              Long stockId,
                                              int sequence,
                                              Set<String> reservedRollNos) {
                String base = StringUtils.hasText(preferredRollNo)
                        ? preferredRollNo.trim()
                        : String.format("IMP-%d-%03d", stockId, sequence);

                String candidate = base;
                int suffix = 1;
                while (isRollNoUsed(candidate, reservedRollNos)) {
                    candidate = base + "-" + suffix;
                    suffix++;
                }

                reservedRollNos.add(candidate.toLowerCase(Locale.ROOT));
                return candidate;
            }

            private boolean isRollNoUsed(String rollNo, Set<String> reservedRollNos) {
                if (!StringUtils.hasText(rollNo)) {
                    return true;
                }
                String normalized = rollNo.trim();
                String key = normalized.toLowerCase(Locale.ROOT);
                if (reservedRollNos.contains(key)) {
                    return true;
                }
                FilmStockDetail existed = filmStockDetailMapper.selectOneByRollNoIncludingDeleted(normalized);
                return existed != null;
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
                String normalized = cleaned
                        .replaceAll("[‐‑‒–—―−－]", "-")
                    .replaceAll("[：﹕∶]", ":")
                    .replaceAll("[／]", "/")
                        .replaceAll("\\s+", "")
                        .toUpperCase(Locale.ROOT);
                return normalized;
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

            private String normalizeDetailStatus(String rawStatus) {
                if (!StringUtils.hasText(rawStatus)) {
                    return "available";
                }
                String s = rawStatus.trim().toLowerCase(Locale.ROOT);
                if ("available".equals(s)
                        || "可用".equals(s)
                        || "可使用".equals(s)
                        || "可领用".equals(s)
                        || "在库".equals(s)) {
                    return "available";
                }
                if ("locked".equals(s)
                        || "锁定".equals(s)
                        || "已锁定".equals(s)) {
                    return "locked";
                }
                if ("used".equals(s)
                        || "已使用".equals(s)
                        || "用完".equals(s)
                        || "用完了".equals(s)
                        || "已用完".equals(s)
                        || "耗尽".equals(s)
                        || "无库存".equals(s)) {
                    return "used";
                }
                return "available";
            }

            private String normalizeStockStatus(String rawStatus) {
                if (!StringUtils.hasText(rawStatus)) {
                    return "active";
                }
                String s = rawStatus.trim().toLowerCase(Locale.ROOT);
                if ("active".equals(s)
                        || "可用".equals(s)
                        || "可使用".equals(s)
                        || "在库".equals(s)
                        || "正常".equals(s)) {
                    return "active";
                }
                if ("low_stock".equals(s)
                        || "库存不足".equals(s)
                        || "低库存".equals(s)
                        || "不足".equals(s)) {
                    return "low_stock";
                }
                if ("out_of_stock".equals(s)
                        || "用完".equals(s)
                        || "用完了".equals(s)
                        || "已用完".equals(s)
                        || "无库存".equals(s)
                        || "耗尽".equals(s)) {
                    return "out_of_stock";
                }
                return "active";
            }

            private List<String> buildSkipReasonSummary(List<Map<String, Object>> skippedData, int limit) {
                List<String> summary = new ArrayList<>();
                if (skippedData == null || skippedData.isEmpty()) {
                    return summary;
                }
                Map<String, Integer> reasonCount = new LinkedHashMap<>();
                for (Map<String, Object> row : skippedData) {
                    if (row == null) {
                        continue;
                    }
                    Object reasonObj = row.get("原因");
                    String reason = reasonObj == null ? "未知原因" : String.valueOf(reasonObj).trim();
                    if (!StringUtils.hasText(reason)) {
                        reason = "未知原因";
                    }
                    reasonCount.put(reason, reasonCount.getOrDefault(reason, 0) + 1);
                }

                summary.add("本次导入跳过 " + skippedData.size() + " 条，原因汇总如下：");
                int idx = 0;
                for (Map.Entry<String, Integer> entry : reasonCount.entrySet()) {
                    summary.add("- " + entry.getKey() + "（" + entry.getValue() + "条）");
                    idx++;
                    if (idx >= limit) {
                        break;
                    }
                }
                if (reasonCount.size() > limit) {
                    summary.add("- 其余 " + (reasonCount.size() - limit) + " 类原因请下载跳过数据Excel查看");
                }
                return summary;
            }

            private void appendImportDetailsByRoll(FilmStock stock,
                                                   BigDecimal rowArea,
                                                   Integer rowRolls,
                                                   Integer width,
                                                   BigDecimal thickness,
                                                   String source) {
                if (stock == null || stock.getId() == null) {
                    return;
                }

                int rolls = rowRolls == null || rowRolls <= 0 ? 1 : rowRolls;
                BigDecimal total = rowArea == null ? BigDecimal.ZERO : rowArea;
                BigDecimal perRoll = rolls > 0
                        ? total.divide(BigDecimal.valueOf(rolls), 2, BigDecimal.ROUND_HALF_UP)
                        : BigDecimal.ZERO;

                // 唯一索引包含已逻辑删除记录，这里必须按“全量历史”编号，避免卷号重复
                QueryWrapper<FilmStockDetail> allQw = new QueryWrapper<>();
                allQw.eq("stock_id", stock.getId());
                List<FilmStockDetail> existedAll = filmStockDetailMapper.selectList(allQw);
                int startIndex = existedAll == null ? 1 : existedAll.size() + 1;
                Set<String> reservedRollNos = new HashSet<>();

                Date now = new Date();
                BigDecimal acc = BigDecimal.ZERO;
                for (int i = 1; i <= rolls; i++) {
                    BigDecimal area = (i == rolls) ? total.subtract(acc) : perRoll;
                    if (area.compareTo(BigDecimal.ZERO) < 0) {
                        area = BigDecimal.ZERO;
                    }
                    acc = acc.add(area);

                    FilmStockDetail detail = new FilmStockDetail();
                    detail.setFilmStockId(stock.getId());
                    detail.setMaterialCode(stock.getMaterialCode());
                    detail.setBatchNo("IMP-" + stock.getId());
                    String baseRollNo = String.format("IMP-%d-%03d", stock.getId(), startIndex + i - 1);
                    detail.setRollNo(ensureUniqueRollNo(baseRollNo, stock.getId(), startIndex + i - 1, reservedRollNos));
                    detail.setThickness(thickness != null ? thickness : stock.getThickness());
                    detail.setWidth(width != null ? width : stock.getWidth());
                    if (detail.getWidth() != null && detail.getWidth() > 0 && area.compareTo(BigDecimal.ZERO) > 0) {
                        BigDecimal length = area.divide(BigDecimal.valueOf(detail.getWidth()).divide(BigDecimal.valueOf(1000), 6, BigDecimal.ROUND_HALF_UP), 0, BigDecimal.ROUND_HALF_UP);
                        detail.setLength(length.intValue());
                    }
                    detail.setArea(area);
                    detail.setPackUom("卷");
                    detail.setPackCount(1.0);
                    detail.setStdUom("㎡");
                    detail.setStdQtyPerPack(area);
                    detail.setStatus("available");
                    detail.setQcStatus("qualified");
                    detail.setInboundDate(now);
                    detail.setRemark("系统按导入卷数拆分(" + source + ")");
                    detail.setCreateBy("import");
                    detail.setCreateTime(now);
                    detail.setUpdateBy("import");
                    detail.setUpdateTime(now);
                    detail.setIsDeleted(0);
                    filmStockDetailMapper.insert(detail);
                }
            }

            private void refreshStockSummaryByDetails(Long filmStockId) {
                FilmStock stock = filmStockMapper.selectById(filmStockId);
                if (stock == null) {
                    return;
                }

                QueryWrapper<FilmStockDetail> detailQw = new QueryWrapper<>();
                detailQw.eq("stock_id", filmStockId).eq("is_deleted", 0);
                List<FilmStockDetail> details = filmStockDetailMapper.selectList(detailQw);
                BigDecimal totalArea = BigDecimal.ZERO;
                BigDecimal availableArea = BigDecimal.ZERO;
                BigDecimal lockedArea = BigDecimal.ZERO;
                double totalRolls = 0.0;
                double availableRolls = 0.0;
                double lockedRolls = 0.0;

                for (FilmStockDetail item : details) {
                    if (item == null) {
                        continue;
                    }
                    BigDecimal area = item.getArea() == null ? BigDecimal.ZERO : item.getArea();
                    double packCount = item.getPackCount() != null && item.getPackCount() > 0 ? item.getPackCount() : 1.0;
                    totalArea = totalArea.add(area);
                    totalRolls += packCount;

                    String status = item.getStatus() == null ? "" : item.getStatus().trim().toLowerCase();
                    if ("locked".equals(status)) {
                        lockedArea = lockedArea.add(area);
                        lockedRolls += packCount;
                    } else if ("available".equals(status)) {
                        availableArea = availableArea.add(area);
                        availableRolls += packCount;
                    }
                }

                stock.setTotalArea(totalArea);
                stock.setAvailableArea(availableArea);
                stock.setLockedArea(lockedArea);
                stock.setTotalRolls(totalRolls);
                stock.setAvailableRolls(availableRolls);
                stock.setLockedRolls(lockedRolls);
                stock.setAvailablePackCount(availableRolls);
                stock.setLockedPackCount(lockedRolls);
                stock.setTotalPackCount(totalRolls);

                if (availableArea.compareTo(BigDecimal.ZERO) <= 0) {
                    stock.setStatus("out_of_stock");
                } else if (stock.getSafetyStock() != null && availableArea.compareTo(stock.getSafetyStock()) < 0) {
                    stock.setStatus("low_stock");
                } else {
                    stock.setStatus("active");
                }

                stock.setUpdateBy("detail-sync");
                filmStockMapper.updateById(stock);
            }

            private byte[] generateSkippedDataExcel(List<Map<String, Object>> skippedData) {
                try (Workbook workbook = new XSSFWorkbook()) {
                    Sheet sheet = workbook.createSheet("跳过数据");
                    
                    // 创建表头
                    Row headerRow = sheet.createRow(0);
                    String[] headers = {"行号", "原始物料编号", "清理后物料编号", "原因", "物料名称", "厚度", "宽度"};
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
                        row.createCell(5).setCellValue((String) rowData.getOrDefault("厚度", ""));
                        row.createCell(6).setCellValue((String) rowData.getOrDefault("宽度", ""));
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
                String normalized = value.replace(",", "").replace("，", "").trim();
                return Integer.parseInt(normalized.split("\\.")[0]);
            }

            private Double getDoubleCellValue(Cell cell) {
                String value = getCellValue(cell);
                if (!StringUtils.hasText(value)) {
                    return null;
                }
                String normalized = value.replace(",", "").replace("，", "").trim();
                return Double.parseDouble(normalized);
            }

            private BigDecimal getDecimalCellValue(Cell cell) {
                String value = getCellValue(cell);
                if (!StringUtils.hasText(value)) {
                    return null;
                }
                String normalized = value.replace(",", "").replace("，", "").trim();
                return new BigDecimal(normalized);
            }

            private BigDecimal defaultDecimal(BigDecimal value) {
                return value == null ? BigDecimal.ZERO : value;
            }

            private Integer defaultInt(Integer value) {
                return value == null ? 0 : value;
            }

            private Double defaultDouble(Double value) {
                return value == null ? 0.0 : value;
            }

            private BigDecimal nz(BigDecimal value) {
                return value == null ? BigDecimal.ZERO : value;
            }

            private double nzd(Double value) {
                return value == null ? 0.0 : value;
            }

            private int nzi(Integer value) {
                return value == null ? 0 : value;
            }

            private String normalizeFilmSpecDesc(String specDesc, BigDecimal thickness, Integer width) {
                String raw = specDesc == null ? "" : specDesc.trim();
                String t = formatSpecNumber(thickness);
                String w = width == null || width <= 0 ? "" : String.valueOf(width);

                if (StringUtils.hasText(raw)) {
                    String unified = raw.replace('×', '*').replace('X', '*').replace('x', '*');
                    // 已带单位：仅统一符号
                    if (unified.matches(".*(?i)(μm|um|mm|\\bm\\b).*")) {
                        return unified;
                    }
                    java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("\\d+(?:\\.\\d+)?").matcher(unified);
                    java.util.List<String> numbers = new java.util.ArrayList<>();
                    while (matcher.find()) {
                        numbers.add(formatSpecNumber(new BigDecimal(matcher.group())));
                    }
                    if (numbers.size() >= 3) {
                        return numbers.get(0) + "μm*" + numbers.get(1) + "mm*" + numbers.get(2) + "m";
                    }
                    if (numbers.size() == 2) {
                        return numbers.get(0) + "μm*" + numbers.get(1) + "mm";
                    }
                }

                if (StringUtils.hasText(t) && StringUtils.hasText(w)) {
                    return t + "μm*" + w + "mm";
                }
                if (StringUtils.hasText(t)) {
                    return t + "μm";
                }
                return StringUtils.hasText(raw) ? raw : null;
            }

            private String formatSpecNumber(BigDecimal value) {
                if (value == null) {
                    return "";
                }
                BigDecimal normalized = value.stripTrailingZeros();
                if (normalized.scale() < 0) {
                    normalized = normalized.setScale(0);
                }
                return normalized.toPlainString();
            }
}
