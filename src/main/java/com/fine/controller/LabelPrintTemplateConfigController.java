package com.fine.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fine.Dao.CustomerMaterialMappingMapper;
import com.fine.Dao.LabelPrintTemplateConfigMapper;
import com.fine.Utils.RedisCache;
import com.fine.Utils.ResponseResult;
import com.fine.entity.CustomerMaterialMapping;
import com.fine.entity.LabelPrintTemplateConfig;
import com.fine.modle.DeliveryNotice;
import com.fine.modle.DeliveryNoticeItem;
import com.fine.service.DeliveryNoticeService;
import com.fine.service.SalesOrderService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/basic-data/label-print-config")
@CrossOrigin
public class LabelPrintTemplateConfigController {

    private static final String BIZ_TYPE_SALES_CONTRACT_TEMPLATE = "sales_contract_template";
    private static final String BIZ_TYPE_SALES_CONTRACT_DEFAULT = "sales_contract_default";
    private static final String BIZ_TYPE_DELIVERY_NOTICE_TEMPLATE = "delivery_notice_template";
    private static final String BIZ_TYPE_DELIVERY_NOTICE_DEFAULT = "delivery_notice_default";
        private static final String LIST_SCOPE_LABEL_RULE = "label-rule";
        private static final String LIST_SCOPE_LABEL_GATEWAY = "label-gateway";
        private static final String BIZ_TYPE_GATEWAY_BASE_CONFIG = "GATEWAY_BASE_CONFIG";
        private static final String BIZ_TYPE_GATEWAY_TEMPLATE_MAPPING = "GATEWAY_TEMPLATE_MAPPING";
        private static final String DELIVERY_TEMPLATE_DEFAULT_REMARK = "{\"compact\":false,\"showCarrierPhone\":true,\"showCustomerOrderNo\":true,\"showItemArea\":true,\"showItemBox\":true,\"showItemRemark\":true,\"showFooterNotes\":true}";
        private static final List<String> RESERVED_TEMPLATE_BIZ_TYPES = Arrays.asList(
            BIZ_TYPE_SALES_CONTRACT_TEMPLATE,
            BIZ_TYPE_SALES_CONTRACT_DEFAULT,
            BIZ_TYPE_DELIVERY_NOTICE_TEMPLATE,
            BIZ_TYPE_DELIVERY_NOTICE_DEFAULT
        );
        private static final List<String> GATEWAY_TEMPLATE_BIZ_TYPES = Arrays.asList(
            BIZ_TYPE_GATEWAY_BASE_CONFIG,
            BIZ_TYPE_GATEWAY_TEMPLATE_MAPPING
        );
        private static final List<String> EXCLUDED_BIZ_TYPES_FOR_LABEL_RULE = Arrays.asList(
            BIZ_TYPE_SALES_CONTRACT_TEMPLATE,
            BIZ_TYPE_SALES_CONTRACT_DEFAULT,
            BIZ_TYPE_DELIVERY_NOTICE_TEMPLATE,
            BIZ_TYPE_DELIVERY_NOTICE_DEFAULT,
            BIZ_TYPE_GATEWAY_BASE_CONFIG,
            BIZ_TYPE_GATEWAY_TEMPLATE_MAPPING
        );
    private static final String TEMPLATE_PREVIEW_SAMPLE_DATA_KEY = "label_print:template_preview_sample_data";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private LabelPrintTemplateConfigMapper configMapper;

    @Autowired
    private SalesOrderService salesOrderService;

    @Autowired
    private DeliveryNoticeService deliveryNoticeService;

    @Autowired
    private CustomerMaterialMappingMapper customerMaterialMappingMapper;

    @Autowired
    private RedisCache redisCache;

