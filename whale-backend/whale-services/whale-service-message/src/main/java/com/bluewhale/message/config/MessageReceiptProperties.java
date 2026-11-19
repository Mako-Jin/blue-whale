package com.bluewhale.message.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 回执受理与回调派发配置。
 *
 * <p>启动即校验：派发间隔、批量、超时等参数配成 0 或负数会直接导致调度异常
 * （如 {@code Page(1, 0)}），因此在绑定阶段就拒绝并给出明确报错。</p>
 *
 * @author BlueWhale
 */
@Data
@Validated
@ConfigurationProperties(prefix = "whale.message.receipt")
public class MessageReceiptProperties {

    /** 回执受理开关（关闭时上报端点直接拒绝，避免无回执能力时静默吞数据） */
    private boolean enabled = true;

    /** 回调派发开关 */
    private boolean callbackEnabled = true;

    /** 回调派发间隔（毫秒） */
    @Min(1)
    private long dispatchIntervalMillis = 15000;

    /**
     * 回调派发首次执行延迟（毫秒）。
     *
     * <p>由 {@code MessageCallbackDispatcher} 的 {@code @Scheduled#initialDelayString} 占位符直接消费，
     * 这里声明同名字段是为了让配置类与 yml 保持同步并获得 IDE 提示。</p>
     */
    @Min(0)
    private long dispatchInitialDelayMillis = 20000;

    /** 单次派发批量上限 */
    @Min(1)
    @Max(500)
    private int dispatchBatchSize = 50;

    /** 派发抢占超时（毫秒）：超时未完成的任务会被回收重派，避免实例崩溃后任务永久卡在 DISPATCHING */
    @Min(1000)
    private long dispatchTimeoutMillis = 120000;

    /** 回调最大尝试次数，超过后置为 GAVE_UP */
    @Min(1)
    @Max(20)
    private int maxAttempts = 5;

    /** 失败重试退避基数（秒），按 2^(尝试次数-1) 递增 */
    @Min(1)
    private long retryBackoffSeconds = 30;

    /** 回调连接超时（毫秒） */
    @Min(1)
    private int connectTimeoutMillis = 1000;

    /** 回调读取超时（毫秒） */
    @Min(1)
    private int readTimeoutMillis = 3000;

    /** outbox 待派发积压告警阈值（健康检查超过即 DOWN） */
    @Min(1)
    private long pendingAlertThreshold = 1000L;
}
