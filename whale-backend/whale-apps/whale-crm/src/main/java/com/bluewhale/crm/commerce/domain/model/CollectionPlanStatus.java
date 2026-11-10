package com.bluewhale.crm.commerce.domain.model;

/**
 * 回款计划分期状态。
 *
 * @author BlueWhale
 */
public enum CollectionPlanStatus {

    /** 进行中 */
    OPEN,

    /** 已取消 */
    CANCELLED;

    /**
     * 校验文本是否合法。
     *
     * @param value 文本
     * @return 合法返回 true
     */
    public static boolean valid(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        for (CollectionPlanStatus status : values()) {
            if (status.name().equalsIgnoreCase(value.trim())) {
                return true;
            }
        }
        return false;
    }
}
