package com.bluewhale.archive.scheduler;

import com.bluewhale.archive.client.TenantDirectoryClient;
import com.bluewhale.archive.config.ArchiveProperties;
import com.bluewhale.archive.service.ArchiveFileLockReconcileService;
import com.bluewhale.archive.vo.ArchiveFileLockReconcileVO;
import com.bluewhale.common.jobschedule.ScheduledJob;
import com.bluewhale.common.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 历史归档文件锁定自动补扫任务（F-01）。
 *
 * <p>上线前已归档条目的原件可能未锁定，此前只能由运维逐租户手工调用
 * {@code POST /archive/internal/items/lock-reconcile}。本调度在开关开启后：
 * 逐租户绑定 {@link TenantContext}，按条目 ID 游标循环补锁，直到
 * {@code hasMore=false} 或达到单租户批次数上限；单租户异常只记录日志，不影响其它租户。</p>
 *
 * <p>开关 {@code whale.archive.lock-reconcile.enabled} 只决定定时入口是否执行
 * （关闭时返回 {@code skipped}），不移除 Bean，保证任务始终出现在执行器能力集中。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ArchiveFileLockReconcileScheduler {

    private final ArchiveFileLockReconcileService reconcileService;

    private final ArchiveProperties properties;

    private final TenantDirectoryClient tenantDirectoryClient;

    /**
     * 按租户逐批补锁历史归档文件。
     *
     * <p>每批从 {@code afterId} 游标继续；达到 {@code maxBatches} 时停止本轮，
     * 剩余条目留给下一次调度，避免单轮长时间占用数据库与文件服务。</p>
     *
     * @return 执行摘要（{@code skipped} 或租户数 / 补锁文件数 / 失败租户数）
     */
    @ScheduledJob(code = "archive.file-lock-reconcile",
            cron = "${whale.archive.lock-reconcile.cron:0 30 3 * * *}",
            timeoutSeconds = 1800)
    public String reconcile() {
        ArchiveProperties.LockReconcile config = properties.getLockReconcile();
        if (!config.isEnabled()) {
            return "skipped: lock-reconcile disabled";
        }
        int tenants = 0;
        int failedTenants = 0;
        long locked = 0;
        for (Long tenantId : resolveTenantIds(config)) {
            tenants++;
            TenantContext.set(tenantId);
            try {
                locked += reconcileTenant(tenantId, config);
            } catch (Exception e) {
                failedTenants++;
                log.warn("定时归档补锁失败: tenantId={}, reason={}", tenantId, e.getMessage());
            } finally {
                TenantContext.clear();
            }
        }
        return "tenants=" + tenants + ", locked=" + locked + ", failedTenants=" + failedTenants;
    }

    /**
     * 单租户按游标分批补锁。
     *
     * @param tenantId 租户ID
     * @param config   补扫配置
     * @return 本轮补锁成功的文件数
     */
    private long reconcileTenant(Long tenantId, ArchiveProperties.LockReconcile config) {
        Long afterId = null;
        int batches = 0;
        long locked = 0;
        int maxBatches = Math.max(config.getMaxBatches(), 1);
        while (batches < maxBatches) {
            ArchiveFileLockReconcileVO result = reconcileService.reconcile(config.getBatchSize(), afterId);
            batches++;
            locked += result.getLockedFiles() == null ? 0 : result.getLockedFiles();
            log.info("定时归档补锁批次完成: tenantId={}, batch={}, scanned={}, locked={}, failed={}, hasMore={}",
                    tenantId, batches, result.getScannedItems(), result.getLockedFiles(),
                    result.getFailedFiles(), result.getHasMore());
            if (!Boolean.TRUE.equals(result.getHasMore())) {
                return locked;
            }
            Long next = result.getNextAfterId();
            if (next == null || next.equals(afterId)) {
                log.warn("定时归档补锁游标未推进，提前结束: tenantId={}, afterId={}", tenantId, afterId);
                return locked;
            }
            afterId = next;
        }
        log.info("定时归档补锁达到单轮批次上限，剩余条目留待下次调度: tenantId={}, afterId={}",
                tenantId, afterId);
        return locked;
    }

    /**
     * 解析本轮补扫的租户清单：补锁显式配置优先，其次复用同步配置，否则查询租户目录。
     *
     * @param config 补扫配置
     * @return 租户ID列表（非空）
     */
    private List<Long> resolveTenantIds(ArchiveProperties.LockReconcile config) {
        if (config.getTenantIds() != null && !config.getTenantIds().isEmpty()) {
            return config.getTenantIds();
        }
        List<Long> configured = properties.getSync().getTenantIds();
        if (configured != null && !configured.isEmpty()) {
            return configured;
        }
        List<Long> enabled = tenantDirectoryClient.listEnabledTenantIds();
        return enabled.isEmpty() ? List.of(TenantContext.DEFAULT_TENANT_ID) : enabled;
    }
}
