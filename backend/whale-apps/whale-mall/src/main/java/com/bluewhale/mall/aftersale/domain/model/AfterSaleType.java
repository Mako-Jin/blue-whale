package com.bluewhale.mall.aftersale.domain.model;

import java.util.Locale;

/**
 * 售后类型。
 *
 * <p>三种类型的差异只在「是否退回商品」与「是否退钱」：</p>
 * <ul>
 *   <li>{@code REFUND_ONLY}：不退货，审核通过立即退款。</li>
 *   <li>{@code RETURN_REFUND}：退货 + 退款，审核通过先进入待退货，确认收货后才退款。</li>
 *   <li>{@code EXCHANGE}：退货 + 补发新货，不退钱——不占退款额度，
 *       防超占靠「同一订单行进行中换货单查重」。</li>
 * </ul>
 *
 * <p>用类型字段区分而不是拆多张表：三者的申请、审核、图片、明细完全同构，
 * 差异只在审核后的走向，拆表只会把同一套额度逻辑复制三份各修各的 bug。</p>
 *
 * @author BlueWhale
 */
public enum AfterSaleType {

    /** 仅退款（货未发或买家不要货） */
    REFUND_ONLY,

    /** 退货退款（需退回商品） */
    RETURN_REFUND,

    /** 换货（退回原货、补发新货，不退钱） */
    EXCHANGE;

    /**
     * 是否需要退回商品。
     *
     * @return 退货退款或换货返回 true
     */
    public boolean requiresReturn() {
        return this == RETURN_REFUND || this == EXCHANGE;
    }

    /**
     * 是否为换货（不退钱，不占退款额度）。
     *
     * @return 换货返回 true
     */
    public boolean isExchange() {
        return this == EXCHANGE;
    }

    /**
     * 解析类型文本。
     *
     * @param value 文本
     * @return 类型；无法识别时返回 null（由调用方判定为非法入参）
     */
    public static AfterSaleType parse(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
