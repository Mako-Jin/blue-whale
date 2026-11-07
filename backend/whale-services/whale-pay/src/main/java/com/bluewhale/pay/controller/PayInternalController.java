package com.bluewhale.pay.controller;

import com.bluewhale.common.core.result.Result;
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
 * 支付内网接口（仅服务间直连，网关侧对 {@code /pay/internal/**} 统一 403）。
 *
 * <p>不做用户权限校验（服务间无用户态）；租户上下文由调用方以
 * {@code X-Tenant-Id} 头传入，经 starter 的 {@code TenantContextFilter} 还原。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping("/pay/internal")
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
        return Result.ok(payOrderService.cancelByOrderNo(orderNo));
    }

    /**
     * 创建退款单并执行（成功后投递 REFUNDED 业务通知）。
     *
     * @param request 退款请求
     * @return 退款单
     */
    @PostMapping("/refund")
    public Result<PayRefundVO> refund(@Valid @RequestBody PayInternalRefundRequest request) {
        return Result.ok(payRefundService.refundByNo(request.getPayOrderNo(),
                request.getAmount(), request.getReason()));
    }
}
