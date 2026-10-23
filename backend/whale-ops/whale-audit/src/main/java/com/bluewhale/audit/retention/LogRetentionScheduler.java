package com.bluewhale.audit.retention;

import com.bluewhale.audit.config.RetentionProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 日志保留期定时任务调度器（REQ-SEC-006 / AUTH-09）。
 *
 * <p>{@code whale.audit.retention.enabled=true} 时按 {@code whale.audit.retention.cron}
 * 触发 {@link LogRetentionCleaner} 清理 4 张日志表；默认关闭（不清理，避免误删）。</p>
 *
 * <p>{@code whale.audit.retention.archive-enabled=true}（<b>默认开启</b>）时触发
 * {@link AuditLogArchiver} 导出 {@code audit_log} 过期记录并归档（AUTH-09）。
 * 归档<b>只导出上传、绝不删除 {@code audit_log}</b>；清理器仍只覆盖 4 张日志表。</p>
 *
 * <p>两个开关相互独立：任一开启即装配本调度器，各自按开关决定是否执行。
 * 因此默认配置下仅执行归档、不执行日志清理。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnExpression("${whale.audit.retention.enabled:false} or ${whale.audit.retention.archive-enabled:true}")
public class LogRetentionScheduler {

    private final LogRetentionCleaner cleaner;

    private final AuditLogArchiver archiver;

    private final RetentionProperties properties;

    /**
     * 按配置 cron 触发：清理开关开启时清理 4 张日志表，归档开关开启时导出 {@code audit_log} 过期记录
     */
    @Scheduled(cron = "${whale.audit.retention.cron}")
    public void cleanExpiredLogs() {
        log.info("触发日志保留期任务: 清理={}, 保留天数={}, 归档={}",
                properties.isEnabled(), properties.getDays(), properties.isArchiveEnabled());
        if (properties.isEnabled()) {
            cleaner.cleanExpired(properties.getDays());
        }
        if (properties.isArchiveEnabled()) {
            archiver.archive(properties.getDays());
        }
    }
}
