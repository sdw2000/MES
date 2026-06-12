package com.fine.controller.stock;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fine.Dao.stock.ChemicalStockOutMapper;
import com.fine.Dao.stock.ChemicalStockDetailMapper;
import com.fine.Utils.ResponseResult;
import com.fine.model.stock.ChemicalStock;
import com.fine.model.stock.ChemicalStockDetail;
import com.fine.model.stock.ChemicalStockOut;
import com.fine.service.stock.ChemicalStockService;
import com.fine.service.stock.StocktakeRecordService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletResponse;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URLEncoder;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 化工原料库存管理Controller
 * @author Fine
 * @date 2026-01-15
 */
@PreAuthorize("hasAnyAuthority('admin','warehouse','production','finance','quality')")
@RestController
@RequestMapping("/api/stock/chemical")
@CrossOrigin
public class ChemicalStockController {
    
    @Autowired
    private ChemicalStockService chemicalStockService;

    @Autowired
    private ChemicalStockOutMapper chemicalStockOutMapper;

    @Autowired
    private ChemicalStockDetailMapper chemicalStockDetailMapper;

    @Autowired
    private StocktakeRecordService stocktakeRecordService;
    
    /** 查询所有化工库存 */
    @GetMapping("/list")
    public ResponseResult<List<ChemicalStock>> getChemicalStockList() {
        List<ChemicalStock> list = chemicalStockService.getAllChemicalStock();
        return new ResponseResult<>(20000, "查询成功", list);
    }

    /** 分页查询化工库存 */
    @GetMapping("/list/page")
    public ResponseResult<IPage<ChemicalStock>> getChemicalStockPage(
            @RequestParam(defaultValue = "1") Long current,
            @RequestParam(defaultValue = "20") Long size,
            @RequestParam(required = false) String chemicalType,
            @RequestParam(required = false) String materialCode,
            @RequestParam(required = false) String sortField,
            @RequestParam(required = false) String sortOrder
    ) {
        IPage<ChemicalStock> page = chemicalStockService.getChemicalStockPage(
                current,
                size,
                chemicalType,
                materialCode,
                sortField,
                sortOrder
        );
        return new ResponseResult<>(20000, "查询成功", page);
    }

    /** 化工库存统计（全表聚合，非当前页） */
    @GetMapping("/list/statistics")
    public ResponseResult<Map<String, Object>> getChemicalStockStatistics(
            @RequestParam(required = false) String chemicalType,
            @RequestParam(required = false) String materialCode
    ) {
        Map<String, Object> statistics = chemicalStockService.getChemicalStockStatistics(chemicalType, materialCode);
        return new ResponseResult<>(20000, "查询成功", statistics);
    }
    
    /** 按类型查询化工库存 */
    @GetMapping("/type/{chemicalType}")
    public ResponseResult<List<ChemicalStock>> getByType(@PathVariable String chemicalType) {
        List<ChemicalStock> list = chemicalStockService.getByType(chemicalType);
        return new ResponseResult<>(20000, "查询成功", list);
    }
    
    /**
     * 根据ID查询化工库存
     */
    @GetMapping("/{id}")
    public ResponseResult<ChemicalStock> getById(@PathVariable Long id) {
        ChemicalStock chemicalStock = chemicalStockService.getById(id);
        if (chemicalStock == null) {
            return new ResponseResult<>(40004, "化工库存不存在", null);
        }
        return new ResponseResult<>(20000, "查询成功", chemicalStock);
    }

    /** 更新化工库存主表（初始化维护） */
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('admin')")
    public ResponseResult<ChemicalStock> updateStock(@PathVariable Long id, @RequestBody ChemicalStock stock) {
        try {
            ChemicalStock updated = chemicalStockService.updateStock(id, stock);
            return new ResponseResult<>(20000, "更新成功", updated);
        } catch (Exception e) {
            return new ResponseResult<>(50000, "更新失败: " + e.getMessage(), null);
        }
    }
    
    /**
     * 查询化工库存明细
     */
    @GetMapping("/{id}/details")
    public ResponseResult<List<ChemicalStockDetail>> getDetails(@PathVariable Long id) {
        List<ChemicalStockDetail> details = chemicalStockService.getDetailsByChemicalStockId(id);
        return new ResponseResult<>(20000, "查询成功", details);
    }

