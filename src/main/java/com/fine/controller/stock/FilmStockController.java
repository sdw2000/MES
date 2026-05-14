package com.fine.controller.stock;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fine.Dao.stock.FilmStockOutMapper;
import com.fine.Dao.stock.FilmStockDetailMapper;
import com.fine.Utils.ResponseResult;
import com.fine.model.stock.FilmStock;
import com.fine.model.stock.FilmStockDetail;
import com.fine.model.stock.FilmStockOut;
import com.fine.service.stock.FilmStockService;
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
 * 薄膜库存管理Controller
 * @author Fine
 * @date 2026-01-15
 */
@PreAuthorize("hasAnyAuthority('admin','warehouse','production','finance','quality')")
@RestController
@RequestMapping("/api/stock/film")
@CrossOrigin
public class FilmStockController {
    
    @Autowired
    private FilmStockService filmStockService;

    @Autowired
    private FilmStockOutMapper filmStockOutMapper;

    @Autowired
    private FilmStockDetailMapper filmStockDetailMapper;
    
    /** 查询所有薄膜库存 */
    @GetMapping("/list")
    public ResponseResult<List<FilmStock>> getFilmStockList() {
        List<FilmStock> list = filmStockService.getAllFilmStock();
        return new ResponseResult<>(20000, "查询成功", list);
    }

    /** 分页查询薄膜库存 */
    @GetMapping("/list/page")
    public ResponseResult<IPage<FilmStock>> getFilmStockPage(
            @RequestParam(defaultValue = "1") Long current,
            @RequestParam(defaultValue = "20") Long size,
            @RequestParam(required = false) Integer thickness,
            @RequestParam(required = false) String materialCode,
            @RequestParam(required = false) String sortField,
            @RequestParam(required = false) String sortOrder
    ) {
        IPage<FilmStock> page = filmStockService.getFilmStockPage(current, size, thickness, materialCode, sortField, sortOrder);
        return new ResponseResult<>(20000, "查询成功", page);
    }

    /** 薄膜库存统计（全表聚合，非当前页） */
    @GetMapping("/list/statistics")
    public ResponseResult<Map<String, Object>> getFilmStockStatistics(
            @RequestParam(required = false) Integer thickness,
            @RequestParam(required = false) String materialCode
    ) {
        Map<String, Object> statistics = filmStockService.getFilmStockStatistics(thickness, materialCode);
        return new ResponseResult<>(20000, "查询成功", statistics);
    }
    
    /** 按规格查询薄膜库存 */
    @GetMapping("/spec")
    public ResponseResult<List<FilmStock>> getBySpec(
            @RequestParam(required = false) Integer thickness,
            @RequestParam(required = false) Integer width
    ) {
        List<FilmStock> list = filmStockService.getBySpec(thickness, width);
        return new ResponseResult<>(20000, "查询成功", list);
    }
    
    /**
     * 根据ID查询薄膜库存
     */
    @GetMapping("/{id}")
    public ResponseResult<FilmStock> getById(@PathVariable Long id) {
        FilmStock filmStock = filmStockService.getById(id);
        if (filmStock == null) {
            return new ResponseResult<>(40004, "薄膜库存不存在", null);
        }
        return new ResponseResult<>(20000, "查询成功", filmStock);
    }
    
    /**
     * 查询薄膜库存明细
     */
    @GetMapping("/{id}/details")
    public ResponseResult<List<FilmStockDetail>> getDetails(@PathVariable Long id) {
        List<FilmStockDetail> details = filmStockService.getDetailsByFilmStockId(id);
        return new ResponseResult<>(20000, "查询成功", details);
    }

    /**
     * 分页查询薄膜库存明细（支持排序，默认按入库日期升序）
     */
    @GetMapping("/{id}/details/page")
    public ResponseResult<IPage<FilmStockDetail>> getDetailsPage(
            @PathVariable Long id,
            @RequestParam(defaultValue = "1") Long current,
            @RequestParam(defaultValue = "20") Long size,
            @RequestParam(defaultValue = "false") Boolean includeUsed,
            @RequestParam(required = false) String sortField,
            @RequestParam(required = false) String sortOrder
    ) {
        LambdaQueryWrapper<FilmStockDetail> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FilmStockDetail::getFilmStockId, id)
                .eq(FilmStockDetail::getIsDeleted, 0);

