package com.bluewhale.oa.integration.domain.model;

/**
 * Outbox 事件状态。
 *
 * @author BlueWhale
 */
public enum OutboxStatus {

    /** 待发送 */
    PENDING,

    /** 已发送 */
    SENT,

    /** 失败待重试 */
    FAILED,

    /** 死信，需人工介入 */
    DEAD;

    /**
     * 是否为终态。
     *
     * @return 已发送或死信返回 {@code true}
     */
    public boolean terminal() {
        return this == SENT || this == DEAD;
    }
}
