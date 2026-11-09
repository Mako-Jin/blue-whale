package com.bluewhale.crm.commerce.interfaces.controller;

import com.bluewhale.common.core.result.Result;
import com.bluewhale.crm.commerce.application.service.ArReconcileAppService;
import com.bluewhale.crm.commerce.application.service.ContractAppService;
import com.bluewhale.crm.commerce.application.service.SalesOrderAppService;
import com.bluewhale.crm.config.CrmArProperties;
import com.bluewhale.crm.config.CrmContractProperties;
import com.bluewhale.crm.config.CrmErpProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 商务域同步内网运维入口（仅内网：内部令牌 + 网关 internal-deny）。
 *
 * <p><b>定时调度不经过本控制器</b>：订单下发 / 对账、应收快照、合同投影对账的调度行是
 * {@code job_code=crm.order.push / crm.order.reconcile / crm.ar.snapshot / crm.contract.sync}
 * + {@code httpJobHandler}，由调度中心按执行器寻址后调 {@code POST /internal/job/{code}/run}，
 * 进入对应 {@code @ScheduledJob} 方法并直接调用应用服务。本控制器只是同一应用服务的
 * 手工触发入口（排查 / 补跑）；早期 job_info 1005–1008 走 {@code httpInvokeJobHandler}
 * 直连此处，该通道已于 2026-11-09 合并收口
 * （见 {@code V13__disable_legacy_http_invoke_jobs.sql}）。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping("/crm/internal/commerce-sync")
@RequiredArgsConstructor
public class CommerceSyncInternalController {

    private final SalesOrderAppService salesOrderAppService;

    private final ArReconcileAppService arReconcileAppService;

    private final ContractAppService contractAppService;

    private final CrmErpProperties erpProperties;

    private final CrmArProperties arProperties;

    private final CrmContractProperties contractProperties;

    /**
     * 扫描 outbox 下发待推送商务订单。
     *
     * @return 成功下发单数；ERP 集成禁用时返回 -1（跳过）
     */
    @PostMapping("/order/push")
    public Result<Integer> orderPush() {
        if (!erpProperties.isEnabled()) {
            return Result.ok(-1);
        }
        return Result.ok(salesOrderAppService.pushPending());
    }

    /**
     * 对账 ERP 履约状态。
     *
     * @return 刷新单数；ERP 集成禁用时返回 -1（跳过）
     */
    @PostMapping("/order/reconcile")
    public Result<Integer> orderReconcile() {
        if (!erpProperties.isEnabled()) {
            return Result.ok(-1);
        }
        return Result.ok(salesOrderAppService.reconcile());
    }

    /**
     * 同步当日应收快照（按客户拉取 ERP 未结应收与账龄）。
     *
     * @return 同步客户数；应收集成禁用时返回 -1（跳过）
     */
    @PostMapping("/ar/sync")
    public Result<Integer> arSync() {
        if (!arProperties.isEnabled()) {
            return Result.ok(-1);
        }
        return Result.ok(arReconcileAppService.syncAll().getSynced());
    }

    /**
     * 定时刷新已有台账客户的合同投影（单客户失败不中断本轮）。
     *
     * @return 刷新条数；合同集成禁用时返回 -1（跳过）
     */
    @PostMapping("/contract/reconcile")
    public Result<Integer> contractReconcile() {
        if (!contractProperties.isEnabled()) {
            return Result.ok(-1);
        }
        return Result.ok(contractAppService.reconcile());
    }
}
