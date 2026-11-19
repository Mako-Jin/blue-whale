package com.bluewhale.message.support;

import com.bluewhale.message.enums.DeliveryStatus;
import com.bluewhale.message.enums.MessageChannel;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * 消息域关键指标（Micrometer）。
 *
 * <p>指标命名与标签口径：</p>
 * <ul>
 *   <li>{@code message_send_total{channel,status}}：按渠道与投递结果计数，含被限流/熔断跳过的条目；</li>
 *   <li>{@code message_send_duration{channel}}：真实渠道调用耗时，不含限流熔断拦截；</li>
 *   <li>{@code message_receipt_total{channel,status,match}}：回执受理数，match 区分是否命中投递。</li>
 * </ul>
 *
 * @author BlueWhale
 */
@Component
@RequiredArgsConstructor
public class MessageMetrics {

    /** 发送计数 */
    private static final String SEND_TOTAL = "message_send_total";

    /** 发送耗时 */
    private static final String SEND_DURATION = "message_send_duration";

    /** 回执计数 */
    private static final String RECEIPT_TOTAL = "message_receipt_total";

    private final MeterRegistry registry;

    /**
     * 记录一次发送结果。
     *
     * @param channel       渠道
     * @param status        投递状态
     * @param durationNanos 渠道调用耗时（纳秒），非正数表示未发起真实调用
     */
    public void recordSend(MessageChannel channel, DeliveryStatus status, long durationNanos) {
        registry.counter(SEND_TOTAL, "channel", channel.name(), "status", status.name()).increment();
        if (durationNanos > 0) {
            registry.timer(SEND_DURATION, "channel", channel.name())
                    .record(durationNanos, TimeUnit.NANOSECONDS);
        }
    }

    /**
     * 记录一次回执受理（不含幂等重复）。
     *
     * @param channel 渠道
     * @param status  回执状态
     * @param matched 是否匹配到投递记录
     */
    public void recordReceipt(String channel, DeliveryStatus status, boolean matched) {
        registry.counter(RECEIPT_TOTAL,
                        "channel", channel,
                        "status", status.name(),
                        "match", matched ? "MATCHED" : "UNMATCHED")
                .increment();
    }
}
