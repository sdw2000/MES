package com.fine.model.production.readiness;

/**
 * 齐套状态（后端英文码）
 */
public final class ReadinessStatus {

    private ReadinessStatus() {
    }

    public static final String READY = "READY";
    public static final String READY_BY_ETA = "READY_BY_ETA";
    public static final String SHORTAGE = "SHORTAGE";
    public static final String RISK = "RISK";

    public static String labelOf(String code) {
        if (code == null) return "-";
        switch (code) {
            case READY:
                return "已齐套";
            case READY_BY_ETA:
                return "预计齐套";
            case SHORTAGE:
                return "缺料";
            case RISK:
                return "风险";
            default:
                return code;
        }
    }
}
