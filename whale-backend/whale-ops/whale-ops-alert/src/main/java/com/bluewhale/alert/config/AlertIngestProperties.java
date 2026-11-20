package com.bluewhale.alert.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 告警入站配置，前缀 {@code whale.alert.ingest}。
 *
 * @author BlueWhale
 */
@Data
@Component
@ConfigurationProperties(prefix = "whale.alert.ingest")
public class AlertIngestProperties {

    /** 单次请求允许的最大告警条数，超出直接拒绝，避免超大批次打满内存 */
    private int maxAlerts = 500;

    /** 分片大小：每片一个独立事务，控制单事务时长 */
    private int batchSize = 100;

    /** 入站请求体最大字节数（默认 1MB），超出直接 413 拒绝 */
    private long maxPayloadBytes = 1024L * 1024L;
}
