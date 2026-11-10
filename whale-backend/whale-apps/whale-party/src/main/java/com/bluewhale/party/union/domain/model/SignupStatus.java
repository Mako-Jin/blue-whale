package com.bluewhale.party.union.domain.model;

import com.bluewhale.party.common.PartyErrorCode;

import java.util.Locale;

/**
 * 工会活动报名状态。
 *
 * <p>取消不是删除记录：同一活动同一会员始终只有一行，取消后再报名把状态改回
 * {@code SIGNED}，否则反复取消/报名会在名单里留下多条自相矛盾的行。</p>
 *
 * @author BlueWhale
 */
public enum SignupStatus {

    /** 已报名 */
    SIGNED("已报名"),

    /** 已取消 */
    CANCELLED("已取消");

    /** 中文标签 */
    private final String label;

    /**
     * 构造状态。
     *
     * @param label 中文标签
     */
    SignupStatus(String label) {
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
     * 解析状态；空值回落已报名（新报名记录的默认口径）。
     *
     * @param value 状态文本
     * @return 状态枚举
     */
    public static SignupStatus parse(String value) {
        if (value == null || value.isBlank()) {
            return SIGNED;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw PartyErrorCode.exception(PartyErrorCode.UNION_ACTIVITY_STATE_CONFLICT,
                    "报名状态不合法：" + value);
        }
    }

    /**
     * 是否已报名。
     *
     * @return 已报名返回 true
     */
    public boolean isSigned() {
        return this == SIGNED;
    }

    /**
     * 是否已取消。
     *
     * @return 已取消返回 true
     */
    public boolean isCancelled() {
        return this == CANCELLED;
    }
}
