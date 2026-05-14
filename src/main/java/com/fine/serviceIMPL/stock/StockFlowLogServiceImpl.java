package com.fine.serviceIMPL.stock;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fine.Dao.stock.ChemicalStockMapper;
import com.fine.Dao.stock.ChemicalStockOutMapper;
import com.fine.Dao.stock.FilmStockMapper;
import com.fine.Dao.stock.FilmStockOutMapper;
import com.fine.Dao.stock.StockFlowLogMapper;
import com.fine.Dao.stock.TapeInboundRequestMapper;
import com.fine.Dao.stock.TapeOutboundRequestMapper;
import com.fine.Dao.stock.TapeStockMapper;
import com.fine.modle.stock.TapeInboundRequest;
import com.fine.modle.stock.TapeOutboundRequest;
import com.fine.modle.stock.TapeStock;
import com.fine.model.stock.ChemicalStock;
import com.fine.model.stock.ChemicalStockOut;
import com.fine.model.stock.FilmStock;
import com.fine.model.stock.FilmStockOut;
import com.fine.model.stock.StockFlowLog;
import com.fine.service.stock.StockFlowLogService;
import com.fine.service.UnitService;
import com.fine.model.UnitConversionResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 统一库存流水服务实现
 */
@Service
public class StockFlowLogServiceImpl implements StockFlowLogService {

    @Autowired
    private StockFlowLogMapper stockFlowLogMapper;

    @Autowired
    private UnitService unitService;

    @Autowired
    private TapeStockMapper tapeStockMapper;

    @Autowired
    private FilmStockMapper filmStockMapper;

    @Autowired
    private ChemicalStockMapper chemicalStockMapper;

    @Autowired
    private TapeInboundRequestMapper tapeInboundRequestMapper;

    @Autowired
    private TapeOutboundRequestMapper tapeOutboundRequestMapper;

    @Autowired
    private FilmStockOutMapper filmStockOutMapper;

    @Autowired
    private ChemicalStockOutMapper chemicalStockOutMapper;

    @Override
    public IPage<StockFlowLog> getStockFlowPage(int page, int size, String stockType, String materialCode,
                                               String batchNo, String type, String refNo,
                                               String beginTime, String endTime,
                                               String sortField, String sortOrder) {
        // 统一按数据库层 ORDER BY + LIMIT 分页，避免内存分页导致“仅当前页排序”的体验问题。
        // 如需分切销售聚合展示，建议单独提供聚合接口，不与通用分页混用。
        Page<StockFlowLog> pageParam = new Page<>(page, size);
        IPage<StockFlowLog> result = stockFlowLogMapper.selectPage(pageParam, stockType, materialCode, batchNo, type, refNo, beginTime, endTime, sortField, sortOrder);
        enrichDisplayFields(result.getRecords());
        return result;
    }

    @SuppressWarnings("unused")
    private boolean shouldAggregateSlittingOutbound(String stockType, String type, String batchNo, String refNo) {
        String st = stockType == null ? "" : stockType.trim().toUpperCase();
        String tp = type == null ? "" : type.trim().toUpperCase();
        boolean stockTypeMatch = st.isEmpty() || "TAPE".equals(st);
        boolean typeMatch = tp.isEmpty() || "OUT".equals(tp);
        boolean noStrongExactFilter = (batchNo == null || batchNo.trim().isEmpty()) && (refNo == null || refNo.trim().isEmpty());
        return stockTypeMatch && typeMatch && noStrongExactFilter;
    }

    @SuppressWarnings("unused")
    private IPage<StockFlowLog> getAggregatedSlittingOutboundPage(int page, int size, String stockType, String materialCode,
                                                                  String batchNo, String type, String refNo,
                                                                  String beginTime, String endTime,
                                                                  String sortField, String sortOrder) {
        List<StockFlowLog> raw = stockFlowLogMapper.selectListByFilter(stockType, materialCode, batchNo, type, refNo, beginTime, endTime, sortField, sortOrder);
        List<StockFlowLog> merged = mergeSlittingOutboundLogs(raw);
        applySort(merged, sortField, sortOrder);

        int safePage = Math.max(page, 1);
        int safeSize = Math.max(size, 1);
        int total = merged.size();
        int from = Math.min((safePage - 1) * safeSize, total);
        int to = Math.min(from + safeSize, total);

        Page<StockFlowLog> result = new Page<>(safePage, safeSize, total);
        result.setRecords(merged.subList(from, to));
        return result;
    }