    /** 新增化工库存明细 */
    @PostMapping("/{id}/details")
    @PreAuthorize("hasAnyAuthority('admin','warehouse','production')")
    public ResponseResult<ChemicalStockDetail> createDetail(@PathVariable Long id, @RequestBody ChemicalStockDetail detail) {
        try {
            ChemicalStockDetail created = chemicalStockService.createDetail(id, detail);
            return new ResponseResult<>(20000, "新增成功", created);
        } catch (Exception e) {
            return new ResponseResult<>(50000, "新增失败: " + e.getMessage(), null);
        }
    }

    /** 更新化工库存明细 */
    @PutMapping("/{id}/details/{detailId}")
    @PreAuthorize("hasAnyAuthority('admin','warehouse','production')")
    public ResponseResult<ChemicalStockDetail> updateDetail(@PathVariable Long id,
                                                            @PathVariable Long detailId,
                                                            @RequestBody ChemicalStockDetail detail) {
        try {
            ChemicalStockDetail updated = chemicalStockService.updateDetail(id, detailId, detail);
            return new ResponseResult<>(20000, "更新成功", updated);
        } catch (Exception e) {
            return new ResponseResult<>(50000, "更新失败: " + e.getMessage(), null);
        }
    }

    /** 删除化工库存明细 */
    @DeleteMapping("/{id}/details/{detailId}")
    @PreAuthorize("hasAnyAuthority('admin','warehouse','production')")
    public ResponseResult<Boolean> deleteDetail(@PathVariable Long id, @PathVariable Long detailId) {
        try {
            boolean ok = chemicalStockService.deleteDetail(id, detailId);
            if (!ok) {
                return new ResponseResult<>(50000, "删除失败：明细不存在或已删除", false);
            }
            return new ResponseResult<>(20000, "删除成功", true);
        } catch (Exception e) {
            return new ResponseResult<>(50000, "删除失败: " + e.getMessage(), null);
        }
    }

    /** 化工库存明细盘点 */
    @PostMapping("/{id}/details/{detailId}/stocktake")
    @PreAuthorize("hasAnyAuthority('admin','warehouse')")
    public ResponseResult<ChemicalStockDetail> stocktakeDetail(@PathVariable Long id,
                                                               @PathVariable Long detailId,
                                                               @RequestBody Map<String, Object> payload) {
        try {
            ChemicalStock stock = chemicalStockService.getById(id);
            ChemicalStockDetail before = chemicalStockDetailMapper.selectById(detailId);
            if (stock == null || before == null) {
                return new ResponseResult<>(50000, "库存明细不存在", null);
            }
            if (!id.equals(before.getChemicalStockId())) {
                return new ResponseResult<>(50000, "明细与库存不匹配", null);
            }

            BigDecimal actualQuantity = toBigDecimal(payload == null ? null : payload.get("actualQuantity"));
            if (actualQuantity == null || actualQuantity.compareTo(BigDecimal.ZERO) < 0) {
                return new ResponseResult<>(50000, "实盘数量必须大于等于0", null);
            }
            String operator = payload == null || payload.get("operator") == null ? null : String.valueOf(payload.get("operator"));
            String reason = payload == null || payload.get("reason") == null ? null : String.valueOf(payload.get("reason"));

            ChemicalStockDetail update = new ChemicalStockDetail();
            update.setBatchNo(before.getBatchNo());
            update.setContainerNo(before.getContainerNo());
            update.setUnit(before.getUnit());
            update.setWeight(actualQuantity);
            update.setPackUom(before.getPackUom());
            update.setPackCount(before.getPackCount());
            update.setStdUom(before.getStdUom());
            update.setStdQtyPerPack(before.getStdQtyPerPack());
            update.setLocation(before.getLocation());
            update.setSupplier(before.getSupplier());
            update.setInboundDate(before.getInboundDate());
            update.setExpiryDate(before.getExpiryDate());
            update.setIsOpened(before.getIsOpened());
            update.setDangerLevel(before.getDangerLevel());
            update.setStatus(before.getStatus());
            update.setRemark(before.getRemark());

            ChemicalStockDetail updated = chemicalStockService.updateDetail(id, detailId, update);
            stocktakeRecordService.record(
                    "CHEMICAL",
                    id,
                    detailId,
                    before.getMaterialCode(),
                    stock.getMaterialName(),
                    stock.getUnitWeight() == null ? null : stock.getUnitWeight().toPlainString() + "kg/" + (stock.getUnit() == null ? "" : stock.getUnit()),
                    before.getBatchNo(),
                    before.getContainerNo(),
                    before.getLocation(),
                    "kg",
                    before.getWeight(),
                    actualQuantity,
                    operator,
                    reason,
                    "化工仓库存明细盘点"
            );
            return new ResponseResult<>(20000, "盘点成功", updated);
        } catch (Exception e) {
            return new ResponseResult<>(50000, "盘点失败: " + e.getMessage(), null);
        }
    }
    
