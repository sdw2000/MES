package com.fine.serviceIMPL;

import com.fine.Utils.ResponseResult;
import com.fine.service.GlService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
public class GlServiceImpl implements GlService {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResponseResult<?> postEntry(Map<String, Object> payload) {
        // Expect payload contains voucher_no, entry_date and lines: [{gl_account_id,debit,credit,description}]
        String voucherNo = String.valueOf(payload.getOrDefault("voucher_no", ""));
        String entryDate = String.valueOf(payload.getOrDefault("entry_date", null));
        Object linesObj = payload.get("lines");
        if (voucherNo.isEmpty() || entryDate == null || !(linesObj instanceof List)) {
            return new ResponseResult<>(400, "invalid payload", null);
        }
        List<?> lines = (List<?>) linesObj;
        for (Object o : lines) {
            if (!(o instanceof Map)) continue;
            Map<?,?> line = (Map<?,?>) o;
            Object acctObj = line.get("gl_account_id");
            Number acct = acctObj instanceof Number ? (Number) acctObj : null;
            Object debitObj = line.get("debit");
            Object creditObj = line.get("credit");
            double debitVal = debitObj instanceof Number ? ((Number) debitObj).doubleValue() : 0.0;
            double creditVal = creditObj instanceof Number ? ((Number) creditObj).doubleValue() : 0.0;
            Object descObj = line.get("description");
            String desc = descObj == null ? "" : String.valueOf(descObj);
            jdbcTemplate.update("INSERT INTO gl_entry (voucher_no, entry_date, gl_account_id, debit, credit, description, created_at) VALUES (?, ?, ?, ?, ?, ?, NOW())",
                voucherNo, java.sql.Date.valueOf(entryDate), acct == null ? null : acct.longValue(), debitVal, creditVal, desc);
        }
        return new ResponseResult<>(200, "posted", null);
    }
}
