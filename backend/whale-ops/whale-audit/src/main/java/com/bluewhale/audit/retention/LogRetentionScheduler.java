package com.bluewhale.audit.retention;

import com.bluewhale.audit.config.RetentionProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 日志保留期定时清理调度器（REQ-SEC-006 / AUTH-09）。
 *
 * <p>{@code whale.audit.retention.enabled=true} 时装配并按 {@code whale.audit.retention.cron}
 * 周期性触发 {@link LogRetentionCleaner}；默认关闭（不装配、不清理）。</p>
 *
 * <p>当 {@code whale.audit.retention.archive-enabled=true} 时，同一次调度额外触发
 * {@link AuditLogArchiver} 导出 {@code audit_log} 过期记录并归档（AUTH-09，默认 false）。
 * 归档<b>只导出上传、绝不删除 {@code audit_log}</b>；清理器仍只覆盖 4 张日志表。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "whale.audit.retention.enabled", havingValue = "true")
public class LogRetentionScheduler {

    private final LogRetentionCleaner cleaner;

    private final AuditLogArchiver archiver;

    private final RetentionProperties properties;

    /**
     * 按配置 cron 触发日志保留期清理；开启归档开关时附带执行 {@code audit_log} 归档导出
     */
    @Scheduled(cron = "${whale.audit.retention.cron}")
    public void cleanExpiredLogs() {
        log.info("触发日志保留期清理: 保留天数={}, 归档={}", properties.getDays(), properties.isArchiveEnabled());
        cleaner.cleanExpired(properties.getDays());
        if (properties.isArchiveEnabled()) {
            archiver.archive(properties.getDays());
        }
    }
}
