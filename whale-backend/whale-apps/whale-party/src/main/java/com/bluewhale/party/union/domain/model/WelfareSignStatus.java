package com.bluewhale.party.union.domain.model;

import com.bluewhale.party.common.PartyErrorCode;

import java.util.Locale;

/**
 * 节日福利明细签收状态。
 *
 * <p>签收只进不退：已签收再签按幂等处理，不回退也不改写签收时间，
 * 否则「签收时间」会随反复点击漂移，对账时无法判断实际签收时点。</p>
 *
 * @author BlueWhale
 */
public enum WelfareSignStatus {

    /** 未签收 */
    UNSIGNED("未签收"),

    /** 已签收 */
    SIGNED("已签收");

    /** 中文标签 */
    private final String label;

    /**
     * 构造状态。
     *
     * @param label 中文标签
     */
    WelfareSignStatus(String label) {
        this.label = label;
    }

    /**
     * 查询中文标签。
     *
     * @return 中文标签
     */
    public String getLabel() {
        return label;
    }

    /**
     * 解析状态；空值回落未签收（新生成明细的默认口径）。
     *
     * @param value 状态文本
     * @return 状态枚举
     */
    public static WelfareSignStatus parse(String value) {
        if (value == null || value.isBlank()) {
            return UNSIGNED;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw PartyErrorCode.exception(PartyErrorCode.UNION_WELFARE_ITEM_STATE_CONFLICT,
                    "福利签收状态不合法：" + value);
        }
    }

    /**
     * 是否已签收。
     *
     * @return 已签收返回 true
     */
    public boolean isSigned() {
        return this == SIGNED;
    }
}
