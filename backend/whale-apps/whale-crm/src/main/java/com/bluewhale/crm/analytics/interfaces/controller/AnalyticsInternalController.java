package com.bluewhale.crm.analytics.interfaces.controller;

import com.bluewhale.common.core.result.Result;
import com.bluewhale.crm.analytics.application.service.ActionPlanAppService;
import com.bluewhale.crm.analytics.application.service.AnalyticsAppService;
import com.bluewhale.crm.common.CrmActor;
import com.bluewhale.crm.config.CrmAnalyticsProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Set;

/**
 * 分析定时触发控制器（仅内网：whale-job 调度中心）。
 *
 * <p>原进程内 {@code @Scheduled}（snapshot-cron / action-plan-cron）已于
 * 2026-11-09 第八批迁移至 whale-job（job_info 1001 / 1002，经
 * httpInvokeJobHandler 服务发现直连本控制器）；调度参数（cron / 重试 /
 * misfire / 执行日志）由调度中心统一管理。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping("/crm/internal/analytics")
@RequiredArgsConstructor
public class AnalyticsInternalController {

    private final AnalyticsAppService analyticsAppService;

    private final ActionPlanAppService actionPlanAppService;

    private final CrmAnalyticsProperties analyticsProperties;

    /**
     * 生成昨日指标快照（幂等覆盖，重跑安全）。
     *
     * @return 生成条数；模块禁用时返回 -1（跳过）
     */
    @PostMapping("/snapshot")
    public Result<Integer> snapshot() {
        if (!analyticsProperties.isEnabled()) {
            return Result.ok(-1);
        }
        return Result.ok(analyticsAppService.generateSnapshots(LocalDate.now().minusDays(1)));
    }

    /**
     * 生成行动计划（规则化，自动关闭已解除的未关闭行动项）。
     *
     * @return 新增条数；模块禁用时返回 -1（跳过）
     */
    @PostMapping("/action-plan")
    public Result<Integer> actionPlan() {
        if (!analyticsProperties.isEnabled()) {
            return Result.ok(-1);
        }
        return Result.ok(actionPlanAppService.generate(new CrmActor(0L, "system",
                Set.of("tenant_admin"), Set.of())));
    }
}
