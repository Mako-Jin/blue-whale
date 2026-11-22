package com.bluewhale.mall.overview;

/**
 * 市舶坊（MALL）统计快照：商品、订单、售后、库存四本账。
 *
 * <p>全部为租户内计数，一次 {@code snapshot} 调用取齐；字段只读，
 * 语义见 {@link MallOverviewStatsService}。</p>
 *
 * @param spuTotal            商品总数（全部状态）
 * @param spuOnSale           在售商品（{@code ON_SALE}）
 * @param orderPendingPayment 待支付订单（{@code PENDING_PAYMENT}）
 * @param orderPaid           已支付待发货订单（{@code PAID}，运营要盯的头等事）
 * @param orderShipped        已发货订单（{@code SHIPPED}）
 * @param orderCompleted      已完成订单（{@code COMPLETED}）
 * @param orderCancelled      已取消订单（{@code CANCELLED}）
 * @param orderRefunding      退款中订单（{@code REFUNDING}，钱卡在支付侧）
 * @param orderRefunded       已退款订单（{@code REFUNDED}）
 * @param orderTotal          订单总数
 * @param aftersaleCreated    待审核售后单（{@code CREATED}）
 * @param aftersaleWaitReturn 待退货售后单（{@code WAIT_RETURN}，审核通过等买家寄回）
 * @param stockZero           零库存 SKU 数（{@code available_qty = 0}）
 * @author BlueWhale
 */
public record MallOverviewStats(
        long spuTotal,
        long spuOnSale,
        long orderPendingPayment,
        long orderPaid,
        long orderShipped,
        long orderCompleted,
        long orderCancelled,
        long orderRefunding,
        long orderRefunded,
        long orderTotal,
        long aftersaleCreated,
        long aftersaleWaitReturn,
        long stockZero) {
}
