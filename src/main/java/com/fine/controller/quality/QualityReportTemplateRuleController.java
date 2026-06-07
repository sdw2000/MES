package com.fine.controller.quality;

import com.fine.Utils.ResponseResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import javax.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/api/quality/report-template")
@PreAuthorize("hasAnyAuthority('admin','quality','sales','production')")
public class QualityReportTemplateRuleController {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @PostConstruct
    public void ensureTable() {
        String ddl = "CREATE TABLE IF NOT EXISTS quality_report_template_rule ("
                + "id BIGINT PRIMARY KEY AUTO_INCREMENT,"
                + "customer_code VARCHAR(64) NOT NULL,"
                + "inspection_type VARCHAR(32) NOT NULL,"
                + "material_code VARCHAR(128) NULL,"
                + "material_prefix VARCHAR(128) NULL,"
                + "priority INT NOT NULL DEFAULT 0,"
                + "template_code VARCHAR(64) NOT NULL,"
                + "enabled TINYINT NOT NULL DEFAULT 1,"
                + "remark VARCHAR(255) NULL,"
                + "created_by VARCHAR(64) NULL,"
                + "updated_by VARCHAR(64) NULL,"
                + "created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,"
                + "updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,"
                + "INDEX idx_customer_type_enabled (customer_code, inspection_type, enabled),"
                + "INDEX idx_material_code (material_code),"
                + "INDEX idx_material_prefix (material_prefix),"
                + "INDEX idx_priority (priority)"
                + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4";
        jdbcTemplate.execute(ddl);

        // 兼容旧库：补列
        String[] alters = new String[] {
                "ALTER TABLE quality_report_template_rule ADD COLUMN IF NOT EXISTS material_code VARCHAR(128) NULL",
                "ALTER TABLE quality_report_template_rule ADD COLUMN IF NOT EXISTS material_prefix VARCHAR(128) NULL",
                "ALTER TABLE quality_report_template_rule ADD COLUMN IF NOT EXISTS priority INT NOT NULL DEFAULT 0",
                "ALTER TABLE quality_report_template_rule ADD COLUMN IF NOT EXISTS remark VARCHAR(255) NULL"
        };
        for (String sql : alters) {
            try {
                jdbcTemplate.execute(sql);
            } catch (Exception ignore) {
            }
        }

        // 兼容旧库：移除唯一约束（允许同客户+类型下配置多个规则）
        try {
            Integer exists = jdbcTemplate.queryForObject(
                    "SELECT COUNT(1) FROM information_schema.statistics " +
                            "WHERE table_schema = DATABASE() AND table_name='quality_report_template_rule' AND index_name='uk_customer_type'",
                    Integer.class
            );
            if (exists != null && exists > 0) {
                jdbcTemplate.execute("ALTER TABLE quality_report_template_rule DROP INDEX uk_customer_type");
            }
        } catch (Exception ignore) {
        }
    }

    @GetMapping("/get")
    public ResponseResult<?> getRule(@RequestParam("customerCode") String customerCode,
                                     @RequestParam(value = "inspectionType", required = false, defaultValue = "outbound") String inspectionType,
                                     @RequestParam(value = "materialCode", required = false) String materialCode) {
        String normalizedCustomerCode = normalizeCustomerCode(customerCode);
        String normalizedInspectionType = normalizeInspectionType(inspectionType);
        String normalizedMaterialCode = normalizeMaterialCode(materialCode);

        String sql = "SELECT id, customer_code, inspection_type, material_code, material_prefix, priority, template_code, enabled, remark, updated_at "
                + "FROM quality_report_template_rule "
            + "WHERE inspection_type=? AND enabled=1 "
            + "AND (customer_code=? OR customer_code='' OR customer_code IS NULL) "
                + "AND (material_code IS NULL OR material_code='' OR material_code=?) "
                + "AND (material_prefix IS NULL OR material_prefix='' OR ? LIKE CONCAT(material_prefix, '%')) "
                + "ORDER BY "
            + "  CASE WHEN customer_code=? THEN 20 ELSE 10 END DESC, "
            + "  CASE WHEN material_code IS NOT NULL AND material_code<>'' AND material_code=? THEN 3 "
            + "       WHEN material_prefix IS NOT NULL AND material_prefix<>'' AND ? LIKE CONCAT(material_prefix, '%') THEN 2 "
            + "       ELSE 1 END DESC, "
                + "  CHAR_LENGTH(IFNULL(material_prefix,'')) DESC, "
                + "  priority DESC, "
                + "  updated_at DESC, id DESC LIMIT 1";
        List<Map<String, Object>> list = jdbcTemplate.queryForList(
                sql,
                normalizedInspectionType,
            normalizedCustomerCode,
                normalizedMaterialCode,
                normalizedMaterialCode,
            normalizedCustomerCode,
                normalizedMaterialCode,
                normalizedMaterialCode
        );
        if (list == null || list.isEmpty()) {
            return ResponseResult.success(null);
        }
        Map<String, Object> row = list.get(0);
        Map<String, Object> data = new HashMap<>();
        data.put("id", row.get("id"));
        data.put("customerCode", row.get("customer_code"));
        data.put("inspectionType", row.get("inspection_type"));
        data.put("materialCode", row.get("material_code"));
        data.put("materialPrefix", row.get("material_prefix"));
        data.put("priority", row.get("priority"));
        data.put("templateCode", row.get("template_code"));
        data.put("enabled", row.get("enabled"));
        data.put("remark", row.get("remark"));
        data.put("updatedAt", row.get("updated_at"));
        return ResponseResult.success(data);
    }

