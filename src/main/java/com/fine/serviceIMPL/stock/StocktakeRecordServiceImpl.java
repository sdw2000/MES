package com.fine.serviceIMPL.stock;

import com.fine.service.stock.StocktakeRecordService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * 盘点记录服务（临时实现）
 */
@Slf4j
@Service
public class StocktakeRecordServiceImpl implements StocktakeRecordService {

    @Override
    public void record(String stockType,
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
                       String bizDesc) {
        log.info("stocktake record: stockType={}, stockId={}, detailId={}, materialCode={}, materialName={}, spec={}, " +
                        "batchNo={}, rollOrContainerNo={}, location={}, unit={}, beforeQty={}, afterQty={}, operator={}, reason={}, bizDesc={}",
                stockType, stockId, detailId, materialCode, materialName, spec,
                batchNo, rollOrContainerNo, location, unit, beforeQty, afterQty, operator, reason, bizDesc);
    }
}
