package com.fine.serviceIMPL.stock;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fine.Dao.stock.FilmStockMapper;
import com.fine.Dao.stock.FilmStockDetailMapper;
import com.fine.Dao.stock.FilmStockOutMapper;
import com.fine.model.stock.FilmStock;
import com.fine.model.stock.FilmStockDetail;
import com.fine.model.stock.FilmStockOut;
import com.fine.service.stock.FilmStockService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 薄膜库存服务实现类
 * @author Fine
 * @date 2026-01-15
 */
@Service
public class FilmStockServiceImpl implements FilmStockService {
    
    @Autowired
    private FilmStockMapper filmStockMapper;
    
    @Autowired
    private FilmStockDetailMapper filmStockDetailMapper;
    
    @Autowired
    private FilmStockOutMapper filmStockOutMapper;
    
    @Override
    public List<FilmStock> getBySpec(Integer thickness, Integer width) {
        return filmStockMapper.selectBySpec(thickness, width);
    }
    
    @Override
    public List<FilmStock> getAllFilmStock() {
        QueryWrapper<FilmStock> wrapper = new QueryWrapper<>();
        wrapper.eq("is_deleted", 0);
        wrapper.orderByDesc("create_time");
        return filmStockMapper.selectList(wrapper);
    }
    
    @Override
    public FilmStock getById(Long id) {
        return filmStockMapper.selectById(id);
    }
    
    @Override
    public List<FilmStockDetail> getDetailsByFilmStockId(Long filmStockId) {
        return filmStockDetailMapper.selectByFilmStockId(filmStockId);
    }
    
