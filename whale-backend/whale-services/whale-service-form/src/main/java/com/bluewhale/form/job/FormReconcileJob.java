package com.bluewhale.form.job;

import com.bluewhale.common.jobschedule.ScheduledJob;
import com.bluewhale.common.tenant.TenantContext;
import com.bluewhale.form.mapper.FormDataMapper;
import com.bluewhale.form.service.FormReconcileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 表单主从数据对账任务（每日一次，按租户遍历）。
 *
 * <p>为什么在任务里遍历租户而不是服务里：定时线程没有租户上下文，服务方法内若自调用，
 * 事务代理会失效；且对账范围必须由租户上下文收敛。任务持有活跃租户清单，逐个设置上下文后
 * 调用服务，每次调用都真正经过代理。</p>
 *
 * <p>单租户失败只记日志不抛出：一个租户的脏数据不该让其他租户的对账一起停摆，明天这一轮还会重来。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "whale.form", name = "reconcile-enabled",
        havingValue = "true", matchIfMissing = true)
public class FormReconcileJob {

    private final FormReconcileService formReconcileService;

    private final FormDataMapper formDataMapper;

    /**
     * 遍历活跃租户执行主从数据对账并登记差异。
     *
     * @return 执行摘要
     */
    @ScheduledJob(code = "form.data.reconcile",
            cron = "${whale.form.reconcile-cron:0 40 3 * * ?}", timeoutSeconds = 600)
    public String reconcile() {
        List<Long> tenantIds = formDataMapper.selectActiveTenantIds();
        int tables = 0;
        int diff = 0;
        int truncated = 0;
        int failed = 0;
        for (Long tenantId : tenantIds) {
            TenantContext.set(tenantId);
            try {
                FormReconcileService.ReconcileSummary summary = formReconcileService.reconcile(tenantId);
                tables += summary.tableCount();
                diff += summary.diffCount();
                if (summary.truncated()) {
                    truncated++;
                }
                if (summary.diffCount() > 0) {
                    log.warn("表单主从数据存在差异 tenantId={} batch={} tables={} diff={}",
                            tenantId, summary.batchNo(), summary.tableCount(), summary.diffCount());
                }
            } catch (RuntimeException e) {
                failed++;
                log.warn("表单主从数据对账失败 tenantId={}", tenantId, e);
            } finally {
                TenantContext.clear();
            }
        }
        return "OK: tenants=" + tenantIds.size() + " tables=" + tables + " diff=" + diff
                + " truncated=" + truncated + " failed=" + failed;
    }
}
