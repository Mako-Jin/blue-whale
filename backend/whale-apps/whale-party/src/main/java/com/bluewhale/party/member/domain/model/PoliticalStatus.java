package com.bluewhale.party.member.domain.model;

/**
 * 政治面貌。
 *
 * <p>只区分正式与预备：入党申请人与积极分子属于「发展党员」流程，
 * 在成为预备党员之前没有党员档案，不落 {@code pty_member}。
 * 把发展对象塞进党员台账会让「党员总数」这类统计长期虚高。</p>
 *
 * @author BlueWhale
 */
public enum PoliticalStatus {

    /** 正式党员 */
    FULL_MEMBER("正式党员"),

    /** 预备党员 */
    PROBATIONARY("预备党员");

    /** 中文标签 */
    private final String label;

    /**
     * 构造政治面貌。
     *
     * @param label 中文标签
     */
    PoliticalStatus(String label) {
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
     * 是否预备党员。
     *
     * @return 预备党员返回 true
     */
    public boolean isProbationary() {
        return this == PROBATIONARY;
    }

    /**
     * 解析政治面貌；未知值与空值按正式党员处理（历史数据兜底，不因脏值拒读）。
     *
     * @param code 面码
     * @return 政治面貌
     */
    public static PoliticalStatus parse(String code) {
        if (code == null || code.isBlank()) {
            return FULL_MEMBER;
        }
        for (PoliticalStatus status : values()) {
            if (status.name().equalsIgnoreCase(code.trim())) {
                return status;
            }
        }
        return FULL_MEMBER;
    }
}