        if (!Boolean.TRUE.equals(includeUsed)) {
            wrapper.ne(FilmStockDetail::getStatus, "used");
        }

        boolean asc = "ascending".equalsIgnoreCase(sortOrder) || "asc".equalsIgnoreCase(sortOrder);
        String field = sortField == null ? "" : sortField.trim();
        if ("batchNo".equals(field)) {
            wrapper.orderBy(true, asc, FilmStockDetail::getBatchNo);
        } else if ("rollNo".equals(field)) {
            wrapper.orderBy(true, asc, FilmStockDetail::getRollNo);
        } else if ("width".equals(field)) {
            wrapper.orderBy(true, asc, FilmStockDetail::getWidth);
        } else if ("length".equals(field)) {
            wrapper.orderBy(true, asc, FilmStockDetail::getLength);
        } else if ("area".equals(field)) {
            wrapper.orderBy(true, asc, FilmStockDetail::getArea);
        } else if ("qcStatus".equals(field)) {
            wrapper.orderBy(true, asc, FilmStockDetail::getQcStatus);
        } else if ("location".equals(field)) {
            wrapper.orderBy(true, asc, FilmStockDetail::getLocation);
        } else if ("supplier".equals(field)) {
            wrapper.orderBy(true, asc, FilmStockDetail::getSupplier);
        } else if ("status".equals(field)) {
            wrapper.orderBy(true, asc, FilmStockDetail::getStatus);
        } else {
            // 默认：按入库时间顺序（升序）
            wrapper.orderByAsc(FilmStockDetail::getInboundDate);
            wrapper.orderByAsc(FilmStockDetail::getId);
            Page<FilmStockDetail> page = new Page<>(current, size);
            IPage<FilmStockDetail> result = filmStockDetailMapper.selectPage(page, wrapper);
            return new ResponseResult<>(20000, "查询成功", result);
        }

