package com.fine.controller.finance;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.fine.Utils.ResponseResult;
import com.fine.service.finance.FinanceAccountingService;
import com.fine.serviceIMPL.finance.FinanceInventoryPriceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/finance")
@PreAuthorize("hasAnyAuthority('admin','finance')")
public class FinanceAccountingController {

    @Autowired
    private FinanceAccountingService financeAccountingService;

    @Autowired
    private FinanceInventoryPriceService financeInventoryPriceService;

    @GetMapping("/cost-accounting/coating")
    public ResponseResult<?> getCoatingCostAccounting(@RequestParam String month,
                                                      @RequestParam(defaultValue = "1") Integer pageNum,
                                                      @RequestParam(defaultValue = "20") Integer pageSize) {
        try {
            return new ResponseResult<>(200, "查询成功", financeAccountingService.getCoatingCostAccounting(month, pageNum, pageSize));
        } catch (Exception e) {
            return new ResponseResult<>(500, "查询失败: " + e.getMessage(), null);
        }
    }

    @GetMapping("/cost-accounting/coating-summary")
    public ResponseResult<?> getCoatingCostSummary(@RequestParam String month) {
        try {
            return new ResponseResult<>(200, "查询成功", financeAccountingService.getCoatingCostSummary(month));
        } catch (Exception e) {
            return new ResponseResult<>(500, "查询失败: " + e.getMessage(), null);
        }
    }

    @GetMapping("/cost-accounting/formula-theoretical")
    public ResponseResult<?> getFormulaTheoreticalCost(@RequestParam String month,
                                                       @RequestParam(defaultValue = "1") Integer pageNum,
                                                       @RequestParam(defaultValue = "20") Integer pageSize,
                                                       @RequestParam(required = false) String keyword,
                                                       @RequestParam(required = false) String sortField,
                                                       @RequestParam(required = false) String sortOrder) {
        try {
            return new ResponseResult<>(200, "查询成功", financeAccountingService.getFormulaTheoreticalCost(month, pageNum, pageSize, keyword, sortField, sortOrder));
        } catch (Exception e) {
            return new ResponseResult<>(500, "查询失败: " + e.getMessage(), null);
        }
    }

    @GetMapping("/cost-accounting/formula-factor")
    public ResponseResult<?> getFormulaCostFactor(@RequestParam String month) {
        try {
            return new ResponseResult<>(200, "查询成功", financeAccountingService.getFormulaCostFactor(month));
        } catch (Exception e) {
            return new ResponseResult<>(500, "查询失败: " + e.getMessage(), null);
        }
    }

    @PostMapping("/cost-accounting/formula-factor")
    public ResponseResult<?> saveFormulaCostFactor(@RequestBody Map<String, Object> payload) {
        try {
            return new ResponseResult<>(200, "保存成功", financeAccountingService.saveFormulaCostFactor(payload));
        } catch (Exception e) {
            return new ResponseResult<>(500, "保存失败: " + e.getMessage(), null);
        }
    }

    @GetMapping("/cost-accounting/material-config")
    public ResponseResult<?> getMaterialCostConfig(@RequestParam(required = false) String month,
                                                   @RequestParam(required = false) String keyword,
                                                   @RequestParam(defaultValue = "1") Integer pageNum,
                                                   @RequestParam(defaultValue = "20") Integer pageSize) {
        try {
            IPage<Map<String, Object>> page = financeAccountingService.getMaterialCostConfigPage(month, keyword, pageNum, pageSize);
            return new ResponseResult<>(200, "查询成功", page);
        } catch (Exception e) {
            return new ResponseResult<>(500, "查询失败: " + e.getMessage(), null);
        }
    }

    @PostMapping("/cost-accounting/material-config")
    public ResponseResult<?> saveMaterialCostConfig(@RequestBody Map<String, Object> payload) {
        try {
            return new ResponseResult<>(200, "保存成功", financeAccountingService.saveMaterialCostConfig(payload));
        } catch (Exception e) {
            return new ResponseResult<>(500, "保存失败: " + e.getMessage(), null);
        }
    }

