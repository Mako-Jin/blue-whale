package com.bluewhale.archive.scheduler;

import com.bluewhale.archive.client.TenantDirectoryClient;
import com.bluewhale.archive.config.ArchiveProperties;
import com.bluewhale.archive.service.ArchiveSyncService;
import com.bluewhale.archive.vo.ArchiveSyncResultVO;
import com.bluewhale.common.jobschedule.ScheduledJob;
import com.bluewhale.common.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 档案同步定时任务。
 *
 * <p>开关 {@code whale.archive.sync.enabled} 只决定定时入口是否执行（关闭时返回
 * {@code skipped}），不移除 Bean——条件装配会让任务从执行器能力集中消失，
 * 调度中心将无法区分「开关没开」与「服务没部署」。</p>
 *
 * <p>单租户异常只记录日志并计数，不影响其余租户（失败来源由台账重试机制兜底）。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Component
@RequiredArgsConstructor
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
     *
     * @return 执行摘要（{@code skipped} 或租户数 / 归档数 / 失败租户数）
     */
    @ScheduledJob(code = "archive.sync",
            cron = "${whale.archive.sync.cron:0 */5 * * * *}",
            timeoutSeconds = 1800)
    public String sync() {
        if (!properties.getSync().isEnabled()) {
            return "skipped: archive sync disabled";
        }
        int tenants = 0;
        int failedTenants = 0;
        long archived = 0;
        for (Long tenantId : resolveTenantIds()) {
            tenants++;
            TenantContext.set(tenantId);
            try {
                ArchiveSyncResultVO result = archiveSyncService.runOnce();
                archived += result.getArchived();
            } catch (Exception e) {
                failedTenants++;
                log.warn("定时档案同步失败: tenantId={}, reason={}", tenantId, e.getMessage());
            } finally {
                TenantContext.clear();
            }
        }
        return "tenants=" + tenants + ", archived=" + archived + ", failedTenants=" + failedTenants;
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