        wrapper.orderBy(true, asc, FilmStockDetail::getId);
        Page<FilmStockDetail> page = new Page<>(current, size);
        IPage<FilmStockDetail> result = filmStockDetailMapper.selectPage(page, wrapper);
        return new ResponseResult<>(20000, "查询成功", result);
    }

    /** 新增薄膜库存明细 */
    @PostMapping("/{id}/details")
    @PreAuthorize("hasAnyAuthority('admin','warehouse','production')")
    public ResponseResult<FilmStockDetail> createDetail(@PathVariable Long id, @RequestBody FilmStockDetail detail) {
        try {
            FilmStockDetail created = filmStockService.createDetail(id, detail);
            return new ResponseResult<>(20000, "新增成功", created);
        } catch (Exception e) {
            return new ResponseResult<>(50000, "新增失败: " + e.getMessage(), null);
        }
    }

    /** 更新薄膜库存明细 */
    @PutMapping("/{id}/details/{detailId}")
    @PreAuthorize("hasAnyAuthority('admin','warehouse','production')")
    public ResponseResult<FilmStockDetail> updateDetail(@PathVariable Long id,
                                                        @PathVariable Long detailId,
                                                        @RequestBody FilmStockDetail detail) {
        try {
            FilmStockDetail updated = filmStockService.updateDetail(id, detailId, detail);
            return new ResponseResult<>(20000, "更新成功", updated);
        } catch (Exception e) {
            return new ResponseResult<>(50000, "更新失败: " + e.getMessage(), null);
        }
    }

    /** 删除薄膜库存明细 */
    @DeleteMapping("/{id}/details/{detailId}")
    @PreAuthorize("hasAnyAuthority('admin','warehouse','production')")
    public ResponseResult<Boolean> deleteDetail(@PathVariable Long id, @PathVariable Long detailId) {
        try {
            boolean ok = filmStockService.deleteDetail(id, detailId);
            if (!ok) {
                return new ResponseResult<>(50000, "删除失败：明细不存在或已删除", false);
            }
            return new ResponseResult<>(20000, "删除成功", true);
        } catch (Exception e) {
            return new ResponseResult<>(50000, "删除失败: " + e.getMessage(), null);
        }
    }
    
    /** 查询可用的薄膜明细 */
    @GetMapping("/{id}/available")
    public ResponseResult<List<FilmStockDetail>> getAvailableDetails(@PathVariable Long id) {
        List<FilmStockDetail> details = filmStockService.getAvailableDetails(id);
        return new ResponseResult<>(20000, "查询成功", details);
    }

    /**
     * 按料号分页查询可用薄膜明细（支持排序）
     */
    @GetMapping("/available/page")
    public ResponseResult<IPage<FilmStockDetail>> getAvailableDetailsPage(
            @RequestParam String materialCode,
            @RequestParam(defaultValue = "1") Long current,
            @RequestParam(defaultValue = "20") Long size,
            @RequestParam(required = false) String sortField,
            @RequestParam(required = false) String sortOrder
    ) {
        LambdaQueryWrapper<FilmStockDetail> wrapper = new LambdaQueryWrapper<>();
        String code = materialCode == null ? "" : materialCode.trim();
        wrapper.eq(FilmStockDetail::getIsDeleted, 0)
                .eq(FilmStockDetail::getStatus, "available")
                .eq(!code.isEmpty(), FilmStockDetail::getMaterialCode, code);

        boolean asc = "ascending".equalsIgnoreCase(sortOrder);
        if ("qrCode".equals(sortField)) {
            wrapper.orderBy(true, asc, FilmStockDetail::getRollNo);
        } else if ("batchNo".equals(sortField)) {
            wrapper.orderBy(true, asc, FilmStockDetail::getBatchNo);
        } else if ("width".equals(sortField)) {
            wrapper.orderBy(true, asc, FilmStockDetail::getWidth);
        } else if ("availableArea".equals(sortField)) {
            wrapper.orderBy(true, asc, FilmStockDetail::getArea);
        } else if ("location".equals(sortField)) {
            wrapper.orderBy(true, asc, FilmStockDetail::getLocation);
        } else if ("prodDate".equals(sortField)) {
            wrapper.orderBy(true, asc, FilmStockDetail::getInboundDate);
        } else {
            wrapper.orderByAsc(FilmStockDetail::getInboundDate)
                    .orderByAsc(FilmStockDetail::getId);
        }

        Page<FilmStockDetail> page = new Page<>(current, size);
        IPage<FilmStockDetail> result = filmStockDetailMapper.selectPage(page, wrapper);
        return new ResponseResult<>(20000, "查询成功", result);
    }
    
    /**
     * 查询薄膜出库记录
     */
    @GetMapping("/outbound/schedule/{scheduleId}")
    public ResponseResult<List<FilmStockOut>> getOutboundBySchedule(@PathVariable Long scheduleId) {
        List<FilmStockOut> records = filmStockService.getOutboundByScheduleId(scheduleId);
        return new ResponseResult<>(20000, "查询成功", records);
    }

    /** 分页查询薄膜出库记录 */
    @GetMapping("/outbound/list")
    public ResponseResult<IPage<FilmStockOut>> getOutboundList(
            @RequestParam(defaultValue = "1") Long page,
            @RequestParam(defaultValue = "20") Long size,
            @RequestParam(required = false) String materialCode,
            @RequestParam(required = false) Long filmStockId,
            @RequestParam(required = false) String outboundNo
    ) {
        LambdaQueryWrapper<FilmStockOut> wrapper = new LambdaQueryWrapper<>();
        if (materialCode != null && !materialCode.trim().isEmpty()) {
            wrapper.like(FilmStockOut::getMaterialCode, materialCode.trim());
        }
        if (filmStockId != null) {
            wrapper.eq(FilmStockOut::getFilmStockId, filmStockId);
        }
        if (outboundNo != null && !outboundNo.trim().isEmpty()) {
            wrapper.like(FilmStockOut::getOutboundNo, outboundNo.trim());
        }
        wrapper.orderByDesc(FilmStockOut::getCreateTime);
        Page<FilmStockOut> pageParam = new Page<>(page, size);
        IPage<FilmStockOut> result = filmStockOutMapper.selectPage(pageParam, wrapper);
        return new ResponseResult<>(20000, "查询成功", result);
    }

    /** 锁定薄膜库存 */
    @PostMapping("/lock")
    @PreAuthorize("hasAnyAuthority('admin','warehouse','production')")
    public ResponseResult<Boolean> lockStock(@RequestBody Map<String, Object> payload) {
        try {
            Long filmStockId = toLong(payload.get("filmStockId"));
            BigDecimal lockArea = toBigDecimal(payload.get("lockArea"));
            Integer lockRolls = toInteger(payload.get("lockRolls"));
            BigDecimal requiredStdQty = toBigDecimal(payload.get("requiredStdQty"));
            BigDecimal stdQtyPerPack = toBigDecimal(payload.get("stdQtyPerPack"));
            List<Long> detailIds = toLongList(payload.get("detailIds"));

            if ((lockRolls == null || lockRolls <= 0) && requiredStdQty != null && stdQtyPerPack != null
                    && requiredStdQty.compareTo(BigDecimal.ZERO) > 0 && stdQtyPerPack.compareTo(BigDecimal.ZERO) > 0) {
                lockRolls = requiredStdQty.divide(stdQtyPerPack, 0, BigDecimal.ROUND_CEILING).intValue();
                if (lockArea == null || lockArea.compareTo(BigDecimal.ZERO) <= 0) {
                    lockArea = stdQtyPerPack.multiply(BigDecimal.valueOf(lockRolls));
                }
            }

            if (filmStockId == null) {
                return new ResponseResult<>(50000, "filmStockId不能为空", null);
            }
            if (lockArea == null || lockArea.compareTo(BigDecimal.ZERO) <= 0) {
                return new ResponseResult<>(50000, "lockArea必须大于0", null);
            }
            if (lockRolls == null || lockRolls <= 0) {
                return new ResponseResult<>(50000, "lockRolls必须大于0", null);
            }

            boolean ok = filmStockService.lockStock(filmStockId, lockArea, lockRolls, detailIds);
            return new ResponseResult<>(20000, "锁定成功", ok);
        } catch (Exception e) {
            return new ResponseResult<>(50000, "锁定失败: " + e.getMessage(), null);
        }
    }

    /** 解锁薄膜库存 */
    @PostMapping("/unlock")
    @PreAuthorize("hasAnyAuthority('admin','warehouse','production')")
    public ResponseResult<Boolean> unlockStock(@RequestBody Map<String, Object> payload) {
        try {
            Long filmStockId = toLong(payload.get("filmStockId"));
            BigDecimal unlockArea = toBigDecimal(payload.get("unlockArea"));
            Integer unlockRolls = toInteger(payload.get("unlockRolls"));
            List<Long> detailIds = toLongList(payload.get("detailIds"));

            if (filmStockId == null) {
                return new ResponseResult<>(50000, "filmStockId不能为空", null);
            }
            if (unlockArea == null || unlockArea.compareTo(BigDecimal.ZERO) <= 0) {
                return new ResponseResult<>(50000, "unlockArea必须大于0", null);
            }
            if (unlockRolls == null || unlockRolls <= 0) {
                return new ResponseResult<>(50000, "unlockRolls必须大于0", null);
            }

            boolean ok = filmStockService.unlockStock(filmStockId, unlockArea, unlockRolls, detailIds);
            return new ResponseResult<>(20000, "解锁成功", ok);
        } catch (Exception e) {
            return new ResponseResult<>(50000, "解锁失败: " + e.getMessage(), null);
        }
    }

    /** 薄膜出库 */
    @PostMapping("/outbound")
    @PreAuthorize("hasAnyAuthority('admin','warehouse','production')")
    public ResponseResult<Boolean> outbound(@RequestBody Map<String, Object> payload) {
        try {
            FilmStockOut out = new FilmStockOut();
            out.setFilmStockId(toLong(payload.get("filmStockId")));
            out.setOutArea(toBigDecimal(payload.get("outArea")));
            out.setOutRolls(toInteger(payload.get("outRolls")));
            out.setScheduleId(toLong(payload.get("scheduleId")));
            out.setCoatingTaskId(toLong(payload.get("coatingTaskId")));
            out.setPurpose(payload.get("purpose") == null ? null : String.valueOf(payload.get("purpose")));
            out.setOutboundBy(payload.get("outboundBy") == null ? null : String.valueOf(payload.get("outboundBy")));
            out.setRemark(payload.get("remark") == null ? null : String.valueOf(payload.get("remark")));
            out.setOutboundNo(payload.get("outboundNo") == null ? null : String.valueOf(payload.get("outboundNo")));
            out.setBatchNo(payload.get("batchNo") == null ? null : String.valueOf(payload.get("batchNo")));

            List<Long> detailIds = toLongList(payload.get("detailIds"));

            if (out.getFilmStockId() == null) {
                return new ResponseResult<>(50000, "filmStockId不能为空", null);
            }
            if (out.getOutArea() == null || out.getOutArea().compareTo(BigDecimal.ZERO) <= 0) {
                return new ResponseResult<>(50000, "outArea必须大于0", null);
            }
            if (out.getOutRolls() == null || out.getOutRolls() <= 0) {
                return new ResponseResult<>(50000, "outRolls必须大于0", null);
            }

            boolean ok = filmStockService.outbound(out, detailIds);
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

    /** 下载薄膜库存导入模板（明细口径） */
    @GetMapping("/template")
    @PreAuthorize("hasAnyAuthority('admin','warehouse')")
    public void downloadTemplate(HttpServletResponse response) throws IOException {
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("薄膜库存导入模板");

        Row header = sheet.createRow(0);
        String[] headers = {"物料编号", "批次号", "卷号", "厚度(μm)", "宽度(mm)", "长度(m)", "面积(㎡)", "包装单位", "包装数量", "标准单位", "每包装标准量", "质检状态", "库位", "供应商", "入库日期", "状态", "备注"};
        for (int i = 0; i < headers.length; i++) {
            header.createCell(i).setCellValue(headers[i]);
        }

        Row demo = sheet.createRow(1);
        // 注意：物料编号必须是原材料代码表中存在的标准料号（宽度单独填在“宽度(mm)”列）
        demo.createCell(0).setCellValue("BOPPM-T25");
        demo.createCell(1).setCellValue("20260424-A01");
        demo.createCell(2).setCellValue("R0001");
        demo.createCell(3).setCellValue(25);
        demo.createCell(4).setCellValue(1040);
        demo.createCell(5).setCellValue(6000);
        demo.createCell(6).setCellValue(6240);
        demo.createCell(7).setCellValue("卷");
        demo.createCell(8).setCellValue(1);
        demo.createCell(9).setCellValue("㎡");
        demo.createCell(10).setCellValue(6240);
        demo.createCell(11).setCellValue("qualified");
        demo.createCell(12).setCellValue("F-01");
        demo.createCell(13).setCellValue("供应商A");
        demo.createCell(14).setCellValue("2026-04-24");
        demo.createCell(15).setCellValue("available");
        demo.createCell(16).setCellValue("明细导入示例");

        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment;filename=" + URLEncoder.encode("薄膜库存导入模板-明细.xlsx", "UTF-8"));
        workbook.write(response.getOutputStream());
        workbook.close();
    }

    /**
     * 按导入模板格式导出当前薄膜库存明细（可修改后回导）
     */
    @GetMapping("/export/import-format")
    @PreAuthorize("hasAnyAuthority('admin','warehouse')")
    public void exportImportFormat(
            @RequestParam(required = false) Integer thickness,
            @RequestParam(required = false) String materialCode,
            HttpServletResponse response) throws IOException {
        IPage<FilmStock> page = filmStockService.getFilmStockPage(1, 100000, thickness, materialCode, "createTime", "descending");
        List<FilmStock> records = page == null ? java.util.Collections.emptyList() : page.getRecords();

        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("薄膜库存明细导出");

        Row header = sheet.createRow(0);
        String[] headers = {"物料编号", "批次号", "卷号", "厚度(μm)", "宽度(mm)", "长度(m)", "面积(㎡)", "包装单位", "包装数量", "标准单位", "每包装标准量", "质检状态", "库位", "供应商", "入库日期", "状态", "备注"};
        for (int i = 0; i < headers.length; i++) {
            header.createCell(i).setCellValue(headers[i]);
        }

        int rowIndex = 1;
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd");
        for (FilmStock stock : records) {
            List<FilmStockDetail> details = filmStockService.getDetailsByFilmStockId(stock.getId());
            for (FilmStockDetail detail : details) {
                Row row = sheet.createRow(rowIndex++);
                row.createCell(0).setCellValue(detail.getMaterialCode() == null ? "" : detail.getMaterialCode());
                row.createCell(1).setCellValue(detail.getBatchNo() == null ? "" : detail.getBatchNo());
                row.createCell(2).setCellValue(detail.getRollNo() == null ? "" : detail.getRollNo());
                row.createCell(3).setCellValue(detail.getThickness() == null ? "" : detail.getThickness().toPlainString());
                row.createCell(4).setCellValue(detail.getWidth() == null ? "" : String.valueOf(detail.getWidth()));
                row.createCell(5).setCellValue(detail.getLength() == null ? "" : String.valueOf(detail.getLength()));
                row.createCell(6).setCellValue(detail.getArea() == null ? 0 : detail.getArea().doubleValue());
                row.createCell(7).setCellValue(detail.getPackUom() == null ? "卷" : detail.getPackUom());
                row.createCell(8).setCellValue(detail.getPackCount() == null ? 1 : detail.getPackCount());
                row.createCell(9).setCellValue(detail.getStdUom() == null ? "㎡" : detail.getStdUom());
                row.createCell(10).setCellValue(detail.getStdQtyPerPack() == null ? 0 : detail.getStdQtyPerPack().doubleValue());
                row.createCell(11).setCellValue(detail.getQcStatus() == null ? "qualified" : detail.getQcStatus());
                row.createCell(12).setCellValue(detail.getLocation() == null ? "" : detail.getLocation());
                row.createCell(13).setCellValue(detail.getSupplier() == null ? "" : detail.getSupplier());
                row.createCell(14).setCellValue(detail.getInboundDate() == null ? "" : sdf.format(detail.getInboundDate()));
                row.createCell(15).setCellValue(detail.getStatus() == null ? "available" : detail.getStatus());
                row.createCell(16).setCellValue(detail.getRemark() == null ? "" : detail.getRemark());
            }
        }

        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment;filename=" + URLEncoder.encode("薄膜库存明细导出-可回导.xlsx", "UTF-8"));
        workbook.write(response.getOutputStream());
        workbook.close();
    }

    /** 导入薄膜库存汇总 */
    @PostMapping("/import")
    @PreAuthorize("hasAnyAuthority('admin','warehouse')")
    public ResponseResult<Map<String, Object>> importExcel(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "clearBeforeImport", defaultValue = "false") boolean clearBeforeImport
    ) {
        try {
            Map<String, Object> result = filmStockService.importExcel(file, clearBeforeImport);
            return new ResponseResult<>(20000, "导入完成", result);
        } catch (Exception e) {
            return new ResponseResult<>(50000, "导入失败: " + e.getMessage(), null);
        }
    }

    /** 清空薄膜库存（用于重新盘点后全量导入） */
    @PostMapping("/clear-for-reimport")
    @PreAuthorize("hasAnyAuthority('admin','warehouse')")
    public ResponseResult<Map<String, Object>> clearForReimport(
            @RequestParam(value = "clearOutbound", defaultValue = "true") boolean clearOutbound
    ) {
        try {
            Map<String, Object> result = filmStockService.clearForReimport(clearOutbound);
            return new ResponseResult<>(20000, "薄膜库存已清空", result);
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
            headers.setContentDispositionFormData("attachment", "film_stock_skipped_data.xlsx");
            
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
}
