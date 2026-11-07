package com.bluewhale.mall.aftersale.domain.model;

import java.util.Locale;

/**
 * 售后类型。
 *
 * <p>两者流程同构（申请 → 审核 → 退款），差异只在审核时的检查项：
 * 退货退款要等商品退回才能完成，仅退款不需要。用类型字段区分而不是拆两张表，
 * 后续接 WMS 退货入库时也只对 {@code RETURN_REFUND} 生效。</p>
 *
 * @author BlueWhale
 */
public enum AfterSaleType {

    /** 仅退款（货未发或买家不要货） */
    REFUND_ONLY,

    /** 退货退款（需退回商品） */
    RETURN_REFUND;

    /**
     * 是否需要退回商品。
     *
     * @return 退货退款返回 true
     */
    public boolean requiresReturn() {
        return this == RETURN_REFUND;
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
