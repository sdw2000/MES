package com.fine.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fine.Dao.LabelPrintTemplateConfigMapper;
import com.fine.Utils.ResponseResult;
import com.fine.entity.LabelPrintTemplateConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/basic-data/label-print-config")
@CrossOrigin
public class LabelPrintTemplateConfigController {

    private static final String BIZ_TYPE_SALES_CONTRACT_TEMPLATE = "sales_contract_template";
    private static final String BIZ_TYPE_SALES_CONTRACT_DEFAULT = "sales_contract_default";
    private static final String BIZ_TYPE_DELIVERY_NOTICE_TEMPLATE = "delivery_notice_template";
    private static final String BIZ_TYPE_DELIVERY_NOTICE_DEFAULT = "delivery_notice_default";

    @Autowired
    private LabelPrintTemplateConfigMapper configMapper;

    @GetMapping("/list")
    public ResponseResult<List<LabelPrintTemplateConfig>> list(
            @RequestParam(required = false) String bizType,
            @RequestParam(required = false) String customerCode,
            @RequestParam(required = false) Integer isActive
    ) {
        try {
            QueryWrapper<LabelPrintTemplateConfig> wrapper = new QueryWrapper<>();
            if (bizType != null && !bizType.trim().isEmpty()) {
                wrapper.like("biz_type", bizType.trim());
            }
            if (customerCode != null && !customerCode.trim().isEmpty()) {
                wrapper.eq("customer_code", customerCode.trim());
            }
            if (isActive != null) {
                wrapper.eq("is_active", isActive);
            }
            wrapper.orderByAsc("sort_no").orderByDesc("update_time");
            return ResponseResult.success(configMapper.selectList(wrapper));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseResult.error("查询标签打印配置失败: " + e.getMessage());
        }
    }

    @GetMapping("/active")
    public ResponseResult<List<LabelPrintTemplateConfig>> activeList() {
        try {
            QueryWrapper<LabelPrintTemplateConfig> wrapper = new QueryWrapper<>();
            wrapper.eq("is_active", 1)
                    .orderByAsc("sort_no")
                    .orderByDesc("update_time");
            return ResponseResult.success(configMapper.selectList(wrapper));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseResult.error("查询启用标签打印配置失败: " + e.getMessage());
        }
    }

    @PostMapping("/batch-save")
    @Transactional(rollbackFor = Exception.class)
    public ResponseResult<List<LabelPrintTemplateConfig>> batchSave(@RequestBody(required = false) List<LabelPrintTemplateConfig> configs) {
        try {
            QueryWrapper<LabelPrintTemplateConfig> deleteWrapper = new QueryWrapper<>();
            configMapper.delete(deleteWrapper);

            List<LabelPrintTemplateConfig> saved = new ArrayList<>();
            LocalDateTime now = LocalDateTime.now();
            if (configs != null) {
                int sortNo = 1;
                for (LabelPrintTemplateConfig item : configs) {
                    if (item == null) {
                        continue;
                    }
                    if (item.getBizType() == null || item.getBizType().trim().isEmpty()) {
                        continue;
                    }
                    if (item.getTemplateKey() == null || item.getTemplateKey().trim().isEmpty()) {
                        continue;
                    }

                    LabelPrintTemplateConfig entity = new LabelPrintTemplateConfig();
                    entity.setBizType(item.getBizType() == null ? "" : item.getBizType().trim());
                    entity.setSceneName(item.getSceneName() == null ? "" : item.getSceneName().trim());
                    entity.setTemplateKey(item.getTemplateKey() == null ? "" : item.getTemplateKey().trim());
                    entity.setCustomerCode(item.getCustomerCode() == null ? null : item.getCustomerCode().trim());
                    entity.setSortNo(item.getSortNo() != null ? item.getSortNo() : sortNo++);
                    entity.setIsActive(item.getIsActive() != null ? item.getIsActive() : 1);
                    entity.setRemark(item.getRemark());
                    entity.setCreateBy(item.getCreateBy() == null ? "system" : item.getCreateBy());
                    entity.setCreateTime(now);
                    entity.setUpdateBy(item.getUpdateBy() == null ? entity.getCreateBy() : item.getUpdateBy());
                    entity.setUpdateTime(now);
                    configMapper.insert(entity);
                    saved.add(entity);
                }
            }
            return ResponseResult.success("保存成功", saved);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseResult.error("保存标签打印配置失败: " + e.getMessage());
        }
    }

    /**
     * 二期：销售合同打印模板（云端）
     * 仅返回模板定义（不含客户默认映射）
     */
    @GetMapping("/sales-contract/templates")
    public ResponseResult<List<LabelPrintTemplateConfig>> salesContractTemplates() {
        try {
            QueryWrapper<LabelPrintTemplateConfig> wrapper = new QueryWrapper<>();
            wrapper.eq("biz_type", BIZ_TYPE_SALES_CONTRACT_TEMPLATE)
                    .eq("is_active", 1)
                    .orderByAsc("sort_no")
                    .orderByDesc("update_time");
            return ResponseResult.success(configMapper.selectList(wrapper));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseResult.error("查询销售合同打印模板失败: " + e.getMessage());
        }
    }

