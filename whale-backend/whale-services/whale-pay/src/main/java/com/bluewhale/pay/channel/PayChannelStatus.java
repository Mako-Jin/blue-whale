package com.bluewhale.pay.channel;

/**
 * 渠道动作结果状态。
 *
 * <p>真实渠道（支付宝/微信）的同步返回通常只代表「已受理」，最终结果以异步回调或
 * 主动查询为准；因此渠道结果必须能区分「即时成功」与「受理中」，否则支付单会被
 * 错误地推进为 {@code PAID}。</p>
 *
 * <p>渠道明确失败不走本枚举：适配器直接抛出异常，由调用方回退抢占状态，
 * 避免调用方遗漏对失败分支的处理。</p>
 *
 * @author BlueWhale
 */
public enum PayChannelStatus {

    /** 渠道即时成功（mock / 同步返回成功） */
    SUCCESS,

    /** 渠道已受理，最终结果待回调或主动查询 */
    ACCEPTED
}
