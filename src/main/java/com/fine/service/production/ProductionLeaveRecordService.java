package com.fine.service.production;

import com.baomidou.mybatisplus.extension.service.IService;
import com.fine.model.production.ProductionLeaveRecord;

import java.util.Date;
import java.util.List;

public interface ProductionLeaveRecordService extends IService<ProductionLeaveRecord> {
    List<ProductionLeaveRecord> getLeaveList(Long staffId, String status, Date startDate, Date endDate);
    boolean addLeaveRecord(ProductionLeaveRecord record);
    boolean updateLeaveRecord(ProductionLeaveRecord record);
    boolean approveLeaveRecord(Long id, String status);
    boolean deleteLeaveRecord(Long id);
}
