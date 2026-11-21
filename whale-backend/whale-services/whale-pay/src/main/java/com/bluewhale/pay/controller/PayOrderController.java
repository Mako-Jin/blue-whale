package com.bluewhale.pay.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.pay.dto.PayOrderCreateRequest;
import com.bluewhale.pay.dto.PayOrderQueryRequest;
import com.bluewhale.pay.service.PayOrderService;
import com.bluewhale.pay.vo.PayOrderVO;
import com.bluewhale.pay.vo.PayReconcileVO;
import com.bluewhale.pay.vo.PayTransactionVO;
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
 * 支付单管理接口
 *
 * <p>服务内路径 {@code /pay/order/**}；经网关按 {@code Path=/pay/**} 规则转发，
 * 外部路径与本文档一致。全部接口返回统一响应体 {@link Result}，受
 * {@code @RequirePermission("sys:pay:*")} 保护（由网关权限码头
 * {@code X-Permission-Codes} + starter {@code RequirePermissionInterceptor} 判定），
 * 写操作均标注 {@link AuditLog} 留痕。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping("/pay/order")
@RequiredArgsConstructor
public class PayOrderController {

    /** 审计操作对象类型 */
    private static final String TARGET_TYPE = "pay_order";

    private final PayOrderService payOrderService;

    /**
     * 分页查询支付单
     *
     * @param request 查询条件
     * @return 分页结果
     */
    @GetMapping("/page")
    @RequirePermission("sys:pay:list")
    public Result<PageResult<PayOrderVO>> page(PayOrderQueryRequest request) {
        return Result.ok(payOrderService.page(request));
    }

    /**
     * 支付单详情
     *
     * @param id 支付单ID
     * @return 支付单视图对象
     */
    @GetMapping("/{id:\\d+}")
    @RequirePermission("sys:pay:list")
    public Result<PayOrderVO> detail(@PathVariable Long id) {
        return Result.ok(payOrderService.getDetail(id));
    }

    /**
     * 创建支付单
     *
     * @param request 创建请求
     * @return 创建后的支付单视图对象
     */
    @PostMapping
    @RequirePermission("sys:pay:add")
    @AuditLog(operation = "创建支付单", type = OperateType.CREATE, targetType = TARGET_TYPE)
    public Result<PayOrderVO> create(@Valid @RequestBody PayOrderCreateRequest request) {
        return Result.ok(payOrderService.create(request));
    }

    /**
     * 支付支付单（CREATED → PAID）
     *
     * @param id 支付单ID
     * @return 支付后的支付单视图对象
     */
    @PostMapping("/{id}/pay")
    @RequirePermission("sys:pay:pay")
    @AuditLog(operation = "支付支付单", type = OperateType.UPDATE, targetType = TARGET_TYPE, targetId = "#id")
    public Result<PayOrderVO> pay(@PathVariable Long id) {
        return Result.ok(payOrderService.pay(id));
    }

    /**
     * 取消支付单（CREATED → CANCELLED）
     *
     * @param id 支付单ID
     * @return 取消后的支付单视图对象
     */
    @PostMapping("/{id}/cancel")
    @RequirePermission("sys:pay:cancel")
    @AuditLog(operation = "取消支付单", type = OperateType.UPDATE, targetType = TARGET_TYPE, targetId = "#id")
    public Result<PayOrderVO> cancel(@PathVariable Long id) {
        return Result.ok(payOrderService.cancel(id));
    }

    /**
     * 退款支付单（PAID → REFUNDED，当前为全额退款）
     *
     * @param id 支付单ID
     * @return 退款后的支付单视图对象
     */
    @PostMapping("/{id}/refund")
    @RequirePermission("sys:pay:refund")
    @AuditLog(operation = "退款支付单", type = OperateType.UPDATE, targetType = TARGET_TYPE, targetId = "#id")
    public Result<PayOrderVO> refund(@PathVariable Long id) {
        return Result.ok(payOrderService.refund(id));
    }

    /**
     * 支付单对账：比对本地状态与渠道状态
     *
     * @param id 支付单ID
     * @return 对账结果
     */
    @GetMapping("/{id}/reconcile")
    @RequirePermission("sys:pay:reconcile")
    public Result<PayReconcileVO> reconcile(@PathVariable Long id) {
        return Result.ok(payOrderService.reconcile(id));
    }

    /**
     * 支付/退款流水列表
     *
     * @param id 支付单ID
     * @return 流水列表
     */
    @GetMapping("/{id}/transactions")
    @RequirePermission("sys:pay:list")
    public Result<List<PayTransactionVO>> transactions(@PathVariable Long id) {
        return Result.ok(payOrderService.listTransactions(id));
    }
}
