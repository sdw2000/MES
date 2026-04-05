package com.fine.serviceIMPL.production;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fine.Dao.production.ProductionOvertimeRecordMapper;
import com.fine.model.production.ProductionOvertimeRecord;
import com.fine.service.production.ProductionOvertimeRecordService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;

@Service
public class ProductionOvertimeRecordServiceImpl extends ServiceImpl<ProductionOvertimeRecordMapper, ProductionOvertimeRecord>
        implements ProductionOvertimeRecordService {

    private volatile boolean tablesChecked = false;

    @Autowired
    private ProductionOvertimeRecordMapper overtimeRecordMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private void ensureTablesInitialized() {
        if (tablesChecked) {
            return;
        }
        synchronized (this) {
            if (tablesChecked) {
                return;
            }
            jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS `production_overtime_record` (" +
                    "`id` BIGINT PRIMARY KEY AUTO_INCREMENT," +
                    "`staff_id` BIGINT NOT NULL," +
                    "`staff_code` VARCHAR(20) NOT NULL," +
                    "`staff_name` VARCHAR(50) NOT NULL," +
                    "`overtime_date` DATE NOT NULL," +
                    "`start_time` VARCHAR(5) NOT NULL," +
                    "`end_time` VARCHAR(5) NOT NULL," +
                    "`hours` DECIMAL(6,1) NOT NULL DEFAULT 1.0," +
                    "`reason` VARCHAR(500) NOT NULL," +
                    "`status` VARCHAR(20) NOT NULL DEFAULT 'pending'," +
                    "`remark` VARCHAR(200)," +
                    "`create_time` DATETIME DEFAULT CURRENT_TIMESTAMP," +
                    "`update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP," +
                    "`create_by` VARCHAR(50)," +
                    "`update_by` VARCHAR(50)," +
                    "`is_deleted` TINYINT DEFAULT 0," +
                    "INDEX `idx_overtime_staff_id` (`staff_id`)," +
                    "INDEX `idx_overtime_status` (`status`)," +
                    "INDEX `idx_overtime_date` (`overtime_date`)," +
                    "INDEX `idx_overtime_deleted` (`is_deleted`)" +
                    ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
            tablesChecked = true;
        }
    }

    @Override
    public List<ProductionOvertimeRecord> getOvertimeList(Long staffId, String status, Date startDate, Date endDate) {
        ensureTablesInitialized();
        return overtimeRecordMapper.selectOvertimeList(staffId, status, startDate, endDate);
    }

    @Override
    @Transactional
    public boolean addOvertimeRecord(ProductionOvertimeRecord record) {
        ensureTablesInitialized();
        Date now = new Date();
        record.setCreateTime(now);
        record.setUpdateTime(now);
        if (record.getStatus() == null || record.getStatus().trim().isEmpty()) {
            record.setStatus("pending");
        }
        record.setIsDeleted(0);
        return overtimeRecordMapper.insert(record) > 0;
    }

    @Override
    @Transactional
    public boolean updateOvertimeRecord(ProductionOvertimeRecord record) {
        ensureTablesInitialized();
        record.setUpdateTime(new Date());
        return overtimeRecordMapper.updateById(record) > 0;
    }

    @Override
    @Transactional
    public boolean approveOvertimeRecord(Long id, String status) {
        ensureTablesInitialized();
        ProductionOvertimeRecord update = new ProductionOvertimeRecord();
        update.setId(id);
        update.setStatus(status);
        update.setUpdateTime(new Date());
        return overtimeRecordMapper.updateById(update) > 0;
    }

    @Override
    @Transactional
    public boolean deleteOvertimeRecord(Long id) {
        ensureTablesInitialized();
        ProductionOvertimeRecord update = new ProductionOvertimeRecord();
        update.setId(id);
        update.setIsDeleted(1);
        update.setUpdateTime(new Date());
        return overtimeRecordMapper.updateById(update) > 0;
    }
}
