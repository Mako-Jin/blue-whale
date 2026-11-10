package com.bluewhale.oa.administrative.domain.model;

/**
 * 采购 ERP 推送状态。
 *
 * @author BlueWhale
 */
public enum PurchasePushStatus {

    /** 未推送 */
    NONE,

    /** 待推送 */
    PENDING,

    /** 已推送 */
    SENT,

    /** 推送失败 */
    FAILED;

    /**
     * 安全解析状态文本，未知值返回 {@link #NONE}。
     *
     * @param value 状态文本
     * @return 状态枚举
     */
    public static PurchasePushStatus of(String value) {
        if (value == null || value.isBlank()) {
            return NONE;
        }
        try {
            return valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            return NONE;
        }
    }
}
