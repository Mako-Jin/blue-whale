package com.bluewhale.common.log.sink;

/**
 * 日志落库 sink 抽象（REQ-SEC-006）。
 *
 * <p>阶段一仅实现 {@link LocalLogSink}（本地异步直写）。阶段二预留 MQ 汇聚实现，
 * 由配置 {@code whale.audit.sink=local|mq} 选择；MQ 实现未就绪时回落 local
 * （见 {@code WhaleLogAutoConfiguration}）。</p>
 *
 * @author BlueWhale
 */
public interface LogSink {

    /**
     * 提交落库任务
     *
     * @param persistTask 落库任务（内部自行捕获异常并仅告警）
     * @param channel 通道标识（api / outbound / frontend / behavior），用于告警定位
     */
    void submit(Runnable persistTask, String channel);
}
