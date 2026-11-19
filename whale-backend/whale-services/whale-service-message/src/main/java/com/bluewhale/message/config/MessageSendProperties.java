package com.bluewhale.message.config;

import jakarta.validation.constraints.Min;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 消息投递配置。
 *
 * <p>{@link #enabled} 为全局默认投递模式：关闭时按请求的 {@code async} 字段逐条决定，
 * 打开后所有请求默认走异步线程池。补偿任务按 {@link #recoverThresholdSeconds} 回收
 * 长时间停留在 {@code PENDING} 的消息，避免进程崩溃导致消息永久未投递。</p>
 *
 * @author BlueWhale
 */
@Data
@Validated
@ConfigurationProperties(prefix = "whale.message.send")
public class MessageSendProperties {

    /** 是否默认异步投递：false 时仅请求显式 async=true 的消息异步 */
    private boolean enabled;

    /** 异步投递线程数 */
    @Min(1)
    private int poolSize = 4;

    /** 异步投递队列容量 */
    @Min(0)
    private int queueCapacity = 1000;

    /** 补偿任务执行间隔（毫秒） */
    @Min(1000)
    private long recoverIntervalMillis = 60000L;

    /** 待投递消息超过该时长仍未完成时触发补偿（秒） */
    @Min(1)
    private int recoverThresholdSeconds = 120;
}
