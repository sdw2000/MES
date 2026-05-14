package com.fine.serviceIMPL;

import com.fine.Utils.ResponseResult;
import com.fine.service.ApService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class ApServiceImpl implements ApService {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Override
    public ResponseResult<?> listBills(Map<String, Object> params) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("SELECT * FROM ap_bill WHERE is_deleted = 0 ORDER BY bill_date DESC LIMIT 200");
        return new ResponseResult<>(200, "OK", rows);
    }

    @Override
    public ResponseResult<?> getBill(Long id) {
        if (id == null) return new ResponseResult<>(400, "id required", null);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("SELECT * FROM ap_bill WHERE id = ? AND is_deleted = 0 LIMIT 1", id);
        return new ResponseResult<>(200, "OK", rows.isEmpty() ? null : rows.get(0));
    }

    @Override
    public ResponseResult<?> createBill(Map<String, Object> payload) {
        String billNo = String.valueOf(payload.getOrDefault("bill_no", ""));
        String supplier = String.valueOf(payload.getOrDefault("supplier_code", ""));
        String billDate = String.valueOf(payload.getOrDefault("bill_date", null));
        Number total = (Number) payload.getOrDefault("total_amount", 0);
        if (billNo.isEmpty() || supplier.isEmpty() || billDate == null) {
            return new ResponseResult<>(400, "missing fields", null);
        }
        jdbcTemplate.update("INSERT INTO ap_bill (bill_no, supplier_code, bill_date, total_amount, created_at, updated_at) VALUES (?, ?, ?, ?, NOW(), NOW())",
                billNo, supplier, java.sql.Date.valueOf(billDate), total.doubleValue());
        return new ResponseResult<>(200, "created", null);
    }
}
