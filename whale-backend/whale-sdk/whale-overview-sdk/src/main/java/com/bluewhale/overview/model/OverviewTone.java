package com.bluewhale.overview.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 指标语气：决定牌面那一行字的颜色。
 *
 * <p>颜色不写死在服务端（服务端不该懂调色板），也不由前端按数值大小猜
 * （"营收下降"到底是坏还是季节性，只有业务知道）。因此由 provider 判定语气，
 * 渲染层只做映射。</p>
 *
 * @author BlueWhale
 */
public enum OverviewTone {

    /** 正常/良好 */
    GOOD("good"),

    /** 需关注 */
    WARN("warn"),

    /** 异常/严重 */
    BAD("bad"),

    /** 中性，无判断 */
    FLAT("flat");

    /** JSON 字面量 */
    private final String value;

    /**
     * 构造语气枚举
     *
     * @param value JSON 字面量
     */
    OverviewTone(String value) {
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
     * @return 对应枚举；未知值回落到 {@link #FLAT}
     */
    @JsonCreator
    public static OverviewTone fromValue(String value) {
        if (value != null) {
            for (OverviewTone tone : values()) {
                if (tone.value.equalsIgnoreCase(value)) {
                    return tone;
                }
            }
        }
        return FLAT;
    }
}
