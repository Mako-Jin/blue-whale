package com.bluewhale.archive.scheduler;

import com.bluewhale.archive.client.TenantDirectoryClient;
import com.bluewhale.archive.config.ArchiveProperties;
import com.bluewhale.archive.service.ArchiveSyncService;
import com.bluewhale.common.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 档案同步定时任务。
 *
 * <p>仅当 {@code whale.archive.sync.enabled=true} 时注册；单轮异常只记录日志，
 * 不影响后续调度（失败来源由台账重试机制兜底）。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "whale.archive.sync", name = "enabled", havingValue = "true")
public class ArchiveSyncScheduler {

    private final ArchiveSyncService archiveSyncService;

    private final ArchiveProperties properties;

    private final TenantDirectoryClient tenantDirectoryClient;

    /**
     * 按租户目录逐租户执行一轮同步。
     *
     * <p>租户清单优先取显式配置；未配置时查询租户目录，目录不可用回退默认租户 1。
     * 每个租户独立绑定 {@link TenantContext}，单租户异常只记录日志，
     * 不影响其余租户与后续调度。</p>
     */
    @Scheduled(cron = "${whale.archive.sync.cron:0 */5 * * * *}")
    public void sync() {
        for (Long tenantId : resolveTenantIds()) {
            TenantContext.set(tenantId);
            try {
                archiveSyncService.runOnce();
            } catch (Exception e) {
                log.warn("定时档案同步失败: tenantId={}, reason={}", tenantId, e.getMessage());
            } finally {
                TenantContext.clear();
            }
        }
    }

    /**
     * 解析本轮同步的租户清单：显式配置优先，否则查询租户目录，目录为空回退默认租户。
     *
     * @return 租户ID列表（非空）
     */
    private List<Long> resolveTenantIds() {
        List<Long> configured = properties.getSync().getTenantIds();
        if (configured != null && !configured.isEmpty()) {
            return configured;
        }
        List<Long> enabled = tenantDirectoryClient.listEnabledTenantIds();
        return enabled.isEmpty() ? List.of(TenantContext.DEFAULT_TENANT_ID) : enabled;
    }
}
