package com.bluewhale.audit.retention;

import com.bluewhale.audit.config.RetentionProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 日志保留期定时清理调度器（REQ-SEC-006）。
 *
 * <p>{@code whale.audit.retention.enabled=true} 时装配并按 {@code whale.audit.retention.cron}
 * 周期性触发 {@link LogRetentionCleaner}；默认关闭（不装配、不清理）。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "whale.audit.retention.enabled", havingValue = "true")
public class LogRetentionScheduler {

    private final LogRetentionCleaner cleaner;

    private final RetentionProperties properties;

    /**
     * 按配置 cron 触发日志保留期清理
     */
    @Scheduled(cron = "${whale.audit.retention.cron}")
    public void cleanExpiredLogs() {
        log.info("触发日志保留期清理: 保留天数={}", properties.getDays());
        cleaner.cleanExpired(properties.getDays());
    }
}
