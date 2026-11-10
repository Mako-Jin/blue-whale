package com.bluewhale.mall.catalog.domain.model;

/**
 * 活动状态。
 *
 * <p>只留启停两态：活动价能不能被命中完全由时间窗 + 状态共同决定，
 * 不引入「草稿/审核」这类流程态——运营建错活动直接停用重建，成本最低。</p>
 *
 * @author BlueWhale
 */
public enum ActivityStatus {

    /** 启用（时间窗内可命中） */
    ACTIVE,

    /** 停用（任何时候都不参与计价） */
    DISABLED;

    /**
     * 解析状态文本。
     *
     * @param value 文本
     * @return 状态；无法识别或为空时回落 DISABLED（未明确启用的活动一律不参与计价）
     */
    public static ActivityStatus parse(String value) {
        if (value == null || value.isBlank()) {
            return DISABLED;
        }
        try {
            return valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return DISABLED;
        }
    }
}
