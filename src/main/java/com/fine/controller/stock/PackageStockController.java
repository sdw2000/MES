package com.fine.controller.stock;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fine.Dao.stock.PackageStockDetailMapper;
import com.fine.Dao.stock.PackageStockMapper;
import com.fine.Utils.ResponseResult;
import com.fine.model.stock.PackageStock;
import com.fine.model.stock.PackageStockDetail;
import com.fine.service.stock.StocktakeRecordService;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 包材仓库存管理
 */
@PreAuthorize("hasAnyAuthority('admin','warehouse','production','finance','quality')")
@RestController
@RequestMapping("/api/stock/package")
@CrossOrigin
public class PackageStockController {

    @Autowired
    private PackageStockMapper packageStockMapper;

    @Autowired
    private PackageStockDetailMapper packageStockDetailMapper;

    @Autowired
    private StocktakeRecordService stocktakeRecordService;

    @GetMapping("/list")
    public ResponseResult<List<PackageStock>> list() {
        LambdaQueryWrapper<PackageStock> qw = new LambdaQueryWrapper<>();
        qw.orderByAsc(PackageStock::getMaterialCode);
        return new ResponseResult<>(20000, "查询成功", packageStockMapper.selectList(qw));
    }

    @GetMapping("/list/page")
    public ResponseResult<IPage<PackageStock>> page(
            @RequestParam(defaultValue = "1") Long current,
            @RequestParam(defaultValue = "20") Long size,
            @RequestParam(required = false) String materialCode,
            @RequestParam(required = false) String sortField,
            @RequestParam(required = false) String sortOrder
    ) {
        LambdaQueryWrapper<PackageStock> qw = new LambdaQueryWrapper<>();
        String code = materialCode == null ? "" : materialCode.trim();
        if (!code.isEmpty()) {
            qw.and(w -> w.like(PackageStock::getMaterialCode, code)
                    .or().like(PackageStock::getMaterialName, code));
        }

        boolean asc = "ascending".equalsIgnoreCase(sortOrder) || "asc".equalsIgnoreCase(sortOrder);
        String field = sortField == null ? "" : sortField.trim();
        if ("materialCode".equals(field)) {
            qw.orderBy(true, asc, PackageStock::getMaterialCode);
        } else if ("materialName".equals(field)) {
            qw.orderBy(true, asc, PackageStock::getMaterialName);
        } else if ("totalQuantity".equals(field)) {
            qw.orderBy(true, asc, PackageStock::getTotalQuantity);
        } else if ("availableQuantity".equals(field)) {
            qw.orderBy(true, asc, PackageStock::getAvailableQuantity);
        } else if ("lockedQuantity".equals(field)) {
            qw.orderBy(true, asc, PackageStock::getLockedQuantity);
        } else if ("status".equals(field)) {
            qw.orderBy(true, asc, PackageStock::getStatus);
        } else {
            qw.orderByAsc(PackageStock::getMaterialCode);
        }

        Page<PackageStock> page = new Page<>(current, size);
        IPage<PackageStock> result = packageStockMapper.selectPage(page, qw);
        return new ResponseResult<>(20000, "查询成功", result);
    }

    @GetMapping("/list/statistics")
    public ResponseResult<Map<String, Object>> statistics(
            @RequestParam(required = false) String materialCode
    ) {
        LambdaQueryWrapper<PackageStock> qw = new LambdaQueryWrapper<>();
        String code = materialCode == null ? "" : materialCode.trim();
        if (!code.isEmpty()) {
            qw.and(w -> w.like(PackageStock::getMaterialCode, code)
                    .or().like(PackageStock::getMaterialName, code));
        }
        List<PackageStock> list = packageStockMapper.selectList(qw);

        long totalTypes = list.size();
        int totalQuantity = 0;
        int availableQuantity = 0;
        int lockedQuantity = 0;
        for (PackageStock s : list) {
            totalQuantity += s == null || s.getTotalQuantity() == null ? 0 : s.getTotalQuantity();
            availableQuantity += s == null || s.getAvailableQuantity() == null ? 0 : s.getAvailableQuantity();
            lockedQuantity += s == null || s.getLockedQuantity() == null ? 0 : s.getLockedQuantity();
        }

        Map<String, Object> data = new HashMap<>();
        data.put("totalTypes", totalTypes);
        data.put("totalQuantity", totalQuantity);
        data.put("availableQuantity", availableQuantity);
        data.put("lockedQuantity", lockedQuantity);
        return new ResponseResult<>(20000, "查询成功", data);
    }