    /** 查询可用的化工明细 */
    @GetMapping("/{id}/available")
    public ResponseResult<List<ChemicalStockDetail>> getAvailableDetails(@PathVariable Long id) {
        List<ChemicalStockDetail> details = chemicalStockService.getAvailableDetails(id);
        return new ResponseResult<>(20000, "查询成功", details);
    }

    /**
     * 按料号分页查询可用化工明细（支持排序）
     */
    @GetMapping("/available/page")
    public ResponseResult<IPage<ChemicalStockDetail>> getAvailableDetailsPage(
            @RequestParam String materialCode,
            @RequestParam(defaultValue = "1") Long current,
            @RequestParam(defaultValue = "20") Long size,
            @RequestParam(required = false) String sortField,
            @RequestParam(required = false) String sortOrder
    ) {
        LambdaQueryWrapper<ChemicalStockDetail> wrapper = new LambdaQueryWrapper<>();
        String code = materialCode == null ? "" : materialCode.trim();
        wrapper.eq(ChemicalStockDetail::getStatus, "available")
                .eq(!code.isEmpty(), ChemicalStockDetail::getMaterialCode, code);

        boolean asc = "ascending".equalsIgnoreCase(sortOrder);
        if ("qrCode".equals(sortField)) {
            wrapper.orderBy(true, asc, ChemicalStockDetail::getContainerNo);
        } else if ("batchNo".equals(sortField)) {
            wrapper.orderBy(true, asc, ChemicalStockDetail::getBatchNo);
        } else if ("availableArea".equals(sortField)) {
            wrapper.orderBy(true, asc, ChemicalStockDetail::getWeight);
        } else if ("location".equals(sortField)) {
            wrapper.orderBy(true, asc, ChemicalStockDetail::getLocation);
        } else if ("prodDate".equals(sortField)) {
            wrapper.orderBy(true, asc, ChemicalStockDetail::getInboundDate);
        } else {
            wrapper.orderByAsc(ChemicalStockDetail::getInboundDate)
                    .orderByAsc(ChemicalStockDetail::getId);
        }

        Page<ChemicalStockDetail> page = new Page<>(current, size);
        IPage<ChemicalStockDetail> result = chemicalStockDetailMapper.selectPage(page, wrapper);
        return new ResponseResult<>(20000, "查询成功", result);
    }
    
    /** 查询即将过期的化工原料 */
    @GetMapping("/expiring")
    public ResponseResult<List<ChemicalStockDetail>> getExpiringSoon(
            @RequestParam(defaultValue = "30") Integer days
    ) {
        List<ChemicalStockDetail> details = chemicalStockService.getExpiringSoon(days);
        return new ResponseResult<>(20000, "查询成功", details);
    }
    
    /**
     * 查询化工出库记录
     */
    @GetMapping("/outbound/schedule/{scheduleId}")
    public ResponseResult<List<ChemicalStockOut>> getOutboundBySchedule(@PathVariable Long scheduleId) {
        List<ChemicalStockOut> records = chemicalStockService.getOutboundByScheduleId(scheduleId);
        return new ResponseResult<>(20000, "查询成功", records);
    }

