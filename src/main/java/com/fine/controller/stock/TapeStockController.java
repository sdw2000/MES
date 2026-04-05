package com.fine.controller.stock;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.fine.Utils.ResponseResult;
import com.fine.modle.stock.*;
import com.fine.service.stock.TapeStockService;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLEncoder;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 胶带库存管理控制器
 */
@RestController
@RequestMapping("/api/tape-stock")
@PreAuthorize("hasAnyAuthority('warehouse','admin','sales','production','finance','quality')")
public class TapeStockController {
    
    @Autowired
    private TapeStockService stockService;
    
    // ============= 库存管理 =============
    
    /**
     * 分页查询库存
     */
    @GetMapping("/list")
    @PreAuthorize("hasAnyAuthority('warehouse','admin','sales','finance','quality')")
    public ResponseResult<?> getStockList(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String qrCode,
            @RequestParam(required = false) String materialCode,
            @RequestParam(required = false) String rollType,
            @RequestParam(required = false) String location) {        IPage<TapeStock> result = stockService.getStockPage(page, size, qrCode, materialCode, rollType, location);
        Map<String, Object> data = new HashMap<>();
        data.put("records", result.getRecords());
        data.put("total", result.getTotal());
        data.put("current", result.getCurrent());
        data.put("size", result.getSize());
        data.put("pages", result.getPages());
        return ResponseResult.success("查询成功", data);
    }

    /**
    * 成品（分切卷）库存快捷查询
     */
    @GetMapping("/finished/list")
    @PreAuthorize("hasAnyAuthority('warehouse','admin','sales','production','finance','quality')")
    public ResponseResult<?> getFinishedStockList(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String materialCode,
            @RequestParam(required = false) String location) {
        IPage<TapeStock> result = stockService.getStockPage(page, size, null, materialCode, "分切卷", location);
        Map<String, Object> data = new HashMap<>();
        data.put("records", result.getRecords());
        data.put("total", result.getTotal());
        data.put("current", result.getCurrent());
        data.put("size", result.getSize());
        data.put("pages", result.getPages());
        return ResponseResult.success("查询成功", data);
    }
    