    @GetMapping("/list")
    public ResponseResult<List<LabelPrintTemplateConfig>> list(
            @RequestParam(required = false) String bizType,
            @RequestParam(required = false) String customerCode,
            @RequestParam(required = false) Integer isActive,
            @RequestParam(required = false) String scope
    ) {
        try {
            QueryWrapper<LabelPrintTemplateConfig> wrapper = new QueryWrapper<>();
            if (isLabelRuleScope(scope)) {
                wrapper.notIn("biz_type", EXCLUDED_BIZ_TYPES_FOR_LABEL_RULE);
            }
            if (isLabelGatewayScope(scope)) {
                wrapper.in("biz_type", GATEWAY_TEMPLATE_BIZ_TYPES);
            }
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
    public ResponseResult<List<LabelPrintTemplateConfig>> batchSave(
            @RequestBody(required = false) List<LabelPrintTemplateConfig> configs,
            @RequestParam(required = false) String scope) {
        try {
            QueryWrapper<LabelPrintTemplateConfig> deleteWrapper = new QueryWrapper<>();
            boolean labelRuleScope = isLabelRuleScope(scope);
            boolean labelGatewayScope = isLabelGatewayScope(scope);
            if (labelRuleScope) {
                // 仅覆盖“标签规则”域，避免误删送货单/合同模板配置
                deleteWrapper.notIn("biz_type", EXCLUDED_BIZ_TYPES_FOR_LABEL_RULE);
            }
            if (labelGatewayScope) {
                // 仅覆盖“网关与模板映射”域，避免影响其他配置
                deleteWrapper.in("biz_type", GATEWAY_TEMPLATE_BIZ_TYPES);
            }
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
                    if (labelRuleScope && EXCLUDED_BIZ_TYPES_FOR_LABEL_RULE.contains(item.getBizType().trim())) {
                        // 标签规则域禁止写入其他模板域
                        continue;
                    }
                    if (labelGatewayScope && !GATEWAY_TEMPLATE_BIZ_TYPES.contains(item.getBizType().trim())) {
                        // 网关配置域仅允许写入网关相关类型
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
            List<LabelPrintTemplateConfig> rows = findCustomerDefaultRows(BIZ_TYPE_SALES_CONTRACT_DEFAULT, customerCode, true);
            LabelPrintTemplateConfig found = pickLatestByUpdateTime(rows);
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

                List<LabelPrintTemplateConfig> rows = findCustomerDefaultRows(BIZ_TYPE_SALES_CONTRACT_DEFAULT, customerCode.trim(), false);
                LabelPrintTemplateConfig existing = pickLatestByUpdateTime(rows);

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
                existing = entity;
            } else {
                existing.setBizType(BIZ_TYPE_SALES_CONTRACT_DEFAULT);
                existing.setSceneName("客户默认模板");
                existing.setTemplateKey(templateKey.trim());
                existing.setCustomerCode(customerCode.trim());
                existing.setIsActive(1);
                existing.setUpdateBy(user);
                existing.setUpdateTime(now);
                configMapper.updateById(existing);
            }

            // 保存后自动去重（防止历史/外部写入导致同客户多条默认模板）
            deleteCustomerDefaultDuplicates(BIZ_TYPE_SALES_CONTRACT_DEFAULT, customerCode.trim(), existing == null ? null : existing.getId());

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
            ensureDeliveryTemplatesReferencedByDefaultsExist();
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
            ensureDeliveryTemplatesReferencedByDefaultsExist();
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
            List<LabelPrintTemplateConfig> rows = findCustomerDefaultRows(BIZ_TYPE_DELIVERY_NOTICE_DEFAULT, customerCode, true);
            LabelPrintTemplateConfig found = pickLatestByUpdateTime(rows);
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

                List<LabelPrintTemplateConfig> rows = findCustomerDefaultRows(BIZ_TYPE_DELIVERY_NOTICE_DEFAULT, customerCode.trim(), false);
                LabelPrintTemplateConfig existing = pickLatestByUpdateTime(rows);

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
                existing = entity;
            } else {
                existing.setBizType(BIZ_TYPE_DELIVERY_NOTICE_DEFAULT);
                existing.setSceneName("发货通知客户默认模板");
                existing.setTemplateKey(templateKey.trim());
                existing.setCustomerCode(customerCode.trim());
                existing.setIsActive(1);
                existing.setUpdateBy(user);
                existing.setUpdateTime(now);
                configMapper.updateById(existing);
            }

            // 保存后自动去重（防止历史/外部写入导致同客户多条默认模板）
            deleteCustomerDefaultDuplicates(BIZ_TYPE_DELIVERY_NOTICE_DEFAULT, customerCode.trim(), existing == null ? null : existing.getId());

            Map<String, Object> result = new HashMap<>();
            result.put("customerCode", customerCode.trim());
            result.put("templateKey", templateKey.trim());
            return ResponseResult.success("保存成功", result);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseResult.error("保存发货通知客户默认模板失败: " + e.getMessage());
        }
    }

    /**
     * 打印模板数据查询（按模板键返回可渲染数据）
     * 示例：
     * - 销售合同：/template-data?bizType=sales_contract_template&templateKey=contract_standard_v1&orderNo=SO001
     * - 发货通知：/template-data?bizType=delivery_notice_template&templateKey=delivery_standard_v1&noticeId=123
     */
    @GetMapping("/template-data")
    public ResponseResult<Map<String, Object>> getTemplateData(
            @RequestParam String bizType,
            @RequestParam String templateKey,
            @RequestParam(required = false) String orderNo,
            @RequestParam(required = false) Long noticeId) {
        try {
            String safeBizType = bizType == null ? "" : bizType.trim();
            String safeTemplateKey = templateKey == null ? "" : templateKey.trim();
            if (safeBizType.isEmpty()) {
                return ResponseResult.error("bizType不能为空");
            }
            if (safeTemplateKey.isEmpty()) {
                return ResponseResult.error("templateKey不能为空");
            }

            Map<String, Object> result = new HashMap<>();
            result.put("bizType", safeBizType);
            result.put("templateKey", safeTemplateKey);

            if (BIZ_TYPE_SALES_CONTRACT_TEMPLATE.equalsIgnoreCase(safeBizType)) {
                if (orderNo == null || orderNo.trim().isEmpty()) {
                    return ResponseResult.error("sales_contract_template场景下，orderNo不能为空");
                }
                ResponseResult<?> detail = salesOrderService.getOrderByOrderNo(orderNo.trim());
                Integer code = detail == null ? null : detail.getCode();
                if (detail == null || !((code != null && code == 200) || (code != null && code == 20000))) {
                    return ResponseResult.error("查询销售订单打印数据失败: " + (detail == null ? "未知错误" : detail.getMsg()));
                }
                result.put("source", "sales_order");
                result.put("data", detail.getData());
                return ResponseResult.success(result);
            }

            if (BIZ_TYPE_DELIVERY_NOTICE_TEMPLATE.equalsIgnoreCase(safeBizType)) {
                if (noticeId == null) {
                    return ResponseResult.error("delivery_notice_template场景下，noticeId不能为空");
                }
                DeliveryNotice notice = deliveryNoticeService.getDeliveryNoticeDetail(noticeId);
                if (notice == null) {
                    return ResponseResult.error("发货通知不存在");
                }

                int totalQty = 0;
                int totalBox = 0;
                BigDecimal totalArea = BigDecimal.ZERO;
                List<DeliveryNoticeItem> items = notice.getItems() == null ? new ArrayList<>() : notice.getItems();
                for (DeliveryNoticeItem item : items) {
                    if (item == null) {
                        continue;
                    }
                    totalQty += item.getQuantity() == null ? 0 : item.getQuantity();
                    totalBox += item.getBoxCount() == null ? 0 : item.getBoxCount();
                    totalArea = totalArea.add(item.getAreaSize() == null ? BigDecimal.ZERO : item.getAreaSize());
                }

                Map<String, Object> summary = new HashMap<>();
                summary.put("totalQty", totalQty);
                summary.put("totalBox", totalBox);
                summary.put("totalArea", totalArea);

                result.put("source", "delivery_notice");
                result.put("data", notice);
                result.put("summary", summary);
                return ResponseResult.success(result);
            }

            return ResponseResult.error("暂不支持的bizType: " + safeBizType);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseResult.error("查询打印模板数据失败: " + e.getMessage());
        }
    }

    /**
     * 每个模板独立查询接口：销售合同
     */
    @GetMapping("/template-data/sales-contract/{templateKey}")
    public ResponseResult<Map<String, Object>> getSalesContractTemplateData(
            @PathVariable String templateKey,
            @RequestParam String orderNo) {
        return getTemplateData(BIZ_TYPE_SALES_CONTRACT_TEMPLATE, templateKey, orderNo, null);
    }

    /**
     * 每个模板独立查询接口：发货通知
     * 规则：按 客户 + 料号 + 三规格(厚/宽/长) 匹配 customer_material_mapping
     */
    @GetMapping("/template-data/delivery-notice/{templateKey}")
    public ResponseResult<Map<String, Object>> getDeliveryNoticeTemplateData(
            @PathVariable String templateKey,
            @RequestParam Long noticeId) {
        try {
            String safeTemplateKey = templateKey == null ? "" : templateKey.trim();
            if (safeTemplateKey.isEmpty()) {
                return ResponseResult.error("templateKey不能为空");
            }
            if (noticeId == null) {
                return ResponseResult.error("noticeId不能为空");
            }

            DeliveryNotice notice = deliveryNoticeService.getDeliveryNoticeDetail(noticeId);
            if (notice == null) {
                return ResponseResult.error("发货通知不存在");
            }

            applyCustomerMaterialMappingForDeliveryPrint(notice);

            int totalQty = 0;
            int totalBox = 0;
            BigDecimal totalArea = BigDecimal.ZERO;
            List<DeliveryNoticeItem> items = notice.getItems() == null ? new ArrayList<>() : notice.getItems();
            for (DeliveryNoticeItem item : items) {
                if (item == null) {
                    continue;
                }
                totalQty += item.getQuantity() == null ? 0 : item.getQuantity();
                totalBox += item.getBoxCount() == null ? 0 : item.getBoxCount();
                totalArea = totalArea.add(item.getAreaSize() == null ? BigDecimal.ZERO : item.getAreaSize());
            }

            Map<String, Object> summary = new HashMap<>();
            summary.put("totalQty", totalQty);
            summary.put("totalBox", totalBox);
            summary.put("totalArea", totalArea);

            Map<String, Object> result = new HashMap<>();
            result.put("bizType", BIZ_TYPE_DELIVERY_NOTICE_TEMPLATE);
            result.put("templateKey", safeTemplateKey);
            result.put("source", "delivery_notice");
            result.put("data", notice);
            result.put("summary", summary);
            return ResponseResult.success(result);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseResult.error("查询发货通知模板数据失败: " + e.getMessage());
        }
    }

    /**
     * 模板预览示例数据：读取共享配置（Redis）
     */
    @GetMapping("/template-preview-samples")
    public ResponseResult<Map<String, Object>> getTemplatePreviewSamples() {
        try {
            Map<String, Object> result = readTemplatePreviewSamplesFromCache();
            return ResponseResult.success(result);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseResult.error("读取模板预览示例数据失败: " + e.getMessage());
        }
    }

    /**
     * 模板预览示例数据：保存共享配置（Redis）
     */
    @PostMapping("/template-preview-samples")
    @Transactional(rollbackFor = Exception.class)
    public ResponseResult<Map<String, Object>> saveTemplatePreviewSamples(@RequestBody(required = false) Map<String, Object> body) {
        try {
            Map<String, Object> data = body == null ? new HashMap<>() : new HashMap<>(body);
            String json = objectMapper.writeValueAsString(data);
            redisCache.setCacheObject(TEMPLATE_PREVIEW_SAMPLE_DATA_KEY, Objects.requireNonNull(json));
            return ResponseResult.success("保存成功", data);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseResult.error("保存模板预览示例数据失败: " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readTemplatePreviewSamplesFromCache() {
        try {
            String json = redisCache.getCacheObject(TEMPLATE_PREVIEW_SAMPLE_DATA_KEY);
            if (json == null || json.trim().isEmpty()) {
                return new HashMap<>();
            }
            Map<String, Object> data = (Map<String, Object>) objectMapper.readValue(json, Map.class);
            return data == null ? new HashMap<>() : new HashMap<>(data);
        } catch (Exception e) {
            return new HashMap<>();
        }
    }

    private void applyCustomerMaterialMappingForDeliveryPrint(DeliveryNotice notice) {
        if (notice == null || notice.getItems() == null || notice.getItems().isEmpty()) {
            return;
        }
        String customerCode = notice.getCustomer() == null ? "" : notice.getCustomer().trim();
        if (customerCode.isEmpty()) {
            return;
        }

        for (DeliveryNoticeItem item : notice.getItems()) {
            if (item == null) {
                continue;
            }
            String materialCode = item.getMaterialCode() == null ? "" : item.getMaterialCode().trim();
            if (materialCode.isEmpty()) {
                continue;
            }

            BigDecimal[] dims = parseSpecDimensions(item.getSpec());
            BigDecimal thickness = dims[0];
            BigDecimal width = dims[1];
            BigDecimal length = dims[2];

            CustomerMaterialMapping matched = findBestMapping(customerCode, materialCode, thickness, width, length, item.getRemark());
            if (matched == null) {
                continue;
            }

            // 物料代码栏：我司料号
            if (notBlank(matched.getMaterialCode())) {
                item.setMaterialCode(matched.getMaterialCode().trim());
            }
            // 产品名称：客户名称
            if (notBlank(matched.getCustomerMaterialName())) {
                item.setMaterialName(matched.getCustomerMaterialName().trim());
            }
            // 规格栏：客户规格
            if (notBlank(matched.getCustomerSpec())) {
                item.setSpec(matched.getCustomerSpec().trim());
            } else {
                item.setSpec(buildSpecText(
                        matched.getCustomerThickness() == null ? thickness : matched.getCustomerThickness(),
                        matched.getCustomerWidth() == null ? width : matched.getCustomerWidth(),
                        matched.getCustomerLength() == null ? length : matched.getCustomerLength(),
                        item.getSpec()
                ));
            }
            // 物料编号：客户物料编号
            if (notBlank(matched.getCustomerMaterialCode())) {
                item.setCustomerMaterialNo(matched.getCustomerMaterialCode().trim());
            }
            // 备注：优先客户映射备注；为空时回退客户物料代码
            if (notBlank(matched.getRemark())) {
                item.setRemark(matched.getRemark().trim());
            } else if (!notBlank(item.getRemark()) && notBlank(matched.getCustomerMaterialCode())) {
                item.setRemark(matched.getCustomerMaterialCode().trim());
            }
        }
    }

    private CustomerMaterialMapping findBestMapping(String customerCode,
                                                    String materialCode,
                                                    BigDecimal thickness,
                                                    BigDecimal width,
                                                    BigDecimal length,
                                                    String customerMaterialCodeHint) {
        QueryWrapper<CustomerMaterialMapping> qw = new QueryWrapper<>();
        qw.eq("customer_code", customerCode)
                .eq("is_active", 1)
                .orderByDesc("update_time")
                .orderByDesc("id");

        List<CustomerMaterialMapping> candidates = customerMaterialMappingMapper.selectList(qw);
        if (candidates == null || candidates.isEmpty()) {
            return null;
        }

        String materialNorm = normalizeMaterialCode(materialCode);
        String hint = customerMaterialCodeHint == null ? "" : customerMaterialCodeHint.trim();

        // 1) 先严格锁定“同客户 + 同料号(归一化后完全相等)”
        List<CustomerMaterialMapping> materialMatched = candidates.stream()
                .filter(m -> {
                    String mapMaterialNorm = normalizeMaterialCode(m == null ? null : m.getMaterialCode());
                    return notBlank(materialNorm) && notBlank(mapMaterialNorm) && materialNorm.equals(mapMaterialNorm);
                })
                .collect(Collectors.toList());
        if (materialMatched.isEmpty()) {
            return null;
        }

        // 2) 若有客户物料编号提示，仅作为同料号内的优先条件（不允许跨料号命中）
        if (notBlank(hint)) {
            List<CustomerMaterialMapping> hinted = materialMatched.stream()
                    .filter(m -> notBlank(m.getCustomerMaterialCode()) && hint.equals(m.getCustomerMaterialCode().trim()))
                    .collect(Collectors.toList());
            CustomerMaterialMapping bestHinted = pickMostPreciseMapping(hinted, thickness, width, length);
            if (bestHinted != null) {
                return bestHinted;
            }
        }

        // 3) 无提示或提示未命中时，在同料号内按规格精确分层匹配
        return pickMostPreciseMapping(materialMatched, thickness, width, length);
    }

    private CustomerMaterialMapping pickMostPreciseMapping(List<CustomerMaterialMapping> pool,
                                                           BigDecimal thickness,
                                                           BigDecimal width,
                                                           BigDecimal length) {
        if (pool == null || pool.isEmpty()) {
            return null;
        }

        // 层级A：原始规格三维精确
        if (thickness != null && width != null && length != null) {
            List<CustomerMaterialMapping> exact = pool.stream()
                    .filter(m -> numberEq(thickness, m.getThickness())
                            && numberEq(width, m.getWidth())
                            && numberEq(length, m.getLength()))
                    .collect(Collectors.toList());
            CustomerMaterialMapping hit = pickNewest(exact);
            if (hit != null) {
                return hit;
            }

            // 层级B：客户规格三维精确（兼容历史数据）
            List<CustomerMaterialMapping> exactCustomer = pool.stream()
                    .filter(m -> numberEq(thickness, m.getCustomerThickness())
                            && numberEq(width, m.getCustomerWidth())
                            && numberEq(length, m.getCustomerLength()))
                    .collect(Collectors.toList());
            hit = pickNewest(exactCustomer);
            if (hit != null) {
                return hit;
            }
        }

        // 层级C：厚度精确
        if (thickness != null) {
            List<CustomerMaterialMapping> byThickness = pool.stream()
                    .filter(m -> numberEq(thickness, m.getThickness()))
                    .collect(Collectors.toList());
            CustomerMaterialMapping hit = pickNewest(byThickness);
            if (hit != null) {
                return hit;
            }

            List<CustomerMaterialMapping> byCustomerThickness = pool.stream()
                    .filter(m -> numberEq(thickness, m.getCustomerThickness()))
                    .collect(Collectors.toList());
            hit = pickNewest(byCustomerThickness);
            if (hit != null) {
                return hit;
            }
        }

        // 层级D：同客户同料号兜底，取最新
        return pickNewest(pool);
    }

    private CustomerMaterialMapping pickNewest(List<CustomerMaterialMapping> list) {
        if (list == null || list.isEmpty()) {
            return null;
        }
        return list.stream().max(
                Comparator
                        .comparing(CustomerMaterialMapping::getUpdateTime,
                                Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(CustomerMaterialMapping::getId,
                                Comparator.nullsLast(Comparator.naturalOrder()))
        ).orElse(null);
    }

    private String normalizeMaterialCode(String code) {
        if (!notBlank(code)) {
            return "";
        }
        return code.trim().toUpperCase().replaceAll("[\\s\\-_]", "");
    }

    private boolean numberEq(BigDecimal a, BigDecimal b) {
        if (a == null || b == null) {
            return false;
        }
        return a.subtract(b).abs().compareTo(new BigDecimal("0.0001")) < 0;
    }

    private String buildSpecText(BigDecimal thickness, BigDecimal width, BigDecimal length, String fallback) {
        if (thickness == null || width == null || length == null) {
            return fallback;
        }
        return fmt(thickness) + "μm*" + fmt(width) + "mm*" + fmt(length) + "m";
    }

    private String fmt(BigDecimal value) {
        if (value == null) {
            return "";
        }
        return value.stripTrailingZeros().toPlainString();
    }

    private BigDecimal[] parseSpecDimensions(String spec) {
        BigDecimal[] out = new BigDecimal[] {null, null, null};
        if (!notBlank(spec)) {
            return out;
        }
        Matcher matcher = Pattern.compile("\\d+(?:\\.\\d+)?").matcher(spec);
        List<BigDecimal> nums = new ArrayList<>();
        while (matcher.find()) {
            try {
                nums.add(new BigDecimal(matcher.group()));
            } catch (Exception ignore) {
            }
        }
        if (nums.size() >= 3) {
            out[0] = nums.get(0);
            out[1] = nums.get(1);
            out[2] = nums.get(2);
        }
        return out;
    }

    private boolean notBlank(String v) {
        return v != null && !v.trim().isEmpty();
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
            return DELIVERY_TEMPLATE_DEFAULT_REMARK;
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

    private boolean isLabelRuleScope(String scope) {
        return LIST_SCOPE_LABEL_RULE.equalsIgnoreCase(scope == null ? "" : scope.trim());
    }

    private boolean isLabelGatewayScope(String scope) {
        return LIST_SCOPE_LABEL_GATEWAY.equalsIgnoreCase(scope == null ? "" : scope.trim());
    }

    private void ensureDeliveryTemplatesReferencedByDefaultsExist() {
        QueryWrapper<LabelPrintTemplateConfig> templateQw = new QueryWrapper<>();
        templateQw.eq("biz_type", BIZ_TYPE_DELIVERY_NOTICE_TEMPLATE);
        List<LabelPrintTemplateConfig> templates = configMapper.selectList(templateQw);

        Set<String> existsKeys = new LinkedHashSet<>();
        int maxSortNo = 0;
        if (templates != null) {
            for (LabelPrintTemplateConfig one : templates) {
                if (one == null) {
                    continue;
                }
                if (one.getTemplateKey() != null && !one.getTemplateKey().trim().isEmpty()) {
                    existsKeys.add(one.getTemplateKey().trim());
                }
                if (one.getSortNo() != null && one.getSortNo() > maxSortNo) {
                    maxSortNo = one.getSortNo();
                }
            }
        }

        QueryWrapper<LabelPrintTemplateConfig> defaultQw = new QueryWrapper<>();
        defaultQw.eq("biz_type", BIZ_TYPE_DELIVERY_NOTICE_DEFAULT)
                .eq("is_active", 1)
                .isNotNull("template_key")
                .orderByDesc("update_time");
        List<LabelPrintTemplateConfig> defaults = configMapper.selectList(defaultQw);
        if (defaults == null || defaults.isEmpty()) {
            return;
        }

        Set<String> missingTemplateKeys = new LinkedHashSet<>();
        for (LabelPrintTemplateConfig one : defaults) {
            if (one == null || one.getTemplateKey() == null || one.getTemplateKey().trim().isEmpty()) {
                continue;
            }
            String key = one.getTemplateKey().trim();
            if (!existsKeys.contains(key)) {
                missingTemplateKeys.add(key);
            }
        }
        if (missingTemplateKeys.isEmpty()) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        for (String key : missingTemplateKeys) {
            LabelPrintTemplateConfig entity = new LabelPrintTemplateConfig();
            entity.setBizType(BIZ_TYPE_DELIVERY_NOTICE_TEMPLATE);
            entity.setSceneName(guessDeliveryTemplateSceneName(key));
            entity.setTemplateKey(key);
            entity.setCustomerCode(null);
            entity.setSortNo(++maxSortNo);
            entity.setIsActive(1);
            entity.setRemark(DELIVERY_TEMPLATE_DEFAULT_REMARK);
            entity.setCreateBy("system");
            entity.setCreateTime(now);
            entity.setUpdateBy("system");
            entity.setUpdateTime(now);
            configMapper.insert(entity);
        }
    }

    private String guessDeliveryTemplateSceneName(String templateKey) {
        String key = templateKey == null ? "" : templateKey.trim();
        if (key.isEmpty()) {
            return "发货通知模板";
        }
        String lower = key.toLowerCase();
        if (lower.contains("wanbao") || key.contains("万宝")) {
            return "万宝发货通知模板";
        }
        if (lower.contains("liyuan") || key.contains("力源")) {
            return "力源发货通知模板";
        }
        if (lower.contains("yiwei") || key.contains("亿纬")) {
            return "亿纬发货通知模板";
        }
        if (lower.contains("standard") || key.contains("标准")) {
            return "标准发货通知模板";
        }
        if (lower.contains("simple") || key.contains("简版")) {
            return "简版发货通知模板";
        }
        return key;
    }

    private List<LabelPrintTemplateConfig> findCustomerDefaultRows(String bizType, String customerCode, boolean activeOnly) {
        if (customerCode == null || customerCode.trim().isEmpty()) {
            return new ArrayList<>();
        }
        QueryWrapper<LabelPrintTemplateConfig> wrapper = new QueryWrapper<>();
        wrapper.apply("LOWER(biz_type) = LOWER('" + bizType + "')")
                .eq("customer_code", customerCode.trim());
        if (activeOnly) {
            wrapper.eq("is_active", 1);
        }
        wrapper.orderByDesc("update_time").orderByDesc("id");
        List<LabelPrintTemplateConfig> rows = configMapper.selectList(wrapper);
        return rows == null ? new ArrayList<>() : rows;
    }

    private LabelPrintTemplateConfig pickLatestByUpdateTime(List<LabelPrintTemplateConfig> rows) {
        if (rows == null || rows.isEmpty()) {
            return null;
        }
        return rows.stream().max((a, b) -> {
            LocalDateTime ta = a == null ? null : a.getUpdateTime();
            LocalDateTime tb = b == null ? null : b.getUpdateTime();
            if (ta == null && tb == null) {
                Long ia = a == null ? null : a.getId();
                Long ib = b == null ? null : b.getId();
                if (ia == null && ib == null) return 0;
                if (ia == null) return -1;
                if (ib == null) return 1;
                return Long.compare(ia, ib);
            }
            if (ta == null) return -1;
            if (tb == null) return 1;
            int cmp = ta.compareTo(tb);
            if (cmp != 0) return cmp;
            Long ia = a == null ? null : a.getId();
            Long ib = b == null ? null : b.getId();
            if (ia == null && ib == null) return 0;
            if (ia == null) return -1;
            if (ib == null) return 1;
            return Long.compare(ia, ib);
        }).orElse(rows.get(0));
    }

    private void deleteCustomerDefaultDuplicates(String bizType, String customerCode, Long keepId) {
        if (customerCode == null || customerCode.trim().isEmpty()) {
            return;
        }
        List<LabelPrintTemplateConfig> rows = findCustomerDefaultRows(bizType, customerCode.trim(), false);
        for (LabelPrintTemplateConfig row : rows) {
            if (row == null || row.getId() == null) {
                continue;
            }
            if (keepId != null && keepId.equals(row.getId())) {
                continue;
            }
            configMapper.deleteById(row.getId());
        }
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
