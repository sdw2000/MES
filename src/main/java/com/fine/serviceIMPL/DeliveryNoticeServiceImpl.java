package com.fine.serviceIMPL;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.DigestUtils;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fine.Dao.DeliveryNoticeItemMapper;
import com.fine.Dao.DeliveryNoticeMapper;
import com.fine.Dao.SalesOrderItemMapper;
import com.fine.Dao.production.SalesOrderMapper;
import com.fine.modle.DeliveryNotice;
import com.fine.modle.DeliveryNoticeItem;
import com.fine.modle.LogisticsCompany;
import com.fine.modle.SalesOrder;
import com.fine.modle.SalesOrderItem;
import com.fine.service.DeliveryNoticeService;
import com.fine.service.LogisticsCompanyService;

@Service
public class DeliveryNoticeServiceImpl extends ServiceImpl<DeliveryNoticeMapper, DeliveryNotice> implements DeliveryNoticeService {

    @Autowired
    private DeliveryNoticeMapper deliveryNoticeMapper;

    @Autowired
    private DeliveryNoticeItemMapper deliveryNoticeItemMapper;

    @Autowired
    private SalesOrderMapper salesOrderMapper;
    
    @Autowired
    private SalesOrderItemMapper salesOrderItemMapper;

    @Autowired
    private LogisticsCompanyService logisticsCompanyService;

    @Value("${mes.logistics.enabled:false}")
    private boolean logisticsEnabled;

    @Value("${mes.logistics.api-url:https://poll.kuaidi100.com/poll/query.do}")
    private String logisticsApiUrl;

    @Value("${mes.logistics.customer:YOUR_KUAIDI100_CUSTOMER}")
    private String logisticsCustomer;

    @Value("${mes.logistics.key:YOUR_KUAIDI100_KEY}")
    private String logisticsKey;

    @Value("${mes.logistics.connect-timeout-ms:5000}")
    private int logisticsConnectTimeoutMs;

