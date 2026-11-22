package com.bluewhale.pay.enums;

/**
 * 对账批次状态（{@code pay_reconcile_batch.status} 的唯一取值来源）。
 *
 * <p>{@link #PARTIAL} 表示渠道不支持账单下载、已降级为逐笔状态查询：
 * 只发现「本地有、渠道状态不同」的差异，无法发现「渠道有、本地无」的补单差异，
 * 必须让看的人知道这批对账并不完整。</p>
 *
 * @author BlueWhale
 */
public enum PayReconcileBatchStatus {

    /** 执行中 */
    RUNNING("RUNNING"),

    /** 执行完成，账单与本地均已比对 */
    SUCCESS("SUCCESS"),

    /** 执行失败（取不到渠道账单等），不产出差异 */
    FAILED("FAILED"),

    /** 降级完成：仅逐笔状态查询，未做全量账单比对 */
    PARTIAL("PARTIAL");

    private final String code;

    /**
     * 状态码构造
     *
     * @param code 状态码（与库中存储值一致）
     */
    PayReconcileBatchStatus(String code) {
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
    public static PayReconcileBatchStatus fromCode(String code) {
        for (PayReconcileBatchStatus status : values()) {
            if (status.code.equals(code)) {
                return status;
            }
        }
        return null;
    }
}