    /**
     * 二期：查询客户默认模板
     */
    @GetMapping("/sales-contract/default")
    public ResponseResult<Map<String, Object>> getSalesContractDefault(@RequestParam String customerCode) {
        try {
            QueryWrapper<LabelPrintTemplateConfig> wrapper = new QueryWrapper<>();
            wrapper.eq("biz_type", BIZ_TYPE_SALES_CONTRACT_DEFAULT)
                    .eq("customer_code", customerCode)
                    .eq("is_active", 1)
                    .last("limit 1");

            LabelPrintTemplateConfig found = configMapper.selectOne(wrapper);
            Map<String, Object> result = new HashMap<>();
            result.put("customerCode", customerCode);
            result.put("templateKey", found == null ? null : found.getTemplateKey());
            return ResponseResult.success(result);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseResult.error("查询客户默认模板失败: " + e.getMessage());
        }
    }

    /**
     * 二期：保存客户默认模板
     */
    @PostMapping("/sales-contract/default")
    @Transactional(rollbackFor = Exception.class)
    public ResponseResult<Map<String, Object>> saveSalesContractDefault(@RequestBody Map<String, String> body) {
        try {
            String customerCode = body == null ? null : body.get("customerCode");
            String templateKey = body == null ? null : body.get("templateKey");
            String operator = body == null ? null : body.get("operator");

            if (customerCode == null || customerCode.trim().isEmpty()) {
                return ResponseResult.error("customerCode不能为空");
            }
            if (templateKey == null || templateKey.trim().isEmpty()) {
                return ResponseResult.error("templateKey不能为空");
            }

            QueryWrapper<LabelPrintTemplateConfig> wrapper = new QueryWrapper<>();
            wrapper.eq("biz_type", BIZ_TYPE_SALES_CONTRACT_DEFAULT)
                    .eq("customer_code", customerCode.trim())
                    .last("limit 1");
            LabelPrintTemplateConfig existing = configMapper.selectOne(wrapper);

            LocalDateTime now = LocalDateTime.now();
            String user = (operator == null || operator.trim().isEmpty()) ? "system" : operator.trim();
            if (existing == null) {
                LabelPrintTemplateConfig entity = new LabelPrintTemplateConfig();
                entity.setBizType(BIZ_TYPE_SALES_CONTRACT_DEFAULT);
                entity.setSceneName("客户默认模板");
                entity.setTemplateKey(templateKey.trim());
                entity.setCustomerCode(customerCode.trim());
                entity.setSortNo(1);
                entity.setIsActive(1);
                entity.setRemark(null);
                entity.setCreateBy(user);
                entity.setCreateTime(now);
                entity.setUpdateBy(user);
                entity.setUpdateTime(now);
                configMapper.insert(entity);
            } else {
                existing.setTemplateKey(templateKey.trim());
                existing.setIsActive(1);
                existing.setUpdateBy(user);
                existing.setUpdateTime(now);
                configMapper.updateById(existing);
            }

            Map<String, Object> result = new HashMap<>();
            result.put("customerCode", customerCode.trim());
            result.put("templateKey", templateKey.trim());
            return ResponseResult.success("保存成功", result);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseResult.error("保存客户默认模板失败: " + e.getMessage());
        }
    }

    /**
     * 发货通知：打印模板列表（云端）
     */
    @GetMapping("/delivery-notice/templates")
    public ResponseResult<List<LabelPrintTemplateConfig>> deliveryNoticeTemplates() {
        try {
            QueryWrapper<LabelPrintTemplateConfig> wrapper = new QueryWrapper<>();
            wrapper.eq("biz_type", BIZ_TYPE_DELIVERY_NOTICE_TEMPLATE)
                    .eq("is_active", 1)
                    .orderByAsc("sort_no")
                    .orderByDesc("update_time");
            return ResponseResult.success(configMapper.selectList(wrapper));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseResult.error("查询发货通知打印模板失败: " + e.getMessage());
        }
    }

    /**
     * 发货通知：模板列表（含停用项，供管理页编辑）
     */
    @GetMapping("/delivery-notice/templates/all")
    public ResponseResult<List<LabelPrintTemplateConfig>> deliveryNoticeTemplatesAll() {
        try {
            QueryWrapper<LabelPrintTemplateConfig> wrapper = new QueryWrapper<>();
            wrapper.eq("biz_type", BIZ_TYPE_DELIVERY_NOTICE_TEMPLATE)
                    .orderByAsc("sort_no")
                    .orderByDesc("update_time");
            return ResponseResult.success(configMapper.selectList(wrapper));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseResult.error("查询发货通知模板(全部)失败: " + e.getMessage());
        }
    }

