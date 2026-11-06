package com.bluewhale.lms.integration.domain.model;

/**
 * 员工在职状态。
 *
 * <p>取值与 ERP 的 {@code HrStatus} 完全一致（{@code ACTIVE} / {@code LEFT}），
 * 不在 LMS 侧另立一套翻译。两侧各存一套枚举，迟早会出现「LMS 认为在职、
 * ERP 认为已离职」的对不上账，而催办正是靠这个字段决定发不发通知。</p>
 *
 * <p>未知取值一律按 {@link #LEFT} 处理：宁可少发一条催办，也不要给
 * 已离职员工持续发消息。</p>
 *
 * @author BlueWhale
 */
public enum MemberStatus {

    /** 在职 */
    ACTIVE,

    /** 离职 */
    LEFT;

    /**
     * 解析状态。
     *
     * @param value 原始值
     * @return 状态；空值或未知值返回 {@link #LEFT}
     */
    public static MemberStatus parse(String value) {
        if (value == null || value.isBlank()) {
            return LEFT;
        }
        for (MemberStatus status : values()) {
            if (status.name().equalsIgnoreCase(value.trim())) {
                return status;
            }
        }
        return LEFT;
    }

    /**
     * 是否在职。
     *
     * @return 在职返回 {@code true}
     */
    public boolean isActive() {
        return this == ACTIVE;
    }
}