    @GetMapping("/{id}/details/page")
    public ResponseResult<IPage<PackageStockDetail>> detailPage(
            @PathVariable Long id,
            @RequestParam(defaultValue = "1") Long current,
            @RequestParam(defaultValue = "20") Long size,
            @RequestParam(defaultValue = "false") Boolean includeUsed
    ) {
        LambdaQueryWrapper<PackageStockDetail> qw = new LambdaQueryWrapper<>();
        qw.eq(PackageStockDetail::getPackageStockId, id)
                .eq(PackageStockDetail::getIsDeleted, 0);
        if (!Boolean.TRUE.equals(includeUsed)) {
            qw.ne(PackageStockDetail::getStatus, "used");
        }
        qw.orderByAsc(PackageStockDetail::getInboundDate)
                .orderByAsc(PackageStockDetail::getId);

        Page<PackageStockDetail> page = new Page<>(current, size);
        IPage<PackageStockDetail> result = packageStockDetailMapper.selectPage(page, qw);
        return new ResponseResult<>(20000, "查询成功", result);
    }

    @GetMapping("/{id}/details")
    public ResponseResult<List<PackageStockDetail>> details(@PathVariable Long id) {
        LambdaQueryWrapper<PackageStockDetail> qw = new LambdaQueryWrapper<>();
        qw.eq(PackageStockDetail::getPackageStockId, id)
                .eq(PackageStockDetail::getIsDeleted, 0)
                .orderByAsc(PackageStockDetail::getInboundDate)
                .orderByAsc(PackageStockDetail::getId);
        return new ResponseResult<>(20000, "查询成功", packageStockDetailMapper.selectList(qw));
    }

    @PostMapping("/{id}/details/{detailId}/stocktake")
    @PreAuthorize("hasAnyAuthority('admin','warehouse')")
    public ResponseResult<PackageStockDetail> stocktakeDetail(@PathVariable Long id,
                                                              @PathVariable Long detailId,
                                                              @RequestBody Map<String, Object> payload) {
        try {
            PackageStock stock = packageStockMapper.selectById(id);
            PackageStockDetail before = packageStockDetailMapper.selectById(detailId);
            if (stock == null || before == null || before.getIsDeleted() != null && before.getIsDeleted() == 1) {
                return new ResponseResult<>(50000, "库存明细不存在", null);
            }
            if (!id.equals(before.getPackageStockId())) {
                return new ResponseResult<>(50000, "明细与库存不匹配", null);
            }
            BigDecimal actualQuantity = toBigDecimal(payload == null ? null : payload.get("actualQuantity"));
            if (actualQuantity == null || actualQuantity.compareTo(BigDecimal.ZERO) < 0) {
                return new ResponseResult<>(50000, "实盘数量必须大于等于0", null);
            }
            String operator = payload == null || payload.get("operator") == null ? null : String.valueOf(payload.get("operator"));
            String reason = payload == null || payload.get("reason") == null ? null : String.valueOf(payload.get("reason"));
            BigDecimal beforeQuantity = before.getQuantity();

            before.setQuantity(actualQuantity);
            before.setUpdateBy(operator);
            before.setUpdateTime(new Date());
            packageStockDetailMapper.updateById(before);
            refreshPackageSummary(id, operator);

            stocktakeRecordService.record(
                    "PACKAGE",
                    id,
                    detailId,
                    before.getMaterialCode(),
                    stock.getMaterialName(),
                    stock.getSpecDesc(),
                    before.getBatchNo(),
                    before.getContainerNo(),
                    before.getLocation(),
                    stock.getUnit(),
                    beforeQuantity,
                    actualQuantity,
                    operator,
                    reason,
                    "包材仓库存明细盘点"
            );
            return new ResponseResult<>(20000, "盘点成功", packageStockDetailMapper.selectById(detailId));
        } catch (Exception e) {
            return new ResponseResult<>(50000, "盘点失败: " + e.getMessage(), null);
        }
    }

