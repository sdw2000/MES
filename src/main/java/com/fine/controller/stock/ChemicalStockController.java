package com.fine.controller.stock;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.fine.Utils.ResponseResult;
import com.fine.model.stock.ChemicalStock;
import com.fine.model.stock.ChemicalStockDetail;
import com.fine.model.stock.ChemicalStockOut;
import com.fine.service.stock.ChemicalStockService;
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
            @RequestParam(required = false) String chemicalType
    ) {
        IPage<ChemicalStock> page = chemicalStockService.getChemicalStockPage(current, size, chemicalType);
        return new ResponseResult<>(20000, "查询成功", page);
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
    
    /** 查询可用的化工明细 */
    @GetMapping("/{id}/available")
    public ResponseResult<List<ChemicalStockDetail>> getAvailableDetails(@PathVariable Long id) {
        List<ChemicalStockDetail> details = chemicalStockService.getAvailableDetails(id);
        return new ResponseResult<>(20000, "查询成功", details);
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

    /** 锁定化工库存 */
    @PostMapping("/lock")
    @PreAuthorize("hasAnyAuthority('admin','warehouse','production')")
    public ResponseResult<Boolean> lockStock(@RequestBody Map<String, Object> payload) {
        try {
            Long chemicalStockId = toLong(payload.get("chemicalStockId"));
            Integer lockQuantity = toInteger(payload.get("lockQuantity"));
            List<Long> detailIds = toLongList(payload.get("detailIds"));

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
            Integer unlockQuantity = toInteger(payload.get("unlockQuantity"));
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
            out.setOutQuantity(toInteger(payload.get("outQuantity")));
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

    /** 下载化工库存导入模板 */
    @GetMapping("/template")
    @PreAuthorize("hasAnyAuthority('admin','warehouse')")
    public void downloadTemplate(HttpServletResponse response) throws IOException {
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("化工库存导入模板");

        Row header = sheet.createRow(0);
        String[] headers = {"物料编号", "单位", "单桶重量(kg)", "总重量", "桶数"};
        for (int i = 0; i < headers.length; i++) {
            header.createCell(i).setCellValue(headers[i]);
        }

        Row demo = sheet.createRow(1);
        demo.createCell(0).setCellValue("FN8558");
        demo.createCell(1).setCellValue("Kg");
        demo.createCell(2).setCellValue(150);
        demo.createCell(3).setCellValue(150);
        demo.createCell(4).setCellValue(1);

        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment;filename=" + URLEncoder.encode("化工库存导入模板.xlsx", "UTF-8"));
        workbook.write(response.getOutputStream());
        workbook.close();
    }

    /** 导入化工库存汇总 */
    @PostMapping("/import")
    @PreAuthorize("hasAnyAuthority('admin','warehouse')")
    public ResponseResult<Map<String, Object>> importExcel(@RequestParam("file") MultipartFile file) {
        try {
            Map<String, Object> result = chemicalStockService.importExcel(file);
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
