package com.bluewhale.mall.shipment.domain.model;

import java.util.Locale;

/**
 * 发货单状态。
 *
 * <pre>
 * SHIPPED ──deliver──> DELIVERED
 *    └────cancel─────> CANCELLED
 * </pre>
 *
 * <p>没有草稿态。草稿意味着「占没占可发数量、占没占运单号」要另定一套规则，
 * 而运单号一旦向承运商申请就已经是消耗掉的资源；运营需要「先建单后打印面单」时，
 * 属于电子面单对接，是独立一波的量。</p>
 *
 * <p>只有 {@code SHIPPED} 能向外走：已签收不能再作废（货已经到买家手里，
 * 该走售后而不是把发货单抹掉），已作废不能再签收（数据错乱的信号，宁可报错）。</p>
 *
 * @author BlueWhale
 */
public enum ShipmentStatus {

    /** 已发货（在途） */
    SHIPPED,

    /** 已签收 */
    DELIVERED,

    /** 已作废 */
    CANCELLED;

    /**
     * 是否可签收。
     *
     * @return 在途返回 true
     */
    public boolean canDeliver() {
        return this == SHIPPED;
    }

    /**
     * 是否可作废。
     *
     * @return 在途返回 true
     */
    public boolean canCancel() {
        return this == SHIPPED;
    }

    /**
     * 是否为有效单（未作废）。
     *
     * <p>「有效」是相对作废而言：作废的单不占可发数量、也不参与「是否全签收」的判定。</p>
     *
     * @return 未作废返回 true
     */
    public boolean effective() {
        return this != CANCELLED;
    }

    /**
     * 解析状态文本。
     *
     * @param value 文本
     * @return 状态；无法识别时返回 null
     */
    public static ShipmentStatus parse(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return ShipmentStatus.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
