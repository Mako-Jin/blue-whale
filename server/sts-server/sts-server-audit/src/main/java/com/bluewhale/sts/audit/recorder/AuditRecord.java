package com.bluewhale.sts.audit.recorder;

import com.bluewhale.sts.audit.auditlog.AuditResult;

/**
 * 一条待落库的审计事实 —— 拦截器组装完，交给 {@link AuditRecorder} 写下去。
 *
 * <p>用 {@code record}（Java 21）而不是一个可变的 JavaBean：它在跨过
 * "组装 → 落库"这一步之后**不该再被改动**。若它是可变的，某处顺手补一个字段，
 * 落库内容就与拦截器当时的判断悄悄不一致了 —— 而审计最不能接受的就是
 * "记录下来的不是当时发生的事"。
 *
 * <p>刻意**不含 {@code serverTime}**：记录时间由 {@link AuditRecorder} 在真正落库的那一刻取，
 * 而不是由组装方传进来。若让拦截器传时间，那么在"组装"与"落库"之间万一有排队或重试，
 * 记下的时间就比实际写入早 —— 审计的时间戳只有一个权威来源，它应该是最靠近落库的那一刻。
 */
public record AuditRecord(

        String action,

        String resource,

        String targetId,

        Long operatorId,

        String operatorName,

        Long tenantId,

        AuditResult result,

        Integer errorCode,

        String errorMessage,

        String httpMethod,

        String requestUri,

        String ip,

        String userAgent,

        String traceId,

        long durationMs) {
}
