package com.fine.serviceIMPL.stock;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fine.Dao.stock.StockFlowLogMapper;
import com.fine.model.stock.StockFlowLog;
import com.fine.service.stock.StockFlowLogService;
import com.fine.service.UnitService;
import com.fine.model.UnitConversionResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

/**
 * 统一库存流水服务实现
 */
@Service
public class StockFlowLogServiceImpl implements StockFlowLogService {

    @Autowired
    private StockFlowLogMapper stockFlowLogMapper;

    @Autowired
    private UnitService unitService;

    @Override
    public IPage<StockFlowLog> getStockFlowPage(int page, int size, String stockType, String materialCode,
                                               String batchNo, String type, String refNo,
                                               String beginTime, String endTime) {
        Page<StockFlowLog> pageParam = new Page<>(page, size);
        return stockFlowLogMapper.selectPage(pageParam, stockType, materialCode, batchNo, type, refNo, beginTime, endTime);
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