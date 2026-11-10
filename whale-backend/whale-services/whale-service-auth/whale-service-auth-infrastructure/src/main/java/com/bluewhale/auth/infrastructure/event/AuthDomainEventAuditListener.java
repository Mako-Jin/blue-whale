package com.bluewhale.auth.infrastructure.event;

import com.bluewhale.auth.domain.event.AuthDomainEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 领域事件审计监听器（AUTH-17 / T-DDD-11）。
 *
 * <p>审计的权威来源仍是 Controller 上的 {@code @AuditLog}（含操作人、请求上下文与落库明细）；
 * 本监听器只把领域事件以固定格式写入名为 {@code com.bluewhale.auth.audit.event} 的日志，
 * 提供一条可供日志采集与告警消费的事件流，不改变既有审计语义。</p>
 *
 * @author BlueWhale
 */
@Component
public class AuthDomainEventAuditListener {

    /** 审计事件流日志（独立 logger 名便于采集侧过滤） */
    private static final Logger AUDIT_LOG = LoggerFactory.getLogger("com.bluewhale.auth.audit.event");

    /**
     * 监听全部认证权限域领域事件并输出结构化日志
     *
     * @param event 领域事件
     */
    @EventListener
    public void onAuthDomainEvent(AuthDomainEvent event) {
        AUDIT_LOG.info("auth-domain-event aggregate={} aggregateId={} change={}",
                event.getClass().getSimpleName(), event.aggregateId(), event.changeType());
    }
}
