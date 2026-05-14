package com.fine.controller;

import com.fine.Utils.ResponseResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import javax.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/production/label-qr-rule")
@PreAuthorize("hasAnyAuthority('admin','sales','finance','production','packaging','plan','warehouse','quality','rd')")
public class LabelQrRuleController {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @PostConstruct
    public void ensureTable() {
        String ddl = "CREATE TABLE IF NOT EXISTS label_qr_rule ("
                + "id BIGINT PRIMARY KEY AUTO_INCREMENT,"
                + "customer_code VARCHAR(64) NOT NULL,"
                + "biz_type VARCHAR(64) NOT NULL,"
                + "qr_template VARCHAR(1000) NOT NULL,"
                + "enabled TINYINT NOT NULL DEFAULT 1,"
                + "created_by VARCHAR(64) NULL,"
                + "updated_by VARCHAR(64) NULL,"
                + "created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,"
                + "updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,"
                + "UNIQUE KEY uk_customer_biz (customer_code, biz_type)"
                + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4";
        jdbcTemplate.execute(ddl);
    }

    @GetMapping("/get")
    public ResponseResult<?> getRule(@RequestParam("customerCode") String customerCode,
                                     @RequestParam(value = "bizType", required = false, defaultValue = "SLITTING_OUTER_LABEL") String bizType) {
        if (isBlank(customerCode)) {
            return ResponseResult.error("customerCode不能为空");
        }
        String normalizedBizType = normalizeBizType(bizType);
        String sql = "SELECT id, customer_code, biz_type, qr_template, enabled, updated_at FROM label_qr_rule WHERE customer_code=? AND biz_type=? LIMIT 1";
        List<Map<String, Object>> list = jdbcTemplate.queryForList(sql, customerCode.trim(), normalizedBizType);
        if (list == null || list.isEmpty()) {
            return ResponseResult.success(null);
        }
        Map<String, Object> row = list.get(0);
        Map<String, Object> data = new HashMap<>();
        data.put("id", row.get("id"));
        data.put("customerCode", row.get("customer_code"));
        data.put("bizType", row.get("biz_type"));
        data.put("qrTemplate", row.get("qr_template"));
        data.put("enabled", row.get("enabled"));
        data.put("updatedAt", row.get("updated_at"));
        return ResponseResult.success(data);
    }

    @PostMapping("/save")
    public ResponseResult<?> saveRule(@RequestBody Map<String, Object> body) {
        String customerCode = text(body, "customerCode");
        String bizType = text(body, "bizType");
        String qrTemplate = text(body, "qrTemplate");
        Integer enabled = number(body, "enabled");

        if (isBlank(customerCode)) {
            return ResponseResult.error("customerCode不能为空");
        }
        bizType = normalizeBizType(bizType);
        if (isBlank(qrTemplate)) {
            return ResponseResult.error("qrTemplate不能为空");
        }
        if (enabled == null) {
            enabled = 1;
        }

        String user = resolveOperator();
        LocalDateTime now = LocalDateTime.now();

        String upsert = "INSERT INTO label_qr_rule (customer_code, biz_type, qr_template, enabled, created_by, updated_by, created_at, updated_at) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?) "
                + "ON DUPLICATE KEY UPDATE qr_template=VALUES(qr_template), enabled=VALUES(enabled), updated_by=VALUES(updated_by), updated_at=VALUES(updated_at)";
        jdbcTemplate.update(upsert,
                customerCode.trim(),
                bizType,
                qrTemplate,
                enabled,
                user,
                user,
                now,
                now);

        Map<String, Object> data = new HashMap<>();
        data.put("customerCode", customerCode.trim());
        data.put("bizType", bizType);
        data.put("qrTemplate", qrTemplate);
        data.put("enabled", enabled);
        data.put("updatedBy", user);
        data.put("updatedAt", now.toString());
        return ResponseResult.success(data);
    }

