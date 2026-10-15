package com.bluewhale.sts.audit.auditlog;

/**
 * 审计的结果。
 *
 * <p><b>为什么只有两个取值，而不是"成功 / 业务拒绝 / 系统异常"三档：</b>
 * 三档听起来更细，实际会把"这次操作发生了没有"这个最要紧的判断搅浑 ——
 * 业务拒绝与系统异常在审计语境里是同一件事：<b>这次操作没有生效</b>。
 * 再细的分档由 {@code error_code} 承担（它本来就是分好类的错误码），
 * 把分类塞进结果枚举里只会造出第二套与错误码并存的判断标准。
 *
 * <p>落库用大写（{@code SUCCESS} / {@code FAILURE}）：审计是给人逐条看的，
 * 大小写在这里没有跨端契约的压力（不像日志级别要对齐前端 SDK），
 * 可读性优先。
 */
public enum AuditResult {

    /** 业务代码正常跑完，且响应码为成功 */
    SUCCESS,

    /** 被拒绝或抛异常：包括越权、参数非法、业务规则拒绝、未预期异常 */
    FAILURE;

    public String wireName() {
        return name();
    }

    /**
     * 大小写不敏感解析；无法识别返回 {@code null}。
     *
     * <p>不兜底成 {@code SUCCESS}：把非法值悄悄当成成功，会让上游的拼写错误永远暴露不出来
     * （与 {@code Status.parse} / {@code ClientLogLevel.parse} 同一取舍）。
     * 查询侧认不得时按原样比，见 {@code FilterValue.enumValue}。
     */
    public static AuditResult parse(String value) {
        if (value == null) {
            return null;
        }
        for (AuditResult result : values()) {
            if (result.name().equalsIgnoreCase(value.trim())) {
                return result;
            }
        }
        return null;
    }

    public boolean isSuccess() {
        return this == SUCCESS;
    }
}
