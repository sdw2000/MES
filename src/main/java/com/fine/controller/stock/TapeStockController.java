package com.fine.controller.stock;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.fine.Utils.ResponseResult;
import com.fine.Dao.stock.TapeInboundRequestMapper;
import com.fine.Dao.stock.TapeOutboundRequestMapper;
import com.fine.modle.stock.*;
import com.fine.service.stock.TapeStockService;
import com.fine.service.stock.StocktakeRecordService;
import com.fine.service.purchase.PurchaseReceiptService;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLEncoder;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 胶带库存管理控制器
 */
@RestController
@RequestMapping("/api/tape-stock")
@PreAuthorize("hasAnyAuthority('warehouse','admin','sales','production','finance','quality','packaging','packing','plan','rd')")
public class TapeStockController {
    
    @Autowired
    private TapeStockService stockService;

    @Autowired
    private StocktakeRecordService stocktakeRecordService;

    @Autowired
    private PurchaseReceiptService purchaseReceiptService;
    
    // ============= 库存管理 =============
    
    /**
     * 分页查询库存
     */
    @GetMapping("/list")
    @PreAuthorize("hasAnyAuthority('warehouse','admin','sales','production','finance','quality','packaging','packing','plan','rd')")
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

    @Autowired
    private TapeOutboundRequestMapper outboundRequestMapper;

    @Autowired
    private TapeInboundRequestMapper inboundRequestMapper;

