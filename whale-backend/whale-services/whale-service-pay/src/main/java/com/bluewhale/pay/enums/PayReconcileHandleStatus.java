package com.bluewhale.pay.enums;

/**
 * 对账差异处置状态（{@code pay_reconcile_diff.handle_status}）。
 *
 * <p>状态机单向：{@code OPEN → CONFIRMED / COMPENSATED / SUSPENDED}；
 * {@link #SUSPENDED} 仍计为「未处置」，概览继续告警，避免挂起等于静默关闭。</p>
 *
 * @author BlueWhale
 */
public enum PayReconcileHandleStatus {

    /** 待处置 */
    OPEN("OPEN"),

    /** 人工确认本地无误 */
    CONFIRMED("CONFIRMED"),

    /** 以渠道为准完成补偿 */
    COMPENSATED("COMPENSATED"),

    /** 暂不处理（持续告警） */
    SUSPENDED("SUSPENDED");

    private final String code;

    /**
     * 状态码构造
     *
     * @param code 状态码（与库中存储值一致）
     */
    PayReconcileHandleStatus(String code) {
        this.code = code;
    }

    /**
     * 落库与本系统内部比较使用的状态码
     *
     * @return 状态字符串
     */
    public String code() {
        return code;
    }

    /**
     * 按状态码解析枚举
     *
     * @param code 状态码
     * @return 匹配的枚举，未知返回 {@code null}
     */
    public static PayReconcileHandleStatus fromCode(String code) {
        for (PayReconcileHandleStatus status : values()) {
            if (status.code.equals(code)) {
                return status;
            }
        }
        return null;
    }
}
