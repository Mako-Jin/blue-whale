package com.bluewhale.mall.catalog.domain.model;

import java.util.Locale;

/**
 * 类目状态。
 *
 * <p>停用（{@code DISABLED}）的类目不再出现在买家侧筛选里，但历史商品与订单照旧可见：
 * 类目是导航结构，不是商品的存活开关——把类目停用当成「下架该类目全部商品」，
 * 会让运营在毫不知情的情况下清空货架。</p>
 *
 * @author BlueWhale
 */
public enum CategoryStatus {

    /** 启用 */
    ACTIVE,

    /** 停用（仅影响买家侧导航） */
    DISABLED;

    /**
     * 解析状态文本。
     *
     * @param value 状态文本，可为 null
     * @return 状态；无法识别时回落到 {@link #ACTIVE}
     */
    public static CategoryStatus parse(String value) {
        if (value == null || value.isBlank()) {
            return ACTIVE;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return ACTIVE;
        }
    }
}
