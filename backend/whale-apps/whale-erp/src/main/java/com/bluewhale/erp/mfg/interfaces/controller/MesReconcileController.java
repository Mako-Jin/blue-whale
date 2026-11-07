package com.bluewhale.erp.mfg.interfaces.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.erp.interfaces.support.ErpUserContext;
import com.bluewhale.erp.interfaces.support.PageParams;
import com.bluewhale.erp.mfg.application.command.MesCompletionApplyCommand;
import com.bluewhale.erp.mfg.application.service.MesIntegrationAppService;
import com.bluewhale.erp.mfg.application.view.MesReconcileDetailView;
import com.bluewhale.erp.mfg.application.view.MesReconcileView;
import com.bluewhale.erp.mfg.application.view.ProductionOrderView;
import com.bluewhale.erp.mfg.domain.model.MesProgressSnapshot;
import com.bluewhale.erp.mfg.domain.model.MesReconcileQuery;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * MES 对账接口：分页、详情、重推、拉取、人工确认入库。
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping("/erp/mfg/mes/reconcile")
@RequiredArgsConstructor
public class MesReconcileController {

    private final MesIntegrationAppService mesIntegrationAppService;

    /**
     * 对账分页。
     *
     * @param query    查询条件
     * @param current  后端风格页码
     * @param pageNum  前端风格页码
     * @param size     后端风格条数
     * @param pageSize 前端风格条数
     * @return 对账行分页
     */
    @GetMapping("/page")
    @RequirePermission("sys:erp:mes:reconcile:list")
    public Result<PageResult<MesReconcileView>> page(
            @ModelAttribute MesReconcileQuery query,
            @RequestParam(value = "current", required = false) Long current,
            @RequestParam(value = "pageNum", required = false) Long pageNum,
            @RequestParam(value = "size", required = false) Long size,
            @RequestParam(value = "pageSize", required = false) Long pageSize) {
        query.setCurrent(PageParams.current(current, pageNum));
        query.setSize(PageParams.size(size, pageSize));
        return Result.ok(mesIntegrationAppService.reconcilePage(query));
    }

    /**
     * 对账详情。
     *
     * @param orderId 生产订单 ID
     * @return 详情
     */
    @GetMapping("/{orderId}")
    @RequirePermission("sys:erp:mes:reconcile:list")
    public Result<MesReconcileDetailView> detail(@PathVariable Long orderId) {
        return Result.ok(mesIntegrationAppService.reconcileDetail(orderId));
    }

    /**
     * 重推生产订单到 MES。
     *
     * @param orderId 生产订单 ID
     * @return 生产订单视图
     */
    @PostMapping("/{orderId}/push")
    @RequirePermission("sys:erp:mes:push")
    @AuditLog(operation = "重推生产订单到 MES", targetType = "erp_mfg_production_order", type = OperateType.UPDATE)
    public Result<ProductionOrderView> push(@PathVariable Long orderId) {
        return Result.ok(mesIntegrationAppService.push(orderId));
    }

    /**
     * 从 MES 拉取工单进度。
     *
     * @param orderId 生产订单 ID
     * @return 进度快照
     */
    @PostMapping("/{orderId}/pull")
    @RequirePermission("sys:erp:mes:pull")
    @AuditLog(operation = "拉取 MES 工单进度", targetType = "erp_mfg_production_order", type = OperateType.UPDATE)
    public Result<MesProgressSnapshot> pull(@PathVariable Long orderId) {
        return Result.ok(mesIntegrationAppService.pull(orderId));
    }

    /**
     * 人工确认 MES 完工入库。
     *
     * @param orderId 生产订单 ID
     * @param command 入库数量
     * @param request HTTP 请求（取操作人）
     * @return 生产订单视图
     */
    @PostMapping("/{orderId}/completion-apply")
    @RequirePermission("sys:erp:mes:complete")
    @AuditLog(operation = "确认 MES 完工入库", targetType = "erp_mfg_production_order", type = OperateType.UPDATE)
    public Result<ProductionOrderView> applyCompletion(@PathVariable Long orderId,
                                                       @Valid @RequestBody MesCompletionApplyCommand command,
                                                       HttpServletRequest request) {
        return Result.ok(mesIntegrationAppService.applyCompletion(orderId, command.getQuantity(),
                ErpUserContext.userId(request)));
    }
}
