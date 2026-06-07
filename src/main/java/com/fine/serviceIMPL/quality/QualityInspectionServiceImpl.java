package com.fine.serviceIMPL.quality;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fine.Dao.quality.QualityInspectionRecordMapper;
import com.fine.model.quality.QualityInspectionRecord;
import com.fine.service.quality.QualityInspectionService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.dao.DuplicateKeyException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Service
public class QualityInspectionServiceImpl extends ServiceImpl<QualityInspectionRecordMapper, QualityInspectionRecord>
        implements QualityInspectionService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Override
    public IPage<QualityInspectionRecord> list(Page<QualityInspectionRecord> page,
                                               String inspectionType,
                                               String inspectionNo,
                                               String batchNo,
                                               String rollCode,
                                               String materialCode,
                                               String inspectorName,
                                               String result,
                                               String startDate,
                                               String endDate) {
        String normalizedStartDate = normalizeDateStart(startDate);
        String normalizedEndDate = normalizeDateEnd(endDate);
        return baseMapper.selectPaged(page, inspectionType, inspectionNo, batchNo, rollCode, materialCode, inspectorName, result, normalizedStartDate, normalizedEndDate);
    }

    @Override
    public QualityInspectionRecord detail(Long id) {
        return this.getById(id);
    }

    @Override
    public QualityInspectionRecord create(QualityInspectionRecord record) {
        if (!StringUtils.hasText(record.getInspectionType())) {
            record.setInspectionType("incoming");
        }

        if (StringUtils.hasText(record.getRollCode())) {
            record.setRollCode(record.getRollCode().trim());
        }

        if ("process".equalsIgnoreCase(record.getInspectionType())
            && "coating".equalsIgnoreCase(nullSafe(record.getProcessNode()))) {
            QualityInspectionRecord existing = findLatestCoatingProcessInspectionByRoll(record.getRollCode());
            if (existing != null) {
                if (!"pending".equalsIgnoreCase(nullSafe(existing.getOverallResult()))) {
                    throw new RuntimeException("该母卷已完成过程检验，请勿重复检验：" + record.getRollCode());
                }
                validateAndFillCoatingProcessInspection(record);
                LocalDateTime now = LocalDateTime.now();
                mergePendingInspection(existing, record, now);
                this.updateById(existing);
                return existing;
            }
            validateAndFillCoatingProcessInspection(record);
        }

        if (!StringUtils.hasText(record.getInspectionNo())) {
            record.setInspectionNo(generateInspectionNoSafely(resolveInspectionPrefix(record.getInspectionType())));
        }
        LocalDateTime now = LocalDateTime.now();
        if (record.getInspectionTime() == null) {
            record.setInspectionTime(now);
        }
        record.setCreatedAt(now);
        record.setUpdatedAt(now);
        try {
            this.save(record);
        } catch (DuplicateKeyException ex) {
            record.setInspectionNo(generateInspectionNoFallback(resolveInspectionPrefix(record.getInspectionType())));
            this.save(record);
        }
        return record;
    }

    @Override
    public QualityInspectionRecord updateRecord(QualityInspectionRecord record) {
        record.setUpdatedAt(LocalDateTime.now());
        this.updateById(record);
        return record;
    }

    @Override
    public boolean deleteRecord(Long id) {
        return this.removeById(id);
    }

    @Override
    public List<String> listDistinctBatchNos(String inspectionType, String materialCode, String keyword, int limit) {
        String type = StringUtils.hasText(inspectionType) ? inspectionType.trim() : "outbound";
        String code = StringUtils.hasText(materialCode) ? materialCode.trim() : null;
        String key = StringUtils.hasText(keyword) ? keyword.trim() : null;
        int safeLimit = limit > 0 ? Math.min(limit, 500) : 200;
        List<String> rows = baseMapper.selectDistinctBatchNos(type, code, key, safeLimit);
        return rows == null ? Collections.emptyList() : rows;
    }

    @Override
    public List<Map<String, Object>> listCoatingRollCandidates(String keyword, String materialCode, Boolean onlyPending, int limit) {
        String safeKeyword = StringUtils.hasText(keyword) ? keyword.trim() : null;
        String safeMaterialCode = StringUtils.hasText(materialCode) ? materialCode.trim() : null;
        int safeLimit = limit > 0 ? Math.min(limit, 500) : 200;
        Boolean pendingOnly = onlyPending == null ? Boolean.TRUE : onlyPending;
        List<Map<String, Object>> rows = baseMapper.selectCoatingRollCandidates(safeKeyword, safeMaterialCode, pendingOnly, safeLimit);
        return rows == null ? Collections.emptyList() : rows;
    }

    private void validateAndFillCoatingProcessInspection(QualityInspectionRecord record) {
        if (!StringUtils.hasText(record.getRollCode())) {
            throw new RuntimeException("涂布过程检验必须选择母卷号");
        }
        String rollCode = record.getRollCode().trim();
        record.setRollCode(rollCode);

        Map<String, Object> roll = baseMapper.selectCoatingRollByCode(rollCode);
        if (roll == null || roll.isEmpty()) {
            throw new RuntimeException("未找到对应涂布母卷，请确认母卷号是否由涂布报工生成");
        }

        if (!StringUtils.hasText(record.getBatchNo())) {
            record.setBatchNo(asString(roll.get("batchNo")));
        }
        if (!StringUtils.hasText(record.getMaterialCode())) {
            record.setMaterialCode(asString(roll.get("materialCode")));
        }
        if (!StringUtils.hasText(record.getMaterialName())) {
            record.setMaterialName(asString(roll.get("materialName")));
        }
        if (!StringUtils.hasText(record.getTaskType())) {
            record.setTaskType("COATING");
        }
        if (record.getTaskId() == null) {
            record.setTaskId(asLong(roll.get("scheduleId")));
        }
        if (record.getSampleQty() == null || record.getSampleQty() <= 0) {
            record.setSampleQty(1);
        }

        Map<String, Object> snapshot = parseSnapshot(record.getProcessSnapshot());
        ensureCoatingItemPresent(snapshot, "thickness", "厚度", 3);
        ensureCoatingItemPresent(snapshot, "color", "颜色", 3);
        ensureCoatingItemPresent(snapshot, "peelStrength", "剥离力", 3);
        ensureCoatingItemPresent(snapshot, "appearance", "外观", 1);

        String overall = resolveOverallResultFromSnapshot(snapshot);
        if (!StringUtils.hasText(record.getOverallResult()) || "pending".equalsIgnoreCase(record.getOverallResult())) {
            record.setOverallResult(overall);
        }
    }

    private Map<String, Object> parseSnapshot(String json) {
        if (!StringUtils.hasText(json)) {
            throw new RuntimeException("请填写厚度、颜色、剥离力检测结果");
        }
        try {
            return OBJECT_MAPPER.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            throw new RuntimeException("过程检验明细格式错误，请重新填写");
        }
    }

    private void ensureCoatingItemPresent(Map<String, Object> snapshot, String key, String label, int requiredCount) {
        Object item = snapshot.get(key);
        if (item == null) {
            throw new RuntimeException("请填写" + label + "检测结果");
        }
        if (item instanceof Map) {
            Object values = ((Map<?, ?>) item).get("values");
            Object value = ((Map<?, ?>) item).get("value");
            Object result = ((Map<?, ?>) item).get("result");
            if (values instanceof List) {
                int count = 0;
                for (Object v : (List<?>) values) {
                    if (StringUtils.hasText(nullSafe(v))) {
                        count++;
                    }
                }
                if (count < requiredCount) {
                    throw new RuntimeException(label + "需要填写" + requiredCount + "次检测值");
                }
            } else if (!StringUtils.hasText(nullSafe(value))) {
                throw new RuntimeException(label + "检测值不能为空");
            }
            String resultText = nullSafe(result);
            if (!StringUtils.hasText(resultText) || "pending".equalsIgnoreCase(resultText)) {
                throw new RuntimeException(label + "判定结果不能为空");
            }
            return;
        }
        if (!StringUtils.hasText(nullSafe(item))) {
            throw new RuntimeException(label + "检测结果不能为空");
        }
    }

    private String resolveOverallResultFromSnapshot(Map<String, Object> snapshot) {
        String[] keys = new String[]{"thickness", "color", "peelStrength", "appearance"};
        boolean allPass = true;
        for (String key : keys) {
            Object item = snapshot.get(key);
            String result = "pending";
            if (item instanceof Map) {
                result = nullSafe(((Map<?, ?>) item).get("result")).toLowerCase();
            } else if (item != null) {
                result = nullSafe(item).toLowerCase();
            }
            if ("fail".equals(result)) {
                return "fail";
            }
            if (!"pass".equals(result)) {
                allPass = false;
            }
        }
        return allPass ? "pass" : "pending";
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private Long asLong(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return Long.valueOf(String.valueOf(value));
        } catch (Exception e) {
            return null;
        }
    }

    private String nullSafe(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private String resolveInspectionPrefix(String inspectionType) {
        String type = inspectionType == null ? "" : inspectionType.trim().toLowerCase();
        if ("outbound".equals(type)) {
            return "OQC";
        }
        if ("process".equals(type)) {
            return "PQC";
        }
        return "IQC";
    }

    private QualityInspectionRecord findLatestCoatingProcessInspectionByRoll(String rollCode) {
        if (!StringUtils.hasText(rollCode)) {
            return null;
        }
        LambdaQueryWrapper<QualityInspectionRecord> qw = new LambdaQueryWrapper<>();
        qw.eq(QualityInspectionRecord::getInspectionType, "process")
            .eq(QualityInspectionRecord::getProcessNode, "coating")
            .eq(QualityInspectionRecord::getRollCode, rollCode.trim())
            .eq(QualityInspectionRecord::getIsDeleted, 0)
            .orderByDesc(QualityInspectionRecord::getId)
            .last("LIMIT 1");
        return this.getOne(qw, false);
    }

    private void mergePendingInspection(QualityInspectionRecord existing,
                                        QualityInspectionRecord incoming,
                                        LocalDateTime now) {
        existing.setBatchNo(pickText(incoming.getBatchNo(), existing.getBatchNo()));
        existing.setMaterialCode(pickText(incoming.getMaterialCode(), existing.getMaterialCode()));
        existing.setMaterialName(pickText(incoming.getMaterialName(), existing.getMaterialName()));
        existing.setTaskType(pickText(incoming.getTaskType(), existing.getTaskType()));
        existing.setTaskId(incoming.getTaskId() != null ? incoming.getTaskId() : existing.getTaskId());

        existing.setSampleQty(incoming.getSampleQty() != null ? incoming.getSampleQty() : existing.getSampleQty());
        existing.setPassQty(incoming.getPassQty() != null ? incoming.getPassQty() : existing.getPassQty());
        existing.setFailQty(incoming.getFailQty() != null ? incoming.getFailQty() : existing.getFailQty());

        existing.setOverallResult(pickText(incoming.getOverallResult(), existing.getOverallResult()));
        existing.setInspectorId(incoming.getInspectorId() != null ? incoming.getInspectorId() : existing.getInspectorId());
        existing.setInspectorName(pickText(incoming.getInspectorName(), existing.getInspectorName()));
        existing.setInspectionTime(incoming.getInspectionTime() != null ? incoming.getInspectionTime() : now);
        existing.setDefectType(pickText(incoming.getDefectType(), existing.getDefectType()));
        existing.setDefectDesc(pickText(incoming.getDefectDesc(), existing.getDefectDesc()));
        existing.setRemark(pickText(incoming.getRemark(), existing.getRemark()));
        existing.setProcessSnapshot(pickText(incoming.getProcessSnapshot(), existing.getProcessSnapshot()));
        existing.setUpdatedAt(now);
    }

    private String pickText(String preferred, String fallback) {
        if (StringUtils.hasText(preferred)) {
            return preferred.trim();
        }
        return fallback;
    }

    private String generateInspectionNoSafely(String prefix) {
        String no = baseMapper.generateInspectionNo(prefix);
        if (StringUtils.hasText(no)) {
            return no;
        }
        return generateInspectionNoFallback(prefix);
    }

    private String generateInspectionNoFallback(String prefix) {
        String datePart = LocalDate.now().format(DateTimeFormatter.ofPattern("yyMMdd"));
        long suffix = System.currentTimeMillis() % 100000000L;
        return prefix + datePart + "-" + suffix;
    }

    private String normalizeDateStart(String startDate) {
        if (!StringUtils.hasText(startDate)) {
            return null;
        }
        String date = startDate.trim();
        if (date.length() == 10) {
            return date + " 00:00:00";
        }
        return date;
    }

    private String normalizeDateEnd(String endDate) {
        if (!StringUtils.hasText(endDate)) {
            return null;
        }
        String date = endDate.trim();
        if (date.length() == 10) {
            try {
                LocalDate day = LocalDate.parse(date, DateTimeFormatter.ISO_LOCAL_DATE);
                return day.plusDays(1) + " 00:00:00";
            } catch (Exception ignore) {
                return date + " 23:59:59";
            }
        }
        return date;
    }
}