    @Value("${mes.logistics.read-timeout-ms:8000}")
    private int logisticsReadTimeoutMs;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DeliveryNotice createDeliveryNotice(DeliveryNotice deliveryNotice) {
        if (deliveryNotice == null) {
            throw new RuntimeException("发货单参数不能为空");
        }
        // 创建时强制清空主键，避免前端误传导致主键冲突
        deliveryNotice.setId(null);

        // 1. 验证订单是否存在
        SalesOrder order = null;
        if (deliveryNotice.getOrderId() != null) {
            order = salesOrderMapper.selectById(deliveryNotice.getOrderId());
        } else if (deliveryNotice.getOrderNo() != null) {
            // 尝试通过订单号查找
            List<SalesOrder> orders = salesOrderMapper.selectList(
                new QueryWrapper<SalesOrder>().eq("order_no", deliveryNotice.getOrderNo())
            );
            if (!orders.isEmpty()) {
                order = orders.get(0);
                deliveryNotice.setOrderId(order.getId());
            }
        }

        if (order == null) {
            throw new RuntimeException("关联销售订单不存在");
        }

        validateDeliveryItemQuantity(deliveryNotice.getItems(), null);
        
        // 2. 生成发货单号：Fine + yyMMdd + 当日三位序号
        String noticeNo = buildNoticeNo(deliveryNotice.getDeliveryDate());
        
        deliveryNotice.setNoticeNo(noticeNo);
        // 如果前端没有传，使用订单的客户
        if (deliveryNotice.getCustomer() == null || deliveryNotice.getCustomer().isEmpty()) {
            deliveryNotice.setCustomer(order.getCustomer());
        }
        // 如果前端没有传，使用订单号
        if (deliveryNotice.getOrderNo() == null || deliveryNotice.getOrderNo().isEmpty()) {
            deliveryNotice.setOrderNo(order.getOrderNo());
        }
        
        deliveryNotice.setCreatedAt(new Date());
        deliveryNotice.setIsDeleted(0);
        
        // 设置默认状态为待发货
        if (deliveryNotice.getStatus() == null || deliveryNotice.getStatus().isEmpty()) {
            deliveryNotice.setStatus("待发货");
        }
        
        // 3. 保存主表
        deliveryNoticeMapper.insert(deliveryNotice);
        
        // 4. 保存明细表
        if (deliveryNotice.getItems() != null && !deliveryNotice.getItems().isEmpty()) {
            for (DeliveryNoticeItem item : deliveryNotice.getItems()) {
                item.setNoticeId(deliveryNotice.getId());
                item.setId(null); // 确保ID为null，触发自增
                
                // 补充明细信息，如物料名等（如果前端没传且关联了订单明细）
                if (item.getOrderItemId() != null) {
                    SalesOrderItem orderItem = salesOrderItemMapper.selectById(item.getOrderItemId());
                    if(orderItem != null) {
                        if(item.getMaterialCode() == null || item.getMaterialCode().isEmpty()) item.setMaterialCode(orderItem.getMaterialCode());
                        if(item.getSpec() == null || item.getSpec().isEmpty()) {
                            item.setSpec(buildSpecWithUnit(orderItem));
                        }
                        item.setAreaSize(calculateDeliveryArea(orderItem, item.getQuantity()));
                    }
                }
                // 降低冗余：产品名称不在发货明细表持久化，前端展示时从研发规格表按料号带出。
                // 兼容历史库约束：material_name 可能为 NOT NULL，写空串避免插入失败。
                item.setMaterialName("");
                item.setSpec(normalizeSpecWithUnit(item.getSpec()));
                
                deliveryNoticeItemMapper.insert(item);
            }
        }
        
        return deliveryNotice;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DeliveryNotice updateDeliveryNotice(DeliveryNotice deliveryNotice) {
        if (deliveryNotice == null || deliveryNotice.getId() == null) {
            throw new RuntimeException("缺少发货单ID");
        }

        DeliveryNotice existing = deliveryNoticeMapper.selectById(deliveryNotice.getId());
        if (existing == null) {
            throw new RuntimeException("发货单不存在或已删除");
        }

        validateDeliveryItemQuantity(deliveryNotice.getItems(), deliveryNotice.getId());

        deliveryNotice.setCreatedAt(existing.getCreatedAt());
        deliveryNotice.setCreatedBy(existing.getCreatedBy());
        deliveryNotice.setUpdatedAt(new Date());
        if (deliveryNotice.getIsDeleted() == null) {
            deliveryNotice.setIsDeleted(existing.getIsDeleted());
        }

        boolean ok = this.updateById(deliveryNotice);
        if (!ok) {
            throw new RuntimeException("更新主表失败");
        }

        deliveryNoticeItemMapper.delete(new QueryWrapper<DeliveryNoticeItem>().eq("notice_id", deliveryNotice.getId()));
        if (deliveryNotice.getItems() != null && !deliveryNotice.getItems().isEmpty()) {
            for (DeliveryNoticeItem item : deliveryNotice.getItems()) {
                item.setNoticeId(deliveryNotice.getId());
                item.setId(null);
                if (item.getOrderItemId() != null) {
                    SalesOrderItem orderItem = salesOrderItemMapper.selectById(item.getOrderItemId());
                    if (orderItem != null) {
                        if (item.getMaterialCode() == null || item.getMaterialCode().isEmpty()) item.setMaterialCode(orderItem.getMaterialCode());
                        if (item.getSpec() == null || item.getSpec().isEmpty()) {
                            item.setSpec(buildSpecWithUnit(orderItem));
                        }
                        item.setAreaSize(calculateDeliveryArea(orderItem, item.getQuantity()));
                    }
                }
                // 兼容历史库约束：material_name 可能为 NOT NULL，写空串避免插入失败。
                item.setMaterialName("");
                item.setSpec(normalizeSpecWithUnit(item.getSpec()));
                deliveryNoticeItemMapper.insert(item);
            }
        }

        return this.getDeliveryNoticeDetail(deliveryNotice.getId());
    }

    private void validateDeliveryItemQuantity(List<DeliveryNoticeItem> items, Long currentNoticeId) {
        if (items == null || items.isEmpty()) {
            return;
        }

        Map<Long, Integer> requestQtyMap = new HashMap<>();
        for (DeliveryNoticeItem item : items) {
            if (item == null || item.getOrderItemId() == null) continue;
            int qty = item.getQuantity() == null ? 0 : item.getQuantity();
            if (qty < 0) {
                throw new RuntimeException("发货数量不能小于0");
            }
            Integer oldVal = requestQtyMap.get(item.getOrderItemId());
            requestQtyMap.put(item.getOrderItemId(), (oldVal == null ? 0 : oldVal) + qty);
        }

        for (Map.Entry<Long, Integer> entry : requestQtyMap.entrySet()) {
            Long orderItemId = entry.getKey();
            int requestQty = entry.getValue() == null ? 0 : entry.getValue();

            SalesOrderItem orderItem = salesOrderItemMapper.selectById(orderItemId);
            if (orderItem == null) {
                throw new RuntimeException("订单明细不存在：" + orderItemId);
            }

            int orderQty = orderItem.getRolls() == null ? 0 : orderItem.getRolls();
            int shippedQty = deliveryNoticeItemMapper.getShippedQuantityByOrderItemId(orderItemId);
            int selfQty = 0;
            if (currentNoticeId != null) {
                selfQty = deliveryNoticeItemMapper.getNoticeItemQuantity(currentNoticeId, orderItemId);
            }

            int remainQty = Math.max(0, orderQty - Math.max(0, shippedQty - selfQty));
            if (requestQty > remainQty) {
                throw new RuntimeException("发货数量不能多过欠货数量（订单明细ID=" + orderItemId + "，欠货=" + remainQty + "，本次=" + requestQty + "）");
            }
        }
    }

    @Override
    public DeliveryNotice getDeliveryNoticeDetail(Long id) {
        DeliveryNotice notice = deliveryNoticeMapper.selectById(id);
        if (notice != null) {
            List<DeliveryNoticeItem> items = deliveryNoticeItemMapper.selectByNoticeId(id);
            notice.setItems(items);
        }
        return notice;
    }

    @Override
    public Map<String, Object> queryLogistics(Long id) {
        Map<String, Object> result = new HashMap<>();
        DeliveryNotice notice = deliveryNoticeMapper.selectById(id);
        if (notice == null) {
            result.put("success", false);
            result.put("message", "发货单不存在");
            return result;
        }
        if (!StringUtils.hasText(notice.getCarrierNo())) {
            result.put("success", false);
            result.put("message", "未找到快递单号");
            return result;
        }
        String normalizedCarrierNo = normalizeTrackingNumber(notice.getCarrierNo());
        if (!StringUtils.hasText(normalizedCarrierNo)) {
            result.put("success", false);
            result.put("message", "物流单号格式无效");
            return result;
        }

        if (shouldOfflineQuery(notice.getCarrierName())) {
            result.put("success", false);
            result.put("message", "未识别标准承运公司，请先维护物流公司后再查询轨迹");
            result.put("status", "未识别承运公司");
            result.put("lastUpdate", "-");
            result.put("traces", java.util.Collections.emptyList());
            result.put("carrierNo", normalizedCarrierNo);
            return result;
        }
        if (!logisticsEnabled) {
            result.put("success", false);
            result.put("message", "物流查询未启用，请在配置文件中设置 mes.logistics.enabled=true");
            return result;
        }
        if (!StringUtils.hasText(logisticsCustomer) || !StringUtils.hasText(logisticsKey)
                || logisticsCustomer.startsWith("YOUR_") || logisticsKey.startsWith("YOUR_")) {
            result.put("success", false);
            result.put("message", "物流API参数未配置，请设置 mes.logistics.customer / mes.logistics.key");
            return result;
        }

        try {
            List<String> companyCodes = resolveExpressCodeCandidates(notice.getCarrierName(), normalizedCarrierNo);
            if (companyCodes.isEmpty()) {
                result.put("success", false);
                result.put("message", "未识别快递公司，请先填写标准承运公司名称");
                result.put("carrierName", notice.getCarrierName());
                result.put("carrierNo", normalizedCarrierNo);
                return result;
            }

            String phoneTail4 = resolvePhoneTail4(notice);
            if (!StringUtils.hasText(phoneTail4) && containsSfCarrierCode(companyCodes)) {
                result.put("success", false);
                result.put("message", "顺丰查询需提供收件/寄件手机号后4位，请补充联系电话或运输电话");
                result.put("carrierName", notice.getCarrierName());
                result.put("carrierNo", normalizedCarrierNo);
                result.put("triedCompanyCodes", companyCodes);
                return result;
            }

            Map<String, Object> apiResp = null;
            String failMsg = "物流接口查询失败";
            for (String companyCode : companyCodes) {
                apiResp = queryKuaidi100(companyCode, normalizedCarrierNo, phoneTail4);
                if (isKuaidi100Success(apiResp)) {
                    break;
                }
                failMsg = buildKuaidi100FailMessage(apiResp);
                if (!shouldTryNextCompanyCode(failMsg)) {
                    apiResp = null;
                    break;
                }
                apiResp = null;
            }

            if (apiResp == null || !isKuaidi100Success(apiResp)) {
                result.put("success", false);
                result.put("message", failMsg);
                result.put("carrierName", notice.getCarrierName());
                result.put("carrierNo", normalizedCarrierNo);
                result.put("triedCompanyCodes", companyCodes);
                result.put("lastApiResponse", apiResp == null ? "" : String.valueOf(apiResp));
                return result;
            }

            String state = stringValue(apiResp.get("state"));
            String statusText = mapKuaidi100StateToStatus(state);
            List<Map<String, String>> traces = parseKuaidi100Traces(apiResp.get("data"));
            String lastUpdate = traces.isEmpty()
                    ? LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                    : traces.get(0).get("time");

            result.put("success", true);
            result.put("message", "查询成功");
            result.put("status", statusText);
            result.put("lastUpdate", lastUpdate);
            result.put("traces", traces);
            result.put("carrierNo", normalizedCarrierNo);

            notice.setUpdatedAt(new Date());
            if ("已送达".equals(statusText) || "已签收".equals(statusText)) {
                notice.setStatus("已收货");
            } else if ("运输中".equals(statusText) || "派件中".equals(statusText)) {
                if (!"已收货".equals(notice.getStatus()) && !"received".equalsIgnoreCase(notice.getStatus())) {
                    notice.setStatus("已发货");
                }
            }
            deliveryNoticeMapper.updateById(notice);
            return result;
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "物流查询失败: " + e.getMessage());
            return result;
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteDeliveryNotice(Long id) {
        if (id == null) {
            throw new RuntimeException("缺少发货单ID");
        }
        DeliveryNotice existing = deliveryNoticeMapper.selectById(id);
        if (existing == null || Integer.valueOf(1).equals(existing.getIsDeleted())) {
            throw new RuntimeException("发货单不存在或已删除");
        }

        String status = existing.getStatus() == null ? "" : existing.getStatus().trim();
        if ("已发货".equals(status) || "shipped".equalsIgnoreCase(status)
                || "已收货".equals(status) || "received".equalsIgnoreCase(status)) {
            throw new RuntimeException("已发货或已收货的单据不允许删除");
        }

        deliveryNoticeItemMapper.delete(new QueryWrapper<DeliveryNoticeItem>().eq("notice_id", id));
        return this.removeById(id);
    }

    private BigDecimal calculateDeliveryArea(SalesOrderItem orderItem, Integer quantity) {
        if (orderItem == null || quantity == null || quantity <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }

        BigDecimal orderSqm = orderItem.getSqm();
        Integer orderRolls = orderItem.getRolls();
        if (orderSqm == null || orderRolls == null || orderRolls <= 0) {
            throw new RuntimeException("订单明细面积数据不完整，无法计算发货面积（orderItemId=" + orderItem.getId() + "）");
        }
        return orderSqm
                .divide(BigDecimal.valueOf(orderRolls), 8, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(quantity))
                .setScale(2, RoundingMode.HALF_UP);
    }

    private String buildNoticeNo(LocalDate deliveryDate) {
        LocalDate bizDate = deliveryDate == null
                ? LocalDate.now(ZoneId.of("Asia/Shanghai"))
                : deliveryDate;
        String datePart = bizDate.format(DateTimeFormatter.ofPattern("yyMMdd"));
        String prefix = "Fine" + datePart;

        QueryWrapper<DeliveryNotice> wrapper = new QueryWrapper<>();
        wrapper.likeRight("notice_no", prefix).eq("is_deleted", 0);
        List<DeliveryNotice> exists = deliveryNoticeMapper.selectList(wrapper);

        int maxSeq = 0;
        for (DeliveryNotice one : exists) {
            if (one == null || one.getNoticeNo() == null) continue;
            String no = one.getNoticeNo().trim();
            if (!no.startsWith(prefix)) continue;
            String tail = no.substring(prefix.length());
            if (tail.matches("\\d{3}")) {
                maxSeq = Math.max(maxSeq, Integer.parseInt(tail));
            }
        }
        return prefix + String.format("%03d", maxSeq + 1);
    }

    private String buildSpecWithUnit(SalesOrderItem orderItem) {
        if (orderItem == null) return "";
        String thickness = orderItem.getThickness() == null ? "0" : orderItem.getThickness().stripTrailingZeros().toPlainString();
        String width = orderItem.getWidth() == null ? "0" : orderItem.getWidth().stripTrailingZeros().toPlainString();
        String length = orderItem.getLength() == null ? "0" : orderItem.getLength().stripTrailingZeros().toPlainString();
        return thickness + "μm*" + width + "mm*" + length + "m";
    }

    private String normalizeSpecWithUnit(String rawSpec) {
        if (rawSpec == null) return "";
        String s = rawSpec.trim();
        if (s.isEmpty()) return "";
        if (s.contains("μm") || s.contains("mm") || s.contains("m")) {
            return s;
        }
        String[] parts = s.split("\\*");
        if (parts.length == 3) {
            return parts[0].trim() + "μm*" + parts[1].trim() + "mm*" + parts[2].trim() + "m";
        }
        return s;
    }

    private Map<String, Object> queryKuaidi100(String companyCode, String trackingNumber, String phoneTail4) throws Exception {
        Map<String, Object> param = new LinkedHashMap<>();
        param.put("com", companyCode);
        param.put("num", trackingNumber);
        if (StringUtils.hasText(phoneTail4) && isPhoneRequiredCarrier(companyCode)) {
            param.put("phone", phoneTail4);
        }

        ObjectMapper mapper = new ObjectMapper();
        String paramJson = mapper.writeValueAsString(param);
        String signRaw = paramJson + logisticsKey + logisticsCustomer;
        byte[] signBytes = java.util.Objects.requireNonNull((signRaw == null ? "" : signRaw).getBytes(StandardCharsets.UTF_8));
        String sign = DigestUtils.md5DigestAsHex(signBytes).toUpperCase();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("customer", logisticsCustomer);
        body.add("sign", sign);
        body.add("param", paramJson);

        RestTemplate restTemplate = buildLogisticsRestTemplate();
        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);
        String apiUrl = java.util.Objects.requireNonNull(StringUtils.hasText(logisticsApiUrl) ? logisticsApiUrl : "https://poll.kuaidi100.com/poll/query.do");
        ResponseEntity<String> response = restTemplate.postForEntity(apiUrl, request, String.class);
        String raw = response.getBody();
        if (!StringUtils.hasText(raw)) {
            throw new RuntimeException("物流接口返回空响应");
        }
        return mapper.readValue(raw, new TypeReference<Map<String, Object>>() {});
    }

    private RestTemplate buildLogisticsRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(logisticsConnectTimeoutMs);
        factory.setReadTimeout(logisticsReadTimeoutMs);
        return new RestTemplate(factory);
    }

