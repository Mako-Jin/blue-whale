package com.bluewhale.pay.mapper.stat;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 时间窗内的收款统计（按支付时间）。
 *
 * <p>只统计 {@code status IN ('PAID','REFUNDED')}：待支付与已取消的单不算收款，
 * 已退款单的 {@code amount} 仍是当初收进来的钱——退款额另算，
 * 把两者相减会让"今日收款"随退款变动，而收款是既成事实。</p>
 *
 * @author BlueWhale
 */
@Data
public class PayPaidStatPo {

    /** 收款笔数 */
    private Long paidOrders;

    /** 收款金额（原币种求和，当前只做 CNY） */
    private BigDecimal paidAmount;
}