    /** 分页查询化工出库记录 */
    @GetMapping("/outbound/list")
    public ResponseResult<IPage<ChemicalStockOut>> getOutboundList(
            @RequestParam(defaultValue = "1") Long page,
            @RequestParam(defaultValue = "20") Long size,
            @RequestParam(required = false) String materialCode
    ) {
        LambdaQueryWrapper<ChemicalStockOut> wrapper = new LambdaQueryWrapper<>();
        if (materialCode != null && !materialCode.trim().isEmpty()) {
            wrapper.like(ChemicalStockOut::getMaterialCode, materialCode.trim());
        }
        wrapper.orderByDesc(ChemicalStockOut::getCreateTime);
        Page<ChemicalStockOut> pageParam = new Page<>(page, size);
        IPage<ChemicalStockOut> result = chemicalStockOutMapper.selectPage(pageParam, wrapper);
        return new ResponseResult<>(20000, "查询成功", result);
    }

    /** 锁定化工库存 */
    @PostMapping("/lock")
    @PreAuthorize("hasAnyAuthority('admin','warehouse','production')")
    public ResponseResult<Boolean> lockStock(@RequestBody Map<String, Object> payload) {
        try {
            Long chemicalStockId = toLong(payload.get("chemicalStockId"));
            Double lockQuantity = toDouble(payload.get("lockQuantity"));
            BigDecimal requiredStdQty = toBigDecimal(payload.get("requiredStdQty"));
            BigDecimal stdQtyPerPack = toBigDecimal(payload.get("stdQtyPerPack"));
            List<Long> detailIds = toLongList(payload.get("detailIds"));

            if ((lockQuantity == null || lockQuantity <= 0) && requiredStdQty != null && stdQtyPerPack != null
                    && requiredStdQty.compareTo(BigDecimal.ZERO) > 0 && stdQtyPerPack.compareTo(BigDecimal.ZERO) > 0) {
                lockQuantity = requiredStdQty.divide(stdQtyPerPack, 0, BigDecimal.ROUND_CEILING).doubleValue();
            }

            if (chemicalStockId == null) {
                return new ResponseResult<>(50000, "chemicalStockId不能为空", null);
            }
            if (lockQuantity == null || lockQuantity <= 0) {
                return new ResponseResult<>(50000, "lockQuantity必须大于0", null);
            }

            boolean ok = chemicalStockService.lockStock(chemicalStockId, lockQuantity, detailIds);
            return new ResponseResult<>(20000, "锁定成功", ok);
        } catch (Exception e) {
            return new ResponseResult<>(50000, "锁定失败: " + e.getMessage(), null);
        }
    }

    /** 解锁化工库存 */
    @PostMapping("/unlock")
    @PreAuthorize("hasAnyAuthority('admin','warehouse','production')")
    public ResponseResult<Boolean> unlockStock(@RequestBody Map<String, Object> payload) {
        try {
            Long chemicalStockId = toLong(payload.get("chemicalStockId"));
            Double unlockQuantity = toDouble(payload.get("unlockQuantity"));
            List<Long> detailIds = toLongList(payload.get("detailIds"));

            if (chemicalStockId == null) {
                return new ResponseResult<>(50000, "chemicalStockId不能为空", null);
            }
            if (unlockQuantity == null || unlockQuantity <= 0) {
                return new ResponseResult<>(50000, "unlockQuantity必须大于0", null);
            }

            boolean ok = chemicalStockService.unlockStock(chemicalStockId, unlockQuantity, detailIds);
            return new ResponseResult<>(20000, "解锁成功", ok);
        } catch (Exception e) {
            return new ResponseResult<>(50000, "解锁失败: " + e.getMessage(), null);
        }
    }