    /**
     * 发货通知：新增/编辑模板定义
     */
    @PostMapping("/delivery-notice/template")
    @Transactional(rollbackFor = Exception.class)
    public ResponseResult<Map<String, Object>> saveDeliveryNoticeTemplate(@RequestBody Map<String, Object> body) {
        try {
            String templateKey = body == null ? null : toSafeString(body.get("templateKey"));
            String sceneName = body == null ? null : toSafeString(body.get("sceneName"));
            Integer sortNo = body == null ? null : toSafeInteger(body.get("sortNo"));
            Integer isActive = body == null ? null : toSafeInteger(body.get("isActive"));
            String operator = body == null ? null : toSafeString(body.get("operator"));
            String remark = buildDeliveryTemplateRemark(body);

            if (templateKey == null || templateKey.trim().isEmpty()) {
                return ResponseResult.error("templateKey不能为空");
            }

            QueryWrapper<LabelPrintTemplateConfig> wrapper = new QueryWrapper<>();
            wrapper.eq("biz_type", BIZ_TYPE_DELIVERY_NOTICE_TEMPLATE)
                    .eq("template_key", templateKey.trim())
                    .last("limit 1");
            LabelPrintTemplateConfig existing = configMapper.selectOne(wrapper);

            LocalDateTime now = LocalDateTime.now();
            String user = (operator == null || operator.trim().isEmpty()) ? "system" : operator.trim();
            if (existing == null) {
                LabelPrintTemplateConfig entity = new LabelPrintTemplateConfig();
                entity.setBizType(BIZ_TYPE_DELIVERY_NOTICE_TEMPLATE);
                entity.setSceneName((sceneName == null || sceneName.trim().isEmpty()) ? templateKey.trim() : sceneName.trim());
                entity.setTemplateKey(templateKey.trim());
                entity.setCustomerCode(null);
                entity.setSortNo(sortNo == null ? 999 : sortNo);
                entity.setIsActive(isActive == null ? 1 : (isActive == 0 ? 0 : 1));
                entity.setRemark(remark);
                entity.setCreateBy(user);
                entity.setCreateTime(now);
                entity.setUpdateBy(user);
                entity.setUpdateTime(now);
                configMapper.insert(entity);
            } else {
                existing.setSceneName((sceneName == null || sceneName.trim().isEmpty()) ? existing.getTemplateKey() : sceneName.trim());
                existing.setSortNo(sortNo == null ? existing.getSortNo() : sortNo);
                existing.setIsActive(isActive == null ? existing.getIsActive() : (isActive == 0 ? 0 : 1));
                existing.setRemark(remark);
                existing.setUpdateBy(user);
                existing.setUpdateTime(now);
                configMapper.updateById(existing);
            }

            Map<String, Object> result = new HashMap<>();
            result.put("templateKey", templateKey.trim());
            result.put("sceneName", (sceneName == null || sceneName.trim().isEmpty()) ? templateKey.trim() : sceneName.trim());
            result.put("sortNo", sortNo == null ? 999 : sortNo);
            result.put("isActive", isActive == null ? 1 : (isActive == 0 ? 0 : 1));
            result.put("remark", remark);
            return ResponseResult.success("保存成功", result);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseResult.error("保存发货通知模板失败: " + e.getMessage());
        }
    }

    /**
     * 发货通知：删除模板定义
     */
    @DeleteMapping("/delivery-notice/template")
    @Transactional(rollbackFor = Exception.class)
    public ResponseResult<String> deleteDeliveryNoticeTemplate(@RequestParam String templateKey) {
        try {
            String key = templateKey == null ? "" : templateKey.trim();
            if (key.isEmpty()) {
                return ResponseResult.error("templateKey不能为空");
            }

            QueryWrapper<LabelPrintTemplateConfig> wrapper = new QueryWrapper<>();
            wrapper.eq("biz_type", BIZ_TYPE_DELIVERY_NOTICE_TEMPLATE)
                    .eq("template_key", key);
            configMapper.delete(wrapper);
            return ResponseResult.success("删除成功", key);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseResult.error("删除发货通知模板失败: " + e.getMessage());
        }
    }

    /**
     * 发货通知：查询客户默认模板
     */
    @GetMapping("/delivery-notice/default")
    public ResponseResult<Map<String, Object>> getDeliveryNoticeDefault(@RequestParam String customerCode) {
        try {
            QueryWrapper<LabelPrintTemplateConfig> wrapper = new QueryWrapper<>();
            wrapper.eq("biz_type", BIZ_TYPE_DELIVERY_NOTICE_DEFAULT)
                    .eq("customer_code", customerCode)
                    .eq("is_active", 1)
                    .last("limit 1");

            LabelPrintTemplateConfig found = configMapper.selectOne(wrapper);
            Map<String, Object> result = new HashMap<>();
            result.put("customerCode", customerCode);
            result.put("templateKey", found == null ? null : found.getTemplateKey());
            return ResponseResult.success(result);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseResult.error("查询发货通知客户默认模板失败: " + e.getMessage());
        }
    }

