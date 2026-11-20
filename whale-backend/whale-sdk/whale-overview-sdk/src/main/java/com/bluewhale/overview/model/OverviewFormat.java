package com.bluewhale.overview.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 指标/图表的排版口径。
 *
 * <p>只表达"怎么排版"，不表达业务语义。之所以把它做成枚举而不是让每个服务自己拼字符串：
 * 各服务对百分比的习惯口径不一致（有的返 {@code 0.82}，有的返 {@code 82}），
 * 前端不得不靠"小于 1.5 就当比率"兜底，遇到真实的 0.8% 会显示成 80%。
 * 契约规定 {@link #PERCENT} 一律传比率，格式化只在渲染层做一次。</p>
 *
 * @author BlueWhale
 */
public enum OverviewFormat {

    /** 计数：千分位 + 万/亿折算 */
    COUNT("count"),

    /** 金额：千分位 + ￥，万/亿折算 */
    MONEY("money"),

    /** 百分比：值传比率（0.82 表示 82%），渲染保留一位小数 */
    PERCENT("percent"),

    /** 时长：值传秒数，渲染为 "12 分钟" 这类可读文本 */
    DURATION("duration"),

    /** 纯文本：值仍是数字（用于排序/比对），展示交给调用方 */
    TEXT("text");

    /** JSON 字面量 */
    private final String value;

    /**
     * 构造格式枚举
     *
     * @param value JSON 字面量
     */
    OverviewFormat(String value) {
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
     * @return 对应枚举；未知值回落到 {@link #COUNT}（保证前端永远有可用的排版分支）
     */
    @JsonCreator
    public static OverviewFormat fromValue(String value) {
        if (value != null) {
            for (OverviewFormat format : values()) {
                if (format.value.equalsIgnoreCase(value)) {
                    return format;
                }
            }
        }
        return COUNT;
    }
}