    private List<String> resolveExpressCodeCandidates(String expressCompany, String trackingNo) {
        String v = expressCompany == null ? "" : expressCompany.trim();
        Map<String, String> map = new HashMap<>();
        map.put("顺丰速运", "shunfeng");
        map.put("顺丰", "shunfeng");
        map.put("顺丰快递", "shunfeng");
        map.put("SF", "shunfeng");
        map.put("SF EXPRESS", "shunfeng");
        map.put("邮政", "youzhengguonei");
        map.put("中国邮政", "youzhengguonei");
        map.put("EMS快递", "ems");
        map.put("圆通速递", "yuantong");
        map.put("圆通", "yuantong");
        map.put("中通快递", "zhongtong");
        map.put("中通", "zhongtong");
        map.put("中通快运", "zhongtongkuaiyun");
        map.put("中通快运物流", "zhongtongkuaiyun");
        map.put("ZTO快运", "zhongtongkuaiyun");
        map.put("ZTO快递", "zhongtong");
        map.put("申通快递", "shentong");
        map.put("申通", "shentong");
        map.put("韵达快递", "yunda");
        map.put("韵达", "yunda");
        map.put("优速快递", "youshuwuliu");
        map.put("优速物流", "youshuwuliu");
        map.put("优速", "youshuwuliu");
        map.put("UC", "youshuwuliu");
        map.put("UC56", "youshuwuliu");
        map.put("邮政EMS", "ems");
        map.put("EMS", "ems");
        map.put("京东物流", "jd");
        map.put("京东", "jd");
        map.put("德邦快递", "debangwuliu");
        map.put("德邦", "debangwuliu");
        map.put("安能物流", "annengwuliu");
        map.put("安能", "annengwuliu");
        map.put("极兔速递", "jtexpress");
        map.put("极兔", "jtexpress");
        map.put("百世快递", "huitongkuaidi");
        map.put("百世", "huitongkuaidi");
        map.put("天天快递", "tiantian");
        map.put("宅急送", "zhaijisong");
        map.put("丰网速运", "fengwang");
        map.put("丰网", "fengwang");
        map.put("顺心捷达", "shunxinjieda");
        map.put("中铁快运", "ztky");
        map.put("中铁", "ztky");
        map.put("跨越速运", "kuayue");
        map.put("跨越快递", "kuayue");
        map.put("跨越", "kuayue");

        java.util.LinkedHashSet<String> codes = new java.util.LinkedHashSet<>();

        // 优先使用物流公司主数据维护的 company_code（建议维护为快递100编码）
        String codeFromMaster = resolveCompanyCodeFromMaster(v);
        if (StringUtils.hasText(codeFromMaster)) {
            codes.add(codeFromMaster);
        }

        String code = map.get(v);
        if (!StringUtils.hasText(code) && StringUtils.hasText(v)) {
            code = map.get(v.toUpperCase());
        }
        if (StringUtils.hasText(code)) {
            codes.add(code);
        }

        // 标准化后再做一次模糊匹配，增强各种别名容错
        String normalized = normalizeCarrierNameForMatch(v);
        if (StringUtils.hasText(normalized)) {
            for (Map.Entry<String, String> entry : map.entrySet()) {
                String keyNorm = normalizeCarrierNameForMatch(entry.getKey());
                if (StringUtils.hasText(keyNorm) && normalized.contains(keyNorm)) {
                    codes.add(entry.getValue());
                }
            }
        }

        if (isSfTrackingNumber(trackingNo)) {
            codes.add("shunfeng");
            codes.add("sf");
        }
        if (v.contains("顺丰") || v.toUpperCase().contains("SF")) {
            codes.add("shunfeng");
            codes.add("sf");
        }
        if (v.contains("跨越")) {
            codes.add("kuayue");
            codes.add("kuayuekuaiyun");
            codes.add("kyexp");
        }
        if (v.contains("中通") && v.contains("快运")) {
            codes.add("zhongtongkuaiyun");
            // 兼容个别渠道仍按快递编码返回
            codes.add("zhongtong");
        }
        if (StringUtils.hasText(v)) {
            codes.add(v);
        }
        return new java.util.ArrayList<>(codes);
    }

