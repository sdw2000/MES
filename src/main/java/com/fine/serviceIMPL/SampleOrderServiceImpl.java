package com.fine.serviceIMPL;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fine.Dao.SampleOrderMapper;
import com.fine.Dao.SampleItemMapper;
import com.fine.Dao.CustomerMapper;
import com.fine.Dao.CustomerContactMapper;
import com.fine.Dao.UserMapper;
import com.fine.Utils.ResponseResult;
import com.fine.modle.*;
import com.fine.service.LogisticsCompanyService;
import com.fine.service.SampleOrderService;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.usermodel.DateUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.util.DigestUtils;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 送样订单Service实现类
 * @author AI Assistant
 * @date 2026-01-05
 */
@Service
public class SampleOrderServiceImpl implements SampleOrderService {
        private static final String SAMPLE_STATUS_COMPLETED = "已完成";
        private static final String SAMPLE_STATUS_UNCOMPLETED = "未完成";

      @Autowired
    private SampleOrderMapper sampleOrderMapper;
    
    @Autowired
    private SampleItemMapper sampleItemMapper;

    @Autowired
    private CustomerMapper customerMapper;

    @Autowired
    private CustomerContactMapper customerContactMapper;

    @Autowired
    private UserMapper userMapper;

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
    public Page<SampleOrderDTO> list(int current, int size, String customerName, String status, String trackingNumber) {
        Page<SampleOrder> page = new Page<>(current, size);
        page.setOptimizeCountSql(false);
        
        LambdaQueryWrapper<SampleOrder> wrapper = new LambdaQueryWrapper<>();
        
        // 客户名称模糊查询
        if (StringUtils.hasText(customerName)) {
            wrapper.like(SampleOrder::getCustomerName, customerName);
        }
        
        // 状态筛选（仅保留：已完成/未完成）
        if (StringUtils.hasText(status)) {
            String normalizedStatus = normalizeSampleStatus(status);
            if (SAMPLE_STATUS_COMPLETED.equals(normalizedStatus)) {
                wrapper.eq(SampleOrder::getStatus, SAMPLE_STATUS_COMPLETED);
            } else {
                wrapper.ne(SampleOrder::getStatus, SAMPLE_STATUS_COMPLETED);
            }
        }
        
        // 快递单号查询
        if (StringUtils.hasText(trackingNumber)) {
            wrapper.like(SampleOrder::getTrackingNumber, trackingNumber);
        }
        
        // 按创建时间倒序
        wrapper.eq(SampleOrder::getIsDeleted, false);
        wrapper.orderByDesc(SampleOrder::getCreateTime);

        LoginUser loginUser = getLoginUser();
        if (loginUser != null && !hasRole(loginUser, "admin")) {
            Long uid = getCurrentUserId(loginUser);
            List<Long> allowedIds = customerMapper.selectCustomerIdsByOwner(uid);
            if (allowedIds == null || allowedIds.isEmpty()) {
                Page<SampleOrderDTO> emptyPage = new Page<>(current, size, 0);
                emptyPage.setRecords(Collections.emptyList());
                return emptyPage;
            }
            wrapper.in(SampleOrder::getCustomerId, allowedIds);
        }
        
        Page<SampleOrder> resultPage = sampleOrderMapper.selectPage(page, wrapper);
        
        // 转换为DTO
        Page<SampleOrderDTO> dtoPage = new Page<>(resultPage.getCurrent(), resultPage.getSize(), resultPage.getTotal());
        List<SampleOrderDTO> dtoList = resultPage.getRecords().stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
        
        dtoPage.setRecords(dtoList);
        
        return dtoPage;
    }

    @Override
    public Map<String, Long> stats(String customerName, String status, String trackingNumber) {
        Map<String, Long> result = new HashMap<>();
        result.put("orderCount", 0L);
        result.put("itemCount", 0L);

        LambdaQueryWrapper<SampleOrder> wrapper = new LambdaQueryWrapper<>();

        if (StringUtils.hasText(customerName)) {
            wrapper.like(SampleOrder::getCustomerName, customerName);
        }
        if (StringUtils.hasText(status)) {
            String normalizedStatus = normalizeSampleStatus(status);
            if (SAMPLE_STATUS_COMPLETED.equals(normalizedStatus)) {
                wrapper.eq(SampleOrder::getStatus, SAMPLE_STATUS_COMPLETED);
            } else {
                wrapper.ne(SampleOrder::getStatus, SAMPLE_STATUS_COMPLETED);
            }
        }
        if (StringUtils.hasText(trackingNumber)) {
            wrapper.like(SampleOrder::getTrackingNumber, trackingNumber);
        }

        wrapper.eq(SampleOrder::getIsDeleted, false);

        LoginUser loginUser = getLoginUser();
        if (loginUser != null && !hasRole(loginUser, "admin")) {
            Long uid = getCurrentUserId(loginUser);
            List<Long> allowedIds = customerMapper.selectCustomerIdsByOwner(uid);
            if (allowedIds == null || allowedIds.isEmpty()) {
                return result;
            }
            wrapper.in(SampleOrder::getCustomerId, allowedIds);
        }

        Long orderCount = sampleOrderMapper.selectCount(wrapper);
        long safeOrderCount = orderCount == null ? 0L : orderCount;
        result.put("orderCount", safeOrderCount);
        if (safeOrderCount <= 0) {
            return result;
        }

        LambdaQueryWrapper<SampleOrder> sampleNoWrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(customerName)) {
            sampleNoWrapper.like(SampleOrder::getCustomerName, customerName);
        }
        if (StringUtils.hasText(status)) {
            sampleNoWrapper.eq(SampleOrder::getStatus, status);
        }
        if (StringUtils.hasText(trackingNumber)) {
            sampleNoWrapper.like(SampleOrder::getTrackingNumber, trackingNumber);
        }
        sampleNoWrapper.eq(SampleOrder::getIsDeleted, false);