    /**
     * 复卷打码母卷号搜索（在库 + 已领料到包装车间）
     */
    @GetMapping("/mother-roll/search")
    @PreAuthorize("hasAnyAuthority('warehouse','admin','sales','production','finance','quality','packaging','packing','plan','rd')")
    public ResponseResult<?> searchMotherRolls(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "20") Integer size) {
        List<Map<String, Object>> rows = searchMotherRollCandidates(keyword, size);
        return ResponseResult.success("查询成功", rows);
    }

    /**
     * 复卷打码母卷号解析（在库优先，其次已领料到包装车间）
     */
    @GetMapping("/mother-roll/resolve/{code}")
    @PreAuthorize("hasAnyAuthority('warehouse','admin','sales','production','finance','quality','packaging','packing','plan','rd')")
    public ResponseResult<?> resolveMotherRoll(@PathVariable String code) {
        String qrCode = code == null ? "" : code.trim();
        if (!StringUtils.hasText(qrCode)) {
            return ResponseResult.error("母卷号不能为空");
        }

        // 在库优先
        LambdaQueryWrapper<TapeStock> inStockQw = new LambdaQueryWrapper<>();
        inStockQw.eq(TapeStock::getStatus, 1)
                .eq(TapeStock::getRollType, "母卷")
                .and(w -> w.eq(TapeStock::getQrCode, qrCode)
                        .or().eq(TapeStock::getBatchNo, qrCode))
                .orderByDesc(TapeStock::getId)
                .last("LIMIT 1");
        TapeStock inStock = stockMapper.selectOne(inStockQw);
        if (inStock != null) {
            return ResponseResult.success("查询成功", buildMotherRollRow(inStock, "IN_STOCK", "在库"));
        }

        // 已领料到包装车间
        LambdaQueryWrapper<TapeStock> anyStockQw = new LambdaQueryWrapper<>();
        anyStockQw.and(w -> w.eq(TapeStock::getRollType, "母卷")
                .or().like(TapeStock::getRollType, "母卷")
                .or().isNull(TapeStock::getRollType))
                .and(w -> w.eq(TapeStock::getQrCode, qrCode)
                        .or().eq(TapeStock::getBatchNo, qrCode))
                .orderByDesc(TapeStock::getId)
                .last("LIMIT 1");
        TapeStock stockAnyStatus = stockMapper.selectOne(anyStockQw);
        if (stockAnyStatus != null && stockAnyStatus.getId() != null) {
            LambdaQueryWrapper<TapeOutboundRequest> issuedQw = new LambdaQueryWrapper<>();
            issuedQw.eq(TapeOutboundRequest::getStatus, TapeOutboundRequest.STATUS_APPROVED)
                    .eq(TapeOutboundRequest::getStockId, stockAnyStatus.getId())
                    .like(TapeOutboundRequest::getApplyDept, "包装")
                    .orderByDesc(TapeOutboundRequest::getId)
                    .last("LIMIT 1");
            TapeOutboundRequest issued = outboundRequestMapper.selectOne(issuedQw);
            if (issued != null) {
                return ResponseResult.success("查询成功", buildMotherRollRow(stockAnyStatus, "ISSUED_PACKAGING", "已领料-包装车间"));
            }
            String sourceType = (stockAnyStatus.getStatus() != null && stockAnyStatus.getStatus() == 1)
                ? "IN_STOCK"
                : "IN_STOCK_NON_ACTIVE";
            String sourceLabel = (stockAnyStatus.getStatus() != null && stockAnyStatus.getStatus() == 1)
                ? "在库"
                : "在库-非可用状态(" + stockAnyStatus.getStatus() + ")";
            return ResponseResult.success("查询成功", buildMotherRollRow(stockAnyStatus, sourceType, sourceLabel));
        }

        // 兼容历史领料数据：存在已审批包装领料，但未回填stockId
        LambdaQueryWrapper<TapeOutboundRequest> issuedByBatchQw = new LambdaQueryWrapper<>();
        issuedByBatchQw.eq(TapeOutboundRequest::getStatus, TapeOutboundRequest.STATUS_APPROVED)
                .like(TapeOutboundRequest::getApplyDept, "包装")
                .and(w -> w.eq(TapeOutboundRequest::getBatchNo, qrCode)
                        .or().eq(TapeOutboundRequest::getRequestNo, qrCode))
                .orderByDesc(TapeOutboundRequest::getId)
                .last("LIMIT 1");
        TapeOutboundRequest issuedByBatch = outboundRequestMapper.selectOne(issuedByBatchQw);
        if (issuedByBatch != null) {
            if (issuedByBatch.getStockId() != null && issuedByBatch.getStockId() > 0) {
                TapeStock byReqStock = stockMapper.selectById(issuedByBatch.getStockId());
                if (byReqStock != null) {
                    return ResponseResult.success("查询成功", buildMotherRollRow(byReqStock, "ISSUED_PACKAGING", "已领料-包装车间"));
                }
            }
            return ResponseResult.success("查询成功", buildMotherRollRowFromOutboundRequest(issuedByBatch));
        }

        // 兼容“报工已提交但尚未入库审核”场景：允许从待审批工序入库申请中解析
        LambdaQueryWrapper<TapeInboundRequest> pendingInboundQw = new LambdaQueryWrapper<>();
        pendingInboundQw.eq(TapeInboundRequest::getStatus, TapeInboundRequest.STATUS_PENDING)
                .eq(TapeInboundRequest::getBatchNo, qrCode)
                .orderByDesc(TapeInboundRequest::getId)
                .last("LIMIT 1");
        TapeInboundRequest pendingInbound = inboundRequestMapper.selectOne(pendingInboundQw);
        if (pendingInbound != null && isProcessMotherRollInbound(pendingInbound)) {
            return ResponseResult.success("查询成功", buildMotherRollRowFromInboundRequest(pendingInbound,
                    "INBOUND_PENDING", "报工已提交-待入库审核"));
        }

        // 模糊兜底：允许输入批次前缀或片段
        List<Map<String, Object>> fuzzy = searchMotherRollCandidates(qrCode, 20);
        if (!fuzzy.isEmpty()) {
            return ResponseResult.success("查询成功", fuzzy.get(0));
        }

        return ResponseResult.error("未找到该母卷（不在库且未领料到包装车间）");
    }

    private List<Map<String, Object>> searchMotherRollCandidates(String keyword, Integer size) {
        String kw = keyword == null ? "" : keyword.trim();
        int limit = size == null ? 20 : Math.max(1, Math.min(size, 100));

        LinkedHashMap<String, Map<String, Object>> merged = new LinkedHashMap<>();

        // 1) 在库母卷
        LambdaQueryWrapper<TapeStock> inStockQw = new LambdaQueryWrapper<>();
        inStockQw.eq(TapeStock::getStatus, 1)
            .and(w -> w.eq(TapeStock::getRollType, "母卷")
                .or().like(TapeStock::getRollType, "母卷")
                .or().isNull(TapeStock::getRollType));
        if (StringUtils.hasText(kw)) {
            inStockQw.and(w -> w.like(TapeStock::getQrCode, kw)
                    .or().like(TapeStock::getBatchNo, kw)
                    .or().like(TapeStock::getMaterialCode, kw)
                    .or().like(TapeStock::getProductName, kw));
        }
        inStockQw.orderByDesc(TapeStock::getId).last("LIMIT " + (limit * 3));
        List<TapeStock> inStockList = stockMapper.selectList(inStockQw);
        for (TapeStock stock : inStockList) {
            Map<String, Object> row = buildMotherRollRow(stock, "IN_STOCK", "在库");
            String code = row.get("value") == null ? "" : String.valueOf(row.get("value")).trim();
            if (StringUtils.hasText(code)) {
                merged.putIfAbsent(code, row);
            }
        }

        // 1.5) 在库母卷（包含非可用状态）
        LambdaQueryWrapper<TapeStock> anyStatusQw = new LambdaQueryWrapper<>();
        anyStatusQw.and(w -> w.eq(TapeStock::getRollType, "母卷")
                .or().like(TapeStock::getRollType, "母卷")
                .or().isNull(TapeStock::getRollType));
        if (StringUtils.hasText(kw)) {
            anyStatusQw.and(w -> w.like(TapeStock::getQrCode, kw)
                    .or().like(TapeStock::getBatchNo, kw)
                    .or().like(TapeStock::getMaterialCode, kw)
                    .or().like(TapeStock::getProductName, kw));
        }
        anyStatusQw.orderByDesc(TapeStock::getId).last("LIMIT " + (limit * 3));
        List<TapeStock> anyStatusList = stockMapper.selectList(anyStatusQw);
        for (TapeStock stock : anyStatusList) {
            String sourceType = (stock.getStatus() != null && stock.getStatus() == 1)
                    ? "IN_STOCK"
                    : "IN_STOCK_NON_ACTIVE";
            String sourceLabel = (stock.getStatus() != null && stock.getStatus() == 1)
                    ? "在库"
                    : "在库-非可用状态(" + stock.getStatus() + ")";
            Map<String, Object> row = buildMotherRollRow(stock, sourceType, sourceLabel);
            String code = row.get("value") == null ? "" : String.valueOf(row.get("value")).trim();
            if (StringUtils.hasText(code)) {
                merged.putIfAbsent(code, row);
            }
        }

        // 2) 已领料到包装车间（已审批出库）
        LambdaQueryWrapper<TapeOutboundRequest> issuedQw = new LambdaQueryWrapper<>();
        issuedQw.eq(TapeOutboundRequest::getStatus, TapeOutboundRequest.STATUS_APPROVED)
                .like(TapeOutboundRequest::getApplyDept, "包装");
        if (StringUtils.hasText(kw)) {
            issuedQw.and(w -> w.like(TapeOutboundRequest::getBatchNo, kw)
                    .or().like(TapeOutboundRequest::getMaterialCode, kw)
                    .or().like(TapeOutboundRequest::getProductName, kw)
                    .or().like(TapeOutboundRequest::getSpecDesc, kw)
                    .or().like(TapeOutboundRequest::getRequestNo, kw)
                    .or().like(TapeOutboundRequest::getOrderNo, kw));
        }
        issuedQw.orderByDesc(TapeOutboundRequest::getId).last("LIMIT " + (limit * 8));
        List<TapeOutboundRequest> issuedList = outboundRequestMapper.selectList(issuedQw);

        for (TapeOutboundRequest req : issuedList) {
            if (req == null) {
                continue;
            }
            TapeStock stock = null;
            if (req.getStockId() != null && req.getStockId() > 0) {
                stock = stockMapper.selectById(req.getStockId());
            }

            if (stock == null && StringUtils.hasText(req.getBatchNo())) {
                LambdaQueryWrapper<TapeStock> byBatchQw = new LambdaQueryWrapper<>();
                byBatchQw.eq(TapeStock::getRollType, "母卷")
                        .eq(TapeStock::getBatchNo, req.getBatchNo())
                        .orderByDesc(TapeStock::getId)
                        .last("LIMIT 1");
                stock = stockMapper.selectOne(byBatchQw);
            }

            if (stock == null) {
                // 仍查不到库存实体时，至少返回领料单可识别信息，保证可选可回填
                if (!StringUtils.hasText(req.getBatchNo())) {
                    continue;
                }
                Map<String, Object> row = buildMotherRollRowFromOutboundRequest(req);
                String code = row.get("value") == null ? "" : String.valueOf(row.get("value")).trim();
                if (StringUtils.hasText(code)) {
                    merged.putIfAbsent(code, row);
                }
                continue;
            }
            if (StringUtils.hasText(stock.getRollType()) && !"母卷".equals(stock.getRollType())) {
                continue;
            }
            Map<String, Object> row = buildMotherRollRow(stock, "ISSUED_PACKAGING", "已领料-包装车间");
            String code = row.get("value") == null ? "" : String.valueOf(row.get("value")).trim();
            if (StringUtils.hasText(code)) {
                merged.putIfAbsent(code, row);
            }
        }

        // 3) 报工后待审批入库（涂布/复卷）
        LambdaQueryWrapper<TapeInboundRequest> pendingInboundQw = new LambdaQueryWrapper<>();
        pendingInboundQw.eq(TapeInboundRequest::getStatus, TapeInboundRequest.STATUS_PENDING);
        if (StringUtils.hasText(kw)) {
            pendingInboundQw.and(w -> w.like(TapeInboundRequest::getBatchNo, kw)
                    .or().like(TapeInboundRequest::getMaterialCode, kw)
                    .or().like(TapeInboundRequest::getProductName, kw)
                    .or().like(TapeInboundRequest::getSpecDesc, kw)
                    .or().like(TapeInboundRequest::getRequestNo, kw));
        }
        pendingInboundQw.orderByDesc(TapeInboundRequest::getId).last("LIMIT " + (limit * 8));
        List<TapeInboundRequest> pendingInboundList = inboundRequestMapper.selectList(pendingInboundQw);
        for (TapeInboundRequest req : pendingInboundList) {
            if (req == null || !isProcessMotherRollInbound(req)) {
                continue;
            }
            Map<String, Object> row = buildMotherRollRowFromInboundRequest(req,
                    "INBOUND_PENDING", "报工已提交-待入库审核");
            String code = row.get("value") == null ? "" : String.valueOf(row.get("value")).trim();
            if (StringUtils.hasText(code)) {
                merged.putIfAbsent(code, row);
            }
        }

        List<Map<String, Object>> rows = new ArrayList<>(merged.values());
        if (rows.size() > limit) {
            rows = rows.subList(0, limit);
        }
        return rows;
    }

    private Map<String, Object> buildMotherRollRow(TapeStock stock, String sourceType, String sourceLabel) {
        Map<String, Object> row = new LinkedHashMap<>();
        // 母卷查询展示优先使用批次号，避免回填成带流水尾号（如 001）的二维码格式
        String code = StringUtils.hasText(stock.getBatchNo()) ? stock.getBatchNo() : stock.getQrCode();
        row.put("value", code);
        row.put("id", stock.getId());
        row.put("qrCode", stock.getQrCode());
        row.put("batchNo", stock.getBatchNo());
        row.put("materialCode", stock.getMaterialCode());
        row.put("materialName", stock.getProductName());
        row.put("productName", stock.getProductName());
        row.put("thickness", stock.getThickness());
        row.put("width", stock.getWidth());
        row.put("widthMm", stock.getWidth());
        row.put("length", stock.getCurrentLength() != null ? stock.getCurrentLength() : stock.getLength());
        row.put("lengthM", stock.getCurrentLength() != null ? stock.getCurrentLength() : stock.getLength());
        row.put("currentLength", stock.getCurrentLength() != null ? stock.getCurrentLength() : stock.getLength());
        row.put("sourceType", sourceType);
        row.put("sourceLabel", sourceLabel);
        row.put("inStock", stock.getStatus() != null && stock.getStatus() == 1);
        return row;
    }

    private Map<String, Object> buildMotherRollRowFromOutboundRequest(TapeOutboundRequest req) {
        Map<String, Object> row = new LinkedHashMap<>();
        String code = StringUtils.hasText(req.getBatchNo()) ? req.getBatchNo() : req.getRequestNo();
        row.put("value", code);
        row.put("id", req.getStockId());
        row.put("qrCode", null);
        row.put("batchNo", req.getBatchNo());
        row.put("materialCode", req.getMaterialCode());
        row.put("materialName", req.getProductName());
        row.put("productName", req.getProductName());
        row.put("thickness", null);
        row.put("width", null);
        row.put("widthMm", null);
        row.put("length", null);
        row.put("lengthM", null);
        row.put("currentLength", null);
        row.put("sourceType", "ISSUED_PACKAGING");
        row.put("sourceLabel", "已领料-包装车间");
        row.put("inStock", false);
        return row;
    }

    private boolean isProcessMotherRollInbound(TapeInboundRequest req) {
        if (req == null) {
            return false;
        }
        String batchNo = req.getBatchNo() == null ? "" : req.getBatchNo().trim();
        if (!StringUtils.hasText(batchNo)) {
            return false;
        }
        String applyDept = req.getApplyDept() == null ? "" : req.getApplyDept().trim();
        if (!applyDept.contains("生产")) {
            return false;
        }
        String remark = req.getRemark() == null ? "" : req.getRemark();
        // 排除分切自动直入库（该类不用于复卷母卷查找）
        if (remark.contains("process=SLITTING") || batchNo.contains("-SLITTING-") || batchNo.contains("-SLIT-")) {
            return false;
        }
        return true;
    }

    private Map<String, Object> buildMotherRollRowFromInboundRequest(TapeInboundRequest req, String sourceType, String sourceLabel) {
        Map<String, Object> row = new LinkedHashMap<>();
        String code = StringUtils.hasText(req.getBatchNo()) ? req.getBatchNo() : req.getRequestNo();
        row.put("value", code);
        row.put("id", null);
        row.put("qrCode", null);
        row.put("batchNo", req.getBatchNo());
        row.put("materialCode", req.getMaterialCode());
        row.put("materialName", req.getProductName());
        row.put("productName", req.getProductName());
        row.put("thickness", req.getThickness());
        row.put("width", req.getWidth());
        row.put("widthMm", req.getWidth());
        row.put("length", req.getLength());
        row.put("lengthM", req.getLength());
        row.put("currentLength", req.getLength());
        row.put("sourceType", sourceType);
        row.put("sourceLabel", sourceLabel);
        row.put("inStock", false);
        return row;
    }
    
    /**
    * 按料号汇总库存
     */
    @GetMapping("/summary")
    @PreAuthorize("hasAnyAuthority('warehouse','admin','sales','finance','quality')")
    public ResponseResult<?> getStockSummary(@RequestParam(required = false) Boolean includeReturnWarehouse) {
        List<TapeStock> list = includeReturnWarehouse != null ? 
            stockService.getStockSummary(includeReturnWarehouse) : 
            stockService.getStockSummary(true);  // 默认包含退货专仓
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
            @RequestParam(required = false) String materialCode,
            @RequestParam(required = false) Boolean includeReturnWarehouse) {
        IPage<TapeStock> result = includeReturnWarehouse != null ? 
            stockService.getStockSummaryPage(current, size, materialCode, includeReturnWarehouse) : 
            stockService.getStockSummaryPage(current, size, materialCode, true);  // 默认包含退货专仓
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
        List<TapeStock> list = stockService.getStockByMaterialFIFO(materialCode == null ? null : materialCode.trim());
        return ResponseResult.success("查询成功", list);
    }

    /**
    * 根据料号查询库存明细（分页）
     */
    @GetMapping("/by-material/page")
    public ResponseResult<?> getStockByMaterialPage(
            @RequestParam String materialCode,
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Boolean includeReturnWarehouse,
            @RequestParam(required = false) String sortField,
            @RequestParam(required = false) String sortOrder) {
        IPage<TapeStock> result = includeReturnWarehouse != null ? 
            stockService.getStockByMaterialPage(current, size, materialCode, includeReturnWarehouse, sortField, sortOrder) : 
            stockService.getStockByMaterialPage(current, size, materialCode, true, sortField, sortOrder);  // 默认包含退货专仓
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
     * 物料盘点
     */
    @PostMapping("/{id}/stocktake")
    @PreAuthorize("hasAnyAuthority('warehouse','admin')")
    public ResponseResult<?> stocktake(@PathVariable Long id, @RequestBody TapeStocktakeRequest request) {
        try {
            TapeStock before = stockMapper.selectById(id);
            if (before == null) {
                return ResponseResult.error("库存记录不存在");
            }
            TapeStock result = stockService.stocktake(
                    id,
                    request == null ? null : request.getActualRolls(),
                    request == null ? null : request.getActualSqm(),
                    request == null ? null : request.getOperator(),
                    request == null ? null : request.getReason()
            );
                    stocktakeRecordService.record(
                        "TAPE",
                        id,
                        id,
                        before.getMaterialCode(),
                        before.getProductName(),
                        before.getSpecDesc(),
                        before.getBatchNo(),
                        before.getQrCode(),
                        before.getLocation(),
                        "卷",
                        BigDecimal.valueOf(before.getTotalRolls() == null ? 0 : before.getTotalRolls()),
                        BigDecimal.valueOf(result.getTotalRolls() == null ? 0 : result.getTotalRolls()),
                        request == null ? null : request.getOperator(),
                        request == null ? null : request.getReason(),
                        "胶带仓库存盘点"
                    );
            return ResponseResult.success("盘点成功", result);
        } catch (Exception e) {
            return ResponseResult.error("盘点失败: " + e.getMessage());
        }
    }
    
    /**
     * 导入Excel库存数据
     */
    @PostMapping("/import")
    @PreAuthorize("hasAnyAuthority('warehouse','admin')")
    public ResponseResult<?> importExcel(@RequestParam("file") MultipartFile file) {
        try {
            Map<String, Object> result = stockService.importExcel(file);
            Object successCount = result.getOrDefault("successCount", 0);
            Object failCount = result.getOrDefault("failCount", 0);
            String msg = "导入完成：成功" + successCount + "条，失败/跳过" + failCount + "条";
            return ResponseResult.success(msg, result);
        } catch (Exception e) {
            return ResponseResult.error("导入失败: " + e.getMessage());
        }
    }

    /**
     * 异步导入Excel库存数据（大文件推荐）
     */
    @PostMapping("/import/async")
    @PreAuthorize("hasAnyAuthority('warehouse','admin')")
    public ResponseResult<?> importExcelAsync(@RequestParam("file") MultipartFile file) {
        try {
            Map<String, Object> result = stockService.importExcelAsync(file);
            return ResponseResult.success("异步导入任务已创建", result);
        } catch (Exception e) {
            return ResponseResult.error("创建异步导入任务失败: " + e.getMessage());
        }
    }

    /**
     * 查询异步导入任务状态
     */
    @GetMapping("/import/task/{taskId}")
    @PreAuthorize("hasAnyAuthority('warehouse','admin')")
    public ResponseResult<?> getImportTaskStatus(@PathVariable String taskId) {
        Map<String, Object> result = stockService.getImportTaskStatus(taskId);
        return ResponseResult.success("查询成功", result);
    }

    /**
     * 下载异步导入失败明细文件
     */
    @GetMapping("/import/task/{taskId}/failed.xlsx")
    @PreAuthorize("hasAnyAuthority('warehouse','admin')")
    public ResponseEntity<byte[]> downloadImportFailedFile(@PathVariable String taskId) {
        byte[] content = stockService.getImportTaskFailedExcel(taskId);
        if (content == null || content.length == 0) {
            return ResponseEntity.notFound().build();
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        headers.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=tape_stock_import_failed.xlsx");
        return ResponseEntity.ok().headers(headers).body(content);
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
        String[] headers = {"物料代码", "名称", "规格", "库存数量", "存放位置", "实盘数量", "生产批次号", "二维码", "卷类型", "总平米数", "生产日期"};
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
            row.createCell(2).setCellValue(stock.getSpecDesc() != null ? stock.getSpecDesc() : "");
            row.createCell(3).setCellValue(stock.getTotalRolls() != null ? stock.getTotalRolls() : 0);
            row.createCell(4).setCellValue(stock.getLocation() != null ? stock.getLocation() : "");
            row.createCell(5).setCellValue("");
            row.createCell(6).setCellValue(stock.getBatchNo() != null ? stock.getBatchNo() : "");
            row.createCell(7).setCellValue(stock.getQrCode() != null ? stock.getQrCode() : stock.getBatchNo());
            row.createCell(8).setCellValue(stock.getRollType() != null ? stock.getRollType() : "母卷");
            row.createCell(9).setCellValue(stock.getTotalSqm() != null ? stock.getTotalSqm().doubleValue() : 0);
            row.createCell(10).setCellValue(stock.getProdDate() != null ? stock.getProdDate().format(dtf) : "");
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
            @RequestParam(required = false) String materialCode,
            @RequestParam(required = false) String sourceType,
            @RequestParam(required = false) Long receiptId,
            @RequestParam(required = false) Long itemId,
            @RequestParam(required = false) String keyword) {
        if (StringUtils.hasText(sourceType) && "PURCHASE_RECEIVING".equalsIgnoreCase(sourceType.trim())) {
            try {
                purchaseReceiptService.syncInboundRequestsForAllActiveReceipts();
            } catch (Exception ignored) {
                // 自动同步失败不影响列表查询
            }
        }
        IPage<TapeInboundRequest> result = stockService.getInboundPage(page, size, status, materialCode, sourceType, receiptId, itemId, keyword);
        Map<String, Object> data = new HashMap<>();
        data.put("records", result.getRecords());
        data.put("total", result.getTotal());
        data.put("current", result.getCurrent());
        data.put("size", result.getSize());
        return ResponseResult.success("查询成功", data);
    }

    /**
     * 扫码入仓：待扫描单据列表（小程序）
     */
    @GetMapping("/inbound/scan/documents")
    @PreAuthorize("hasAnyAuthority('warehouse','admin')")
    public ResponseResult<?> listScanInboundDocuments(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size,
            @RequestParam(required = false) String keyword) {
        return purchaseReceiptService.listScanInboundDocuments(page, size, keyword);
    }

    /**
     * 扫码入仓：按收货单查询基础信息（小程序）
     */
    @GetMapping("/inbound/scan/document")
    @PreAuthorize("hasAnyAuthority('warehouse','admin')")
    public ResponseResult<?> getScanInboundDocument(
            @RequestParam(required = false) String scanCode,
            @RequestParam(required = false) String receiptNo,
            @RequestParam(required = false) Long receiptId) {
        String lookupCode = StringUtils.hasText(scanCode) ? scanCode : receiptNo;
        return purchaseReceiptService.getScanInboundDocument(lookupCode, receiptId);
    }

    /**
     * 扫码入仓：提交入仓（小程序）
     */
    @PostMapping("/inbound/scan/submit")
    @PreAuthorize("hasAnyAuthority('warehouse','admin')")
    public ResponseResult<?> submitScanInbound(@RequestBody Map<String, Object> params) {
        String receiptNo = params.get("receiptNo") == null ? null : String.valueOf(params.get("receiptNo"));
        Long receiptId = null;
        try {
            if (params.get("receiptId") != null) {
                receiptId = Long.valueOf(String.valueOf(params.get("receiptId")));
            }
        } catch (Exception ignored) {
        }
        String scannedLocation = params.get("scannedLocation") == null ? null : String.valueOf(params.get("scannedLocation"));
        String operator = params.get("operator") == null ? null : String.valueOf(params.get("operator"));
        List<String> scanCodes = new java.util.ArrayList<>();
        Object codesObj = params.get("scanCodes");
        if (codesObj instanceof List) {
            for (Object row : (List<?>) codesObj) {
                if (row != null) {
                    scanCodes.add(String.valueOf(row));
                }
            }
        }
        return purchaseReceiptService.submitScanInbound(receiptNo, receiptId, scanCodes, scannedLocation, operator);
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
     * 采购收货标签打印前置：生成打印数据与二维码（日期+日流水）
     */
    @PostMapping("/inbound/{id}/purchase-label/prepare")
    @PreAuthorize("hasAnyAuthority('warehouse','admin','production')")
    public ResponseResult<?> preparePurchaseInboundLabel(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, Object> payload) {
        try {
            String operator = payload != null && payload.get("operator") != null
                    ? String.valueOf(payload.get("operator"))
                    : "";
            Map<String, Object> result = stockService.preparePurchaseInboundLabelPrint(id, payload, operator);
            return ResponseResult.success("标签数据生成成功", result);
        } catch (Exception e) {
            return ResponseResult.error("标签数据生成失败: " + e.getMessage());
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

    /**
     * 历史分切成品库存聚合（一次性治理）
     */
    @PostMapping("/inbound/merge-historical-slitting")
    @PreAuthorize("hasAnyAuthority('admin','warehouse')")
    public ResponseResult<?> mergeHistoricalSlitting() {
        try {
            Map<String, Object> result = stockService.mergeHistoricalSlittingFinishedStock();
            return ResponseResult.success("历史分切成品库存合并完成", result);
        } catch (Exception e) {
            return ResponseResult.error("历史分切成品库存合并失败: " + e.getMessage());
        }
    }

    /**
     * 历史采购入库纠偏（化工/薄膜误入胶带仓迁移）
     */
    @PostMapping("/inbound/migrate-misrouted-purchase")
    @PreAuthorize("hasAnyAuthority('admin','warehouse')")
    public ResponseResult<?> migrateMisroutedPurchaseInbound(
            @RequestParam(required = false) String auditor) {
        try {
            Map<String, Object> result = stockService.migrateMisroutedPurchaseInboundToRawWarehouse(auditor);
            return ResponseResult.success("历史采购入库纠偏完成", result);
        } catch (Exception e) {
            return ResponseResult.error("历史采购入库纠偏失败: " + e.getMessage());
        }
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
            @RequestParam(required = false) String materialCode,
            @RequestParam(required = false) String orderNo) {        IPage<TapeOutboundRequest> result = stockService.getOutboundPage(page, size, status, materialCode, orderNo);
        Map<String, Object> data = new HashMap<>();
        data.put("records", result.getRecords());
        data.put("total", result.getTotal());
        data.put("current", result.getCurrent());
        data.put("size", result.getSize());
        return ResponseResult.success("查询成功", data);
    }

    /**
     * 统一分页查询出库列表（胶带产品 + 原材料）
     */
    @GetMapping("/outbound/unified-list")
    @PreAuthorize("hasAnyAuthority('warehouse','admin','sales','finance','quality')")
    public ResponseResult<?> getUnifiedOutboundList(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String materialCode,
            @RequestParam(required = false) String bizType,
            @RequestParam(required = false) String orderNo) {
        Map<String, Object> data = stockService.getUnifiedOutboundPage(page, size, status, materialCode, bizType, orderNo);
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
     * 修改出库申请（仅待审批）
     */
    @PutMapping("/outbound/{id}")
    @PreAuthorize("hasAnyAuthority('warehouse','admin','sales','finance','quality')")
    public ResponseResult<?> updateOutboundRequest(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        try {
            Integer rolls = null;
            if (body != null && body.get("rolls") != null) {
                Object r = body.get("rolls");
                if (r instanceof Number) {
                    rolls = ((Number) r).intValue();
                } else {
                    try {
                        rolls = Integer.parseInt(String.valueOf(r).trim());
                    } catch (Exception ignored) {
                    }
                }
            }
            String applyDept = body == null || body.get("applyDept") == null ? null : String.valueOf(body.get("applyDept"));
            String remark = body == null || body.get("remark") == null ? null : String.valueOf(body.get("remark"));

            TapeOutboundRequest result = stockService.updateOutboundRequest(id, rolls, applyDept, remark);
            return ResponseResult.success("修改成功", result);
        } catch (Exception e) {
            return ResponseResult.error("修改失败: " + e.getMessage());
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
            @RequestParam(required = false) String remark,
            @RequestParam(required = false) String orderNo,
            @RequestParam(required = false) Long orderItemId,
            @RequestParam(required = false) String bizType) {        try {
            List<TapeOutboundRequest> result = stockService.createOutboundRequestFIFO(
                materialCode, totalRolls, applicant, applyDept, remark, orderNo, orderItemId, bizType);
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
            @RequestParam(required = false) String batchNo,
            @RequestParam(required = false) String orderNo) {        IPage<TapeStockLog> result = stockService.getStockLogPage(page, size, type, materialCode, batchNo, orderNo);
        Map<String, Object> data = new HashMap<>();
        data.put("records", result.getRecords());
        data.put("total", result.getTotal());
        data.put("current", result.getCurrent());
        data.put("size", result.getSize());
        return ResponseResult.success("查询成功", data);
    }

    /**
     * 分页查询出库流水汇总（按关联单号+料号+批次聚合）
     */
    @GetMapping("/log/outbound-summary/list")
    @PreAuthorize("hasAnyAuthority('warehouse','admin','sales','production','finance','quality')")
    public ResponseResult<?> getOutboundSummaryLogList(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String materialCode,
            @RequestParam(required = false) String batchNo,
            @RequestParam(required = false) String orderNo) {
        IPage<TapeStockLog> result = stockService.getOutboundLogSummaryPage(page, size, materialCode, batchNo, orderNo);
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
        String[] headers = {"时间", "类型", "订单号", "料号", "产品名称", "规格", "批次号", 
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
            row.createCell(2).setCellValue(log.getOrderNo() != null ? log.getOrderNo() : "");
            row.createCell(3).setCellValue(log.getMaterialCode() != null ? log.getMaterialCode() : "");
            row.createCell(4).setCellValue(log.getProductName() != null ? log.getProductName() : "");
            row.createCell(5).setCellValue(log.getSpecDesc() != null ? log.getSpecDesc() : "");
            row.createCell(6).setCellValue(log.getBatchNo() != null ? log.getBatchNo() : "");
            row.createCell(7).setCellValue(log.getChangeRolls() != null ? log.getChangeRolls() : 0);
            row.createCell(8).setCellValue(log.getBeforeRolls() != null ? log.getBeforeRolls() : 0);
            row.createCell(9).setCellValue(log.getAfterRolls() != null ? log.getAfterRolls() : 0);
            row.createCell(10).setCellValue(log.getRefNo() != null ? log.getRefNo() : "");
            row.createCell(11).setCellValue(log.getOperator() != null ? log.getOperator() : "");
            row.createCell(12).setCellValue(log.getRemark() != null ? log.getRemark() : "");
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
