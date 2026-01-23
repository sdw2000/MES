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
    public List<TapeStock> getStockByMaterialFIFO(String materialCode) {
        try {
            stockMapper.normalizeAreaFields();
        } catch (Exception ignored) {
        }
        return stockMapper.selectByMaterialCodeFIFO(materialCode);
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
            
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;
                
                try {
                    TapeStock stock = new TapeStock();
                    // 基础字段
                    stock.setMaterialCode(getCellValue(row.getCell(0)));
                    stock.setProductName(getCellValue(row.getCell(1)));
                    stock.setBatchNo(getCellValue(row.getCell(2)));
                    
                    // 二维码和卷类型
                    String qrCode = getCellValue(row.getCell(3));
                    stock.setQrCode(qrCode != null && !qrCode.isEmpty() ? qrCode : stock.getBatchNo());
                    String rollType = getCellValue(row.getCell(4));
                    stock.setRollType(rollType != null && !rollType.isEmpty() ? rollType : "母卷");
                    
                    // 规格信息
                    stock.setThickness(getIntCellValue(row.getCell(5)));
                    stock.setWidth(getIntCellValue(row.getCell(6)));
                    stock.setLength(getIntCellValue(row.getCell(7)));
                    stock.setTotalRolls(getIntCellValue(row.getCell(8)));
                    stock.setLocation(getCellValue(row.getCell(9)));
                    stock.setProdYear(getIntCellValue(row.getCell(10)));
                    stock.setProdMonth(getIntCellValue(row.getCell(11)));
                    stock.setProdDay(getIntCellValue(row.getCell(12)));
                    stock.setRemark(getCellValue(row.getCell(13)));
                    
                    // 初始化长度信息
                    stock.initLength();
                    // 自动计算
                    stock.generateSpecDesc();
                    stock.generateProdDate();
                    stock.calculateTotalSqm();
                    stock.generateQrCode();
                    stock.setStatus(1);
                    
                    // 检查批次号是否已存在
                    TapeStock existing = stockMapper.selectByBatchNo(stock.getBatchNo());
                    if (existing != null) {
                        // 更新现有记录
                        existing.setTotalRolls(stock.getTotalRolls());
                        existing.setLocation(stock.getLocation());
                        existing.setQrCode(stock.getQrCode());
                        existing.setRollType(stock.getRollType());
                        existing.calculateTotalSqm();
                        stockMapper.updateById(existing);
                    } else {
                        stockMapper.insert(stock);
                    }
                    successCount++;
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
    public void approveInbound(Long id, boolean approved, String auditor, String auditRemark) {
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
            request.setStatus(TapeInboundRequest.STATUS_APPROVED);
            
            // 执行入库：检查批次是否存在
            TapeStock existingStock = stockMapper.selectByBatchNo(request.getBatchNo());
            
            if (existingStock != null) {
                // 批次已存在，累加卷数
                int beforeRolls = existingStock.getTotalRolls();
                int afterRolls = beforeRolls + request.getRolls();
                existingStock.setTotalRolls(afterRolls);
                existingStock.calculateTotalSqm();
                existingStock.setLocation(request.getLocation()); // 更新库位
                stockMapper.updateById(existingStock);
                
                // 记录流水
                saveStockLog(existingStock.getId(), request.getBatchNo(), request.getMaterialCode(),
                        request.getProductName(), TapeStockLog.TYPE_IN, request.getRolls(),
                        beforeRolls, afterRolls, request.getRequestNo(), auditor, "入库审批通过");
            } else {
                // 新批次，创建库存记录
                TapeStock stock = new TapeStock();
                stock.setMaterialCode(request.getMaterialCode());
                stock.setProductName(request.getProductName());
                stock.setBatchNo(request.getBatchNo());
                stock.setQrCode(request.getBatchNo());  // 二维码默认=批次号
                stock.setRollType("母卷");              // 默认卷类型
                stock.setThickness(request.getThickness());
                stock.setWidth(request.getWidth());
                stock.setLength(request.getLength());
                stock.setOriginalLength(request.getLength());  // 原始长度
                stock.setCurrentLength(request.getLength());   // 当前长度
                stock.setTotalRolls(request.getRolls());
                stock.setLocation(request.getLocation());
                stock.setSpecDesc(request.getSpecDesc());
                stock.setProdYear(request.getProdYear());
                stock.setProdMonth(request.getProdMonth());
                stock.setProdDay(request.getProdDay());
                stock.setProdDate(request.getProdDate());
                stock.setStatus(1);
                stock.calculateTotalSqm();
                stockMapper.insert(stock);
                
                // 记录流水
                saveStockLog(stock.getId(), stock.getBatchNo(), stock.getMaterialCode(),
                        stock.getProductName(), TapeStockLog.TYPE_IN, request.getRolls(),
                        0, request.getRolls(), request.getRequestNo(), auditor, "入库审批通过-新建批次");
            }
        } else {
            request.setStatus(TapeInboundRequest.STATUS_REJECTED);
        }
        
        inboundMapper.updateById(request);
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
        if (stock.getTotalRolls() < request.getRolls()) {
            throw new RuntimeException("库存不足，当前可用: " + stock.getTotalRolls() + " 卷");
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
        int totalAvailable = stocks.stream().mapToInt(TapeStock::getTotalRolls).sum();
        if (totalAvailable < totalRolls) {
            throw new RuntimeException("库存不足，当前可用: " + totalAvailable + " 卷，需要: " + totalRolls + " 卷");
        }
        
        // FIFO分配
        List<TapeOutboundRequest> requests = new ArrayList<>();
        int remaining = totalRolls;
        
        for (TapeStock stock : stocks) {
            if (remaining <= 0) break;
            
            int allocate = Math.min(remaining, stock.getTotalRolls());
            
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
    public void approveOutbound(Long id, boolean approved, String auditor, String auditRemark) {
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
            if (stock == null || stock.getTotalRolls() < request.getRolls()) {
                throw new RuntimeException("库存不足，无法完成出库");
            }
            
            request.setStatus(TapeOutboundRequest.STATUS_APPROVED);
            
            // 扣减库存
            int beforeRolls = stock.getTotalRolls();
            int afterRolls = beforeRolls - request.getRolls();
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
}
