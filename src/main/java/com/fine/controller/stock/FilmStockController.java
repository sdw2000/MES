package com.fine.controller.stock;

import com.baomidou.mybatisplus.core.metadata.IPage;
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
            @RequestParam(required = false) Integer thickness
    ) {
        IPage<FilmStock> page = filmStockService.getFilmStockPage(current, size, thickness);
        return new ResponseResult<>(20000, "查询成功", page);
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
     * 查询薄膜出库记录
     */
    @GetMapping("/outbound/schedule/{scheduleId}")
    public ResponseResult<List<FilmStockOut>> getOutboundBySchedule(@PathVariable Long scheduleId) {
        List<FilmStockOut> records = filmStockService.getOutboundByScheduleId(scheduleId);
        return new ResponseResult<>(20000, "查询成功", records);
    }

    /** 锁定薄膜库存 */
    @PostMapping("/lock")
    @PreAuthorize("hasAnyAuthority('admin','warehouse','production')")
    public ResponseResult<Boolean> lockStock(@RequestBody Map<String, Object> payload) {
        try {
            Long filmStockId = toLong(payload.get("filmStockId"));
            BigDecimal lockArea = toBigDecimal(payload.get("lockArea"));
            Integer lockRolls = toInteger(payload.get("lockRolls"));
            List<Long> detailIds = toLongList(payload.get("detailIds"));

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

    /** 下载薄膜库存导入模板 */
    @GetMapping("/template")
    @PreAuthorize("hasAnyAuthority('admin','warehouse')")
    public void downloadTemplate(HttpServletResponse response) throws IOException {
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("薄膜库存导入模板");

        Row header = sheet.createRow(0);
        String[] headers = {"物料编号", "物料名称", "厚度(μm)", "宽度(mm)", "规格描述", "总面积(㎡)", "可用面积(㎡)", "锁定面积(㎡)", "总卷数", "可用卷数", "锁定卷数", "安全库存(㎡)", "状态", "备注"};
        for (int i = 0; i < headers.length; i++) {
            header.createCell(i).setCellValue(headers[i]);
        }

        Row demo = sheet.createRow(1);
        demo.createCell(0).setCellValue("BOPPM-T25-1040");
        demo.createCell(1).setCellValue("BOPP膜 25μm*1040mm");
        demo.createCell(2).setCellValue(25);
        demo.createCell(3).setCellValue(1040);
        demo.createCell(4).setCellValue("25*1040");
        demo.createCell(5).setCellValue(12000);
        demo.createCell(6).setCellValue(11000);
        demo.createCell(7).setCellValue(1000);
        demo.createCell(8).setCellValue(120);
        demo.createCell(9).setCellValue(110);
        demo.createCell(10).setCellValue(10);
        demo.createCell(11).setCellValue(3000);
        demo.createCell(12).setCellValue("active");
        demo.createCell(13).setCellValue("导入示例");

        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment;filename=" + URLEncoder.encode("薄膜库存导入模板.xlsx", "UTF-8"));
        workbook.write(response.getOutputStream());
        workbook.close();
    }

    /** 导入薄膜库存汇总 */
    @PostMapping("/import")
    @PreAuthorize("hasAnyAuthority('admin','warehouse')")
    public ResponseResult<Map<String, Object>> importExcel(@RequestParam("file") MultipartFile file) {
        try {
            Map<String, Object> result = filmStockService.importExcel(file);
            return new ResponseResult<>(20000, "导入完成", result);
        } catch (Exception e) {
            return new ResponseResult<>(50000, "导入失败: " + e.getMessage(), null);
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
