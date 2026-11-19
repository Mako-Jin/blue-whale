package com.bluewhale.message.channel;

import com.bluewhale.message.config.MessageChannelProperties;
import com.bluewhale.message.enums.DeliveryStatus;
import com.bluewhale.message.enums.MessageChannel;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 渠道级限流与熔断保护（轻量自研实现，不引入额外依赖）。
 *
 * <p>两道闸门按"先熔断、后限流"顺序判定：</p>
 * <ol>
 *   <li><b>熔断</b>：同一渠道连续失败达到阈值后进入打开状态，在恢复窗口内直接跳过投递，
 *       避免故障渠道拖垮整个发送链路；窗口结束后自动半开放行，由首次调用结果决定是否再次打开。</li>
 *   <li><b>限流</b>：按渠道配置的每秒令牌数做匀速放行，超出的投递记为 SKIPPED，
 *       由业务方按幂等键重试，不会丢失消息。</li>
 * </ol>
 *
 * <p>状态按渠道维护在进程内，单实例生效；多实例部署时各实例独立限流，
 * 总量上限为"单实例上限 × 实例数"。</p>
 *
 * @author BlueWhale
 */
@Component
public class ChannelGuard {

    /** 每秒允许发送的令牌桶 */
    private final Map<MessageChannel, TokenBucket> buckets = new ConcurrentHashMap<>();

    /** 渠道熔断状态 */
    private final Map<MessageChannel, Circuit> circuits = new ConcurrentHashMap<>();

    /**
     * 获取投递许可。
     *
     * @param channel  渠道
     * @param settings 渠道保护配置
     * @return 判定结果：不允许时携带跳过原因
     */
    public Decision acquire(MessageChannel channel, MessageChannelProperties.Guard settings) {
        if (settings == null) {
            return Decision.allow();
        }
        Circuit circuit = circuits.computeIfAbsent(channel, key -> new Circuit());
        if (circuit.isOpen()) {
            return Decision.reject("渠道熔断中: " + channel.name());
        }
        TokenBucket bucket = buckets.computeIfAbsent(channel, key -> new TokenBucket());
        if (!bucket.tryAcquire(settings.getRateLimitPerSecond())) {
            return Decision.reject("渠道限流: " + channel.name());
        }
        return Decision.allow();
    }

    /**
     * 记录一次真实调用结果，用于维护熔断计数。
     *
     * <p>只有 {@code FAILED} 计为失败；{@code SENT} 重置计数；{@code MOCKED}/{@code SKIPPED}
     * 既非真实调用也非渠道故障，不改变熔断状态。</p>
     *
     * @param channel  渠道
     * @param settings 渠道保护配置
     * @param status   投递状态
     */
    public void record(MessageChannel channel, MessageChannelProperties.Guard settings, DeliveryStatus status) {
        if (settings == null || status == null) {
            return;
        }
        Circuit circuit = circuits.computeIfAbsent(channel, key -> new Circuit());
        if (status == DeliveryStatus.FAILED) {
            circuit.onFailure(settings.getFailureThreshold(), settings.getOpenMillis());
        } else if (status == DeliveryStatus.SENT) {
            circuit.onSuccess();
        }
    }

    /**
     * 投递许可判定结果。
     *
     * @param allowed 是否放行
     * @param reason  拒绝原因
     */
    public record Decision(boolean allowed, String reason) {

        /**
         * 允许投递。
         *
         * @return 放行结果
         */
        public static Decision allow() {
            return new Decision(true, null);
        }

        /**
         * 拒绝投递。
         *
         * @param reason 拒绝原因
         * @return 拒绝结果
         */
        public static Decision reject(String reason) {
            return new Decision(false, reason);
        }
    }

    /**
     * 令牌桶：容量与补充速率均为每秒上限，允许不超过一秒的突发。
     */
    private static final class TokenBucket {

        /** 剩余令牌，负数表示尚未初始化 */
        private double tokens = -1;

        /** 上次补充时间（纳秒） */
        private long lastNanos = System.nanoTime();

        /**
         * 尝试获取一个令牌。
         *
         * @param ratePerSecond 每秒令牌数，非正数表示不限流
         * @return 获取成功返回 {@code true}
         */
        synchronized boolean tryAcquire(int ratePerSecond) {
            if (ratePerSecond <= 0) {
                return true;
            }
            long now = System.nanoTime();
            if (tokens < 0) {
                tokens = ratePerSecond;
                lastNanos = now;
            }
            double elapsedSeconds = (now - lastNanos) / 1_000_000_000.0;
            if (elapsedSeconds > 0) {
                tokens = Math.min(ratePerSecond, tokens + elapsedSeconds * ratePerSecond);
                lastNanos = now;
            }
            if (tokens < 1.0) {
                return false;
            }
            tokens -= 1.0;
            return true;
        }
    }

    /**
     * 熔断器：连续失败达阈值后打开，窗口结束后自动半开。
     */
    private static final class Circuit {

        /** 连续失败次数 */
        private int consecutiveFailures;

        /** 打开截止时间（纳秒），0 表示闭合 */
        private long openUntilNanos;

        /**
         * 是否处于打开（熔断）状态。
         *
         * @return 打开返回 {@code true}
         */
        synchronized boolean isOpen() {
            return openUntilNanos > 0 && System.nanoTime() < openUntilNanos;
        }

        /**
         * 调用成功：闭合并清零计数。
         */
        synchronized void onSuccess() {
            consecutiveFailures = 0;
            openUntilNanos = 0;
        }

        /**
         * 调用失败：累计计数，达到阈值后打开熔断窗口。
         *
         * @param threshold  失败阈值，非正数表示不熔断
         * @param openMillis 熔断窗口（毫秒）
         */
        synchronized void onFailure(int threshold, long openMillis) {
            if (threshold <= 0) {
                return;
            }
            consecutiveFailures++;
            if (consecutiveFailures >= threshold) {
                openUntilNanos = System.nanoTime() + Math.max(1L, openMillis) * 1_000_000L;
                consecutiveFailures = 0;
            }
        }
    }
}
