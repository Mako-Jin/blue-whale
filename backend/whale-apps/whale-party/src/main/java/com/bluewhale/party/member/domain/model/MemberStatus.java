package com.bluewhale.party.member.domain.model;

/**
 * 党员档案状态。
 *
 * <p>{@link #TRANSFERRED}（已转出）是<b>只读终态</b>：组织关系转出后档案仍保留
 * （历史党费与组织生活记录要能追溯），但不再允许改支部、改党费基数、生成新账单。</p>
 *
 * @author BlueWhale
 */
public enum MemberStatus {

    /** 在册 */
    ACTIVE("在册"),

    /** 已转出（只读） */
    TRANSFERRED("已转出"),

    /** 停籍（保留党籍但暂停组织生活与党费） */
    SUSPENDED("停籍");

    /** 中文标签 */
    private final String label;

    /**
     * 构造状态。
     *
     * @param label 中文标签
     */
    MemberStatus(String label) {
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
     * 是否在册。
     *
     * @return 在册返回 true
     */
    public boolean isActive() {
        return this == ACTIVE;
    }

    /**
     * 是否只读（已转出）。
     *
     * @return 只读返回 true
     */
    public boolean isReadOnly() {
        return this == TRANSFERRED;
    }

    /**
     * 是否应缴党费（在册才缴）。
     *
     * @return 应缴返回 true
     */
    public boolean shouldPayDues() {
        return this == ACTIVE;
    }

    /**
     * 解析状态；未知值与空值按停籍处理（失败关闭，避免脏数据被当成在册计费）。
     *
     * @param code 状态码
     * @return 状态
     */
    public static MemberStatus parse(String code) {
        if (code == null || code.isBlank()) {
            return SUSPENDED;
        }
        for (MemberStatus status : values()) {
            if (status.name().equalsIgnoreCase(code.trim())) {
                return status;
            }
        }
        return SUSPENDED;
    }
}
