package com.bluewhale.pay.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 支付超时自动关单配置。
 *
 * <p>只扫描 {@code CREATED} 状态：{@code PAYING} 已发往渠道，必须等回调或对账确认，
 * 直接关单可能出现「渠道已扣款、本地已取消」。渠道关单失败按指数退避重试，
 * 不阻塞同一轮其他支付单扫描。</p>
 *
 * @author BlueWhale
 */
@Data
@ConfigurationProperties(prefix = "whale.pay.close")
public class PayCloseProperties {

    /** 支付有效期（分钟）：建单超过该时长仍未支付即自动关单 */
    private long timeoutMinutes = 30;

    /** 单轮最多关单的支付单数 */
    private int batchSize = 100;

    /** 关单扫描间隔（毫秒） */
    private long intervalMs = 300000;

    /** 渠道关单失败后的首次退避秒数（之后按 2^n 指数退避） */
    private long backoffSeconds = 60;

    /** 退避上限（秒），避免失败单无限延后 */
    private long maxBackoffSeconds = 3600;
}
