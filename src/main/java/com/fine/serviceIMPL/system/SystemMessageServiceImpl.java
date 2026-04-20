package com.fine.serviceIMPL.system;

import com.fine.service.system.SystemMessageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.annotation.PostConstruct;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class SystemMessageServiceImpl implements SystemMessageService {

    private static final String BIZ_TYPE_PURCHASE_ARRIVAL = "PURCHASE_RECEIPT_ARRIVAL";
    private static final Pattern RECEIPT_ID_PATTERN = Pattern.compile("\"receiptId\"\\s*:\\s*(\\d+)");
    private static final Pattern ITEM_ID_PATTERN = Pattern.compile("\"itemId\"\\s*:\\s*\"?([^\",}]+)");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @PostConstruct
    public void ensureTables() {
        String noticeTable = "CREATE TABLE IF NOT EXISTS system_message_notice ("
                + "id BIGINT PRIMARY KEY AUTO_INCREMENT,"
                + "target_role VARCHAR(64) NOT NULL DEFAULT 'ALL',"
                + "title VARCHAR(200) NOT NULL,"
                + "content VARCHAR(1000) NULL,"
                + "biz_type VARCHAR(100) NULL,"
                + "biz_id VARCHAR(100) NULL,"
                + "route_path VARCHAR(255) NULL,"
                + "route_query_json VARCHAR(1000) NULL,"
                + "is_deleted TINYINT NOT NULL DEFAULT 0,"
                + "created_by VARCHAR(64) NULL,"
                + "created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,"
                + "updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,"
                + "UNIQUE KEY uk_msg_biz_role (target_role, biz_type, biz_id),"
                + "KEY idx_msg_role_time (target_role, created_at),"
                + "KEY idx_msg_deleted_time (is_deleted, created_at)"
                + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4";

        String readTable = "CREATE TABLE IF NOT EXISTS system_message_read ("
                + "id BIGINT PRIMARY KEY AUTO_INCREMENT,"
                + "message_id BIGINT NOT NULL,"
                + "reader VARCHAR(64) NOT NULL,"
                + "read_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,"
                + "UNIQUE KEY uk_msg_reader (message_id, reader),"
                + "KEY idx_reader_time (reader, read_at)"
                + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4";

        jdbcTemplate.execute(noticeTable);
        jdbcTemplate.execute(readTable);
        normalizeLegacyPurchaseArrivalMessages();
    }

    @Override
    public void createRoleMessage(String targetRole,
                                  String title,
                                  String content,
                                  String bizType,
                                  String bizId,
                                  String routePath,
                                  String routeQueryJson,
                                  String createdBy) {
        String role = normalizeRole(targetRole);
        String sql = "INSERT INTO system_message_notice (target_role, title, content, biz_type, biz_id, route_path, route_query_json, created_by, created_at, updated_at) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?) "
                + "ON DUPLICATE KEY UPDATE title=VALUES(title), content=VALUES(content), route_path=VALUES(route_path), route_query_json=VALUES(route_query_json), is_deleted=0, updated_at=VALUES(updated_at)";
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update(sql,
                role,
                trimText(title),
                trimText(content),
                trimText(bizType),
                trimText(bizId),
                trimText(routePath),
                trimText(routeQueryJson),
                trimText(createdBy),
                now,
                now);
    }

    @Override
    public long unreadCountForCurrentUser() {
        String user = currentUser();
        Set<String> roles = currentRoles();
        if (!StringUtils.hasText(user) || roles.isEmpty()) {
            return 0L;
        }

        List<Object> args = new ArrayList<>();
        String roleClause = buildRoleClause(roles, args);
        args.add(user);

        String sql = "SELECT COUNT(1) FROM system_message_notice m "
                + "WHERE m.is_deleted = 0 AND " + roleClause + " "
                + "AND NOT EXISTS (SELECT 1 FROM system_message_read r WHERE r.message_id = m.id AND r.reader = ?)";
        Long count = jdbcTemplate.queryForObject(sql, args.toArray(), Long.class);
        return count == null ? 0L : count;
    }

    @Override
    public Map<String, Object> pageForCurrentUser(int current, int size, boolean onlyUnread) {
        int safeCurrent = current < 1 ? 1 : current;
        int safeSize = size < 1 ? 20 : Math.min(size, 100);
        int offset = (safeCurrent - 1) * safeSize;

        String user = currentUser();
        Set<String> roles = currentRoles();
        Map<String, Object> data = new HashMap<>();
        if (!StringUtils.hasText(user) || roles.isEmpty()) {
            data.put("records", new ArrayList<>());
            data.put("total", 0L);
            data.put("current", safeCurrent);
            data.put("size", safeSize);
            return data;
        }

        List<Object> args = new ArrayList<>();
        String roleClause = buildRoleClause(roles, args);

        StringBuilder where = new StringBuilder(" WHERE m.is_deleted = 0 AND ").append(roleClause).append(" ");
        if (onlyUnread) {
            where.append(" AND NOT EXISTS (SELECT 1 FROM system_message_read ur WHERE ur.message_id = m.id AND ur.reader = ?) ");
            args.add(user);
        }

        String countSql = "SELECT COUNT(1) FROM system_message_notice m " + where;
        Long total = jdbcTemplate.queryForObject(countSql, args.toArray(), Long.class);

        List<Object> pageArgs = new ArrayList<>();
        pageArgs.add(user);
        pageArgs.addAll(args);
        pageArgs.add(safeSize);
        pageArgs.add(offset);

        String pageSql = "SELECT m.id, m.title, m.content, m.target_role, m.biz_type, m.biz_id, m.route_path, m.route_query_json, m.created_at, "
                + "CASE WHEN EXISTS (SELECT 1 FROM system_message_read ur WHERE ur.message_id = m.id AND ur.reader = ?) THEN 1 ELSE 0 END AS is_read "
                + "FROM system_message_notice m " + where
                + " ORDER BY m.created_at DESC, m.id DESC LIMIT ? OFFSET ?";

        List<Map<String, Object>> records = jdbcTemplate.queryForList(pageSql, pageArgs.toArray());

        data.put("records", records);
        data.put("total", total == null ? 0L : total);
        data.put("current", safeCurrent);
        data.put("size", safeSize);
        return data;
    }

    @Override
    public boolean markReadForCurrentUser(Long messageId) {
        if (messageId == null || messageId <= 0) {
            return false;
        }
        String user = currentUser();
        if (!StringUtils.hasText(user)) {
            return false;
        }

        String sql = "INSERT INTO system_message_read (message_id, reader, read_at) VALUES (?, ?, ?) "
                + "ON DUPLICATE KEY UPDATE read_at = VALUES(read_at)";
        int affected = jdbcTemplate.update(sql, messageId, user, LocalDateTime.now());
        return affected > 0;
    }

    @Override
    public int markAllReadForCurrentUser() {
        String user = currentUser();
        Set<String> roles = currentRoles();
        if (!StringUtils.hasText(user) || roles.isEmpty()) {
            return 0;
        }

        List<Object> args = new ArrayList<>();
        String roleClause = buildRoleClause(roles, args);
        args.add(user);
        args.add(user);

        String sql = "INSERT INTO system_message_read (message_id, reader, read_at) "
                + "SELECT m.id, ?, NOW() FROM system_message_notice m "
                + "WHERE m.is_deleted = 0 AND " + roleClause + " "
                + "AND NOT EXISTS (SELECT 1 FROM system_message_read r WHERE r.message_id = m.id AND r.reader = ?)";
        return jdbcTemplate.update(sql, args.toArray());
    }

    private String buildRoleClause(Set<String> roles, List<Object> args) {
        StringBuilder sb = new StringBuilder("(m.target_role = 'ALL'");
        for (String role : roles) {
            sb.append(" OR m.target_role = ?");
            args.add(role);
        }
        sb.append(")");
        return sb.toString();
    }

    private String currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null || "anonymousUser".equalsIgnoreCase(authentication.getName())) {
            return null;
        }
        return authentication.getName();
    }

    private Set<String> currentRoles() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getAuthorities() == null) {
            return new LinkedHashSet<>();
        }
        Set<String> roles = new LinkedHashSet<>();
        authentication.getAuthorities().forEach(authority -> {
            if (authority == null || authority.getAuthority() == null) {
                return;
            }
            String value = authority.getAuthority().trim();
            if (value.isEmpty()) {
                return;
            }
            roles.add(value);
            if (value.startsWith("ROLE_")) {
                roles.add(value.substring(5));
            }
        });
        return roles;
    }

    private String normalizeRole(String role) {
        String value = trimText(role);
        if (!StringUtils.hasText(value)) {
            return "ALL";
        }
        return value;
    }

    private String trimText(String text) {
        if (text == null) {
            return null;
        }
        String value = text.trim();
        return value.isEmpty() ? null : value;
    }

    private void normalizeLegacyPurchaseArrivalMessages() {
        try {
            String sql = "SELECT id, content, route_query_json FROM system_message_notice "
                    + "WHERE is_deleted = 0 AND biz_type = ? ORDER BY id DESC LIMIT 500";
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, BIZ_TYPE_PURCHASE_ARRIVAL);
            for (Map<String, Object> row : rows) {
                Long messageId = toLong(row.get("id"));
                if (messageId == null) {
                    continue;
                }
                String routeQueryJson = Objects.toString(row.get("route_query_json"), "");
                String normalized = buildNormalizedArrivalContent(routeQueryJson);
                if (!StringUtils.hasText(normalized)) {
                    continue;
                }
                String oldContent = Objects.toString(row.get("content"), "");
                if (!normalized.equals(oldContent)) {
                    jdbcTemplate.update("UPDATE system_message_notice SET title=?, content=?, updated_at=NOW() WHERE id=?",
                            "收货提醒", normalized, messageId);
                }
            }
        } catch (Exception ignored) {
        }
    }

    private String buildNormalizedArrivalContent(String routeQueryJson) {
        Long receiptId = extractLong(routeQueryJson, RECEIPT_ID_PATTERN);
        String itemIdText = extractText(routeQueryJson, ITEM_ID_PATTERN);
        Long itemId = toLong(itemIdText);
        if (receiptId == null) {
            return null;
        }

        Map<String, Object> receipt = jdbcTemplate.queryForMap(
                "SELECT supplier FROM purchase_receipts WHERE id = ? LIMIT 1", receiptId);
        String supplierCode = Objects.toString(receipt.get("supplier"), "");
        String supplierName = resolveSupplierDisplayName(supplierCode);

        if (itemId == null) {
            return supplierName + "的到货信息已更新，请查收。";
        }

        Map<String, Object> item = jdbcTemplate.queryForMap(
                "SELECT material_code, material_name, received_qty, unit, price_qty, stock_qty, purchase_qty, expected_qty "
                        + "FROM purchase_receipt_items WHERE id = ? LIMIT 1", itemId);
        String materialCode = Objects.toString(item.get("material_code"), "");
        String materialName = resolveMaterialDisplayName(materialCode, Objects.toString(item.get("material_name"), ""));
        String qtyText = resolveQtyText(item);
        return supplierName + "的" + materialName + "货到了" + qtyText + "，请查收。";
    }

    private String resolveSupplierDisplayName(String supplierCodeOrName) {
        if (!StringUtils.hasText(supplierCodeOrName)) {
            return "供应商";
        }
        String value = supplierCodeOrName.trim();
        String inner = extractInnerAlias(value);
        return StringUtils.hasText(inner) ? inner : value;
    }

    private String extractInnerAlias(String text) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        String value = text.trim();
        int left = Math.max(value.lastIndexOf('('), value.lastIndexOf('（'));
        int right = Math.max(value.lastIndexOf(')'), value.lastIndexOf('）'));
        if (left >= 0 && right > left) {
            return value.substring(left + 1, right).trim();
        }
        return "";
    }

    private String resolveMaterialDisplayName(String materialCode, String rawName) {
        String code = materialCode == null ? "" : materialCode.trim();
        String name = rawName == null ? "" : rawName.trim();
        if (StringUtils.hasText(name) && !name.equalsIgnoreCase(code)) {
            return name;
        }
        if (StringUtils.hasText(code)) {
            try {
                List<Map<String, Object>> materials = jdbcTemplate.queryForList(
                        "SELECT material_name FROM tape_raw_material WHERE material_code = ? LIMIT 1", code);
                if (!materials.isEmpty()) {
                    String dbName = Objects.toString(materials.get(0).get("material_name"), "").trim();
                    if (StringUtils.hasText(dbName)) {
                        return dbName;
                    }
                }
            } catch (Exception ignored) {
            }
        }
        if (StringUtils.hasText(name)) {
            return name;
        }
        return StringUtils.hasText(code) ? code : "物料";
    }

    private String resolveQtyText(Map<String, Object> itemRow) {
        BigDecimal qty = toBigDecimal(itemRow.get("received_qty"));
        if (qty == null || qty.compareTo(BigDecimal.ZERO) <= 0) {
            qty = firstPositive(
                    toBigDecimal(itemRow.get("price_qty")),
                    toBigDecimal(itemRow.get("stock_qty")),
                    toBigDecimal(itemRow.get("purchase_qty")),
                    toBigDecimal(itemRow.get("expected_qty"))
            );
        }
        String qtyText = qty == null ? "未知数量" : qty.stripTrailingZeros().toPlainString();
        String unit = Objects.toString(itemRow.get("unit"), "").trim();
        return StringUtils.hasText(unit) ? qtyText + unit : qtyText;
    }

    private BigDecimal firstPositive(BigDecimal... values) {
        if (values == null) {
            return null;
        }
        for (BigDecimal value : values) {
            if (value != null && value.compareTo(BigDecimal.ZERO) > 0) {
                return value;
            }
        }
        return null;
    }

    private Long extractLong(String text, Pattern pattern) {
        return toLong(extractText(text, pattern));
    }

    private String extractText(String text, Pattern pattern) {
        if (!StringUtils.hasText(text) || pattern == null) {
            return null;
        }
        Matcher matcher = pattern.matcher(text);
        if (matcher.find() && matcher.groupCount() >= 1) {
            return matcher.group(1);
        }
        return null;
    }

    private Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return Long.valueOf(String.valueOf(value).trim());
        } catch (Exception ignored) {
            return null;
        }
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return new BigDecimal(String.valueOf(value).trim());
        } catch (Exception ignored) {
            return null;
        }
    }
}
