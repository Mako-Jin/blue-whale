package com.bluewhale.lms.integration.domain.model;

/**
 * 通知投递状态。
 *
 * @author BlueWhale
 */
public enum NotifyStatus {

    /** 投递成功 */
    SENT,

    /** 投递失败（下一轮可重试并覆盖为 {@link #SENT}） */
    FAILED;

    /**
     * 是否已成功投递。
     *
     * <p>只有 {@code SENT} 才算「发过了」。失败记录必须允许重试，
     * 否则消息服务抖动一次就会永久漏发。</p>
     *
     * @return 成功返回 {@code true}
     */
    public boolean isSent() {
        return this == SENT;
    }

    /**
     * 解析状态（未知值按 {@link #FAILED} 处理，倾向于重试而不是静默跳过）。
     *
     * @param value 原始值
     * @return 状态
     */
    public static NotifyStatus parse(String value) {
        if (value == null || value.isBlank()) {
            return FAILED;
        }
        for (NotifyStatus status : values()) {
            if (status.name().equalsIgnoreCase(value.trim())) {
                return status;
            }
        }
        return FAILED;
    }
}