    /**
    * 根据二维码查询库存（扫码查询）
     */
    @GetMapping("/scan/{qrCode}")
    @PreAuthorize("hasAnyAuthority('warehouse','admin','sales','production','finance','quality')")
    public ResponseResult<?> getStockByQrCode(@PathVariable String qrCode) {
        // 先按二维码查，再按批次号查
        LambdaQueryWrapper<TapeStock> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TapeStock::getStatus, 1)
               .and(w -> w.eq(TapeStock::getQrCode, qrCode)
                         .or().eq(TapeStock::getBatchNo, qrCode));        TapeStock stock = stockMapper.selectOne(wrapper);
        if (stock == null) {
            return ResponseResult.error("未找到该物料");
        }
        return ResponseResult.success("查询成功", stock);
    }
    
    @Autowired
    private com.fine.Dao.stock.TapeStockMapper stockMapper;
    
    /**
    * 按料号汇总库存
     */
    @GetMapping("/summary")
    @PreAuthorize("hasAnyAuthority('warehouse','admin','sales','finance','quality')")
    public ResponseResult<?> getStockSummary() {
        List<TapeStock> list = stockService.getStockSummary();
        return ResponseResult.success("查询成功", list);
    }

    /**
    * 按料号汇总库存（分页）
     */
    @GetMapping("/summary/page")
    @PreAuthorize("hasAnyAuthority('warehouse','admin','sales','finance','quality')")
    public ResponseResult<?> getStockSummaryPage(
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String materialCode) {
        IPage<TapeStock> result = stockService.getStockSummaryPage(current, size, materialCode);
        Map<String, Object> data = new HashMap<>();
        data.put("records", result.getRecords());
        data.put("total", result.getTotal());
        data.put("current", result.getCurrent());
        data.put("size", result.getSize());
        data.put("pages", result.getPages());
        return ResponseResult.success("查询成功", data);
    }
    
    /**
    * 根据料号查询所有批次（FIFO排序）
     */
    @GetMapping("/by-material/{materialCode}")
    public ResponseResult<?> getStockByMaterial(@PathVariable String materialCode) {
        List<TapeStock> list = stockService.getStockByMaterialFIFO(materialCode);
        return ResponseResult.success("查询成功", list);
    }

    /**
    * 根据料号查询库存明细（分页）
     */
    @GetMapping("/by-material/page")
    public ResponseResult<?> getStockByMaterialPage(
            @RequestParam String materialCode,
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "20") int size) {
        IPage<TapeStock> result = stockService.getStockByMaterialPage(current, size, materialCode);
        Map<String, Object> data = new HashMap<>();
        data.put("records", result.getRecords());
        data.put("total", result.getTotal());
        data.put("current", result.getCurrent());
        data.put("size", result.getSize());
        data.put("pages", result.getPages());
        return ResponseResult.success("查询成功", data);
    }
    
    /**
     * 根据ID查询库存详情
     */
    @GetMapping("/{id}")
    public ResponseResult<?> getStockById(@PathVariable Long id) {
        TapeStock stock = stockService.getStockById(id);
        return ResponseResult.success("查询成功", stock);
    }
    
    /**
     * 导入Excel库存数据
     */
    @PostMapping("/import")
    @PreAuthorize("hasAnyAuthority('warehouse','admin')")
    public ResponseResult<?> importExcel(@RequestParam("file") MultipartFile file) {
        try {
            Map<String, Object> result = stockService.importExcel(file);
            if ((boolean) result.get("success")) {
                return ResponseResult.success("导入成功", result);
            } else {
                return ResponseResult.error(50000, (String) result.get("message"));
            }
        } catch (Exception e) {
            return ResponseResult.error("导入失败: " + e.getMessage());
        }
    }
    
    /**
     * 导出库存数据
     */
    @GetMapping("/export")
    public void exportStock(
            @RequestParam(required = false) String materialCode,
            @RequestParam(required = false) String location,
            HttpServletResponse response) throws IOException {
        List<TapeStock> list = stockService.exportStock(materialCode, location);
        
        // 创建Excel
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("库存数据");
        
        // 表头
        Row header = sheet.createRow(0);
        String[] headers = {"料号", "产品名称", "生产批次号", "二维码", "卷类型", "厚度μm", "宽度mm", 
                   "长度M", "原始长度", "当前长度", "库存卷数", "总平米数", "卡板号", "生产日期"};
        for (int i = 0; i < headers.length; i++) {
            header.createCell(i).setCellValue(headers[i]);
        }
        
        // 数据
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        for (int i = 0; i < list.size(); i++) {
            TapeStock stock = list.get(i);
            Row row = sheet.createRow(i + 1);
            row.createCell(0).setCellValue(stock.getMaterialCode() != null ? stock.getMaterialCode() : "");
            row.createCell(1).setCellValue(stock.getProductName() != null ? stock.getProductName() : "");
            row.createCell(2).setCellValue(stock.getBatchNo() != null ? stock.getBatchNo() : "");
            row.createCell(3).setCellValue(stock.getQrCode() != null ? stock.getQrCode() : stock.getBatchNo());
            row.createCell(4).setCellValue(stock.getRollType() != null ? stock.getRollType() : "母卷");
            row.createCell(5).setCellValue(stock.getThickness() != null ? stock.getThickness() : 0);
            row.createCell(6).setCellValue(stock.getWidth() != null ? stock.getWidth() : 0);
            row.createCell(7).setCellValue(stock.getLength() != null ? stock.getLength() : 0);
            row.createCell(8).setCellValue(stock.getOriginalLength() != null ? stock.getOriginalLength() : (stock.getLength() != null ? stock.getLength() : 0));
            row.createCell(9).setCellValue(stock.getCurrentLength() != null ? stock.getCurrentLength() : (stock.getLength() != null ? stock.getLength() : 0));
            row.createCell(10).setCellValue(stock.getTotalRolls() != null ? stock.getTotalRolls() : 0);
            row.createCell(11).setCellValue(stock.getTotalSqm() != null ? stock.getTotalSqm().doubleValue() : 0);
            row.createCell(12).setCellValue(stock.getLocation() != null ? stock.getLocation() : "");
            row.createCell(13).setCellValue(stock.getProdDate() != null ? stock.getProdDate().format(dtf) : "");
        }
        
        // 输出
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment;filename=" + 
                URLEncoder.encode("库存数据.xlsx", "UTF-8"));
        workbook.write(response.getOutputStream());
        workbook.close();
    }
    
    /**
     * 下载导入模板
     */
    @GetMapping("/template")
    public void downloadTemplate(HttpServletResponse response) throws IOException {
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("库存导入模板");
        
        // 表头
        Row header = sheet.createRow(0);
        String[] headers = {"料号", "产品名称", "生产批次号", "二维码", "卷类型", "厚度μm", "宽度mm", "长度M", 
                   "库存卷数", "卡板号", "生产年份", "生产月份", "生产日期", "备注"};
        for (int i = 0; i < headers.length; i++) {
            header.createCell(i).setCellValue(headers[i]);
        }
        
        // 示例数据
        Row row = sheet.createRow(1);
        row.createCell(0).setCellValue("1011-R02-2307-G03-0350");
        row.createCell(1).setCellValue("30u无机翠绿PET胶带");
        row.createCell(2).setCellValue("2601032B01");
        row.createCell(3).setCellValue("2601032B01");  // 二维码默认批次号
        row.createCell(4).setCellValue("母卷");         // 卷类型
        row.createCell(5).setCellValue(30);
        row.createCell(6).setCellValue(500);
        row.createCell(7).setCellValue(6010);
        row.createCell(8).setCellValue(2);
        row.createCell(9).setCellValue("18");
        row.createCell(10).setCellValue(26);
        row.createCell(11).setCellValue(1);
        row.createCell(12).setCellValue(3);
        row.createCell(13).setCellValue("");
        
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment;filename=" + 
                URLEncoder.encode("库存导入模板.xlsx", "UTF-8"));
        workbook.write(response.getOutputStream());
        workbook.close();
    }
    
    // ============= 入库申请 =============
    
    /**
     * 分页查询入库申请
     */
    @GetMapping("/inbound/list")
    @PreAuthorize("hasAnyAuthority('warehouse','admin','production')")
    public ResponseResult<?> getInboundList(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String materialCode) {        IPage<TapeInboundRequest> result = stockService.getInboundPage(page, size, status, materialCode);
        Map<String, Object> data = new HashMap<>();
        data.put("records", result.getRecords());
        data.put("total", result.getTotal());
        data.put("current", result.getCurrent());
        data.put("size", result.getSize());
        return ResponseResult.success("查询成功", data);
    }
    
    /**
     * 创建入库申请
     */
    @PostMapping("/inbound")
    @PreAuthorize("hasAnyAuthority('warehouse','admin','production')")    public ResponseResult<?> createInboundRequest(@RequestBody TapeInboundRequest request) {
        try {
            TapeInboundRequest result = stockService.createInboundRequest(request);
            return ResponseResult.success("申请提交成功", result);
        } catch (Exception e) {
            return ResponseResult.error("申请失败: " + e.getMessage());
        }
    }
    
    /**
     * 审批入库申请
     */
    @PostMapping("/inbound/{id}/approve")
    @PreAuthorize("hasAnyAuthority('admin', 'warehouse')")
    public ResponseResult<?> approveInbound(
            @PathVariable Long id,
            @RequestParam boolean approved,
            @RequestParam String auditor,
            @RequestParam(required = false) String auditRemark,
            @RequestParam(required = false) String scannedRollCode,
            @RequestParam(required = false) String scannedLocation) {        try {
            stockService.approveInbound(id, approved, auditor, auditRemark, scannedRollCode, scannedLocation);
            return ResponseResult.success(approved ? "审批通过" : "已拒绝", null);
        } catch (Exception e) {
            return ResponseResult.error("审批失败: " + e.getMessage());
        }
    }

    /**
     * 批量扫码审批入库（同一卡板）
     */
    @PostMapping("/inbound/approve-by-roll-codes")
    @PreAuthorize("hasAnyAuthority('admin', 'warehouse')")
    public ResponseResult<?> approveInboundByRollCodes(@RequestBody Map<String, Object> params) {
        try {
            Object codesObj = params.get("rollCodes");
            List<String> rollCodes = new java.util.ArrayList<>();
            if (codesObj instanceof List) {
                for (Object o : (List<?>) codesObj) {
                    if (o != null) {
                        rollCodes.add(String.valueOf(o));
                    }
                }
            }
            String auditor = params.get("auditor") == null ? null : String.valueOf(params.get("auditor"));
            String auditRemark = params.get("auditRemark") == null ? null : String.valueOf(params.get("auditRemark"));
            String scannedLocation = params.get("scannedLocation") == null ? null : String.valueOf(params.get("scannedLocation"));

            Map<String, Object> result = stockService.approveInboundByRollCodes(rollCodes, auditor, auditRemark, scannedLocation);
            return ResponseResult.success("批量审批完成", result);
        } catch (Exception e) {
            return ResponseResult.error("批量审批失败: " + e.getMessage());
        }
    }
    
    /**
     * 取消入库申请
     */
    @PostMapping("/inbound/{id}/cancel")
    @PreAuthorize("hasAnyAuthority('warehouse','admin','production')")    public ResponseResult<?> cancelInbound(@PathVariable Long id) {
        try {
            stockService.cancelInbound(id);
            return ResponseResult.success("已取消", null);
        } catch (Exception e) {
            return ResponseResult.error("取消失败: " + e.getMessage());
        }
    }
    
    /**
    * 待审批入库数量
     */
    @GetMapping("/inbound/pending-count")
    @PreAuthorize("hasAnyAuthority('warehouse','admin','production')")    public ResponseResult<?> countPendingInbound() {
        int count = stockService.countPendingInbound();
        return ResponseResult.success("查询成功", count);
    }
    
    // ============= 出库申请 =============
    
    /**
     * 分页查询出库申请
     */
    @GetMapping("/outbound/list")
    @PreAuthorize("hasAnyAuthority('warehouse','admin','sales','finance','quality')")
    public ResponseResult<?> getOutboundList(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String materialCode) {        IPage<TapeOutboundRequest> result = stockService.getOutboundPage(page, size, status, materialCode);
        Map<String, Object> data = new HashMap<>();
        data.put("records", result.getRecords());
        data.put("total", result.getTotal());
        data.put("current", result.getCurrent());
        data.put("size", result.getSize());
        return ResponseResult.success("查询成功", data);
    }
    
    /**
    * 创建出库申请（手动选择批次）
     */
    @PostMapping("/outbound")
    @PreAuthorize("hasAnyAuthority('warehouse','admin','sales','finance','quality')")    public ResponseResult<?> createOutboundRequest(@RequestBody TapeOutboundRequest request) {
        try {
            TapeOutboundRequest result = stockService.createOutboundRequest(request);
            return ResponseResult.success("申请提交成功", result);
        } catch (Exception e) {
            return ResponseResult.error("申请失败: " + e.getMessage());
        }
    }
    
    /**
    * 创建出库申请（FIFO自动分配）
     */
    @PostMapping("/outbound/fifo")
    @PreAuthorize("hasAnyAuthority('warehouse','admin','sales','finance','quality')")
    public ResponseResult<?> createOutboundRequestFIFO(
            @RequestParam String materialCode,
            @RequestParam int totalRolls,
            @RequestParam String applicant,
            @RequestParam(required = false) String applyDept,
            @RequestParam(required = false) String remark) {        try {
            List<TapeOutboundRequest> result = stockService.createOutboundRequestFIFO(
                    materialCode, totalRolls, applicant, applyDept, remark);
            return ResponseResult.success("申请提交成功，共分配" + result.size() + "个批次", result);
        } catch (Exception e) {
            return ResponseResult.error("申请失败: " + e.getMessage());
        }
    }
    
    /**
     * 审批出库申请
     */
    @PostMapping("/outbound/{id}/approve")
    @PreAuthorize("hasAnyAuthority('admin', 'warehouse')")
    public ResponseResult<?> approveOutbound(
            @PathVariable Long id,
            @RequestParam boolean approved,
            @RequestParam String auditor,
            @RequestParam(required = false) String auditRemark,
            @RequestParam(required = false) String scannedRollCode) {        try {
            stockService.approveOutbound(id, approved, auditor, auditRemark, scannedRollCode);
            return ResponseResult.success(approved ? "审批通过" : "已拒绝", null);
        } catch (Exception e) {
            return ResponseResult.error("审批失败: " + e.getMessage());
        }
    }

    /**
     * 批量扫码审批出库
     */
    @PostMapping("/outbound/approve-by-roll-codes")
    @PreAuthorize("hasAnyAuthority('admin', 'warehouse')")
    public ResponseResult<?> approveOutboundByRollCodes(@RequestBody Map<String, Object> params) {
        try {
            Object codesObj = params.get("rollCodes");
            List<String> rollCodes = new java.util.ArrayList<>();
            if (codesObj instanceof List) {
                for (Object o : (List<?>) codesObj) {
                    if (o != null) {
                        rollCodes.add(String.valueOf(o));
                    }
                }
            }
            String auditor = params.get("auditor") == null ? null : String.valueOf(params.get("auditor"));
            String auditRemark = params.get("auditRemark") == null ? null : String.valueOf(params.get("auditRemark"));

            Map<String, Object> result = stockService.approveOutboundByRollCodes(rollCodes, auditor, auditRemark);
            return ResponseResult.success("批量审批完成", result);
        } catch (Exception e) {
            return ResponseResult.error("批量审批失败: " + e.getMessage());
        }
    }
    
    /**
     * 取消出库申请
     */
    @PostMapping("/outbound/{id}/cancel")
    @PreAuthorize("hasAnyAuthority('warehouse','admin','sales','finance','quality')")    public ResponseResult<?> cancelOutbound(@PathVariable Long id) {
        try {
            stockService.cancelOutbound(id);
            return ResponseResult.success("已取消", null);
        } catch (Exception e) {
            return ResponseResult.error("取消失败: " + e.getMessage());
        }
    }
    
    /**
    * 待审批出库数量
     */
    @GetMapping("/outbound/pending-count")
    @PreAuthorize("hasAnyAuthority('warehouse','admin','sales','finance','quality')")    public ResponseResult<?> countPendingOutbound() {
        int count = stockService.countPendingOutbound();
        return ResponseResult.success("查询成功", count);
    }
    
    // ============= 库存流水 =============
    
    /**
     * 分页查询库存流水
     */
    @GetMapping("/log/list")
    @PreAuthorize("hasAnyAuthority('warehouse','admin','sales','production','finance','quality')")
    public ResponseResult<?> getStockLogList(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String materialCode,
            @RequestParam(required = false) String batchNo) {        IPage<TapeStockLog> result = stockService.getStockLogPage(page, size, type, materialCode, batchNo);
        Map<String, Object> data = new HashMap<>();
        data.put("records", result.getRecords());
        data.put("total", result.getTotal());
        data.put("current", result.getCurrent());
        data.put("size", result.getSize());
        return ResponseResult.success("查询成功", data);
    }
    
    /**
     * 导出库存流水
     */
    @GetMapping("/log/export")
    public void exportStockLog(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String materialCode,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            HttpServletResponse response) throws IOException {
        List<TapeStockLog> list = stockService.exportStockLog(type, materialCode, startDate, endDate);
        
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("库存流水");
        
        Row header = sheet.createRow(0);
        String[] headers = {"时间", "类型", "料号", "产品名称", "批次号", 
                   "变动卷数", "变动前", "变动后", "关联单号", "操作人", "备注"};
        for (int i = 0; i < headers.length; i++) {
            header.createCell(i).setCellValue(headers[i]);
        }
        
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        for (int i = 0; i < list.size(); i++) {
            TapeStockLog log = list.get(i);
            Row row = sheet.createRow(i + 1);
            row.createCell(0).setCellValue(log.getCreateTime() != null ? log.getCreateTime().format(dtf) : "");
            row.createCell(1).setCellValue(getTypeName(log.getType()));
            row.createCell(2).setCellValue(log.getMaterialCode() != null ? log.getMaterialCode() : "");
            row.createCell(3).setCellValue(log.getProductName() != null ? log.getProductName() : "");
            row.createCell(4).setCellValue(log.getBatchNo() != null ? log.getBatchNo() : "");
            row.createCell(5).setCellValue(log.getChangeRolls() != null ? log.getChangeRolls() : 0);
            row.createCell(6).setCellValue(log.getBeforeRolls() != null ? log.getBeforeRolls() : 0);
            row.createCell(7).setCellValue(log.getAfterRolls() != null ? log.getAfterRolls() : 0);
            row.createCell(8).setCellValue(log.getRefNo() != null ? log.getRefNo() : "");
            row.createCell(9).setCellValue(log.getOperator() != null ? log.getOperator() : "");
            row.createCell(10).setCellValue(log.getRemark() != null ? log.getRemark() : "");
        }
        
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment;filename=" + 
                URLEncoder.encode("库存流水.xlsx", "UTF-8"));
        workbook.write(response.getOutputStream());
        workbook.close();
    }
    
    private String getTypeName(String type) {
        if (type == null) return "";
        switch (type) {
            case "IN": return "入库";
            case "OUT": return "出库";
            case "ADJUST": return "调整";
            default: return type;
        }
    }
}
