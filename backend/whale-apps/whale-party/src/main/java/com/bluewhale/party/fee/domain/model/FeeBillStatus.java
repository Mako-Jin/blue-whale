package com.bluewhale.party.fee.domain.model;

/**
 * 党费账单状态。
 *
 * @author BlueWhale
 */
public enum FeeBillStatus {

    /** 未缴 */
    UNPAID("未缴"),

    /** 部分缴纳 */
    PARTIAL("部分缴纳"),

    /** 已缴清 */
    PAID("已缴清"),

    /** 免缴（经支部研究同意的困难党员） */
    WAIVED("免缴");

    /** 中文标签 */
    private final String label;

    /**
     * 构造状态。
     *
     * @param label 中文标签
     */
    FeeBillStatus(String label) {
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
     * 是否已结清（已缴清或免缴）。
     *
     * @return 结清返回 true
     */
    public boolean isSettled() {
        return this == PAID || this == WAIVED;
    }

    /**
     * 解析状态；未知值与空值按未缴处理（宁可显示欠缴，也不要静默当成已缴）。
     *
     * @param code 状态码
     * @return 状态
     */
    public static FeeBillStatus parse(String code) {
        if (code == null || code.isBlank()) {
            return UNPAID;
        }
        for (FeeBillStatus status : values()) {
            if (status.name().equalsIgnoreCase(code.trim())) {
                return status;
            }
        }
        return UNPAID;
    }
}