    @PostMapping("/save")
    public ResponseResult<?> saveRule(@RequestBody Map<String, Object> body) {
        Long id = longNumber(body, "id");
        String customerCode = text(body, "customerCode");
        String inspectionType = text(body, "inspectionType");
        String materialCode = text(body, "materialCode");
        String materialPrefix = text(body, "materialPrefix");
        Integer priority = number(body, "priority");
        String templateCode = text(body, "templateCode");
        Integer enabled = number(body, "enabled");
        String remark = text(body, "remark");

        if (isBlank(templateCode)) {
            return ResponseResult.error("templateCode不能为空");
        }

        String normalizedCustomerCode = normalizeCustomerCode(customerCode);
        String normalizedInspectionType = normalizeInspectionType(inspectionType);
        String normalizedMaterialCode = normalizeMaterialCode(materialCode);
        String normalizedMaterialPrefix = normalizeMaterialCode(materialPrefix);
        String normalizedTemplateCode = normalizeTemplateCode(templateCode);

        if (enabled == null) {
            enabled = 1;
        }
        if (priority == null) {
            priority = 0;
        }

        String user = resolveOperator();
        LocalDateTime now = LocalDateTime.now();

        if (id != null && id > 0) {
            String update = "UPDATE quality_report_template_rule SET customer_code=?, inspection_type=?, material_code=?, material_prefix=?, priority=?, template_code=?, enabled=?, remark=?, updated_by=?, updated_at=? WHERE id=?";
            int affected = jdbcTemplate.update(update,
                    normalizedCustomerCode,
                    normalizedInspectionType,
                    emptyToNull(normalizedMaterialCode),
                    emptyToNull(normalizedMaterialPrefix),
                    priority,
                    normalizedTemplateCode,
                    enabled,
                    emptyToNull(remark),
                    user,
                    now,
                    id);
            if (affected <= 0) {
                return ResponseResult.error("规则不存在或已删除");
            }
        } else {
            String insert = "INSERT INTO quality_report_template_rule (customer_code, inspection_type, material_code, material_prefix, priority, template_code, enabled, remark, created_by, updated_by, created_at, updated_at) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
            jdbcTemplate.update(insert,
                    normalizedCustomerCode,
                    normalizedInspectionType,
                    emptyToNull(normalizedMaterialCode),
                    emptyToNull(normalizedMaterialPrefix),
                    priority,
                    normalizedTemplateCode,
                    enabled,
                    emptyToNull(remark),
                    user,
                    user,
                    now,
                    now);
        }

        Map<String, Object> data = new HashMap<>();
        data.put("customerCode", normalizedCustomerCode);
        data.put("inspectionType", normalizedInspectionType);
        data.put("materialCode", normalizedMaterialCode);
        data.put("materialPrefix", normalizedMaterialPrefix);
        data.put("priority", priority);
        data.put("templateCode", normalizedTemplateCode);
        data.put("enabled", enabled);
        data.put("remark", remark);
        data.put("updatedBy", user);
        data.put("updatedAt", now.toString());
        return ResponseResult.success(data);
    }

