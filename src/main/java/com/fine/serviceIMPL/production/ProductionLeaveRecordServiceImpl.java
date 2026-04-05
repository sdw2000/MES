package com.fine.serviceIMPL.production;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fine.Dao.production.ProductionLeaveRecordMapper;
import com.fine.model.production.ProductionLeaveRecord;
import com.fine.service.production.ProductionLeaveRecordService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;

@Service
public class ProductionLeaveRecordServiceImpl extends ServiceImpl<ProductionLeaveRecordMapper, ProductionLeaveRecord>
        implements ProductionLeaveRecordService {

    private volatile boolean tablesChecked = false;

    @Autowired
    private ProductionLeaveRecordMapper leaveRecordMapper;

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
            jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS `production_leave_record` (" +
                    "`id` BIGINT PRIMARY KEY AUTO_INCREMENT," +
                    "`staff_id` BIGINT NOT NULL," +
                    "`staff_code` VARCHAR(20) NOT NULL," +
                    "`staff_name` VARCHAR(50) NOT NULL," +
                    "`leave_type` VARCHAR(20) NOT NULL," +
                    "`start_date` DATE NOT NULL," +
                    "`end_date` DATE NOT NULL," +
                    "`days` DECIMAL(6,1) NOT NULL DEFAULT 1.0," +
                    "`reason` VARCHAR(500) NOT NULL," +
                    "`status` VARCHAR(20) NOT NULL DEFAULT 'pending'," +
                    "`remark` VARCHAR(200)," +
                    "`create_time` DATETIME DEFAULT CURRENT_TIMESTAMP," +
                    "`update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP," +
                    "`create_by` VARCHAR(50)," +
                    "`update_by` VARCHAR(50)," +
                    "`is_deleted` TINYINT DEFAULT 0," +
                    "INDEX `idx_leave_staff_id` (`staff_id`)," +
                    "INDEX `idx_leave_status` (`status`)," +
                    "INDEX `idx_leave_date` (`start_date`, `end_date`)," +
                    "INDEX `idx_leave_deleted` (`is_deleted`)" +
                    ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
            tablesChecked = true;
        }
    }

    @Override
    public List<ProductionLeaveRecord> getLeaveList(Long staffId, String status, Date startDate, Date endDate) {
        ensureTablesInitialized();
        return leaveRecordMapper.selectLeaveList(staffId, status, startDate, endDate);
    }

    @Override
    @Transactional
    public boolean addLeaveRecord(ProductionLeaveRecord record) {
        ensureTablesInitialized();
        Date now = new Date();
        record.setCreateTime(now);
        record.setUpdateTime(now);
        if (record.getStatus() == null || record.getStatus().trim().isEmpty()) {
            record.setStatus("pending");
        }
        record.setIsDeleted(0);
        return leaveRecordMapper.insert(record) > 0;
    }

    @Override
    @Transactional
    public boolean updateLeaveRecord(ProductionLeaveRecord record) {
        ensureTablesInitialized();
        record.setUpdateTime(new Date());
        return leaveRecordMapper.updateById(record) > 0;
    }

    @Override
    @Transactional
    public boolean approveLeaveRecord(Long id, String status) {
        ensureTablesInitialized();
        ProductionLeaveRecord update = new ProductionLeaveRecord();
        update.setId(id);
        update.setStatus(status);
        update.setUpdateTime(new Date());
        return leaveRecordMapper.updateById(update) > 0;
    }

    @Override
    @Transactional
    public boolean deleteLeaveRecord(Long id) {
        ensureTablesInitialized();
        ProductionLeaveRecord update = new ProductionLeaveRecord();
        update.setId(id);
        update.setIsDeleted(1);
        update.setUpdateTime(new Date());
        return leaveRecordMapper.updateById(update) > 0;
    }
}
