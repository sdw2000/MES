package com.fine.serviceIMPL.stock;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fine.Dao.stock.ChemicalStockMapper;
import com.fine.Dao.stock.ChemicalStockDetailMapper;
import com.fine.Dao.stock.ChemicalStockOutMapper;
import com.fine.model.stock.ChemicalStock;
import com.fine.model.stock.ChemicalStockDetail;
import com.fine.model.stock.ChemicalStockOut;
import com.fine.service.stock.ChemicalStockService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;

/**
 * 化工原料库存服务实现类
 * @author Fine
 * @date 2026-01-15
 */
@Service
public class ChemicalStockServiceImpl implements ChemicalStockService {
    
    @Autowired
    private ChemicalStockMapper chemicalStockMapper;
    
    @Autowired
    private ChemicalStockDetailMapper chemicalStockDetailMapper;
    
    @Autowired
    private ChemicalStockOutMapper chemicalStockOutMapper;
    
    @Override
    public List<ChemicalStock> getByType(String chemicalType) {
        return chemicalStockMapper.selectByType(chemicalType);
    }
    
    @Override
    public List<ChemicalStock> getAllChemicalStock() {
        QueryWrapper<ChemicalStock> wrapper = new QueryWrapper<>();
        wrapper.orderByDesc("create_time");
        return chemicalStockMapper.selectList(wrapper);
    }
    
    @Override
    public ChemicalStock getById(Long id) {
        return chemicalStockMapper.selectById(id);
    }
    
    @Override
    public List<ChemicalStockDetail> getDetailsByChemicalStockId(Long chemicalStockId) {
        return chemicalStockDetailMapper.selectByChemicalStockId(chemicalStockId);
    }
    
    @Override
    public List<ChemicalStockDetail> getAvailableDetails(Long chemicalStockId) {
        return chemicalStockDetailMapper.selectByStatus(chemicalStockId, "available");
    }
    
    @Override
    public List<ChemicalStockDetail> getExpiringSoon(Integer days) {
        return chemicalStockDetailMapper.selectExpiringSoon(days);
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean lockStock(Long chemicalStockId, Integer lockQuantity, List<Long> detailIds) {
        // 1. 锁定总量表
        int rows = chemicalStockMapper.lockStock(chemicalStockId, lockQuantity);
        if (rows == 0) {
            throw new RuntimeException("库存不足，无法锁定");
        }
        
        // 2. 锁定明细
        if (detailIds != null && !detailIds.isEmpty()) {
            int detailRows = chemicalStockDetailMapper.batchUpdateStatus(detailIds, "locked");
            if (detailRows != detailIds.size()) {
                throw new RuntimeException("部分明细锁定失败");
            }
        }
        
        return true;
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean unlockStock(Long chemicalStockId, Integer unlockQuantity, List<Long> detailIds) {
        // 1. 解锁总量表
        int rows = chemicalStockMapper.unlockStock(chemicalStockId, unlockQuantity);
        if (rows == 0) {
            throw new RuntimeException("解锁失败，锁定库存不足");
        }
        
        // 2. 解锁明细
        if (detailIds != null && !detailIds.isEmpty()) {
            int detailRows = chemicalStockDetailMapper.batchUpdateStatus(detailIds, "available");
            if (detailRows != detailIds.size()) {
                throw new RuntimeException("部分明细解锁失败");
            }
        }
        
        return true;
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean outbound(ChemicalStockOut chemicalStockOut, List<Long> detailIds) {
        // 1. 扣减总量
        int rows = chemicalStockMapper.deductStock(
            chemicalStockOut.getChemicalStockId(), 
            chemicalStockOut.getOutQuantity()
        );
        if (rows == 0) {
            throw new RuntimeException("出库失败，锁定库存不足");
        }
        
        // 2. 更新明细状态为已使用
        if (detailIds != null && !detailIds.isEmpty()) {
            int detailRows = chemicalStockDetailMapper.batchUpdateStatus(detailIds, "used");
            if (detailRows != detailIds.size()) {
                throw new RuntimeException("部分明细更新失败");
            }
        }
        
        // 3. 创建出库记录
        chemicalStockOut.setCreateTime(new Date());
        if (chemicalStockOut.getOutboundTime() == null) {
            chemicalStockOut.setOutboundTime(new Date());
        }
        chemicalStockOutMapper.insert(chemicalStockOut);
        
        return true;
    }
    
    @Override
    public List<ChemicalStockOut> getOutboundByScheduleId(Long scheduleId) {
        return chemicalStockOutMapper.selectByScheduleId(scheduleId);
    }
}
