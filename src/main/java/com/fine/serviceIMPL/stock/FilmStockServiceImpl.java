package com.fine.serviceIMPL.stock;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fine.Dao.rd.TapeFormulaMapper;
import com.fine.Dao.stock.FilmStockMapper;
import com.fine.Dao.stock.FilmStockDetailMapper;
import com.fine.Dao.stock.FilmStockOutMapper;
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
    public IPage<FilmStock> getFilmStockPage(long current, long size, Integer thickness) {
        Page<FilmStock> page = new Page<>(current, size);
        QueryWrapper<FilmStock> wrapper = new QueryWrapper<>();
        wrapper.eq("is_deleted", 0);
        wrapper.eq(thickness != null, "thickness", thickness);
        wrapper.orderByDesc("create_time");
        return filmStockMapper.selectPage(page, wrapper);
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
            throw new RuntimeException("出库失败，锁定库存不足");
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
                filmStockOut.getScheduleId() != null ? filmStockOut.getScheduleId().toString() : "MANUAL_OUT",
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
            
            int totalAvailableRolls = list.stream()
                    .map(FilmStock::getAvailableRolls)
                    .filter(Objects::nonNull)
                    .mapToInt(Integer::intValue)
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
        
        int totalAvailableRolls = stocks.stream()
                .map(FilmStock::getAvailableRolls)
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue)
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
                Map<String, Object> result = new HashMap<>();
                int successCount = 0;
                int skipCount = 0;
                List<String> errors = new ArrayList<>();
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

                            // 严格按原材料代码表入库：保留原始料号格式（仅去首尾空白）
                            String cleanedMaterialCode = originalMaterialCode.replace("\u00A0", " ").trim();

                            // 从原材料代码表获取标准物料名称
                            TapeRawMaterial rawMaterial = tapeFormulaMapper.selectRawMaterialByCode(cleanedMaterialCode);
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

                            BigDecimal thickness = getDecimalCellValue(getCellByHeader(row, headerIndex,
                                    new String[]{"厚度", "厚度(μm)", "thickness"}, 2));
                            Integer width = getIntCellValue(getCellByHeader(row, headerIndex,
                                    new String[]{"宽度", "宽度(mm)", "width"}, 3));
                            String specDesc = getCellValue(getCellByHeader(row, headerIndex,
                                    new String[]{"规格描述", "spec_desc"}, 4));

                            BigDecimal totalArea = defaultDecimal(getDecimalCellValue(getCellByHeader(row, headerIndex,
                                    new String[]{"总面积", "总面积(㎡)", "total_area"}, 5)));
                            BigDecimal availableArea = getDecimalCellValue(getCellByHeader(row, headerIndex,
                                    new String[]{"可用面积", "可用面积(㎡)", "available_area"}, 6));
                            BigDecimal lockedArea = getDecimalCellValue(getCellByHeader(row, headerIndex,
                                    new String[]{"锁定面积", "锁定面积(㎡)", "locked_area"}, 7));

                            Integer totalRolls = defaultInt(getIntCellValue(getCellByHeader(row, headerIndex,
                                    new String[]{"总卷数", "total_rolls"}, 8)));
                            Integer availableRolls = getIntCellValue(getCellByHeader(row, headerIndex,
                                    new String[]{"可用卷数", "available_rolls"}, 9));
                            Integer lockedRolls = getIntCellValue(getCellByHeader(row, headerIndex,
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
                                lockedRolls = 0;
                            }
                            if (!StringUtils.hasText(status)) {
                                status = "active";
                            }

                            QueryWrapper<FilmStock> qw = new QueryWrapper<>();
                            qw.eq("material_code", cleanedMaterialCode);
                            FilmStock exist = filmStockMapper.selectOne(qw);

                            FilmStock stock = exist == null ? new FilmStock() : exist;
                            stock.setMaterialCode(cleanedMaterialCode);
                            stock.setMaterialName(materialName);
                            stock.setThickness(thickness);
                            stock.setWidth(width);
                            stock.setSpecDesc(StringUtils.hasText(specDesc) ? specDesc.trim() : null);
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
                                stock.setTotalRolls(nzi(exist.getTotalRolls()) + nzi(totalRolls));
                                stock.setAvailableRolls(nzi(exist.getAvailableRolls()) + nzi(availableRolls));
                                stock.setLockedRolls(nzi(exist.getLockedRolls()) + nzi(lockedRolls));
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
                            appendImportDetailsByRoll(stock, totalArea, totalRolls, width, thickness, "import-rolls");
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
                }

                result.put("success", skipCount == 0);
                result.put("successCount", successCount);
                result.put("skipCount", skipCount);
                result.put("errors", errors);
                result.put("skippedExcel", skippedExcel != null ? java.util.Base64.getEncoder().encodeToString(skippedExcel) : null);
                return result;
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
                    detail.setRollNo(String.format("IMP-%d-%03d", stock.getId(), startIndex + i - 1));
                    detail.setThickness(thickness != null ? thickness : stock.getThickness());
                    detail.setWidth(width != null ? width : stock.getWidth());
                    if (detail.getWidth() != null && detail.getWidth() > 0 && area.compareTo(BigDecimal.ZERO) > 0) {
                        BigDecimal length = area.divide(BigDecimal.valueOf(detail.getWidth()).divide(BigDecimal.valueOf(1000), 6, BigDecimal.ROUND_HALF_UP), 0, BigDecimal.ROUND_HALF_UP);
                        detail.setLength(length.intValue());
                    }
                    detail.setArea(area);
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

                List<FilmStockDetail> details = filmStockDetailMapper.selectByFilmStockId(filmStockId);
                BigDecimal totalArea = BigDecimal.ZERO;
                BigDecimal availableArea = BigDecimal.ZERO;
                BigDecimal lockedArea = BigDecimal.ZERO;
                int totalRolls = 0;
                int availableRolls = 0;
                int lockedRolls = 0;

                for (FilmStockDetail item : details) {
                    if (item == null) {
                        continue;
                    }
                    BigDecimal area = item.getArea() == null ? BigDecimal.ZERO : item.getArea();
                    totalArea = totalArea.add(area);
                    totalRolls++;

                    String status = item.getStatus() == null ? "" : item.getStatus().trim().toLowerCase();
                    if ("locked".equals(status)) {
                        lockedArea = lockedArea.add(area);
                        lockedRolls++;
                    } else if ("available".equals(status)) {
                        availableArea = availableArea.add(area);
                        availableRolls++;
                    }
                }

                stock.setTotalArea(totalArea);
                stock.setAvailableArea(availableArea);
                stock.setLockedArea(lockedArea);
                stock.setTotalRolls(totalRolls);
                stock.setAvailableRolls(availableRolls);
                stock.setLockedRolls(lockedRolls);

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

            private BigDecimal nz(BigDecimal value) {
                return value == null ? BigDecimal.ZERO : value;
            }

            private int nzi(Integer value) {
                return value == null ? 0 : value;
            }
}