    private String resolveCompanyCodeFromMaster(String carrierName) {
        if (!StringUtils.hasText(carrierName)) return "";
        try {
            QueryWrapper<LogisticsCompany> exactQw = new QueryWrapper<>();
            exactQw.eq("company_name", carrierName.trim()).eq("is_deleted", 0).last("LIMIT 1");
            LogisticsCompany exact = logisticsCompanyService.getOne(exactQw, false);
            if (exact != null && StringUtils.hasText(exact.getCompanyCode())) {
                return exact.getCompanyCode().trim();
            }

            QueryWrapper<LogisticsCompany> likeQw = new QueryWrapper<>();
            likeQw.like("company_name", carrierName.trim()).eq("is_deleted", 0).orderByDesc("updated_at").last("LIMIT 1");
            LogisticsCompany fuzzy = logisticsCompanyService.getOne(likeQw, false);
            if (fuzzy != null && StringUtils.hasText(fuzzy.getCompanyCode())) {
                return fuzzy.getCompanyCode().trim();
            }
        } catch (Exception ignored) {
        }
        return "";
    }

    private String normalizeCarrierNameForMatch(String name) {
        if (!StringUtils.hasText(name)) return "";
        return name.trim()
                .toUpperCase()
                .replaceAll("[\\s\\-_/（）()·,.，。]", "")
                .replace("速运", "")
                .replace("快递", "")
                .replace("快运", "")
                .replace("物流", "");
    }

