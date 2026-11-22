package com.bluewhale.pay.controller;

import com.bluewhale.common.core.result.Result;
import com.bluewhale.common.tenant.TenantContext;
import com.bluewhale.pay.dto.PayInternalCreateRequest;
import com.bluewhale.pay.dto.PayInternalRefundRequest;
import com.bluewhale.pay.service.PayOrderService;
import com.bluewhale.pay.service.PayRefundService;
import com.bluewhale.pay.vo.PayOrderVO;
import com.bluewhale.pay.vo.PayRefundVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 支付内网接口（仅服务间直连，网关侧对 {@code /pay/v1/internal/**} 统一 403）。
 *
 * <p>入站安全分两层：</p>
 * <ol>
 *   <li><b>服务间令牌</b>：starter 的 {@code InternalTokenFilter} 对 {@code /pay/v1/internal/**}
 *       校验 {@code X-Internal-Token}，无令牌直接 403；非 dev 环境未配置令牌时
 *       {@code InternalTokenGuard} 拒绝启动。</li>
 *   <li><b>租户上下文</b>：{@code X-Tenant-Id} 由 {@code TenantContextFilter} 还原，
 *       本控制器每个入口显式 {@link TenantContext#requireId()}，缺失即 400，
 *       不允许无租户写入。</li>
 * </ol>
 *
 * <p>残余风险：当前令牌为服务间共享令牌，不能把租户与调用方身份强绑定；
 * 需要更强隔离时应改为按服务/租户签发令牌，并配合 K8s NetworkPolicy 限制直连端口。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping(path = "/pay/v{version}/internal", version = "1")
@RequiredArgsConstructor
public class PayInternalController {

    private final PayOrderService payOrderService;

    private final PayRefundService payRefundService;

    /**
     * 创建支付单（按 orderNo 幂等：已存在直接返回原单）。
     *
     * @param request 创建请求
     * @return 支付单
     */
    @PostMapping("/order")
    public Result<PayOrderVO> create(@Valid @RequestBody PayInternalCreateRequest request) {
        requireTenant();
        return Result.ok(payOrderService.createInternal(request));
    }

    /**
     * 按支付单号查询。
     *
     * @param orderNo 支付单号
     * @return 支付单
     */
    @GetMapping("/order/{orderNo}")
    public Result<PayOrderVO> getByOrderNo(@PathVariable String orderNo) {
        requireTenant();
        return Result.ok(payOrderService.getByOrderNo(orderNo));
    }

    /**
     * 发起支付（MOCK 即时成功；成功后投递 PAID 业务通知）。
     *
     * @param orderNo 支付单号
     * @return 支付单
     */
    @PostMapping("/order/{orderNo}/pay")
    public Result<PayOrderVO> pay(@PathVariable String orderNo) {
        requireTenant();
        return Result.ok(payOrderService.payByOrderNo(orderNo));
    }

    /**
     * 取消支付单（仅 CREATED 可取消；已取消幂等成功）。
     *
     * @param orderNo 支付单号
     * @return 支付单
     */
    @PostMapping("/order/{orderNo}/cancel")
    public Result<PayOrderVO> cancel(@PathVariable String orderNo) {
        requireTenant();
        return Result.ok(payOrderService.cancelByOrderNo(orderNo));
    }

    /**
     * 创建退款单并执行（成功后投递 REFUNDED 业务通知）。
     *
     * <p>{@code refundNo} 是调用方生成的幂等键，同号重试返回原退款单。</p>
     *
     * @param request 退款请求
     * @return 退款单
     */
    @PostMapping("/refund")
    public Result<PayRefundVO> refund(@Valid @RequestBody PayInternalRefundRequest request) {
        requireTenant();
        return Result.ok(payRefundService.refundByNo(request.getPayOrderNo(), request.getRefundNo(),
                request.getAmount(), request.getReason()));
    }

    /**
     * 校验租户上下文存在（缺失直接 400）。
     */
    private static void requireTenant() {
        TenantContext.requireId();
    }
}
