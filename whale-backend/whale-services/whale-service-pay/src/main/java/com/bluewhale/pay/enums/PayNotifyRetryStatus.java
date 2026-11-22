package com.bluewhale.pay.enums;

/**
 * 手工重试通知任务的结果状态。
 *
 * <p>布尔返回值无法区分「投递失败已排重试」与「任务正被其他实例投递」，
 * 运营会把后者误判成失败并反复点击；改为三态后前端可给出准确提示。</p>
 *
 * @author BlueWhale
 */
public enum PayNotifyRetryStatus {

    /** 本次投递成功，或任务此前已成功 */
    SUCCESS("SUCCESS"),

    /** 任务正被其他实例投递或状态已变更，本次未重复投递 */
    IN_PROGRESS("IN_PROGRESS"),

    /** 本次投递失败，已按退避策略安排下次自动重试 */
    RETRY_PENDING("RETRY_PENDING");

    private final String code;

    /**
     * 构造结果状态。
     *
     * @param code 状态码
     */
    PayNotifyRetryStatus(String code) {
        this.code = code;
    }

    /**
     * 状态码（返回给前端）。
     *
     * @return 状态码
     */
    public String code() {
        return code;
    }
}
