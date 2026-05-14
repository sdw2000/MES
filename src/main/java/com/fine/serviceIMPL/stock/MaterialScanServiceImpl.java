package com.fine.serviceIMPL.stock;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fine.Dao.stock.*;
import com.fine.model.stock.*;
import com.fine.service.stock.MaterialScanService;
import com.fine.service.stock.StockFlowLogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.SimpleDateFormat;
import java.util.*;

@Service
public class MaterialScanServiceImpl implements MaterialScanService {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(3, RoundingMode.HALF_UP);

    @Autowired
    private MaterialScanTxnMapper materialScanTxnMapper;
    @Autowired
    private FilmStockDetailMapper filmStockDetailMapper;
    @Autowired
    private FilmStockMapper filmStockMapper;
    @Autowired
    private FilmStockOutMapper filmStockOutMapper;
    @Autowired
    private ChemicalStockDetailMapper chemicalStockDetailMapper;
    @Autowired
    private ChemicalStockMapper chemicalStockMapper;
    @Autowired
    private ChemicalStockOutMapper chemicalStockOutMapper;
    @Autowired
    private StockFlowLogService stockFlowLogService;

    @Override
    public Map<String, Object> resolveByCode(String code, String stockTypeHint) {
        String qr = trimToNull(code);
        if (qr == null) {
            throw new RuntimeException("二维码不能为空");
        }
        String hint = normalizeStockType(stockTypeHint);

        if (MaterialScanTxn.STOCK_TYPE_FILM.equals(hint) || hint == null) {
            Map<String, Object> film = resolveFilm(qr);
            if (film != null) {
                return film;
            }
        }
        if (MaterialScanTxn.STOCK_TYPE_CHEMICAL.equals(hint) || hint == null) {
            Map<String, Object> chem = resolveChemical(qr);
            if (chem != null) {
                return chem;
            }
        }
        throw new RuntimeException("未找到对应二维码库存: " + qr);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> issueByScan(Map<String, Object> payload) {
        String idempotencyKey = trimToNull(stringVal(payload.get("idempotencyKey")));
        if (idempotencyKey != null) {
            MaterialScanTxn existed = findByIdempotencyKey(idempotencyKey);
            if (existed != null) {
                return buildTxnResult(existed);
            }
        }

        String qrCode = trimToNull(stringVal(payload.get("qrCode")));
        Integer packQty = toInteger(payload.get("packQty"));
        BigDecimal qty = toBigDecimal(payload.get("qty"));
        if (qrCode == null) {
            throw new RuntimeException("qrCode不能为空");
        }
        if ((packQty == null || packQty <= 0) && (qty == null || qty.compareTo(BigDecimal.ZERO) <= 0)) {
            throw new RuntimeException("packQty或qty必须大于0");
        }
        if (qty != null) {
            qty = qty.setScale(3, RoundingMode.HALF_UP);
        }

        String stockType = normalizeStockType(stringVal(payload.get("stockType")));
        Map<String, Object> target = resolveByCode(qrCode, stockType);
        String actualStockType = stringVal(target.get("stockType"));

        if (MaterialScanTxn.STOCK_TYPE_FILM.equals(actualStockType)) {
            return issueFilm(payload, target, qty, packQty, idempotencyKey);
        }
        if (MaterialScanTxn.STOCK_TYPE_CHEMICAL.equals(actualStockType)) {
            return issueChemical(payload, target, qty, packQty, idempotencyKey);
        }
        throw new RuntimeException("暂不支持该库存类型扫码领料: " + actualStockType);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> returnByScan(Map<String, Object> payload) {
        String idempotencyKey = trimToNull(stringVal(payload.get("idempotencyKey")));
        if (idempotencyKey != null) {
            MaterialScanTxn existed = findByIdempotencyKey(idempotencyKey);
            if (existed != null) {
                return buildTxnResult(existed);
            }
        }

        String qrCode = trimToNull(stringVal(payload.get("qrCode")));
        Long sourceIssueTxnId = toLong(payload.get("sourceIssueTxnId"));
        Integer packQty = toInteger(payload.get("packQty"));
        BigDecimal qty = toBigDecimal(payload.get("qty"));
        if (qrCode == null) {
            throw new RuntimeException("qrCode不能为空");
        }
        if (sourceIssueTxnId == null || sourceIssueTxnId <= 0) {
            throw new RuntimeException("sourceIssueTxnId不能为空");
        }
        if ((packQty == null || packQty <= 0) && (qty == null || qty.compareTo(BigDecimal.ZERO) <= 0)) {
            throw new RuntimeException("packQty或qty必须大于0");
        }
        if (qty != null) {
            qty = qty.setScale(3, RoundingMode.HALF_UP);
        }

        MaterialScanTxn sourceIssue = materialScanTxnMapper.selectById(sourceIssueTxnId);
        if (sourceIssue == null || !MaterialScanTxn.TXN_TYPE_ISSUE.equalsIgnoreCase(stringVal(sourceIssue.getTxnType()))) {
            throw new RuntimeException("sourceIssueTxnId无效，未找到原领料记录");
        }

        boolean sourcePackMode = sourceIssue.getPackQty() != null && sourceIssue.getPackQty() > 0;
        if (sourcePackMode) {
            if (packQty == null || packQty <= 0) {
                throw new RuntimeException("该记录按包装领料，退料必须传packQty且大于0");
            }
            Integer returnedPackQty = sumReturnedPackQty(sourceIssueTxnId);
            int maxReturnPack = sourceIssue.getPackQty() - returnedPackQty;
            if (maxReturnPack <= 0) {
                throw new RuntimeException("该领料记录已全部退回");
            }
            if (packQty > maxReturnPack) {
                throw new RuntimeException("退料包装数超过可退数量，可退=" + maxReturnPack + "，本次=" + packQty);
            }
        } else {
            BigDecimal returnedQty = sumReturnedQty(sourceIssueTxnId);
            BigDecimal maxReturn = nvl(sourceIssue.getQty()).subtract(returnedQty).setScale(3, RoundingMode.HALF_UP);
            if (maxReturn.compareTo(BigDecimal.ZERO) <= 0) {
                throw new RuntimeException("该领料记录已全部退回");
            }
            if (qty == null || qty.compareTo(maxReturn) > 0) {
                throw new RuntimeException("退料数量超过可退数量，可退=" + maxReturn + "，本次=" + qty);
            }
        }

        String stockType = stringVal(sourceIssue.getStockType());
        if (MaterialScanTxn.STOCK_TYPE_FILM.equalsIgnoreCase(stockType)) {
            return returnFilm(payload, qrCode, sourceIssue, qty, packQty, idempotencyKey);
        }
        if (MaterialScanTxn.STOCK_TYPE_CHEMICAL.equalsIgnoreCase(stockType)) {
            return returnChemical(payload, qrCode, sourceIssue, qty, packQty, idempotencyKey);
        }
        throw new RuntimeException("暂不支持该库存类型扫码退料: " + stockType);
    }

    @Override
    public IPage<MaterialScanTxn> getTxnPage(int page,
                                             int size,
                                             String txnType,
                                             String stockType,
                                             String orderNo,
                                             Long scheduleId,
                                             String qrCode) {
        Page<MaterialScanTxn> pageParam = new Page<>(Math.max(page, 1), Math.max(size, 1));
        LambdaQueryWrapper<MaterialScanTxn> qw = new LambdaQueryWrapper<>();
        qw.eq(trimToNull(txnType) != null, MaterialScanTxn::getTxnType, trimToNull(txnType));
        qw.eq(trimToNull(stockType) != null, MaterialScanTxn::getStockType, normalizeStockType(stockType));
        qw.eq(trimToNull(orderNo) != null, MaterialScanTxn::getOrderNo, trimToNull(orderNo));
        qw.eq(scheduleId != null && scheduleId > 0, MaterialScanTxn::getScheduleId, scheduleId);
        String qr = trimToNull(qrCode);
        if (qr != null) {
            qw.and(w -> w.eq(MaterialScanTxn::getQrCode, qr).or().eq(MaterialScanTxn::getBatchNo, qr));
        }
        qw.orderByDesc(MaterialScanTxn::getId);
        return materialScanTxnMapper.selectPage(pageParam, qw);
    }

    private Map<String, Object> issueFilm(Map<String, Object> payload,
                                          Map<String, Object> target,
                                          BigDecimal qty,
                                          Integer packQty,
                                          String idempotencyKey) {
        Long detailId = toLong(target.get("detailId"));
        FilmStockDetail detail = lockFilmDetail(detailId);
        if (detail == null || detail.getId() == null) {
            throw new RuntimeException("薄膜明细不存在或已删除");
        }
        if (detail.getIsDeleted() != null && detail.getIsDeleted() == 1) {
            throw new RuntimeException("薄膜明细已删除");
        }
        if ("locked".equalsIgnoreCase(stringVal(detail.getStatus()))) {
            throw new RuntimeException("该薄膜已锁定，不能扫码领料");
        }

        BigDecimal stdQtyPerPack = detail.getStdQtyPerPack();
        String stdUom = trimToNull(detail.getStdUom()) == null ? "m" : trimToNull(detail.getStdUom());
        String packUom = trimToNull(detail.getPackUom()) == null ? "卷" : trimToNull(detail.getPackUom());
        Integer detailPackCount = detail.getPackCount();
        int packQtyVal = packQty == null ? 0 : packQty.intValue();
        boolean packMode = packQtyVal > 0;
        if (packMode && (stdQtyPerPack == null || stdQtyPerPack.compareTo(BigDecimal.ZERO) <= 0)) {
            throw new RuntimeException("该薄膜未维护stdQtyPerPack，无法按包装领料");
        }
        if (packMode && detailPackCount != null && detailPackCount < packQtyVal) {
            throw new RuntimeException("薄膜包装数不足，可用=" + detailPackCount + packUom + "，需求=" + packQtyVal + packUom);
        }

        BigDecimal effectiveQty = packMode ? stdQtyPerPack.multiply(BigDecimal.valueOf(packQtyVal)).setScale(3, RoundingMode.HALF_UP) : qty;
        if (effectiveQty == null || effectiveQty.compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("领料数量无效");
        }

        BigDecimal before = resolveFilmCurrentLengthM(detail);
        if (before.compareTo(effectiveQty) < 0) {
            throw new RuntimeException("薄膜长度不足，可用=" + before + "m，需求=" + effectiveQty + "m");
        }
        BigDecimal after = before.subtract(effectiveQty).setScale(3, RoundingMode.HALF_UP);
        BigDecimal original = resolveFilmOriginalLengthM(detail, before);

        if (packMode && detailPackCount != null) {
            detail.setPackCount(Math.max(detailPackCount - packQtyVal, 0));
        }

        applyFilmLengthChange(detail, original, after, stringVal(payload.get("operator")));
        recalcFilmStock(detail.getFilmStockId(), stringVal(payload.get("operator")));

        MaterialScanTxn txn = buildBaseTxn(payload, MaterialScanTxn.TXN_TYPE_ISSUE, MaterialScanTxn.STOCK_TYPE_FILM, idempotencyKey);
        txn.setDetailId(detail.getId());
        txn.setStockId(detail.getFilmStockId());
        txn.setQrCode(trimToNull(stringVal(payload.get("qrCode"))));
        txn.setBatchNo(trimToNull(detail.getBatchNo()));
        txn.setMaterialCode(trimToNull(detail.getMaterialCode()));
        txn.setQty(effectiveQty);
        txn.setUnit("m");
        txn.setPackQty(packMode ? packQty : null);
        txn.setPackUom(packMode ? packUom : null);
        txn.setStdQty(packMode ? effectiveQty : null);
        txn.setStdUom(packMode ? stdUom : null);
        txn.setBeforeQty(before);
        txn.setAfterQty(after);
        materialScanTxnMapper.insert(txn);

        createFilmOutRecord(detail, effectiveQty, txn);
        logFilmFlow(detail, effectiveQty.negate(), before, after, txn);
        return buildTxnResult(txn);
    }

    private Map<String, Object> returnFilm(Map<String, Object> payload,
                                           String qrCode,
                                           MaterialScanTxn sourceIssue,
                                           BigDecimal qty,
                                           Integer packQty,
                                           String idempotencyKey) {
        Long detailId = sourceIssue.getDetailId();
        FilmStockDetail detail = lockFilmDetail(detailId);
        if (detail == null || detail.getId() == null) {
            throw new RuntimeException("原领料对应薄膜明细不存在");
        }

        String sourceQr = trimToNull(sourceIssue.getQrCode());
        if (sourceQr != null && !sourceQr.equals(qrCode)) {
            throw new RuntimeException("退料二维码与原领料不一致，必须退回同一码");
        }

        boolean sourcePackMode = sourceIssue.getPackQty() != null && sourceIssue.getPackQty() > 0;
        BigDecimal stdQtyPerPack = sourceIssue.getPackQty() != null && sourceIssue.getPackQty() > 0
            ? nvl(sourceIssue.getQty()).divide(BigDecimal.valueOf(sourceIssue.getPackQty()), 6, RoundingMode.HALF_UP)
            : detail.getStdQtyPerPack();
        String packUom = trimToNull(sourceIssue.getPackUom()) == null ? (trimToNull(detail.getPackUom()) == null ? "卷" : trimToNull(detail.getPackUom())) : trimToNull(sourceIssue.getPackUom());
        String stdUom = trimToNull(sourceIssue.getStdUom()) == null ? "m" : trimToNull(sourceIssue.getStdUom());
        BigDecimal effectiveQty = sourcePackMode
            ? stdQtyPerPack.multiply(BigDecimal.valueOf(packQty)).setScale(3, RoundingMode.HALF_UP)
            : qty;

        BigDecimal before = resolveFilmCurrentLengthM(detail);
        BigDecimal original = resolveFilmOriginalLengthM(detail, before);
        BigDecimal after = before.add(effectiveQty).setScale(3, RoundingMode.HALF_UP);
        if (after.compareTo(original) > 0) {
            throw new RuntimeException("退料后长度超过原始长度，原始=" + original + "m，退后=" + after + "m");
        }

        if (sourcePackMode && detail.getPackCount() != null) {
            detail.setPackCount(detail.getPackCount() + packQty);
        }

        applyFilmLengthChange(detail, original, after, stringVal(payload.get("operator")));
        recalcFilmStock(detail.getFilmStockId(), stringVal(payload.get("operator")));

        MaterialScanTxn txn = buildBaseTxn(payload, MaterialScanTxn.TXN_TYPE_RETURN, MaterialScanTxn.STOCK_TYPE_FILM, idempotencyKey);
        txn.setSourceIssueTxnId(sourceIssue.getId());
        txn.setDetailId(detail.getId());
        txn.setStockId(detail.getFilmStockId());
        txn.setQrCode(qrCode);
        txn.setBatchNo(trimToNull(detail.getBatchNo()));
        txn.setMaterialCode(trimToNull(detail.getMaterialCode()));
        txn.setQty(effectiveQty);
        txn.setUnit("m");
        txn.setPackQty(sourcePackMode ? packQty : null);
        txn.setPackUom(sourcePackMode ? packUom : null);
        txn.setStdQty(sourcePackMode ? effectiveQty : null);
        txn.setStdUom(sourcePackMode ? stdUom : null);
        txn.setBeforeQty(before);
        txn.setAfterQty(after);
        if (txn.getOrderNo() == null) {
            txn.setOrderNo(sourceIssue.getOrderNo());
        }
        if (txn.getScheduleId() == null) {
            txn.setScheduleId(sourceIssue.getScheduleId());
        }
        if (txn.getProcessType() == null) {
            txn.setProcessType(sourceIssue.getProcessType());
        }
        materialScanTxnMapper.insert(txn);

        logFilmFlow(detail, effectiveQty, before, after, txn);
        return buildTxnResult(txn);
    }

    private Map<String, Object> issueChemical(Map<String, Object> payload,
                                              Map<String, Object> target,
                                              BigDecimal qty,
                                              Integer packQty,
                                              String idempotencyKey) {
        Long detailId = toLong(target.get("detailId"));
        ChemicalStockDetail detail = lockChemicalDetail(detailId);
        if (detail == null || detail.getId() == null) {
            throw new RuntimeException("化工明细不存在");
        }
        if ("locked".equalsIgnoreCase(stringVal(detail.getStatus()))) {
            throw new RuntimeException("该化工批次已锁定，不能扫码领料");
        }

        BigDecimal stdQtyPerPack = detail.getStdQtyPerPack();
        String stdUom = trimToNull(detail.getStdUom()) == null ? "kg" : trimToNull(detail.getStdUom());
        String packUom = trimToNull(detail.getPackUom()) == null ? "桶" : trimToNull(detail.getPackUom());
        Integer detailPackCount = detail.getPackCount();
        int packQtyVal = packQty == null ? 0 : packQty.intValue();
        boolean packMode = packQtyVal > 0;
        if (packMode && (stdQtyPerPack == null || stdQtyPerPack.compareTo(BigDecimal.ZERO) <= 0)) {
            throw new RuntimeException("该化工料未维护stdQtyPerPack，无法按包装领料");
        }
        if (packMode && detailPackCount != null && detailPackCount < packQtyVal) {
            throw new RuntimeException("化工包装数不足，可用=" + detailPackCount + packUom + "，需求=" + packQtyVal + packUom);
        }

        BigDecimal effectiveQty = packMode ? stdQtyPerPack.multiply(BigDecimal.valueOf(packQtyVal)).setScale(3, RoundingMode.HALF_UP) : qty;
        if (effectiveQty == null || effectiveQty.compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("领料数量无效");
        }

        BigDecimal before = nvl(detail.getWeight());
        if (before.compareTo(effectiveQty) < 0) {
            throw new RuntimeException("化工重量不足，可用=" + before + "kg，需求=" + effectiveQty + "kg");
        }
        BigDecimal after = before.subtract(effectiveQty).setScale(3, RoundingMode.HALF_UP);

        if (packMode && detailPackCount != null) {
            detail.setPackCount(Math.max(detailPackCount - packQtyVal, 0));
        }

        applyChemicalWeightChange(detail, after);
        recalcChemicalStock(detail.getChemicalStockId(), stringVal(payload.get("operator")));

        MaterialScanTxn txn = buildBaseTxn(payload, MaterialScanTxn.TXN_TYPE_ISSUE, MaterialScanTxn.STOCK_TYPE_CHEMICAL, idempotencyKey);
        txn.setDetailId(detail.getId());
        txn.setStockId(detail.getChemicalStockId());
        txn.setQrCode(trimToNull(stringVal(payload.get("qrCode"))));
        txn.setBatchNo(trimToNull(detail.getBatchNo()));
        txn.setMaterialCode(trimToNull(detail.getMaterialCode()));
        txn.setQty(effectiveQty);
        txn.setUnit("kg");
        txn.setPackQty(packMode ? packQty : null);
        txn.setPackUom(packMode ? packUom : null);
        txn.setStdQty(packMode ? effectiveQty : null);
        txn.setStdUom(packMode ? stdUom : null);
        txn.setBeforeQty(before);
        txn.setAfterQty(after);
        materialScanTxnMapper.insert(txn);

        createChemicalOutRecord(detail, effectiveQty, txn);
        logChemicalFlow(detail, effectiveQty.negate(), before, after, txn);
        return buildTxnResult(txn);
    }

    private Map<String, Object> returnChemical(Map<String, Object> payload,
                                               String qrCode,
                                               MaterialScanTxn sourceIssue,
                                               BigDecimal qty,
                                               Integer packQty,
                                               String idempotencyKey) {
        Long detailId = sourceIssue.getDetailId();
        ChemicalStockDetail detail = lockChemicalDetail(detailId);
        if (detail == null || detail.getId() == null) {
            throw new RuntimeException("原领料对应化工明细不存在");
        }

        String sourceQr = trimToNull(sourceIssue.getQrCode());
        if (sourceQr != null && !sourceQr.equals(qrCode)) {
            throw new RuntimeException("退料二维码与原领料不一致，必须退回同一码");
        }

        boolean sourcePackMode = sourceIssue.getPackQty() != null && sourceIssue.getPackQty() > 0;
        BigDecimal stdQtyPerPack = sourceIssue.getPackQty() != null && sourceIssue.getPackQty() > 0
            ? nvl(sourceIssue.getQty()).divide(BigDecimal.valueOf(sourceIssue.getPackQty()), 6, RoundingMode.HALF_UP)
            : detail.getStdQtyPerPack();
        String packUom = trimToNull(sourceIssue.getPackUom()) == null ? (trimToNull(detail.getPackUom()) == null ? "桶" : trimToNull(detail.getPackUom())) : trimToNull(sourceIssue.getPackUom());
        String stdUom = trimToNull(sourceIssue.getStdUom()) == null ? "kg" : trimToNull(sourceIssue.getStdUom());
        BigDecimal effectiveQty = sourcePackMode
            ? stdQtyPerPack.multiply(BigDecimal.valueOf(packQty)).setScale(3, RoundingMode.HALF_UP)
            : qty;

        BigDecimal before = nvl(detail.getWeight());
        BigDecimal after = before.add(effectiveQty).setScale(3, RoundingMode.HALF_UP);

        if (sourcePackMode && detail.getPackCount() != null) {
            detail.setPackCount(detail.getPackCount() + packQty);
        }

        applyChemicalWeightChange(detail, after);
        recalcChemicalStock(detail.getChemicalStockId(), stringVal(payload.get("operator")));

        MaterialScanTxn txn = buildBaseTxn(payload, MaterialScanTxn.TXN_TYPE_RETURN, MaterialScanTxn.STOCK_TYPE_CHEMICAL, idempotencyKey);
        txn.setSourceIssueTxnId(sourceIssue.getId());
        txn.setDetailId(detail.getId());
        txn.setStockId(detail.getChemicalStockId());
        txn.setQrCode(qrCode);
        txn.setBatchNo(trimToNull(detail.getBatchNo()));
        txn.setMaterialCode(trimToNull(detail.getMaterialCode()));
        txn.setQty(effectiveQty);
        txn.setUnit("kg");
        txn.setPackQty(sourcePackMode ? packQty : null);
        txn.setPackUom(sourcePackMode ? packUom : null);
        txn.setStdQty(sourcePackMode ? effectiveQty : null);
        txn.setStdUom(sourcePackMode ? stdUom : null);
        txn.setBeforeQty(before);
        txn.setAfterQty(after);
        if (txn.getOrderNo() == null) {
            txn.setOrderNo(sourceIssue.getOrderNo());
        }
        if (txn.getScheduleId() == null) {
            txn.setScheduleId(sourceIssue.getScheduleId());
        }
        if (txn.getProcessType() == null) {
            txn.setProcessType(sourceIssue.getProcessType());
        }
        materialScanTxnMapper.insert(txn);

        logChemicalFlow(detail, effectiveQty, before, after, txn);
        return buildTxnResult(txn);
    }

    private Map<String, Object> resolveFilm(String code) {
        LambdaQueryWrapper<FilmStockDetail> qw = new LambdaQueryWrapper<>();
        qw.eq(FilmStockDetail::getIsDeleted, 0)
                .and(w -> w.eq(FilmStockDetail::getRollNo, code).or().eq(FilmStockDetail::getBatchNo, code))
                .orderByAsc(FilmStockDetail::getId)
                .last("LIMIT 20");
        List<FilmStockDetail> list = filmStockDetailMapper.selectList(qw);
        if (list == null || list.isEmpty()) {
            return null;
        }
        FilmStockDetail chosen = pickFilmDetailByCode(list, code);
        if (chosen == null) {
            return null;
        }
        FilmStock stock = chosen.getFilmStockId() == null ? null : filmStockMapper.selectById(chosen.getFilmStockId());

        BigDecimal currentLen = resolveFilmCurrentLengthM(chosen);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("stockType", MaterialScanTxn.STOCK_TYPE_FILM);
        data.put("detailId", chosen.getId());
        data.put("stockId", chosen.getFilmStockId());
        data.put("qrCode", trimToNull(chosen.getRollNo()) != null ? trimToNull(chosen.getRollNo()) : trimToNull(chosen.getBatchNo()));
        data.put("batchNo", chosen.getBatchNo());
        data.put("materialCode", chosen.getMaterialCode());
        data.put("materialName", stock == null ? null : stock.getMaterialName());
        data.put("qty", currentLen);
        data.put("unit", "m");
        data.put("packUom", chosen.getPackUom());
        data.put("packCount", chosen.getPackCount());
        data.put("stdUom", chosen.getStdUom());
        data.put("stdQtyPerPack", chosen.getStdQtyPerPack());
        data.put("status", chosen.getStatus());
        data.put("width", chosen.getWidth());
        return data;
    }

    private Map<String, Object> resolveChemical(String code) {
        LambdaQueryWrapper<ChemicalStockDetail> qw = new LambdaQueryWrapper<>();
        qw.and(w -> w.eq(ChemicalStockDetail::getContainerNo, code).or().eq(ChemicalStockDetail::getBatchNo, code))
                .orderByAsc(ChemicalStockDetail::getId)
                .last("LIMIT 20");
        List<ChemicalStockDetail> list = chemicalStockDetailMapper.selectList(qw);
        if (list == null || list.isEmpty()) {
            return null;
        }
        ChemicalStockDetail chosen = pickChemicalDetailByCode(list, code);
        if (chosen == null) {
            return null;
        }
        ChemicalStock stock = chosen.getChemicalStockId() == null ? null : chemicalStockMapper.selectById(chosen.getChemicalStockId());

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("stockType", MaterialScanTxn.STOCK_TYPE_CHEMICAL);
        data.put("detailId", chosen.getId());
        data.put("stockId", chosen.getChemicalStockId());
        data.put("qrCode", trimToNull(chosen.getContainerNo()) != null ? trimToNull(chosen.getContainerNo()) : trimToNull(chosen.getBatchNo()));
        data.put("batchNo", chosen.getBatchNo());
        data.put("materialCode", chosen.getMaterialCode());
        data.put("materialName", stock == null ? null : stock.getMaterialName());
        data.put("qty", nvl(chosen.getWeight()));
        data.put("unit", "kg");
        data.put("packUom", chosen.getPackUom());
        data.put("packCount", chosen.getPackCount());
        data.put("stdUom", chosen.getStdUom());
        data.put("stdQtyPerPack", chosen.getStdQtyPerPack());
        data.put("status", chosen.getStatus());
        return data;
    }

    private FilmStockDetail pickFilmDetailByCode(List<FilmStockDetail> list, String code) {
        if (list == null || list.isEmpty()) {
            return null;
        }
        for (FilmStockDetail d : list) {
            if (d != null && code.equals(trimToNull(d.getRollNo()))) {
                return d;
            }
        }
        for (FilmStockDetail d : list) {
            if (d != null && "available".equalsIgnoreCase(stringVal(d.getStatus()))) {
                return d;
            }
        }
        return list.get(0);
    }

    private ChemicalStockDetail pickChemicalDetailByCode(List<ChemicalStockDetail> list, String code) {
        if (list == null || list.isEmpty()) {
            return null;
        }
        for (ChemicalStockDetail d : list) {
            if (d != null && code.equals(trimToNull(d.getContainerNo()))) {
                return d;
            }
        }
        for (ChemicalStockDetail d : list) {
            if (d != null && "available".equalsIgnoreCase(stringVal(d.getStatus()))) {
                return d;
            }
        }
        return list.get(0);
    }

    private FilmStockDetail lockFilmDetail(Long detailId) {
        if (detailId == null || detailId <= 0) {
            return null;
        }
        LambdaQueryWrapper<FilmStockDetail> qw = new LambdaQueryWrapper<>();
        qw.eq(FilmStockDetail::getId, detailId)
            .eq(FilmStockDetail::getIsDeleted, 0)
            .last("LIMIT 1 FOR UPDATE");
        return filmStockDetailMapper.selectOne(qw);
    }

    private ChemicalStockDetail lockChemicalDetail(Long detailId) {
        if (detailId == null || detailId <= 0) {
            return null;
        }
        LambdaQueryWrapper<ChemicalStockDetail> qw = new LambdaQueryWrapper<>();
        qw.eq(ChemicalStockDetail::getId, detailId)
            .last("LIMIT 1 FOR UPDATE");
        return chemicalStockDetailMapper.selectOne(qw);
    }

    private void applyFilmLengthChange(FilmStockDetail detail,
                                       BigDecimal originalLen,
                                       BigDecimal currentLen,
                                       String operator) {
        detail.setOriginalLengthM(originalLen.setScale(3, RoundingMode.HALF_UP));
        detail.setCurrentLengthM(currentLen.setScale(3, RoundingMode.HALF_UP));
        BigDecimal area = calcFilmAreaByLength(detail.getWidth(), currentLen);
        detail.setArea(area);
        if (currentLen.compareTo(new BigDecimal("0.0001")) <= 0) {
            detail.setCurrentLengthM(ZERO);
            detail.setArea(BigDecimal.ZERO.setScale(3, RoundingMode.HALF_UP));
            detail.setStatus("used");
        } else if (!"locked".equalsIgnoreCase(stringVal(detail.getStatus()))) {
            detail.setStatus("available");
        }
        detail.setUpdateBy(trimToNull(operator) == null ? "scan" : trimToNull(operator));
        detail.setUpdateTime(new Date());
        int rows = filmStockDetailMapper.updateById(detail);
        if (rows <= 0) {
            throw new RuntimeException("更新薄膜明细失败，detailId=" + detail.getId());
        }
    }

    private void applyChemicalWeightChange(ChemicalStockDetail detail, BigDecimal newWeight) {
        detail.setWeight(newWeight.setScale(3, RoundingMode.HALF_UP));
        if (newWeight.compareTo(new BigDecimal("0.0001")) <= 0) {
            detail.setWeight(ZERO);
            detail.setStatus("used");
        } else if (!"locked".equalsIgnoreCase(stringVal(detail.getStatus()))) {
            detail.setStatus("available");
        }
        detail.setUpdateTime(new Date());
        int rows = chemicalStockDetailMapper.updateById(detail);
        if (rows <= 0) {
            throw new RuntimeException("更新化工明细失败，detailId=" + detail.getId());
        }
    }

    private void recalcFilmStock(Long stockId, String operator) {
        if (stockId == null || stockId <= 0) {
            return;
        }
        FilmStock stock = filmStockMapper.selectById(stockId);
        if (stock == null) {
            return;
        }
        LambdaQueryWrapper<FilmStockDetail> qw = new LambdaQueryWrapper<>();
        qw.eq(FilmStockDetail::getFilmStockId, stockId).eq(FilmStockDetail::getIsDeleted, 0);
        List<FilmStockDetail> details = filmStockDetailMapper.selectList(qw);

        BigDecimal totalArea = BigDecimal.ZERO;
        BigDecimal availableArea = BigDecimal.ZERO;
        BigDecimal lockedArea = BigDecimal.ZERO;
        int totalRolls = 0;
        int availableRolls = 0;
        int lockedRolls = 0;
        int totalPackCount = 0;
        int availablePackCount = 0;
        int lockedPackCount = 0;

        for (FilmStockDetail d : details) {
            BigDecimal area = nvl(d.getArea());
            totalArea = totalArea.add(area);
            if (area.compareTo(new BigDecimal("0.0001")) > 0) {
                totalRolls++;
            }
            String st = stringVal(d.getStatus());
            int packCount = d.getPackCount() == null || d.getPackCount() < 0 ? 0 : d.getPackCount();
            totalPackCount += packCount;
            if ("available".equalsIgnoreCase(st)) {
                availableArea = availableArea.add(area);
                if (area.compareTo(new BigDecimal("0.0001")) > 0) {
                    availableRolls++;
                }
                availablePackCount += packCount;
            } else if ("locked".equalsIgnoreCase(st)) {
                lockedArea = lockedArea.add(area);
                if (area.compareTo(new BigDecimal("0.0001")) > 0) {
                    lockedRolls++;
                }
                lockedPackCount += packCount;
            }
        }

        stock.setTotalArea(totalArea.setScale(3, RoundingMode.HALF_UP));
        stock.setAvailableArea(availableArea.setScale(3, RoundingMode.HALF_UP));
        stock.setLockedArea(lockedArea.setScale(3, RoundingMode.HALF_UP));
        stock.setTotalRolls(totalRolls);
        stock.setAvailableRolls(availableRolls);
        stock.setLockedRolls(lockedRolls);
        stock.setTotalPackCount(totalPackCount > 0 ? totalPackCount : totalRolls);
        stock.setAvailablePackCount(availablePackCount > 0 ? availablePackCount : availableRolls);
        stock.setLockedPackCount(lockedPackCount > 0 ? lockedPackCount : lockedRolls);

        BigDecimal safety = nvl(stock.getSafetyStock());
        if (availableArea.compareTo(BigDecimal.ZERO) <= 0) {
            stock.setStatus("out_of_stock");
        } else if (safety.compareTo(BigDecimal.ZERO) > 0 && availableArea.compareTo(safety) < 0) {
            stock.setStatus("low_stock");
        } else {
            stock.setStatus("active");
        }
        stock.setUpdateBy(trimToNull(operator) == null ? "scan" : trimToNull(operator));
        stock.setUpdateTime(new Date());
        filmStockMapper.updateById(stock);
    }

    private void recalcChemicalStock(Long stockId, String operator) {
        if (stockId == null || stockId <= 0) {
            return;
        }
        ChemicalStock stock = chemicalStockMapper.selectById(stockId);
        if (stock == null) {
            return;
        }
        LambdaQueryWrapper<ChemicalStockDetail> qw = new LambdaQueryWrapper<>();
        qw.eq(ChemicalStockDetail::getChemicalStockId, stockId);
        List<ChemicalStockDetail> details = chemicalStockDetailMapper.selectList(qw);

        int available = 0;
        int locked = 0;
        int availablePackCount = 0;
        int lockedPackCount = 0;
        for (ChemicalStockDetail d : details) {
            BigDecimal w = nvl(d.getWeight());
            if (w.compareTo(new BigDecimal("0.0001")) <= 0) {
                continue;
            }
            String st = stringVal(d.getStatus());
            int packCount = d.getPackCount() == null || d.getPackCount() < 0 ? 0 : d.getPackCount();
            if ("locked".equalsIgnoreCase(st)) {
                locked++;
                lockedPackCount += packCount > 0 ? packCount : 1;
            } else {
                available++;
                availablePackCount += packCount > 0 ? packCount : 1;
            }
        }
        int total = available + locked;
        int totalPackCount = availablePackCount + lockedPackCount;

        stock.setAvailableQuantity(available);
        stock.setLockedQuantity(locked);
        stock.setTotalQuantity(total);
        stock.setAvailablePackCount(availablePackCount);
        stock.setLockedPackCount(lockedPackCount);
        stock.setTotalPackCount(totalPackCount);
        stock.setBucketCount(total);
        Integer safety = stock.getSafetyStock() == null ? 0 : stock.getSafetyStock();
        if (available <= 0) {
            stock.setStatus("out_of_stock");
        } else if (safety > 0 && available < safety) {
            stock.setStatus("low_stock");
        } else {
            stock.setStatus("active");
        }
        stock.setUpdateBy(trimToNull(operator) == null ? "scan" : trimToNull(operator));
        stock.setUpdateTime(new Date());
        chemicalStockMapper.updateById(stock);
    }

    private MaterialScanTxn buildBaseTxn(Map<String, Object> payload,
                                         String txnType,
                                         String stockType,
                                         String idempotencyKey) {
        MaterialScanTxn txn = new MaterialScanTxn();
        txn.setTxnNo(genTxnNo());
        txn.setTxnType(txnType);
        txn.setStockType(stockType);
        txn.setOrderNo(trimToNull(stringVal(payload.get("orderNo"))));
        txn.setScheduleId(toLong(payload.get("scheduleId")));
        txn.setProcessType(trimToNull(stringVal(payload.get("processType"))));
        txn.setOperator(trimToNull(stringVal(payload.get("operator"))));
        txn.setDeviceId(trimToNull(stringVal(payload.get("deviceId"))));
        txn.setChannel(trimToNull(stringVal(payload.get("channel"))));
        txn.setIdempotencyKey(idempotencyKey);
        txn.setRemark(trimToNull(stringVal(payload.get("remark"))));
        txn.setCreateTime(new Date());
        return txn;
    }

    private Map<String, Object> buildTxnResult(MaterialScanTxn txn) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("txnId", txn.getId());
        data.put("txnNo", txn.getTxnNo());
        data.put("txnType", txn.getTxnType());
        data.put("stockType", txn.getStockType());
        data.put("detailId", txn.getDetailId());
        data.put("stockId", txn.getStockId());
        data.put("qrCode", txn.getQrCode());
        data.put("batchNo", txn.getBatchNo());
        data.put("materialCode", txn.getMaterialCode());
        data.put("qty", txn.getQty());
        data.put("unit", txn.getUnit());
        data.put("packQty", txn.getPackQty());
        data.put("packUom", txn.getPackUom());
        data.put("stdQty", txn.getStdQty());
        data.put("stdUom", txn.getStdUom());
        data.put("beforeQty", txn.getBeforeQty());
        data.put("afterQty", txn.getAfterQty());
        data.put("sourceIssueTxnId", txn.getSourceIssueTxnId());
        data.put("orderNo", txn.getOrderNo());
        data.put("scheduleId", txn.getScheduleId());
        data.put("processType", txn.getProcessType());
        data.put("createTime", txn.getCreateTime());
        return data;
    }

    private void createFilmOutRecord(FilmStockDetail detail, BigDecimal qtyLenM, MaterialScanTxn txn) {
        BigDecimal outArea = calcFilmAreaByLength(detail.getWidth(), qtyLenM);
        FilmStockOut out = new FilmStockOut();
        out.setFilmStockId(detail.getFilmStockId());
        out.setFilmDetailId(detail.getId());
        out.setMaterialCode(detail.getMaterialCode());
        out.setOutboundNo("FMOUT" + new SimpleDateFormat("yyMMddHHmmss").format(new Date()) + "-" + detail.getId());
        out.setBatchNo(detail.getBatchNo());
        out.setRollNo(detail.getRollNo());
        out.setOutArea(outArea);
        out.setScheduleId(txn.getScheduleId());
        out.setPurpose("SCAN_ISSUE");
        out.setOutboundBy(txn.getOperator());
        out.setOutboundTime(new Date());
        out.setCreateBy(txn.getOperator());
        out.setRemark("scanTxnNo=" + txn.getTxnNo() + ";unit=m;qty=" + qtyLenM);
        out.setCreateTime(new Date());
        filmStockOutMapper.insert(out);
    }

    private void createChemicalOutRecord(ChemicalStockDetail detail, BigDecimal qtyKg, MaterialScanTxn txn) {
        ChemicalStockOut out = new ChemicalStockOut();
        out.setChemicalStockId(detail.getChemicalStockId());
        out.setChemicalDetailId(detail.getId());
        out.setMaterialCode(detail.getMaterialCode());
        out.setOutboundNo("CHOUT" + new SimpleDateFormat("yyMMddHHmmss").format(new Date()) + "-" + detail.getId());
        out.setBatchNo(detail.getBatchNo());
        out.setOutWeight(qtyKg.setScale(3, RoundingMode.HALF_UP));
        out.setScheduleId(txn.getScheduleId());
        out.setPurpose("SCAN_ISSUE");
        out.setOutboundBy(txn.getOperator());
        out.setOutboundTime(new Date());
        out.setCreateBy(txn.getOperator());
        out.setRemark("scanTxnNo=" + txn.getTxnNo() + ";unit=kg;qty=" + qtyKg);
        out.setCreateTime(new Date());
        chemicalStockOutMapper.insert(out);
    }

    private void logFilmFlow(FilmStockDetail detail,
                             BigDecimal deltaLen,
                             BigDecimal beforeLen,
                             BigDecimal afterLen,
                             MaterialScanTxn txn) {
        stockFlowLogService.logStockChange(
                StockFlowLog.StockType.FILM.name(),
                detail.getFilmStockId(),
                trimToNull(detail.getRollNo()) == null ? detail.getBatchNo() : detail.getRollNo(),
                detail.getMaterialCode(),
                detail.getMaterialCode(),
                deltaLen.compareTo(BigDecimal.ZERO) >= 0 ? StockFlowLog.OperationType.IN.name() : StockFlowLog.OperationType.OUT.name(),
                deltaLen,
                "m",
                beforeLen,
                afterLen,
                txn.getTxnNo(),
                txn.getOperator(),
                "扫码" + (MaterialScanTxn.TXN_TYPE_ISSUE.equalsIgnoreCase(txn.getTxnType()) ? "领料" : "退料")
        );
    }

    private void logChemicalFlow(ChemicalStockDetail detail,
                                 BigDecimal deltaKg,
                                 BigDecimal beforeKg,
                                 BigDecimal afterKg,
                                 MaterialScanTxn txn) {
        stockFlowLogService.logStockChange(
                StockFlowLog.StockType.CHEMICAL.name(),
                detail.getChemicalStockId(),
                trimToNull(detail.getContainerNo()) == null ? detail.getBatchNo() : detail.getContainerNo(),
                detail.getMaterialCode(),
                detail.getMaterialCode(),
                deltaKg.compareTo(BigDecimal.ZERO) >= 0 ? StockFlowLog.OperationType.IN.name() : StockFlowLog.OperationType.OUT.name(),
                deltaKg,
                "kg",
                beforeKg,
                afterKg,
                txn.getTxnNo(),
                txn.getOperator(),
                "扫码" + (MaterialScanTxn.TXN_TYPE_ISSUE.equalsIgnoreCase(txn.getTxnType()) ? "领料" : "退料")
        );
    }

    private BigDecimal sumReturnedQty(Long sourceIssueTxnId) {
        LambdaQueryWrapper<MaterialScanTxn> qw = new LambdaQueryWrapper<>();
        qw.eq(MaterialScanTxn::getSourceIssueTxnId, sourceIssueTxnId)
                .eq(MaterialScanTxn::getTxnType, MaterialScanTxn.TXN_TYPE_RETURN);
        List<MaterialScanTxn> rows = materialScanTxnMapper.selectList(qw);
        BigDecimal sum = BigDecimal.ZERO;
        for (MaterialScanTxn row : rows) {
            if (row != null && row.getQty() != null) {
                sum = sum.add(row.getQty());
            }
        }
        return sum.setScale(3, RoundingMode.HALF_UP);
    }

    private Integer sumReturnedPackQty(Long sourceIssueTxnId) {
        LambdaQueryWrapper<MaterialScanTxn> qw = new LambdaQueryWrapper<>();
        qw.eq(MaterialScanTxn::getSourceIssueTxnId, sourceIssueTxnId)
                .eq(MaterialScanTxn::getTxnType, MaterialScanTxn.TXN_TYPE_RETURN);
        List<MaterialScanTxn> rows = materialScanTxnMapper.selectList(qw);
        int sum = 0;
        for (MaterialScanTxn row : rows) {
            if (row != null && row.getPackQty() != null && row.getPackQty() > 0) {
                sum += row.getPackQty();
            }
        }
        return sum;
    }

    private MaterialScanTxn findByIdempotencyKey(String key) {
        if (key == null || key.trim().isEmpty()) {
            return null;
        }
        LambdaQueryWrapper<MaterialScanTxn> qw = new LambdaQueryWrapper<>();
        qw.eq(MaterialScanTxn::getIdempotencyKey, key.trim()).orderByDesc(MaterialScanTxn::getId).last("LIMIT 1");
        return materialScanTxnMapper.selectOne(qw);
    }

    private String genTxnNo() {
        return "MSTX" + new SimpleDateFormat("yyyyMMddHHmmssSSS").format(new Date()) + (int) (Math.random() * 1000);
    }

    private BigDecimal resolveFilmCurrentLengthM(FilmStockDetail detail) {
        if (detail == null) {
            return ZERO;
        }
        if (detail.getCurrentLengthM() != null && detail.getCurrentLengthM().compareTo(BigDecimal.ZERO) > 0) {
            return detail.getCurrentLengthM().setScale(3, RoundingMode.HALF_UP);
        }
        if (detail.getArea() != null && detail.getArea().compareTo(BigDecimal.ZERO) > 0
                && detail.getWidth() != null && detail.getWidth() > 0) {
            return detail.getArea()
                    .multiply(new BigDecimal("1000"))
                    .divide(new BigDecimal(detail.getWidth()), 3, RoundingMode.HALF_UP);
        }
        return ZERO;
    }

    private BigDecimal resolveFilmOriginalLengthM(FilmStockDetail detail, BigDecimal currentLen) {
        if (detail.getOriginalLengthM() != null && detail.getOriginalLengthM().compareTo(BigDecimal.ZERO) > 0) {
            return detail.getOriginalLengthM().setScale(3, RoundingMode.HALF_UP);
        }
        return currentLen == null ? ZERO : currentLen.setScale(3, RoundingMode.HALF_UP);
    }

    private BigDecimal calcFilmAreaByLength(Integer widthMm, BigDecimal lenM) {
        if (widthMm == null || widthMm <= 0 || lenM == null || lenM.compareTo(BigDecimal.ZERO) <= 0) {
            return ZERO;
        }
        return new BigDecimal(widthMm)
                .divide(new BigDecimal("1000"), 8, RoundingMode.HALF_UP)
                .multiply(lenM)
                .setScale(3, RoundingMode.HALF_UP);
    }

    private String normalizeStockType(String stockType) {
        String s = trimToNull(stockType);
        if (s == null) {
            return null;
        }
        String upper = s.toUpperCase(Locale.ROOT);
        if (MaterialScanTxn.STOCK_TYPE_FILM.equals(upper)) {
            return MaterialScanTxn.STOCK_TYPE_FILM;
        }
        if (MaterialScanTxn.STOCK_TYPE_CHEMICAL.equals(upper)) {
            return MaterialScanTxn.STOCK_TYPE_CHEMICAL;
        }
        return upper;
    }

    private String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private String stringVal(Object value) {
        return value == null ? null : String.valueOf(value);
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
            return BigDecimal.valueOf(((Number) value).doubleValue());
        }
        try {
            String s = String.valueOf(value).trim();
            return s.isEmpty() ? null : new BigDecimal(s);
        } catch (Exception e) {
            return null;
        }
    }

    private BigDecimal nvl(BigDecimal value) {
        return value == null ? BigDecimal.ZERO.setScale(3, RoundingMode.HALF_UP) : value.setScale(3, RoundingMode.HALF_UP);
    }
}