    private List<StockFlowLog> mergeSlittingOutboundLogs(List<StockFlowLog> raw) {
        if (raw == null || raw.isEmpty()) {
            return new ArrayList<>();
        }

        Map<String, StockFlowLog> groupMap = new LinkedHashMap<>();
        List<StockFlowLog> passthrough = new ArrayList<>();
        Map<String, Integer> groupSize = new HashMap<>();

        for (StockFlowLog log : raw) {
            if (!isSlittingOutboundLog(log)) {
                passthrough.add(log);
                continue;
            }

            String minuteKey = formatMinute(log.getCreateTime());
            String key = String.join("|",
                    safe(log.getStockType()),
                    safe(log.getType()),
                    safe(log.getMaterialCode()),
                    safe(log.getProductName()),
                    safe(log.getOperator()),
                    minuteKey
            );

            if (!groupMap.containsKey(key)) {
                StockFlowLog copy = copyLog(log);
                groupMap.put(key, copy);
                groupSize.put(key, 1);
                continue;
            }

            StockFlowLog agg = groupMap.get(key);
            groupSize.put(key, groupSize.getOrDefault(key, 1) + 1);

            agg.setChangeQuantity(nz(agg.getChangeQuantity()).add(nz(log.getChangeQuantity())));
            agg.setStdChangeQuantity(nz(agg.getStdChangeQuantity()).add(nz(log.getStdChangeQuantity())));
            agg.setBeforeQuantity(nz(agg.getBeforeQuantity()).add(nz(log.getBeforeQuantity())));
            agg.setAfterQuantity(nz(agg.getAfterQuantity()).add(nz(log.getAfterQuantity())));

            if (log.getCreateTime() != null && (agg.getCreateTime() == null || log.getCreateTime().isAfter(agg.getCreateTime()))) {
                agg.setCreateTime(log.getCreateTime());
            }

            String existingBatch = safe(agg.getBatchNo());
            String incomingBatch = safe(log.getBatchNo());
            if (!existingBatch.equals(incomingBatch)) {
                agg.setBatchNo("多批次");
            }

            if (!safe(agg.getRefNo()).equals(safe(log.getRefNo()))) {
                agg.setRefNo("批量销售出库");
            }
        }

        for (Map.Entry<String, StockFlowLog> entry : groupMap.entrySet()) {
            int size = groupSize.getOrDefault(entry.getKey(), 1);
            if (size > 1) {
                entry.getValue().setRemark("分切销售出库（聚合" + size + "条）");
            }
            passthrough.add(entry.getValue());
        }

        return passthrough;
    }

    private void applySort(List<StockFlowLog> records, String sortField, String sortOrder) {
        if (records == null || records.isEmpty()) {
            return;
        }
        String field = sortField == null ? "" : sortField.trim();
        String order = sortOrder == null ? "descending" : sortOrder.trim().toLowerCase(Locale.ROOT);
        boolean asc = "asc".equals(order) || "ascending".equals(order);

        Comparator<StockFlowLog> comparator;
        switch (field) {
            case "stockType":
                comparator = Comparator.comparing(StockFlowLog::getStockType, Comparator.nullsLast(String::compareToIgnoreCase));
                break;
            case "type":
                comparator = Comparator.comparing(StockFlowLog::getType, Comparator.nullsLast(String::compareToIgnoreCase));
                break;
            case "materialCode":
                comparator = Comparator.comparing(StockFlowLog::getMaterialCode, Comparator.nullsLast(String::compareToIgnoreCase));
                break;
            case "productName":
                comparator = Comparator.comparing(StockFlowLog::getProductName, Comparator.nullsLast(String::compareToIgnoreCase));
                break;
            case "batchNo":
                comparator = Comparator.comparing(StockFlowLog::getBatchNo, Comparator.nullsLast(String::compareToIgnoreCase));
                break;
            case "changeQuantity":
                comparator = Comparator.comparing(StockFlowLog::getChangeQuantity, Comparator.nullsLast(BigDecimal::compareTo));
                break;
            case "stdChangeQuantity":
                comparator = Comparator.comparing(StockFlowLog::getStdChangeQuantity, Comparator.nullsLast(BigDecimal::compareTo));
                break;
            case "beforeQuantity":
                comparator = Comparator.comparing(StockFlowLog::getBeforeQuantity, Comparator.nullsLast(BigDecimal::compareTo));
                break;
            case "afterQuantity":
                comparator = Comparator.comparing(StockFlowLog::getAfterQuantity, Comparator.nullsLast(BigDecimal::compareTo));
                break;
            case "refNo":
                comparator = Comparator.comparing(StockFlowLog::getRefNo, Comparator.nullsLast(String::compareToIgnoreCase));
                break;
            case "operator":
                comparator = Comparator.comparing(StockFlowLog::getOperator, Comparator.nullsLast(String::compareToIgnoreCase));
                break;
            case "documentTime":
            case "createTime":
            default:
                comparator = Comparator.comparing(StockFlowLog::getCreateTime, Comparator.nullsLast(LocalDateTime::compareTo));
                break;
        }
        if (!asc) {
            comparator = comparator.reversed();
        }
        records.sort(comparator);
    }

