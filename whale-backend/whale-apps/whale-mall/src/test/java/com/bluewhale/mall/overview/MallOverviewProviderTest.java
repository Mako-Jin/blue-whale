package com.bluewhale.mall.overview;

import com.bluewhale.overview.OverviewContext;
import com.bluewhale.overview.model.OverviewAlert;
import com.bluewhale.overview.model.OverviewAlertLevel;
import com.bluewhale.overview.model.OverviewChart;
import com.bluewhale.overview.model.OverviewFormat;
import com.bluewhale.overview.model.OverviewKpi;
import com.bluewhale.overview.model.OverviewLink;
import com.bluewhale.overview.model.OverviewSnapshot;
import com.bluewhale.overview.model.OverviewTone;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link MallOverviewProvider} 单元测试：四张牌的口径提示、运营语气、
 * 订单七态柱图、告警分条、空市不误报。
 *
 * @author BlueWhale
 */
class MallOverviewProviderTest {

    private final MallOverviewStatsService statsService = mock(MallOverviewStatsService.class);

    private final MallOverviewProvider provider = new MallOverviewProvider(statsService);

    /**
     * 构造一份市舶账本。
     *
     * @param spuTotal            商品总数
     * @param spuOnSale           在售
     * @param orderPendingPayment 待支付
     * @param orderPaid           待发货
     * @param orderShipped        已发货
     * @param orderCompleted      已完成
     * @param orderCancelled      已取消
     * @param orderRefunding      退款中
     * @param orderRefunded       已退款
     * @param aftersaleCreated    待审核售后
     * @param aftersaleWaitReturn 待退货售后
     * @param stockZero           零库存 SKU
     * @return 统计快照
     */
    private static MallOverviewStats stats(long spuTotal, long spuOnSale,
            long orderPendingPayment, long orderPaid, long orderShipped, long orderCompleted,
            long orderCancelled, long orderRefunding, long orderRefunded,
            long aftersaleCreated, long aftersaleWaitReturn, long stockZero) {
        long orderTotal = orderPendingPayment + orderPaid + orderShipped + orderCompleted
                + orderCancelled + orderRefunding + orderRefunded;
        return new MallOverviewStats(spuTotal, spuOnSale, orderPendingPayment, orderPaid,
                orderShipped, orderCompleted, orderCancelled, orderRefunding, orderRefunded,
                orderTotal, aftersaleCreated, aftersaleWaitReturn, stockZero);
    }

    /**
     * 按 key 索引指标
     *
     * @param snapshot 快照
     * @return key → 指标
     */
    private static Map<String, OverviewKpi> kpiMap(OverviewSnapshot snapshot) {
        return snapshot.kpis().stream().collect(Collectors.toMap(OverviewKpi::key, kpi -> kpi));
    }

    @Test
    @DisplayName("元信息与权限码：复用 2D 订单台账同一权限码，不为 3D 单独发授权")
    void shouldExposeMetadata() {
        assertThat(provider.service()).isEqualTo("mall");
        assertThat(provider.nodeKey()).isEqualTo("shibosi-hall");
        assertThat(provider.title()).isEqualTo("市舶");
        assertThat(provider.subtitle()).isEqualTo("商城运营");
        assertThat(provider.permissionCode()).isEqualTo("sys:mall:order:list");
    }

    @Test
    @DisplayName("租户 ID 从上下文透传给统计服务：缺租户由统计服务抛（另一例），provider 不吞")
    void shouldPassTenantIdThrough() {
        when(statsService.snapshot(9001L)).thenReturn(stats(30L, 18L,
                8L, 5L, 12L, 60L, 10L, 3L, 2L, 4L, 2L, 6L));

        provider.build(OverviewContext.of(42L, 9001L, null, null));

        verify(statsService).snapshot(9001L);
    }

    @Test
    @DisplayName("四张牌：待发货/售后/零库存有账就黄，售后再分成两段说")
    void shouldBuildFourKpisWithTones() {
        when(statsService.snapshot(9001L)).thenReturn(stats(30L, 18L,
                8L, 5L, 12L, 60L, 10L, 3L, 2L, 4L, 2L, 6L));

        Map<String, OverviewKpi> kpis = kpiMap(provider.build(context()));

        assertThat(kpis.get("on-sale").value()).isEqualTo(18d);
        assertThat(kpis.get("on-sale").hint()).isEqualTo("未上架 12 件");
        assertThat(kpis.get("paid").tone()).isEqualTo(OverviewTone.WARN);
        assertThat(kpis.get("paid").hint()).isEqualTo("已收款未发货，要人去发");
        assertThat(kpis.get("aftersale").value()).isEqualTo(6d);
        assertThat(kpis.get("aftersale").hint()).isEqualTo("待审核 4 · 待退货 2");
        assertThat(kpis.get("stock-zero").tone()).isEqualTo(OverviewTone.WARN);
        assertThat(kpis.get("stock-zero").hint()).isEqualTo("有规格缺货，下单会失败");
    }

