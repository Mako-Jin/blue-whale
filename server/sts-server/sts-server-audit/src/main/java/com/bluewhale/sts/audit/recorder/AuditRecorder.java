package com.bluewhale.sts.audit.recorder;

import com.bluewhale.sts.audit.auditlog.AuditLogEntity;
import com.bluewhale.sts.audit.auditlog.AuditLogMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 审计落库。
 *
 * <p><b>{@code REQUIRES_NEW} 不是可有可无的优化，它决定审计在失败场景下还能不能留下来。</b>
 * 业务请求失败时，业务事务会回滚。若审计写在这个事务里（默认的 {@code REQUIRED} 会加入它），
 * 那么**失败的审计记录会随业务一起回滚掉** —— 而不巧的是，"操作被拒绝"恰恰是
 * 审计里最需要看清的那一类。开一个独立事务后，业务怎么回滚都不影响这条记录。
 *
 * <p><b>调用方必须自己吞掉异常。</b>本方法不加 {@code try/catch} 是刻意的：
 * 在同一个类里自己调自己（self-invocation）不经过 Spring 的事务代理，
 * {@code @Transactional} 会**静默失效** —— 这是一个只在"审计写失败"时才暴露的坑，
 * 而那时你正在查审计为什么没有记录。所以异常处理放在调用方（{@link AuditInterceptor}），
 * 事务边界留在代理上。
 */
@Service
public class AuditRecorder {

    private static final Logger log = LoggerFactory.getLogger(AuditRecorder.class);

    private final AuditLogMapper auditLogMapper;

    public AuditRecorder(AuditLogMapper auditLogMapper) {
        this.auditLogMapper = auditLogMapper;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(AuditRecord record) {
        AuditLogEntity entity = new AuditLogEntity();
        entity.setAction(record.action());
        entity.setResource(record.resource());
        entity.setTargetId(record.targetId());
        entity.setOperatorId(record.operatorId());
        entity.setOperatorName(record.operatorName());
        entity.setTenantId(record.tenantId());
        entity.setResult(record.result().wireName());
        entity.setErrorCode(record.errorCode());
        entity.setErrorMessage(record.errorMessage());
        entity.setHttpMethod(record.httpMethod());
        entity.setRequestUri(record.requestUri());
        entity.setIp(record.ip());
        entity.setUserAgent(record.userAgent());
        entity.setTraceId(record.traceId());
        entity.setDurationMs(record.durationMs());
        entity.setServerTime(System.currentTimeMillis());
        auditLogMapper.insertRecord(entity);
    }

    /**
     * 写失败时的唯一动作：记一条 WARN，然后什么也不做。
     *
     * <p>供调用方在 {@code catch} 里使用。单独开一个方法而不是顺手写在
     * {@code record} 的 catch 里，是为了让"审计失败不允许影响业务"这条约定
     * 在调用点一眼可见 —— 见 {@link AuditInterceptor} 的注释。
     */
    public void reportFailure(AuditRecord record, Exception cause) {
        log.warn("审计记录写入失败，已忽略（不影响业务响应） | action={} | operatorId={} | message={}",
                record.action(), record.operatorId(), cause.getMessage());
    }

}
