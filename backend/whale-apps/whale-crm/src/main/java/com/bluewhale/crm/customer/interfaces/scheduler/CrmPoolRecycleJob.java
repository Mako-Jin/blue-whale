package com.bluewhale.crm.customer.interfaces.scheduler;

import com.bluewhale.common.jobschedule.ScheduledJob;
import com.bluewhale.crm.common.CrmActor;
import com.bluewhale.crm.config.CrmProperties;
import com.bluewhale.crm.customer.application.service.CustomerPoolAppService;
import com.bluewhale.crm.customer.application.view.PoolRecycleResultView;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * 公海自动回收定时任务。
 *
 * <p>默认关闭：{@code whale.crm.pool-recycle-enabled=false} 时任务空转并返回 {@code skipped}。
 * 开关判断放在方法内而非 {@code @ConditionalOnProperty}，是为了让本任务始终登记进执行器能力集——
 * 否则调度中心只会看到「执行器在线但不支持该任务」，无法区分「开关没开」与「服务没部署」。</p>
 *
 * <p>当前按默认租户执行；多租户部署需由调度平台逐租户触发，或改造为按租户遍历。</p>
 *
 * @author BlueWhale
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CrmPoolRecycleJob {

    private final CustomerPoolAppService poolAppService;

    private final CrmProperties crmProperties;

    /**
     * 按公海规则回收客户。
     *
     * @return 执行摘要（{@code skipped} 或规则名与回收数量）
     */
    @ScheduledJob(code = "crm.pool.recycle",
            cron = "${whale.crm.pool-recycle-cron:0 0 2 * * ?}",
            timeoutSeconds = 1800)
    public String recycle() {
        if (!crmProperties.isPoolRecycleEnabled()) {
            return "skipped: pool-recycle disabled";
        }
        PoolRecycleResultView result = poolAppService.recycle(systemActor());
        log.info("CRM 公海自动回收完成：rule={}, recycled={}", result.getRuleName(), result.getRecycledCount());
        return "rule=" + result.getRuleName() + ", recycled=" + result.getRecycledCount();
    }

    /**
     * 构造系统操作人（无用户 ID，具备全量数据范围与管理员角色）。
     *
     * @return 系统操作人
     */
    private CrmActor systemActor() {
        return new CrmActor(null, "system", Set.of("crm_admin"), Set.of());
    }
}
