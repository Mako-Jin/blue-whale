package com.bluewhale.message.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 回执受理与回调派发配置。
 *
 * @author BlueWhale
 */
@Data
@ConfigurationProperties(prefix = "whale.message.receipt")
public class MessageReceiptProperties {

    /** 回执受理开关（关闭时上报端点直接拒绝，避免无回执能力时静默吞数据） */
    private boolean enabled = true;

    /** 回调派发开关 */
    private boolean callbackEnabled = true;

    /** 回调派发间隔（毫秒） */
    private long dispatchIntervalMillis = 15000;

    /** 单次派发批量上限 */
    private int dispatchBatchSize = 50;

    /** 回调最大尝试次数，超过后置为 GAVE_UP */
    private int maxAttempts = 5;

    /** 失败重试退避基数（秒），按 2^(尝试次数-1) 递增 */
    private long retryBackoffSeconds = 30;

    /** 回调连接超时（毫秒） */
    private int connectTimeoutMillis = 1000;

    /** 回调读取超时（毫秒） */
    private int readTimeoutMillis = 3000;
}