    @Test
    @DisplayName("清空状态：三张运营牌全绿，商品牌说明没上过货")
    void shouldToneClearedState() {
        when(statsService.snapshot(9001L)).thenReturn(stats(0L, 0L,
                0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L));

        Map<String, OverviewKpi> kpis = kpiMap(provider.build(context()));

        assertThat(kpis.get("paid").tone()).isEqualTo(OverviewTone.GOOD);
        assertThat(kpis.get("paid").hint()).isEqualTo("没有压单");
        assertThat(kpis.get("aftersale").tone()).isEqualTo(OverviewTone.GOOD);
        assertThat(kpis.get("aftersale").hint()).isEqualTo("售后没有积压");
        assertThat(kpis.get("stock-zero").tone()).isEqualTo(OverviewTone.GOOD);
        assertThat(kpis.get("on-sale").hint()).isEqualTo("还没有上过货");
    }

    @Test
    @DisplayName("订单七态柱图：固定顺序含退款两态，格式是计数")
    void shouldChartOrderStatusDistribution() {
        when(statsService.snapshot(9001L)).thenReturn(stats(30L, 18L,
                8L, 5L, 12L, 60L, 10L, 3L, 2L, 4L, 2L, 6L));

        OverviewChart chart = provider.build(context()).charts().get(0);

        assertThat(chart.type()).isEqualTo("bar");
        assertThat(chart.format()).isEqualTo(OverviewFormat.COUNT);
        assertThat(chart.categories()).containsExactly("待支付", "待发货", "已发货", "已完成",
                "已取消", "退款中", "已退款");
        assertThat(chart.series()).hasSize(1);
        assertThat(chart.series().get(0).values()).containsExactly(8d, 5d, 12d, 60d, 10d, 3d, 2d);
    }

    @Test
    @DisplayName("入口：订单台账为主账，商品台账并列，各带自己的权限码")
    void shouldLinkOrderAndProductPages() {
        when(statsService.snapshot(9001L)).thenReturn(stats(30L, 18L,
                8L, 5L, 12L, 60L, 10L, 3L, 2L, 4L, 2L, 6L));

        List<OverviewLink> links = provider.build(context()).links();

        assertThat(links).extracting(OverviewLink::path)
                .containsExactly("/mall/v1/order/page", "/mall/v1/product/page");
        assertThat(links).extracting(OverviewLink::permission)
                .containsExactly("sys:mall:order:list", "sys:mall:product:list");
    }

    @Test
    @DisplayName("告警：待发货/退款中/待审核售后/零库存是黄灯；待退货不是运营欠账只说明")
    void shouldSeparateWarningsFromInfo() {
        when(statsService.snapshot(9001L)).thenReturn(stats(30L, 18L,
                8L, 5L, 12L, 60L, 10L, 3L, 2L, 4L, 2L, 6L));

        OverviewSnapshot snapshot = provider.build(context());

        List<String> warnings = snapshot.alerts().stream()
                .filter(alert -> alert.level() == OverviewAlertLevel.WARNING)
                .map(OverviewAlert::text)
                .toList();
        List<String> infos = snapshot.alerts().stream()
                .filter(alert -> alert.level() == OverviewAlertLevel.INFO)
                .map(OverviewAlert::text)
                .toList();
        assertThat(warnings).anyMatch(text -> text.contains("5 单已收款未发货"))
                .anyMatch(text -> text.contains("3 单退款进行中"))
                .anyMatch(text -> text.contains("4 单售后待审核"))
                .anyMatch(text -> text.contains("6 个 SKU 零库存"));
        assertThat(infos).anyMatch(text -> text.contains("2 单售后待退货"));
    }

    @Test
    @DisplayName("只欠待退货（审核都过了）：不出黄灯，只提示等买家寄回")
    void shouldNotBlameWaitReturnOnly() {
        when(statsService.snapshot(9001L)).thenReturn(stats(30L, 18L,
                0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 3L, 0L));

        OverviewSnapshot snapshot = provider.build(context());

        assertThat(snapshot.alerts())
                .filteredOn(alert -> alert.level() == OverviewAlertLevel.WARNING)
                .isEmpty();
        assertThat(snapshot.alerts()).extracting(OverviewAlert::text)
                .anyMatch(text -> text.contains("3 单售后待退货"));
    }

    @Test
    @DisplayName("空市：没有上过货也没成交过一单，只说明不出黄灯")
    void shouldNotBlameEmptyMarket() {
        when(statsService.snapshot(9001L)).thenReturn(stats(0L, 0L,
                0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L));

        OverviewSnapshot snapshot = provider.build(context());

        assertThat(snapshot.alerts())
                .filteredOn(alert -> alert.level() == OverviewAlertLevel.WARNING)
                .isEmpty();
        assertThat(snapshot.alerts()).extracting(OverviewAlert::text)
                .anyMatch(text -> text.contains("市舶还是空的"));
    }

    /**
     * 上下文：总控聚合时会显式传租户，屏风直连时由 SDK 控制器从请求头取。
     *
     * @return 概览上下文
     */
    private static OverviewContext context() {
        return OverviewContext.of(42L, 9001L, null, null);
    }
}