    private String normalizeTrackingNumber(String carrierNo) {
        if (!StringUtils.hasText(carrierNo)) {
            return "";
        }
        String text = carrierNo.trim().toUpperCase();
        // 去掉空格、短横线等分隔符
        text = text.replaceAll("[^A-Z0-9]", "");
        return text;
    }

    private boolean isSfTrackingNumber(String trackingNo) {
        if (!StringUtils.hasText(trackingNo)) return false;
        String no = trackingNo.trim().toUpperCase();
        // 仅在单号前缀明确为 SF 时识别为顺丰，避免普通数字单号被误判
        return no.startsWith("SF");
    }

    private String resolvePhoneTail4(DeliveryNotice notice) {
        if (notice == null) return "";
        String[] candidates = new String[] {
                notice.getContactPhone(),
                notice.getCarrierPhone()
        };
        for (String one : candidates) {
            String tail4 = extractPhoneTail4(one);
            if (StringUtils.hasText(tail4)) {
                return tail4;
            }
        }
        return "";
    }

    private String extractPhoneTail4(String phone) {
        if (!StringUtils.hasText(phone)) return "";
        String digits = phone.replaceAll("\\D", "");
        if (digits.length() < 4) return "";
        return digits.substring(digits.length() - 4);
    }

    private boolean isPhoneRequiredCarrier(String companyCode) {
        if (!StringUtils.hasText(companyCode)) return false;
        String code = companyCode.trim().toLowerCase();
        return "shunfeng".equals(code) || "sf".equals(code);
    }

