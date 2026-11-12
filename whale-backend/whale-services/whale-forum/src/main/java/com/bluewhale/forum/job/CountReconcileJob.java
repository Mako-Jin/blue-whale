package com.bluewhale.forum.job;

import com.bluewhale.common.jobschedule.ScheduledJob;
import com.bluewhale.common.tenant.TenantContext;
import com.bluewhale.forum.config.ForumProperties;
import com.bluewhale.forum.dto.ReconcileReportVO;
import com.bluewhale.forum.mapper.ForumThreadMapper;
import com.bluewhale.forum.service.CountReconcileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 计数对账任务（每日一次，按租户遍历）。
 *
 * <p>为什么在任务里遍历租户而不是服务里：定时线程没有租户上下文，服务方法一旦被同类的
 * 另一个方法自调用，{@code @Transactional} 会绕过代理静默失效（热榜快照踩过这个坑，
 * 见 {@code HotSnapshotStore} 的注释）。任务持有租户清单、逐个设置上下文后调用服务，
 * 每次调用都真正经过事务代理。</p>
 *
 * <p>单租户失败只记日志不抛出：一个租户的脏数据不该让其他租户的对账一起停摆，
 * 明天这一轮还会重来。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "whale.forum", name = "count-reconcile-enabled",
        havingValue = "true", matchIfMissing = true)
public class CountReconcileJob {

    private final CountReconcileService reconcileService;

    private final ForumThreadMapper threadMapper;

    private final ForumProperties properties;

    /**
     * 按真实值修正所有租户的计数漂移。
     *
     * @return 执行摘要
     */
    @ScheduledJob(code = "forum.count.reconcile",
            cron = "${whale.forum.count-reconcile-cron:0 30 3 * * ?}", timeoutSeconds = 300)
    public String reconcile() {
        if (!properties.isCountReconcileEnabled()) {
            return "SKIPPED: whale.forum.count-reconcile-enabled=false";
        }
        List<Long> tenantIds = threadMapper.selectActiveTenantIds();
        int diff = 0;
        int fixed = 0;
        int failed = 0;
        for (Long tenantId : tenantIds) {
            TenantContext.set(tenantId);
            try {
                ReconcileReportVO report = reconcileService.reconcile(true, null, null);
                diff += report.getDiffCount();
                fixed += report.getFixedCount();
                if (report.getDiffCount() > 0) {
                    log.warn("论坛计数漂移已修正 tenantId={} batch={} diff={} fixed={}",
                            tenantId, report.getBatchNo(), report.getDiffCount(), report.getFixedCount());
                }
            } catch (RuntimeException e) {
                failed++;
                log.warn("论坛计数对账失败 tenantId={}", tenantId, e);
            } finally {
                TenantContext.clear();
            }
        }
        return "OK: tenants=" + tenantIds.size() + " diff=" + diff + " fixed=" + fixed
                + " failed=" + failed;
    }
}
