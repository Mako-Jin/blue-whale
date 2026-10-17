package com.bluewhale.common.audit.aop;

import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.entity.AuditLogRecord;
import com.bluewhale.common.audit.mapper.AuditLogMapper;
import com.bluewhale.common.audit.support.SensitiveDataMasker;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.result.Result;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.context.expression.MethodBasedEvaluationContext;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.Ordered;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.http.HttpHeaders;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

/**
 * {@link AuditLog} 切面：采集审计上下文并异步落库。
 *
 * <p>执行链路：计时 → 执行目标方法（成功置 {@code biz_status=1}，异常置 0 并原样抛出）→
 * 组装 {@link AuditLogRecord}（网关注入头取用户与 traceId，参数经脱敏）→ 落库。</p>
 *
 * <p>落库时机与隔离性：</p>
 * <ol>
 *   <li>存在活动事务（{@code TransactionSynchronizationManager.isSynchronizationActive()}）时注册
 *       {@code afterCommit} 回调，确保「审计记录不会指向未提交的数据」；</li>
 *   <li>无事务时直接提交；</li>
 *   <li>两种方式都走独立线程池，业务线程零阻塞；审计异常/线程池拒绝只告警，不影响主流程。</li>
 * </ol>
 *
 * <p>用户上下文来源为网关 {@code JwtAuthGlobalFilter} 注入的 {@code X-User-Id} / {@code X-Username}
 * 与 {@code TraceIdGlobalFilter} 注入的 {@code X-Trace-Id}；本模块不依赖 spring-security，
 * 未取到身份信息时相应字段留空。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Aspect
@Order(Ordered.LOWEST_PRECEDENCE)
public class AuditLogAspect {

    /** 链路追踪头（与网关 {@code TraceIdGlobalFilter} 一致） */
    public static final String TRACE_ID_HEADER = "X-Trace-Id";
    /** 用户ID头（与网关 {@code JwtAuthGlobalFilter} 一致） */
    public static final String USER_ID_HEADER = "X-User-Id";
    /** 用户名头（与网关 {@code JwtAuthGlobalFilter} 一致） */
    public static final String USERNAME_HEADER = "X-Username";

    /** 代理转发IP头 */
    private static final String FORWARDED_FOR_HEADER = "X-Forwarded-For";
    /** 反向代理真实IP头 */
    private static final String REAL_IP_HEADER = "X-Real-IP";
    /** 业务成功标记 */
    private static final int BIZ_STATUS_SUCCESS = 1;
    /** 业务失败标记 */
    private static final int BIZ_STATUS_FAILURE = 0;
    /** 非业务异常（如系统异常）默认响应码 */
    private static final int DEFAULT_ERROR_CODE = 500;
    /** 异常消息落库长度上限（表字段 VARCHAR(512)） */
    private static final int MAX_ERROR_MSG_LENGTH = 512;
    /** target_id 落库长度上限（表字段 VARCHAR(64)） */
    private static final int MAX_TARGET_ID_LENGTH = 64;
    /** 纳秒转毫秒的除数 */
    private static final long NANOS_PER_MILLIS = 1_000_000L;

    private final AuditLogMapper auditLogMapper;
    private final SensitiveDataMasker sensitiveDataMasker;
    private final Executor auditLogExecutor;
    private final Environment environment;

    /** SpEL 解析器（线程安全，复用单例） */
    private final ExpressionParser expressionParser = new SpelExpressionParser();

    /** 方法参数名发现器，支撑 {@code #request.userId} 形式的 SpEL */
    private final ParameterNameDiscoverer parameterNameDiscoverer = new DefaultParameterNameDiscoverer();

    public AuditLogAspect(AuditLogMapper auditLogMapper,
                          SensitiveDataMasker sensitiveDataMasker,
                          Executor auditLogExecutor,
                          Environment environment) {
        this.auditLogMapper = auditLogMapper;
        this.sensitiveDataMasker = sensitiveDataMasker;
        this.auditLogExecutor = auditLogExecutor;
        this.environment = environment;
    }

    @Around("@annotation(auditLog)")
    public Object around(ProceedingJoinPoint joinPoint, AuditLog auditLog) throws Throwable {
        long startNanos = System.nanoTime();
        Object result = null;
        Throwable failure = null;
        try {
            result = joinPoint.proceed();
            return result;
        } catch (Throwable throwable) {
            failure = throwable;
            throw throwable;
        } finally {
            long costMillis = (System.nanoTime() - startNanos) / NANOS_PER_MILLIS;
            publish(joinPoint, auditLog, result, failure, costMillis);
        }
    }

    /**
     * 组装并投递审计记录；任何异常只告警，绝不冒泡影响业务主流程
     */
    private void publish(ProceedingJoinPoint joinPoint, AuditLog auditLog,
                         Object result, Throwable failure, long costMillis) {
        try {
            AuditLogRecord record = buildRecord(joinPoint, auditLog, result, failure, costMillis);
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        persist(record);
                    }
                });
            } else {
                persist(record);
            }
        } catch (Exception e) {
            log.warn("审计日志组装失败，不影响业务主流程: operation={}", auditLog.operation(), e);
        }
    }

    /**
     * 提交线程池落库
     */
    private void persist(AuditLogRecord record) {
        try {
            auditLogExecutor.execute(() -> {
                try {
                    auditLogMapper.insert(record);
                } catch (Exception e) {
                    log.warn("审计日志落库失败，不影响业务主流程: operation={}", record.getOperation(), e);
                }
            });
        } catch (RejectedExecutionException e) {
            log.warn("审计日志线程池已满，本次记录丢弃: operation={}", record.getOperation(), e);
        }
    }

    private AuditLogRecord buildRecord(ProceedingJoinPoint joinPoint, AuditLog auditLog,
                                      Object result, Throwable failure, long costMillis) {
        HttpServletRequest request = currentRequest();
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();

        AuditLogRecord record = new AuditLogRecord();
        record.setTraceId(resolveTraceId(request));
        record.setUserId(resolveUserId(request));
        record.setUsername(headerOrNull(request, USERNAME_HEADER));
        record.setModule(resolveModule(auditLog));
        record.setOperation(auditLog.operation());
        record.setOperateType(auditLog.type().name());
        record.setMethod(signature.getDeclaringTypeName() + "#" + signature.getName());
        record.setRequestUri(request == null ? null : request.getRequestURI());
        record.setHttpMethod(request == null ? null : request.getMethod());
        record.setTargetType(StringUtils.hasText(auditLog.targetType()) ? auditLog.targetType() : null);
        record.setTargetId(resolveTargetId(joinPoint, auditLog));
        record.setRequestParams(auditLog.recordParams() ? sensitiveDataMasker.maskArguments(joinPoint.getArgs()) : null);
        record.setResponseCode(resolveResponseCode(result, failure));
        record.setBizStatus(failure == null ? BIZ_STATUS_SUCCESS : BIZ_STATUS_FAILURE);
        record.setErrorMsg(resolveErrorMsg(failure));
        record.setIp(resolveIp(request));
        record.setUserAgent(headerOrNull(request, HttpHeaders.USER_AGENT));
        record.setCostMs(costMillis);
        record.setCreateTime(LocalDateTime.now());
        return record;
    }

    /** 模块名：注解优先，其次 {@code spring.application.name} */
    private String resolveModule(AuditLog auditLog) {
        if (StringUtils.hasText(auditLog.module())) {
            return auditLog.module();
        }
        return environment.getProperty("spring.application.name");
    }

    /** traceId：请求头优先，缺失则按网关同规格生成 32 位无横线 UUID */
    private static String resolveTraceId(HttpServletRequest request) {
        String traceId = headerOrNull(request, TRACE_ID_HEADER);
        return traceId != null ? traceId : UUID.randomUUID().toString().replace("-", "");
    }

    /** 用户ID：取网关注入头，非数字视为未提供 */
    private static Long resolveUserId(HttpServletRequest request) {
        String userId = headerOrNull(request, USER_ID_HEADER);
        if (userId == null) {
            return null;
        }
        try {
            return Long.valueOf(userId);
        } catch (NumberFormatException e) {
            log.debug("网关注入的 X-User-Id 非法，按未提供处理: value={}", userId);
            return null;
        }
    }

    /** IP：{@code X-Forwarded-For} 首段 → {@code X-Real-IP} → 远程地址 */
    private static String resolveIp(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String forwarded = headerOrNull(request, FORWARDED_FOR_HEADER);
        if (forwarded != null) {
            int commaIndex = forwarded.indexOf(',');
            return (commaIndex > 0 ? forwarded.substring(0, commaIndex) : forwarded).trim();
        }
        String realIp = headerOrNull(request, REAL_IP_HEADER);
        return realIp != null ? realIp : request.getRemoteAddr();
    }

    /** 响应码：业务异常码 → Result 体 code → 其他异常 500 → 无异常且非 Result 体则留空 */
    private static Integer resolveResponseCode(Object result, Throwable failure) {
        if (failure instanceof BizException bizException) {
            return bizException.getCode();
        }
        if (result instanceof Result<?> body) {
            return body.getCode();
        }
        return failure != null ? DEFAULT_ERROR_CODE : null;
    }

    /** 失败原因：异常类名 + 消息，按字段长度截断 */
    private static String resolveErrorMsg(Throwable failure) {
        if (failure == null) {
            return null;
        }
        String message = failure.getClass().getSimpleName()
                + (failure.getMessage() == null ? "" : ": " + failure.getMessage());
        return message.length() <= MAX_ERROR_MSG_LENGTH ? message : message.substring(0, MAX_ERROR_MSG_LENGTH);
    }

    /** targetId：SpEL 求值，求值失败或为空则留空（审计不可因表达式问题失败） */
    private String resolveTargetId(ProceedingJoinPoint joinPoint, AuditLog auditLog) {
        if (!StringUtils.hasText(auditLog.targetId())) {
            return null;
        }
        try {
            MethodSignature signature = (MethodSignature) joinPoint.getSignature();
            EvaluationContext context = new MethodBasedEvaluationContext(
                    joinPoint.getTarget(), signature.getMethod(), joinPoint.getArgs(), parameterNameDiscoverer);
            Object value = expressionParser.parseExpression(auditLog.targetId()).getValue(context);
            if (value == null) {
                return null;
            }
            String text = String.valueOf(value);
            if (text.isBlank()) {
                return null;
            }
            return text.length() <= MAX_TARGET_ID_LENGTH ? text : text.substring(0, MAX_TARGET_ID_LENGTH);
        } catch (Exception e) {
            log.warn("审计 targetId SpEL 求值失败，忽略该字段: expression={}", auditLog.targetId(), e);
            return null;
        }
    }

    private static HttpServletRequest currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return attributes.getRequest();
        }
        return null;
    }

    private static String headerOrNull(HttpServletRequest request, String headerName) {
        if (request == null) {
            return null;
        }
        String value = request.getHeader(headerName);
        return StringUtils.hasText(value) ? value : null;
    }
}
