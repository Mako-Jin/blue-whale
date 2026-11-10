package com.bluewhale.lms.cert.domain.model;

/**
 * 证书状态。
 *
 * <p>只落库前三个：{@link #ACTIVE} / {@link #REVOKED} / {@link #RENEWED}，它们都是
 * <b>人工动作的结果</b>。{@link #EXPIRED} 是展示期的派生状态——由 {@code valid_to}
 * 与当前时间比较得出，不写库。这样做的理由很直接：到期是时间的函数，不是事件，
 * 一旦落库就必须有定时任务去刷，任务漏跑或停摆就会出现「已过期但仍显示有效」，
 * 而按时间比较永远不会错。</p>
 *
 * @author BlueWhale
 */
public enum CertStatus {

    /** 有效（未撤销、未换发；是否过期需结合有效期判断） */
    ACTIVE,

    /** 已撤销（合规处置，不可再撤销、不可换发） */
    REVOKED,

    /** 已换发（被新证书取代，只读留档） */
    RENEWED,

    /** 已过期（派生状态，不落库） */
    EXPIRED;

    /**
     * 解析状态（未知值按 {@link #ACTIVE} 处理，避免历史数据导致列表不可用）。
     *
     * @param value 原始值
     * @return 状态
     */
    public static CertStatus parse(String value) {
        if (value == null || value.isBlank()) {
            return ACTIVE;
        }
        for (CertStatus status : values()) {
            if (status.name().equalsIgnoreCase(value.trim())) {
                return status;
            }
        }
        return ACTIVE;
    }

    /**
     * 是否为可对外出示的有效状态。
     *
     * @return 有效返回 true
     */
    public boolean isEffective() {
        return this == ACTIVE;
    }

    /**
     * 是否为终态（撤销 / 换发后不再变化）。
     *
     * @return 终态返回 true
     */
    public boolean isTerminal() {
        return this == REVOKED || this == RENEWED;
    }
}
