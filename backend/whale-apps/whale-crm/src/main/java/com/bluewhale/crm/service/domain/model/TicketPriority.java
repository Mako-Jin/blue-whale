package com.bluewhale.crm.service.domain.model;

import com.bluewhale.crm.common.CrmErrorCode;

/**
 * 工单优先级。
 *
 * <p>P1 最高、P3 最低；升级时按 rank 递减（P3 → P2 → P1），P1 不再升级。</p>
 *
 * @author BlueWhale
 */
public enum TicketPriority {

    /** 紧急 */
    P1(1),

    /** 普通 */
    P2(2),

    /** 低 */
    P3(3);

    /** 等级序号，数值越小优先级越高 */
    private final int rank;

    /**
     * 构造优先级。
     *
     * @param rank 等级序号
     */
    TicketPriority(int rank) {
        this.rank = rank;
    }

    /**
     * 查询等级序号。
     *
     * @return 等级序号
     */
    public int getRank() {
        return rank;
    }

    /**
     * 解析优先级。
     *
     * @param value 文本
     * @return 优先级
     */
    public static TicketPriority from(String value) {
        if (value == null || value.isBlank()) {
            throw CrmErrorCode.exception(CrmErrorCode.PARAM_INVALID, "工单优先级不能为空");
        }
        for (TicketPriority priority : values()) {
            if (priority.name().equals(value.trim().toUpperCase(java.util.Locale.ROOT))) {
                return priority;
            }
        }
        throw CrmErrorCode.exception(CrmErrorCode.PARAM_INVALID, "工单优先级只允许 P1/P2/P3");
    }

    /**
     * 升级一级。
     *
     * @return 升级后的优先级；P1 保持不变
     */
    public TicketPriority escalate() {
        if (rank <= 1) {
            return this;
        }
        for (TicketPriority priority : values()) {
            if (priority.rank == rank - 1) {
                return priority;
            }
        }
        return this;
    }
}
