package com.fine.service.stock;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.fine.model.stock.StockFlowLog;
import java.util.List;

/**
 * 统一库存流水服务接口
 */
public interface StockFlowLogService {

    /**
     * 分页查询库存流水
     */
    IPage<StockFlowLog> getStockFlowPage(int page, int size, String stockType, String materialCode,
                                        String batchNo, String type, String refNo,
                                        String beginTime, String endTime);

    /**
     * 根据库存ID查询流水
     */
    List<StockFlowLog> getStockFlowByStock(String stockType, Long stockId);

    /**
     * 记录库存变动
     */
    void logStockChange(String stockType, Long stockId, String batchNo, String materialCode,
                       String productName, String type, java.math.BigDecimal changeQuantity,
                       String unit, java.math.BigDecimal beforeQuantity, java.math.BigDecimal afterQuantity,
                       String refNo, String operator, String remark);
}