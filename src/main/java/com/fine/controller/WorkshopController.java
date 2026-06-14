package com.fine.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fine.Utils.ResponseResult;
import com.fine.modle.stock.TapeStock;
import com.fine.modle.stock.TapeStockLog;
import com.fine.Dao.stock.TapeStockMapper;
import com.fine.Dao.stock.TapeStockLogMapper;
import com.fine.service.stock.TapeStockService;
import com.fine.Dao.stock.FilmStockDetailMapper;
import com.fine.model.stock.FilmStockDetail;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.stream.Collectors;

/**
 * 车间现场物料管理与损耗统计
 */
@RestController
@RequestMapping("/api/workshop")
@PreAuthorize("hasAnyAuthority('admin','production','warehouse','packing','Coating','slitting','rewinding','Quality')")
public class WorkshopController {

    @Autowired
    private TapeStockMapper tapeStockMapper;

    @Autowired
    private TapeStockLogMapper tapeStockLogMapper;

    @Autowired
    private TapeStockService tapeStockService;

    @Autowired
    private FilmStockDetailMapper filmStockDetailMapper;

    /**
     * 获取车间现场物料列表 (增强版)
     */
    @GetMapping("/stock")
    public ResponseResult<Map<String, Object>> getWorkshopStock(@RequestParam(required = false) String workshopSection) {
        Map<String, Object> result = new HashMap<>();

        // 1. 获取胶带库存 (通用)
        QueryWrapper<TapeStock> tapeQuery = new QueryWrapper<>();
        tapeQuery.eq("status", 1)
                 .and(i -> i.like("location", "车间").or().eq("workshop_status", "CONSUMING"));
        
        if (workshopSection != null && !workshopSection.isEmpty()) {
            tapeQuery.eq("workshop_section", workshopSection);
        }
        
        List<TapeStock> tapes = tapeStockMapper.selectList(tapeQuery);
        result.put("tapes", tapes);

        // 2. 如果是涂布车间，获取薄膜原材料库存
        if (workshopSection == null || "涂布".equals(workshopSection)) {
            QueryWrapper<FilmStockDetail> filmQuery = new QueryWrapper<>();
            filmQuery.eq("qc_status", "qualified")
                     .like("location", "车间");
            List<FilmStockDetail> films = filmStockDetailMapper.selectList(filmQuery);
            result.put("films", films);
        } else {
            result.put("films", null);
        }

        return ResponseResult.success(result);
    }

    /**
     * 领料到现场 (状态变更)
     */
    @PostMapping("/receive")
    @Transactional
    public ResponseResult<?> receiveToFloor(@RequestBody Map<String, String> params) {
        String qrCode = params.get("qrCode");
        String targetLocation = params.get("location"); // 如: 涂布车间, 分切车间
        
        TapeStock stock = tapeStockMapper.selectOne(new QueryWrapper<TapeStock>().eq("qr_code", qrCode).eq("status", 1));
        if (stock == null) return ResponseResult.error(404, "未找到该物料: " + qrCode);

        String oldLocation = stock.getLocation();
        stock.setLocation(targetLocation);
        stock.setWorkshopStatus("NORMAL");
        tapeStockMapper.updateById(stock);

        // 记日志
        TapeStockLog log = new TapeStockLog();
        log.setStockId(stock.getId());
        log.setBatchNo(stock.getBatchNo());
        log.setMaterialCode(stock.getMaterialCode());
        log.setProductName(stock.getProductName());
        log.setType("MOVE");
        log.setRemark("从 [" + oldLocation + "] 领用至 [" + targetLocation + "]");
        tapeStockLogMapper.insert(log);

        return ResponseResult.success("领用成功");
    }

    /**
     * 批量领用入场 (支持小程序批次扫码)
     */
    @PostMapping("/batch-receive")
    @Transactional
    public ResponseResult<?> batchReceiveToFloor(@RequestBody Map<String, Object> params) {
        List<String> qrCodes = (List<String>) params.get("qrCodes");
        if (qrCodes == null) qrCodes = (List<String>) params.get("barcodes"); // 兼容旧版或前端参数名

        String targetSection = params.getOrDefault("workshopSection", "现场").toString();
        String shiftCode = params.getOrDefault("shiftCode", "").toString();
        String operator = params.getOrDefault("operator", "system").toString();

        if (qrCodes == null || qrCodes.isEmpty()) return ResponseResult.error("请提供扫码物料列表");

        int successCount = 0;
        for (String qrCode : qrCodes) {
            TapeStock stock = tapeStockMapper.selectOne(new QueryWrapper<TapeStock>().eq("qr_code", qrCode).eq("status", 1));
            if (stock != null) {
                String oldLocation = stock.getLocation();
                stock.setLocation(targetSection.contains("车间") ? targetSection : targetSection + "车间");
                stock.setWorkshopSection(targetSection);
                stock.setShiftCode(shiftCode);
                stock.setWorkshopStatus("NORMAL");
                tapeStockMapper.updateById(stock);

                TapeStockLog log = new TapeStockLog();
                log.setStockId(stock.getId());
                log.setBatchNo(stock.getBatchNo());
                log.setMaterialCode(stock.getMaterialCode());
                log.setProductName(stock.getProductName());
                log.setType("MOVE");
                log.setOperator(operator);
                log.setWorkshopSection(targetSection);
                log.setShiftCode(shiftCode);
                log.setRemark("批量领用: 从 [" + oldLocation + "] 领用至 [" + targetSection + "], 班组: [" + shiftCode + "]");
                tapeStockLogMapper.insert(log);
                successCount++;
            }
        }
        return ResponseResult.success("批量领用成功，共计: " + successCount + " 卷");
    }

