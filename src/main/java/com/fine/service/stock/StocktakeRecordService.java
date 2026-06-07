package com.fine.service.stock;

import com.fine.Dao.stock.StockProfitLossRecordMapper;
import com.fine.Dao.stock.StocktakeRecordMapper;
import com.fine.model.stock.StockProfitLossRecord;
import com.fine.model.stock.StocktakeRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Date;

@Service
public class StocktakeRecordService {
    @Autowired
    private StocktakeRecordMapper stocktakeRecordMapper;

    @Autowired
    private StockProfitLossRecordMapper stockProfitLossRecordMapper;

    public StocktakeRecord record(String warehouseType,
                                  Long stockId,
                                  Long detailId,
                                  String materialCode,
                                  String materialName,
                                  String specDesc,
                                  String batchNo,
                                  String containerNo,
                                  String location,
                                  String unit,
                                  BigDecimal beforeQuantity,
                                  BigDecimal afterQuantity,
                                  String operator,
                                  String reason,
                                  String remark) {
        BigDecimal before = nz(beforeQuantity);
        BigDecimal after = nz(afterQuantity);
        BigDecimal diff = after.subtract(before);
        String changeStatus = diff.compareTo(BigDecimal.ZERO) > 0 ? "PROFIT"
                : (diff.compareTo(BigDecimal.ZERO) < 0 ? "LOSS" : "UNCHANGED");

        StocktakeRecord record = new StocktakeRecord();
        record.setWarehouseType(warehouseType);
        record.setStockId(stockId);
        record.setDetailId(detailId);
        record.setMaterialCode(materialCode);
        record.setMaterialName(materialName);
        record.setSpecDesc(specDesc);
        record.setBatchNo(batchNo);
        record.setContainerNo(containerNo);
        record.setLocation(location);
        record.setUnit(unit);
        record.setBeforeQuantity(before);
        record.setAfterQuantity(after);
        record.setDiffQuantity(diff);
        record.setChangeStatus(changeStatus);
        record.setOperator(operator);
        record.setReason(reason);
        record.setRemark(remark);
        record.setCreateTime(new Date());
        stocktakeRecordMapper.insert(record);

        if (!"UNCHANGED".equals(changeStatus)) {
            StockProfitLossRecord profitLoss = new StockProfitLossRecord();
            profitLoss.setStocktakeRecordId(record.getId());
            profitLoss.setWarehouseType(warehouseType);
            profitLoss.setStockId(stockId);
            profitLoss.setDetailId(detailId);
            profitLoss.setMaterialCode(materialCode);
            profitLoss.setMaterialName(materialName);
            profitLoss.setSpecDesc(specDesc);
            profitLoss.setBatchNo(batchNo);
            profitLoss.setContainerNo(containerNo);
            profitLoss.setLocation(location);
            profitLoss.setUnit(unit);
            profitLoss.setBeforeQuantity(before);
            profitLoss.setAfterQuantity(after);
            profitLoss.setDiffQuantity(diff);
            profitLoss.setProfitLossType(changeStatus);
            profitLoss.setOperator(operator);
            profitLoss.setReason(reason);
            profitLoss.setCreateTime(new Date());
            stockProfitLossRecordMapper.insert(profitLoss);
        }

        return record;
    }

    private BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}