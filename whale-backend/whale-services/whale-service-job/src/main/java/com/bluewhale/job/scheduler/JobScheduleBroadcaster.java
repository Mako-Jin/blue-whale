package com.bluewhale.job.scheduler;

import com.bluewhale.job.metrics.JobMetricNames;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RTopic;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * 调度失效广播：多实例部署下，任一端修改任务定义/启停/依赖后通知其余实例重新注册调度。
 *
 * <p>基于 Redisson {@link RTopic}（Pub/Sub）实现，消息体为 {@code 实例标识|动作|任务ID}
 * 的纯文本，避免跨版本序列化兼容问题。发布方先完成本地调度变更再广播；接收方按动作
 * 调用本地 {@code reschedule}/{@code cancel}，并通过实例标识跳过自身回声消息。</p>
 *
 * <p>降级策略：Redisson 不可用、主题订阅或发布异常时只记录告警，调度仍按本地单实例语义运行，
 * 不阻断任务 CRUD 主流程（与执行链路「Redis 不可用降级为实例内互斥」口径一致）。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Component
public class JobScheduleBroadcaster {

    /** 失效广播主题 */
    static final String TOPIC_NAME = "job:schedule:invalidate";

    /** 消息分隔符 */
    private static final String DELIMITER = "|";

    /** 动作：重新注册调度 */
    static final String ACTION_RESCHEDULE = "RESCHEDULE";

    /** 动作：取消调度 */
    static final String ACTION_CANCEL = "CANCEL";

    /** 消息段数（实例标识 + 动作 + 任务ID） */
    private static final int SEGMENT_COUNT = 3;

    /** 当前实例标识：用于跳过自身发布的消息 */
    private final String instanceId = UUID.randomUUID().toString().replace("-", "");

    private final ObjectProvider<RedissonClient> redissonClientProvider;

    private final MeterRegistry meterRegistry;

    /** 本地调度变更回调（由 {@link JobScheduler} 注册） */
    private volatile ScheduleEventConsumer consumer;

    /** 已订阅主题：为空表示广播能力不可用 */
    private RTopic topic;

    /** 订阅标识：用于关闭时移除监听 */
    private int listenerId;

    /**
     * 构造失效广播器
     *
     * @param redissonClientProvider Redisson 客户端提供者（可为空，为空时降级）
     * @param meterRegistry 指标注册表
     */
    public JobScheduleBroadcaster(ObjectProvider<RedissonClient> redissonClientProvider, MeterRegistry meterRegistry) {
        this.redissonClientProvider = redissonClientProvider;
        this.meterRegistry = meterRegistry;
    }

    /**
     * 订阅失效广播主题；Redisson 不可用时仅记录告警，保留本地单实例语义
     */
    @PostConstruct
    public void init() {
        RedissonClient client = redissonClientProvider.getIfAvailable();
        if (client == null) {
            recordFallback();
            log.warn("Redisson 不可用，调度失效广播降级为单实例语义: topic={}", TOPIC_NAME);
            return;
        }
        try {
            RTopic clientTopic = client.getTopic(TOPIC_NAME);
            listenerId = clientTopic.addListener(String.class, (channel, message) -> handleMessage(message));
            this.topic = clientTopic;
            log.info("调度失效广播已订阅: topic={}, instanceId={}", TOPIC_NAME, instanceId);
        } catch (Exception e) {
            recordFallback();
            log.warn("调度失效广播订阅失败，降级为单实例语义: topic={}", TOPIC_NAME, e);
        }
    }

    /**
     * 记录一次广播降级
     */
    private void recordFallback() {
        meterRegistry.counter(JobMetricNames.BROADCAST_FALLBACK_TOTAL).increment();
    }

    /**
     * 取消订阅
     */
    @PreDestroy
    public void destroy() {
        if (topic == null) {
            return;
        }
        try {
            topic.removeListener(listenerId);
        } catch (Exception e) {
            log.warn("调度失效广播取消订阅失败: topic={}", TOPIC_NAME, e);
        }
    }

    /**
     * 注册本地调度变更回调
     *
     * @param consumer 调度变更消费者
     */
    public void registerConsumer(ScheduleEventConsumer consumer) {
        this.consumer = consumer;
    }

    /**
     * 通知其他实例重新注册调度
     *
     * @param jobId 任务ID
     */
    public void broadcastReschedule(Long jobId) {
        publish(ACTION_RESCHEDULE, jobId);
    }

    /**
     * 通知其他实例取消调度
     *
     * @param jobId 任务ID
     */
    public void broadcastCancel(Long jobId) {
        publish(ACTION_CANCEL, jobId);
    }

    /**
     * 发布失效消息；广播异常只记录告警，不影响已完成的本地变更
     *
     * @param action 动作
     * @param jobId 任务ID
     */
    private void publish(String action, Long jobId) {
        if (topic == null || jobId == null) {
            recordFallback();
            return;
        }
        String message = instanceId + DELIMITER + action + DELIMITER + jobId;
        try {
            topic.publish(message);
            recordBroadcast(action, JobMetricNames.RESULT_SUCCESS);
        } catch (Exception e) {
            recordBroadcast(action, JobMetricNames.RESULT_FAILURE);
            log.warn("调度失效广播发布失败: topic={}, jobId={}, action={}", TOPIC_NAME, jobId, action, e);
        }
    }

    /**
     * 记录一次广播发布结果
     *
     * @param action 动作
     * @param result 结果
     */
    private void recordBroadcast(String action, String result) {
        meterRegistry.counter(JobMetricNames.BROADCAST_TOTAL,
                        JobMetricNames.TAG_ACTION, action,
                        JobMetricNames.TAG_RESULT, result)
                .increment();
    }

    /**
     * 处理接收到的失效消息：跳过自身回声与非法消息，其余转交本地调度变更回调
     *
     * @param message 消息体
     */
    void handleMessage(String message) {
        if (message == null) {
            return;
        }
        String[] segments = message.split("\\|");
        if (segments.length != SEGMENT_COUNT) {
            log.warn("调度失效广播消息格式非法，已忽略: message={}", message);
            return;
        }
        if (instanceId.equals(segments[0])) {
            return;
        }
        Long jobId = parseJobId(segments[2]);
        if (jobId == null) {
            return;
        }
        ScheduleEventConsumer target = consumer;
        if (target == null) {
            log.debug("调度失效广播消费者未就绪，忽略消息: message={}", message);
            return;
        }
        log.info("收到调度失效广播: jobId={}, action={}, origin={}", jobId, segments[1], segments[0]);
        target.accept(jobId, ACTION_RESCHEDULE.equals(segments[1]));
        meterRegistry.counter(JobMetricNames.BROADCAST_RECEIVE_TOTAL,
                JobMetricNames.TAG_RESULT, JobMetricNames.RESULT_APPLIED).increment();
    }

    /**
     * 解析任务ID
     *
     * @param text 文本任务ID
     * @return 解析成功返回任务ID；非法返回 {@code null}
     */
    private static Long parseJobId(String text) {
        try {
            return Long.valueOf(text);
        } catch (NumberFormatException e) {
            log.warn("调度失效广播任务ID非法，已忽略: jobId={}", text);
            return null;
        }
    }

    /**
     * 调度变更消费者：{@code reschedule=true} 表示重新注册，否则取消
     */
    @FunctionalInterface
    public interface ScheduleEventConsumer {

        /**
         * 应用本地调度变更
         *
         * @param jobId 任务ID
         * @param reschedule 是否重新注册
         */
        void accept(Long jobId, boolean reschedule);
    }
}
