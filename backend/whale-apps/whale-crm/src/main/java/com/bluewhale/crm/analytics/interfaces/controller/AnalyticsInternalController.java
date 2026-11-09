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
 * 分析域内网运维入口（仅内网：内部令牌 + 网关 internal-deny）。
 *
 * <p><b>定时调度不经过本控制器</b>：分析快照与行动项生成的调度行是
 * {@code job_code=crm.analytics.snapshot / crm.analytics.action-plan} +
 * {@code httpJobHandler}，由调度中心按执行器寻址后调 {@code POST /internal/job/{code}/run}，
 * 进入 {@code AnalyticsSnapshotJob} 的 {@code @ScheduledJob} 方法并直接调用应用服务。
 * 本控制器只是同一应用服务的手工触发入口（排查 / 补跑）；早期 job_info 1001 / 1002
 * 走 {@code httpInvokeJobHandler} 直连此处，该通道已于 2026-11-09 合并收口
 * （见 {@code V13__disable_legacy_http_invoke_jobs.sql}）。</p>
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
