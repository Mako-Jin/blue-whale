package com.bluewhale.pdm.integration.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.pdm.integration.dto.PdmIntReconcileQueryRequest;
import com.bluewhale.pdm.integration.service.PdmIntReconcileService;
import com.bluewhale.pdm.integration.support.PdmIntRequestContext;
import com.bluewhale.pdm.integration.vo.PdmIntReconcileVO;
import com.bluewhale.pdm.integration.vo.PdmIntSyncLogVO;
import com.bluewhale.pdm.process.vo.PdmPrcRouteVO;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 集成对账接口
 *
 * <p>对账只读本地发布记录与同步日志；差异通过重新通知或重推补偿，
 * OA 补偿按已保存的审批状态回写业务状态。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping(path = "/pdm/v{version}/integration/reconcile", version = "1")
@RequiredArgsConstructor
public class PdmIntReconcileController {

    /** 审计模块 */
    private static final String MODULE = "PDM 集成";

    private final PdmIntReconcileService reconcileService;

    /**
     * 分页查询对账结果
     *
     * @param request 查询条件
     * @return 分页结果
     */
    @GetMapping("/page")
    @RequirePermission("sys:pdm:int:reconcile:list")
    public Result<PageResult<PdmIntReconcileVO>> page(PdmIntReconcileQueryRequest request) {
        return Result.ok(reconcileService.page(request));
    }

    /**
     * 对账 BOM
     *
     * @return 对账结果
     */
    @PostMapping("/bom/run")
    @RequirePermission("sys:pdm:int:reconcile:bom")
    @AuditLog(module = MODULE, type = OperateType.UPDATE, operation = "对账 ERP BOM 拉取",
            targetType = "pdm_int_reconcile")
    public Result<List<PdmIntReconcileVO>> reconcileBom() {
        return Result.ok(reconcileService.reconcileBom());
    }

    /**
     * 对账 MES 工艺路线
     *
     * @return 对账结果
     */
    @PostMapping("/route/run")
    @RequirePermission("sys:pdm:int:reconcile:bom")
    @AuditLog(module = MODULE, type = OperateType.UPDATE, operation = "对账 MES 路线下发",
            targetType = "pdm_int_reconcile")
    public Result<List<PdmIntReconcileVO>> reconcileRoute() {
        return Result.ok(reconcileService.reconcileRoute());
    }

    /**
     * 对账 OA 审批状态
     *
     * @return 对账结果
     */
    @PostMapping("/oa/run")
    @RequirePermission("sys:pdm:int:reconcile:bom")
    @AuditLog(module = MODULE, type = OperateType.UPDATE, operation = "对账 OA 审批状态",
            targetType = "pdm_int_reconcile")
    public Result<List<PdmIntReconcileVO>> reconcileOa() {
        return Result.ok(reconcileService.reconcileOa());
    }

    /**
     * 重新通知 ERP 拉取 BOM 快照
     *
     * @param bomNo           BOM 编号
     * @param versionNo       版本号
     * @param servletRequest  当前请求
     * @return 同步日志
     */
    @PostMapping("/bom/{bomNo}/versions/{versionNo}/notify")
    @RequirePermission("sys:pdm:int:reconcile:bom")
    @AuditLog(module = MODULE, type = OperateType.UPDATE, operation = "重新通知 ERP 拉取 BOM",
            targetType = "pdm_bom_release")
    public Result<PdmIntSyncLogVO> notifyBom(@PathVariable String bomNo, @PathVariable String versionNo,
                                             HttpServletRequest servletRequest) {
        return Result.ok(reconcileService.notifyBom(bomNo, versionNo,
                PdmIntRequestContext.requireUserId(servletRequest)));
    }

    /**
     * 重推 MES 工艺路线
     *
     * @param routeId        路线ID
     * @param servletRequest 当前请求
     * @return 路线视图
     */
    @PostMapping("/route/{routeId}/repush")
    @RequirePermission("sys:pdm:int:reconcile:bom")
    @AuditLog(module = MODULE, type = OperateType.UPDATE, operation = "重推 MES 工艺路线",
            targetType = "pdm_prc_route")
    public Result<PdmPrcRouteVO> repushRoute(@PathVariable Long routeId, HttpServletRequest servletRequest) {
        return Result.ok(reconcileService.repushRoute(routeId,
                PdmIntRequestContext.requireUserId(servletRequest)));
    }

    /**
     * OA 状态补偿
     *
     * @param bizType        业务类型：REQUEST/ORDER
     * @param bizId          业务ID
     * @param servletRequest 当前请求
     * @return 对账结果
     */
    @PostMapping("/oa/{bizType}/{bizId}/compensate")
    @RequirePermission("sys:pdm:int:oa:compensate")
    @AuditLog(module = MODULE, type = OperateType.UPDATE, operation = "OA 审批状态补偿",
            targetType = "pdm_chg_request")
    public Result<PdmIntReconcileVO> compensateOa(@PathVariable String bizType, @PathVariable Long bizId,
                                                  HttpServletRequest servletRequest) {
        return Result.ok(reconcileService.compensateOa(bizType, bizId,
                PdmIntRequestContext.requireUserId(servletRequest)));
    }
}
