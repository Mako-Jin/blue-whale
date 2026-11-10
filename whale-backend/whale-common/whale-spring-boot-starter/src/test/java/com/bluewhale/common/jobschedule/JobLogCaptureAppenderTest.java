package com.bluewhale.common.jobschedule;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.MDC;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link JobLogCaptureAppender} 采集口径测试。
 *
 * <p>两条核心语义：带执行批次ID的日志（含异常堆栈）必须进快照；
 * 没有批次ID的普通请求日志一律不进——这正是它可以挂 root logger 而不污染的原因。</p>
 *
 * @author BlueWhale
 */
class JobLogCaptureAppenderTest {

    private static final String CODE = "crm.order.reconcile";

    private static final String RUN_ID = "run-abcdef123456";

    @TempDir
    Path tempDir;

    @AfterEach
    void clearMdc() {
        MDC.remove(JobExecutionLogStore.MDC_RUN_ID);
    }

    @Test
    @DisplayName("带执行批次ID的日志与异常堆栈都会被采集")
    void shouldCaptureLogAndStack() {
        JobExecutionLogStore store = store();
        JobLogCaptureAppender appender = appender(store);
        store.begin(RUN_ID, CODE);
        MDC.put(JobExecutionLogStore.MDC_RUN_ID, RUN_ID);

        appender.doAppend(event(Level.ERROR, "回写失败: 连接超时",
                new IllegalStateException("boom")));

        store.finish(RUN_ID, CODE, "失败");
        String content = store.readLatest(CODE);
        assertTrue(content.contains("ERROR"), "应带级别：" + content);
        assertTrue(content.contains("回写失败: 连接超时"), "应带消息：" + content);
        assertTrue(content.contains("IllegalStateException"), "应带异常类型：" + content);
        assertTrue(content.contains("boom"), "应带异常消息：" + content);
        assertTrue(content.contains("JobLogCaptureAppenderTest"), "应带堆栈帧：" + content);
    }

    @Test
    @DisplayName("没有执行批次ID时（普通请求日志）一律不采集")
    void shouldIgnoreLogWithoutRunId() {
        JobExecutionLogStore store = store();
        JobLogCaptureAppender appender = appender(store);

        appender.doAppend(event(Level.INFO, "普通接口请求", null));

        assertNull(store.readLatest(CODE), "无批次ID的日志不应落盘");
    }

    /**
     * 构造已启动的 appender
     *
     * @param store 采集器
     * @return appender
     */
    private JobLogCaptureAppender appender(JobExecutionLogStore store) {
        JobLogCaptureAppender appender = new JobLogCaptureAppender(store);
        appender.start();
        return appender;
    }

    /**
     * 构造采集器，日志目录指向临时目录
     *
     * @return 采集器
     */
    private JobExecutionLogStore store() {
        JobScheduleProperties.Executor config = new JobScheduleProperties.Executor();
        config.setLogDir(tempDir.toString());
        return new JobExecutionLogStore(config);
    }

    /**
     * 构造一条日志事件
     *
     * @param level     级别
     * @param message   消息
     * @param throwable 异常（可为 {@code null}）
     * @return 日志事件
     */
    private static ILoggingEvent event(Level level, String message, Throwable throwable) {
        ILoggingEvent event = mock(ILoggingEvent.class);
        when(event.getTimeStamp()).thenReturn(System.currentTimeMillis());
        when(event.getLevel()).thenReturn(level);
        when(event.getThreadName()).thenReturn("http-nio-9007-exec-1");
        when(event.getLoggerName()).thenReturn("com.bluewhale.crm.CrmOrderSyncJob");
        when(event.getFormattedMessage()).thenReturn(message);
        when(event.getThrowableProxy()).thenReturn(throwable == null ? null : new ThrowableProxy(throwable));
        return event;
    }
}
