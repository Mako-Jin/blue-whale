package com.bluewhale.party.union.domain.model;

import com.bluewhale.party.common.PartyErrorCode;

import java.util.Locale;

/**
 * 工会会籍状态。
 *
 * <p>{@code ACTIVE} 在册、{@code WITHDRAWN} 已退会（终态）。退会后再入会**新建档案**，
 * 不复活旧档——旧档保留历史会费与活动记录，复活会让「何时在册」这段事实无法复原。</p>
 *
 * @author BlueWhale
 */
public enum UnionMemberStatus {

    /** 在册 */
    ACTIVE,

    /** 已退会（终态） */
    WITHDRAWN;

    /**
     * 解析状态，空值回落 {@link #ACTIVE}。
     *
     * @param value 状态文本
     * @return 状态枚举
     */
    public static UnionMemberStatus parse(String value) {
        if (value == null || value.isBlank()) {
            return ACTIVE;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw PartyErrorCode.exception(PartyErrorCode.UNION_MEMBER_INVALID, "会籍状态不合法：" + value);
        }
    }

    /**
     * 是否为在册状态。
     *
     * @return 在册返回 true
     */
    public boolean isActive() {
        return this == ACTIVE;
    }
}
