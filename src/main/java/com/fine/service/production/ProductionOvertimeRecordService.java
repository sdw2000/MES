package com.fine.service.production;

import com.baomidou.mybatisplus.extension.service.IService;
import com.fine.model.production.ProductionOvertimeRecord;

import java.util.Date;
import java.util.List;

public interface ProductionOvertimeRecordService extends IService<ProductionOvertimeRecord> {
    List<ProductionOvertimeRecord> getOvertimeList(Long staffId, String status, Date startDate, Date endDate);
    boolean addOvertimeRecord(ProductionOvertimeRecord record);
    boolean updateOvertimeRecord(ProductionOvertimeRecord record);
    boolean approveOvertimeRecord(Long id, String status);
    boolean deleteOvertimeRecord(Long id);
}