    @GetMapping("/page")
    public ResponseResult<?> pageRules(@RequestParam(value = "customerCode", required = false) String customerCode,
                                       @RequestParam(value = "bizType", required = false) String bizType,
                                       @RequestParam(value = "enabled", required = false) Integer enabled,
                                       @RequestParam(value = "current", required = false, defaultValue = "1") Integer current,
                                       @RequestParam(value = "size", required = false, defaultValue = "20") Integer size) {
        int safeCurrent = current == null || current < 1 ? 1 : current;
        int safeSize = size == null || size < 1 ? 20 : Math.min(size, 200);
        int offset = (safeCurrent - 1) * safeSize;

        StringBuilder where = new StringBuilder(" WHERE 1=1 ");
        List<Object> args = new ArrayList<>();

        if (!isBlank(customerCode)) {
            where.append(" AND customer_code LIKE ? ");
            args.add("%" + customerCode.trim() + "%");
        }
        if (!isBlank(bizType)) {
            where.append(" AND biz_type = ? ");
            args.add(bizType.trim());
        }
        if (enabled != null && (enabled == 0 || enabled == 1)) {
            where.append(" AND enabled = ? ");
            args.add(enabled);
        }

        String countSql = "SELECT COUNT(1) FROM label_qr_rule" + where;
        Long total = jdbcTemplate.queryForObject(countSql, Long.class, args.toArray());

        String pageSql = "SELECT id, customer_code, biz_type, qr_template, enabled, created_by, updated_by, created_at, updated_at "
                + "FROM label_qr_rule " + where
                + " ORDER BY updated_at DESC, id DESC LIMIT ? OFFSET ?";
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(safeSize);
        pageArgs.add(offset);

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(pageSql, pageArgs.toArray());
        List<Map<String, Object>> records = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Map<String, Object> item = new HashMap<>();
            item.put("id", row.get("id"));
            item.put("customerCode", row.get("customer_code"));
            item.put("bizType", row.get("biz_type"));
            item.put("qrTemplate", row.get("qr_template"));
            item.put("enabled", row.get("enabled"));
            item.put("createdBy", row.get("created_by"));
            item.put("updatedBy", row.get("updated_by"));
            item.put("createdAt", row.get("created_at"));
            item.put("updatedAt", row.get("updated_at"));
            records.add(item);
        }

        Map<String, Object> data = new HashMap<>();
        data.put("records", records);
        data.put("total", total == null ? 0L : total);
        data.put("current", safeCurrent);
        data.put("size", safeSize);
        return ResponseResult.success(data);
    }

    @DeleteMapping("/delete")
    public ResponseResult<?> deleteRule(@RequestParam("customerCode") String customerCode,
                                        @RequestParam(value = "bizType", required = false, defaultValue = "SLITTING_OUTER_LABEL") String bizType) {
        if (isBlank(customerCode)) {
            return ResponseResult.error("customerCode不能为空");
        }
        String normalizedBizType = normalizeBizType(bizType);
        String sql = "DELETE FROM label_qr_rule WHERE customer_code=? AND biz_type=?";
        int affected = jdbcTemplate.update(sql, customerCode.trim(), normalizedBizType);
        if (affected <= 0) {
            return ResponseResult.success("未找到可删除的规则", null);
        }
        return ResponseResult.success("删除成功", null);
    }

    private String resolveOperator() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null || "anonymousUser".equalsIgnoreCase(authentication.getName())) {
            return "system";
        }
        return authentication.getName();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String text(Map<String, Object> body, String key) {
        if (body == null || key == null) return null;
        Object value = body.get(key);
        if (value == null) return null;
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    private Integer number(Map<String, Object> body, String key) {
        if (body == null || key == null) return null;
        Object value = body.get(key);
        if (value == null) return null;
        try {
            return Integer.parseInt(String.valueOf(value).trim());
        } catch (Exception e) {
            return null;
        }
    }

    private String normalizeBizType(String bizType) {
        String text = bizType == null ? "" : bizType.trim();
        if (text.isEmpty()) {
            return "SLITTING_OUTER_LABEL";
        }
        String upper = text.toUpperCase();
        if ("[OBJECT POINTEREVENT]".equals(upper)
                || "[OBJECT MOUSEEVENT]".equals(upper)
                || upper.contains("ISTRUSTED")) {
            return "SLITTING_OUTER_LABEL";
        }
        return text;
    }
}
