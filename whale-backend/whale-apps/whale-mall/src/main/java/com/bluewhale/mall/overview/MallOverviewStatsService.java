package com.bluewhale.mall.overview;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bluewhale.mall.aftersale.infrastructure.persistence.entity.AfterSaleEntity;
import com.bluewhale.mall.aftersale.infrastructure.persistence.mapper.AfterSaleMapper;
import com.bluewhale.mall.catalog.infrastructure.persistence.entity.SkuStockEntity;
import com.bluewhale.mall.catalog.infrastructure.persistence.entity.SpuEntity;
import com.bluewhale.mall.catalog.infrastructure.persistence.mapper.SkuStockMapper;
import com.bluewhale.mall.catalog.infrastructure.persistence.mapper.SpuMapper;
import com.bluewhale.mall.order.infrastructure.persistence.entity.OrderEntity;
import com.bluewhale.mall.order.infrastructure.persistence.mapper.OrderMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 市舶坊（MALL）统计口径：商品、订单、售后、库存四本账。
 *
 * <p><b>口径只在这里定义一次。</b>订单七态取枚举名 {@link com.bluewhale.mall.order.domain.model.OrderStatus}：
 * 待支付 / 已支付（待发货）/ 已发货 / 已完成 / 已取消 / 退款中 / 已退款；
 * 商品三态取 {@link com.bluewhale.mall.catalog.domain.model.SpuStatus}（草稿 / 在售 / 下架）；
 * 售后只数两段<b>未结</b>账：待审核（{@code CREATED}）与待退货（{@code WAIT_RETURN}）——
 * 已退款 / 已换货 / 已驳回 / 已撤销是终态，出了结论的欠账不挂在待办里。</p>
 *
 * <p><b>零库存按 SKU 数不按商品数。</b>一个 SPU 常挂多个规格，缺的往往只是一个尺码；
 * 按商品数会把"红了一半"说成"还有货"，按 SKU 数才与下单失败直接对齐——
 * 库存扣减与预占都在 SKU 一级做（{@code SkuStockMapper#reserve}），账也在 SKU 一级看。</p>
 *
 * <p><b>不写 GROUP BY，按状态各 count 一次。</b>状态码值固定且都有索引，
 * 十几次索引 count 比一次聚合直观；更重要的是不走自定义 SQL 就不用自己拼
 * {@code tenant_id} 条件——租户隔离由 MyBatis-Plus 插件保证，一旦漏了就是
 * 跨租户串数，且不会报错（营造成批的老教训）。</p>
 *
 * <p><b>租户 ID 必须显式传入。</b>概览 provider 在独立线程里执行，拿不到
 * 请求线程的租户上下文；传 {@code null} 直接抛——总控聚合时会显式传租户，
 * 缺租户说明调用方配错了，早点降级比在屏风上显示一个错误的 0 强。</p>
 *
 * <p>统计只读，不写库：概览被前端按 30s 轮询，任何写操作都会变成"每 30 秒写一次"。</p>
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class MallOverviewStatsService {

    /** 商品状态：在售（只有此状态能下单） */
    private static final String SPU_ON_SALE = "ON_SALE";

    /** 订单状态：待支付 */
    private static final String ORDER_PENDING_PAYMENT = "PENDING_PAYMENT";

    /** 订单状态：已支付（待发货） */
    private static final String ORDER_PAID = "PAID";

    /** 订单状态：已发货 */
    private static final String ORDER_SHIPPED = "SHIPPED";

    /** 订单状态：已完成 */
    private static final String ORDER_COMPLETED = "COMPLETED";

    /** 订单状态：已取消 */
    private static final String ORDER_CANCELLED = "CANCELLED";

    /** 订单状态：退款中（整单退款进行中，待支付侧回调） */
    private static final String ORDER_REFUNDING = "REFUNDING";

    /** 订单状态：已退款（终态） */
    private static final String ORDER_REFUNDED = "REFUNDED";

    /** 售后状态：待审核 */
    private static final String AFTER_SALE_CREATED = "CREATED";

    /** 售后状态：待退货（审核通过，等买家寄回商品） */
    private static final String AFTER_SALE_WAIT_RETURN = "WAIT_RETURN";

    /** 零库存阈值：可售数等于此值记为缺货 */
    private static final int STOCK_ZERO = 0;

    private final SpuMapper spuMapper;

    private final OrderMapper orderMapper;

    private final AfterSaleMapper afterSaleMapper;

    private final SkuStockMapper skuStockMapper;

    /**
     * 取一次统计快照。
     *
     * @param tenantId 租户 ID（必填）
     * @return 统计快照
     */
    public MallOverviewStats snapshot(Long tenantId) {
        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId is required for mall overview stats");
        }
        long spuTotal = count(spuMapper.selectCount(null));
        long spuOnSale = count(spuMapper.selectCount(new LambdaQueryWrapper<SpuEntity>()
                .eq(SpuEntity::getStatus, SPU_ON_SALE)));
        long orderTotal = count(orderMapper.selectCount(null));
        long orderPendingPayment = count(orderMapper.selectCount(new LambdaQueryWrapper<OrderEntity>()
                .eq(OrderEntity::getStatus, ORDER_PENDING_PAYMENT)));
        long orderPaid = count(orderMapper.selectCount(new LambdaQueryWrapper<OrderEntity>()
                .eq(OrderEntity::getStatus, ORDER_PAID)));
        long orderShipped = count(orderMapper.selectCount(new LambdaQueryWrapper<OrderEntity>()
                .eq(OrderEntity::getStatus, ORDER_SHIPPED)));
        long orderCompleted = count(orderMapper.selectCount(new LambdaQueryWrapper<OrderEntity>()
                .eq(OrderEntity::getStatus, ORDER_COMPLETED)));
        long orderCancelled = count(orderMapper.selectCount(new LambdaQueryWrapper<OrderEntity>()
                .eq(OrderEntity::getStatus, ORDER_CANCELLED)));
        long orderRefunding = count(orderMapper.selectCount(new LambdaQueryWrapper<OrderEntity>()
                .eq(OrderEntity::getStatus, ORDER_REFUNDING)));
        long orderRefunded = count(orderMapper.selectCount(new LambdaQueryWrapper<OrderEntity>()
                .eq(OrderEntity::getStatus, ORDER_REFUNDED)));
        long aftersaleCreated = count(afterSaleMapper.selectCount(new LambdaQueryWrapper<AfterSaleEntity>()
                .eq(AfterSaleEntity::getStatus, AFTER_SALE_CREATED)));
        long aftersaleWaitReturn = count(afterSaleMapper.selectCount(new LambdaQueryWrapper<AfterSaleEntity>()
                .eq(AfterSaleEntity::getStatus, AFTER_SALE_WAIT_RETURN)));
        long stockZero = count(skuStockMapper.selectCount(new LambdaQueryWrapper<SkuStockEntity>()
                .eq(SkuStockEntity::getAvailableQty, STOCK_ZERO)));
        return new MallOverviewStats(spuTotal, spuOnSale, orderPendingPayment, orderPaid,
                orderShipped, orderCompleted, orderCancelled, orderRefunding, orderRefunded,
                orderTotal, aftersaleCreated, aftersaleWaitReturn, stockZero);
    }

    /**
     * 计数兜底：{@code selectCount} 在极端情况下返回 {@code null}，直接拆箱就是 NPE。
     *
     * @param value 计数值，可空
     * @return 非空计数
     */
    private static long count(Long value) {
        return value == null ? 0L : value;
    }
}
