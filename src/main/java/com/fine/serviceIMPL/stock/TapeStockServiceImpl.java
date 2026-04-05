package com.fine.serviceIMPL.stock;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fine.Dao.stock.*;
import com.fine.modle.stock.*;
import com.fine.service.stock.TapeStockService;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 胶带库存服务实现
 */
@Service
public class TapeStockServiceImpl implements TapeStockService {
    
    @Autowired
    private TapeStockMapper stockMapper;
    
    @Autowired
    private TapeInboundRequestMapper inboundMapper;
    
    @Autowired
    private TapeOutboundRequestMapper outboundMapper;
    
    @Autowired
    private TapeStockLogMapper logMapper;

    @Autowired
    private ScheduleMaterialLockMapper scheduleMaterialLockMapper;
    
    // ============= 库存管理 =============
      @Override
    public IPage<TapeStock> getStockPage(int page, int size, String qrCode, String materialCode, String rollType, String location) {
        LambdaQueryWrapper<TapeStock> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TapeStock::getStatus, 1);
        // 二维码/批次号查询
        if (StringUtils.hasText(qrCode)) {
            wrapper.and(w -> w.like(TapeStock::getQrCode, qrCode)
                    .or().like(TapeStock::getBatchNo, qrCode));
        }
        if (StringUtils.hasText(materialCode)) {
            wrapper.like(TapeStock::getMaterialCode, materialCode);
        }
        // 卷类型查询
        if (StringUtils.hasText(rollType)) {
            wrapper.eq(TapeStock::getRollType, rollType);
        }
        if (StringUtils.hasText(location)) {
            wrapper.eq(TapeStock::getLocation, location);
        }
        wrapper.orderByAsc(TapeStock::getProdDate);
        Page<TapeStock> pageParam = new Page<>(page, size);
        pageParam.setOptimizeCountSql(false);
        return stockMapper.selectPage(pageParam, wrapper);
    }
    
    @Override
    public List<TapeStock> getStockSummary() {
        // 先归一化面积字段，避免空值导致前端无数据
        try {
            stockMapper.normalizeAreaFields();
        } catch (Exception ignored) {
            // 忽略非关键错误，继续查询
        }
        return stockMapper.selectSummaryByMaterial();
    }

    @Override
    public IPage<TapeStock> getStockSummaryPage(int page, int size, String materialCode) {
        try {
            stockMapper.normalizeAreaFields();
        } catch (Exception ignored) {
        }
        Page<TapeStock> pageParam = new Page<>(page, size);
        pageParam.setOptimizeCountSql(false);
        long offset = (pageParam.getCurrent() - 1) * pageParam.getSize();
        List<TapeStock> records = stockMapper.selectSummaryByMaterialPageList(offset, pageParam.getSize(), materialCode);
        Long total = stockMapper.countSummaryByMaterial(materialCode);
        pageParam.setRecords(records);
        pageParam.setTotal(total != null ? total : 0);
        return pageParam;
    }
    
    @Override
    public List<TapeStock> getStockByMaterialFIFO(String materialCode) {
        try {
            stockMapper.normalizeAreaFields();
        } catch (Exception ignored) {
        }
        return stockMapper.selectByMaterialCodeFIFO(materialCode);
    }

    @Override
    public IPage<TapeStock> getStockByMaterialPage(int page, int size, String materialCode) {
        try {
            stockMapper.normalizeAreaFields();
        } catch (Exception ignored) {
        }
        Page<TapeStock> pageParam = new Page<>(page, size);
        pageParam.setOptimizeCountSql(false);
        return stockMapper.selectByMaterialCodePage(pageParam, materialCode);
    }
    
    @Override
    public TapeStock getStockById(Long id) {
        return stockMapper.selectById(id);
    }
    
    @Override
    public TapeStock getStockByBatchNo(String batchNo) {
        return stockMapper.selectByBatchNo(batchNo);
    }
    
    @Override
    public Map<String, Object> importExcel(MultipartFile file) {
        Map<String, Object> result = new HashMap<>();
        int successCount = 0;
        int failCount = 0;
        List<String> errors = new ArrayList<>();
        
        try (Workbook workbook = new XSSFWorkbook(file.getInputStream())) {
                Sheet sheet = workbook.getSheetAt(0);

                // 读取表头，支持按列名映射
                Map<String, Integer> headerIndex = new HashMap<>();
                Row headerRow = sheet.getRow(0);
                if (headerRow != null) {
                for (int c = 0; c <= headerRow.getLastCellNum(); c++) {
                    Cell cell = headerRow.getCell(c);
                    String name = getCellValue(cell);
                    if (name != null && !name.isEmpty()) {
                    headerIndex.put(name.replace("\u00A0", "").trim(), c);
                    }
                }
                }
            
                for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;
                
                try {
                    TapeStock stock = new TapeStock();
                    // 基础字段（支持表头映射）
                    stock.setMaterialCode(getCellValue(getCellByHeader(row, headerIndex,
                        new String[]{"料号", "物料编码", "产品编码", "material_code"}, 0)));
                    stock.setProductName(getCellValue(getCellByHeader(row, headerIndex,
                        new String[]{"产品名称", "物料名称", "product_name"}, 1)));
                    stock.setBatchNo(getCellValue(getCellByHeader(row, headerIndex,
                        new String[]{"生产批次号", "批次号", "batch_no"}, 2)));
                    
                    // 二维码和卷类型
                    String qrCode = getCellValue(getCellByHeader(row, headerIndex,
                        new String[]{"二维码", "QR", "qr_code"}, 3));
                    if (qrCode != null) {
                        qrCode = qrCode.trim();
                    }
                    stock.setQrCode(qrCode != null && !qrCode.isEmpty() ? qrCode : null);
                    String rollType = getCellValue(getCellByHeader(row, headerIndex,
                        new String[]{"卷类型", "卷种", "roll_type"}, 4));
                    if (rollType != null && !rollType.isEmpty()) {
                    stock.setRollType(rollType);
                    } else {
                    // 支持“是否母卷”字段
                    String isMother = getCellValue(getCellByHeader(row, headerIndex,
                        new String[]{"是否母卷", "母卷"}, -1));
                    if (StringUtils.hasText(isMother) && ("是".equals(isMother) || "Y".equalsIgnoreCase(isMother))) {
                        stock.setRollType("母卷");
                    } else if (StringUtils.hasText(isMother) && ("否".equals(isMother) || "N".equalsIgnoreCase(isMother))) {
                        stock.setRollType("复卷");
                    } else {
                        stock.setRollType("母卷");
                    }
                    }
                    
                    // 规格信息
                    stock.setThickness(getIntCellValue(getCellByHeader(row, headerIndex,
                        new String[]{"厚度", "厚度μm", "厚度(μm)", "thickness"}, 5)));
                    stock.setWidth(getIntCellValue(getCellByHeader(row, headerIndex,
                        new String[]{"宽度", "宽度mm", "宽度(mm)", "width"}, 6)));
                    stock.setLength(getIntCellValue(getCellByHeader(row, headerIndex,
                        new String[]{"长度", "长度(M)", "长度m", "length"}, 7)));
                    Integer totalRolls = getIntCellValue(getCellByHeader(row, headerIndex,
                        new String[]{"总数", "总数(卷)", "库存卷数", "rolls"}, 8));
                    stock.setTotalRolls(totalRolls);
                    stock.setLocation(getCellValue(getCellByHeader(row, headerIndex,
                        new String[]{"卡板位", "库位", "location"}, 9)));
                    stock.setProdYear(getIntCellValue(getCellByHeader(row, headerIndex,
                        new String[]{"生产年份", "生产年", "年"}, 10)));
                    stock.setProdMonth(getIntCellValue(getCellByHeader(row, headerIndex,
                        new String[]{"生产月份", "生产月", "月"}, 11)));
                    stock.setProdDay(getIntCellValue(getCellByHeader(row, headerIndex,
                        new String[]{"生产日期", "生产日", "日"}, 12)));
                    stock.setRemark(getCellValue(getCellByHeader(row, headerIndex,
                        new String[]{"原因", "备注", "remark"}, 13)));

                    // 可选：序号字段（如“数字号”）
                    Integer sequenceNo = getIntCellValue(getCellByHeader(row, headerIndex,
                        new String[]{"数字号", "序号", "sequence_no"}, -1));
                    if (sequenceNo != null && sequenceNo > 0) {
                    stock.setSequenceNo(sequenceNo);
                    }

                    // 必填校验
                    if (!StringUtils.hasText(stock.getMaterialCode())) {
                        throw new RuntimeException("料号不能为空");
                    }
                    if (!StringUtils.hasText(stock.getBatchNo())) {
                        throw new RuntimeException("生产批次号不能为空");
                    }
                    if (stock.getThickness() == null || stock.getWidth() == null || stock.getLength() == null) {
                        throw new RuntimeException("规格(厚度/宽度/长度)不能为空");
                    }
                    if (stock.getTotalRolls() == null) {
                        stock.setTotalRolls(1);
                    }
                    if (stock.getTotalRolls() <= 0) {
                        throw new RuntimeException("库存卷数必须大于0");
                    }

                    int rowsToCreate = stock.getTotalRolls();
                    for (int r = 0; r < rowsToCreate; r++) {
                        TapeStock rowStock = new TapeStock();
                        rowStock.setMaterialCode(stock.getMaterialCode());
                        rowStock.setProductName(stock.getProductName());
                        rowStock.setBatchNo(stock.getBatchNo());
                        rowStock.setRollType(stock.getRollType());
                        rowStock.setThickness(stock.getThickness());
                        rowStock.setWidth(stock.getWidth());
                        rowStock.setLength(stock.getLength());
                        rowStock.setOriginalLength(stock.getOriginalLength());
                        rowStock.setCurrentLength(stock.getCurrentLength());
                        rowStock.setTotalRolls(1);
                        rowStock.setLocation(stock.getLocation());
                        rowStock.setSpecDesc(stock.getSpecDesc());
                        rowStock.setProdYear(stock.getProdYear());
                        rowStock.setProdMonth(stock.getProdMonth());
                        rowStock.setProdDay(stock.getProdDay());
                        rowStock.setProdDate(stock.getProdDate());
                        rowStock.setRemark(stock.getRemark());
                        rowStock.setStatus(1);
                        rowStock.initLength();
                        rowStock.generateSpecDesc();
                        rowStock.generateProdDate();
                        rowStock.calculateTotalSqm();
                        rowStock.setReservedArea(BigDecimal.ZERO);
                        rowStock.setConsumedArea(BigDecimal.ZERO);
                        BigDecimal totalSqm = rowStock.getTotalSqm() != null ? rowStock.getTotalSqm() : BigDecimal.ZERO;
                        rowStock.setAvailableArea(totalSqm);

                        // 每卷一个号：优先用二维码，否则按批次号+序号生成
                        if (StringUtils.hasText(stock.getQrCode())) {
                            rowStock.setQrCode(stock.getQrCode());
                            rowStock.setSequenceNo(stock.getSequenceNo());
                        } else {
                            Integer maxSeq = stockMapper.selectMaxSequenceNoByBatchNo(stock.getBatchNo());
                            int nextSeq = (maxSeq != null ? maxSeq : 0) + 1;
                            if (stock.getSequenceNo() != null && stock.getSequenceNo() > 0) {
                                nextSeq = stock.getSequenceNo() + r;
                            }
                            rowStock.setSequenceNo(nextSeq);
                            rowStock.generateQrCode();
                        }

                        // 检查二维码是否已存在（每卷唯一）
                        TapeStock existing = stockMapper.selectByQrCode(rowStock.getQrCode());
                        if (existing != null) {
                            throw new RuntimeException("二维码已存在: " + rowStock.getQrCode());
                        }
                        stockMapper.insert(rowStock);
                        successCount++;
                    }

                    // 已拆分为单卷写入，继续处理下一行
                    continue;
                } catch (Exception e) {
                    failCount++;
                    errors.add("第" + (i + 1) + "行导入失败: " + e.getMessage());
                }
            }
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "文件解析失败: " + e.getMessage());
            return result;
        }

        // 归一化面积字段，确保可用面积正确
        try {
            stockMapper.normalizeAreaFields();
        } catch (Exception ignored) {
        }
        
        result.put("success", true);
        result.put("successCount", successCount);
        result.put("failCount", failCount);
        result.put("errors", errors);
        return result;
    }
    
    @Override
    public List<TapeStock> exportStock(String materialCode, String location) {
        LambdaQueryWrapper<TapeStock> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TapeStock::getStatus, 1);
        if (StringUtils.hasText(materialCode)) {
            wrapper.like(TapeStock::getMaterialCode, materialCode);
        }
        if (StringUtils.hasText(location)) {
            wrapper.eq(TapeStock::getLocation, location);
        }
        wrapper.orderByAsc(TapeStock::getProdDate);
        return stockMapper.selectList(wrapper);
    }
    
    // ============= 入库申请 =============
    
    @Override
    public IPage<TapeInboundRequest> getInboundPage(int page, int size, Integer status, String materialCode) {
        LambdaQueryWrapper<TapeInboundRequest> wrapper = new LambdaQueryWrapper<>();
        if (status != null) {
            wrapper.eq(TapeInboundRequest::getStatus, status);
        }
        if (StringUtils.hasText(materialCode)) {
            wrapper.like(TapeInboundRequest::getMaterialCode, materialCode);
        }
        wrapper.orderByDesc(TapeInboundRequest::getCreateTime);
        Page<TapeInboundRequest> pageParam = new Page<>(page, size);
        pageParam.setOptimizeCountSql(false);
        return inboundMapper.selectPage(pageParam, wrapper);
    }
    
    @Override
    @Transactional
    public TapeInboundRequest createInboundRequest(TapeInboundRequest request) {
        // 生成单号
        String requestNo = inboundMapper.generateRequestNo();
        if (requestNo == null) {
            requestNo = "IN" + java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd").format(LocalDateTime.now()) + "0001";
        }
        request.setRequestNo(requestNo);
        request.setApplyTime(LocalDateTime.now());
        request.setStatus(TapeInboundRequest.STATUS_PENDING);
        
        // 生成规格描述和生产日期
        if (request.getThickness() != null && request.getWidth() != null && request.getLength() != null) {
            request.setSpecDesc(request.getThickness() + "μm*" + request.getWidth() + "mm*" + request.getLength() + "m");
        }
        if (request.getProdYear() != null && request.getProdMonth() != null && request.getProdDay() != null) {
            int fullYear = request.getProdYear() < 100 ? 2000 + request.getProdYear() : request.getProdYear();
            request.setProdDate(LocalDate.of(fullYear, request.getProdMonth(), request.getProdDay()));
        }
        
        inboundMapper.insert(request);
        return request;
    }
    
    @Override
    @Transactional
    public void approveInbound(Long id, boolean approved, String auditor, String auditRemark, String scannedRollCode, String scannedLocation) {
        doApproveInbound(id, approved, auditor, auditRemark, scannedRollCode, scannedLocation);
    }

    @Override
    public Map<String, Object> approveInboundByRollCodes(List<String> rollCodes, String auditor, String auditRemark, String scannedLocation) {
        if (rollCodes == null || rollCodes.isEmpty()) {
            throw new RuntimeException("请先录入母卷号");
        }
        if (!StringUtils.hasText(scannedLocation)) {
            throw new RuntimeException("请先扫码卡板位");
        }

        List<String> normalized = rollCodes.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .distinct()
                .collect(java.util.stream.Collectors.toList());
        if (normalized.isEmpty()) {
            throw new RuntimeException("请先录入有效母卷号");
        }

        List<Map<String, Object>> failed = new ArrayList<>();
        List<String> approvedNos = new ArrayList<>();
        int successCount = 0;

        for (String rollCode : normalized) {
            try {
                LambdaQueryWrapper<TapeInboundRequest> wrapper = new LambdaQueryWrapper<>();
                wrapper.eq(TapeInboundRequest::getBatchNo, rollCode)
                        .eq(TapeInboundRequest::getStatus, TapeInboundRequest.STATUS_PENDING)
                        .orderByDesc(TapeInboundRequest::getId)
                        .last("LIMIT 1");
                TapeInboundRequest req = inboundMapper.selectOne(wrapper);
                if (req == null) {
                    Map<String, Object> fail = new HashMap<>();
                    fail.put("rollCode", rollCode);
                    fail.put("reason", "未找到待审批入库申请");
                    failed.add(fail);
                    continue;
                }

                doApproveInbound(req.getId(), true, auditor, auditRemark, rollCode, scannedLocation);
                successCount++;
                approvedNos.add(req.getRequestNo());
            } catch (Exception ex) {
                Map<String, Object> fail = new HashMap<>();
                fail.put("rollCode", rollCode);
                fail.put("reason", ex.getMessage());
                failed.add(fail);
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("total", normalized.size());
        result.put("successCount", successCount);
        result.put("failCount", normalized.size() - successCount);
        result.put("approvedRequestNos", approvedNos);
        result.put("failed", failed);
        return result;
    }

    private void doApproveInbound(Long id, boolean approved, String auditor, String auditRemark, String scannedRollCode, String scannedLocation) {
        TapeInboundRequest request = inboundMapper.selectById(id);
        if (request == null) {
            throw new RuntimeException("入库申请不存在");
        }
        if (request.getStatus() != TapeInboundRequest.STATUS_PENDING) {
            throw new RuntimeException("该申请已处理");
        }
        
        request.setAuditor(auditor);
        request.setAuditTime(LocalDateTime.now());
        request.setAuditRemark(auditRemark);
        
        if (approved) {
            String expectedRollCode = request.getBatchNo() == null ? "" : request.getBatchNo().trim();
            String scannedCode = scannedRollCode == null ? "" : scannedRollCode.trim();
            if (!StringUtils.hasText(scannedCode)) {
                throw new RuntimeException("请先扫码母卷号进行确认");
            }
            if (!expectedRollCode.equalsIgnoreCase(scannedCode)) {
                throw new RuntimeException("扫码母卷号与申请批次不一致，禁止入库");
            }

            if (StringUtils.hasText(scannedLocation)) {
                request.setLocation(scannedLocation.trim());
            }
            if (!StringUtils.hasText(request.getLocation())) {
                throw new RuntimeException("请扫码卡板位后再审批通过");
            }

            request.setStatus(TapeInboundRequest.STATUS_APPROVED);

            int rolls = request.getRolls() != null ? request.getRolls() : 0;
            if (rolls <= 0) {
                throw new RuntimeException("入库卷数必须大于0");
            }

            // 按“每卷一条”写入库存，批次号+序号生成二维码
            Integer maxSeq = stockMapper.selectMaxSequenceNoByBatchNo(request.getBatchNo());
            int seq = maxSeq != null ? maxSeq : 0;
            for (int i = 0; i < rolls; i++) {
                seq += 1;
                TapeStock stock = new TapeStock();
                stock.setMaterialCode(request.getMaterialCode());
                stock.setProductName(request.getProductName());
                stock.setBatchNo(buildInboundRollBatchNo(request.getBatchNo(), request.getRequestNo(), i + 1));
                stock.setSequenceNo(seq);
                stock.setRollType("母卷");
                stock.setThickness(request.getThickness());
                stock.setWidth(request.getWidth());
                stock.setLength(request.getLength());
                stock.setOriginalLength(request.getLength());
                stock.setCurrentLength(request.getLength());
                stock.setTotalRolls(1);
                stock.setLocation(request.getLocation());
                stock.setSpecDesc(request.getSpecDesc());
                stock.setProdYear(request.getProdYear());
                stock.setProdMonth(request.getProdMonth());
                stock.setProdDay(request.getProdDay());
                stock.setProdDate(request.getProdDate());
                stock.setStatus(1);
                stock.initLength();
                stock.generateSpecDesc();
                stock.generateProdDate();
                stock.calculateTotalSqm();
                stock.setReservedArea(BigDecimal.ZERO);
                stock.setConsumedArea(BigDecimal.ZERO);
                BigDecimal totalSqm = stock.getTotalSqm() != null ? stock.getTotalSqm() : BigDecimal.ZERO;
                stock.setAvailableArea(totalSqm);
                stock.generateQrCode();
                stockMapper.insert(stock);

                // 记录流水（每卷）
                saveStockLog(stock.getId(), stock.getBatchNo(), stock.getMaterialCode(),
                        stock.getProductName(), TapeStockLog.TYPE_IN, 1,
                        0, 1, request.getRequestNo(), auditor, "入库审批通过-单卷入库");

                // 入库后自动回填待补锁（仅预留，不做实际消耗）
                autoFulfillPendingLocks(stock, request.getRequestNo(), auditor);
            }
        } else {
            request.setStatus(TapeInboundRequest.STATUS_REJECTED);
        }
        
        inboundMapper.updateById(request);
    }

    private void autoFulfillPendingLocks(TapeStock stock, String sourceDocNo, String operator) {
        if (stock == null || !StringUtils.hasText(stock.getMaterialCode())) {
            return;
        }
        BigDecimal remain = stock.getTotalSqm() == null ? BigDecimal.ZERO : stock.getTotalSqm();
        if (remain.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }

        while (remain.compareTo(BigDecimal.ZERO) > 0) {
            LambdaQueryWrapper<ScheduleMaterialLock> pendingQ = new LambdaQueryWrapper<>();
            pendingQ.eq(ScheduleMaterialLock::getLockStatus, ScheduleMaterialLock.LockStatus.PENDING_SUPPLY)
                    .eq(ScheduleMaterialLock::getMaterialCode, stock.getMaterialCode())
                    .orderByAsc(ScheduleMaterialLock::getLockedTime)
                    .orderByAsc(ScheduleMaterialLock::getId)
                    .last("LIMIT 1");
            ScheduleMaterialLock pending = scheduleMaterialLockMapper.selectOne(pendingQ);
            if (pending == null) {
                break;
            }

            BigDecimal required = pending.getRequiredArea() == null ? BigDecimal.ZERO : pending.getRequiredArea();
            BigDecimal already = pending.getLockedArea() == null ? BigDecimal.ZERO : pending.getLockedArea();
            BigDecimal need = required.subtract(already);
            if (need.compareTo(BigDecimal.ZERO) <= 0) {
                pending.setLockStatus(ScheduleMaterialLock.LockStatus.FULFILLED);
                pending.setReleasedTime(LocalDateTime.now());
                scheduleMaterialLockMapper.updateById(pending);
                continue;
            }

            BigDecimal lockArea = remain.min(need).setScale(2, BigDecimal.ROUND_HALF_UP);
            if (lockArea.compareTo(BigDecimal.ZERO) <= 0) {
                break;
            }

            boolean reserved = false;
            for (int i = 0; i < 3; i++) {
                TapeStock current = stockMapper.selectById(stock.getId());
                if (current == null) {
                    break;
                }
                BigDecimal available = current.getAvailableArea() == null ? BigDecimal.ZERO : current.getAvailableArea();
                if (available.compareTo(lockArea) < 0) {
                    lockArea = available.setScale(2, BigDecimal.ROUND_HALF_UP);
                }
                if (lockArea.compareTo(BigDecimal.ZERO) <= 0) {
                    break;
                }
                Integer version = current.getVersion() == null ? 0 : current.getVersion();
                int ok = stockMapper.updateReservedAreaWithVersion(current.getId(), lockArea, version);
                if (ok > 0) {
                    reserved = true;
                    break;
                }
            }
            if (!reserved) {
                break;
            }

            ScheduleMaterialLock lock = new ScheduleMaterialLock();
            lock.setScheduleId(pending.getScheduleId());
            lock.setOrderId(pending.getOrderId());
            lock.setOrderNo(pending.getOrderNo());
            lock.setMaterialCode(pending.getMaterialCode());
            lock.setFilmStockId(stock.getId());
            lock.setFilmStockDetailId(stock.getId());
            lock.setRollCode(StringUtils.hasText(stock.getQrCode()) ? stock.getQrCode() : stock.getBatchNo());
            lock.setLockedArea(lockArea);
            lock.setRequiredArea(lockArea);
            lock.setLockStatus(ScheduleMaterialLock.LockStatus.LOCKED);
            lock.setLockedTime(LocalDateTime.now());
            lock.setLockedByUserId(1L);
            lock.setVersion(1);
                lock.setRemark("source=inbound-auto-lock;pendingId=" + pending.getId()
                    + ";sourceDoc=" + (sourceDocNo == null ? "" : sourceDocNo)
                    + ";op=" + (operator == null ? "system" : operator)
                    + ";ts=" + LocalDateTime.now());
            scheduleMaterialLockMapper.insert(lock);

            pending.setLockedArea(already.add(lockArea).setScale(2, BigDecimal.ROUND_HALF_UP));
            if (pending.getLockedArea().compareTo(required) >= 0) {
                pending.setLockStatus(ScheduleMaterialLock.LockStatus.FULFILLED);
                pending.setReleasedTime(LocalDateTime.now());
            }
            scheduleMaterialLockMapper.updateById(pending);

            remain = remain.subtract(lockArea);
        }
    }
    
    @Override
    @Transactional
    public void cancelInbound(Long id) {
        TapeInboundRequest request = inboundMapper.selectById(id);
        if (request == null) {
            throw new RuntimeException("入库申请不存在");
        }
        if (request.getStatus() != TapeInboundRequest.STATUS_PENDING) {
            throw new RuntimeException("只能取消待审批的申请");
        }
        request.setStatus(TapeInboundRequest.STATUS_CANCELLED);
        inboundMapper.updateById(request);
    }
    
    @Override
    public int countPendingInbound() {
        return inboundMapper.countPending();
    }
    
    // ============= 出库申请 =============
    
    @Override
    public IPage<TapeOutboundRequest> getOutboundPage(int page, int size, Integer status, String materialCode) {
        LambdaQueryWrapper<TapeOutboundRequest> wrapper = new LambdaQueryWrapper<>();
        if (status != null) {
            wrapper.eq(TapeOutboundRequest::getStatus, status);
        }
        if (StringUtils.hasText(materialCode)) {
            wrapper.like(TapeOutboundRequest::getMaterialCode, materialCode);
        }
        wrapper.orderByDesc(TapeOutboundRequest::getCreateTime);
        Page<TapeOutboundRequest> pageParam = new Page<>(page, size);
        pageParam.setOptimizeCountSql(false);
        return outboundMapper.selectPage(pageParam, wrapper);
    }
    
    @Override
    @Transactional
    public TapeOutboundRequest createOutboundRequest(TapeOutboundRequest request) {
        // 检查库存
        TapeStock stock = stockMapper.selectById(request.getStockId());
        if (stock == null) {
            throw new RuntimeException("库存记录不存在");
        }
        if (request.getRolls() == null || request.getRolls() <= 0) {
            request.setRolls(1);
        }
        if (!Objects.equals(request.getRolls(), 1)) {
            throw new RuntimeException("每条库存仅代表1卷，出库卷数只能为1");
        }
        if (stock.getTotalRolls() == null || stock.getTotalRolls() < 1) {
            throw new RuntimeException("库存不足，当前可用: 0 卷");
        }
        
        // 生成单号
        String requestNo = outboundMapper.generateRequestNo();
        if (requestNo == null) {
            requestNo = "OUT" + java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd").format(LocalDateTime.now()) + "0001";
        }
        request.setRequestNo(requestNo);
        request.setMaterialCode(stock.getMaterialCode());
        request.setProductName(stock.getProductName());
        request.setBatchNo(stock.getBatchNo());
        request.setSpecDesc(stock.getSpecDesc());
        request.setAvailableRolls(stock.getTotalRolls());
        request.setApplyTime(LocalDateTime.now());
        request.setStatus(TapeOutboundRequest.STATUS_PENDING);
        
        outboundMapper.insert(request);
        return request;
    }
    
    @Override
    @Transactional
    public List<TapeOutboundRequest> createOutboundRequestFIFO(String materialCode, int totalRolls,
                                                                String applicant, String applyDept, String remark) {
        List<TapeStock> stocks = stockMapper.selectByMaterialCodeFIFO(materialCode);
        if (stocks.isEmpty()) {
            throw new RuntimeException("该料号无可用库存");
        }
        
        // 计算总可用
        int totalAvailable = (int) stocks.stream().filter(s -> s.getTotalRolls() != null && s.getTotalRolls() > 0).count();
        if (totalAvailable < totalRolls) {
            throw new RuntimeException("库存不足，当前可用: " + totalAvailable + " 卷，需要: " + totalRolls + " 卷");
        }
        
        // FIFO分配
        List<TapeOutboundRequest> requests = new ArrayList<>();
        int remaining = totalRolls;
        
        for (TapeStock stock : stocks) {
            if (remaining <= 0) break;

            if (stock.getTotalRolls() == null || stock.getTotalRolls() <= 0) {
                continue;
            }

            int allocate = 1;
            TapeOutboundRequest request = new TapeOutboundRequest();
            request.setStockId(stock.getId());
            request.setRolls(allocate);
            request.setApplicant(applicant);
            request.setApplyDept(applyDept);
            request.setRemark(remark);
            
            requests.add(createOutboundRequest(request));
            remaining -= allocate;
        }
        
        return requests;
    }
    
    @Override
    @Transactional
    public void approveOutbound(Long id, boolean approved, String auditor, String auditRemark, String scannedRollCode) {
        TapeOutboundRequest request = outboundMapper.selectById(id);
        if (request == null) {
            throw new RuntimeException("出库申请不存在");
        }
        if (request.getStatus() != TapeOutboundRequest.STATUS_PENDING) {
            throw new RuntimeException("该申请已处理");
        }
        
        request.setAuditor(auditor);
        request.setAuditTime(LocalDateTime.now());
        request.setAuditRemark(auditRemark);
        
        if (approved) {
            // 再次检查库存
            TapeStock stock = stockMapper.selectById(request.getStockId());
            if (stock == null || stock.getTotalRolls() == null || stock.getTotalRolls() < 1) {
                throw new RuntimeException("库存不足，无法完成出库");
            }
            String scannedCode = scannedRollCode == null ? "" : scannedRollCode.trim();
            if (!StringUtils.hasText(scannedCode)) {
                throw new RuntimeException("请先扫码卷号进行出库");
            }
            String expectQr = stock.getQrCode() == null ? "" : stock.getQrCode().trim();
            String expectBatch = stock.getBatchNo() == null ? "" : stock.getBatchNo().trim();
            boolean matched = (!expectQr.isEmpty() && expectQr.equalsIgnoreCase(scannedCode))
                    || (!expectBatch.isEmpty() && expectBatch.equalsIgnoreCase(scannedCode));
            if (!matched) {
                throw new RuntimeException("扫码卷号与库存批次/二维码不一致，禁止出库");
            }
            if (request.getRolls() == null || request.getRolls() <= 0) {
                request.setRolls(1);
            }
            if (!Objects.equals(request.getRolls(), 1)) {
                throw new RuntimeException("每条库存仅代表1卷，出库卷数只能为1");
            }
            
            request.setStatus(TapeOutboundRequest.STATUS_APPROVED);
            
            // 扣减库存
            int beforeRolls = stock.getTotalRolls();
            int afterRolls = 0;
            stock.setTotalRolls(afterRolls);
            stock.calculateTotalSqm();
            if (afterRolls == 0) {
                stock.setStatus(0); // 标记为已清空
            }
            stockMapper.updateById(stock);
            
            // 记录流水
            saveStockLog(stock.getId(), stock.getBatchNo(), stock.getMaterialCode(),
                    stock.getProductName(), TapeStockLog.TYPE_OUT, -request.getRolls(),
                    beforeRolls, afterRolls, request.getRequestNo(), auditor, "出库审批通过");
        } else {
            request.setStatus(TapeOutboundRequest.STATUS_REJECTED);
        }
        
        outboundMapper.updateById(request);
    }

    @Override
    @Transactional
    public Map<String, Object> approveOutboundByRollCodes(List<String> rollCodes, String auditor, String auditRemark) {
        List<String> normalized = rollCodes == null ? new ArrayList<>() : rollCodes.stream()
                .filter(Objects::nonNull)
                .map(Object::toString)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .distinct()
                .collect(java.util.stream.Collectors.toList());
        if (normalized.isEmpty()) {
            throw new RuntimeException("请先录入有效卷号");
        }

        List<Map<String, Object>> failed = new ArrayList<>();
        List<String> approvedNos = new ArrayList<>();
        int successCount = 0;

        for (String rollCode : normalized) {
            try {
                LambdaQueryWrapper<TapeStock> stockWrapper = new LambdaQueryWrapper<>();
                stockWrapper.eq(TapeStock::getStatus, 1)
                        .and(w -> w.eq(TapeStock::getQrCode, rollCode)
                                .or().eq(TapeStock::getBatchNo, rollCode))
                        .orderByDesc(TapeStock::getId)
                        .last("LIMIT 1");
                TapeStock stock = stockMapper.selectOne(stockWrapper);
                if (stock == null) {
                    Map<String, Object> fail = new HashMap<>();
                    fail.put("rollCode", rollCode);
                    fail.put("reason", "未找到对应库存");
                    failed.add(fail);
                    continue;
                }

                LambdaQueryWrapper<TapeOutboundRequest> wrapper = new LambdaQueryWrapper<>();
                wrapper.eq(TapeOutboundRequest::getStockId, stock.getId())
                        .eq(TapeOutboundRequest::getStatus, TapeOutboundRequest.STATUS_PENDING)
                        .orderByDesc(TapeOutboundRequest::getId)
                        .last("LIMIT 1");
                TapeOutboundRequest req = outboundMapper.selectOne(wrapper);
                if (req == null) {
                    Map<String, Object> fail = new HashMap<>();
                    fail.put("rollCode", rollCode);
                    fail.put("reason", "未找到待审批出库申请");
                    failed.add(fail);
                    continue;
                }

                approveOutbound(req.getId(), true, auditor, auditRemark, rollCode);
                successCount++;
                approvedNos.add(req.getRequestNo());
            } catch (Exception ex) {
                Map<String, Object> fail = new HashMap<>();
                fail.put("rollCode", rollCode);
                fail.put("reason", ex.getMessage());
                failed.add(fail);
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("total", normalized.size());
        result.put("successCount", successCount);
        result.put("failCount", normalized.size() - successCount);
        result.put("approvedRequestNos", approvedNos);
        result.put("failed", failed);
        return result;
    }
    
    @Override
    @Transactional
    public void cancelOutbound(Long id) {
        TapeOutboundRequest request = outboundMapper.selectById(id);
        if (request == null) {
            throw new RuntimeException("出库申请不存在");
        }
        if (request.getStatus() != TapeOutboundRequest.STATUS_PENDING) {
            throw new RuntimeException("只能取消待审批的申请");
        }
        request.setStatus(TapeOutboundRequest.STATUS_CANCELLED);
        outboundMapper.updateById(request);
    }
    
    @Override
    public int countPendingOutbound() {
        return outboundMapper.countPending();
    }
    
    // ============= 库存流水 =============
    
    @Override
    public IPage<TapeStockLog> getStockLogPage(int page, int size, String type, String materialCode, String batchNo) {
        LambdaQueryWrapper<TapeStockLog> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(type)) {
            wrapper.eq(TapeStockLog::getType, type);
        }
        if (StringUtils.hasText(materialCode)) {
            wrapper.like(TapeStockLog::getMaterialCode, materialCode);
        }
        if (StringUtils.hasText(batchNo)) {
            wrapper.like(TapeStockLog::getBatchNo, batchNo);
        }
        wrapper.orderByDesc(TapeStockLog::getCreateTime);
        Page<TapeStockLog> pageParam = new Page<>(page, size);
        pageParam.setOptimizeCountSql(false); // 禁用COUNT优化，避免生成错误的SQL
        return logMapper.selectPage(pageParam, wrapper);
    }
    
    @Override
    public List<TapeStockLog> exportStockLog(String type, String materialCode, String startDate, String endDate) {
        LambdaQueryWrapper<TapeStockLog> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(type)) {
            wrapper.eq(TapeStockLog::getType, type);
        }
        if (StringUtils.hasText(materialCode)) {
            wrapper.like(TapeStockLog::getMaterialCode, materialCode);
        }
        if (StringUtils.hasText(startDate)) {
            wrapper.ge(TapeStockLog::getCreateTime, LocalDate.parse(startDate).atStartOfDay());
        }
        if (StringUtils.hasText(endDate)) {
            wrapper.le(TapeStockLog::getCreateTime, LocalDate.parse(endDate).plusDays(1).atStartOfDay());
        }
        wrapper.orderByDesc(TapeStockLog::getCreateTime);
        return logMapper.selectList(wrapper);
    }
    
    // ============= 私有方法 =============
    
    private void saveStockLog(Long stockId, String batchNo, String materialCode, String productName,
                              String type, int changeRolls, int beforeRolls, int afterRolls,
                              String refNo, String operator, String remark) {
        TapeStockLog log = new TapeStockLog();
        log.setStockId(stockId);
        log.setBatchNo(batchNo);
        log.setMaterialCode(materialCode);
        log.setProductName(productName);
        log.setType(type);
        log.setChangeRolls(changeRolls);
        log.setBeforeRolls(beforeRolls);
        log.setAfterRolls(afterRolls);
        log.setRefNo(refNo);
        log.setOperator(operator);
        log.setRemark(remark);
        logMapper.insert(log);
    }

    /**
     * 入库审批时按“每卷一条”生成唯一批次号，避免触发 uk_batch_no 唯一约束。
     */
    private String buildInboundRollBatchNo(String batchNo, String requestNo, int rollIndex) {
        String base = StringUtils.hasText(batchNo) ? batchNo.trim() : (StringUtils.hasText(requestNo) ? requestNo.trim() : "INBOUND");
        if (rollIndex <= 1 && stockMapper.selectByBatchNo(base) == null) {
            return base;
        }
        String candidate = base + "-" + String.format("%03d", Math.max(rollIndex, 1));
        int retry = 1;
        while (stockMapper.selectByBatchNo(candidate) != null) {
            candidate = base + "-" + String.format("%03d", Math.max(rollIndex, 1)) + "-" + String.format("%02d", retry);
            retry++;
            if (retry > 99) {
                candidate = base + "-" + System.currentTimeMillis();
                break;
            }
        }
        return candidate;
    }
    
    private String getCellValue(Cell cell) {
        if (cell == null) return null;
        cell.setCellType(CellType.STRING);
        return cell.getStringCellValue().trim();
    }
    
    private Integer getIntCellValue(Cell cell) {
        if (cell == null) return null;
        try {
            if (cell.getCellType() == CellType.NUMERIC) {
                return (int) cell.getNumericCellValue();
            }
            cell.setCellType(CellType.STRING);
            String value = cell.getStringCellValue().trim();
            return StringUtils.hasText(value) ? Integer.parseInt(value) : null;
        } catch (Exception e) {
            return null;
        }
    }

    private Cell getCellByHeader(Row row, Map<String, Integer> headerIndex, String[] headerNames, int fallbackIndex) {
        if (row == null) return null;
        if (headerIndex != null && headerNames != null) {
            for (String name : headerNames) {
                if (!StringUtils.hasText(name)) continue;
                Integer idx = headerIndex.get(name);
                if (idx != null && idx >= 0) {
                    return row.getCell(idx);
                }
            }
        }
        if (fallbackIndex >= 0) {
            return row.getCell(fallbackIndex);
        }
        return null;
    }
}
