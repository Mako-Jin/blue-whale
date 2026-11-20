package com.bluewhale.overview.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 告警条严重度。
 *
 * <p>只有三档：多一档就得在 3D 厅里多一种灯光颜色，
 * 而人对"红/黄/灰"的辨识远好于四五个渐变色。</p>
 *
 * @author BlueWhale
 */
public enum OverviewAlertLevel {

    /** 提示 */
    INFO("info"),

    /** 警告 */
    WARNING("warning"),

    /** 严重 */
    CRITICAL("critical");

    /** JSON 字面量 */
    private final String value;

    /**
     * 构造级别枚举
     *
     * @param value JSON 字面量
     */
    OverviewAlertLevel(String value) {
        this.value = value;
    }

    /**
     * 取 JSON 字面量
     *
     * @return 小写字面量
     */
    @JsonValue
    public String getValue() {
        return value;
    }

    /**
     * 由 JSON 字面量反解
     *
     * @param value JSON 字面量，忽略大小写
     * @return 对应枚举；未知值回落到 {@link #WARNING}（未知严重度按需要人看处理）
     */
    @JsonCreator
    public static OverviewAlertLevel fromValue(String value) {
        if (value != null) {
            for (OverviewAlertLevel level : values()) {
                if (level.value.equalsIgnoreCase(value)) {
                    return level;
                }
            }
        }
        return WARNING;
    }
}