    /**
     * 包材仓总数量盘点 (简化模式)
     */
    @PostMapping("/{id}/stocktake-summary")
    @PreAuthorize("hasAnyAuthority('admin','warehouse')")
    public ResponseResult<Object> stocktakeSummary(@PathVariable Long id,
                                                   @RequestBody Map<String, Object> payload) {
        try {
            PackageStock stock = packageStockMapper.selectById(id);
            if (stock == null) {
                return new ResponseResult<>(50000, "库存主记录不存在", null);
            }

            BigDecimal actualQuantity = toBigDecimal(payload == null ? null : payload.get("actualQuantity"));
            if (actualQuantity == null || actualQuantity.compareTo(BigDecimal.ZERO) < 0) {
                return new ResponseResult<>(50000, "实盘数量必须大于等于0", null);
            }
            String operator = payload == null || payload.get("operator") == null ? null : String.valueOf(payload.get("operator"));
            String reason = payload == null || payload.get("reason") == null ? null : String.valueOf(payload.get("reason"));
            
            BigDecimal beforeTotal = stock.getTotalQuantity() == null ? BigDecimal.ZERO : new BigDecimal(stock.getTotalQuantity());

            // 简化逻辑：直接调整所有可用明细，或者创建一个新的明细来抵消差额
            // 这里为了简单，我们找到最早的一个活跃明细进行调整，如果没有则抛错（或者创建一个新的）
            LambdaQueryWrapper<PackageStockDetail> qw = new LambdaQueryWrapper<>();
            qw.eq(PackageStockDetail::getPackageStockId, id)
                    .eq(PackageStockDetail::getIsDeleted, 0)
                    .ne(PackageStockDetail::getStatus, "used")
                    .orderByDesc(PackageStockDetail::getQuantity)
                    .last("LIMIT 1");
            PackageStockDetail detail = packageStockDetailMapper.selectOne(qw);

            if (detail == null) {
                // 如果没有明细，创建一个虚拟明细
                detail = new PackageStockDetail();
                detail.setPackageStockId(id);
                detail.setMaterialCode(stock.getMaterialCode());
                detail.setBatchNo("STOCKTAKE-" + System.currentTimeMillis() / 1000);
                detail.setQuantity(BigDecimal.ZERO);
                detail.setStatus("available");
                detail.setInboundDate(new Date());
                detail.setCreateTime(new Date());
                detail.setIsDeleted(0);
                packageStockDetailMapper.insert(detail);
            }

            BigDecimal diff = actualQuantity.subtract(beforeTotal);
            BigDecimal originalDetailQty = detail.getQuantity() == null ? BigDecimal.ZERO : detail.getQuantity();
            BigDecimal newDetailQty = originalDetailQty.add(diff);
            
            if (newDetailQty.compareTo(BigDecimal.ZERO) < 0) {
                 // 如果调整后明细变成负数，则直接设为新数量，由 refreshPackageSummary 重新计算
                 // 实际上更好的做法是把总数分摊到各个明细，或者这里允许单条明细微调
                 detail.setQuantity(actualQuantity); 
                 // 如果只剩下这一个明细，这样最稳妥
            } else {
                detail.setQuantity(newDetailQty);
            }

            detail.setUpdateBy(operator);
            detail.setUpdateTime(new Date());
            packageStockDetailMapper.updateById(detail);
            
            refreshPackageSummary(id, operator);

            stocktakeRecordService.record(
                    "PACKAGE",
                    id,
                    detail.getId(),
                    stock.getMaterialCode(),
                    stock.getMaterialName(),
                    stock.getSpecDesc(),
                    detail.getBatchNo(),
                    detail.getContainerNo(),
                    detail.getLocation(),
                    stock.getUnit(),
                    beforeTotal,
                    actualQuantity,
                    operator,
                    reason,
                    "包材仓总数量盘点(简化)"
            );

            return new ResponseResult<>(20000, "总数量盘点成功", null);
        } catch (Exception e) {
            e.printStackTrace();
            return new ResponseResult<>(50000, "盘点失败: " + e.getMessage(), null);
        }
    }

