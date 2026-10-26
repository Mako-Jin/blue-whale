package com.bluewhale.auth.infrastructure.event;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.bluewhale.auth.domain.event.AuthDomainEvent.ChangeType;
import com.bluewhale.auth.domain.event.RoleDomainEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link AuthDomainEventAuditListener} 单元测试：审计事件流日志内容（AUTH-17 / T-DDD-11）。
 *
 * @author BlueWhale
 */
class AuthDomainEventAuditListenerTest {

    private static final String AUDIT_LOGGER_NAME = "com.bluewhale.auth.audit.event";

    private final AuthDomainEventAuditListener listener = new AuthDomainEventAuditListener();

    private Logger auditLogger;

    private ListAppender<ILoggingEvent> appender;

    /**
     * 为审计 logger 挂载内存 appender
     */
    @BeforeEach
    void setUp() {
        auditLogger = (Logger) LoggerFactory.getLogger(AUDIT_LOGGER_NAME);
        appender = new ListAppender<>();
        appender.start();
        auditLogger.addAppender(appender);
    }

    /**
     * 卸载 appender，避免测试间串扰
     */
    @AfterEach
    void tearDown() {
        auditLogger.detachAppender(appender);
    }

    @Test
    @DisplayName("监听领域事件：日志包含事件类型、聚合 ID 与变更类型")
    void shouldLogAggregateAndChangeType() {
        listener.onAuthDomainEvent(new RoleDomainEvent(7L, ChangeType.DELETED));

        List<ILoggingEvent> events = appender.list;

        assertTrue(events.size() == 1);
        String message = events.get(0).getFormattedMessage();
        assertTrue(message.contains("RoleDomainEvent"));
        assertTrue(message.contains("aggregateId=7"));
        assertTrue(message.contains("change=DELETED"));
    }

    @Test
    @DisplayName("监听领域事件：使用独立审计 logger 名")
    void shouldUseDedicatedAuditLogger() {
        listener.onAuthDomainEvent(new RoleDomainEvent(8L, ChangeType.UPDATED));

        assertTrue(appender.list.get(0).getLoggerName().equals(AUDIT_LOGGER_NAME));
    }
}
