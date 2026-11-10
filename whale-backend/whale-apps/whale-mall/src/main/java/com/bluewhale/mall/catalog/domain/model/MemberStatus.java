package com.bluewhale.mall.catalog.domain.model;

/**
 * 会员状态。
 *
 * <p>只有启停两态：会员价是否生效由「状态 + 有效期」共同决定，
 * 过期是事实判定（{@code valid_to}），不靠人工把状态改成 EXPIRED。</p>
 *
 * @author BlueWhale
 */
public enum MemberStatus {

    /** 生效 */
    ACTIVE,

    /** 停用 */
    DISABLED;

    /**
     * 解析状态文本。
     *
     * @param value 文本
     * @return 状态；无法识别或为空时回落 DISABLED（未明确启用的会员不享受会员价）
     */
    public static MemberStatus parse(String value) {
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
