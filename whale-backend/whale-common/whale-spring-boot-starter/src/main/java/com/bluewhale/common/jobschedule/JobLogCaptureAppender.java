package com.bluewhale.common.jobschedule;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import ch.qos.logback.core.AppenderBase;
import org.slf4j.MDC;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * 执行日志采集 appender：把「当前执行批次」线程内的日志追加到 {@link JobExecutionLogStore}。
 *
 * <p>为什么不按 logger 名字或包名过滤：任务代码分散在各业务包里，按包过滤必然漏；
 * 用 MDC 的 {@value JobExecutionLogStore#MDC_RUN_ID} 判定，只有调度中心触发的那一次调用
 * 会带上键，其余请求（含同一方法的本地手工调用）一律零成本跳过。</p>
 *
 * <p>由 {@code JobScheduleAutoConfiguration} 在开启内网触发端点时挂到 root logger，
 * 随上下文关闭而摘除。</p>
 *
 * @author BlueWhale
 */
public class JobLogCaptureAppender extends AppenderBase<ILoggingEvent> {

    /** 日志行时间格式：日期已在文件头，行内只保留时间 */
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    /** 级别列宽：保持行与行对齐，便于肉眼扫读 */
    private static final int LEVEL_WIDTH = 5;

    private final JobExecutionLogStore store;

    /**
     * 构造 appender。
     *
     * @param store 日志采集与落盘器
     */
    public JobLogCaptureAppender(JobExecutionLogStore store) {
        this.store = store;
    }

    /**
     * 挂到 root logger 并启动。
     *
     * <p>挂 root 而不是按包名挂：任务代码散落在各业务包，按包名配置必然漏。</p>
     *
     * @param context logback 上下文
     */
    public void attach(LoggerContext context) {
        setName("job-execution-log");
        setContext(context);
        start();
        context.getLogger(Logger.ROOT_LOGGER_NAME).addAppender(this);
    }

    /**
     * 从 root logger 摘除并停止（随 Spring 上下文关闭调用）。
     */
    public void detach() {
        if (getContext() instanceof LoggerContext context) {
            context.getLogger(Logger.ROOT_LOGGER_NAME).detachAppender(this);
        }
        stop();
    }

    @Override
    protected void append(ILoggingEvent event) {
        String runId = MDC.get(JobExecutionLogStore.MDC_RUN_ID);
        if (runId == null) {
            return;
        }
        StringBuilder line = new StringBuilder(160)
                .append(Instant.ofEpochMilli(event.getTimeStamp())
                        .atZone(ZoneId.systemDefault()).format(TIME_FORMAT))
                .append(' ')
                .append(padLevel(event.getLevel().toString()))
                .append(" [").append(event.getThreadName()).append("] ")
                .append(event.getLoggerName())
                .append(" - ")
                .append(event.getFormattedMessage())
                .append(System.lineSeparator());
        if (event.getThrowableProxy() != null) {
            line.append(ThrowableProxyUtil.asString(event.getThrowableProxy()))
                    .append(System.lineSeparator());
        }
        store.append(runId, line.toString());
    }

    /**
     * 级别右对齐到固定宽度。
     *
     * @param level 级别名
     * @return 定宽文本
     */
    private static String padLevel(String level) {
        if (level.length() >= LEVEL_WIDTH) {
            return level;
        }
        return " ".repeat(LEVEL_WIDTH - level.length()) + level;
    }
}
