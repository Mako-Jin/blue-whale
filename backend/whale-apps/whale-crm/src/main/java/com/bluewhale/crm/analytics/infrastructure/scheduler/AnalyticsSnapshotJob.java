package com.bluewhale.crm.analytics.infrastructure.scheduler;

import com.bluewhale.crm.analytics.application.service.ActionPlanAppService;
import com.bluewhale.crm.analytics.application.service.AnalyticsAppService;
import com.bluewhale.crm.common.CrmActor;
import com.bluewhale.crm.config.CrmAnalyticsProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Set;

/**
 * 分析快照与行动计划定时任务。
 *
 * <p>仅在 {@code whale.crm.analytics.enabled=true} 时注册与执行；cron 来自配置，
 * 默认每日 02:00 生成昨日快照、02:30 生成行动计划。</p>
 *
 * @author BlueWhale
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "whale.crm.analytics", name = "enabled",
        havingValue = "true", matchIfMissing = true)
public class AnalyticsSnapshotJob {

    private final AnalyticsAppService analyticsAppService;
    private final ActionPlanAppService actionPlanAppService;
    private final CrmAnalyticsProperties analyticsProperties;

    /**
     * 生成昨日指标快照。
     */
    @Scheduled(cron = "${whale.crm.analytics.snapshot-cron:0 0 2 * * ?}")
    public void generateSnapshots() {
        if (!analyticsProperties.isEnabled()) {
            return;
        }
        analyticsAppService.generateSnapshots(LocalDate.now().minusDays(1));
    }

    /**
     * 生成行动计划。
     */
    @Scheduled(cron = "${whale.crm.analytics.action-plan-cron:0 30 2 * * ?}")
    public void generateActionItems() {
        if (!analyticsProperties.isEnabled()) {
            return;
        }
        actionPlanAppService.generate(new CrmActor(0L, "system", Set.of("tenant_admin"), Set.of()));
    }
}
