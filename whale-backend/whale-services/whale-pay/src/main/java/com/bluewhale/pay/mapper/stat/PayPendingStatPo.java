package com.bluewhale.pay.mapper.stat;

import lombok.Data;

/**
 * 待支付单统计（含超时未支付）。
 *
 * <p>"待支付"本身不是问题，放着太久才是问题：超过阈值的单多半是渠道回调丢了，
 * 用户付了钱而系统还停在 CREATED。两个数一次查回来，避免两次全表条件。</p>
 *
 * @author BlueWhale
 */
@Data
public class PayPendingStatPo {

    /** 当前待支付单总数 */
    private Long pendingOrders;

    /** 其中创建时间早于阈值的（回调可能丢失） */
    private Long staleOrders;
}