    /**
     * 发货通知：保存客户默认模板
     */
    @PostMapping("/delivery-notice/default")
    @Transactional(rollbackFor = Exception.class)
    public ResponseResult<Map<String, Object>> saveDeliveryNoticeDefault(@RequestBody Map<String, String> body) {
        try {
            String customerCode = body == null ? null : body.get("customerCode");
            String templateKey = body == null ? null : body.get("templateKey");
            String operator = body == null ? null : body.get("operator");

            if (customerCode == null || customerCode.trim().isEmpty()) {
                return ResponseResult.error("customerCode不能为空");
            }
            if (templateKey == null || templateKey.trim().isEmpty()) {
                return ResponseResult.error("templateKey不能为空");
            }

            QueryWrapper<LabelPrintTemplateConfig> wrapper = new QueryWrapper<>();
            wrapper.eq("biz_type", BIZ_TYPE_DELIVERY_NOTICE_DEFAULT)
                    .eq("customer_code", customerCode.trim())
                    .last("limit 1");
            LabelPrintTemplateConfig existing = configMapper.selectOne(wrapper);

            LocalDateTime now = LocalDateTime.now();
            String user = (operator == null || operator.trim().isEmpty()) ? "system" : operator.trim();
            if (existing == null) {
                LabelPrintTemplateConfig entity = new LabelPrintTemplateConfig();
                entity.setBizType(BIZ_TYPE_DELIVERY_NOTICE_DEFAULT);
                entity.setSceneName("发货通知客户默认模板");
                entity.setTemplateKey(templateKey.trim());
                entity.setCustomerCode(customerCode.trim());
                entity.setSortNo(1);
                entity.setIsActive(1);
                entity.setRemark(null);
                entity.setCreateBy(user);
                entity.setCreateTime(now);
                entity.setUpdateBy(user);
                entity.setUpdateTime(now);
                configMapper.insert(entity);
            } else {
                existing.setTemplateKey(templateKey.trim());
                existing.setIsActive(1);
                existing.setUpdateBy(user);
                existing.setUpdateTime(now);
                configMapper.updateById(existing);
            }

            Map<String, Object> result = new HashMap<>();
            result.put("customerCode", customerCode.trim());
            result.put("templateKey", templateKey.trim());
            return ResponseResult.success("保存成功", result);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseResult.error("保存发货通知客户默认模板失败: " + e.getMessage());
        }
    }

    private String toSafeString(Object value) {
        return value == null ? null : String.valueOf(value).trim();
    }

    private Integer toSafeInteger(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return Integer.parseInt(String.valueOf(value).trim());
        } catch (Exception ignore) {
            return null;
        }
    }

    private String buildDeliveryTemplateRemark(Map<String, Object> body) {
        if (body == null) {
            return "{\"compact\":false,\"showCarrierPhone\":true,\"showCustomerOrderNo\":true,\"showItemArea\":true,\"showItemBox\":true,\"showItemRemark\":true,\"showFooterNotes\":true}";
        }
        Boolean compact = parseBooleanLike(body.get("compact"), false);
        Boolean showCarrierPhone = parseBooleanLike(body.get("showCarrierPhone"), true);
        Boolean showCustomerOrderNo = parseBooleanLike(body.get("showCustomerOrderNo"), true);
        Boolean showItemArea = parseBooleanLike(body.get("showItemArea"), true);
        Boolean showItemBox = parseBooleanLike(body.get("showItemBox"), true);
        Boolean showItemRemark = parseBooleanLike(body.get("showItemRemark"), true);
        Boolean showFooterNotes = parseBooleanLike(body.get("showFooterNotes"), !compact);

        return String.format(
                "{\"compact\":%s,\"showCarrierPhone\":%s,\"showCustomerOrderNo\":%s,\"showItemArea\":%s,\"showItemBox\":%s,\"showItemRemark\":%s,\"showFooterNotes\":%s}",
                compact,
                showCarrierPhone,
                showCustomerOrderNo,
                showItemArea,
                showItemBox,
                showItemRemark,
                showFooterNotes
        );
    }

    private Boolean parseBooleanLike(Object value, boolean defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        String s = String.valueOf(value).trim();
        if (s.isEmpty()) {
            return defaultValue;
        }
        return "1".equals(s) || "true".equalsIgnoreCase(s) || "yes".equalsIgnoreCase(s) || "y".equalsIgnoreCase(s);
    }
}