    @PostMapping("/cost-accounting/inventory-price/recalc")
    public ResponseResult<?> recalcInventoryPrice(@RequestParam(required = false) String bizDate) {
        try {
            LocalDate date = (bizDate == null || bizDate.trim().isEmpty()) ? LocalDate.now() : LocalDate.parse(bizDate.trim());
            return new ResponseResult<>(200, "重算成功", financeInventoryPriceService.recalcByDate(date));
        } catch (Exception e) {
            return new ResponseResult<>(500, "重算失败: " + e.getMessage(), null);
        }
    }

    @PostMapping("/cost-accounting/inventory-price/init-sync")
    public ResponseResult<?> initInventoryPriceSync(@RequestParam(required = false) String bizDate) {
        try {
            LocalDate date = (bizDate == null || bizDate.trim().isEmpty()) ? LocalDate.now() : LocalDate.parse(bizDate.trim());
            return new ResponseResult<>(200, "初始化成功", financeInventoryPriceService.initFromCurrentStock(date));
        } catch (Exception e) {
            return new ResponseResult<>(500, "初始化失败: " + e.getMessage(), null);
        }
    }

    @GetMapping("/cost-accounting/inventory-price/latest")
    public ResponseResult<?> getInventoryPriceLatest(@RequestParam(required = false) String keyword,
                                                     @RequestParam(required = false) String sortField,
                                                     @RequestParam(required = false) String sortOrder,
                                                     @RequestParam(defaultValue = "1") Integer pageNum,
                                                     @RequestParam(defaultValue = "20") Integer pageSize) {
        try {
            return new ResponseResult<>(200, "查询成功", financeInventoryPriceService.getLatestPage(keyword, pageNum, pageSize, sortField, sortOrder));
        } catch (Exception e) {
            return new ResponseResult<>(500, "查询失败: " + e.getMessage(), null);
        }
    }

    @GetMapping("/cost-accounting/inventory-price/change")
    public ResponseResult<?> getInventoryPriceChange(@RequestParam(required = false) String bizDate,
                                                     @RequestParam(required = false) String keyword,
                                                     @RequestParam(required = false) String sortField,
                                                     @RequestParam(required = false) String sortOrder,
                                                     @RequestParam(defaultValue = "1") Integer pageNum,
                                                     @RequestParam(defaultValue = "20") Integer pageSize) {
        try {
            return new ResponseResult<>(200, "查询成功", financeInventoryPriceService.getChangePage(bizDate, keyword, pageNum, pageSize, sortField, sortOrder));
        } catch (Exception e) {
            return new ResponseResult<>(500, "查询失败: " + e.getMessage(), null);
        }
    }

