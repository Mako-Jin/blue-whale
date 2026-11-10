package com.bluewhale.crm.service.interfaces.controller;

import com.bluewhale.common.core.result.Result;
import com.bluewhale.crm.config.CrmErpProperties;
import com.bluewhale.crm.service.application.service.ReturnRequestAppService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 退换货 ERP 同步内网运维入口（仅内网：内部令牌 + 网关 internal-deny）。
 *
 * <p><b>定时调度不经过本控制器</b>：退换货下发 / 对账的调度行是
 * {@code job_code=crm.return.push / crm.return.reconcile} + {@code httpJobHandler}，
 * 由调度中心按执行器寻址后调 {@code POST /internal/job/{code}/run}，进入
 * {@code CrmReturnSyncJob} 的 {@code @ScheduledJob} 方法并直接调用应用服务。
 * 本控制器只是同一应用服务的手工触发入口（排查 / 补跑）；早期 job_info 1003 / 1004
 * 走 {@code httpInvokeJobHandler} 直连此处，该通道已于 2026-11-09 合并收口
 * （见 {@code V13__disable_legacy_http_invoke_jobs.sql}）。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping("/crm/internal/return-sync")
@RequiredArgsConstructor
public class ReturnSyncInternalController {

    private final ReturnRequestAppService returnRequestAppService;

    private final CrmErpProperties erpProperties;

    /**
     * 扫描 outbox 下发待执行退换货。
     *
     * @return 成功下发条数；ERP 集成禁用时返回 -1（跳过）
     */
    @PostMapping("/push")
    public Result<Integer> push() {
        if (!erpProperties.isEnabled()) {
            return Result.ok(-1);
        }
        return Result.ok(returnRequestAppService.pushPending());
    }

    /**
     * 对账执行中 / 失败的退换货 ERP 状态。
     *
     * @return 刷新条数；ERP 集成禁用时返回 -1（跳过）
     */
    @PostMapping("/reconcile")
    public Result<Integer> reconcile() {
        if (!erpProperties.isEnabled()) {
            return Result.ok(-1);
        }
        return Result.ok(returnRequestAppService.reconcileAll());
    }
}