    /** 化工出库 */
    @PostMapping("/outbound")
    @PreAuthorize("hasAnyAuthority('admin','warehouse','production')")
    public ResponseResult<Boolean> outbound(@RequestBody Map<String, Object> payload) {
        try {
            ChemicalStockOut out = new ChemicalStockOut();
            out.setChemicalStockId(toLong(payload.get("chemicalStockId")));
            out.setOutQuantity(toDouble(payload.get("outQuantity")));
            out.setOutWeight(toBigDecimal(payload.get("outWeight")));
            out.setScheduleId(toLong(payload.get("scheduleId")));
            out.setCoatingTaskId(toLong(payload.get("coatingTaskId")));
            out.setPurpose(payload.get("purpose") == null ? null : String.valueOf(payload.get("purpose")));
            out.setOutboundBy(payload.get("outboundBy") == null ? null : String.valueOf(payload.get("outboundBy")));
            out.setRemark(payload.get("remark") == null ? null : String.valueOf(payload.get("remark")));
            out.setOutboundNo(payload.get("outboundNo") == null ? null : String.valueOf(payload.get("outboundNo")));
            out.setBatchNo(payload.get("batchNo") == null ? null : String.valueOf(payload.get("batchNo")));

            List<Long> detailIds = toLongList(payload.get("detailIds"));

            if (out.getChemicalStockId() == null) {
                return new ResponseResult<>(50000, "chemicalStockId不能为空", null);
            }
            if (out.getOutQuantity() == null || out.getOutQuantity() <= 0) {
                return new ResponseResult<>(50000, "outQuantity必须大于0", null);
            }

            boolean ok = chemicalStockService.outbound(out, detailIds);
            return new ResponseResult<>(20000, "出库成功", ok);
        } catch (Exception e) {
            return new ResponseResult<>(50000, "出库失败: " + e.getMessage(), null);
        }
    }

