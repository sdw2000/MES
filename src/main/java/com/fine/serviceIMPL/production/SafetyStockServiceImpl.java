package com.fine.serviceIMPL.production;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fine.Dao.production.SafetyStockMapper;
import com.fine.model.production.SafetyStock;
import com.fine.service.production.SafetyStockService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * 安全库存Service实现类
 */
@Service
public class SafetyStockServiceImpl extends ServiceImpl<SafetyStockMapper, SafetyStock> 
        implements SafetyStockService {

    @Autowired
    private SafetyStockMapper safetyStockMapper;

    @Override
    public IPage<SafetyStock> getSafetyStockPage(String materialCode, String stockType,
                                                  Boolean lowStockOnly, Integer page, Integer size) {
        IPage<SafetyStock> pageRequest = new Page<>(page, size);
        
        // 使用MyBatis-Plus的分页查询
        IPage<SafetyStock> pageResult = safetyStockMapper.selectSafetyStockPageList(
            pageRequest, materialCode, stockType, lowStockOnly);
        
        // 计算库存状态
        for (SafetyStock ss : pageResult.getRecords()) {
            ss.setStockStatus(calculateStockStatus(ss));
        }
        
        return pageResult;
    }

    @Override
    public Map<String, Object> getSafetyStockList(String materialCode, String stockType,
                                                  Boolean lowStockOnly, Integer page, Integer size) {
        IPage<SafetyStock> pageRequest = new Page<>(page, size);
        IPage<SafetyStock> pageResult = safetyStockMapper.selectSafetyStockPageList(pageRequest, materialCode, stockType, lowStockOnly);
        
        List<SafetyStock> list = pageResult.getRecords();
        // 计算库存状态
        for (SafetyStock ss : list) {
            ss.setStockStatus(calculateStockStatus(ss));
        }

        Map<String, Object> result = new HashMap<>();
        result.put("list", list);
        result.put("total", pageResult.getTotal());
        result.put("pageNum", pageResult.getCurrent());
        result.put("pageSize", pageResult.getSize());
        return result;
    }

    @Override
    public SafetyStock getByMaterialAndType(String materialCode, String stockType) {
        return safetyStockMapper.selectByMaterialCode(materialCode);
    }

    @Override
    @Transactional
    public boolean addSafetyStock(SafetyStock safetyStock) {
        // 检查是否已存在
        int count = safetyStockMapper.checkExists(safetyStock.getMaterialCode(), 0L);
        if (count > 0) {
            throw new RuntimeException("该料号的安全库存配置已存在");
        }

        safetyStock.setStatus(1);
        safetyStock.setCreateTime(new Date());
        safetyStock.setUpdateTime(new Date());
        return safetyStockMapper.insert(safetyStock) > 0;
    }

    @Override
    @Transactional
    public boolean updateSafetyStock(SafetyStock safetyStock) {
        // 检查是否重复
        int count = safetyStockMapper.checkExists(safetyStock.getMaterialCode(), safetyStock.getId());
        if (count > 0) {
            throw new RuntimeException("该料号的安全库存配置已存在");
        }

        safetyStock.setUpdateTime(new Date());
        return safetyStockMapper.updateById(safetyStock) > 0;
    }

    @Override
    @Transactional
    public boolean deleteSafetyStock(Long id) {
        SafetyStock safetyStock = new SafetyStock();
        safetyStock.setId(id);
        safetyStock.setStatus(0);
        safetyStock.setUpdateTime(new Date());
        return safetyStockMapper.updateById(safetyStock) > 0;
    }

    @Override
    public List<SafetyStock> getNeedRestockList() {
        List<SafetyStock> list = safetyStockMapper.selectNeedRestock();
        for (SafetyStock ss : list) {
            ss.setStockStatus(calculateStockStatus(ss));
        }
        return list;
    }

    @Override
    public Map<String, Object> getStockWarningStats() {
        List<SafetyStock> allList = safetyStockMapper.selectSafetyStockList(null, null, false);
        
        int normalCount = 0;
        int lowCount = 0;
        int criticalCount = 0;
        int overCount = 0;
        
        for (SafetyStock ss : allList) {
            String status = calculateStockStatus(ss);
            switch (status) {
                case "normal":
                    normalCount++;
                    break;
                case "low":
                    lowCount++;
                    break;
                case "critical":
                    criticalCount++;
                    break;
                case "over":
                    overCount++;
                    break;
            }
        }
        
        Map<String, Object> stats = new HashMap<>();
        stats.put("total", allList.size());
        stats.put("normal", normalCount);
        stats.put("low", lowCount);
        stats.put("critical", criticalCount);
        stats.put("over", overCount);
        return stats;
    }

    /**
     * 计算库存状态
     */
    private String calculateStockStatus(SafetyStock ss) {
        Integer currentQty = ss.getCurrentQty() != null ? ss.getCurrentQty() : 0;
        // Integer safetyQty = ss.getSafetyQty() != null ? ss.getSafetyQty() : 0; // Unused
        Integer reorderPoint = ss.getReorderPoint() != null ? ss.getReorderPoint() : 0;
        Integer maxQty = ss.getMaxQty() != null ? ss.getMaxQty() : Integer.MAX_VALUE;

        if (currentQty > maxQty) {
            return "over";  // 超储
        } else if (currentQty <= reorderPoint * 0.5) {
            return "critical";  // 严重不足
        } else if (currentQty <= reorderPoint) {
            return "low";  // 偏低
        } else {
            return "normal";  // 正常
        }
    }
}