    /**
     * 规格修正及损耗计算
     */
    @PostMapping("/adjust")
    @Transactional
    public ResponseResult<?> adjustSpec(@RequestBody Map<String, Object> params) {
        Long id = Long.valueOf(params.get("id").toString());
        Integer newWidth = (Integer) params.get("width");
        Integer newLength = (Integer) params.get("length");
        String reason = (String) params.get("reason");
        String operator = (String) params.get("operator");

        TapeStock stock = tapeStockMapper.selectById(id);
        if (stock == null) return ResponseResult.error(404, "库存不存在");

        BigDecimal oldArea = stock.getTotalSqm();
        String oldSpec = stock.getWidth() + "mm * " + (stock.getCurrentLength() != null ? stock.getCurrentLength() : stock.getLength()) + "m";
        
        // 更新规格
        stock.setWidth(newWidth);
        stock.setCurrentLength(newLength);
        stock.setLength(newLength);
        
        // 面积计算: (W/1000) * L * Rolls
        BigDecimal newArea = new BigDecimal(newWidth)
                .divide(new BigDecimal(1000))
                .multiply(new BigDecimal(newLength))
                .multiply(stock.getTotalRolls() != null ? new BigDecimal(stock.getTotalRolls()) : BigDecimal.ONE)
                .setScale(4, RoundingMode.HALF_UP);
        
        BigDecimal lossArea = oldArea.subtract(newArea);
        
        stock.setTotalSqm(newArea);
        stock.setAvailableArea(newArea); // 简单处理，同步可用面积
        
        // 根据规格是否变小自动设置状态
        if (lossArea.compareTo(BigDecimal.ZERO) > 0) {
            stock.setWorkshopStatus("REMAINING"); // 有损耗，视为余料
        } else {
            stock.setWorkshopStatus("NORMAL");
        }
        
        tapeStockMapper.updateById(stock);

        // 记日志统计损耗
        TapeStockLog log = new TapeStockLog();
        log.setStockId(stock.getId());
        log.setBatchNo(stock.getBatchNo());
        log.setMaterialCode(stock.getMaterialCode());
        log.setType("ADJUST");
        log.setBeforeSpec(oldSpec);
        log.setAfterSpec(newWidth + "mm * " + newLength + "m");
        log.setChangeArea(lossArea.negate());
        log.setLossArea(lossArea.compareTo(BigDecimal.ZERO) > 0 ? lossArea : BigDecimal.ZERO);
        log.setWorkshopSection(stock.getWorkshopSection());
        log.setShiftCode(stock.getShiftCode()); // 记录班组
        
        // 映射前端简单选项到标准分类
        String standardReason = mapToStandardReason(reason);
        log.setLossReason(standardReason);
        
        log.setOperator(operator);
        log.setRemark("现场规格修正 [" + reason + "]，损耗: " + lossArea + "㎡");
        tapeStockLogMapper.insert(log);

        Map<String, Object> result = new HashMap<>();
        result.put("lossArea", lossArea);
        result.put("currentArea", newArea);
        result.put("standardReason", standardReason);
        return ResponseResult.success(result);
    }

    /**
     * 车间退料：将现场余料退回主仓库
     */
    @PostMapping("/return")
    @Transactional
    public ResponseResult<?> returnToWarehouse(@RequestBody Map<String, Object> params) {
        Long id = Long.valueOf(params.get("id").toString());
        String targetLocation = params.getOrDefault("targetLocation", "成品仓").toString();
        String operator = params.getOrDefault("operator", "system").toString();
        String remark = params.getOrDefault("remark", "车间退料").toString();

        TapeStock stock = tapeStockMapper.selectById(id);
        if (stock == null) return ResponseResult.error("物料不存在");

        String oldLocation = stock.getLocation();
        String oldSection = stock.getWorkshopSection();

        stock.setLocation(targetLocation);
        stock.setWorkshopStatus(null);
        stock.setWorkshopSection(null);
        tapeStockMapper.updateById(stock);

        // 记流水
        TapeStockLog log = new TapeStockLog();
        log.setStockId(stock.getId());
        log.setBatchNo(stock.getBatchNo());
        log.setMaterialCode(stock.getMaterialCode());
        log.setType("IN"); // 退料入库
        log.setOperator(operator);
        log.setRemark("车间退料: 从 [" + oldSection + " - " + oldLocation + "] 退回 [" + targetLocation + "]。附言: " + remark);
        tapeStockLogMapper.insert(log);

        return ResponseResult.success("退料成功");
    }