    private boolean containsSfCarrierCode(List<String> companyCodes) {
        if (companyCodes == null || companyCodes.isEmpty()) {
            return false;
        }
        for (String code : companyCodes) {
            if (!StringUtils.hasText(code)) {
                continue;
            }
            String value = code.trim().toLowerCase();
            if ("shunfeng".equals(value) || "sf".equals(value)) {
                return true;
            }
        }
        return false;
    }

    private boolean shouldOfflineQuery(String carrierName) {
        String name = carrierName == null ? "" : carrierName.trim();
        if (!StringUtils.hasText(name)) {
            return true;
        }
        String upper = name.toUpperCase();
        if (name.contains("方恩") || name.contains("自提") || name.contains("送货") || upper.contains("PICKUP")) {
            return true;
        }
        List<String> candidates = resolveExpressCodeCandidates(name, "");
        return candidates == null || candidates.isEmpty();
    }

    private String mapKuaidi100StateToStatus(String state) {
        if ("3".equals(state)) return "已送达";
        if ("5".equals(state)) return "派件中";
        if ("4".equals(state) || "6".equals(state)) return "已拒收";
        return "运输中";
    }

    private List<Map<String, String>> parseKuaidi100Traces(Object dataObj) {
        List<Map<String, String>> traces = new ArrayList<>();
        if (!(dataObj instanceof List)) {
            return traces;
        }
        List<?> dataList = (List<?>) dataObj;
        for (Object obj : dataList) {
            if (!(obj instanceof Map)) continue;
            Map<?, ?> one = (Map<?, ?>) obj;
            String time = stringValue(one.get("ftime"));
            if (!StringUtils.hasText(time)) {
                time = stringValue(one.get("time"));
            }
            String context = stringValue(one.get("context"));
            Map<String, String> trace = new HashMap<>();
            trace.put("time", StringUtils.hasText(time) ? time : "-");
            trace.put("context", StringUtils.hasText(context) ? context : "-");
            traces.add(trace);
        }
        return traces;
    }

