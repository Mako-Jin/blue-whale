package com.bluewhale.form.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.form.model.request.FormReconcileDiffQueryRequest;
import com.bluewhale.form.model.vo.FormReconcileDiffVO;
import com.bluewhale.form.model.vo.FormReconcileSummaryVO;
import com.bluewhale.form.service.FormReconcileService;
import com.bluewhale.form.support.TenantResolver;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 表单主从数据对账接口。
 *
 * <p>服务内路径前缀 {@code /form/data/reconcile}；提供手动触发对账、分页查看差异与人工修复差异
 * 三类能力，全部受 {@code @RequirePermission("sys:form:data:reconcile")} 保护。
 * 租户ID 由网关注入的请求头 {@code X-Tenant-Id} 解析，缺失或非法一律拒绝（{@code FormTenantConfig} 统一守卫）。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping(path = "/form/v{version}/data/reconcile", version = "1")
@RequiredArgsConstructor
public class FormReconcileController {

    private final FormReconcileService formReconcileService;

    /**
     * 手动触发当前租户的主从数据对账。
     *
     * @param httpRequest 当前请求（读取 {@code X-Tenant-Id} 解析租户）
     * @return 对账执行摘要
     */
    @PostMapping("/run")
    @RequirePermission("sys:form:data:reconcile")
    public Result<FormReconcileSummaryVO> run(HttpServletRequest httpRequest) {
        FormReconcileService.ReconcileSummary summary =
                formReconcileService.reconcile(TenantResolver.resolve(httpRequest));
        return Result.ok(toVo(summary));
    }

    /**
     * 分页查询对账差异记录。
     *
     * @param request 查询条件：batchNo / status / physicalTable
     * @param httpRequest 当前请求（读取 {@code X-Tenant-Id} 解析租户）
     * @return 差异记录分页结果
     */
    @GetMapping("/diffs")
    @RequirePermission("sys:form:data:reconcile")
    public Result<PageResult<FormReconcileDiffVO>> diffs(@Valid FormReconcileDiffQueryRequest request,
                                                         HttpServletRequest httpRequest) {
        return Result.ok(formReconcileService.page(request, TenantResolver.resolve(httpRequest)));
    }

    /**
     * 人工修复一条对账差异（幂等：仅处理待处理状态）。
     *
     * @param id 差异记录ID
     * @param httpRequest 当前请求（读取 {@code X-Tenant-Id} 解析租户）
     * @return 空结果
     */
    @PostMapping("/diffs/{id}/repair")
    @RequirePermission("sys:form:data:reconcile")
    public Result<Void> repair(@PathVariable Long id, HttpServletRequest httpRequest) {
        formReconcileService.repair(id, TenantResolver.resolve(httpRequest));
        return Result.ok();
    }

    /**
     * 对账摘要转换为视图对象
     *
     * @param summary 对账摘要
     * @return 摘要视图对象
     */
    private static FormReconcileSummaryVO toVo(FormReconcileService.ReconcileSummary summary) {
        FormReconcileSummaryVO vo = new FormReconcileSummaryVO();
        vo.setBatchNo(summary.batchNo());
        vo.setTableCount(summary.tableCount());
        vo.setDiffCount(summary.diffCount());
        vo.setTruncated(summary.truncated());
        return vo;
    }
}
