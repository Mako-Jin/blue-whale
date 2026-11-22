package com.bluewhale.pay.client;

/**
 * 支付内网客户端。
 *
 * <p>自动注入 {@code X-Internal-Token}、{@code X-Tenant-Id}、{@code X-Trace-Id}；
 * 幂等与重试策略由实现统一约束，业务方不再手写 {@code RestClient} 与 {@code Result} 解析。
 * 失败统一抛 {@link PayClientException}。</p>
 *
 * @author BlueWhale
 */
public interface WhalePayClient {

    /**
     * 创建支付单（服务端按 {@code orderNo} 幂等，可安全重试）。
     *
     * @param command 创建命令
     * @return 支付单
     */
    PayOrderView create(PayCreateCommand command);

    /**
     * 发起支付。
     *
     * <p>非幂等：渠道侧可能已受理，超时后是否重试必须由业务确认，SDK 不做自动重试。</p>
     *
     * @param orderNo 支付单号
     * @return 支付单
     */
    PayOrderView pay(String orderNo);

    /**
     * 取消支付单（仅待支付可取消，已取消幂等成功）。
     *
     * @param orderNo 支付单号
     * @return 支付单
     */
    PayOrderView cancel(String orderNo);

    /**
     * 按支付单号查询。
     *
     * @param orderNo 支付单号
     * @return 支付单
     */
    PayOrderView getByOrderNo(String orderNo);

    /**
     * 退款（服务端按 {@code refundNo} 幂等，可安全重试）。
     *
     * @param command 退款命令
     * @return 退款单
     */
    PayRefundView refund(PayRefundCommand command);
}
