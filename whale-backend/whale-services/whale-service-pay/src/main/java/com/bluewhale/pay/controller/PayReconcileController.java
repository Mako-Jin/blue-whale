package com.bluewhale.pay.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.common.web.UserContext;
import com.bluewhale.pay.dto.PayReconcileBatchQueryRequest;
import com.bluewhale.pay.dto.PayReconcileDiffQueryRequest;
import com.bluewhale.pay.dto.PayReconcileHandleRequest;
import com.bluewhale.pay.dto.PayReconcileRunRequest;
import com.bluewhale.pay.service.PayReconcileService;
import com.bluewhale.pay.vo.PayReconcileBatchVO;
import com.bluewhale.pay.vo.PayReconcileDiffVO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 对账管理接口（批次、差异、处置与手动触发）。
 *
 * <p>服务内路径 {@code /pay/v1/reconcile/**}；查询沿用 {@code sys:pay:reconcile}，
 * 处置与手动触发使用独立权限码。处置与触发均写审计日志。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping(path = "/pay/v{version}/reconcile", version = "1")
@RequiredArgsConstructor
public class PayReconcileController {

    /** 审计对象：差异 */
    private static final String TARGET_DIFF = "pay_reconcile_diff";

    /** 审计对象：批次 */
    private static final String TARGET_BATCH = "pay_reconcile_batch";

    private final PayReconcileService payReconcileService;

    /**
     * 对账批次分页
     *
     * @param request 查询条件
     * @return 分页结果
     */
    @GetMapping("/batch/page")
    @RequirePermission("sys:pay:reconcile")
    public Result<PageResult<PayReconcileBatchVO>> pageBatches(PayReconcileBatchQueryRequest request) {
        return Result.ok(payReconcileService.pageBatches(request));
    }

    /**
     * 对账差异分页
     *
     * @param request 查询条件
     * @return 分页结果
     */
    @GetMapping("/diff/page")
    @RequirePermission("sys:pay:reconcile")
    public Result<PageResult<PayReconcileDiffVO>> pageDiffs(PayReconcileDiffQueryRequest request) {
        return Result.ok(payReconcileService.pageDiffs(request));
    }

    /**
     * 处置一条对账差异（确认 / 补偿 / 挂起）
     *
     * @param id      差异 ID
     * @param request 处置动作与备注
     * @param servletRequest 当前请求（取处置人）
     * @return 处置后的差异
     */
    @PostMapping("/diff/{id:\\d+}/handle")
    @RequirePermission("sys:pay:reconcile:handle")
    @AuditLog(operation = "处置对账差异", type = OperateType.UPDATE, targetType = TARGET_DIFF, targetId = "#id")
    public Result<PayReconcileDiffVO> handle(@PathVariable Long id,
                                             @Valid @RequestBody PayReconcileHandleRequest request,
                                             HttpServletRequest servletRequest) {
        Long operatorId = UserContext.findUserId(servletRequest);
        return Result.ok(payReconcileService.handle(id, request, operatorId));
    }

    /**
     * 手动触发对账（缺省 T-1、全部渠道）
     *
     * @param request 业务日与渠道
     * @return 本次涉及的对账批次
     */
    @PostMapping("/batch")
    @RequirePermission("sys:pay:reconcile:run")
    @AuditLog(operation = "手动触发批量对账", type = OperateType.CREATE, targetType = TARGET_BATCH)
    public Result<List<PayReconcileBatchVO>> run(
            @RequestBody(required = false) PayReconcileRunRequest request) {
        return Result.ok(payReconcileService.run(request));
    }
}
