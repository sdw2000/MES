package com.fine.controller.quality;

import com.fine.Utils.ResponseResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import javax.annotation.PostConstruct;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/api/quality/outbound-mapping")
@PreAuthorize("hasAnyAuthority('admin','quality','production')")
public class QualityOutboundMappingController {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @PostConstruct
    public void ensureTable() {
        try {
            String ddl = "CREATE TABLE IF NOT EXISTS quality_outbound_mapping ("
                + "id BIGINT PRIMARY KEY AUTO_INCREMENT,"
                + "customer_code VARCHAR(64) NOT NULL COMMENT '客户代码',"
                + "material_code VARCHAR(128) NOT NULL COMMENT '物料代码',"
                + "width_min DECIMAL(18,3) NULL COMMENT '宽度下限(mm)',"
                + "width_max DECIMAL(18,3) NULL COMMENT '宽度上限(mm)',"
                + "color_range VARCHAR(512) NULL COMMENT '颜色范围(字符串集合,逗号分隔)',"
                + "length_tol_min DECIMAL(18,3) NULL COMMENT '长度误差下限',"
                + "length_tol_max DECIMAL(18,3) NULL COMMENT '长度误差上限',"
                + "misalign_min DECIMAL(18,3) NULL COMMENT '整卷错位下限',"
                + "misalign_max DECIMAL(18,3) NULL COMMENT '整卷错位上限',"
                + "enabled TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用:1是0否',"
                + "remark VARCHAR(255) NULL COMMENT '备注',"
                + "created_by VARCHAR(64) NULL,"
                + "created_at DATETIME NULL,"
                + "updated_by VARCHAR(64) NULL,"
                + "updated_at DATETIME NULL"
                + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='出货检测映射(客户+物料维度)';";
            jdbcTemplate.execute(ddl);

            Integer idxCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM information_schema.STATISTICS "
                    + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'quality_outbound_mapping' AND INDEX_NAME = 'idx_qom_customer_material'",
                Integer.class
            );
            if (idxCount == null || idxCount == 0) {
            jdbcTemplate.execute("CREATE INDEX idx_qom_customer_material ON quality_outbound_mapping(customer_code, material_code, enabled)");
            }
        } catch (Exception ignored) {
        }
    }

    @GetMapping("/match")
    public ResponseResult<?> match(@RequestParam String customerCode,
                                   @RequestParam String materialCode) {
        try {
            String c = safe(customerCode);
            String m = safe(materialCode);
            // 匹配优先级：
            // 1) 客户+物料 精确
            // 2) 客户精确 + 物料通用(空)
            // 3) 客户通用(空) + 物料精确
            // 4) 客户通用(空) + 物料通用(空)
            String sql = "SELECT id, customer_code AS customerCode, material_code AS materialCode, width_min AS widthMin, width_max AS widthMax, color_range AS colorRange, "
                    + "length_tol_min AS lengthTolMin, length_tol_max AS lengthTolMax, misalign_min AS misalignMin, misalign_max AS misalignMax, enabled, remark, updated_at AS updatedAt "
                    + "FROM quality_outbound_mapping "
                    + "WHERE enabled=1 "
                    + "AND (customer_code=? OR customer_code='' OR customer_code IS NULL) "
                    + "AND (material_code=? OR material_code='' OR material_code IS NULL) "
                    + "ORDER BY "
                    + "  CASE "
                    + "    WHEN customer_code=? AND material_code=? THEN 400 "
                    + "    WHEN customer_code=? AND (material_code='' OR material_code IS NULL) THEN 300 "
                    + "    WHEN (customer_code='' OR customer_code IS NULL) AND material_code=? THEN 200 "
                    + "    WHEN (customer_code='' OR customer_code IS NULL) AND (material_code='' OR material_code IS NULL) THEN 100 "
                    + "    ELSE 0 END DESC, "
                    + "  updated_at DESC, id DESC LIMIT 1";
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, c, m, c, m, c, m);
            if (rows == null || rows.isEmpty()) {
                return ResponseResult.success(null);
            }
            return ResponseResult.success(rows.get(0));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseResult.error("匹配检测映射失败: " + e.getMessage());
        }
    }

    @GetMapping("/page")
    public ResponseResult<?> page(@RequestParam(defaultValue = "1") Integer pageNum,
                                  @RequestParam(defaultValue = "20") Integer pageSize,
                                  @RequestParam(required = false) String customerCode,
                                  @RequestParam(required = false) String materialCode,
                                  @RequestParam(required = false) Integer enabled) {
        try {
            StringBuilder where = new StringBuilder(" WHERE 1=1 ");
            List<Object> args = new ArrayList<>();
            if (!safe(customerCode).isEmpty()) {
                where.append(" AND customer_code=? ");
                args.add(safe(customerCode));
            }
            if (!safe(materialCode).isEmpty()) {
                where.append(" AND material_code LIKE ? ");
                args.add("%" + safe(materialCode) + "%");
            }
            if (enabled != null) {
                where.append(" AND enabled=? ");
                args.add(enabled);
            }

            Integer total = jdbcTemplate.queryForObject(
                    "SELECT COUNT(1) FROM quality_outbound_mapping" + where,
                    Integer.class,
                    args.toArray()
            );

            int offset = Math.max(0, (pageNum - 1) * pageSize);
            List<Object> listArgs = new ArrayList<>(args);
            listArgs.add(offset);
            listArgs.add(pageSize);

                String listSql = "SELECT id, customer_code AS customerCode, material_code AS materialCode, width_min AS widthMin, width_max AS widthMax, color_range AS colorRange, "
                    + "length_tol_min AS lengthTolMin, length_tol_max AS lengthTolMax, misalign_min AS misalignMin, misalign_max AS misalignMax, enabled, remark, created_by AS createdBy, created_at AS createdAt, updated_by AS updatedBy, updated_at AS updatedAt "
                    + "FROM quality_outbound_mapping " + where
                    + " ORDER BY updated_at DESC, id DESC LIMIT ?, ?";

            List<Map<String, Object>> records = jdbcTemplate.queryForList(listSql, listArgs.toArray());

            Map<String, Object> page = new HashMap<>();
            page.put("records", records);
            page.put("total", total == null ? 0 : total);
            page.put("pageNum", pageNum);
            page.put("pageSize", pageSize);
            return ResponseResult.success(page);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseResult.error("查询检测映射失败: " + e.getMessage());
        }
    }

    @PostMapping("/save")
    public ResponseResult<?> save(@RequestBody Map<String, Object> body) {
        try {
            String customerCode = safe(body.get("customerCode"));
            String materialCode = safe(body.get("materialCode"));
            // 允许 customerCode/materialCode 为空：为空代表通用映射

            Long id = toLong(body.get("id"));
            BigDecimal widthMin = toDecimal(body.get("widthMin"));
            BigDecimal widthMax = toDecimal(body.get("widthMax"));
            BigDecimal lengthTolMin = toDecimal(body.get("lengthTolMin"));
            BigDecimal lengthTolMax = toDecimal(body.get("lengthTolMax"));
            BigDecimal misalignMin = toDecimal(body.get("misalignMin"));
            BigDecimal misalignMax = toDecimal(body.get("misalignMax"));
            String colorRange = safe(body.get("colorRange"));
            Integer enabled = toInt(body.get("enabled"));
            if (enabled == null) enabled = 1;
            String remark = safe(body.get("remark"));
            String operator = safe(body.get("updatedBy"));
            if (operator.isEmpty()) operator = "system";
            LocalDateTime now = LocalDateTime.now();

            if (id != null && id > 0) {
                String update = "UPDATE quality_outbound_mapping SET customer_code=?, material_code=?, width_min=?, width_max=?, color_range=?, "
                        + "length_tol_min=?, length_tol_max=?, misalign_min=?, misalign_max=?, enabled=?, remark=?, updated_by=?, updated_at=? "
                        + "WHERE id=?";
                jdbcTemplate.update(update,
                        customerCode, materialCode, widthMin, widthMax, colorRange,
                        lengthTolMin, lengthTolMax, misalignMin, misalignMax,
                        enabled, remark, operator, now, id);
            } else {
                String insert = "INSERT INTO quality_outbound_mapping(customer_code, material_code, width_min, width_max, color_range, "
                        + "length_tol_min, length_tol_max, misalign_min, misalign_max, enabled, remark, created_by, created_at, updated_by, updated_at) "
                        + "VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
                jdbcTemplate.update(insert,
                        customerCode, materialCode, widthMin, widthMax, colorRange,
                        lengthTolMin, lengthTolMax, misalignMin, misalignMax,
                        enabled, remark, operator, now, operator, now);
            }
            return ResponseResult.success("保存成功");
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseResult.error("保存检测映射失败: " + e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public ResponseResult<?> delete(@PathVariable Long id) {
        try {
            int affected = jdbcTemplate.update("DELETE FROM quality_outbound_mapping WHERE id=?", id);
            if (affected <= 0) return ResponseResult.error("记录不存在");
            return ResponseResult.success("删除成功");
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseResult.error("删除检测映射失败: " + e.getMessage());
        }
    }

    private String safe(Object v) {
        return v == null ? "" : String.valueOf(v).trim();
    }

    private Integer toInt(Object v) {
        try {
            return v == null ? null : Integer.parseInt(String.valueOf(v).trim());
        } catch (Exception e) {
            return null;
        }
    }

    private Long toLong(Object v) {
        try {
            return v == null ? null : Long.parseLong(String.valueOf(v).trim());
        } catch (Exception e) {
            return null;
        }
    }

    private BigDecimal toDecimal(Object v) {
        try {
            String s = safe(v);
            return s.isEmpty() ? null : new BigDecimal(s);
        } catch (Exception e) {
            return null;
        }
    }
}
