package com.fine.serviceIMPL.purchase;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fine.Dao.purchase.PurchaseQuotationItemMapper;
import com.fine.Dao.purchase.PurchaseQuotationMapper;
import com.fine.Utils.ResponseResult;
import com.fine.modle.LoginUser;
import com.fine.modle.purchase.PurchaseQuotation;
import com.fine.modle.purchase.PurchaseQuotationItem;
import com.fine.service.purchase.PurchaseQuotationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class PurchaseQuotationServiceImpl extends ServiceImpl<PurchaseQuotationMapper, PurchaseQuotation> implements PurchaseQuotationService {

    private static final long EDIT_WINDOW_MILLIS = 24L * 60L * 60L * 1000L;
    private static final LocalDate LONG_TERM_VALID_UNTIL_DATE = LocalDate.of(2099, 12, 31);
    private static final ZoneId BIZ_ZONE = ZoneId.of("Asia/Shanghai");

    @Autowired
    private PurchaseQuotationMapper quotationMapper;
    @Autowired
    private PurchaseQuotationItemMapper quotationItemMapper;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Override
    public ResponseResult<?> list(Integer pageNum, Integer pageSize, String supplier, String status, String materialCode) {
        Page<PurchaseQuotation> page = new Page<>(pageNum == null ? 1 : pageNum, pageSize == null ? 10 : pageSize);
        IPage<PurchaseQuotation> result = quotationMapper.selectPaged(page, supplier, status, materialCode);
        return ResponseResult.success(result);
    }

    @Override
    public ResponseResult<?> detail(Long id) {
        PurchaseQuotation quotation = quotationMapper.selectById(id);
        if (quotation == null || quotation.getIsDeleted() == 1) {
            return new ResponseResult<>(404, "报价单不存在");
        }
        LambdaQueryWrapper<PurchaseQuotationItem> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PurchaseQuotationItem::getQuotationId, id).eq(PurchaseQuotationItem::getIsDeleted, 0);
        List<PurchaseQuotationItem> items = quotationItemMapper.selectList(wrapper);
        quotation.setItems(items);
        return ResponseResult.success(quotation);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResponseResult<?> create(PurchaseQuotation quotation) {
        ensurePurchaseQuotationAuditSchema();
        if (!StringUtils.hasText(quotation.getStatus())) {
            quotation.setStatus("draft");
        }
        ResponseResult<?> unitCheck = validateAndApplyPricingUnits(quotation.getItems());
        if (unitCheck != null) {
            return unitCheck;
        }
        String currentUser = getCurrentUsername();
        Date now = new Date();
        quotation.setQuotationDate(quotation.getQuotationDate() != null ? quotation.getQuotationDate() : currentBusinessDate());
        quotation.setValidUntil(defaultLongTermValidUntil(quotation.getValidUntil()));
        quotation.setIsDeleted(0);
        quotation.setCreatedBy(currentUser);
        quotation.setUpdatedBy(currentUser);
        quotation.setCreatedAt(now);
        quotation.setUpdatedAt(now);

        int retryTimes = 5;
        for (int attempt = 0; attempt < retryTimes; attempt++) {
            if (!StringUtils.hasText(quotation.getQuotationNo()) || attempt > 0) {
                quotation.setQuotationNo(generateQuotationNo(attempt));
            }
            try {
                quotationMapper.insert(quotation);
                break;
            } catch (org.springframework.dao.DuplicateKeyException ex) {
                if (attempt == retryTimes - 1) {
                    throw ex;
                }
            }
        }

        if (!CollectionUtils.isEmpty(quotation.getItems())) {
            Map<String, String> unitCache = new HashMap<>();
            for (PurchaseQuotationItem item : quotation.getItems()) {
                item.setQuotationId(quotation.getId());
                item.setIsDeleted(0);
                item.setCreatedBy(currentUser);
                item.setUpdatedBy(currentUser);
                item.setCreatedAt(now);
                item.setUpdatedAt(now);
                applyPricingUnitFromMaterialMaster(item, unitCache);
                calculateItem(item);
                quotationItemMapper.insert(item);
            }
        }
        appendChangeLog("purchase", quotation.getId(), quotation.getQuotationNo(), "NEW_QUOTATION", currentUser, "新报价");
        return ResponseResult.success(quotation);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResponseResult<?> updateQuotation(PurchaseQuotation quotation) {
        ensurePurchaseQuotationAuditSchema();
        PurchaseQuotation existing = quotationMapper.selectById(quotation.getId());
        if (existing == null || existing.getIsDeleted() == 1) {
            return new ResponseResult<>(404, "报价单不存在");
        }
        Date now = new Date();
        Date editableDeadline = resolveEditableDeadline(existing);
        if (!isAdminUser() && editableDeadline != null && now.after(editableDeadline)) {
            return new ResponseResult<>(409, "该采购报价已超过24小时，不允许修改；如需调整请新建报价单");
        }
        ResponseResult<?> unitCheck = validateAndApplyPricingUnits(quotation.getItems());
        if (unitCheck != null) {
            return unitCheck;
        }
        String currentUser = getCurrentUsername();
        quotation.setQuotationDate(quotation.getQuotationDate() != null ? quotation.getQuotationDate() : (existing.getQuotationDate() != null ? existing.getQuotationDate() : currentBusinessDate()));
        quotation.setValidUntil(defaultLongTermValidUntil(quotation.getValidUntil()));
        quotation.setCreatedAt(existing.getCreatedAt());
        quotation.setCreatedBy(existing.getCreatedBy());
        quotation.setUpdatedBy(currentUser);
        quotation.setUpdatedAt(now);
        quotation.setIsDeleted(0);
        quotationMapper.updateById(quotation);

        LambdaQueryWrapper<PurchaseQuotationItem> del = new LambdaQueryWrapper<>();
        del.eq(PurchaseQuotationItem::getQuotationId, quotation.getId());
        quotationItemMapper.delete(del);

        if (!CollectionUtils.isEmpty(quotation.getItems())) {
            Map<String, String> unitCache = new HashMap<>();
            for (PurchaseQuotationItem item : quotation.getItems()) {
                item.setId(null);
                item.setQuotationId(quotation.getId());
                item.setIsDeleted(0);
                item.setCreatedBy(currentUser);
                item.setUpdatedBy(currentUser);
                item.setCreatedAt(now);
                item.setUpdatedAt(now);
                applyPricingUnitFromMaterialMaster(item, unitCache);
                calculateItem(item);
                quotationItemMapper.insert(item);
            }
        }
        appendChangeLog("purchase", quotation.getId(), quotation.getQuotationNo(), "MODIFY_QUOTATION", currentUser, "修改报价");
        return ResponseResult.success(quotation);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResponseResult<?> reQuote(Long id) {
        ensurePurchaseQuotationAuditSchema();
        PurchaseQuotation existing = quotationMapper.selectById(id);
        if (existing == null || existing.getIsDeleted() == 1) {
            return new ResponseResult<>(404, "报价单不存在");
        }

        LambdaQueryWrapper<PurchaseQuotationItem> itemWrapper = new LambdaQueryWrapper<>();
        itemWrapper.eq(PurchaseQuotationItem::getQuotationId, id).eq(PurchaseQuotationItem::getIsDeleted, 0);
        List<PurchaseQuotationItem> oldItems = quotationItemMapper.selectList(itemWrapper);
        ResponseResult<?> unitCheck = validateAndApplyPricingUnits(oldItems);
        if (unitCheck != null) {
            return unitCheck;
        }

        String currentUser = getCurrentUsername();
        Date now = new Date();

        PurchaseQuotation newQuotation = new PurchaseQuotation();
        newQuotation.setQuotationNo(generateQuotationNo());
        newQuotation.setSupplier(existing.getSupplier());
        newQuotation.setContactPerson(existing.getContactPerson());
        newQuotation.setContactPhone(existing.getContactPhone());
        newQuotation.setQuotationDate(currentBusinessDate());
        newQuotation.setValidUntil(defaultLongTermValidUntil(null));
        newQuotation.setStatus("accepted");
        newQuotation.setRemark(existing.getRemark());
        newQuotation.setIsDeleted(0);
        newQuotation.setCreatedBy(currentUser);
        newQuotation.setUpdatedBy(currentUser);
        newQuotation.setCreatedAt(now);
        newQuotation.setUpdatedAt(now);
        quotationMapper.insert(newQuotation);

        if (!CollectionUtils.isEmpty(oldItems)) {
            Map<String, String> unitCache = new HashMap<>();
            for (PurchaseQuotationItem old : oldItems) {
                PurchaseQuotationItem item = new PurchaseQuotationItem();
                item.setQuotationId(newQuotation.getId());
                item.setMaterialCode(old.getMaterialCode());
                item.setMaterialName(old.getMaterialName());
                item.setSpecifications(old.getSpecifications());
                item.setLength(old.getLength());
                item.setWidth(old.getWidth());
                item.setThickness(old.getThickness());
                item.setQuantity(old.getQuantity());
                item.setUnit(old.getUnit());
                item.setSqm(old.getSqm());
                item.setUnitPrice(old.getUnitPrice());
                item.setAmount(old.getAmount());
                item.setRemark(old.getRemark());
                item.setIsDeleted(0);
                item.setCreatedBy(currentUser);
                item.setUpdatedBy(currentUser);
                item.setCreatedAt(now);
                item.setUpdatedAt(now);
                applyPricingUnitFromMaterialMaster(item, unitCache);
                calculateItem(item);
                quotationItemMapper.insert(item);
            }
        }

        appendChangeLog("purchase", newQuotation.getId(), newQuotation.getQuotationNo(), "REQUOTE", currentUser, "重报自: " + existing.getQuotationNo());
        return ResponseResult.success(newQuotation);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResponseResult<?> deleteQuotation(Long id) {
        ensurePurchaseQuotationAuditSchema();
        PurchaseQuotation quotation = quotationMapper.selectById(id);
        if (quotation == null) {
            return new ResponseResult<>(404, "报价单不存在");
        }

        String currentUser = getCurrentUsername();
        Date now = new Date();

        LambdaUpdateWrapper<PurchaseQuotation> quotationDeleteWrapper = new LambdaUpdateWrapper<>();
        quotationDeleteWrapper
                .eq(PurchaseQuotation::getId, id)
                .eq(PurchaseQuotation::getIsDeleted, 0)
                .set(PurchaseQuotation::getIsDeleted, 1)
                .set(PurchaseQuotation::getUpdatedBy, currentUser)
                .set(PurchaseQuotation::getUpdatedAt, now);
        int affected = quotationMapper.update(null, quotationDeleteWrapper);
        if (affected <= 0) {
            return new ResponseResult<>(409, "报价单删除失败或已被删除");
        }

        LambdaUpdateWrapper<PurchaseQuotationItem> itemDeleteWrapper = new LambdaUpdateWrapper<>();
        itemDeleteWrapper
                .eq(PurchaseQuotationItem::getQuotationId, id)
                .eq(PurchaseQuotationItem::getIsDeleted, 0)
                .set(PurchaseQuotationItem::getIsDeleted, 1)
            .set(PurchaseQuotationItem::getUpdatedBy, currentUser)
            .set(PurchaseQuotationItem::getUpdatedAt, now);
        quotationItemMapper.update(null, itemDeleteWrapper);

        appendChangeLog("purchase", quotation.getId(), quotation.getQuotationNo(), "DELETE_QUOTATION", currentUser, "删除报价");

        return ResponseResult.success();
    }

    @Override
    public String generateQuotationNo() {
        return generateQuotationNo(0);
    }

    private String generateQuotationNo(int attempt) {
        String datePart = new SimpleDateFormat("yyyyMMdd").format(new Date());
        String timePart = new SimpleDateFormat("HHmmssSSS").format(new Date());
        int randomPart = ThreadLocalRandom.current().nextInt(1000, 10000);
        if (attempt > 0) {
            return String.format("PQ-%s-%s-%d-%d", datePart, timePart, attempt, randomPart);
        }
        return String.format("PQ-%s-%s-%d", datePart, timePart, randomPart);
    }

    @SuppressWarnings("unused")
    private void calculateTotals(PurchaseQuotation quotation) {
        BigDecimal totalArea = BigDecimal.ZERO;
        if (!CollectionUtils.isEmpty(quotation.getItems())) {
            for (PurchaseQuotationItem item : quotation.getItems()) {
                calculateItem(item);
                // 仅薄膜类（有宽度+长度）累计面积；其他原材料sqm承载总重
                if (item.getSqm() != null && item.getWidth() != null && item.getLength() != null) {
                    totalArea = totalArea.add(item.getSqm());
                }
            }
        }
        quotation.setTotalArea(totalArea);
    }

    private void calculateItem(PurchaseQuotationItem item) {
        if (item == null) {
            return;
        }
        String pricingUnit = normalizePricingUnit(item.getUnit());
        if ("㎡".equals(pricingUnit)) {
            if (item.getWidth() != null && item.getLength() != null && item.getQuantity() != null) {
                BigDecimal sqm = item.getWidth().divide(new BigDecimal(1000), 6, BigDecimal.ROUND_HALF_UP)
                        .multiply(item.getLength()).multiply(new BigDecimal(item.getQuantity()));
                item.setSqm(sqm.setScale(2, BigDecimal.ROUND_HALF_UP));
                if (item.getUnitPrice() != null) {
                    item.setAmount(sqm.multiply(item.getUnitPrice()).setScale(2, BigDecimal.ROUND_HALF_UP));
                }
                return;
            }

            if (item.getSqm() != null && item.getUnitPrice() != null) {
                item.setAmount(item.getSqm().multiply(item.getUnitPrice()).setScale(2, BigDecimal.ROUND_HALF_UP));
            }
            return;
        }

        if ("kg".equals(pricingUnit)) {
            if (item.getSqm() != null && item.getUnitPrice() != null) {
                item.setAmount(item.getSqm().multiply(item.getUnitPrice()).setScale(2, BigDecimal.ROUND_HALF_UP));
            }
            return;
        }

        if (item.getWidth() != null && item.getLength() != null && item.getQuantity() != null) {
            BigDecimal sqm = item.getWidth().divide(new BigDecimal(1000), 6, BigDecimal.ROUND_HALF_UP)
                    .multiply(item.getLength()).multiply(new BigDecimal(item.getQuantity()));
            item.setSqm(sqm.setScale(2, BigDecimal.ROUND_HALF_UP));
            if (item.getUnitPrice() != null) {
                item.setAmount(sqm.multiply(item.getUnitPrice()).setScale(2, BigDecimal.ROUND_HALF_UP));
            }
            return;
        }

        // 其他原材料：sqm承载总重，金额=总重*单价
        if (item.getSqm() != null && item.getUnitPrice() != null) {
            item.setAmount(item.getSqm().multiply(item.getUnitPrice()).setScale(2, BigDecimal.ROUND_HALF_UP));
        }
    }

    private void applyPricingUnitFromMaterialMaster(PurchaseQuotationItem item, Map<String, String> unitCache) {
        if (item == null) {
            return;
        }
        // 允许同料号在不同供应商报价中采用不同计价单位，不再强制按料号主数据覆盖
        item.setUnit(normalizeMasterUnit(item.getUnit()));
    }

    private ResponseResult<?> validateAndApplyPricingUnits(List<PurchaseQuotationItem> items) {
        if (CollectionUtils.isEmpty(items)) {
            return null;
        }
        for (PurchaseQuotationItem item : items) {
            if (item == null) {
                continue;
            }
            if (!StringUtils.hasText(item.getMaterialCode())) {
                return new ResponseResult<>(400, "存在未填写物料编码的明细，无法校验单位");
            }
            String finalUnit = normalizeMasterUnit(item.getUnit());
            if (!StringUtils.hasText(finalUnit)) {
                // 未传单位时，按数据形态兜底：有宽长默认为㎡，否则默认kg
                boolean looksFilm = item.getWidth() != null && item.getLength() != null;
                finalUnit = looksFilm ? "㎡" : "kg";
            }
            if (!"㎡".equals(finalUnit) && !"kg".equals(finalUnit)) {
                return new ResponseResult<>(400,
                        "料号[" + item.getMaterialCode() + "]单位不合法，仅支持 kg 或 ㎡");
            }
            item.setUnit(finalUnit);
        }
        return null;
    }

    private String normalizePricingUnit(String unit) {
        if (!StringUtils.hasText(unit)) {
            return null;
        }
        String raw = unit.trim();
        String upper = raw.toUpperCase();
        if (raw.contains("㎡") || raw.contains("平米") || raw.contains("平方米") || upper.contains("M²") || upper.contains("M2") || upper.contains("SQM")) {
            return "㎡";
        }
        if (raw.contains("公斤") || raw.contains("千克") || upper.contains("KG")) {
            return "kg";
        }
        return null;
    }

    private String normalizeMasterUnit(String unit) {
        if (!StringUtils.hasText(unit)) {
            return null;
        }
        String raw = unit.trim();
        String normalized = normalizePricingUnit(raw);
        return StringUtils.hasText(normalized) ? normalized : raw;
    }

    private Date resolveEditableDeadline(PurchaseQuotation quotation) {
        if (quotation == null) {
            return null;
        }
        Date base = quotation.getCreatedAt();
        if (base == null) {
            base = quotation.getUpdatedAt();
        }
        if (base == null) {
            base = quotation.getQuotationDate();
        }
        if (base == null) {
            return null;
        }
        return new Date(base.getTime() + EDIT_WINDOW_MILLIS);
    }

    private Date defaultLongTermValidUntil(Date validUntil) {
        if (validUntil != null) {
            return validUntil;
        }
        return toBusinessDate(LONG_TERM_VALID_UNTIL_DATE);
    }

    private Date currentBusinessDate() {
        return toBusinessDate(LocalDate.now(BIZ_ZONE));
    }

    private Date toBusinessDate(LocalDate localDate) {
        if (localDate == null) {
            return null;
        }
        // 使用中午12点规避 DATE/DATETIME 在跨时区转换时的“前一天”偏移
        LocalDateTime noon = localDate.atTime(12, 0);
        return Date.from(noon.atZone(BIZ_ZONE).toInstant());
    }

    private String getCurrentUsername() {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.getPrincipal() instanceof LoginUser) {
                LoginUser loginUser = (LoginUser) authentication.getPrincipal();
                if (loginUser.getUser() != null && StringUtils.hasText(loginUser.getUser().getUsername())) {
                    return loginUser.getUser().getUsername().trim();
                }
            }
        } catch (Exception ignored) {
        }
        return "系统";
    }

    private boolean isAdminUser() {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.getPrincipal() instanceof LoginUser) {
                LoginUser loginUser = (LoginUser) authentication.getPrincipal();
                return loginUser.getPermissions() != null && loginUser.getPermissions().contains("admin");
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    private void ensurePurchaseQuotationAuditSchema() {
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS quotation_change_log ("
                + "id BIGINT NOT NULL AUTO_INCREMENT,"
                + "biz_type VARCHAR(32) NOT NULL COMMENT 'sales/purchase',"
                + "quotation_id BIGINT NULL,"
                + "quotation_no VARCHAR(64) NULL,"
                + "action_type VARCHAR(64) NOT NULL COMMENT 'NEW_QUOTATION/MODIFY_QUOTATION/DELETE_QUOTATION',"
                + "operator VARCHAR(100) NULL,"
                + "action_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,"
                + "remark VARCHAR(255) NULL,"
                + "PRIMARY KEY (id),"
                + "INDEX idx_qcl_biz_quote (biz_type, quotation_id),"
                + "INDEX idx_qcl_quote_no (quotation_no),"
                + "INDEX idx_qcl_action_time (action_time)"
                + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='报价变更审计日志'");
    }

    private void appendChangeLog(String bizType,
                                 Long quotationId,
                                 String quotationNo,
                                 String actionType,
                                 String operator,
                                 String remark) {
        jdbcTemplate.update(
                "INSERT INTO quotation_change_log(biz_type, quotation_id, quotation_no, action_type, operator, action_time, remark) VALUES (?,?,?,?,?,NOW(),?)",
                bizType,
                quotationId,
                quotationNo,
                actionType,
                operator,
                remark
        );
    }
}
