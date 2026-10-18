package com.bluewhale.common.log.sink;

import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 日志落库支撑：统一「事务边界 + 异步投递 + 异常仅告警」策略（REQ-SEC-006）。
 *
 * <p>执行时机与隔离性（复用 {@code AuditLogAspect} 的三段机制）：</p>
 * <ol>
 *   <li>存在活动事务（{@code TransactionSynchronizationManager.isSynchronizationActive()}）时注册
 *       {@code afterCommit} 回调，确保「日志不会指向未提交的数据」；</li>
 *   <li>无事务时直接投递 {@link LogSink}；</li>
 *   <li>两种方式都走独立线程池，业务线程零阻塞；投递/落库异常只告警，不影响主流程。</li>
 * </ol>
 *
 * <p><b>约定</b>：落库任务为 {@link Runnable}，调用方须在提交前把所有上下文
 * （traceId / userId / 请求体等）捕获为普通对象——线程池/回调线程不可访问
 * {@code RequestContextHolder} 绑定的请求。</p>
 *
 * @author BlueWhale
 */
@Slf4j
public class LogPersistSupport {

    private final LogSink logSink;

    /**
     * 构造落库支撑
     *
     * @param logSink 落库 sink（阶段一为 {@link LocalLogSink}）
     */
    public LogPersistSupport(LogSink logSink) {
        this.logSink = logSink;
    }

    /**
     * 提交落库任务：事务活跃则注册 {@code afterCommit}，否则直接投递
     *
     * @param persistTask 落库任务（须自行捕获落库异常）
     * @param channel 通道标识（api / outbound / frontend / behavior）
     */
    public void submit(Runnable persistTask, String channel) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    safeSubmit(persistTask, channel);
                }
            });
        } else {
            safeSubmit(persistTask, channel);
        }
    }

    /**
     * 投递到 sink，任何异常仅告警，绝不冒泡影响业务主流程
     *
     * @param persistTask 落库任务
     * @param channel 通道标识
     */
    private void safeSubmit(Runnable persistTask, String channel) {
        try {
            logSink.submit(persistTask, channel);
        } catch (Exception e) {
            log.warn("日志落库投递失败，不影响业务主流程: channel={}", channel, e);
        }
    }
}