    /**
     * 批量退库 (支持小程序/PDA批次扫码退料)
     */
    @PostMapping("/batch-return")
    @Transactional
    public ResponseResult<?> batchReturnToWarehouse(@RequestBody Map<String, Object> params) {
        List<String> qrCodes = (List<String>) params.get("qrCodes");
        if (qrCodes == null) qrCodes = (List<String>) params.get("barcodes");

        String targetLocation = params.getOrDefault("targetLocation", "成品仓").toString();
        if (params.containsKey("location")) targetLocation = params.get("location").toString();

        String operator = params.getOrDefault("operator", "system").toString();
        String remarkPrefix = params.getOrDefault("remark", "批量退料").toString();

        if (qrCodes == null || qrCodes.isEmpty()) return ResponseResult.error("请提供扫码物料列表");

        int successCount = 0;
        for (String qrCode : qrCodes) {
            TapeStock stock = tapeStockMapper.selectOne(new QueryWrapper<TapeStock>().eq("qr_code", qrCode).eq("status", 1));
            if (stock != null) {
                String oldSection = stock.getWorkshopSection();
                stock.setLocation(targetLocation);
                stock.setWorkshopStatus(null);
                stock.setWorkshopSection(null);
                stock.setShiftCode(null); // 退库后清除班组信息
                tapeStockMapper.updateById(stock);

                TapeStockLog log = new TapeStockLog();
                log.setStockId(stock.getId());
                log.setBatchNo(stock.getBatchNo());
                log.setMaterialCode(stock.getMaterialCode());
                log.setType("IN"); // 退料入库
                log.setOperator(operator);
                log.setRemark(remarkPrefix + ": 从 [" + (oldSection != null ? oldSection : "现场") + "] 退回 [" + targetLocation + "]");
                tapeStockLogMapper.insert(log);
                successCount++;
            }
        }
        return ResponseResult.success("批量退料成功，共计: " + successCount + " 卷");
    }

    /**
     * 班组/工段交接：物料在涂布、分切、包装等环节流转，或班次间切换
     */
    @PostMapping("/transfer")
    @Transactional
    public ResponseResult<?> transferSection(@RequestBody Map<String, Object> params) {
        Long id = Long.valueOf(params.get("id").toString());
        String targetSection = params.get("targetSection") != null ? params.get("targetSection").toString() : null;
        String targetShift = params.get("targetShift") != null ? params.get("targetShift").toString() : null;
        String operator = params.getOrDefault("operator", "system").toString();
        String remark = params.getOrDefault("remark", "工段交接").toString();

        TapeStock stock = tapeStockMapper.selectById(id);
        if (stock == null) return ResponseResult.error("物料不存在");

        String oldSection = stock.getWorkshopSection();
        String oldShift = stock.getShiftCode();
        
        if (targetSection != null) stock.setWorkshopSection(targetSection);
        if (targetShift != null) stock.setShiftCode(targetShift);
        
        stock.setWorkshopStatus("NORMAL"); // 交接后状态重置为待使用
        tapeStockMapper.updateById(stock);

        // 记流水
        TapeStockLog log = new TapeStockLog();
        log.setStockId(stock.getId());
        log.setBatchNo(stock.getBatchNo());
        log.setMaterialCode(stock.getMaterialCode());
        log.setType("TRANSFER");
        log.setWorkshopSection(stock.getWorkshopSection());
        log.setShiftCode(stock.getShiftCode());
        log.setOperator(operator);
        log.setRemark("交接记录: [" + (oldSection != null ? oldSection : "-串-") + "/" + (oldShift != null ? oldShift : "-班-") + "] -> [" 
                        + stock.getWorkshopSection() + "/" + (stock.getShiftCode() != null ? stock.getShiftCode() : "-班-") + "]。附言: " + remark);
        tapeStockLogMapper.insert(log);

        return ResponseResult.success("交接成功");
    }