    private String stringValue(Object v) {
        return v == null ? "" : String.valueOf(v);
    }

    private boolean isKuaidi100Success(Map<String, Object> apiResp) {
        String returnCode = stringValue(apiResp.get("returnCode"));
        if (!StringUtils.hasText(returnCode)) {
            returnCode = stringValue(apiResp.get("status"));
        }
        if (!StringUtils.hasText(returnCode)) {
            returnCode = stringValue(apiResp.get("code"));
        }
        Object ok = apiResp.get("result");
        String message = stringValue(apiResp.get("message"));
        Object data = apiResp.get("data");
        boolean hasData = data instanceof List && !((List<?>) data).isEmpty();
        boolean hasState = StringUtils.hasText(stringValue(apiResp.get("state")));

        if ("200".equals(returnCode)
                && (Boolean.TRUE.equals(ok) || "true".equalsIgnoreCase(String.valueOf(ok)) || "ok".equalsIgnoreCase(message))) {
            return true;
        }
        return "ok".equalsIgnoreCase(message) && (hasState || hasData);
    }

    private String buildKuaidi100FailMessage(Map<String, Object> apiResp) {
        String returnCode = stringValue(apiResp.get("returnCode"));
        if (!StringUtils.hasText(returnCode)) {
            returnCode = stringValue(apiResp.get("status"));
        }
        if (!StringUtils.hasText(returnCode)) {
            returnCode = stringValue(apiResp.get("code"));
        }
        String resultFlag = stringValue(apiResp.get("result"));
        String message = stringValue(apiResp.get("message"));
        if (!StringUtils.hasText(message)) {
            message = "物流接口查询失败";
        }
        if (!StringUtils.hasText(returnCode) && !StringUtils.hasText(resultFlag)) {
            return message;
        }
        return message + " (returnCode=" + returnCode + ", result=" + resultFlag + ")";
    }