    @GetMapping("/cost-accounting/inventory-price/latest/export")
    public void exportInventoryPriceLatest(@RequestParam(required = false) String keyword,
                                           @RequestParam(required = false) String sortField,
                                           @RequestParam(required = false) String sortOrder,
                                           HttpServletResponse response) throws IOException {
        List<Map<String, Object>> rows = financeInventoryPriceService.exportLatest(keyword, sortField, sortOrder);
        List<String> headers = new ArrayList<>();
        headers.add("料号");
        headers.add("物料名称");
        headers.add("单位");
        headers.add("物料数量");
        headers.add("单价");
        headers.add("库存总金额");
        headers.add("版本");
        headers.add("最近重算时间");
        headers.add("最近入库日期");

        List<List<Object>> lines = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            List<Object> line = new ArrayList<>();
            line.add(row.get("materialCode"));
            line.add(row.get("materialName"));
            line.add(row.get("uom"));
            line.add(row.get("stockQty"));
            line.add(row.get("avgUnitPrice"));
            line.add(row.get("stockAmount"));
            line.add(row.get("version"));
            line.add(row.get("lastRecalcTime"));
            line.add(row.get("lastInboundDate"));
            lines.add(line);
        }
        writeCsv(response, "inventory_price_latest.csv", headers, lines);
    }

    @GetMapping("/cost-accounting/inventory-price/change/export")
    public void exportInventoryPriceChange(@RequestParam(required = false) String bizDate,
                                           @RequestParam(required = false) String keyword,
                                           @RequestParam(required = false) String sortField,
                                           @RequestParam(required = false) String sortOrder,
                                           HttpServletResponse response) throws IOException {
        List<Map<String, Object>> rows = financeInventoryPriceService.exportChange(bizDate, keyword, sortField, sortOrder);
        List<String> headers = new ArrayList<>();
        headers.add("业务日期");
        headers.add("料号");
        headers.add("物料名称");
        headers.add("单位");
        headers.add("旧库存数量");
        headers.add("旧库存金额");
        headers.add("旧库存价格");
        headers.add("来料数量");
        headers.add("来料金额");
        headers.add("出库数量");
        headers.add("新库存数量");
        headers.add("新库存金额");
        headers.add("新价格");
        headers.add("重算时间");

        List<List<Object>> lines = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            List<Object> line = new ArrayList<>();
            line.add(row.get("bizDate"));
            line.add(row.get("materialCode"));
            line.add(row.get("materialName"));
            line.add(row.get("uom"));
            line.add(row.get("oldStockQty"));
            line.add(row.get("oldStockAmount"));
            line.add(row.get("oldUnitPrice"));
            line.add(row.get("inQty"));
            line.add(row.get("inAmount"));
            line.add(row.get("outQty"));
            line.add(row.get("newStockQty"));
            line.add(row.get("newStockAmount"));
            line.add(row.get("newUnitPrice"));
            line.add(row.get("recalcTime"));
            lines.add(line);
        }
        writeCsv(response, "inventory_price_change.csv", headers, lines);
    }

    @PostMapping("/cost-accounting/inventory-price/latest/import")
    public ResponseResult<?> importInventoryPriceLatest(@RequestPart("file") MultipartFile file) {
        try {
            return new ResponseResult<>(200, "导入成功", financeInventoryPriceService.importLatestCsv(file));
        } catch (Exception e) {
            return new ResponseResult<>(500, "导入失败: " + e.getMessage(), null);
        }
    }

    @PostMapping("/cost-accounting/inventory-price/change/import")
    public ResponseResult<?> importInventoryPriceChange(@RequestPart("file") MultipartFile file) {
        try {
            return new ResponseResult<>(200, "导入成功", financeInventoryPriceService.importChangeCsv(file));
        } catch (Exception e) {
            return new ResponseResult<>(500, "导入失败: " + e.getMessage(), null);
        }
    }

    private void writeCsv(HttpServletResponse response,
                          String fileName,
                          List<String> headers,
                          List<List<Object>> lines) throws IOException {
        String encoded = URLEncoder.encode(fileName, StandardCharsets.UTF_8.name()).replaceAll("\\+", "%20");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("text/csv;charset=UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + encoded);
        try (OutputStreamWriter writer = new OutputStreamWriter(response.getOutputStream(), StandardCharsets.UTF_8)) {
            writer.write('\uFEFF');
            writer.write(String.join(",", headers));
            writer.write("\n");
            for (List<Object> line : lines) {
                List<String> values = new ArrayList<>();
                for (Object value : line) {
                    values.add(escapeCsv(value));
                }
                writer.write(String.join(",", values));
                writer.write("\n");
            }
            writer.flush();
        }
    }

    private String escapeCsv(Object value) {
        if (value == null) {
            return "";
        }
        String s = String.valueOf(value);
        if (s.contains("\"") || s.contains(",") || s.contains("\n") || s.contains("\r")) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }

    @GetMapping("/basic-config/monthly")
    public ResponseResult<?> getMonthlyBasicConfig(@RequestParam String month) {
        try {
            return new ResponseResult<>(200, "查询成功", financeAccountingService.getMonthlyBasicConfig(month));
        } catch (Exception e) {
            return new ResponseResult<>(500, "查询失败: " + e.getMessage(), null);
        }
    }

    @PostMapping("/basic-config/monthly")
    public ResponseResult<?> saveMonthlyBasicConfig(@RequestBody Map<String, Object> payload) {
        try {
            return new ResponseResult<>(200, "保存成功", financeAccountingService.saveMonthlyBasicConfig(payload));
        } catch (Exception e) {
            return new ResponseResult<>(500, "保存失败: " + e.getMessage(), null);
        }
    }

    @GetMapping("/salary/page")
    public ResponseResult<?> getSalaryPage(@RequestParam String month,
                                           @RequestParam(required = false) String employeeName,
                                           @RequestParam(defaultValue = "1") Integer pageNum,
                                           @RequestParam(defaultValue = "20") Integer pageSize) {
        try {
            return new ResponseResult<>(200, "查询成功", financeAccountingService.getSalaryPage(month, employeeName, pageNum, pageSize));
        } catch (Exception e) {
            return new ResponseResult<>(500, "查询失败: " + e.getMessage(), null);
        }
    }

    @PostMapping("/salary")
    public ResponseResult<?> saveSalaryRecord(@RequestBody Map<String, Object> payload) {
        try {
            return new ResponseResult<>(200, "保存成功", financeAccountingService.saveSalaryRecord(payload));
        } catch (Exception e) {
            return new ResponseResult<>(500, "保存失败: " + e.getMessage(), null);
        }
    }

    @DeleteMapping("/salary/{id}")
    public ResponseResult<?> deleteSalaryRecord(@PathVariable Long id) {
        try {
            financeAccountingService.deleteSalaryRecord(id);
            return new ResponseResult<>(200, "删除成功", null);
        } catch (Exception e) {
            return new ResponseResult<>(500, "删除失败: " + e.getMessage(), null);
        }
    }

    @GetMapping("/bank-ledger/page")
    public ResponseResult<?> getBankLedgerPage(@RequestParam String month,
                                               @RequestParam(required = false) String bankCode,
                                               @RequestParam(defaultValue = "1") Integer pageNum,
                                               @RequestParam(defaultValue = "20") Integer pageSize) {
        try {
            return new ResponseResult<>(200, "查询成功", financeAccountingService.getBankLedgerPage(month, bankCode, pageNum, pageSize));
        } catch (Exception e) {
            return new ResponseResult<>(500, "查询失败: " + e.getMessage(), null);
        }
    }

    @PostMapping("/bank-ledger")
    public ResponseResult<?> saveBankLedger(@RequestBody Map<String, Object> payload) {
        try {
            return new ResponseResult<>(200, "保存成功", financeAccountingService.saveBankLedger(payload));
        } catch (Exception e) {
            return new ResponseResult<>(500, "保存失败: " + e.getMessage(), null);
        }
    }

    @PostMapping("/bank-ledger/kingdee/push/{id}")
    public ResponseResult<?> pushBankLedgerToKingdee(@PathVariable Long id) {
        try {
            return new ResponseResult<>(200, "推送成功", financeAccountingService.pushBankLedgerToKingdee(id));
        } catch (Exception e) {
            return new ResponseResult<>(500, "推送失败: " + e.getMessage(), null);
        }
    }

    @GetMapping("/bank-ledger/kingdee/template")
    public ResponseResult<?> getKingdeeTemplate() {
        try {
            return new ResponseResult<>(200, "查询成功", financeAccountingService.getKingdeeTemplate());
        } catch (Exception e) {
            return new ResponseResult<>(500, "查询失败: " + e.getMessage(), null);
        }
    }
}
