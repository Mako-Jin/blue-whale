package com.bluewhale.pay.service;

import com.bluewhale.pay.channel.PayChannelResult;
import com.bluewhale.pay.po.PayOrderPo;
import com.bluewhale.pay.po.PayRefundPo;

import java.math.BigDecimal;

/**
 * 支付结果落账服务：支付成功与退款成功的唯一状态推进入口。
 *
 * <p>同步支付、渠道回调（后续）、停留单对账三条链路都必须经由本服务推进状态，
 * 保证「条件更新抢占 → 写流水 → 投递通知」只有一份实现，避免各链路各写一套导致
 * 重复流水或漏发通知。</p>
 *
 * @author BlueWhale
 */
public interface PaySettlementService {

    /**
     * 将支付单推进为已支付并记账。
     *
     * <p>只接受 {@link com.bluewhale.pay.channel.PayChannelStatus#SUCCESS} 的渠道结果；
     * 条件更新覆盖 {@code CREATED} 与 {@code PAYING}，未命中说明已被并发事务推进，
     * 返回 {@code false} 且不重复写流水与通知。</p>
     *
     * @param order  支付单
     * @param result 渠道结果
     * @return 是否由本次调用完成落账
     */
    boolean markPaid(PayOrderPo order, PayChannelResult result);

    /**
     * 将退款单推进为成功并记账。
     *
     * <p>先原子累计支付单退款额（0 行即状态不允许或超额，整段回滚），
     * 再置退款单成功、写退款流水并投递退款通知。</p>
     *
     * @param order  支付单
     * @param refund 退款单
     * @param amount 本次退款金额
     * @param result 渠道结果
     */
    void markRefunded(PayOrderPo order, PayRefundPo refund, BigDecimal amount, PayChannelResult result);
}
