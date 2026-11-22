package com.bluewhale.pay.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code PAYING} 停留单对账配置。
 *
 * <p>定时扫描更新时间早于阈值的 {@code PAYING} 支付单，逐单向渠道确认状态：
 * 仅渠道明确返回 {@code PAID} 时自动收敛，其余状态保持原状等待人工确认，
 * 避免把渠道已扣款的单回退成可支付造成重复扣款。</p>
 *
 * @author BlueWhale
 */
@Data
@ConfigurationProperties(prefix = "whale.pay.reconcile")
public class PayReconcileProperties {

    /** 判定为停留单的阈值（秒）：{@code update_time} 早于 {@code now - staleSeconds} 才扫描 */
    private long staleSeconds = 300;

    /** 单轮最多扫描的支付单数，避免一次对账拖垮渠道接口 */
    private int batchSize = 50;
}