    @GetMapping("/page")
    public ResponseResult<?> pageRules(@RequestParam(value = "customerCode", required = false) String customerCode,
                                       @RequestParam(value = "inspectionType", required = false) String inspectionType,
                                       @RequestParam(value = "materialCode", required = false) String materialCode,
                                       @RequestParam(value = "templateCode", required = false) String templateCode,
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
            args.add("%" + normalizeCustomerCode(customerCode) + "%");
        }
        if (!isBlank(inspectionType)) {
            where.append(" AND inspection_type = ? ");
            args.add(normalizeInspectionType(inspectionType));
        }
        if (!isBlank(materialCode)) {
            String normalized = normalizeMaterialCode(materialCode);
            where.append(" AND (material_code LIKE ? OR material_prefix LIKE ?) ");
            args.add("%" + normalized + "%");
            args.add("%" + normalized + "%");
        }
        if (!isBlank(templateCode)) {
            where.append(" AND template_code = ? ");
            args.add(normalizeTemplateCode(templateCode));
        }
        if (enabled != null && (enabled == 0 || enabled == 1)) {
            where.append(" AND enabled = ? ");
            args.add(enabled);
        }

        String countSql = "SELECT COUNT(1) FROM quality_report_template_rule" + where;
        Long total = jdbcTemplate.queryForObject(countSql, Long.class, args.toArray());

        String pageSql = "SELECT id, customer_code, inspection_type, material_code, material_prefix, priority, template_code, enabled, remark, created_by, updated_by, created_at, updated_at "
                + "FROM quality_report_template_rule " + where
            + " ORDER BY customer_code ASC, inspection_type ASC, priority DESC, updated_at DESC, id DESC LIMIT ? OFFSET ?";
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(safeSize);
        pageArgs.add(offset);

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(pageSql, pageArgs.toArray());
        List<Map<String, Object>> records = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Map<String, Object> item = new HashMap<>();
            item.put("id", row.get("id"));
            item.put("customerCode", row.get("customer_code"));
            item.put("inspectionType", row.get("inspection_type"));
            item.put("materialCode", row.get("material_code"));
            item.put("materialPrefix", row.get("material_prefix"));
            item.put("priority", row.get("priority"));
            item.put("templateCode", row.get("template_code"));
            item.put("enabled", row.get("enabled"));
            item.put("remark", row.get("remark"));
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
    public ResponseResult<?> deleteRule(@RequestParam(value = "id", required = false) Long id,
                                        @RequestParam(value = "customerCode", required = false) String customerCode,
                                        @RequestParam(value = "inspectionType", required = false, defaultValue = "outbound") String inspectionType) {
        if (id != null && id > 0) {
            int affected = jdbcTemplate.update("DELETE FROM quality_report_template_rule WHERE id=?", id);
            if (affected <= 0) {
                return ResponseResult.success("未找到可删除的规则", null);
            }
            return ResponseResult.success("删除成功", null);
        }

        if (isBlank(customerCode)) {
            return ResponseResult.error("id或customerCode至少提供一个");
        }
        String normalizedCustomerCode = normalizeCustomerCode(customerCode);
        String normalizedInspectionType = normalizeInspectionType(inspectionType);
        String sql = "DELETE FROM quality_report_template_rule WHERE customer_code=? AND inspection_type=?";
        int affected = jdbcTemplate.update(sql, normalizedCustomerCode, normalizedInspectionType);
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

    private Long longNumber(Map<String, Object> body, String key) {
        if (body == null || key == null) return null;
        Object value = body.get(key);
        if (value == null) return null;
        try {
            return Long.parseLong(String.valueOf(value).trim());
        } catch (Exception e) {
            return null;
        }
    }

    private String normalizeCustomerCode(String customerCode) {
        String text = customerCode == null ? "" : customerCode.trim().toUpperCase();
        if (text.isEmpty()) {
            return text;
        }
        return text.replaceAll("[^A-Z0-9]", "");
    }

    private String normalizeInspectionType(String inspectionType) {
        String text = inspectionType == null ? "" : inspectionType.trim().toLowerCase();
        if ("incoming".equals(text) || "process".equals(text) || "outbound".equals(text)) {
            return text;
        }
        return "outbound";
    }

    private String normalizeTemplateCode(String templateCode) {
        String text = templateCode == null ? "" : templateCode.trim().toUpperCase();
        if (text.isEmpty()) {
            return "OUTBOUND_DEFAULT";
        }
        return text;
    }

    private String normalizeMaterialCode(String materialCode) {
        String text = materialCode == null ? "" : materialCode.trim().toUpperCase();
        if (text.isEmpty()) {
            return "";
        }
        return text.replaceAll("\\s+", "");
    }

    private String emptyToNull(String value) {
        if (value == null) return null;
        String text = value.trim();
        return text.isEmpty() ? null : text;
    }
}
