package com.bluewhale.pay.client;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 支付客户端配置（前缀 {@code whale.pay.client}）。
 *
 * <p>单测与本地联调可直接指向 MOCK 渠道的支付服务；生产应配置按服务签发的内网令牌，
 * 并把 {@code tenant-id} 固定为调用方租户，避免运行期串租户。</p>
 *
 * @author BlueWhale
 */
@Data
@ConfigurationProperties(prefix = "whale.pay.client")
public class WhalePayClientProperties {

    /** 是否启用自动装配 */
    private boolean enabled = true;

    /** 支付服务基础地址，如 {@code http://whale-pay:8086} */
    private String baseUrl = "http://whale-pay:8086";

    /** 内网令牌；为空时取 starter 当前生效的令牌 */
    private String internalToken;

    /** 租户 ID；为空时取当前请求的租户上下文 */
    private Long tenantId;

    /** 连接超时 */
    private Duration connectTimeout = Duration.ofSeconds(2);

    /** 读超时 */
    private Duration readTimeout = Duration.ofSeconds(5);

    /** 重试次数（仅连接异常、5xx、服务端 SYSTEM_ERROR 生效） */
    private int maxRetries = 2;

    /** 接口版本段，默认 {@code v1} */
    private String apiVersion = "v1";
}
