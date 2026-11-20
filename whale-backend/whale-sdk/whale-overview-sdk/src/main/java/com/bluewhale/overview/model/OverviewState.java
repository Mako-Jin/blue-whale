package com.bluewhale.overview.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 概览快照的三态。
 *
 * <p>三态是这套契约的核心：{@code live} 才是实时数据；{@code stale} 是"上次成功、这次失败"，
 * 前端必须明确标注而不是伪装成实时；{@code degraded} 表示从未成功过，只能显示空态。
 * 少了这个区分，"牌上有一个数字"与"牌上的数字是真的"就无法分辨。</p>
 *
 * @author BlueWhale
 */
public enum OverviewState {

    /** 本次实时取数成功 */
    LIVE("live"),

    /** 本次失败，返回的是上一次成功的快照 */
    STALE("stale"),

    /** 从未成功过，或部分块不可用 */
    DEGRADED("degraded");

    /** JSON 字面量 */
    private final String value;

    /**
     * 构造状态枚举
     *
     * @param value JSON 字面量
     */
    OverviewState(String value) {
        this.value = value;
    }

    /**
     * 取 JSON 字面量
     *
     * @return 小写字面量，与契约一致
     */
    @JsonValue
    public String getValue() {
        return value;
    }

    /**
     * 由 JSON 字面量反解
     *
     * @param value JSON 字面量，忽略大小写
     * @return 对应枚举；未知值按 {@link #DEGRADED} 处理（宁可显示空态也不要误报实时）
     */
    @JsonCreator
    public static OverviewState fromValue(String value) {
        if (value != null) {
            for (OverviewState state : values()) {
                if (state.value.equalsIgnoreCase(value)) {
                    return state;
                }
            }
        }
        return DEGRADED;
    }
}
