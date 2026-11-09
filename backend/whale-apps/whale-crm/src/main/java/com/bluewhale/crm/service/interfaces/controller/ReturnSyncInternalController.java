package com.bluewhale.crm.service.interfaces.controller;

import com.bluewhale.common.core.result.Result;
import com.bluewhale.crm.config.CrmErpProperties;
import com.bluewhale.crm.service.application.service.ReturnRequestAppService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 退换货 ERP 同步调度触发控制器（仅内网：whale-job 调度中心）。
 *
 * <p>原进程内 {@code @Scheduled}（erp.push-cron / erp.reconcile-cron）已于
 * 2026-11-09 第十一批迁移至 whale-job（job_info 1003 / 1004，经
 * httpInvokeJobHandler 服务发现直连本控制器）；调度参数（cron / 重试 /
 * misfire / 执行日志）由调度中心统一管理。</p>
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
