package com.bluewhale.pay.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 业务通知配置。
 *
 * @author BlueWhale
 */
@Data
@ConfigurationProperties(prefix = "whale.pay.notify")
public class PayNotifyProperties {

    /** 最大自动重试次数，超过后置 DEAD 等待人工重试 */
    private int maxRetries = 6;

    /** 首次重试退避秒数（之后按 2^n 指数退避） */
    private int backoffSeconds = 30;

    /** 单次投递读超时秒数 */
    private long timeoutSeconds = 5;
}