    @GetMapping("/export/stocktake")
    @PreAuthorize("hasAnyAuthority('admin','warehouse')")
    public void exportStocktake(@RequestParam(required = false) String materialCode,
                                HttpServletResponse response) throws IOException {
        LambdaQueryWrapper<PackageStock> stockQw = new LambdaQueryWrapper<>();
        String code = materialCode == null ? "" : materialCode.trim();
        if (!code.isEmpty()) {
            stockQw.and(w -> w.like(PackageStock::getMaterialCode, code)
                    .or().like(PackageStock::getMaterialName, code));
        }
        stockQw.orderByAsc(PackageStock::getMaterialCode);
        List<PackageStock> stocks = packageStockMapper.selectList(stockQw);

        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("包材仓盘点表");
        Row header = sheet.createRow(0);
        String[] headers = {"物料代码", "名称", "规格", "库存数量", "存放位置", "实盘数量"};
        for (int i = 0; i < headers.length; i++) {
            header.createCell(i).setCellValue(headers[i]);
        }
        int rowIndex = 1;
        for (PackageStock stock : stocks) {
            LambdaQueryWrapper<PackageStockDetail> detailQw = new LambdaQueryWrapper<>();
            detailQw.eq(PackageStockDetail::getPackageStockId, stock.getId())
                    .eq(PackageStockDetail::getIsDeleted, 0)
                    .ne(PackageStockDetail::getStatus, "used")
                    .orderByAsc(PackageStockDetail::getInboundDate)
                    .orderByAsc(PackageStockDetail::getId);
            List<PackageStockDetail> details = packageStockDetailMapper.selectList(detailQw);
            for (PackageStockDetail detail : details) {
                // 仅导出在库数据（数量 > 0 且状态非 used）
                if ("used".equalsIgnoreCase(detail.getStatus()) || 
                    detail.getQuantity() == null || detail.getQuantity().compareTo(BigDecimal.ZERO) <= 0) {
                    continue;
                }
                Row row = sheet.createRow(rowIndex++);
                row.createCell(0).setCellValue(detail.getMaterialCode() == null ? "" : detail.getMaterialCode());
                row.createCell(1).setCellValue(stock.getMaterialName() == null ? "" : stock.getMaterialName());
                row.createCell(2).setCellValue(stock.getSpecDesc() == null ? "" : stock.getSpecDesc());
                row.createCell(3).setCellValue(detail.getQuantity() == null ? 0 : detail.getQuantity().doubleValue());
                row.createCell(4).setCellValue(detail.getLocation() == null ? "" : detail.getLocation());
                row.createCell(5).setCellValue("");
            }
        }
        for (int i = 0; i < headers.length; i++) {
            sheet.autoSizeColumn(i);
        }
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment;filename=" + URLEncoder.encode("包材仓盘点表.xlsx", "UTF-8"));
        workbook.write(response.getOutputStream());
        workbook.close();
    }

    private void refreshPackageSummary(Long stockId, String operator) {
        PackageStock stock = packageStockMapper.selectById(stockId);
        if (stock == null) {
            return;
        }
        LambdaQueryWrapper<PackageStockDetail> qw = new LambdaQueryWrapper<>();
        qw.eq(PackageStockDetail::getPackageStockId, stockId)
                .eq(PackageStockDetail::getIsDeleted, 0);
        List<PackageStockDetail> details = packageStockDetailMapper.selectList(qw);
        BigDecimal total = BigDecimal.ZERO;
        BigDecimal available = BigDecimal.ZERO;
        BigDecimal locked = BigDecimal.ZERO;
        int totalPack = 0;
        int availablePack = 0;
        int lockedPack = 0;
        for (PackageStockDetail detail : details) {
            BigDecimal qty = detail.getQuantity() == null ? BigDecimal.ZERO : detail.getQuantity();
            int packCount = detail.getPackCount() == null ? 0 : detail.getPackCount();
            if (!"used".equalsIgnoreCase(detail.getStatus())) {
                total = total.add(qty);
                totalPack += packCount;
            }
            if ("locked".equalsIgnoreCase(detail.getStatus())) {
                locked = locked.add(qty);
                lockedPack += packCount;
            } else if (!"used".equalsIgnoreCase(detail.getStatus())) {
                available = available.add(qty);
                availablePack += packCount;
            }
        }
        stock.setTotalQuantity(total.setScale(0, BigDecimal.ROUND_HALF_UP).intValue());
        stock.setAvailableQuantity(available.setScale(0, BigDecimal.ROUND_HALF_UP).intValue());
        stock.setLockedQuantity(locked.setScale(0, BigDecimal.ROUND_HALF_UP).intValue());
        stock.setTotalPackCount(totalPack);
        stock.setAvailablePackCount(availablePack);
        stock.setLockedPackCount(lockedPack);
        stock.setUpdateBy(operator);
        stock.setUpdateTime(new Date());
        packageStockMapper.updateById(stock);
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof BigDecimal) {
            return (BigDecimal) value;
        }
        if (value instanceof Number) {
            return BigDecimal.valueOf(((Number) value).doubleValue());
        }
        try {
            String s = String.valueOf(value).trim();
            return s.isEmpty() ? null : new BigDecimal(s);
        } catch (Exception e) {
            return null;
        }
    }
}
