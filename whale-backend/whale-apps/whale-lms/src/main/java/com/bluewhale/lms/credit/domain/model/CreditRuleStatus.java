package com.bluewhale.lms.credit.domain.model;

/**
 * 学分规则状态。
 *
 * @author BlueWhale
 */
public enum CreditRuleStatus {

    /** 启用：可用于记账 */
    ENABLED,

    /** 停用：保留历史，不参与新的记账 */
    DISABLED;

    /**
     * 解析状态（未知值按 {@link #DISABLED} 处理，避免坏值导致误记账）。
     *
     * @param value 原始值
     * @return 状态
     */
    public static CreditRuleStatus parse(String value) {
        if (value == null || value.isBlank()) {
            return DISABLED;
        }
        for (CreditRuleStatus status : values()) {
            if (status.name().equalsIgnoreCase(value.trim())) {
                return status;
            }
        }
        return DISABLED;
    }

    /**
     * 是否可用于记账。
     *
     * @return 启用返回 true
     */
    public boolean canAccrue() {
        return this == ENABLED;
    }
}
