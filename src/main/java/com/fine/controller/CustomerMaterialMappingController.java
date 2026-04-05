package com.fine.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fine.Dao.CustomerMaterialMappingMapper;
import com.fine.Utils.ResponseResult;
import com.fine.entity.CustomerMaterialMapping;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@RestController
@RequestMapping("/sales/customer-material-mapping")
@PreAuthorize("hasAnyAuthority('admin','sales','finance')")
public class CustomerMaterialMappingController {

    @Autowired
    private CustomerMaterialMappingMapper mappingMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @GetMapping("/page")
    public ResponseResult<?> page(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "20") Integer pageSize,
            @RequestParam(required = false) String customerCode,
            @RequestParam(required = false) String materialCode,
            @RequestParam(required = false) BigDecimal thickness,
            @RequestParam(required = false) BigDecimal width,
            @RequestParam(required = false) BigDecimal length,
            @RequestParam(required = false) BigDecimal customerThickness,
            @RequestParam(required = false) BigDecimal customerWidth,
            @RequestParam(required = false) BigDecimal customerLength,
            @RequestParam(required = false) Integer isActive
    ) {
        try {
            QueryWrapper<CustomerMaterialMapping> wrapper = new QueryWrapper<>();
            if (customerCode != null && !customerCode.trim().isEmpty()) {
                wrapper.eq("customer_code", customerCode.trim());
            }
            if (materialCode != null && !materialCode.trim().isEmpty()) {
                wrapper.eq("material_code", materialCode.trim());
            }
            if (thickness != null) {
                wrapper.eq("thickness", thickness);
            }
            if (width != null) {
                wrapper.eq("width", width);
            }
            if (length != null) {
                wrapper.eq("length", length);
            }
            if (customerThickness != null) {
                wrapper.eq("customer_thickness", customerThickness);
            }
            if (customerWidth != null) {
                wrapper.eq("customer_width", customerWidth);
            }
            if (customerLength != null) {
                wrapper.eq("customer_length", customerLength);
            }
            if (isActive != null) {
                wrapper.eq("is_active", isActive);
            }
            wrapper.orderByDesc("update_time").orderByDesc("id");

            Page<CustomerMaterialMapping> page = new Page<>(pageNum, pageSize);
            Page<CustomerMaterialMapping> result = mappingMapper.selectPage(page, wrapper);
            return ResponseResult.success(result);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseResult.error("查询客户物料映射失败: " + e.getMessage());
        }
    }

    @PostMapping
    @Transactional(rollbackFor = Exception.class)
    public ResponseResult<?> save(@RequestBody CustomerMaterialMapping body) {
        try {
            if (body == null) {
                return ResponseResult.error("请求体不能为空");
            }
            String customerCode = body.getCustomerCode() == null ? "" : body.getCustomerCode().trim();
            String materialCode = body.getMaterialCode() == null ? "" : body.getMaterialCode().trim();

            if (customerCode.isEmpty()) {
                return ResponseResult.error("customerCode不能为空");
            }
            if (materialCode.isEmpty()) {
                return ResponseResult.error("materialCode不能为空");
            }
            if (body.getThickness() == null) {
                return ResponseResult.error("thickness不能为空");
            }
            if (body.getWidth() == null) {
                return ResponseResult.error("width不能为空");
            }
            if (body.getLength() == null) {
                return ResponseResult.error("length不能为空");
            }

            LocalDateTime now = LocalDateTime.now();
            String user = body.getUpdateBy() == null || body.getUpdateBy().trim().isEmpty() ? "system" : body.getUpdateBy().trim();

            CustomerMaterialMapping entity;
            if (body.getId() != null) {
                entity = mappingMapper.selectById(body.getId());
                if (entity == null) {
                    return ResponseResult.error("记录不存在");
                }
            } else {
                QueryWrapper<CustomerMaterialMapping> dupWrapper = new QueryWrapper<>();
                dupWrapper.eq("customer_code", customerCode)
                        .eq("material_code", materialCode)
                    .eq("thickness", body.getThickness())
                    .eq("width", body.getWidth())
                    .eq("length", body.getLength())
                        .last("limit 1");
                entity = mappingMapper.selectOne(dupWrapper);
                if (entity == null) {
                    entity = new CustomerMaterialMapping();
                    entity.setCreateBy(user);
                    entity.setCreateTime(now);
                }
            }

            entity.setCustomerCode(customerCode);
            entity.setMaterialCode(materialCode);
            entity.setThickness(body.getThickness());
            entity.setWidth(body.getWidth());
            entity.setLength(body.getLength());
            entity.setCustomerThickness(body.getCustomerThickness() == null ? body.getThickness() : body.getCustomerThickness());
            entity.setCustomerWidth(body.getCustomerWidth() == null ? body.getWidth() : body.getCustomerWidth());
            entity.setCustomerLength(body.getCustomerLength() == null ? body.getLength() : body.getCustomerLength());
            entity.setCustomerMaterialCode(body.getCustomerMaterialCode() == null ? null : body.getCustomerMaterialCode().trim());
            entity.setCustomerMaterialName(body.getCustomerMaterialName() == null ? null : body.getCustomerMaterialName().trim());
            entity.setIsActive(body.getIsActive() == null ? 1 : body.getIsActive());
            entity.setRemark(body.getRemark());
            entity.setUpdateBy(user);
            entity.setUpdateTime(now);

            if (entity.getId() == null) {
                mappingMapper.insert(entity);
            } else {
                mappingMapper.updateById(entity);
            }
            return ResponseResult.success("保存成功", entity);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseResult.error("保存客户物料映射失败: " + e.getMessage());
        }
    }

    @PostMapping("/batch-save")
    @Transactional(rollbackFor = Exception.class)
    public ResponseResult<?> batchSave(@RequestBody List<CustomerMaterialMapping> list) {
        try {
            if (list == null || list.isEmpty()) {
                return ResponseResult.error("导入数据为空");
            }

            LocalDateTime now = LocalDateTime.now();
            int saved = 0;
            for (CustomerMaterialMapping body : list) {
                if (body == null) continue;

                String customerCode = body.getCustomerCode() == null ? "" : body.getCustomerCode().trim();
                String materialCode = body.getMaterialCode() == null ? "" : body.getMaterialCode().trim();
                if (customerCode.isEmpty() || materialCode.isEmpty() || body.getThickness() == null || body.getWidth() == null || body.getLength() == null) {
                    continue;
                }

                String user = body.getUpdateBy() == null || body.getUpdateBy().trim().isEmpty() ? "system" : body.getUpdateBy().trim();

                QueryWrapper<CustomerMaterialMapping> dupWrapper = new QueryWrapper<>();
                dupWrapper.eq("customer_code", customerCode)
                        .eq("material_code", materialCode)
                    .eq("thickness", body.getThickness())
                    .eq("width", body.getWidth())
                    .eq("length", body.getLength())
                        .last("limit 1");
                CustomerMaterialMapping entity = mappingMapper.selectOne(dupWrapper);
                if (entity == null) {
                    entity = new CustomerMaterialMapping();
                    entity.setCreateBy(user);
                    entity.setCreateTime(now);
                }

                entity.setCustomerCode(customerCode);
                entity.setMaterialCode(materialCode);
                entity.setThickness(body.getThickness());
                entity.setWidth(body.getWidth());
                entity.setLength(body.getLength());
                entity.setCustomerThickness(body.getCustomerThickness() == null ? body.getThickness() : body.getCustomerThickness());
                entity.setCustomerWidth(body.getCustomerWidth() == null ? body.getWidth() : body.getCustomerWidth());
                entity.setCustomerLength(body.getCustomerLength() == null ? body.getLength() : body.getCustomerLength());
                entity.setCustomerMaterialCode(body.getCustomerMaterialCode() == null ? null : body.getCustomerMaterialCode().trim());
                entity.setCustomerMaterialName(body.getCustomerMaterialName() == null ? null : body.getCustomerMaterialName().trim());
                entity.setIsActive(body.getIsActive() == null ? 1 : body.getIsActive());
                entity.setRemark(body.getRemark());
                entity.setUpdateBy(user);
                entity.setUpdateTime(now);

                if (entity.getId() == null) {
                    mappingMapper.insert(entity);
                } else {
                    mappingMapper.updateById(entity);
                }
                saved++;
            }

            return ResponseResult.success("批量保存成功", saved);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseResult.error("批量保存客户物料映射失败: " + e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    @Transactional(rollbackFor = Exception.class)
    public ResponseResult<?> delete(@PathVariable Long id) {
        try {
            if (id == null || id <= 0) {
                return ResponseResult.error("id不合法");
            }
            int affected = mappingMapper.deleteById(id);
            if (affected <= 0) {
                return ResponseResult.error("记录不存在或已删除");
            }
            return ResponseResult.success("删除成功", null);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseResult.error("删除客户物料映射失败: " + e.getMessage());
        }
    }

    /**
     * 打印匹配接口：优先“客户+料号+厚度+宽度+长度”精确匹配；
     * 未命中时回退“客户+料号+厚度”，再回退“客户+料号”最新启用配置。
     */
    @GetMapping("/match")
    @PreAuthorize("hasAnyAuthority('admin','sales','finance','production','packaging','plan','warehouse','quality','rd')")
    public ResponseResult<?> match(
            @RequestParam String customerCode,
            @RequestParam String materialCode,
            @RequestParam(required = false) BigDecimal thickness,
            @RequestParam(required = false) BigDecimal width,
            @RequestParam(required = false) BigDecimal length
    ) {
        try {
            String c = customerCode == null ? "" : customerCode.trim();
            String m = materialCode == null ? "" : materialCode.trim();
            if (c.isEmpty() || m.isEmpty()) {
                return ResponseResult.success(null);
            }

            CustomerMaterialMapping hit = null;
            if (thickness != null && width != null && length != null) {
                QueryWrapper<CustomerMaterialMapping> exact = new QueryWrapper<>();
                exact.eq("customer_code", c)
                        .eq("material_code", m)
                        .eq("thickness", thickness)
                        .eq("width", width)
                        .eq("length", length)
                        .eq("is_active", 1)
                        .orderByDesc("update_time")
                        .orderByDesc("id")
                        .last("limit 1");
                hit = mappingMapper.selectOne(exact);
            }

            if (hit == null && thickness != null) {
                QueryWrapper<CustomerMaterialMapping> byThickness = new QueryWrapper<>();
                byThickness.eq("customer_code", c)
                        .eq("material_code", m)
                        .eq("thickness", thickness)
                        .eq("is_active", 1)
                        .orderByDesc("update_time")
                        .orderByDesc("id")
                        .last("limit 1");
                hit = mappingMapper.selectOne(byThickness);
            }

            if (hit == null) {
                QueryWrapper<CustomerMaterialMapping> fallback = new QueryWrapper<>();
                fallback.eq("customer_code", c)
                        .eq("material_code", m)
                        .eq("is_active", 1)
                        .orderByDesc("update_time")
                        .orderByDesc("id")
                        .last("limit 1");
                hit = mappingMapper.selectOne(fallback);
            }

            return ResponseResult.success(hit);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseResult.error("匹配客户物料映射失败: " + e.getMessage());
        }
    }

    @GetMapping("/all")
    public ResponseResult<List<CustomerMaterialMapping>> all(@RequestParam(required = false) Integer isActive) {
        try {
            QueryWrapper<CustomerMaterialMapping> wrapper = new QueryWrapper<>();
            if (isActive != null) {
                wrapper.eq("is_active", isActive);
            }
            wrapper.orderByDesc("update_time").orderByDesc("id");
            return ResponseResult.success(mappingMapper.selectList(wrapper));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseResult.error("查询客户物料映射失败: " + e.getMessage());
        }
    }

    /**
     * 一次性历史初始化：从历史销售订单提取客户+料号+厚度+宽度+长度，写入映射表。
     * 规则：
     * 1) customer_material_code 默认=material_code
     * 2) customer_material_name 默认=material_name
     * 3) 若已存在同键(customer+material+thickness+width+length)，则仅在为空时补值，保留人工维护结果
     */
    @PostMapping("/init-from-history")
    @Transactional(rollbackFor = Exception.class)
    public ResponseResult<?> initFromHistory(@RequestBody(required = false) Map<String, Object> body) {
        try {
            String operator = body != null && body.get("operator") != null ? String.valueOf(body.get("operator")).trim() : "system";
            String customerCode = body != null && body.get("customerCode") != null ? String.valueOf(body.get("customerCode")).trim() : "";

            StringBuilder sql = new StringBuilder();
                sql.append("SELECT so.customer AS customer_code, soi.material_code, soi.material_name, soi.thickness, soi.width, soi.length ")
                    .append("FROM sales_order_items soi ")
                    .append("JOIN sales_orders so ON so.id = soi.order_id ")
                    .append("WHERE so.is_deleted = 0 AND soi.is_deleted = 0 ")
                    .append("AND so.customer IS NOT NULL AND TRIM(so.customer) <> '' ")
                    .append("AND soi.material_code IS NOT NULL AND TRIM(soi.material_code) <> '' ")
                    .append("AND soi.thickness IS NOT NULL ")
                    .append("AND soi.width IS NOT NULL ")
                    .append("AND soi.length IS NOT NULL ");

            List<Object> params = new ArrayList<>();
            if (!customerCode.isEmpty()) {
                sql.append("AND so.customer = ? ");
                params.add(customerCode);
            }
            sql.append("GROUP BY so.customer, soi.material_code, soi.material_name, soi.thickness, soi.width, soi.length");

            String querySql = Objects.requireNonNull(sql.toString(), "query sql");
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(querySql, params.toArray());

            int inserted = 0;
            int updated = 0;
            int skipped = 0;
            LocalDateTime now = LocalDateTime.now();

            for (Map<String, Object> row : rows) {
                String c = row.get("customer_code") == null ? "" : String.valueOf(row.get("customer_code")).trim();
                String m = row.get("material_code") == null ? "" : String.valueOf(row.get("material_code")).trim();
                String materialName = row.get("material_name") == null ? "" : String.valueOf(row.get("material_name")).trim();
                BigDecimal t = row.get("thickness") == null ? null : new BigDecimal(String.valueOf(row.get("thickness")));
                BigDecimal w = row.get("width") == null ? null : new BigDecimal(String.valueOf(row.get("width")));
                BigDecimal l = row.get("length") == null ? null : new BigDecimal(String.valueOf(row.get("length")));

                if (c.isEmpty() || m.isEmpty() || t == null || w == null || l == null) {
                    skipped++;
                    continue;
                }

                QueryWrapper<CustomerMaterialMapping> dupWrapper = new QueryWrapper<>();
                dupWrapper.eq("customer_code", c)
                        .eq("material_code", m)
                    .eq("thickness", t)
                    .eq("width", w)
                    .eq("length", l)
                        .last("limit 1");
                CustomerMaterialMapping entity = mappingMapper.selectOne(dupWrapper);

                if (entity == null) {
                    entity = new CustomerMaterialMapping();
                    entity.setCustomerCode(c);
                    entity.setMaterialCode(m);
                    entity.setThickness(t);
                    entity.setWidth(w);
                    entity.setLength(l);
                    entity.setCustomerThickness(t);
                    entity.setCustomerWidth(w);
                    entity.setCustomerLength(l);
                    entity.setCustomerMaterialCode(m);
                    entity.setCustomerMaterialName(materialName.isEmpty() ? null : materialName);
                    entity.setIsActive(1);
                    entity.setRemark("历史订单初始化");
                    entity.setCreateBy(operator);
                    entity.setCreateTime(now);
                    entity.setUpdateBy(operator);
                    entity.setUpdateTime(now);
                    mappingMapper.insert(entity);
                    inserted++;
                } else {
                    boolean changed = false;
                    if (entity.getCustomerMaterialCode() == null || entity.getCustomerMaterialCode().trim().isEmpty()) {
                        entity.setCustomerMaterialCode(m);
                        changed = true;
                    }
                    if ((entity.getCustomerMaterialName() == null || entity.getCustomerMaterialName().trim().isEmpty()) && !materialName.isEmpty()) {
                        entity.setCustomerMaterialName(materialName);
                        changed = true;
                    }
                    if (entity.getCustomerThickness() == null) {
                        entity.setCustomerThickness(t);
                        changed = true;
                    }
                    if (entity.getCustomerWidth() == null) {
                        entity.setCustomerWidth(w);
                        changed = true;
                    }
                    if (entity.getCustomerLength() == null) {
                        entity.setCustomerLength(l);
                        changed = true;
                    }
                    if (entity.getIsActive() == null) {
                        entity.setIsActive(1);
                        changed = true;
                    }
                    if (changed) {
                        entity.setUpdateBy(operator);
                        entity.setUpdateTime(now);
                        if (entity.getRemark() == null || entity.getRemark().trim().isEmpty()) {
                            entity.setRemark("历史订单初始化补全");
                        }
                        mappingMapper.updateById(entity);
                        updated++;
                    } else {
                        skipped++;
                    }
                }
            }

            Map<String, Object> result = new HashMap<>();
            result.put("completed", true);
            result.put("total", rows.size());
            result.put("inserted", inserted);
            result.put("updated", updated);
            result.put("skipped", skipped);
            result.put("customerCode", customerCode);
            return ResponseResult.success("历史初始化完成", result);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseResult.error("历史初始化失败: " + e.getMessage());
        }
    }
}
