package com.fine.serviceIMPL.stock;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fine.Dao.purchase.PurchaseOrderItemMapper;
import com.fine.Dao.purchase.PurchaseOrderMapper;
import com.fine.Dao.rd.TapeFormulaMapper;
import com.fine.Dao.schedule.ManualScheduleMapper;
import com.fine.Dao.stock.*;
import com.fine.modle.PurchaseOrder;
import com.fine.modle.PurchaseOrderItem;
import com.fine.model.stock.*;
import com.fine.model.stock.enums.ChemicalRequisitionStatus;
import com.fine.modle.rd.TapeFormula;
import com.fine.modle.rd.TapeFormulaItem;
import com.fine.modle.rd.TapeRawMaterial;
import com.fine.service.stock.ChemicalRequisitionService;
import com.fine.service.stock.ChemicalStockService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ChemicalRequisitionServiceImpl implements ChemicalRequisitionService {

    @Autowired
    private ManualScheduleMapper manualScheduleMapper;
    @Autowired
    private TapeFormulaMapper tapeFormulaMapper;
    @Autowired
    private ChemicalStockMapper chemicalStockMapper;
    @Autowired
    private ChemicalStockDetailMapper chemicalStockDetailMapper;
    @Autowired
    private FilmStockMapper filmStockMapper;
    @Autowired
    private FilmStockDetailMapper filmStockDetailMapper;
    @Autowired
    private ChemicalMaterialLockMapper chemicalMaterialLockMapper;
    @Autowired
    private ChemicalPurchaseRequestMapper chemicalPurchaseRequestMapper;
    @Autowired
    private ChemicalPurchaseRequestItemMapper chemicalPurchaseRequestItemMapper;

    @Autowired
    private PurchaseOrderMapper purchaseOrderMapper;

    @Autowired
    private PurchaseOrderItemMapper purchaseOrderItemMapper;

    @Autowired
    private ChemicalRequisitionLogMapper chemicalRequisitionLogMapper;

    @Autowired
    private ChemicalStockService chemicalStockService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> generateFromCoatingPlan(LocalDate planDate, Long scheduleId, String orderNo, String materialCode) {
        LocalDate targetDate = planDate == null ? LocalDate.now() : planDate;
        String normalizedOrderNo = trim(orderNo);
        String normalizedMaterialCode = trim(materialCode);
        // 当已指定排程ID时，不再强依赖前端传入料号（可能是拼接展示文本），避免把正确排程过滤掉
        String effectiveMaterialCode = (scheduleId != null && scheduleId > 0) ? null : normalizedMaterialCode;

        List<Map<String, Object>> plans = manualScheduleMapper.selectCoatingPlansForChemical(targetDate, normalizedOrderNo, effectiveMaterialCode);
        if ((plans == null || plans.isEmpty())
                && (scheduleId != null || normalizedOrderNo != null || normalizedMaterialCode != null)) {
            plans = manualScheduleMapper.selectCoatingPlansForChemicalFallback(scheduleId, normalizedOrderNo, effectiveMaterialCode);
        }

        int lockCount = 0;
        int reqCount = 0;
        Set<String> requestNos = new LinkedHashSet<>();
        List<Map<String, Object>> missingFormulaPlans = new ArrayList<>();
        Map<String, TapeRawMaterial> rawMaterialCache = new HashMap<>();

        if (plans == null || plans.isEmpty()) {
            return resultMap(0, 0, requestNos, missingFormulaPlans);
        }

        for (Map<String, Object> plan : plans) {
            Long planScheduleId = toLong(plan.get("schedule_id"));
            String oNo = str(plan.get("order_no"));
            String finishedCodeRaw = str(plan.get("material_code"));
            String finishedCode = normalizeFinishedCodeForFormula(finishedCodeRaw, str(plan.get("material_name")));
            String finishedName = str(plan.get("material_name"));
            BigDecimal coatingArea = toDecimal(plan.get("coating_area"));
            if (finishedCode == null || coatingArea.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }

            TapeFormula formula = resolveFormulaByFinishedCode(finishedCode);
            if (formula == null || formula.getId() == null || formula.getCoatingArea() == null || formula.getCoatingArea().compareTo(BigDecimal.ZERO) <= 0) {
                missingFormulaPlans.add(buildMissingFormulaRow(planScheduleId, oNo, finishedCode, finishedName, "配胶单缺失或标准涂布面积未配置"));
                continue;
            }
            List<TapeFormulaItem> items = tapeFormulaMapper.selectItemsByFormulaId(formula.getId());
            if (items == null || items.isEmpty()) {
                missingFormulaPlans.add(buildMissingFormulaRow(planScheduleId, oNo, finishedCode, finishedName, "配胶单明细为空"));
                continue;
            }

            ChemicalPurchaseRequest req = ensureDraftRequest(targetDate, planScheduleId, oNo, finishedCode);
            requestNos.add(req.getRequestNo());

            // 若已发生过领料(ALLOCATED)，则不再重复分解/请购，避免重复单据
            if (hasAllocatedAutoLocks(planScheduleId, oNo, finishedCode)) {
                continue;
            }

            // 幂等处理：同一排程重复查询时，先回滚并清理历史自动生成记录，再重新计算
            cleanupAutoGeneratedForSchedule(req.getId(), planScheduleId, oNo, finishedCode);

            BigDecimal factor = coatingArea.divide(formula.getCoatingArea(), 8, RoundingMode.HALF_UP);
            BigDecimal totalWeight = formula.getTotalWeight() == null ? BigDecimal.ZERO : formula.getTotalWeight();

            for (TapeFormulaItem item : items) {
                String rawCode = item == null ? null : trim(item.getMaterialCode());
                if (rawCode == null) {
                    continue;
                }
                TapeRawMaterial rawMaster = rawMaterialCache.computeIfAbsent(rawCode, tapeFormulaMapper::selectRawMaterialByCode);
                String rawName = item == null ? null : str(item.getMaterialName());
                if (trim(rawName) == null && rawMaster != null) {
                    rawName = rawMaster.getMaterialName();
                }

                BigDecimal needKg = calcNeedKg(item, factor, totalWeight);
                if (needKg.compareTo(BigDecimal.ZERO) <= 0) {
                    continue;
                }

                ChemicalStock stock = pickChemicalStock(rawCode);
                int requiredQty = convertKgToQty(needKg, stock);
                int lockedQty = 0;
                Long stockId = null;
                String status = "PENDING";

                if (stock != null && requiredQty > 0) {
                    int availableQty = resolveChemicalAvailableQty(stock, null);
                    int canLock = Math.min(requiredQty, Math.max(availableQty, 0));
                    if (canLock > 0) {
                        int ok = chemicalStockMapper.lockStock(stock.getId(), canLock);
                        if (ok > 0) {
                            lockedQty = canLock;
                            stockId = stock.getId();
                            status = lockedQty >= requiredQty ? "LOCKED" : "PARTIAL";
                            lockCount++;
                        }
                    }
                }

                ChemicalMaterialLock lock = new ChemicalMaterialLock();
                lock.setPlanDate(Date.from(targetDate.atStartOfDay(ZoneId.systemDefault()).toInstant()));
                lock.setScheduleId(planScheduleId);
                lock.setOrderNo(oNo);
                lock.setFinishedMaterialCode(finishedCode);
                lock.setRawMaterialCode(rawCode);
                lock.setRawMaterialName(rawName);
                lock.setChemicalStockId(stockId);
                lock.setRequiredKg(needKg);
                lock.setRequiredQty(requiredQty);
                lock.setLockedQty(lockedQty);
                lock.setLockStatus(status);
                String sectionKey = resolveFormulaItemSectionKey(item, rawCode, rawName);
                lock.setSourceRef("formulaId=" + formula.getId() + ";section=" + sectionKey + ";finished=" + finishedName);
                lock.setRemark("auto-generate-on-lock-query");
                lock.setCreateTime(new Date());
                lock.setUpdateTime(new Date());
                chemicalMaterialLockMapper.insert(lock);

                if (requiredQty > lockedQty) {
                    int shortage = requiredQty - lockedQty;
                    ChemicalPurchaseRequestItem reqItem = new ChemicalPurchaseRequestItem();
                    reqItem.setRequestId(req.getId());
                    reqItem.setScheduleId(planScheduleId);
                    reqItem.setOrderNo(oNo);
                    reqItem.setFinishedMaterialCode(finishedCode);
                    reqItem.setRawMaterialCode(rawCode);
                    reqItem.setRawMaterialName(rawName);
                    reqItem.setRequiredKg(needKg);
                    reqItem.setSuggestedQty(shortage);
                    reqItem.setRequestedQty(shortage);
                    String reqSectionKey = resolveFormulaItemSectionKey(item, rawCode, rawName);
                    reqItem.setUnit(resolveDisplayUnit(reqSectionKey, rawMaster, stock));
                    reqItem.setRemark("库存不足自动请购");
                    reqItem.setCreateTime(new Date());
                    reqItem.setUpdateTime(new Date());
                    chemicalPurchaseRequestItemMapper.insert(reqItem);
                    reqCount++;
                }
            }
        }

        return resultMap(lockCount, reqCount, requestNos, missingFormulaPlans);
    }

    @Override
    public List<Map<String, Object>> queryLocksByPlan(LocalDate planDate, Long scheduleId, String orderNo, String materialCode) {
        LocalDate targetDate = planDate == null ? LocalDate.now() : planDate;
        String normalizedOrderNo = trim(orderNo);
        String normalizedMaterialCode = trim(materialCode);
        String effectiveMaterialCode = (scheduleId != null && scheduleId > 0) ? null : normalizedMaterialCode;

        QueryWrapper<ChemicalMaterialLock> qw = new QueryWrapper<>();
        qw.eq("plan_date", Date.from(targetDate.atStartOfDay(ZoneId.systemDefault()).toInstant()))
                .eq(scheduleId != null, "schedule_id", scheduleId)
            .like(normalizedOrderNo != null, "order_no", normalizedOrderNo)
            .eq(effectiveMaterialCode != null, "finished_material_code", effectiveMaterialCode)
                .orderByAsc("raw_material_code", "order_no", "id");

        List<ChemicalMaterialLock> locks = chemicalMaterialLockMapper.selectList(qw);
        if ((locks == null || locks.isEmpty())
                && (scheduleId != null || normalizedOrderNo != null || normalizedMaterialCode != null)) {
            QueryWrapper<ChemicalMaterialLock> fallbackQw = new QueryWrapper<>();
            fallbackQw.eq(scheduleId != null, "schedule_id", scheduleId)
                    .like(normalizedOrderNo != null, "order_no", normalizedOrderNo)
                    .eq(effectiveMaterialCode != null, "finished_material_code", effectiveMaterialCode)
                    .orderByDesc("id");
            locks = chemicalMaterialLockMapper.selectList(fallbackQw);
        }
        if (locks == null || locks.isEmpty()) {
            return Collections.emptyList();
        }

        Map<Long, ChemicalStock> stockMap = new HashMap<>();
        Map<Long, Integer> chemicalAvailableQtyMap = new HashMap<>();
        Map<String, FilmStock> filmStockByCodeMap = new HashMap<>();
        Map<Long, BigDecimal> filmAvailableAreaMap = new HashMap<>();
        Map<Long, BigDecimal> filmAvailableLengthMap = new HashMap<>();
        Map<String, TapeRawMaterial> rawMap = new HashMap<>();
        List<Map<String, Object>> result = new ArrayList<>();
        for (ChemicalMaterialLock lock : locks) {
            if (lock == null) {
                continue;
            }

            Integer requiredQty = lock.getRequiredQty() == null ? 0 : lock.getRequiredQty();
            Integer lockedQty = lock.getLockedQty() == null ? 0 : lock.getLockedQty();
            int shortageQty = Math.max(requiredQty - lockedQty, 0);

            ChemicalStock stock = null;
            if (lock.getChemicalStockId() != null) {
                stock = stockMap.computeIfAbsent(lock.getChemicalStockId(), chemicalStockMapper::selectById);
            }
            String rawCode = lock.getRawMaterialCode();
            TapeRawMaterial rawMaster = null;
            if (trim(rawCode) != null) {
                rawMaster = rawMap.computeIfAbsent(trim(rawCode), tapeFormulaMapper::selectRawMaterialByCode);
            }
            String sectionKey = parseTokenFromSourceRef(lock.getSourceRef(), "section");
            boolean areaSection = isAreaSection(sectionKey, rawMaster);

            // 兼容历史锁记录：当未写入chemicalStockId时，按原料编码动态补查库存用于前端展示
            if (!areaSection && stock == null && trim(rawCode) != null) {
                stock = pickChemicalStock(rawCode);
                if (stock != null && stock.getId() != null) {
                    stockMap.put(stock.getId(), stock);
                }
            }

            FilmStock filmStock = null;
            BigDecimal filmAvailableArea = BigDecimal.ZERO;
            BigDecimal filmAvailableLengthM = BigDecimal.ZERO;
            Integer filmAvailableRolls = 0;
            if (areaSection && trim(rawCode) != null) {
                String codeKey = normalizeStockCode(rawCode);
                if (codeKey != null) {
                    filmStock = filmStockByCodeMap.computeIfAbsent(codeKey, k -> pickFilmStock(rawCode));
                    filmAvailableArea = resolveFilmAvailableArea(filmStock, filmAvailableAreaMap);
                    filmAvailableLengthM = resolveFilmAvailableLengthM(filmStock, filmAvailableLengthMap);
                    filmAvailableRolls = resolveFilmAvailableRolls(filmStock);
                }
            }

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", lock.getId());
            row.put("planDate", lock.getPlanDate());
            row.put("scheduleId", lock.getScheduleId());
            row.put("orderNo", lock.getOrderNo());
            row.put("finishedMaterialCode", lock.getFinishedMaterialCode());
            row.put("rawMaterialCode", lock.getRawMaterialCode());
            row.put("rawMaterialName", lock.getRawMaterialName());
            row.put("chemicalStockId", lock.getChemicalStockId());
            row.put("filmStockId", areaSection && filmStock != null ? filmStock.getId() : null);
            row.put("requiredKg", lock.getRequiredKg());
            row.put("requiredQty", requiredQty);
            row.put("lockedQty", lockedQty);
            row.put("shortageQty", shortageQty);
            row.put("lockStatus", lock.getLockStatus());
            String sectionLabel = sectionLabel(sectionKey);
            row.put("sectionKey", sectionKey);
            row.put("sectionLabel", sectionLabel);
            row.put("plannedUsage", lock.getRequiredKg());
            row.put("plannedUsageUnit", areaSection ? "㎡" : "kg");
            row.put("unit", areaSection ? "卷" : resolveDisplayUnit(sectionKey, rawMaster, stock));
            row.put("specDesc", areaSection ? buildFilmSpecDesc(filmStock, rawMaster) : buildChemicalSpecDesc(stock, rawMaster));
            row.put("stockAvailableQty", areaSection
                    ? filmAvailableArea
                    : resolveChemicalAvailableQty(stock, chemicalAvailableQtyMap));
            row.put("stockAvailableLengthM", areaSection ? filmAvailableLengthM : BigDecimal.ZERO);
            row.put("stockAvailableRolls", areaSection ? filmAvailableRolls : 0);
            row.put("updateTime", lock.getUpdateTime());
            result.add(row);
        }
        return result;
    }

    private Integer resolveFilmAvailableRolls(FilmStock stock) {
        if (stock == null) {
            return 0;
        }
        Integer availableRolls = stock.getAvailableRolls();
        if (availableRolls != null && availableRolls > 0) {
            return availableRolls;
        }
        Integer availablePackCount = stock.getAvailablePackCount();
        if (availablePackCount != null && availablePackCount > 0) {
            return availablePackCount;
        }
        return 0;
    }

    private FilmStock pickFilmStock(String rawCode) {
        String code = trim(rawCode);
        if (code == null) {
            return null;
        }
        QueryWrapper<FilmStock> exactQw = new QueryWrapper<>();
        exactQw.eq("material_code", code)
                .orderByDesc("available_area")
                .last("LIMIT 1");
        FilmStock exact = filmStockMapper.selectOne(exactQw);
        if (exact != null) {
            return exact;
        }

        QueryWrapper<FilmStock> fallbackQw = new QueryWrapper<>();
        fallbackQw.like("material_code", code).orderByDesc("available_area");
        List<FilmStock> list = filmStockMapper.selectList(fallbackQw);
        if (list == null || list.isEmpty()) {
            return null;
        }
        String target = normalizeStockCode(code);
        FilmStock best = null;
        BigDecimal bestArea = BigDecimal.valueOf(-1);
        for (FilmStock one : list) {
            if (one == null) {
                continue;
            }
            String oneCode = normalizeStockCode(one.getMaterialCode());
            if (target != null && oneCode != null && !target.equals(oneCode)) {
                continue;
            }
            BigDecimal area = one.getAvailableArea() == null ? BigDecimal.ZERO : one.getAvailableArea();
            if (best == null || area.compareTo(bestArea) > 0) {
                best = one;
                bestArea = area;
            }
        }
        return best != null ? best : list.get(0);
    }

    private BigDecimal resolveFilmAvailableArea(FilmStock stock, Map<Long, BigDecimal> cache) {
        if (stock == null || stock.getId() == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal area = stock.getAvailableArea() == null ? BigDecimal.ZERO : stock.getAvailableArea();
        if (area.compareTo(BigDecimal.ZERO) > 0) {
            return area.setScale(3, RoundingMode.HALF_UP);
        }
        BigDecimal cached = cache.get(stock.getId());
        if (cached != null) {
            return cached;
        }

        QueryWrapper<FilmStockDetail> qw = new QueryWrapper<>();
        qw.eq("stock_id", stock.getId())
                .eq("is_deleted", 0);
        List<FilmStockDetail> details = filmStockDetailMapper.selectList(qw);
        BigDecimal sum = BigDecimal.ZERO;
        if (details != null) {
            for (FilmStockDetail d : details) {
                if (d == null) {
                    continue;
                }
                String st = str(d.getStatus());
                if (st != null && "locked".equalsIgnoreCase(st.trim())) {
                    continue;
                }
                BigDecimal oneArea = d.getArea() == null ? BigDecimal.ZERO : d.getArea();
                if (oneArea.compareTo(BigDecimal.ZERO) <= 0 && d.getCurrentLengthM() != null && d.getCurrentLengthM().compareTo(BigDecimal.ZERO) > 0
                        && d.getWidth() != null && d.getWidth() > 0) {
                    oneArea = new BigDecimal(d.getWidth())
                            .divide(new BigDecimal("1000"), 8, RoundingMode.HALF_UP)
                            .multiply(d.getCurrentLengthM())
                            .setScale(3, RoundingMode.HALF_UP);
                }
                if (oneArea.compareTo(BigDecimal.ZERO) > 0) {
                    sum = sum.add(oneArea);
                }
            }
        }
        BigDecimal normalized = sum.setScale(3, RoundingMode.HALF_UP);
        cache.put(stock.getId(), normalized);
        return normalized;
    }

    private BigDecimal resolveFilmAvailableLengthM(FilmStock stock, Map<Long, BigDecimal> cache) {
        if (stock == null || stock.getId() == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal cached = cache.get(stock.getId());
        if (cached != null) {
            return cached;
        }

        QueryWrapper<FilmStockDetail> qw = new QueryWrapper<>();
        qw.eq("stock_id", stock.getId())
                .eq("is_deleted", 0);
        List<FilmStockDetail> details = filmStockDetailMapper.selectList(qw);
        BigDecimal sum = BigDecimal.ZERO;
        if (details != null) {
            for (FilmStockDetail d : details) {
                if (d == null) {
                    continue;
                }
                String st = str(d.getStatus());
                if (st != null && "locked".equalsIgnoreCase(st.trim())) {
                    continue;
                }
                BigDecimal oneLen = d.getCurrentLengthM();
                if ((oneLen == null || oneLen.compareTo(BigDecimal.ZERO) <= 0) && d.getLength() != null && d.getLength() > 0) {
                    oneLen = BigDecimal.valueOf(d.getLength());
                }
                if (oneLen != null && oneLen.compareTo(BigDecimal.ZERO) > 0) {
                    sum = sum.add(oneLen);
                }
            }
        }
        BigDecimal normalized = sum.setScale(3, RoundingMode.HALF_UP);
        cache.put(stock.getId(), normalized);
        return normalized;
    }

    private String buildFilmSpecDesc(FilmStock stock, TapeRawMaterial rawMaster) {
        if (stock != null) {
            String spec = trim(stock.getSpecDesc());
            if (spec != null) {
                return spec;
            }
            BigDecimal thickness = stock.getThickness();
            if (thickness == null || thickness.compareTo(BigDecimal.ZERO) <= 0) {
                Integer parsed = parseThicknessFromCode(stock.getMaterialCode());
                if (parsed != null && parsed > 0) {
                    thickness = BigDecimal.valueOf(parsed);
                }
            }

            Integer width = stock.getWidth();
            if (width == null || width <= 0) {
                QueryWrapper<FilmStockDetail> qw = new QueryWrapper<>();
                qw.eq("stock_id", stock.getId())
                        .eq("is_deleted", 0)
                        .orderByDesc("id")
                        .last("LIMIT 20");
                List<FilmStockDetail> details = filmStockDetailMapper.selectList(qw);
                if (details != null) {
                    for (FilmStockDetail d : details) {
                        if (d != null && d.getWidth() != null && d.getWidth() > 0) {
                            width = d.getWidth();
                            break;
                        }
                    }
                }
            }

            if (thickness != null && thickness.compareTo(BigDecimal.ZERO) > 0 && width != null && width > 0) {
                return thickness.stripTrailingZeros().toPlainString() + "μm×" + width + "mm";
            }
        }
        return rawMaster != null ? str(rawMaster.getSpec()) : "";
    }

    private String buildChemicalSpecDesc(ChemicalStock stock, TapeRawMaterial rawMaster) {
        if (stock != null) {
            BigDecimal unitWeight = stock.getUnitWeight();
            String unit = trim(stock.getUnit());
            boolean packagingUnit = unit != null
                    && !"kg".equalsIgnoreCase(unit)
                    && !"公斤".equals(unit)
                    && !"千克".equals(unit);
            if (unitWeight != null && unitWeight.compareTo(BigDecimal.ZERO) > 0 && packagingUnit) {
                return unitWeight.stripTrailingZeros().toPlainString() + "kg/" + unit;
            }
        }
        String spec = rawMaster != null ? str(rawMaster.getSpec()) : "";
        return firstSpecToken(spec);
    }

    private String firstSpecToken(String spec) {
        String s = trim(spec);
        if (s == null) {
            return "";
        }
        String[] parts = s.split("[,，、\\|]");
        for (String p : parts) {
            String one = trim(p);
            if (one != null) {
                return one;
            }
        }
        return s;
    }

    private Integer parseThicknessFromCode(String materialCode) {
        String code = str(materialCode);
        if (code == null) {
            return null;
        }
        Matcher m = Pattern.compile("(?:-|_)T(\\d{2,4})(?:-|_|$)", Pattern.CASE_INSENSITIVE).matcher(code);
        if (!m.find()) {
            return null;
        }
        try {
            return Integer.parseInt(m.group(1));
        } catch (Exception ignore) {
            return null;
        }
    }

    private String normalizeStockCode(String code) {
        String t = trim(code);
        if (t == null) {
            return null;
        }
        return t.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
    }

    @Override
    public IPage<ChemicalPurchaseRequest> getRequestPage(int current, int size, String status) {
        Page<ChemicalPurchaseRequest> page = new Page<>(current, size);
        IPage<ChemicalPurchaseRequest> result = chemicalPurchaseRequestMapper.selectPageByStatus(page, trim(status));
        if (result != null && result.getRecords() != null) {
            for (ChemicalPurchaseRequest req : result.getRecords()) {
                if (req != null) {
                    req.setStatus(ChemicalRequisitionStatus.normalize(req.getStatus()));
                    req.setStatusText(ChemicalRequisitionStatus.labelOf(req.getStatus()));
                }
            }
        }
        return result;
    }

    @Override
    public ChemicalPurchaseRequest getRequestDetail(String requestNo) {
        ChemicalPurchaseRequest req = chemicalPurchaseRequestMapper.selectByRequestNo(requestNo);
        if (req == null) {
            return null;
        }
        req.setStatus(ChemicalRequisitionStatus.normalize(req.getStatus()));
        req.setStatusText(ChemicalRequisitionStatus.labelOf(req.getStatus()));
        req.setItems(chemicalPurchaseRequestItemMapper.selectByRequestId(req.getId()));
        req.setLogs(chemicalRequisitionLogMapper.selectByRequestNo(requestNo));
        return req;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateRequestedQty(Long itemId, Integer requestedQty) {
        if (itemId == null || requestedQty == null || requestedQty < 0) {
            throw new RuntimeException("参数无效");
        }

        ChemicalPurchaseRequestItem item = chemicalPurchaseRequestItemMapper.selectById(itemId);
        if (item == null || item.getRequestId() == null) {
            throw new RuntimeException("请购明细不存在");
        }
        ChemicalPurchaseRequest req = chemicalPurchaseRequestMapper.selectById(item.getRequestId());
        if (req == null) {
            throw new RuntimeException("请购单不存在");
        }
        String status = ChemicalRequisitionStatus.normalize(req.getStatus());
        if (!(ChemicalRequisitionStatus.DRAFT.equals(status) || ChemicalRequisitionStatus.SUBMITTED.equals(status))) {
            throw new RuntimeException("当前状态不允许修改请购数量");
        }

        int ok = chemicalPurchaseRequestItemMapper.updateRequestedQty(itemId, requestedQty);
        if (ok <= 0) {
            throw new RuntimeException("更新失败");
        }
        appendLog(req.getRequestNo(), req.getId(), "UPDATE_QTY", "warehouse", "更新请购数量 itemId=" + itemId + ", requestedQty=" + requestedQty);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void submitRequest(String requestNo) {
        ChemicalPurchaseRequest req = chemicalPurchaseRequestMapper.selectByRequestNo(requestNo);
        if (req == null) {
            throw new RuntimeException("请购单不存在");
        }
        String currentStatus = ChemicalRequisitionStatus.normalize(req.getStatus());
        if (!ChemicalRequisitionStatus.DRAFT.equals(currentStatus)) {
            throw new RuntimeException("仅草稿状态可提交");
        }
        chemicalPurchaseRequestMapper.updateStatus(requestNo, ChemicalRequisitionStatus.SUBMITTED, "warehouse");
        appendLog(requestNo, req.getId(), "SUBMIT", "warehouse", "提交请购单");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void approveRequest(String requestNo) {
        ChemicalPurchaseRequest req = chemicalPurchaseRequestMapper.selectByRequestNo(requestNo);
        if (req == null) {
            throw new RuntimeException("请购单不存在");
        }
        String currentStatus = ChemicalRequisitionStatus.normalize(req.getStatus());
        if (!ChemicalRequisitionStatus.SUBMITTED.equals(currentStatus)) {
            throw new RuntimeException("当前状态不允许审核");
        }
        chemicalPurchaseRequestMapper.updateStatus(requestNo, ChemicalRequisitionStatus.APPROVED, "warehouse");
        appendLog(requestNo, req.getId(), "APPROVE", "warehouse", "审核通过");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String createPurchaseOrder(String requestNo) {
        ChemicalPurchaseRequest req = chemicalPurchaseRequestMapper.selectByRequestNo(requestNo);
        if (req == null) {
            throw new RuntimeException("请购单不存在");
        }
        if (!ChemicalRequisitionStatus.APPROVED.equals(ChemicalRequisitionStatus.normalize(req.getStatus()))) {
            throw new RuntimeException("仅已审核状态可转采购");
        }
        if (req.getPurchaseOrderNo() != null && !req.getPurchaseOrderNo().trim().isEmpty()) {
            return req.getPurchaseOrderNo();
        }

        List<ChemicalPurchaseRequestItem> items = chemicalPurchaseRequestItemMapper.selectByRequestId(req.getId());
        if (items == null || items.isEmpty()) {
            throw new RuntimeException("请购明细为空");
        }

        BigDecimal totalQty = BigDecimal.ZERO;
        for (ChemicalPurchaseRequestItem item : items) {
            Integer qty = item.getRequestedQty() == null ? 0 : item.getRequestedQty();
            if (qty > 0) {
                totalQty = totalQty.add(BigDecimal.valueOf(qty));
            }
        }

        PurchaseOrder po = new PurchaseOrder();
        po.setOrderNo(generatePurchaseOrderNo());
        po.setSupplier("AUTO-CHEMICAL");
        po.setOrderDate(LocalDate.now());
        po.setDeliveryDate(LocalDate.now().plusDays(7));
        po.setStatus("pending");
        po.setRemark("source=chemical_purchase_request;requestNo=" + requestNo);
        po.setCreatedBy("system");
        po.setUpdatedBy("system");
        po.setCreatedAt(new Date());
        po.setUpdatedAt(new Date());
        po.setIsDeleted(0);
        po.setTotalAmount(BigDecimal.ZERO);
        po.setTotalArea(totalQty);
        po.setRequiredArea(totalQty);
        purchaseOrderMapper.insert(po);

        for (ChemicalPurchaseRequestItem reqItem : items) {
            int qty = reqItem.getRequestedQty() == null ? 0 : reqItem.getRequestedQty();
            if (qty <= 0) {
                continue;
            }
            PurchaseOrderItem poi = new PurchaseOrderItem();
            poi.setOrderId(po.getId());
            poi.setMaterialCode(reqItem.getRawMaterialCode());
            poi.setMaterialName(reqItem.getRawMaterialName());
            poi.setRolls(qty);
            poi.setSqm(BigDecimal.valueOf(qty));
            poi.setUnitPrice(BigDecimal.ZERO);
            poi.setAmount(BigDecimal.ZERO);
            poi.setRemark("linkedRequest=" + requestNo + ";requestItemId=" + reqItem.getId());
            poi.setCreatedBy("system");
            poi.setUpdatedBy("system");
            poi.setCreatedAt(new Date());
            poi.setUpdatedAt(new Date());
            poi.setIsDeleted(0);
            purchaseOrderItemMapper.insert(poi);
            chemicalPurchaseRequestItemMapper.updatePurchaseOrderItemId(reqItem.getId(), poi.getId());
        }

        chemicalPurchaseRequestMapper.updatePurchaseOrderNo(requestNo, po.getOrderNo(), "warehouse");
        chemicalPurchaseRequestMapper.updateStatus(requestNo, ChemicalRequisitionStatus.PO_CREATED, "warehouse");
        appendLog(requestNo, req.getId(), "CREATE_PO", "warehouse", "生成采购单: " + po.getOrderNo());
        return po.getOrderNo();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> receiveAndFulfill(String requestNo, Map<Long, Integer> receiveQtyMap) {
        ChemicalPurchaseRequest req = chemicalPurchaseRequestMapper.selectByRequestNo(requestNo);
        if (req == null) {
            throw new RuntimeException("请购单不存在");
        }
        String currentStatus = ChemicalRequisitionStatus.normalize(req.getStatus());
        if (!Arrays.asList(ChemicalRequisitionStatus.PO_CREATED,
                ChemicalRequisitionStatus.PARTIAL_RECEIVED,
                ChemicalRequisitionStatus.RECEIVED).contains(currentStatus)) {
            throw new RuntimeException("当前状态不允许收货");
        }
        if (ChemicalRequisitionStatus.RECEIVED.equals(currentStatus)) {
            Map<String, Object> done = new LinkedHashMap<>();
            done.put("receivedQty", 0);
            done.put("fulfilledQty", 0);
            done.put("message", "该请购单已入库");
            return done;
        }

        List<ChemicalPurchaseRequestItem> items = chemicalPurchaseRequestItemMapper.selectByRequestId(req.getId());
        if (items == null || items.isEmpty()) {
            throw new RuntimeException("请购明细为空");
        }

        int totalReceived = 0;
        int totalFulfilled = 0;
        Map<Long, Integer> qtyMap = receiveQtyMap == null ? Collections.emptyMap() : receiveQtyMap;
        boolean hasExplicitQty = !qtyMap.isEmpty();

        for (ChemicalPurchaseRequestItem item : items) {
            if (item == null || item.getRawMaterialCode() == null || item.getRawMaterialCode().trim().isEmpty()) {
                continue;
            }
            int defaultReceive = item.getRequestedQty() == null ? 0 : item.getRequestedQty();
            int alreadyReceived = item.getReceivedQty() == null ? 0 : item.getReceivedQty();
            int remainingReceivable = Math.max(defaultReceive - alreadyReceived, 0);
            int receiveQty = qtyMap.containsKey(item.getId()) ? Math.max(qtyMap.get(item.getId()), 0) : remainingReceivable;
            if (receiveQty > remainingReceivable) {
                throw new RuntimeException("物料 " + item.getRawMaterialCode() + " 本次实收数量不能大于剩余待收数量");
            }
            if (receiveQty <= 0) {
                continue;
            }

            ChemicalStock stock = ensureChemicalStockByCode(item.getRawMaterialCode().trim(), item.getRawMaterialName(), item.getUnit());
            int inOk = chemicalStockMapper.addStock(stock.getId(), receiveQty);
            if (inOk <= 0) {
                throw new RuntimeException("化工入库失败，物料：" + item.getRawMaterialCode());
            }
            totalReceived += receiveQty;
            chemicalPurchaseRequestItemMapper.updateReceivedQty(item.getId(), alreadyReceived + receiveQty);

            int fulfilled = fulfillLocksForItem(stock.getId(), item, receiveQty);
            totalFulfilled += fulfilled;
            appendLog(requestNo, req.getId(), "RECEIVE_ITEM", "warehouse",
                    "明细到货 itemId=" + item.getId() + ", raw=" + item.getRawMaterialCode() + ", receiveQty=" + receiveQty + ", fulfilled=" + fulfilled);
        }

        if (hasExplicitQty && totalReceived <= 0) {
            throw new RuntimeException("请填写本次实收数量");
        }

        boolean allReceived = true;
        List<ChemicalPurchaseRequestItem> latestItems = chemicalPurchaseRequestItemMapper.selectByRequestId(req.getId());
        for (ChemicalPurchaseRequestItem latest : latestItems) {
            int reqQty = latest.getRequestedQty() == null ? 0 : latest.getRequestedQty();
            int recQty = latest.getReceivedQty() == null ? 0 : latest.getReceivedQty();
            if (recQty < reqQty) {
                allReceived = false;
                break;
            }
        }
        chemicalPurchaseRequestMapper.updateStatus(requestNo,
            allReceived ? ChemicalRequisitionStatus.RECEIVED : ChemicalRequisitionStatus.PARTIAL_RECEIVED,
            "warehouse");
        appendLog(requestNo, req.getId(), "RECEIVE", "warehouse", "到货入库完成, receivedQty=" + totalReceived + ", fulfilledQty=" + totalFulfilled);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("receivedQty", totalReceived);
        result.put("fulfilledQty", totalFulfilled);
        result.put("message", allReceived ? "全部到货入库并回填完成" : "部分到货入库并回填完成");
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> confirmIssueByLocks(List<Long> lockIds, String operator) {
        if (lockIds == null || lockIds.isEmpty()) {
            throw new RuntimeException("锁定记录不能为空");
        }
        Map<Long, Integer> lockQtyMap = new LinkedHashMap<>();
        for (Long id : lockIds) {
            if (id != null && id > 0) {
                lockQtyMap.put(id, Integer.MAX_VALUE);
            }
        }
        return confirmIssueByLocks(lockQtyMap, operator);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> confirmIssueByLocks(Map<Long, Integer> lockQtyMap, String operator) {
        if (lockQtyMap == null || lockQtyMap.isEmpty()) {
            throw new RuntimeException("锁定记录不能为空");
        }

        int issuedCount = 0;
        int skippedCount = 0;
        int totalQty = 0;
        BigDecimal totalWeight = BigDecimal.ZERO;

        String user = trim(operator) == null ? "production" : trim(operator);
        for (Map.Entry<Long, Integer> entry : lockQtyMap.entrySet()) {
            Long lockId = entry.getKey();
            if (lockId == null) {
                continue;
            }
            ChemicalMaterialLock lock = chemicalMaterialLockMapper.selectById(lockId);
            if (lock == null) {
                skippedCount++;
                continue;
            }

            String status = str(lock.getLockStatus());
            if ("ALLOCATED".equalsIgnoreCase(status)) {
                skippedCount++;
                continue;
            }
            if (!("LOCKED".equalsIgnoreCase(status) || "PARTIAL".equalsIgnoreCase(status))) {
                skippedCount++;
                continue;
            }

            int maxQty = lock.getLockedQty() == null ? 0 : lock.getLockedQty();
            int requestedQty = entry.getValue() == null ? 0 : entry.getValue();
            int outQty = Math.min(maxQty, requestedQty <= 0 ? maxQty : requestedQty);
            if (outQty <= 0 || lock.getChemicalStockId() == null) {
                skippedCount++;
                continue;
            }

            BigDecimal outWeight = BigDecimal.ZERO;
            BigDecimal requiredKg = lock.getRequiredKg() == null ? BigDecimal.ZERO : lock.getRequiredKg();
            int requiredQty = lock.getRequiredQty() == null ? 0 : lock.getRequiredQty();
            if (requiredQty > 0 && requiredKg.compareTo(BigDecimal.ZERO) > 0) {
                outWeight = requiredKg.multiply(BigDecimal.valueOf(outQty))
                        .divide(BigDecimal.valueOf(requiredQty), 3, RoundingMode.HALF_UP);
            }

            ChemicalStockOut out = new ChemicalStockOut();
            out.setChemicalStockId(lock.getChemicalStockId());
            out.setOutboundNo("CHOUT" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyMMdd")) + "-" + lock.getId());
            out.setOutQuantity(outQty);
            out.setOutWeight(outWeight);
            out.setScheduleId(lock.getScheduleId());
            out.setPurpose("COATING_MATERIAL_ISSUE");
            out.setOutboundBy(user);
            out.setOutboundTime(new Date());
            out.setRemark("source=coating-task-issue;orderNo=" + (lock.getOrderNo() == null ? "" : lock.getOrderNo()));

            // 化工出库（会扣减 locked_quantity，并写出库记录）
            chemicalStockService.outbound(out, null);

            int remainLockedQty = Math.max(maxQty - outQty, 0);
            lock.setLockedQty(remainLockedQty);
            lock.setLockStatus(remainLockedQty <= 0 ? "ALLOCATED" : "PARTIAL");
            lock.setUpdateTime(new Date());
            chemicalMaterialLockMapper.updateById(lock);

            issuedCount++;
            totalQty += outQty;
            totalWeight = totalWeight.add(outWeight);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("issuedCount", issuedCount);
        result.put("skippedCount", skippedCount);
        result.put("totalOutQty", totalQty);
        result.put("totalOutWeight", totalWeight.setScale(3, RoundingMode.HALF_UP));
        return result;
    }

    private String resolveFormulaItemSectionKey(TapeFormulaItem item, String rawCode, String rawName) {
        String remark = item == null ? null : item.getRemark();
        if (remark != null) {
            Matcher matcher = Pattern.compile("【分组:(GLUE|ISOLATOR|FILM|RELEASE|BASE)】", Pattern.CASE_INSENSITIVE).matcher(remark);
            if (matcher.find()) {
                return String.valueOf(matcher.group(1)).toUpperCase(Locale.ROOT);
            }
        }
        String text = (str(rawCode) + " " + str(rawName)).toUpperCase(Locale.ROOT);
        if (text.contains("离型") || text.contains("RELEASE")) {
            return "RELEASE";
        }
        if (text.contains("隔离") || text.contains("ISOLATOR")) {
            return "ISOLATOR";
        }
        if (text.contains("PET") || text.contains("BOPP") || text.contains("CPP") || text.contains("PI")
                || text.contains("PVC") || text.contains("OPP") || text.contains("TPU") || text.contains("基材") || text.contains("薄膜")) {
            return "BASE";
        }
        return "GLUE";
    }

    private String parseTokenFromSourceRef(String sourceRef, String key) {
        String s = str(sourceRef);
        String k = str(key);
        if (s == null || k == null) {
            return "";
        }
        String[] parts = s.split(";");
        for (String p : parts) {
            String[] kv = p.split("=", 2);
            if (kv.length == 2 && k.equalsIgnoreCase(String.valueOf(kv[0]).trim())) {
                return String.valueOf(kv[1]).trim();
            }
        }
        return "";
    }

    private String sectionLabel(String sectionKey) {
        String key = str(sectionKey) == null ? "" : str(sectionKey).toUpperCase(Locale.ROOT);
        switch (key) {
            case "ISOLATOR":
                return "隔离剂";
            case "RELEASE":
                return "离型剂";
            case "BASE":
            case "FILM":
                return "基材";
            case "GLUE":
            default:
                return "胶水";
        }
    }

    private boolean isAreaSection(String sectionKey, TapeRawMaterial rawMaster) {
        String key = str(sectionKey) == null ? "" : str(sectionKey).toUpperCase(Locale.ROOT);
        if ("BASE".equals(key) || "FILM".equals(key) || "RELEASE".equals(key)) {
            return true;
        }
        if (rawMaster != null) {
            String cat = str(rawMaster.getMaterialCategory());
            return cat != null && "film".equalsIgnoreCase(cat);
        }
        return false;
    }

    private String resolveDisplayUnit(String sectionKey, TapeRawMaterial rawMaster, ChemicalStock stock) {
        String rawUnit = rawMaster == null ? null : trim(rawMaster.getUnit());
        String stockUnit = stock == null ? null : trim(stock.getUnit());
        boolean stockPackagingUnit = stockUnit != null
                && !"kg".equalsIgnoreCase(stockUnit)
                && !"公斤".equals(stockUnit)
                && !"千克".equals(stockUnit);
        if (stockPackagingUnit) {
            return stockUnit;
        }

        boolean rawPackagingUnit = rawUnit != null
                && !"kg".equalsIgnoreCase(rawUnit)
                && !"公斤".equals(rawUnit)
                && !"千克".equals(rawUnit);
        if (rawPackagingUnit) {
            return rawUnit;
        }

        if (!isAreaSection(sectionKey, rawMaster) && stock != null
                && stock.getUnitWeight() != null
                && stock.getUnitWeight().compareTo(BigDecimal.ZERO) > 0) {
            return "桶";
        }

        if (stockUnit != null) {
            return stockUnit;
        }
        if (rawUnit != null) {
            return rawUnit;
        }
        return isAreaSection(sectionKey, rawMaster) ? "㎡" : "kg";
    }

    private void appendLog(String requestNo, Long requestId, String actionType, String operator, String content) {
        ChemicalRequisitionLog log = new ChemicalRequisitionLog();
        log.setRequestNo(requestNo);
        log.setRequestId(requestId);
        log.setActionType(actionType);
        log.setOperator(operator == null ? "system" : operator);
        log.setContent(content);
        log.setCreateTime(new Date());
        chemicalRequisitionLogMapper.insert(log);
    }

    private ChemicalStock ensureChemicalStockByCode(String materialCode, String materialName, String unit) {
        QueryWrapper<ChemicalStock> qw = new QueryWrapper<>();
        qw.eq("material_code", materialCode).last("LIMIT 1");
        ChemicalStock stock = chemicalStockMapper.selectOne(qw);
        if (stock != null) {
            return stock;
        }

        ChemicalStock created = new ChemicalStock();
        created.setMaterialCode(materialCode);
        created.setMaterialName(materialName == null || materialName.trim().isEmpty() ? materialCode : materialName.trim());
        created.setChemicalType("other");
        created.setUnit(unit == null || unit.trim().isEmpty() ? "桶" : unit.trim());
        created.setUnitWeight(BigDecimal.ONE);
        created.setTotalQuantity(0);
        created.setAvailableQuantity(0);
        created.setLockedQuantity(0);
        created.setSafetyStock(0);
        created.setStatus("active");
        created.setRemark("auto-create-by-chemical-receive");
        created.setCreateTime(new Date());
        created.setUpdateTime(new Date());
        created.setCreateBy("system");
        created.setUpdateBy("system");
        chemicalStockMapper.insert(created);
        return created;
    }

    private int fulfillLocksForItem(Long stockId, ChemicalPurchaseRequestItem item, int receiveQty) {
        int remain = receiveQty;
        int fulfilled = 0;
        if (remain <= 0) {
            return 0;
        }

        QueryWrapper<ChemicalMaterialLock> qw = new QueryWrapper<>();
        qw.eq("raw_material_code", item.getRawMaterialCode())
                .eq(item.getScheduleId() != null, "schedule_id", item.getScheduleId())
                .eq(item.getOrderNo() != null && !item.getOrderNo().trim().isEmpty(), "order_no", item.getOrderNo())
                .in("lock_status", Arrays.asList("PENDING", "PARTIAL"))
                .orderByAsc("id");

        List<ChemicalMaterialLock> locks = chemicalMaterialLockMapper.selectList(qw);
        for (ChemicalMaterialLock lock : locks) {
            if (remain <= 0) {
                break;
            }
            int required = lock.getRequiredQty() == null ? 0 : lock.getRequiredQty();
            int already = lock.getLockedQty() == null ? 0 : lock.getLockedQty();
            int need = required - already;
            if (need <= 0) {
                lock.setLockStatus("LOCKED");
                chemicalMaterialLockMapper.updateById(lock);
                continue;
            }
            int add = Math.min(remain, need);
            if (add <= 0) {
                continue;
            }
            int lockOk = chemicalStockMapper.lockStock(stockId, add);
            if (lockOk <= 0) {
                break;
            }

            lock.setChemicalStockId(stockId);
            lock.setLockedQty(already + add);
            lock.setLockStatus(lock.getLockedQty() >= required ? "LOCKED" : "PARTIAL");
            lock.setUpdateTime(new Date());
            chemicalMaterialLockMapper.updateById(lock);
            remain -= add;
            fulfilled += add;
        }
        return fulfilled;
    }

    private String generatePurchaseOrderNo() {
        String dateCode = LocalDate.now().format(DateTimeFormatter.ofPattern("yyMMdd"));
        String prefix = "CD" + dateCode;
        String last = purchaseOrderMapper.selectLastOrderNoByPrefix(prefix);
        int nextSeq = 1;
        if (last != null && last.length() > prefix.length()) {
            try {
                nextSeq = Integer.parseInt(last.substring(prefix.length())) + 1;
            } catch (Exception ignore) {
                nextSeq = 1;
            }
        }
        return prefix + String.format("%02d", nextSeq);
    }

    private ChemicalPurchaseRequest ensureDraftRequest(LocalDate planDate, Long scheduleId, String orderNo, String finishedCode) {
        QueryWrapper<ChemicalPurchaseRequest> findQw = new QueryWrapper<>();
        findQw.eq("is_deleted", 0)
                .eq(scheduleId != null, "schedule_id", scheduleId)
                .eq(orderNo != null && !orderNo.trim().isEmpty(), "order_no", orderNo)
                .eq(finishedCode != null && !finishedCode.trim().isEmpty(), "finished_material_code", finishedCode)
                .eq("source", "coating-lock-query")
                .orderByDesc("id")
                .last("LIMIT 1");
        ChemicalPurchaseRequest byBizKey = chemicalPurchaseRequestMapper.selectOne(findQw);
        if (byBizKey != null) {
            return byBizKey;
        }

        String schedulePart = scheduleId == null ? "TB0" : ("TB" + scheduleId);
        String orderPart = orderNo == null ? "NO" : orderNo.replaceAll("[^A-Za-z0-9]", "");
        String requestNo = "CPR" + planDate.format(DateTimeFormatter.ofPattern("yyMMdd")) + "-" + schedulePart + "-" + orderPart;
        ChemicalPurchaseRequest exists = chemicalPurchaseRequestMapper.selectByRequestNo(requestNo);
        if (exists != null) {
            return exists;
        }
        ChemicalPurchaseRequest req = new ChemicalPurchaseRequest();
        req.setRequestNo(requestNo);
        req.setPlanDate(Date.from(planDate.atStartOfDay(ZoneId.systemDefault()).toInstant()));
        req.setScheduleId(scheduleId);
        req.setOrderNo(orderNo);
        req.setFinishedMaterialCode(finishedCode);
        req.setStatus(ChemicalRequisitionStatus.DRAFT);
        req.setSource("coating-lock-query");
        req.setRemark("按配胶标准自动生成");
        req.setCreateTime(new Date());
        req.setUpdateTime(new Date());
        req.setCreateBy("system");
        req.setUpdateBy("system");
        req.setIsDeleted(0);
        chemicalPurchaseRequestMapper.insert(req);
        return req;
    }

    private ChemicalStock pickChemicalStock(String rawCode) {
        String code = trim(rawCode);
        if (code == null) {
            return null;
        }

        QueryWrapper<ChemicalStock> qw = new QueryWrapper<>();
        qw.eq("material_code", code).orderByDesc("id");
        List<ChemicalStock> list = chemicalStockMapper.selectList(qw);

        // 兜底：当按编码精确匹配不到时，使用全量候选进行编码/名称的兼容匹配
        if (list == null || list.isEmpty()) {
            QueryWrapper<ChemicalStock> allQw = new QueryWrapper<>();
            allQw.orderByDesc("id");
            List<ChemicalStock> all = chemicalStockMapper.selectList(allQw);
            if (all == null || all.isEmpty()) {
                return null;
            }

            String targetCode = normalizeStockCode(code);
            String targetText = code.toUpperCase(Locale.ROOT).replaceAll("\\s+", "");
            List<ChemicalStock> matched = new ArrayList<>();
            for (ChemicalStock one : all) {
                if (one == null) {
                    continue;
                }
                String oneCodeRaw = trim(one.getMaterialCode());
                String oneNameRaw = trim(one.getMaterialName());
                String oneCodeNorm = normalizeStockCode(oneCodeRaw);
                String oneCodeText = oneCodeRaw == null ? "" : oneCodeRaw.toUpperCase(Locale.ROOT).replaceAll("\\s+", "");
                String oneNameText = oneNameRaw == null ? "" : oneNameRaw.toUpperCase(Locale.ROOT).replaceAll("\\s+", "");

                boolean codeMatch = targetCode != null && oneCodeNorm != null
                        && (targetCode.equals(oneCodeNorm)
                        || targetCode.contains(oneCodeNorm)
                        || oneCodeNorm.contains(targetCode));
                boolean textMatch = !targetText.isEmpty()
                        && (oneCodeText.contains(targetText)
                        || targetText.contains(oneCodeText)
                        || oneNameText.contains(targetText)
                        || targetText.contains(oneNameText));
                if (codeMatch || textMatch) {
                    matched.add(one);
                }
            }
            list = matched;
            if (list.isEmpty()) {
                return null;
            }
        }

        for (ChemicalStock stock : list) {
            int available = resolveChemicalAvailableQty(stock, null);
            if (available > 0) {
                return chemicalStockMapper.selectById(stock.getId());
            }
        }
        return null;
    }

    private int resolveChemicalAvailableQty(ChemicalStock stock, Map<Long, Integer> cache) {
        if (stock == null || stock.getId() == null) {
            return 0;
        }
        Long stockId = stock.getId();
        if (cache != null && cache.containsKey(stockId)) {
            return cache.get(stockId);
        }

        List<ChemicalStockDetail> details = chemicalStockDetailMapper.selectByChemicalStockId(stockId);
        Integer packCount = stock.getAvailablePackCount() == null ? 0 : stock.getAvailablePackCount();
        if (details == null || details.isEmpty()) {
            // 兼容历史数据：部分库存仅维护汇总数量，未维护明细批次
            int fallback = Math.max(
                    Math.max(0, stock.getAvailableQuantity() == null ? 0 : stock.getAvailableQuantity()),
                    Math.max(0, packCount));
            if (cache != null) {
                cache.put(stockId, fallback);
            }
            return fallback;
        }
        int available = 0;
        int locked = 0;
        BigDecimal weightSum = BigDecimal.ZERO;
        int weightCount = 0;
        if (details != null) {
            for (ChemicalStockDetail d : details) {
                if (d == null) {
                    continue;
                }
                String status = str(d.getStatus());
                if (status != null && "used".equalsIgnoreCase(status.trim())) {
                    continue;
                }
                if (status != null && "locked".equalsIgnoreCase(status.trim())) {
                    locked++;
                } else {
                    available++;
                }

                BigDecimal w = d.getWeight();
                if (w != null && w.compareTo(BigDecimal.ZERO) > 0) {
                    weightSum = weightSum.add(w);
                    weightCount++;
                }
            }
        }

        int total = available + locked;
        Integer oldAvailable = stock.getAvailableQuantity() == null ? 0 : stock.getAvailableQuantity();
        Integer oldLocked = stock.getLockedQuantity() == null ? 0 : stock.getLockedQuantity();
        Integer oldTotal = stock.getTotalQuantity() == null ? 0 : stock.getTotalQuantity();
        Integer oldBucket = stock.getBucketCount() == null ? 0 : stock.getBucketCount();

        // 单一口径：主表仅由明细实时重算，不使用任何兜底值
        int effectiveAvailable = available;

        boolean changed = oldAvailable != effectiveAvailable || oldLocked != locked || oldTotal != total || oldBucket != total;
        if (changed) {
            stock.setAvailableQuantity(effectiveAvailable);
            stock.setLockedQuantity(locked);
            stock.setTotalQuantity(total);
            stock.setBucketCount(total);
            if (weightCount > 0) {
                stock.setUnitWeight(weightSum.divide(BigDecimal.valueOf(weightCount), 3, RoundingMode.HALF_UP));
            }
            Integer safety = stock.getSafetyStock() == null ? 0 : stock.getSafetyStock();
            if (effectiveAvailable <= 0) {
                stock.setStatus("out_of_stock");
            } else if (safety > 0 && effectiveAvailable < safety) {
                stock.setStatus("low_stock");
            } else {
                stock.setStatus("active");
            }
            stock.setUpdateTime(new Date());
            chemicalStockMapper.updateById(stock);
        }

        int finalAvailable = effectiveAvailable;
        if (cache != null) {
            cache.put(stockId, finalAvailable);
        }
        return finalAvailable;
    }

    private void cleanupAutoGeneratedForSchedule(Long requestId, Long scheduleId, String orderNo, String finishedCode) {
        if (scheduleId == null) {
            return;
        }

        QueryWrapper<ChemicalMaterialLock> lockQw = new QueryWrapper<>();
        lockQw.eq("schedule_id", scheduleId)
                .eq(orderNo != null && !orderNo.trim().isEmpty(), "order_no", orderNo)
                .eq(finishedCode != null && !finishedCode.trim().isEmpty(), "finished_material_code", finishedCode)
                .eq("remark", "auto-generate-on-lock-query");
        List<ChemicalMaterialLock> oldLocks = chemicalMaterialLockMapper.selectList(lockQw);
        if (oldLocks != null && !oldLocks.isEmpty()) {
            List<Long> deletableIds = new ArrayList<>();
            for (ChemicalMaterialLock old : oldLocks) {
                if (old == null) {
                    continue;
                }

                // 已领料记录保留，避免重复请购/重复锁定
                if ("ALLOCATED".equalsIgnoreCase(str(old.getLockStatus()))) {
                    continue;
                }

                int lockedQty = old.getLockedQty() == null ? 0 : old.getLockedQty();
                if (lockedQty > 0 && old.getChemicalStockId() != null) {
                    chemicalStockMapper.unlockStock(old.getChemicalStockId(), lockedQty);
                }
                deletableIds.add(old.getId());
            }
            if (!deletableIds.isEmpty()) {
                chemicalMaterialLockMapper.deleteBatchIds(deletableIds);
            }
        }

        if (requestId != null) {
            QueryWrapper<ChemicalPurchaseRequestItem> itemQw = new QueryWrapper<>();
            itemQw.eq("request_id", requestId)
                    .eq("schedule_id", scheduleId)
                    .eq(orderNo != null && !orderNo.trim().isEmpty(), "order_no", orderNo)
                    .eq(finishedCode != null && !finishedCode.trim().isEmpty(), "finished_material_code", finishedCode)
                    .eq("remark", "库存不足自动请购");
            chemicalPurchaseRequestItemMapper.delete(itemQw);
        }
    }

    private boolean hasAllocatedAutoLocks(Long scheduleId, String orderNo, String finishedCode) {
        if (scheduleId == null) {
            return false;
        }
        QueryWrapper<ChemicalMaterialLock> qw = new QueryWrapper<>();
        qw.eq("schedule_id", scheduleId)
                .eq(orderNo != null && !orderNo.trim().isEmpty(), "order_no", orderNo)
                .eq(finishedCode != null && !finishedCode.trim().isEmpty(), "finished_material_code", finishedCode)
                .eq("remark", "auto-generate-on-lock-query")
                .eq("lock_status", "ALLOCATED")
                .last("LIMIT 1");
        return chemicalMaterialLockMapper.selectOne(qw) != null;
    }

    private BigDecimal calcNeedKg(TapeFormulaItem item, BigDecimal factor, BigDecimal totalWeight) {
        BigDecimal need = BigDecimal.ZERO;
        if (item.getWeight() != null && item.getWeight().compareTo(BigDecimal.ZERO) > 0) {
            need = item.getWeight().multiply(factor);
        } else if (item.getRatio() != null && item.getRatio().compareTo(BigDecimal.ZERO) > 0 && totalWeight.compareTo(BigDecimal.ZERO) > 0) {
            need = totalWeight.multiply(item.getRatio())
                    .divide(new BigDecimal("100"), 8, RoundingMode.HALF_UP)
                    .multiply(factor);
        }
        return need.setScale(3, RoundingMode.HALF_UP);
    }

    private int convertKgToQty(BigDecimal needKg, ChemicalStock stock) {
        if (needKg == null || needKg.compareTo(BigDecimal.ZERO) <= 0) {
            return 0;
        }
        BigDecimal unitWeight = (stock == null || stock.getUnitWeight() == null || stock.getUnitWeight().compareTo(BigDecimal.ZERO) <= 0)
                ? BigDecimal.ONE : stock.getUnitWeight();
        return needKg.divide(unitWeight, 0, RoundingMode.CEILING).intValue();
    }

    /**
     * 配方匹配容错：
     * 1) 精确匹配（trim 后）
     * 2) 模糊候选匹配（按标准化编码评分，优先最接近）
     */
    private TapeFormula resolveFormulaByFinishedCode(String finishedCode) {
        String code = trim(finishedCode);
        if (code == null) {
            return null;
        }

        Map<Long, TapeFormula> candidates = new LinkedHashMap<>();

        TapeFormula exact = tapeFormulaMapper.selectByMaterialCode(code);
        if (isFormulaBasicUsable(exact)) {
            return exact;
        }
        mergeFormulaCandidates(candidates, Collections.singletonList(exact));

        String normalizedCode = normalizeMaterialCode(code);
        if (normalizedCode != null && !normalizedCode.equals(code)) {
            TapeFormula normalizedExact = tapeFormulaMapper.selectByMaterialCode(normalizedCode);
            if (isFormulaBasicUsable(normalizedExact)) {
                return normalizedExact;
            }
            mergeFormulaCandidates(candidates, Collections.singletonList(normalizedExact));
        }

        List<String> searchKeys = buildFormulaSearchKeys(code);

        for (String key : searchKeys) {
            List<TapeFormula> activeList = tapeFormulaMapper.selectList(key, null, null, 1, 0, 200);
            mergeFormulaCandidates(candidates, activeList);

            if (candidates.isEmpty()) {
                List<TapeFormula> allStatusList = tapeFormulaMapper.selectList(key, null, null, null, 0, 200);
                mergeFormulaCandidates(candidates, allStatusList);
            }

            TapeFormula best = pickBestFormulaCandidate(code, candidates.values());
            if (best != null) {
                return best;
            }
        }

        return pickBestFormulaCandidate(code, candidates.values());
    }

    private boolean isFormulaBasicUsable(TapeFormula formula) {
        return formula != null
                && formula.getId() != null
                && formula.getCoatingArea() != null
                && formula.getCoatingArea().compareTo(BigDecimal.ZERO) > 0;
    }

    private void mergeFormulaCandidates(Map<Long, TapeFormula> target, List<TapeFormula> list) {
        if (list == null || list.isEmpty()) {
            return;
        }
        for (TapeFormula f : list) {
            if (f == null || f.getId() == null) {
                continue;
            }
            target.putIfAbsent(f.getId(), f);
        }
    }

    private List<String> buildFormulaSearchKeys(String materialCode) {
        LinkedHashSet<String> keys = new LinkedHashSet<>();
        String code = trim(materialCode);
        if (code == null) {
            return Collections.emptyList();
        }

        keys.add(code);
        String normalized = normalizeMaterialCode(code);
        if (normalized != null) {
            keys.add(normalized);
        }

        // 从混合字符串中提取可能的嵌入料号（例如任务号+订单号+成品料号拼接）
        for (String embedded : extractEmbeddedMaterialCodeCandidates(code)) {
            keys.add(embedded);
        }

        String current = normalized == null ? code : normalized;
        for (int i = 0; i < 3; i++) {
            int lastDash = current.lastIndexOf('-');
            if (lastDash <= 0) {
                break;
            }
            current = current.substring(0, lastDash);
            if (current.length() >= 6) {
                keys.add(current);
            }
        }
        return new ArrayList<>(keys);
    }

    private List<String> extractEmbeddedMaterialCodeCandidates(String text) {
        String normalized = normalizeMaterialCode(text);
        if (normalized == null) {
            return Collections.emptyList();
        }

        LinkedHashSet<String> keys = new LinkedHashSet<>();
        // 典型格式：302-R00-6530-T01-1200 或 001302-R00-6530-T01-1200
        Pattern p = Pattern.compile("([A-Z0-9]{2,8}-R\\d{2}-\\d{3,5}-T\\d{2}-\\d{3,8})");
        Matcher m = p.matcher(normalized);
        while (m.find()) {
            String hit = m.group(1);
            if (hit == null || hit.length() < 10) {
                continue;
            }
            keys.add(hit);

            String[] parts = hit.split("-");
            if (parts.length >= 5) {
                String last = parts[4];
                if (last.matches("\\d{5,}")) {
                    keys.add(parts[0] + "-" + parts[1] + "-" + parts[2] + "-" + parts[3] + "-" + last.substring(0, 4));
                    keys.add(parts[0] + "-" + parts[1] + "-" + parts[2] + "-" + parts[3] + "-" + last.substring(0, 3));
                }

                String first = parts[0];
                if (first.matches("\\d{4,8}")) {
                    String trimmedFirst = first.replaceFirst("^0+", "");
                    if (trimmedFirst.length() >= 3) {
                        keys.add(trimmedFirst + "-" + parts[1] + "-" + parts[2] + "-" + parts[3] + "-" + parts[4]);
                    }
                    if (first.length() > 3) {
                        keys.add(first.substring(first.length() - 3) + "-" + parts[1] + "-" + parts[2] + "-" + parts[3] + "-" + parts[4]);
                    }
                }
            }
        }

        return new ArrayList<>(keys);
    }

    private String normalizeFinishedCodeForFormula(String materialCode, String materialName) {
        String direct = trim(materialCode);
        if (direct == null) {
            return null;
        }

        List<String> candidates = new ArrayList<>();
        candidates.add(direct);
        candidates.addAll(extractEmbeddedMaterialCodeCandidates(direct));
        candidates.addAll(extractEmbeddedMaterialCodeCandidates(str(materialName)));

        for (String c : candidates) {
            String one = trim(c);
            if (one == null) {
                continue;
            }
            String normalized = normalizeMaterialCode(one);
            if (normalized == null) {
                continue;
            }

            Matcher strict = Pattern.compile("^[A-Z0-9]{2,8}-R\\d{2}-\\d{3,5}-T\\d{2}-\\d{3,4}$").matcher(normalized);
            if (strict.find()) {
                return normalized;
            }
        }

        // 回退：返回原始trim值，保持兼容
        return direct;
    }

    private TapeFormula pickBestFormulaCandidate(String finishedCode, Collection<TapeFormula> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return null;
        }
        String target = normalizeMaterialCode(finishedCode);
        TapeFormula best = null;
        int bestScore = Integer.MIN_VALUE;

        for (TapeFormula formula : candidates) {
            if (formula == null || formula.getMaterialCode() == null) {
                continue;
            }
            String formulaCode = normalizeMaterialCode(formula.getMaterialCode());
            int score = scoreFormulaCodeMatch(target, formulaCode);
            if (formula.getStatus() != null && formula.getStatus() == 1) {
                score += 20;
            }
            if (formula.getCoatingArea() != null && formula.getCoatingArea().compareTo(BigDecimal.ZERO) > 0) {
                score += 20;
            }
            if (formula.getUpdateTime() != null) {
                score += 2;
            }
            if (score > bestScore) {
                bestScore = score;
                best = formula;
            }
        }

        return bestScore >= 70 ? best : null;
    }

    private int scoreFormulaCodeMatch(String targetCode, String formulaCode) {
        if (targetCode == null || formulaCode == null) {
            return 0;
        }
        if (targetCode.equals(formulaCode)) {
            return 100;
        }
        if (targetCode.startsWith(formulaCode)) {
            return 90 + Math.min(formulaCode.length(), 9);
        }
        if (formulaCode.startsWith(targetCode)) {
            return 80 + Math.min(targetCode.length(), 9);
        }
        if (targetCode.contains(formulaCode) || formulaCode.contains(targetCode)) {
            return 65;
        }
        return 0;
    }

    private String normalizeMaterialCode(String code) {
        String t = trim(code);
        if (t == null) {
            return null;
        }
        return t.replace(" ", "")
                .replace("\t", "")
                .toUpperCase(Locale.ROOT);
    }

    private Map<String, Object> resultMap(int lockCount, int reqCount, Set<String> requestNos, List<Map<String, Object>> missingFormulaPlans) {
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("lockCount", lockCount);
        res.put("requestItemCount", reqCount);
        res.put("requestNos", new ArrayList<>(requestNos));
        List<Map<String, Object>> missing = missingFormulaPlans == null ? Collections.emptyList() : missingFormulaPlans;
        res.put("missingFormulaCount", missing.size());
        res.put("missingFormulaPlans", missing);
        return res;
    }

    private Map<String, Object> buildMissingFormulaRow(Long scheduleId,
                                                       String orderNo,
                                                       String finishedCode,
                                                       String finishedName,
                                                       String reason) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("scheduleId", scheduleId);
        row.put("orderNo", orderNo);
        row.put("materialCode", finishedCode);
        row.put("materialName", finishedName);
        row.put("reason", reason);
        return row;
    }

    private String trim(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private String str(Object obj) {
        return obj == null ? null : String.valueOf(obj);
    }

    private Long toLong(Object obj) {
        if (obj == null) {
            return null;
        }
        if (obj instanceof Number) {
            return ((Number) obj).longValue();
        }
        try {
            return Long.parseLong(String.valueOf(obj));
        } catch (Exception e) {
            return null;
        }
    }

    private BigDecimal toDecimal(Object obj) {
        if (obj == null) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(String.valueOf(obj));
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }
}