    private boolean isSlittingOutboundLog(StockFlowLog log) {
        if (log == null) {
            return false;
        }
        if (!"TAPE".equalsIgnoreCase(safe(log.getStockType()))) {
            return false;
        }
        if (!"OUT".equalsIgnoreCase(safe(log.getType()))) {
            return false;
        }
        String batchNo = safe(log.getBatchNo()).toUpperCase();
        String productName = safe(log.getProductName()).toUpperCase();
        return batchNo.contains("SLITTING") || productName.contains("SLITTING");
    }

    private String formatMinute(LocalDateTime time) {
        if (time == null) {
            return "";
        }
        return time.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
    }

    private StockFlowLog copyLog(StockFlowLog src) {
        StockFlowLog dst = new StockFlowLog();
        dst.setId(src.getId());
        dst.setStockType(src.getStockType());
        dst.setStockId(src.getStockId());
        dst.setBatchNo(src.getBatchNo());
        dst.setMaterialCode(src.getMaterialCode());
        dst.setProductName(src.getProductName());
        dst.setType(src.getType());
        dst.setChangeQuantity(src.getChangeQuantity());
        dst.setUnit(src.getUnit());
        dst.setStdChangeQuantity(src.getStdChangeQuantity());
        dst.setStdUnit(src.getStdUnit());
        dst.setBeforeQuantity(src.getBeforeQuantity());
        dst.setAfterQuantity(src.getAfterQuantity());
        dst.setStdBeforeQuantity(src.getStdBeforeQuantity());
        dst.setStdAfterQuantity(src.getStdAfterQuantity());
        dst.setRefNo(src.getRefNo());
        dst.setOperator(src.getOperator());
        dst.setRemark(src.getRemark());
        dst.setSpecDesc(src.getSpecDesc());
        dst.setDocumentTime(src.getDocumentTime());
        dst.setCreateTime(src.getCreateTime());
        return dst;
    }

    private void enrichDisplayFields(List<StockFlowLog> records) {
        if (records == null || records.isEmpty()) {
            return;
        }
        for (StockFlowLog log : records) {
            if (log == null) {
                continue;
            }
            log.setSpecDesc(resolveSpecDesc(log));
            LocalDateTime docTime = resolveDocumentTime(log);
            log.setDocumentTime(docTime != null ? docTime : log.getCreateTime());
        }
    }