    private Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        try {
            String s = String.valueOf(value).trim();
            return s.isEmpty() ? null : Long.parseLong(s);
        } catch (Exception e) {
            return null;
        }
    }

    private Integer toInteger(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        try {
            String s = String.valueOf(value).trim();
            return s.isEmpty() ? null : Integer.parseInt(s);
        } catch (Exception e) {
            return null;
        }
    }

    private Double toDouble(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        try {
            String s = String.valueOf(value).trim();
            return s.isEmpty() ? null : Double.parseDouble(s);
        } catch (Exception e) {
            return null;
        }
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof BigDecimal) {
            return (BigDecimal) value;
        }
        if (value instanceof Number) {
            return new BigDecimal(String.valueOf(((Number) value).doubleValue()));
        }
        try {
            String s = String.valueOf(value).trim();
            return s.isEmpty() ? null : new BigDecimal(s);
        } catch (Exception e) {
            return null;
        }
    }

    private List<Long> toLongList(Object value) {
        List<Long> ids = new java.util.ArrayList<>();
        if (!(value instanceof List)) {
            return ids;
        }
        for (Object obj : (List<?>) value) {
            Long id = toLong(obj);
            if (id != null && id > 0) {
                ids.add(id);
            }
        }
        return ids;
    }

    /** 下载化工库存导入模板（明细口径） */
    @GetMapping("/template")
    @PreAuthorize("hasAnyAuthority('admin','warehouse')")
    public void downloadTemplate(HttpServletResponse response) throws IOException {
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("化工库存导入模板");

        Row header = sheet.createRow(0);
        String[] headers = {"物料编号", "批次号", "桶号/包号", "单位", "重量(kg)", "包装单位", "包装数量", "标准单位", "每包装标准量", "库位", "供应商", "入库日期", "有效期至", "是否开封", "危险等级", "状态", "备注"};
        for (int i = 0; i < headers.length; i++) {
            header.createCell(i).setCellValue(headers[i]);
        }

        Row demo = sheet.createRow(1);
        demo.createCell(0).setCellValue("FN8558");
        demo.createCell(1).setCellValue("20260424-C01");
        demo.createCell(2).setCellValue("T001");
        demo.createCell(3).setCellValue("Kg");
        demo.createCell(4).setCellValue(150);
        demo.createCell(5).setCellValue("桶");
        demo.createCell(6).setCellValue(1);
        demo.createCell(7).setCellValue("kg");
        demo.createCell(8).setCellValue(150);
        demo.createCell(9).setCellValue("C-01");
        demo.createCell(10).setCellValue("供应商A");
        demo.createCell(11).setCellValue("2026-04-24");
        demo.createCell(12).setCellValue("2027-04-24");
        demo.createCell(13).setCellValue("否");
        demo.createCell(14).setCellValue(1);
        demo.createCell(15).setCellValue("available");
        demo.createCell(16).setCellValue("明细导入示例");

        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment;filename=" + URLEncoder.encode("化工库存导入模板-明细.xlsx", "UTF-8"));
        workbook.write(response.getOutputStream());
        workbook.close();
    }

    /**
     * 按导入模板格式导出当前化工库存明细（可修改后回导）
     */
    @GetMapping("/export/import-format")
    @PreAuthorize("hasAnyAuthority('admin','warehouse')")
    public void exportImportFormat(
            @RequestParam(required = false) String chemicalType,
            @RequestParam(required = false) String materialCode,
            HttpServletResponse response) throws IOException {
        IPage<ChemicalStock> page = chemicalStockService.getChemicalStockPage(
                1,
                100000,
                chemicalType,
                materialCode,
                "createTime",
                "descending"
        );
        List<ChemicalStock> records = page == null ? java.util.Collections.emptyList() : page.getRecords();

        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("化工库存明细导出");

        Row header = sheet.createRow(0);
        String[] headers = {"物料编号", "批次号", "桶号/包号", "单位", "重量(kg)", "包装单位", "包装数量", "标准单位", "每包装标准量", "库位", "供应商", "入库日期", "有效期至", "是否开封", "危险等级", "状态", "备注"};
        for (int i = 0; i < headers.length; i++) {
            header.createCell(i).setCellValue(headers[i]);
        }

        int rowIndex = 1;
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd");
        for (ChemicalStock stock : records) {
            List<ChemicalStockDetail> details = chemicalStockService.getDetailsByChemicalStockId(stock.getId());
            for (ChemicalStockDetail detail : details) {
                // 仅导出在库数据（重量 > 0 且状态非 used）
                if ("used".equalsIgnoreCase(detail.getStatus()) || 
                    detail.getWeight() == null || detail.getWeight().compareTo(BigDecimal.ZERO) <= 0) {
                    continue;
                }
                Row row = sheet.createRow(rowIndex++);
                row.createCell(0).setCellValue(detail.getMaterialCode() == null ? "" : detail.getMaterialCode());
                row.createCell(1).setCellValue(detail.getBatchNo() == null ? "" : detail.getBatchNo());
                row.createCell(2).setCellValue(detail.getContainerNo() == null ? "" : detail.getContainerNo());
                row.createCell(3).setCellValue(detail.getUnit() == null ? "Kg" : detail.getUnit());
                row.createCell(4).setCellValue(detail.getWeight() == null ? 0 : detail.getWeight().doubleValue());
                row.createCell(5).setCellValue(detail.getPackUom() == null ? "桶" : detail.getPackUom());
                row.createCell(6).setCellValue(detail.getPackCount() == null ? 1 : detail.getPackCount());
                row.createCell(7).setCellValue(detail.getStdUom() == null ? "kg" : detail.getStdUom());
                row.createCell(8).setCellValue(detail.getStdQtyPerPack() == null ? 0 : detail.getStdQtyPerPack().doubleValue());
                row.createCell(9).setCellValue(detail.getLocation() == null ? "" : detail.getLocation());
                row.createCell(10).setCellValue(detail.getSupplier() == null ? "" : detail.getSupplier());
                row.createCell(11).setCellValue(detail.getInboundDate() == null ? "" : sdf.format(detail.getInboundDate()));
                row.createCell(12).setCellValue(detail.getExpiryDate() == null ? "" : sdf.format(detail.getExpiryDate()));
                row.createCell(13).setCellValue(Boolean.TRUE.equals(detail.getIsOpened()) ? "是" : "否");
                row.createCell(14).setCellValue(detail.getDangerLevel() == null ? 1 : detail.getDangerLevel());
                row.createCell(15).setCellValue(detail.getStatus() == null ? "available" : detail.getStatus());
                row.createCell(16).setCellValue(detail.getRemark() == null ? "" : detail.getRemark());
            }
        }

        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment;filename=" + URLEncoder.encode("化工库存明细导出-可回导.xlsx", "UTF-8"));
        workbook.write(response.getOutputStream());
        workbook.close();
    }

    /** 导出化工盘点表 */
    @GetMapping("/export/stocktake")
    @PreAuthorize("hasAnyAuthority('admin','warehouse')")
    public void exportStocktake(
            @RequestParam(required = false) String chemicalType,
            @RequestParam(required = false) String materialCode,
            HttpServletResponse response) throws IOException {
        IPage<ChemicalStock> page = chemicalStockService.getChemicalStockPage(1, 100000, chemicalType, materialCode, "createTime", "descending");
        List<ChemicalStock> records = page == null ? java.util.Collections.emptyList() : page.getRecords();
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("化工仓盘点表");
        Row header = sheet.createRow(0);
        String[] headers = {"物料代码", "名称", "规格", "库存数量", "存放位置", "实盘数量"};
        for (int i = 0; i < headers.length; i++) {
            header.createCell(i).setCellValue(headers[i]);
        }
        int rowIndex = 1;
        for (ChemicalStock stock : records) {
            List<ChemicalStockDetail> details = chemicalStockService.getDetailsByChemicalStockId(stock.getId());
            for (ChemicalStockDetail detail : details) {
                // 仅导出在库数据（重量 > 0 且状态非 used）
                if ("used".equalsIgnoreCase(detail.getStatus()) || 
                    detail.getWeight() == null || detail.getWeight().compareTo(BigDecimal.ZERO) <= 0) {
                    continue;
                }
                Row row = sheet.createRow(rowIndex++);
                row.createCell(0).setCellValue(detail.getMaterialCode() == null ? "" : detail.getMaterialCode());
                row.createCell(1).setCellValue(stock.getMaterialName() == null ? "" : stock.getMaterialName());
                row.createCell(2).setCellValue(stock.getUnitWeight() == null ? "" : stock.getUnitWeight().toPlainString() + "kg/" + (stock.getUnit() == null ? "" : stock.getUnit()));
                row.createCell(3).setCellValue(detail.getWeight() == null ? 0 : detail.getWeight().doubleValue());
                row.createCell(4).setCellValue(detail.getLocation() == null ? "" : detail.getLocation());
                row.createCell(5).setCellValue("");
            }
        }
        for (int i = 0; i < headers.length; i++) {
            sheet.autoSizeColumn(i);
        }
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment;filename=" + URLEncoder.encode("化工仓盘点表.xlsx", "UTF-8"));
        workbook.write(response.getOutputStream());
        workbook.close();
    }

    /** 导入化工库存汇总 */
    @PostMapping("/import")
    @PreAuthorize("hasAnyAuthority('admin','warehouse')")
    public ResponseResult<Map<String, Object>> importExcel(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "clearBeforeImport", defaultValue = "false") boolean clearBeforeImport
    ) {
        try {
            Map<String, Object> result = chemicalStockService.importExcel(file, clearBeforeImport);
            return new ResponseResult<>(20000, "导入完成", result);
        } catch (Exception e) {
            return new ResponseResult<>(50000, "导入失败: " + e.getMessage(), null);
        }
    }

    /** 清空化工库存（用于重新盘点后全量导入） */
    @PostMapping("/clear-for-reimport")
    @PreAuthorize("hasAnyAuthority('admin','warehouse')")
    public ResponseResult<Map<String, Object>> clearForReimport(
            @RequestParam(value = "clearOutbound", defaultValue = "true") boolean clearOutbound
    ) {
        try {
            Map<String, Object> result = chemicalStockService.clearForReimport(clearOutbound);
            return new ResponseResult<>(20000, "化工库存已清空", result);
        } catch (Exception e) {
            return new ResponseResult<>(50000, "清空失败: " + e.getMessage(), null);
        }
    }

    /** 下载跳过数据Excel */
    @GetMapping("/download-skipped")
    @PreAuthorize("hasAnyAuthority('admin','warehouse')")
    public ResponseEntity<byte[]> downloadSkippedExcel(@RequestParam("data") String skippedDataJson) {
        try {
            // 解析跳过数据JSON并生成Excel
            ObjectMapper mapper = new ObjectMapper();
            List<Map<String, Object>> skippedData = mapper.readValue(skippedDataJson, 
                mapper.getTypeFactory().constructCollectionType(List.class, Map.class));
            
            byte[] excelBytes = generateSkippedDataExcel(skippedData);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            headers.setContentDispositionFormData("attachment", "chemical_stock_skipped_data.xlsx");
            
            return new ResponseEntity<>(excelBytes, headers, HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
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
}