    private boolean shouldTryNextCompanyCode(String failMsg) {
        if (!StringUtils.hasText(failMsg)) return false;
        return failMsg.contains("不支持此快递公司")
                || failMsg.contains("公司编码")
            || failMsg.contains("手机号")
            || failMsg.contains("手机尾号");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String appendBatchNoByNoticeNo(String noticeNo, String batchNo) {
        String normalizedNoticeNo = noticeNo == null ? "" : noticeNo.trim();
        String normalizedBatchNo = batchNo == null ? "" : batchNo.trim();
        if (!StringUtils.hasText(normalizedNoticeNo)) {
            throw new RuntimeException("送货单号不能为空");
        }
        if (!StringUtils.hasText(normalizedBatchNo)) {
            throw new RuntimeException("批次号不能为空");
        }

        DeliveryNotice notice = deliveryNoticeMapper.selectOne(
                new QueryWrapper<DeliveryNotice>()
                        .eq("notice_no", normalizedNoticeNo)
                        .eq("is_deleted", 0)
                        .last("LIMIT 1")
        );
        if (notice == null) {
            throw new RuntimeException("未找到送货单：" + normalizedNoticeNo);
        }

        String merged = mergeUniqueBatchNos(notice.getBatchNos(), normalizedBatchNo);
        notice.setBatchNos(merged);
        notice.setUpdatedAt(new Date());
        if (deliveryNoticeMapper.updateById(notice) <= 0) {
            throw new RuntimeException("更新送货单批次号失败");
        }
        return merged;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int syncItemBatchNoByNoticeNo(String noticeNo, String materialCode, String batchNo) {
        String normalizedNoticeNo = noticeNo == null ? "" : noticeNo.trim();
        String normalizedMaterialCode = materialCode == null ? "" : materialCode.trim();
        String normalizedBatchNo = batchNo == null ? "" : batchNo.trim();

        if (!StringUtils.hasText(normalizedNoticeNo) || !StringUtils.hasText(normalizedBatchNo)) {
            return 0;
        }

        DeliveryNotice notice = deliveryNoticeMapper.selectOne(
                new QueryWrapper<DeliveryNotice>()
                        .eq("notice_no", normalizedNoticeNo)
                        .eq("is_deleted", 0)
                        .last("LIMIT 1")
        );
        if (notice == null || notice.getId() == null) {
            return 0;
        }

        QueryWrapper<DeliveryNoticeItem> itemQw = new QueryWrapper<DeliveryNoticeItem>()
                .eq("notice_id", notice.getId());
        if (StringUtils.hasText(normalizedMaterialCode)) {
            itemQw.eq("material_code", normalizedMaterialCode);
        }
        List<DeliveryNoticeItem> items = deliveryNoticeItemMapper.selectList(itemQw);
        if (items == null || items.isEmpty()) {
            return 0;
        }

        int updated = 0;
        for (DeliveryNoticeItem item : items) {
            if (item == null || item.getId() == null) {
                continue;
            }
            String merged = mergeUniqueBatchNos(item.getBatchNo(), normalizedBatchNo);
            String oldVal = item.getBatchNo() == null ? "" : item.getBatchNo().trim();
            String newVal = merged == null ? "" : merged.trim();
            if (oldVal.equals(newVal)) {
                continue;
            }
            item.setBatchNo(merged);
            if (deliveryNoticeItemMapper.updateById(item) > 0) {
                updated++;
            }
        }
        return updated;
    }

    private String mergeUniqueBatchNos(String existingBatchNos, String incomingBatchNo) {
        Set<String> unique = new LinkedHashSet<>();
        if (StringUtils.hasText(existingBatchNos)) {
            String[] parts = existingBatchNos.split("[,，]");
            for (String part : parts) {
                String one = part == null ? "" : part.trim();
                if (StringUtils.hasText(one)) {
                    unique.add(one);
                }
            }
        }
        String next = incomingBatchNo == null ? "" : incomingBatchNo.trim();
        if (StringUtils.hasText(next)) {
            unique.add(next);
        }
        return String.join(",", unique);
    }
}