    @Override
    public List<FilmStockDetail> getAvailableDetails(Long filmStockId) {
        return filmStockDetailMapper.selectByStatus(filmStockId, "available");
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean lockStock(Long filmStockId, BigDecimal lockArea, Integer lockRolls, List<Long> detailIds) {
        // 1. 锁定总量表
        int rows = filmStockMapper.lockStock(filmStockId, lockArea, lockRolls);
        if (rows == 0) {
            throw new RuntimeException("库存不足，无法锁定");
        }
        
        // 2. 锁定明细
        if (detailIds != null && !detailIds.isEmpty()) {
            int detailRows = filmStockDetailMapper.batchUpdateStatus(detailIds, "locked");
            if (detailRows != detailIds.size()) {
                throw new RuntimeException("部分明细锁定失败");
            }
        }
        
        return true;
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean unlockStock(Long filmStockId, BigDecimal unlockArea, Integer unlockRolls, List<Long> detailIds) {
        // 1. 解锁总量表
        int rows = filmStockMapper.unlockStock(filmStockId, unlockArea, unlockRolls);
        if (rows == 0) {
            throw new RuntimeException("解锁失败，锁定库存不足");
        }
        
        // 2. 解锁明细
        if (detailIds != null && !detailIds.isEmpty()) {
            int detailRows = filmStockDetailMapper.batchUpdateStatus(detailIds, "available");
            if (detailRows != detailIds.size()) {
                throw new RuntimeException("部分明细解锁失败");
            }
        }
        
        return true;
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean outbound(FilmStockOut filmStockOut, List<Long> detailIds) {
        // 1. 扣减总量
        int rows = filmStockMapper.deductStock(
            filmStockOut.getFilmStockId(), 
            filmStockOut.getOutArea(), 
            filmStockOut.getOutRolls()
        );
        if (rows == 0) {
            throw new RuntimeException("出库失败，锁定库存不足");
        }
        
        // 2. 更新明细状态为已使用
        if (detailIds != null && !detailIds.isEmpty()) {
            int detailRows = filmStockDetailMapper.batchUpdateStatus(detailIds, "used");
            if (detailRows != detailIds.size()) {
                throw new RuntimeException("部分明细更新失败");
            }
        }
        
        // 3. 创建出库记录
        filmStockOut.setCreateTime(new Date());
        if (filmStockOut.getOutboundTime() == null) {
            filmStockOut.setOutboundTime(new Date());
        }
        filmStockOutMapper.insert(filmStockOut);
        
        return true;
    }
    
    @Override
    public List<FilmStockOut> getOutboundByScheduleId(Long scheduleId) {
        return filmStockOutMapper.selectByScheduleId(scheduleId);
    }
    
    @Override
    public List<Map<String, Object>> getAvailableWidths(Integer thickness) {
        QueryWrapper<FilmStock> wrapper = new QueryWrapper<>();
        
        // 只查询有可用库存的
        wrapper.eq("is_deleted", 0);
        wrapper.gt("available_area", 0);
        wrapper.gt("available_rolls", 0);
        
        // 如果指定厚度，按厚度筛选
        if (thickness != null) {
            wrapper.eq("thickness", thickness);
        }
        
        wrapper.orderByAsc("width");
        List<FilmStock> stocks = filmStockMapper.selectList(wrapper);
        
        // 按宽度分组汇总
        Map<Integer, List<FilmStock>> widthGroupMap = stocks.stream()
                .filter(s -> s.getWidth() != null)
                .collect(Collectors.groupingBy(FilmStock::getWidth));
        
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map.Entry<Integer, List<FilmStock>> entry : widthGroupMap.entrySet()) {
            Integer width = entry.getKey();
            List<FilmStock> list = entry.getValue();
            
            // 汇总该宽度的总可用面积和卷数
            BigDecimal totalAvailableArea = list.stream()
                    .map(FilmStock::getAvailableArea)
                    .filter(Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            
            int totalAvailableRolls = list.stream()
                    .map(FilmStock::getAvailableRolls)
                    .filter(Objects::nonNull)
                    .mapToInt(Integer::intValue)
                    .sum();
            
            // 获取该宽度下的不同厚度
            List<BigDecimal> thicknessList = list.stream()
                    .map(FilmStock::getThickness)
                    .filter(Objects::nonNull)
                    .distinct()
                    .sorted()
                    .collect(Collectors.toList());
            
            Map<String, Object> widthInfo = new HashMap<>();
            widthInfo.put("width", width);
            widthInfo.put("availableArea", totalAvailableArea);
            widthInfo.put("availableRolls", totalAvailableRolls);
            widthInfo.put("thicknessList", thicknessList);
            widthInfo.put("label", width + "mm (可用: " + totalAvailableArea + "㎡, " + totalAvailableRolls + "卷)");
            
            result.add(widthInfo);
        }
        
        // 按宽度排序
        result.sort((a, b) -> {
            Integer w1 = (Integer) a.get("width");
            Integer w2 = (Integer) b.get("width");
            return w1.compareTo(w2);
        });
        
        return result;
    }
    
    @Override
    public Map<String, Object> getStockDetailBySpec(Integer width, Integer thickness) {
        QueryWrapper<FilmStock> wrapper = new QueryWrapper<>();
        wrapper.eq("width", width);
        wrapper.eq("is_deleted", 0);
        if (thickness != null) {
            wrapper.eq("thickness", thickness);
        }
        wrapper.gt("available_area", 0);
        
        List<FilmStock> stocks = filmStockMapper.selectList(wrapper);
        
        if (stocks.isEmpty()) {
            Map<String, Object> empty = new HashMap<>();
            empty.put("width", width);
            empty.put("thickness", thickness);
            empty.put("availableArea", BigDecimal.ZERO);
            empty.put("availableRolls", 0);
            empty.put("stocks", new ArrayList<>());
            return empty;
        }
        
        // 汇总可用库存
        BigDecimal totalAvailableArea = stocks.stream()
                .map(FilmStock::getAvailableArea)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        int totalAvailableRolls = stocks.stream()
                .map(FilmStock::getAvailableRolls)
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue)
                .sum();
        
        Map<String, Object> detail = new HashMap<>();
        detail.put("width", width);
        detail.put("thickness", thickness);
        detail.put("availableArea", totalAvailableArea);
        detail.put("availableRolls", totalAvailableRolls);
        detail.put("stocks", stocks);
        
        return detail;
    }
    
    @Override
    public boolean checkStockAvailability(Integer width, Integer thickness, Double requiredArea) {
        Map<String, Object> detail = getStockDetailBySpec(width, thickness);
        BigDecimal availableArea = (BigDecimal) detail.get("availableArea");
        
        if (availableArea == null) {
            return false;
        }
        
        return availableArea.compareTo(new BigDecimal(requiredArea)) >= 0;
    }
}
