package com.bluewhale.alert.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 告警记录保留策略配置，前缀 {@code whale.alert.retention}。
 *
 * <p>{@code alert_record} 只增不减会持续膨胀。已恢复 / 已静默且超过保留期的记录
 * 由定时任务物理删除（告警记录属于运行数据，逻辑删除对容量无意义）。</p>
 *
 * @author BlueWhale
 */
@Data
@Component
@ConfigurationProperties(prefix = "whale.alert.retention")
public class AlertRetentionProperties {

    /** 是否启用清理任务 */
    private boolean enabled = true;

    /** 保留天数（按 trigger_time 计算） */
    private int days = 90;

    /** 单次清理最大行数，避免长事务与主从延迟 */
    private int batchSize = 1000;
}
