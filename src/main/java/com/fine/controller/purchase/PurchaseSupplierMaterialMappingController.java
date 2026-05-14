package com.fine.controller.purchase;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fine.Dao.purchase.PurchaseSupplierMaterialMappingMapper;
import com.fine.Utils.ResponseResult;
import com.fine.entity.PurchaseSupplierMaterialMapping;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import javax.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/purchase/supplier-material-mapping")
@PreAuthorize("hasAnyAuthority('admin','purchase')")
public class PurchaseSupplierMaterialMappingController {

    @Autowired
    private PurchaseSupplierMaterialMappingMapper mappingMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @PostConstruct
    public void initTableIfAbsent() {
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS purchase_supplier_material_mapping ("
                + "id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,"
                + "supplier_code VARCHAR(64) NOT NULL,"
                + "material_code VARCHAR(128) NOT NULL,"
                + "material_name VARCHAR(255) NULL,"
                + "supplier_material_code VARCHAR(128) NULL,"
                + "supplier_material_name VARCHAR(255) NULL,"
                + "is_active TINYINT NOT NULL DEFAULT 1,"
                + "remark VARCHAR(500) NULL,"
                + "create_by VARCHAR(64) NULL,"
                + "create_time DATETIME NULL,"
                + "update_by VARCHAR(64) NULL,"
                + "update_time DATETIME NULL,"
                + "UNIQUE KEY uk_supplier_material (supplier_code, material_code),"
                + "KEY idx_supplier_code (supplier_code),"
                + "KEY idx_material_code (material_code),"
                + "KEY idx_update_time (update_time)"
                + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
    }

    @GetMapping("/page")
    public ResponseResult<?> page(@RequestParam(defaultValue = "1") Integer pageNum,
                                  @RequestParam(defaultValue = "20") Integer pageSize,
                                  @RequestParam(required = false) String supplierCode,
                                  @RequestParam(required = false) String supplierKeyword,
                                  @RequestParam(required = false) String materialCode,
                                  @RequestParam(required = false) String materialName,
                                  @RequestParam(required = false) String supplierMaterialCode,
                                  @RequestParam(required = false) String supplierMaterialName,
                                  @RequestParam(required = false) Integer isActive) {
        try {
            QueryWrapper<PurchaseSupplierMaterialMapping> wrapper = buildQueryWrapper(
                    supplierCode,
                    supplierKeyword,
                    materialCode,
                    materialName,
                    supplierMaterialCode,
                    supplierMaterialName,
                    isActive
            );
            wrapper.orderByDesc("update_time").orderByDesc("id");
            Page<PurchaseSupplierMaterialMapping> page = new Page<>(pageNum, pageSize);
            return ResponseResult.success(mappingMapper.selectPage(page, wrapper));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseResult.error("查询供应商物料映射失败: " + e.getMessage());
        }
    }

    @GetMapping("/all")
    public ResponseResult<?> all(@RequestParam(required = false) String supplierCode,
                                 @RequestParam(required = false) String supplierKeyword,
                                 @RequestParam(required = false) String materialCode,
                                 @RequestParam(required = false) Integer isActive) {
        try {
            QueryWrapper<PurchaseSupplierMaterialMapping> wrapper = buildQueryWrapper(
                    supplierCode,
                    supplierKeyword,
                    materialCode,
                    null,
                    null,
                    null,
                    isActive
            );
            wrapper.orderByDesc("update_time").orderByDesc("id");
            return ResponseResult.success(mappingMapper.selectList(wrapper));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseResult.error("查询供应商物料映射失败: " + e.getMessage());
        }
    }

    @GetMapping("/match")
    public ResponseResult<?> match(@RequestParam String supplierCode,
                                   @RequestParam String materialCode) {
        try {
            String supplier = trimToEmpty(supplierCode);
            String material = trimToEmpty(materialCode);
            if (supplier.isEmpty() || material.isEmpty()) {
                return ResponseResult.error("supplierCode/materialCode 不能为空");
            }
            QueryWrapper<PurchaseSupplierMaterialMapping> wrapper = new QueryWrapper<>();
            wrapper.eq("supplier_code", supplier)
                    .eq("material_code", material)
                    .eq("is_active", 1)
                    .last("limit 1");
            return ResponseResult.success(mappingMapper.selectOne(wrapper));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseResult.error("匹配供应商物料映射失败: " + e.getMessage());
        }
    }

    @PostMapping
    @Transactional(rollbackFor = Exception.class)
    public ResponseResult<?> save(@RequestBody PurchaseSupplierMaterialMapping body) {
        try {
            if (body == null) {
                return ResponseResult.error("请求体不能为空");
            }
            String supplierCode = trimToEmpty(body.getSupplierCode());
            String materialCode = trimToEmpty(body.getMaterialCode());
            if (supplierCode.isEmpty()) {
                return ResponseResult.error("supplierCode 不能为空");
            }
            if (materialCode.isEmpty()) {
                return ResponseResult.error("materialCode 不能为空");
            }

            LocalDateTime now = LocalDateTime.now();
            String operator = trimToEmpty(body.getUpdateBy());
            if (operator.isEmpty()) {
                operator = "system";
            }

            PurchaseSupplierMaterialMapping entity;
            if (body.getId() != null) {
                entity = mappingMapper.selectById(body.getId());
                if (entity == null) {
                    return ResponseResult.error("记录不存在");
                }
            } else {
                QueryWrapper<PurchaseSupplierMaterialMapping> dup = new QueryWrapper<>();
                dup.eq("supplier_code", supplierCode)
                        .eq("material_code", materialCode)
                        .last("limit 1");
                entity = mappingMapper.selectOne(dup);
                if (entity == null) {
                    entity = new PurchaseSupplierMaterialMapping();
                    entity.setCreateBy(operator);
                    entity.setCreateTime(now);
                }
            }

            entity.setSupplierCode(supplierCode);
            entity.setMaterialCode(materialCode);
            entity.setMaterialName(trimToNull(body.getMaterialName()));
            entity.setSupplierMaterialCode(trimToNull(body.getSupplierMaterialCode()));
            entity.setSupplierMaterialName(trimToNull(body.getSupplierMaterialName()));
            entity.setIsActive(body.getIsActive() == null ? 1 : body.getIsActive());
            entity.setRemark(trimToNull(body.getRemark()));
            entity.setUpdateBy(operator);
            entity.setUpdateTime(now);

            if (entity.getId() == null) {
                mappingMapper.insert(entity);
            } else {
                mappingMapper.updateById(entity);
            }

            return ResponseResult.success("保存成功", entity);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseResult.error("保存供应商物料映射失败: " + e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public ResponseResult<?> delete(@PathVariable Long id) {
        try {
            if (id == null || id <= 0) {
                return ResponseResult.error("id 非法");
            }
            PurchaseSupplierMaterialMapping entity = mappingMapper.selectById(id);
            if (entity == null) {
                return ResponseResult.error("记录不存在");
            }
            mappingMapper.deleteById(id);
            return ResponseResult.success("删除成功");
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseResult.error("删除供应商物料映射失败: " + e.getMessage());
        }
    }

    private QueryWrapper<PurchaseSupplierMaterialMapping> buildQueryWrapper(String supplierCode,
                                                                            String supplierKeyword,
                                                                            String materialCode,
                                                                            String materialName,
                                                                            String supplierMaterialCode,
                                                                            String supplierMaterialName,
                                                                            Integer isActive) {
        QueryWrapper<PurchaseSupplierMaterialMapping> wrapper = new QueryWrapper<>();
        String supplierCodeTrimmed = trimToEmpty(supplierCode);
        if (!supplierCodeTrimmed.isEmpty()) {
            wrapper.eq("supplier_code", supplierCodeTrimmed);
        }

        String supplierKeywordTrimmed = trimToEmpty(supplierKeyword);
        if (!supplierKeywordTrimmed.isEmpty()) {
            String keyword = "%" + supplierKeywordTrimmed + "%";
            List<String> matchedSupplierCodes = jdbcTemplate.queryForList(
                    "SELECT supplier_code FROM purchase_suppliers "
                            + "WHERE is_deleted = 0 "
                            + "AND (supplier_code LIKE ? OR supplier_name LIKE ? OR short_name LIKE ?)",
                    String.class,
                    keyword,
                    keyword,
                    keyword
            );
            if (matchedSupplierCodes == null || matchedSupplierCodes.isEmpty()) {
                matchedSupplierCodes = new ArrayList<>();
                matchedSupplierCodes.add("__NO_MATCH__");
            }
            wrapper.in("supplier_code", matchedSupplierCodes);
        }

        String materialCodeTrimmed = trimToEmpty(materialCode);
        if (!materialCodeTrimmed.isEmpty()) {
            wrapper.eq("material_code", materialCodeTrimmed);
        }

        String materialNameTrimmed = trimToEmpty(materialName);
        if (!materialNameTrimmed.isEmpty()) {
            wrapper.like("material_name", materialNameTrimmed);
        }

        String supplierMaterialCodeTrimmed = trimToEmpty(supplierMaterialCode);
        if (!supplierMaterialCodeTrimmed.isEmpty()) {
            wrapper.like("supplier_material_code", supplierMaterialCodeTrimmed);
        }

        String supplierMaterialNameTrimmed = trimToEmpty(supplierMaterialName);
        if (!supplierMaterialNameTrimmed.isEmpty()) {
            wrapper.like("supplier_material_name", supplierMaterialNameTrimmed);
        }

        if (isActive != null) {
            wrapper.eq("is_active", isActive);
        }

        return wrapper;
    }

    private String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private String trimToNull(String value) {
        String text = trimToEmpty(value);
        return text.isEmpty() ? null : text;
    }
}
