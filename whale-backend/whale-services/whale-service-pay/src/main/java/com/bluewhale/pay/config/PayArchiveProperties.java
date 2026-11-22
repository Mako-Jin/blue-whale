package com.bluewhale.pay.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 支付数据归档配置。
 *
 * <p>热表只保留近期数据：支付/退款流水按天归档，成功通知按天归档；
 * 待投递与死信任务永不归档（死信需要人工介入）。归档采用
 * {@code INSERT IGNORE ... SELECT} + {@code DELETE ... LIMIT} 分批执行，
 * 中断后重跑不会丢数据也不会重复。</p>
 *
 * @author BlueWhale
 */
@Data
@ConfigurationProperties(prefix = "whale.pay.archive")
public class PayArchiveProperties {

    /** 支付流水保留天数（超过则归档到 pay_transaction_history） */
    private long transactionRetentionDays = 180;

    /** 成功通知任务保留天数（超过则归档到 pay_notify_task_history） */
    private long notifySuccessRetentionDays = 30;

    /** 单批搬迁行数 */
    private int batchSize = 500;

    /** 单次任务最多执行批次数，避免一次归档长时间占用数据库 */
    private int maxBatchesPerRun = 200;
}
