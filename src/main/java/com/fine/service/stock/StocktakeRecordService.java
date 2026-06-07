package com.fine.service.stock;

import java.math.BigDecimal;

/**
 * 盘点记录服务（轻量实现接口）
 */
public interface StocktakeRecordService {

    void record(String stockType,
                Long stockId,
                Long detailId,
                String materialCode,
                String materialName,
                String spec,
                String batchNo,
                String rollOrContainerNo,
                String location,
                String unit,
                BigDecimal beforeQty,
                BigDecimal afterQty,
                String operator,
                String reason,
                String bizDesc);
}
