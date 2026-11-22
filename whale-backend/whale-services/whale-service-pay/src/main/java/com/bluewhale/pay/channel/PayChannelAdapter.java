package com.bluewhale.pay.channel;

import com.bluewhale.pay.po.PayOrderPo;

import java.math.BigDecimal;
import java.util.Map;

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
     * <p>正常返回即代表渠道已接单：{@link PayChannelStatus#SUCCESS} 可立即落账，
     * {@link PayChannelStatus#ACCEPTED} 表示结果待回调确认；渠道明确失败直接抛异常。</p>
     *
     * @param order 支付单（状态已抢占为 PAYING）
     * @return 渠道结果（渠道流水号 + 结果状态）
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

    /**
     * 解析渠道异步回调报文并验签。
     *
     * <p>验签必须在本方法内完成：签名不合法、报文结构非法直接抛业务异常，
     * 由回调服务落 {@code REJECTED/SIGN_INVALID} 并返回渠道失败体。
     * 回调请求不携带可信租户，租户由 {@code outTradeNo} 反查支付单获得。</p>
     *
     * @param rawBody 原始报文
     * @param headers 原始请求头（只读）
     * @return 统一回调请求
     */
    ChannelCallbackRequest parseCallback(String rawBody, Map<String, String> headers);

    /**
     * 渠道要求的成功响应体（HTTP 恒为 200，渠道按响应体判断是否重发）。
     *
     * @return 成功响应体
     */
    default String successResponse() {
        return "SUCCESS";
    }

    /**
     * 渠道要求的失败响应体（触发渠道重发）。
     *
     * @return 失败响应体
     */
    default String failureResponse() {
        return "FAIL";
    }
}
