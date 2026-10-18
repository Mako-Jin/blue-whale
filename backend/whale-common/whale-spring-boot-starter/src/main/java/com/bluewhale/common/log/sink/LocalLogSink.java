package com.bluewhale.common.log.sink;

import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

/**
 * 本地异步直写 sink（阶段一，REQ-SEC-006）。
 *
 * <p>把落库任务投递到 {@code whaleLogExecutor}（独立线程池），业务线程零阻塞；
 * 线程池拒绝时仅告警，不冒泡影响主流程（与 {@code AuditLogAspect} 策略一致）。</p>
 *
 * @author BlueWhale
 */
@Slf4j
public class LocalLogSink implements LogSink {

    private final Executor executor;

    /**
     * 构造本地 sink
     *
     * @param executor 日志落库线程池（{@code whaleLogExecutor}）
     */
    public LocalLogSink(Executor executor) {
        this.executor = executor;
    }

    @Override
    public void submit(Runnable persistTask, String channel) {
        try {
            executor.execute(persistTask);
        } catch (RejectedExecutionException e) {
            log.warn("日志线程池已满，本次记录丢弃: channel={}", channel, e);
        }
    }
}