        if (loginUser != null && !hasRole(loginUser, "admin")) {
            Long uid = getCurrentUserId(loginUser);
            List<Long> allowedIds = customerMapper.selectCustomerIdsByOwner(uid);
            if (allowedIds == null || allowedIds.isEmpty()) {
                return result;
            }
            sampleNoWrapper.in(SampleOrder::getCustomerId, allowedIds);
        }

        sampleNoWrapper.select(SampleOrder::getSampleNo);
        List<SampleOrder> orders = sampleOrderMapper.selectList(sampleNoWrapper);
        if (orders == null || orders.isEmpty()) {
            return result;
        }

        List<String> sampleNos = orders.stream()
            .map(SampleOrder::getSampleNo)
            .filter(StringUtils::hasText)
            .distinct()
            .collect(Collectors.toList());

        if (sampleNos.isEmpty()) {
            return result;
        }

        Long itemCount = sampleItemMapper.selectCount(new LambdaQueryWrapper<SampleItem>()
            .in(SampleItem::getSampleNo, sampleNos));
        result.put("itemCount", itemCount == null ? 0L : itemCount);
        return result;
    }
    
    @Override
    public SampleOrderDTO getDetailBySampleNo(String sampleNo) {
        LambdaQueryWrapper<SampleOrder> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SampleOrder::getSampleNo, sampleNo);
        wrapper.eq(SampleOrder::getIsDeleted, false);
        
        SampleOrder order = sampleOrderMapper.selectOne(wrapper);
        if (order == null) {
            return null;
        }
        if (!canAccessSample(getLoginUser(), order)) {
            return null;
        }
        
        // 查询明细
        List<SampleItem> items = sampleItemMapper.selectBySampleNo(sampleNo);
        order.setItems(items);
        
        // 计算总数量
        int totalQty = items.stream().mapToInt(SampleItem::getQuantity).sum();
        order.setTotalQuantity(totalQty);
        
        return convertToDTO(order);
    }

    @Override
    public List<SampleOrderDTO> getHistoryByCustomerId(Long customerId) {
        if (customerId == null) return Collections.emptyList();
        
        LambdaQueryWrapper<SampleOrder> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SampleOrder::getCustomerId, customerId)
               .eq(SampleOrder::getIsDeleted, 0)
               .orderByDesc(SampleOrder::getSendDate);
        
        List<SampleOrder> orders = sampleOrderMapper.selectList(wrapper);
        return orders.stream().map(order -> {
            SampleOrderDTO dto = convertToDTO(order);
            // 填充送样明细，确保前端能显示物料名称、规格、数量等信息
            dto.setItems(sampleItemMapper.selectBySampleNo(order.getSampleNo()));
            return dto;
        }).collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String create(SampleOrderDTO dto) {
        // 生成送样编号
        String sampleNo = generateSampleNo();

        LoginUser loginUser = getLoginUser();
        if (dto != null && loginUser != null && !hasRole(loginUser, "admin")) {
            Long uid = getCurrentUserId(loginUser);
            List<Long> allowedIds = customerMapper.selectCustomerIdsByOwner(uid);
            if (allowedIds == null || dto.getCustomerId() == null || !allowedIds.contains(dto.getCustomerId())) {
                throw new RuntimeException("无权限创建该客户的送样单");
            }
        }
        
        SampleOrder order = new SampleOrder();
        if (dto != null) {
            BeanUtils.copyProperties(dto, order);
            order.setSampleNo(sampleNo);

            if (loginUser != null && StringUtils.hasText(loginUser.getUsername())) {
                // 制单人=当前登录账号
                order.setCreateBy(loginUser.getUsername());
            }
            
            // 设置默认状态
            if (!StringUtils.hasText(order.getStatus())) {
                order.setStatus(SAMPLE_STATUS_UNCOMPLETED);
            } else {
                order.setStatus(normalizeSampleStatus(order.getStatus()));
            }
            
            // 保存主表
            sampleOrderMapper.insert(order);
            
            // 保存明细
            if (dto.getItems() != null && !dto.getItems().isEmpty()) {
                for (SampleItem item : dto.getItems()) {
                    normalizeSampleItem(item);
                    item.setSampleNo(sampleNo);
                    sampleItemMapper.insert(item);
                }
            }
        }
        
        return sampleNo;
    }
      @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean update(SampleOrderDTO dto) {
        if (dto == null) {
            return false;
        }
        
        LambdaQueryWrapper<SampleOrder> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SampleOrder::getSampleNo, dto.getSampleNo());
        wrapper.eq(SampleOrder::getIsDeleted, false);
        SampleOrder order = sampleOrderMapper.selectOne(wrapper);
        if (order == null) {
            return false;
        }
        if (!canAccessSample(getLoginUser(), order)) {
            return false;
        }
        
        // 更新主表
        BeanUtils.copyProperties(dto, order, "id", "sampleNo", "createTime", "createBy");
        order.setStatus(normalizeSampleStatus(order.getStatus()));
        sampleOrderMapper.updateById(order);
        
        // 删除旧明细
        sampleItemMapper.deleteBySampleNo(dto.getSampleNo());
        
        // 插入新明细
        if (dto.getItems() != null && !dto.getItems().isEmpty()) {
            for (SampleItem item : dto.getItems()) {
                normalizeSampleItem(item);
                item.setId(null); // 确保是新记录
                item.setSampleNo(dto.getSampleNo());
                sampleItemMapper.insert(item);
            }
        }
        
        return true;
    }
    
    @Override
    public boolean delete(String sampleNo) {
        LambdaQueryWrapper<SampleOrder> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SampleOrder::getSampleNo, sampleNo);
        wrapper.eq(SampleOrder::getIsDeleted, false);
        
        SampleOrder order = sampleOrderMapper.selectOne(wrapper);
        if (order == null) {
            return false;
        }
        if (!canAccessSample(getLoginUser(), order)) {
            return false;
        }

        // 优先使用按条件删除（有@TableLogic时为逻辑删除；无@TableLogic时为物理删除）
        int affected = sampleOrderMapper.delete(wrapper);
        if (affected > 0) {
            // 主表删除后，清理明细，避免脏数据
            sampleItemMapper.deleteBySampleNo(sampleNo);
            return true;
        }
        return false;
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateLogistics(LogisticsUpdateDTO dto) {
        LambdaQueryWrapper<SampleOrder> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SampleOrder::getSampleNo, dto.getSampleNo());
        wrapper.eq(SampleOrder::getIsDeleted, false);
        
        SampleOrder order = sampleOrderMapper.selectOne(wrapper);
        if (order == null) {
            return false;
        }
        if (!canAccessSample(getLoginUser(), order)) {
            return false;
        }
        
        // 更新物流信息
        order.setExpressCompany(dto.getExpressCompany());
        order.setTrackingNumber(dto.getTrackingNumber());
        
        // 发货日期默认等于送样日期（前端不再维护发货日期）
        if (StringUtils.hasText(dto.getShipDate())) {
            order.setShipDate(LocalDate.parse(dto.getShipDate()));
        } else if (order.getSendDate() != null) {
            order.setShipDate(order.getSendDate());
        }
        
        if (StringUtils.hasText(dto.getDeliveryDate())) {
            order.setDeliveryDate(LocalDate.parse(dto.getDeliveryDate()));
        }
        
        // 物流维护不再覆盖送样主状态（主状态仅：已完成/未完成）
        if (StringUtils.hasText(dto.getTrackingNumber()) && order.getShipDate() == null) {
            order.setShipDate(order.getSendDate() != null ? order.getSendDate() : LocalDate.now());
        }
        
        // 尝试查询物流信息
        if (StringUtils.hasText(dto.getTrackingNumber())) {
            try {
                queryAndUpdateLogistics(order);
            } catch (Exception e) {
                // 查询失败不影响主流程
                System.err.println("物流查询失败: " + e.getMessage());
            }
        }
        
        return sampleOrderMapper.updateById(order) > 0;
    }
    
    @Override
    public boolean updateStatus(String sampleNo, String newStatus, String reason) {
        LambdaQueryWrapper<SampleOrder> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SampleOrder::getSampleNo, sampleNo);
        wrapper.eq(SampleOrder::getIsDeleted, false);
        
        SampleOrder order = sampleOrderMapper.selectOne(wrapper);
        if (order == null) {
            return false;
        }
        
        order.setStatus(normalizeSampleStatus(newStatus));
        return sampleOrderMapper.updateById(order) > 0;
    }
    
    @Override
    public Map<String, Object> queryLogistics(String sampleNo) {
        return queryLogistics(sampleNo, null, null);
    }

    @Override
    public Map<String, Object> queryLogistics(String sampleNo, String trackingNumber, String expressCompany) {
        LambdaQueryWrapper<SampleOrder> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SampleOrder::getSampleNo, sampleNo);
        wrapper.eq(SampleOrder::getIsDeleted, false);

        SampleOrder order = sampleOrderMapper.selectOne(wrapper);
        if (order == null) {
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("message", "送样单不存在");
            return result;
        }

        boolean changed = false;
        if (StringUtils.hasText(trackingNumber)) {
            order.setTrackingNumber(trackingNumber.trim());
            changed = true;
        }
        if (StringUtils.hasText(expressCompany)) {
            order.setExpressCompany(expressCompany.trim());
            changed = true;
        }
        if (changed) {
            sampleOrderMapper.updateById(order);
        }

        if (!StringUtils.hasText(order.getTrackingNumber())) {
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("message", "未找到快递单号");
            return result;
        }
        
        return queryAndUpdateLogistics(order);
    }
      /**
     * 查询并更新物流信息
     * 这里需要调用快递100 API或其他物流查询API
     */
    private Map<String, Object> queryAndUpdateLogistics(SampleOrder order) {
        Map<String, Object> result = new HashMap<>();

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
            String normalizedTrackingNo = normalizeTrackingNumber(order.getTrackingNumber());
            if (!StringUtils.hasText(normalizedTrackingNo)) {
                result.put("success", false);
                result.put("message", "物流单号格式无效");
                return result;
            }

            List<String> companyCodes = resolveExpressCodeCandidates(order.getExpressCompany(), normalizedTrackingNo);
            if (companyCodes.isEmpty()) {
                result.put("success", false);
                result.put("message", "未识别快递公司，请先在物流中选择标准快递公司");
                result.put("carrierName", order.getExpressCompany());
                result.put("carrierNo", normalizedTrackingNo);
                return result;
            }

            String phoneTail4 = resolvePhoneTail4(order);
            if (!StringUtils.hasText(phoneTail4) && containsSfCarrierCode(companyCodes)) {
                result.put("success", false);
                result.put("message", "顺丰查询需提供收件手机号后4位，请补充联系人手机号");
                result.put("carrierName", order.getExpressCompany());
                result.put("carrierNo", normalizedTrackingNo);
                result.put("triedCompanyCodes", companyCodes);
                return result;
            }

            Map<String, Object> apiResp = null;
            String failMsg = "物流接口查询失败";
            boolean triedAutoCode = false;
            for (String companyCode : companyCodes) {
                apiResp = queryKuaidi100(companyCode, normalizedTrackingNo, phoneTail4);
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

            // 兜底：若返回“公司不支持/编码不支持”，再尝试快递100自动识别编码
            if ((apiResp == null || !isKuaidi100Success(apiResp)) && isUnsupportedCarrierMessage(failMsg)) {
                triedAutoCode = true;
                Map<String, Object> autoResp = queryKuaidi100("auto", normalizedTrackingNo, phoneTail4);
                if (isKuaidi100Success(autoResp)) {
                    apiResp = autoResp;
                } else {
                    failMsg = buildKuaidi100FailMessage(autoResp);
                    apiResp = null;
                }
            }

            if (apiResp == null || !isKuaidi100Success(apiResp)) {
                result.put("success", false);
                result.put("message", failMsg);
                result.put("carrierName", order.getExpressCompany());
                result.put("carrierNo", normalizedTrackingNo);
                List<String> triedCodes = new ArrayList<>(companyCodes);
                if (triedAutoCode && !triedCodes.contains("auto")) {
                    triedCodes.add("auto");
                }
                result.put("triedCompanyCodes", triedCodes);
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
            result.put("carrierName", order.getExpressCompany());
            result.put("carrierNo", normalizedTrackingNo);

            order.setLogisticsStatus(statusText);
            order.setLastLogisticsQueryTime(LocalDateTime.now());
            if ("已送达".equals(statusText) || "已签收".equals(statusText)) {
                if (order.getDeliveryDate() == null) {
                    order.setDeliveryDate(LocalDate.now());
                }
            }

            sampleOrderMapper.updateById(order);
            return result;
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "物流查询失败: " + e.getMessage());
            return result;
        }
    }

    private Map<String, Object> queryKuaidi100(String companyCode, String trackingNumber, String phoneTail4) throws Exception {
        Map<String, Object> param = new LinkedHashMap<>();
        param.put("com", companyCode);
        param.put("num", trackingNumber);
        if (StringUtils.hasText(phoneTail4) && isPhoneRequiredCarrier(companyCode)) {
            param.put("phone", phoneTail4);
        }

        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        String paramJson = mapper.writeValueAsString(param);
        String signRaw = paramJson + logisticsKey + logisticsCustomer;
        byte[] signBytes = java.util.Objects.requireNonNull((signRaw == null ? "" : signRaw).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        String sign = DigestUtils.md5DigestAsHex(signBytes).toUpperCase();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("customer", logisticsCustomer);
        body.add("sign", sign);
        body.add("param", paramJson);

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);
        RestTemplate restTemplate = buildLogisticsRestTemplate();
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
        if (!StringUtils.hasText(v)) return java.util.Collections.emptyList();
        Map<String, String> map = new HashMap<>();
        map.put("顺丰速运", "shunfeng");
        map.put("顺丰", "shunfeng");
        map.put("顺丰快递", "shunfeng");
        map.put("顺丰快运", "shunfengkuaiyun");
        map.put("顺丰快运物流", "shunfengkuaiyun");
        map.put("SF", "shunfeng");
        map.put("SF EXPRESS", "shunfeng");
        map.put("圆通速递", "yuantong");
        map.put("圆通", "yuantong");
        map.put("中通快递", "zhongtong");
        map.put("中通", "zhongtong");
        map.put("申通快递", "shentong");
        map.put("申通", "shentong");
        map.put("韵达快递", "yunda");
        map.put("韵达", "yunda");
        map.put("邮政EMS", "ems");
        map.put("EMS", "ems");
        map.put("京东物流", "jd");
        map.put("京东", "jd");
        map.put("德邦快递", "debangwuliu");
        map.put("德邦", "debangwuliu");
        map.put("跨越速运", "kuayue");
        map.put("跨越快递", "kuayue");
        map.put("跨越", "kuayue");

        java.util.LinkedHashSet<String> codes = new java.util.LinkedHashSet<>();

        // 优先使用物流公司主数据中的 company_code
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

        // 标准化后做一次模糊匹配，兼容别名/后缀差异
        String normalized = normalizeCarrierNameForMatch(v);
        if (StringUtils.hasText(normalized)) {
            for (Map.Entry<String, String> entry : map.entrySet()) {
                String keyNorm = normalizeCarrierNameForMatch(entry.getKey());
                if (StringUtils.hasText(keyNorm) && normalized.contains(keyNorm)) {
                    codes.add(entry.getValue());
                }
            }
        }

        // 顺丰增强：根据公司名/单号前缀补充 sf 兜底
        if (isSfTrackingNumber(trackingNo) || v.contains("顺丰") || v.toUpperCase().contains("SF")) {
            codes.add("shunfeng");
            codes.add("sf");
            if (v.contains("快运")) {
                codes.add("shunfengkuaiyun");
            }
        }

        // 跨越在不同渠道可能存在不同编码，按顺序兜底重试
        if (v.contains("跨越")) {
            codes.add("kuayue");
            codes.add("kuayuekuaiyun");
            codes.add("kyexp");
        }
        // 默认兜底：将原值当编码尝试一次
        codes.add(v);
        return new java.util.ArrayList<>(codes);
    }

    private String resolveCompanyCodeFromMaster(String carrierName) {
        if (!StringUtils.hasText(carrierName)) return "";
        try {
            QueryWrapper<LogisticsCompany> exactQw = new QueryWrapper<>();
            exactQw.eq("company_name", carrierName.trim()).eq("is_deleted", 0).last("LIMIT 1");
            LogisticsCompany exact = logisticsCompanyService.getOne(exactQw, false);
            if (exact != null && StringUtils.hasText(exact.getCompanyCode())) {
                String code = exact.getCompanyCode().trim();
                if (isLikelyKuaidiCompanyCode(code)) {
                    return code;
                }
            }

            QueryWrapper<LogisticsCompany> likeQw = new QueryWrapper<>();
            likeQw.like("company_name", carrierName.trim()).eq("is_deleted", 0).orderByDesc("updated_at").last("LIMIT 1");
            LogisticsCompany fuzzy = logisticsCompanyService.getOne(likeQw, false);
            if (fuzzy != null && StringUtils.hasText(fuzzy.getCompanyCode())) {
                String code = fuzzy.getCompanyCode().trim();
                if (isLikelyKuaidiCompanyCode(code)) {
                    return code;
                }
            }
        } catch (Exception ignored) {
        }
        return "";
    }

    private boolean isLikelyKuaidiCompanyCode(String code) {
        if (!StringUtils.hasText(code)) {
            return false;
        }
        return code.trim().matches("^[A-Za-z0-9_-]+$");
    }

    private String normalizeCarrierNameForMatch(String name) {
        if (!StringUtils.hasText(name)) {
            return "";
        }
        return name.trim()
                .toUpperCase(Locale.ROOT)
                .replaceAll("[\\s\\-_/（）()·,.，。]", "")
                .replace("速运", "")
                .replace("快递", "")
                .replace("快运", "")
                .replace("物流", "");
    }

    private boolean isSfTrackingNumber(String trackingNo) {
        if (!StringUtils.hasText(trackingNo)) {
            return false;
        }
        String normalized = trackingNo.trim().toUpperCase(Locale.ROOT);
        return normalized.startsWith("SF");
    }

    private String normalizeTrackingNumber(String trackingNo) {
        if (!StringUtils.hasText(trackingNo)) {
            return "";
        }
        String normalized = trackingNo.trim();
        normalized = normalized.replaceAll("[\\s\\u00A0]", "");
        normalized = normalized.replaceAll("[^0-9A-Za-z-]", "");
        return normalized.toUpperCase();
    }

    private String resolvePhoneTail4(SampleOrder order) {
        if (order == null) {
            return "";
        }

        // 1) 优先取样品单表头联系电话
        String tail4 = extractPhoneTail4(order.getContactPhone());
        if (StringUtils.hasText(tail4)) {
            return tail4;
        }

        // 2) 按客户ID从客户管理表获取
        if (order.getCustomerId() != null) {
            Customer byId = customerMapper.selectById(order.getCustomerId());
            tail4 = resolvePhoneTail4FromCustomer(byId);
            if (StringUtils.hasText(tail4)) {
                return tail4;
            }
        }

        // 3) 按客户代码/名称兜底匹配客户管理表
        String customerText = order.getCustomerName() == null ? "" : order.getCustomerName().trim();
        if (StringUtils.hasText(customerText)) {
            Customer byCode = customerMapper.selectByCustomerCode(customerText);
            tail4 = resolvePhoneTail4FromCustomer(byCode);
            if (StringUtils.hasText(tail4)) {
                return tail4;
            }

            Customer byName = customerMapper.selectByShortNameOrCustomerName(customerText, customerText);
            tail4 = resolvePhoneTail4FromCustomer(byName);
            if (StringUtils.hasText(tail4)) {
                return tail4;
            }
        }

        return "";
    }

    private String resolvePhoneTail4FromCustomer(Customer customer) {
        if (customer == null) {
            return "";
        }

        // 客户主表公司电话
        String tail4 = extractPhoneTail4(customer.getCompanyPhone());
        if (StringUtils.hasText(tail4)) {
            return tail4;
        }

        // 客户主联系人手机号/电话
        if (customer.getId() != null) {
            CustomerContact primary = customerContactMapper.selectPrimaryContact(customer.getId());
            if (primary != null) {
                tail4 = resolvePhoneTail4FromContact(primary);
                if (StringUtils.hasText(tail4)) {
                    return tail4;
                }
            }

            // 客户资料-联系人列表兜底（优先收货人，其次其他联系人）
            List<CustomerContact> contacts = customerContactMapper.selectByCustomerId(customer.getId());
            if (contacts != null && !contacts.isEmpty()) {
                for (CustomerContact one : contacts) {
                    if (one != null && Integer.valueOf(1).equals(one.getIsReceiver())) {
                        tail4 = resolvePhoneTail4FromContact(one);
                        if (StringUtils.hasText(tail4)) {
                            return tail4;
                        }
                    }
                }
                for (CustomerContact one : contacts) {
                    tail4 = resolvePhoneTail4FromContact(one);
                    if (StringUtils.hasText(tail4)) {
                        return tail4;
                    }
                }
            }
        }

        return "";
    }

    private String resolvePhoneTail4FromContact(CustomerContact contact) {
        if (contact == null) {
            return "";
        }
        String tail4 = extractPhoneTail4(contact.getContactMobile());
        if (StringUtils.hasText(tail4)) {
            return tail4;
        }
        tail4 = extractPhoneTail4(contact.getContactPhone());
        if (StringUtils.hasText(tail4)) {
            return tail4;
        }
        return "";
    }

    private String extractPhoneTail4(String phone) {
        if (!StringUtils.hasText(phone)) {
            return "";
        }
        String digits = phone.replaceAll("\\D", "");
        if (digits.length() < 4) {
            return "";
        }
        return digits.substring(digits.length() - 4);
    }

    private boolean isPhoneRequiredCarrier(String companyCode) {
        String code = companyCode == null ? "" : companyCode.trim().toLowerCase();
        return "shunfeng".equals(code)
                || "sf".equals(code)
                || "sfexpress".equals(code)
                || "shunfengkuaiyun".equals(code);
    }

    private boolean containsSfCarrierCode(List<String> companyCodes) {
        if (companyCodes == null || companyCodes.isEmpty()) {
            return false;
        }
        for (String one : companyCodes) {
            if (isPhoneRequiredCarrier(one)) {
                return true;
            }
        }
        return false;
    }

    private String mapKuaidi100StateToStatus(String state) {
        if ("3".equals(state)) return "已送达";
        if ("5".equals(state)) return "派件中";
        if ("4".equals(state) || "6".equals(state)) return "已拒收";
        if ("0".equals(state) || "1".equals(state) || "2".equals(state) || "7".equals(state) || "8".equals(state)) {
            return "运输中";
        }
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

        // 兼容：不同接口字段不一致（returnCode/status/code/result/message）
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
                || failMsg.contains("查询无结果")
                || failMsg.contains("参数异常")
                || failMsg.contains("验证错误");
    }

    private boolean isUnsupportedCarrierMessage(String failMsg) {
        if (!StringUtils.hasText(failMsg)) {
            return false;
        }
        return failMsg.contains("不支持此快递公司")
                || failMsg.contains("公司编码")
                || failMsg.contains("快递公司不存在")
                || failMsg.contains("快递公司错误");
    }
      @Override
    @Transactional(rollbackFor = Exception.class)
    public String convertToOrder(String sampleNo) {
        // Future enhancement: 实现完整的转订单逻辑
        // 1. 查询送样订单
        // 2. 创建销售订单
        // 3. 复制明细信息
        // 4. 更新送样订单状态
        
        SampleOrderDTO sample = getDetailBySampleNo(sampleNo);
        if (sample == null) {
            throw new RuntimeException("送样订单不存在");
        }
        
        if (Boolean.TRUE.equals(sample.getConvertedToOrder())) {
            throw new RuntimeException("该送样订单已转为订单");
        }
        
        // 生成订单号（这里简化处理）
        String orderNo = "SO" + System.currentTimeMillis();
        
        // 更新送样订单
        LambdaQueryWrapper<SampleOrder> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SampleOrder::getIsDeleted, false);
        wrapper.eq(SampleOrder::getSampleNo, sampleNo);
        SampleOrder order = sampleOrderMapper.selectOne(wrapper);
        order.setConvertedToOrder(true);
        order.setOrderNo(orderNo);
        sampleOrderMapper.updateById(order);
        
        return orderNo;
    }
    
    @Override
    public String generateSampleNo() {
        String sampleNo = sampleOrderMapper.generateSampleNo();
        if (sampleNo == null || sampleNo.isEmpty()) {
            // 如果数据库函数失败，使用Java生成
            String dateStr = LocalDate.now().format(DateTimeFormatter.ofPattern("yyMMdd"));
            return "SP" + dateStr + "-001";
        }
        return sampleNo;
    }    /**
     * 转换为DTO
     */
    private SampleOrderDTO convertToDTO(SampleOrder order) {
        SampleOrderDTO dto = new SampleOrderDTO();
        if (order != null) {
            BeanUtils.copyProperties(order, dto);
            dto.setStatus(normalizeSampleStatus(dto.getStatus()));

            // 制单人（账号）
            dto.setCreateByName(order.getCreateBy());

            // 申请人=客户销售负责人（从数据库读取）
            dto.setApplicantName(resolveApplicantName(order));
            
            // 格式化时间
            if (order.getCreateTime() != null) {
                dto.setCreateTime(order.getCreateTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
            }
            if (order.getUpdateTime() != null) {
                dto.setUpdateTime(order.getUpdateTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
            }
        }
        
        return dto;
    }

    private String normalizeSampleStatus(String status) {
        String raw = String.valueOf(status == null ? "" : status).trim();
        if (raw.isEmpty()) {
            return SAMPLE_STATUS_UNCOMPLETED;
        }
        String upper = raw.toUpperCase(Locale.ROOT);
        if (SAMPLE_STATUS_COMPLETED.equals(raw)
                || "COMPLETED".equals(upper)
                || "DONE".equals(upper)
                || "已送达".equals(raw)
                || "已签收".equals(raw)) {
            return SAMPLE_STATUS_COMPLETED;
        }
        return SAMPLE_STATUS_UNCOMPLETED;
    }

    private String resolveApplicantName(SampleOrder order) {
        if (order == null || order.getCustomerId() == null) {
            return "";
        }
        try {
            Customer customer = customerMapper.selectById(order.getCustomerId());
            if (customer == null || customer.getSalesUserId() == null) {
                return "";
            }
            User salesUser = userMapper.selectById(customer.getSalesUserId());
            if (salesUser == null) {
                return "";
            }
            if (StringUtils.hasText(salesUser.getRealName())) {
                return salesUser.getRealName();
            }
            return StringUtils.hasText(salesUser.getUsername()) ? salesUser.getUsername() : "";
        } catch (Exception ignore) {
            return "";
        }
    }
    
    @Override
    public Map<String, Object> importFromExcel(MultipartFile file) throws Exception {
        Map<String, Object> result = new HashMap<>();
        int successCount = 0;
        int failCount = 0;
        StringBuilder errorMsg = new StringBuilder();

        try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);
            int lastRow = sheet.getLastRowNum();

            for (int i = 1; i <= lastRow; i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;

                try {
                    SampleOrder order = new SampleOrder();
                    
                    // 送样编号（如果为空则自动生成）
                    String sampleNo = getCellStringValue(row.getCell(0));
                    if (sampleNo == null || sampleNo.isEmpty()) {
                        sampleNo = generateSampleNo();
                    } else {
                        // 检查编号是否已存在
                        LambdaQueryWrapper<SampleOrder> wrapper = new LambdaQueryWrapper<>();
                        wrapper.eq(SampleOrder::getSampleNo, sampleNo);
                        if (sampleOrderMapper.selectCount(wrapper) > 0) {
                            errorMsg.append("第").append(i + 1).append("行：送样编号已存在\n");
                            failCount++;
                            continue;
                        }
                    }
                    order.setSampleNo(sampleNo);
                    
                    // 客户名称（必填）
                    String customerName = getCellStringValue(row.getCell(1));
                    if (customerName == null || customerName.isEmpty()) {
                        errorMsg.append("第").append(i + 1).append("行：客户名称不能为空\n");
                        failCount++;
                        continue;
                    }
                    order.setCustomerName(customerName);
                    
                    order.setContactName(getCellStringValue(row.getCell(2)));
                    order.setContactPhone(getCellStringValue(row.getCell(3)));                    order.setContactAddress(getCellStringValue(row.getCell(4)));
                    String sendDateStr = getCellStringValue(row.getCell(5));
                    if (sendDateStr != null && !sendDateStr.isEmpty()) {
                        order.setSendDate(LocalDate.parse(sendDateStr, DateTimeFormatter.ofPattern("yyyy-MM-dd")));
                    }
                    order.setExpressCompany(getCellStringValue(row.getCell(6)));
                    order.setTrackingNumber(getCellStringValue(row.getCell(7)));
                    
                    String status = getCellStringValue(row.getCell(8));
                    order.setStatus(normalizeSampleStatus(status));
                    
                    order.setRemark(getCellStringValue(row.getCell(9)));
                    order.setCreateTime(LocalDateTime.now());
                    order.setUpdateTime(LocalDateTime.now());

                    sampleOrderMapper.insert(order);
                    successCount++;
                } catch (Exception e) {
                    errorMsg.append("第").append(i + 1).append("行：").append(e.getMessage()).append("\n");
                    failCount++;
                }
            }
        }

        result.put("success", failCount == 0);
        result.put("successCount", successCount);
        result.put("failCount", failCount);
        result.put("message", failCount > 0 ? errorMsg.toString() : "导入成功");
        return result;
    }
    
    @Override
    public ResponseResult<?> exportToExcel(String customerName, String status) {
        LambdaQueryWrapper<SampleOrder> wrapper = new LambdaQueryWrapper<>();

        LoginUser loginUser = getLoginUser();
        if (loginUser != null && !hasRole(loginUser, "admin")) {
            Long uid = getCurrentUserId(loginUser);
            List<Long> allowedIds = customerMapper.selectCustomerIdsByOwner(uid);
            if (allowedIds == null || allowedIds.isEmpty()) {
                return new ResponseResult<>(20000, "导出成功", Collections.emptyList());
            }
            wrapper.in(SampleOrder::getCustomerId, allowedIds);
        }
        
        if (StringUtils.hasText(customerName)) {
            wrapper.like(SampleOrder::getCustomerName, customerName);
        }
        if (StringUtils.hasText(status)) {
            wrapper.eq(SampleOrder::getStatus, status);
        }
        wrapper.orderByDesc(SampleOrder::getCreateTime);
        
        List<SampleOrder> list = sampleOrderMapper.selectList(wrapper);
        
        List<Map<String, Object>> exportData = list.stream().map(order -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("sampleNo", order.getSampleNo());
            map.put("customerName", order.getCustomerName());
            map.put("contactName", order.getContactName());
            map.put("contactPhone", order.getContactPhone());
            map.put("contactAddress", order.getContactAddress());
            map.put("sendDate", order.getSendDate());
            map.put("expressCompany", order.getExpressCompany());
            map.put("trackingNumber", order.getTrackingNumber());
            map.put("status", normalizeSampleStatus(order.getStatus()));
            map.put("remark", order.getRemark());
            return map;
        }).collect(Collectors.toList());
        
        return new ResponseResult<>(20000, "导出成功", exportData);
    }

    private LoginUser getLoginUser() {
        org.springframework.security.core.Authentication authentication = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof LoginUser) {
            return (LoginUser) authentication.getPrincipal();
        }
        return null;
    }

    private boolean hasRole(LoginUser loginUser, String role) {
        return loginUser != null && loginUser.getPermissions() != null && loginUser.getPermissions().contains(role);
    }

    private Long getCurrentUserId(LoginUser loginUser) {
        return loginUser != null && loginUser.getUser() != null ? loginUser.getUser().getId() : null;
    }

    private boolean canAccessSample(LoginUser loginUser, SampleOrder order) {
        if (order == null) return true;
        if (loginUser == null) return false;
        if (hasRole(loginUser, "admin") 
                || hasRole(loginUser, "plan")
                || hasRole(loginUser, "scheduler")
                || hasRole(loginUser, "coating")) return true;
        if (hasRole(loginUser, "production") || hasRole(loginUser, "packaging") || hasRole(loginUser, "packing")) {
            return true;
        }
        Long uid = getCurrentUserId(loginUser);
        if (uid == null) return false;
        List<Long> allowedIds = customerMapper.selectCustomerIdsByOwner(uid);
        return allowedIds != null && order.getCustomerId() != null && allowedIds.contains(order.getCustomerId());
    }

    /**
     * 统一送样明细字段逻辑：
     * 仅保留 thickness/width/length/quantity 作为核心规格字段，
     * 清理历史 model/specification/batchNo，避免多套逻辑并存。
     */
    private void normalizeSampleItem(SampleItem item) {
        if (item == null) {
            return;
        }

        item.setThickness(toPositiveOrNull(item.getThickness()));
        item.setWidth(toPositiveOrNull(item.getWidth()));
        item.setLength(toPositiveOrNull(item.getLength()));

        Integer qty = item.getQuantity();
        item.setQuantity(qty == null || qty <= 0 ? 1 : qty);

        if (!StringUtils.hasText(item.getUnit())) {
            item.setUnit("卷");
        }

        item.setModel(null);
        item.setSpecification(null);
        item.setBatchNo(null);
    }

    private BigDecimal toPositiveOrNull(BigDecimal value) {
        if (value == null) {
            return null;
        }
        return value.compareTo(BigDecimal.ZERO) > 0 ? value : null;
    }
      // 辅助方法：获取单元格字符串值
    private String getCellStringValue(Cell cell) {
        if (cell == null) return null;
        try {
            switch (cell.getCellType()) {
                case STRING:
                    return cell.getStringCellValue().trim();
                case NUMERIC:
                    if (DateUtil.isCellDateFormatted(cell)) {
                        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd");
                        return sdf.format(cell.getDateCellValue());
                    }
                    return String.valueOf((long) cell.getNumericCellValue());
                case BOOLEAN:
                    return String.valueOf(cell.getBooleanCellValue());
                case FORMULA:
                    return cell.getCellFormula();
                default:
                    return null;
            }
        } catch (Exception e) {
            return null;
        }
    }
}
