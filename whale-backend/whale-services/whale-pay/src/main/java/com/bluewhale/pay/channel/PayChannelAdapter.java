package com.bluewhale.pay.channel;

import com.bluewhale.pay.po.PayOrderPo;

import java.math.BigDecimal;

/**
 * 支付渠道适配器 SPI。
 *
 * <p>每个真实支付渠道实现本接口并注册为 Spring Bean；{@link PayChannelGateway}
 * 按渠道号路由。新增渠道 = 新增实现类 + 在 {@code whale.pay.allowed-channels}
 * 中登记渠道号，业务代码零改动。</p>
 *
 * @author BlueWhale
 */
public interface PayChannelAdapter {

    /**
     * 渠道号（大写，如 MOCK / ALIPAY / WECHAT）。
     *
     * @return 渠道号
     */
    String channel();

    /**
     * 渠道收单。
     *
     * @param order 支付单（状态已抢占为 PAID）
     * @return 渠道结果（渠道流水号 + 渠道状态）
     */
    PayChannelResult pay(PayOrderPo order);

    /**
     * 渠道退款。
     *
     * @param order        支付单
     * @param refundNo     退款单号（渠道侧幂等键）
     * @param refundAmount 本次退款金额
     * @return 渠道结果
     */
    PayChannelResult refund(PayOrderPo order, String refundNo, BigDecimal refundAmount);

    /**
     * 渠道侧订单状态查询（对账用）。
     *
     * @param order 支付单
     * @return 渠道状态码
     */
    String queryStatus(PayOrderPo order);
}