    /**
     * 获取每日进出流水汇总
     */
    @GetMapping("/daily-summary")
    public ResponseResult<?> getDailySummary(
            @RequestParam String date,
            @RequestParam(required = false) String workshopSection) {
        
        QueryWrapper<TapeStockLog> query = new QueryWrapper<>();
        query.apply("DATE(create_time) = {0}", date);
        if (workshopSection != null && !workshopSection.isEmpty()) {
            query.eq("workshop_section", workshopSection);
        }
        query.orderByAsc("create_time");
        
        List<TapeStockLog> logs = tapeStockLogMapper.selectList(query);
        return ResponseResult.success(logs);
    }

    /**
     * 将前端简单选项映射为标准损耗分类
     */
    private String mapToStandardReason(String reason) {
        if (reason == null) return "其他报废";
        switch (reason) {
            case "首尾/切边": return "工艺性损耗-固定损耗";
            case "表面瑕疵": return "质量异常-涂布面障";
            case "机台调机": return "技术损耗-调机试机";
            case "实测差异": return "管理损耗-测量误差";
            default: return "其他-" + reason;
        }
    }

    /**
     * 损耗统计汇总 (按原因分类)
     */
    @GetMapping("/loss-summary")
    public ResponseResult<?> getLossSummary(@RequestParam(required = false) String workshopSection) {
        // 实际开发中建议使用 MyBatis XML 写聚合查询
        QueryWrapper<TapeStockLog> qw = new QueryWrapper<TapeStockLog>().gt("loss_area", 0);
        if (workshopSection != null && !workshopSection.isEmpty()) {
            qw.eq("workshop_section", workshopSection);
        }
        
        List<TapeStockLog> logs = tapeStockLogMapper.selectList(qw);
        
        Map<String, BigDecimal> summary = new HashMap<>();
        BigDecimal totalLoss = BigDecimal.ZERO;
        for (TapeStockLog log : logs) {
            String reason = log.getLossReason() != null ? log.getLossReason() : "未知";
            BigDecimal current = summary.getOrDefault(reason, BigDecimal.ZERO);
            summary.put(reason, current.add(log.getLossArea()));
            totalLoss = totalLoss.add(log.getLossArea());
        }

        // 计算现场总面积用于损耗率参考 (本月)
        LambdaQueryWrapper<TapeStock> stockQw = new LambdaQueryWrapper<>();
        stockQw.eq(TapeStock::getLocation, "车间现场");
        if (workshopSection != null && !workshopSection.isEmpty()) {
            stockQw.eq(TapeStock::getWorkshopSection, workshopSection);
        }
        List<TapeStock> floorStocks = tapeStockMapper.selectList(stockQw);
        BigDecimal totalFloorArea = floorStocks.stream()
                .map(s -> s.getTotalSqm() != null ? s.getTotalSqm() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        Map<String, Object> data = new HashMap<>();
        data.put("byReason", summary);
        data.put("monthlyTotalLoss", totalLoss);
        data.put("totalFloorArea", totalFloorArea);
        data.put("totalFloorRolls", floorStocks.size());
        
        // 简单计算损耗率: 总损耗 / (现场现存 + 总损耗)
        BigDecimal totalThroughput = totalFloorArea.add(totalLoss);
        BigDecimal lossRate = totalThroughput.compareTo(BigDecimal.ZERO) > 0 
                ? totalLoss.multiply(new BigDecimal(100)).divide(totalThroughput, 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        data.put("lossRate", lossRate);

        return ResponseResult.success(data);
    }

    /**
     * 损耗详情列表
     */
    @GetMapping("/loss-stats")
    public ResponseResult<?> getLossStats(@RequestParam(required = false) String startDate, 
                                        @RequestParam(required = false) String endDate) {
        // 这部分后续可以用 MyBatis Plus 的聚合查询实现
        // 简单返回所有带损耗的任务
        QueryWrapper<TapeStockLog> query = new QueryWrapper<>();
        query.gt("loss_area", 0)
             .orderByDesc("create_time");
        if (startDate != null) query.ge("create_time", startDate + " 00:00:00");
        if (endDate != null) query.le("create_time", endDate + " 23:59:59");
        
        return ResponseResult.success(tapeStockLogMapper.selectList(query));
    }

    /**
     * 线边仓使用单/流水记录
     */
    @GetMapping("/usage-records")
    public ResponseResult<?> getUsageRecords(
            @RequestParam(required = false) String workshopSection,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String materialCode,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "15") Integer pageSize) {
        
        QueryWrapper<TapeStockLog> query = new QueryWrapper<>();
        if (workshopSection != null && !workshopSection.isEmpty()) {
            query.eq("workshop_section", workshopSection);
        }
        if (type != null && !type.isEmpty()) {
            query.eq("type", type);
        }
        if (materialCode != null && !materialCode.isEmpty()) {
            query.like("material_code", materialCode);
        }
        query.orderByDesc("create_time");
        
        // 分页查询适配
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<TapeStockLog> page = 
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(pageNum, pageSize);
        
        return ResponseResult.success(tapeStockLogMapper.selectPage(page, query));
    }
}
