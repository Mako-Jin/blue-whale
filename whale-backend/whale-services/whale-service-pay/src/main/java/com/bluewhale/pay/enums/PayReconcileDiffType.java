package com.bluewhale.pay.enums;

/**
 * 对账差异类型（{@code pay_reconcile_diff.diff_type}）。
 *
 * <p>只有 {@link #STATUS_MISMATCH} 在「渠道 PAID、本地 PAYING」时允许以渠道为准补偿；
 * 金额不一致一律人工核实，禁止自动改本地账。</p>
 *
 * @author BlueWhale
 */
public enum PayReconcileDiffType {

    /** 本地有、渠道无（可能渠道延迟入账） */
    LOCAL_ONLY("LOCAL_ONLY"),

    /** 渠道有、本地无（需补单，涉及资金入账） */
    CHANNEL_ONLY("CHANNEL_ONLY"),

    /** 单号一致、状态不同 */
    STATUS_MISMATCH("STATUS_MISMATCH"),

    /** 单号一致、金额或币种不同 */
    AMOUNT_MISMATCH("AMOUNT_MISMATCH");

    private final String code;

    /**
     * 类型码构造
     *
     * @param code 类型码（与库中存储值一致）
     */
    PayReconcileDiffType(String code) {
        this.code = code;
    }

    /**
     * 落库与本系统内部比较使用的类型码
     *
     * @return 类型字符串
     */
    public String code() {
        return code;
    }

    /**
     * 按类型码解析枚举
     *
     * @param code 类型码
     * @return 匹配的枚举，未知返回 {@code null}
     */
    public static PayReconcileDiffType fromCode(String code) {
        for (PayReconcileDiffType type : values()) {
            if (type.code.equals(code)) {
                return type;
            }
        }
        return null;
    }
}
