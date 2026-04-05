package com.fine.model.stock.enums;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * 化工请购状态（后端统一英文状态码，前端按字典显示中文）
 */
public final class ChemicalRequisitionStatus {

    private ChemicalRequisitionStatus() {
    }

    public static final String DRAFT = "DRAFT";
    public static final String SUBMITTED = "SUBMITTED";
    public static final String APPROVED = "APPROVED";
    public static final String PO_CREATED = "PO_CREATED";
    public static final String PARTIAL_RECEIVED = "PARTIAL_RECEIVED";
    public static final String RECEIVED = "RECEIVED";

    private static final Map<String, String> LABEL_MAP = new HashMap<>();

    static {
        LABEL_MAP.put(DRAFT, "草稿");
        LABEL_MAP.put(SUBMITTED, "已提交");
        LABEL_MAP.put(APPROVED, "已审核");
        LABEL_MAP.put(PO_CREATED, "已转采购");
        LABEL_MAP.put(PARTIAL_RECEIVED, "部分到货");
        LABEL_MAP.put(RECEIVED, "已收货");
    }

    public static boolean isValid(String status) {
        if (status == null) {
            return false;
        }
        return Arrays.asList(DRAFT, SUBMITTED, APPROVED, PO_CREATED, PARTIAL_RECEIVED, RECEIVED)
                .contains(status.toUpperCase());
    }

    public static String normalize(String status) {
        return status == null ? null : status.trim().toUpperCase();
    }

    public static String labelOf(String status) {
        String normalized = normalize(status);
        if (normalized == null) {
            return "-";
        }
        return LABEL_MAP.getOrDefault(normalized, normalized);
    }
}