    private String resolveSpecDesc(StockFlowLog log) {
        if (log == null || log.getStockId() == null) {
            return "";
        }

        String stockType = safe(log.getStockType()).toUpperCase();
        Long stockId = log.getStockId();

        if ("TAPE".equals(stockType)) {
            TapeStock stock = tapeStockMapper.selectById(stockId);
            if (stock == null) {
                return "";
            }
            if (safe(stock.getSpecDesc()).length() > 0) {
                return stock.getSpecDesc();
            }
            Integer t = stock.getThickness();
            Integer w = stock.getWidth();
            Integer l = stock.getCurrentLength() != null ? stock.getCurrentLength() : stock.getLength();
            if (t != null && w != null && l != null) {
                return t + "μm*" + w + "mm*" + l + "m";
            }
            return "";
        }

        if ("FILM".equals(stockType)) {
            FilmStock stock = filmStockMapper.selectById(stockId);
            if (stock == null) {
                return "";
            }
            if (safe(stock.getSpecDesc()).length() > 0) {
                return stock.getSpecDesc();
            }
            if (stock.getThickness() != null && stock.getWidth() != null) {
                return stock.getThickness().stripTrailingZeros().toPlainString() + "μm*" + stock.getWidth() + "mm";
            }
            return "";
        }

        if ("CHEMICAL".equals(stockType)) {
            ChemicalStock stock = chemicalStockMapper.selectById(stockId);
            if (stock == null) {
                return "";
            }
            if (stock.getUnitWeight() != null && safe(stock.getUnit()).length() > 0) {
                return stock.getUnitWeight().stripTrailingZeros().toPlainString() + "kg/" + stock.getUnit();
            }
            return safe(stock.getUnit());
        }

        return "";
    }

    private LocalDateTime resolveDocumentTime(StockFlowLog log) {
        if (log == null) {
            return null;
        }
        String stockType = safe(log.getStockType()).toUpperCase();
        String type = safe(log.getType()).toUpperCase();
        String refNo = safe(log.getRefNo());

        // 统一口径：所有 INxxxx 关联单号优先回溯入库申请时间（审批时间>申请时间>创建时间）
        if (refNo.startsWith("IN")) {
            LocalDateTime dt = resolveInboundRequestTimeByRefNo(refNo);
            if (dt != null) {
                return dt;
            }
        }

        if ("TAPE".equals(stockType)) {
            if (refNo.startsWith("IN")) {
                LocalDateTime dt = resolveInboundRequestTimeByRefNo(refNo);
                if (dt != null) {
                    return dt;
                }
            }
            if (refNo.startsWith("OUT")) {
                LocalDateTime dt = resolveTapeOutboundRequestTimeByRefNo(refNo);
                if (dt != null) {
                    return dt;
                }
            }
            return log.getCreateTime();
        }

        if ("FILM".equals(stockType) && "OUT".equals(type)) {
            FilmStockOut out = findFilmOutbound(log, refNo);
            if (out != null) {
                LocalDateTime dt = toLocalDateTime(out.getOutboundTime());
                if (dt != null) {
                    return dt;
                }
                return toLocalDateTime(out.getCreateTime());
            }
        }

        if ("CHEMICAL".equals(stockType) && "OUT".equals(type)) {
            ChemicalStockOut out = findChemicalOutbound(log, refNo);
            if (out != null) {
                LocalDateTime dt = toLocalDateTime(out.getOutboundTime());
                if (dt != null) {
                    return dt;
                }
                return toLocalDateTime(out.getCreateTime());
            }
        }

        return log.getCreateTime();
    }

