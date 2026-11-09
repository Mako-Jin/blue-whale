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
 * 商务域同步调度触发控制器（仅内网：whale-job 调度中心）。
 *
 * <p>原进程内 {@code @Scheduled}（订单下发 / 对账、应收快照、合同投影对账）
 * 已于 2026-11-09 第十一批迁移至 whale-job（job_info 1005–1008，经
 * httpInvokeJobHandler 服务发现直连本控制器）；调度参数（cron / 重试 /
 * misfire / 执行日志）由调度中心统一管理。</p>
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