    private LocalDateTime resolveInboundRequestTimeByRefNo(String refNo) {
        if (safe(refNo).isEmpty()) {
            return null;
        }
        LambdaQueryWrapper<TapeInboundRequest> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TapeInboundRequest::getRequestNo, refNo).last("LIMIT 1");
        TapeInboundRequest req = tapeInboundRequestMapper.selectOne(wrapper);
        if (req == null) {
            return null;
        }
        if (req.getAuditTime() != null) {
            return req.getAuditTime();
        }
        if (req.getApplyTime() != null) {
            return req.getApplyTime();
        }
        return req.getCreateTime();
    }

    private LocalDateTime resolveTapeOutboundRequestTimeByRefNo(String refNo) {
        if (safe(refNo).isEmpty()) {
            return null;
        }
        LambdaQueryWrapper<TapeOutboundRequest> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TapeOutboundRequest::getRequestNo, refNo).last("LIMIT 1");
        TapeOutboundRequest req = tapeOutboundRequestMapper.selectOne(wrapper);
        if (req == null) {
            return null;
        }
        if (req.getAuditTime() != null) {
            return req.getAuditTime();
        }
        if (req.getApplyTime() != null) {
            return req.getApplyTime();
        }
        return req.getCreateTime();
    }

    private FilmStockOut findFilmOutbound(StockFlowLog log, String refNo) {
        if (safe(refNo).length() > 0 && !"MANUAL_OUT".equalsIgnoreCase(refNo)) {
            LambdaQueryWrapper<FilmStockOut> byNo = new LambdaQueryWrapper<>();
            byNo.eq(FilmStockOut::getOutboundNo, refNo).orderByDesc(FilmStockOut::getId).last("LIMIT 1");
            FilmStockOut out = filmStockOutMapper.selectOne(byNo);
            if (out != null) {
                return out;
            }
        }

        Long scheduleId = tryParseLong(refNo);
        if (scheduleId != null) {
            List<FilmStockOut> outs = filmStockOutMapper.selectByScheduleId(scheduleId);
            if (outs != null && !outs.isEmpty()) {
                for (FilmStockOut item : outs) {
                    if (item != null && log.getStockId() != null && log.getStockId().equals(item.getFilmStockId())) {
                        return item;
                    }
                }
                return outs.get(0);
            }
        }
        return null;
    }

    private ChemicalStockOut findChemicalOutbound(StockFlowLog log, String refNo) {
        if (safe(refNo).length() > 0 && !"MANUAL_OUT".equalsIgnoreCase(refNo)) {
            LambdaQueryWrapper<ChemicalStockOut> byNo = new LambdaQueryWrapper<>();
            byNo.eq(ChemicalStockOut::getOutboundNo, refNo).orderByDesc(ChemicalStockOut::getId).last("LIMIT 1");
            ChemicalStockOut out = chemicalStockOutMapper.selectOne(byNo);
            if (out != null) {
                return out;
            }
        }

        Long scheduleId = tryParseLong(refNo);
        if (scheduleId != null) {
            List<ChemicalStockOut> outs = chemicalStockOutMapper.selectByScheduleId(scheduleId);
            if (outs != null && !outs.isEmpty()) {
                for (ChemicalStockOut item : outs) {
                    if (item != null && log.getStockId() != null && log.getStockId().equals(item.getChemicalStockId())) {
                        return item;
                    }
                }
                return outs.get(0);
            }
        }
        return null;
    }

    private Long tryParseLong(String text) {
        if (!org.springframework.util.StringUtils.hasText(text)) {
            return null;
        }
        try {
            return Long.parseLong(text.trim());
        } catch (Exception ignored) {
            return null;
        }
    }

    private LocalDateTime toLocalDateTime(Date date) {
        if (date == null) {
            return null;
        }
        return LocalDateTime.ofInstant(date.toInstant(), java.time.ZoneId.systemDefault());
    }

    private String safe(String v) {
        return v == null ? "" : v.trim();
    }

    private BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    @Override
    public List<StockFlowLog> getStockFlowByStock(String stockType, Long stockId) {
        return stockFlowLogMapper.selectByStock(stockType, stockId);
    }

    @Override
    public void logStockChange(String stockType, Long stockId, String batchNo, String materialCode,
                              String productName, String type, BigDecimal changeQuantity,
                              String unit, BigDecimal beforeQuantity, BigDecimal afterQuantity,
                              String refNo, String operator, String remark) {
        StockFlowLog log = new StockFlowLog();
        log.setStockType(stockType);
        log.setStockId(stockId);
        log.setBatchNo(batchNo);
        log.setMaterialCode(materialCode);
        log.setProductName(productName);
        log.setType(type);
        log.setChangeQuantity(changeQuantity);
        log.setUnit(unit);
        // 计算并写入标准单位及标准数量（如果能转换）
        try {
            UnitConversionResult conv = unitService.toStandard(changeQuantity, unit);
            if (conv != null) {
                log.setStdChangeQuantity(conv.getQuantity());
                log.setStdUnit(conv.getUnit());
            }
            UnitConversionResult beforeConv = unitService.toStandard(beforeQuantity, unit);
            if (beforeConv != null) {
                log.setStdBeforeQuantity(beforeConv.getQuantity());
            }
            UnitConversionResult afterConv = unitService.toStandard(afterQuantity, unit);
            if (afterConv != null) {
                log.setStdAfterQuantity(afterConv.getQuantity());
            }
        } catch (Exception ex) {
            // 保守处理：若转换失败，不阻塞主流程，只记录原始单位
        }
        log.setBeforeQuantity(beforeQuantity);
        log.setAfterQuantity(afterQuantity);
        log.setRefNo(refNo);
        log.setOperator(operator);
        log.setRemark(remark);

        stockFlowLogMapper.insert(log);
    }
}