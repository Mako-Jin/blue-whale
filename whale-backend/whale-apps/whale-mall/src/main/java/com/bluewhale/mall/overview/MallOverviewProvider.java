package com.bluewhale.mall.overview;

import com.bluewhale.overview.OverviewContext;
import com.bluewhale.overview.OverviewProvider;
import com.bluewhale.overview.model.OverviewAlertLevel;
import com.bluewhale.overview.model.OverviewFormat;
import com.bluewhale.overview.model.OverviewSeries;
import com.bluewhale.overview.model.OverviewSnapshot;
import com.bluewhale.overview.model.OverviewTone;
import com.bluewhale.overview.support.Overviews;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 市舶（MALL）概览：内城十二坊的第十二坊——商城运营，S6 收官坊。
 *
 * <p><b>这一屏讲什么。</b>市舶司掌通商：架上有几样货在卖、收了钱压着
 * 几单没发货、有多少单售后等着处置、多少个规格已经缺货。它不重复任何一殿——
 * 财务殿管钱本身，市舶看的是<b>一笔生意从下单到收货的流水线卡在哪一节</b>：
 * 待支付、待发货、待退货这条链，在 2D 后台要切订单页、售后页、库存页三处才看得全。</p>
 *
 * <p><b>口径不另造。</b>所有数字来自 {@link MallOverviewStatsService}，
 * 与 2D 订单台账 / 售后页 / 库存页同一批表、同一套租户隔离插件；
 * 售后只数未结账（待审核 + 待退货），零库存按 SKU 数不按商品数。</p>
 *
 * <p><b>取不到数就让它抛。</b>这里不 catch、不返回 0：库连不上与"一单生意
 * 都没有"长得一模一样。SDK 会把异常降级成 stale/degraded，屏风上写"未接通"。</p>
 *
 * @author BlueWhale
 */
@Component
@RequiredArgsConstructor
public class MallOverviewProvider implements OverviewProvider {

    /** 服务一级前缀，与网关路由一致 */
    private static final String SERVICE = "mall";

    /** 城内节点 key（市舶司），与 {@code city-layout.json} 对齐 */
    private static final String NODE_KEY = "shibosi-hall";

    /** 权限码：复用 2D 订单台账同一码，不为 3D 单独发一套授权 */
    private static final String ORDER_PERMISSION = "sys:mall:order:list";

    /** 商品权限码：商品台账入口用 */
    private static final String PRODUCT_PERMISSION = "sys:mall:product:list";

    /** 订单台账列表接口：屏风页直接取这个路径（网关不剥 /mall 前缀） */
    private static final String ORDER_PAGE_PATH = "/mall/v1/order/page";

    /** 商品台账列表接口 */
    private static final String PRODUCT_PAGE_PATH = "/mall/v1/product/page";

    /** 市舶统计口径 */
    private final MallOverviewStatsService statsService;

    @Override
    public String service() {
        return SERVICE;
    }

    @Override
    public String nodeKey() {
        return NODE_KEY;
    }

    @Override
    public String title() {
        return "市舶";
    }

    @Override
    public String subtitle() {
        return "商城运营";
    }

    @Override
    public String permissionCode() {
        return ORDER_PERMISSION;
    }

    @Override
    public OverviewSnapshot build(OverviewContext context) {
        MallOverviewStats stats = statsService.snapshot(context == null ? null : context.tenantId());
        Overviews.Builder builder = Overviews.builder(SERVICE, NODE_KEY, title())
                .subtitle(subtitle() + " · 通商四本账")
                .note("口径：已支付未发货 = 钱已收款待发货；售后只数未结账"
                        + "（待审核 + 待退货）；零库存按 SKU 数不按商品数");
        appendKpis(builder, stats);
        appendStatusChart(builder, stats);
        appendLinks(builder);
        appendAlerts(builder, stats);
        return builder.build();
    }

    /**
     * 四张牌：在售商品、待发货订单、待处理售后、零库存 SKU。
     *
     * <p>后三张是这一屏的语气所在：钱收了货没动（待发货）、买家等着给说法
     * （待处理售后）、架上缺货下单就失败（零库存）——任何一张不为 0 都该黄。
     * 在售商品只报数，语气留给告警条。</p>
     *
     * @param builder 组装器
     * @param stats   统计快照
     */
    private static void appendKpis(Overviews.Builder builder, MallOverviewStats stats) {
        builder.kpi(Overviews.count("on-sale", "在售商品", stats.spuOnSale(), OverviewTone.FLAT,
                        spuHint(stats)))
                .kpi(Overviews.count("paid", "待发货订单", stats.orderPaid(),
                        warnIfPositive(stats.orderPaid()), paidHint(stats)))
                .kpi(Overviews.count("aftersale", "待处理售后",
                        stats.aftersaleCreated() + stats.aftersaleWaitReturn(),
                        warnIfPositive(stats.aftersaleCreated() + stats.aftersaleWaitReturn()),
                        aftersaleHint(stats)))
                .kpi(Overviews.count("stock-zero", "零库存 SKU", stats.stockZero(),
                        warnIfPositive(stats.stockZero()), stockHint(stats)));
    }

    /**
     * 有账就黄，没账就绿：市舶三张运营牌共用的语气规则。
     *
     * @param count 计数
     * @return 语气
     */
    private static OverviewTone warnIfPositive(long count) {
        return count > 0 ? OverviewTone.WARN : OverviewTone.GOOD;
    }

    /**
     * 商品牌的提示语：架上成色——总共几样、几样还没上架。
     *
     * @param stats 统计快照
     * @return 提示语
     */
    private static String spuHint(MallOverviewStats stats) {
        long offShelf = stats.spuTotal() - stats.spuOnSale();
        if (stats.spuTotal() <= 0) {
            return "还没有上过货";
        }
        return "未上架 " + offShelf + " 件";
    }

    /**
     * 待发货牌的提示语：钱收了货没动，压几单要说清。
     *
     * @param stats 统计快照
     * @return 提示语
     */
    private static String paidHint(MallOverviewStats stats) {
        return stats.orderPaid() > 0 ? "已收款未发货，要人去发" : "没有压单";
    }

    /**
     * 售后牌的提示语：两段未结账分开说——待审核要人裁，待退货等买家寄。
     *
     * @param stats 统计快照
     * @return 提示语
     */
    private static String aftersaleHint(MallOverviewStats stats) {
        long pending = stats.aftersaleCreated() + stats.aftersaleWaitReturn();
        if (pending <= 0) {
            return "售后没有积压";
        }
        return "待审核 " + stats.aftersaleCreated() + " · 待退货 " + stats.aftersaleWaitReturn();
    }

    /**
     * 零库存牌的提示语：按 SKU 数，一个尺码断了也算。
     *
     * @param stats 统计快照
     * @return 提示语
     */
    private static String stockHint(MallOverviewStats stats) {
        return stats.stockZero() > 0 ? "有规格缺货，下单会失败" : "库存充足";
    }

    /**
     * 订单状态分布柱图：七态全景。
     *
     * <p><b>为什么是分布不是趋势。</b>市舶的屏风账是存量不是流量：今天比昨天
     * 多几单没有多少信息量，"订单都堆在待发货"这种结构才是运营要先看的东西。
     * 七态码值固定（{@code OrderStatus}），柱图比折线少一根轴的歧义；
     * 退款中与已退款单列，退款积压不混进取消里看不见。</p>
     *
     * @param builder 组装器
     * @param stats   统计快照
     */
    private static void appendStatusChart(Overviews.Builder builder, MallOverviewStats stats) {
        List<String> categories = List.of("待支付", "待发货", "已发货", "已完成", "已取消", "退款中", "已退款");
        List<Double> values = List.of((double) stats.orderPendingPayment(), (double) stats.orderPaid(),
                (double) stats.orderShipped(), (double) stats.orderCompleted(),
                (double) stats.orderCancelled(), (double) stats.orderRefunding(),
                (double) stats.orderRefunded());
        builder.chart(Overviews.chart("order-status", "订单状态分布", "bar", OverviewFormat.COUNT,
                categories, new OverviewSeries("订单数", values)));
    }

    /**
     * 入口：订单台账 + 商品台账。
     *
     * <p>订单是市舶主账，排第一；商品台账给"架上有什么"一个直通入口。
     * 售后与库存不单摆：2D 里都从订单和商品进，屏风上硬凑只会把入口铺满。</p>
     *
     * @param builder 组装器
     */
    private static void appendLinks(Overviews.Builder builder) {
        builder.link(Overviews.link("orders", "订单台账", ORDER_PAGE_PATH, ORDER_PERMISSION));
        builder.link(Overviews.link("products", "商品台账", PRODUCT_PAGE_PATH, PRODUCT_PERMISSION));
    }

    /**
     * 告警条：待发货积压、退款进行中、售后待审核、零库存、空市。
     *
     * <p>这些事"数字本身不会喊"：{@code 5} 单待发货不会自己变红，
     * {@code 0} 个商品与"今天没快照"看起来一样。待支付与已取消是买家自己的
     * 事（过期关单由定时任务兜底），不进告警——运营要盯的是自己这一侧的欠账。</p>
     *
     * @param builder 组装器
     * @param stats   统计快照
     */
    private static void appendAlerts(Overviews.Builder builder, MallOverviewStats stats) {
        if (stats.orderPaid() > 0) {
            builder.alert(OverviewAlertLevel.WARNING, "有 " + stats.orderPaid()
                    + " 单已收款未发货，钱收了货还没动，需要人去发", null);
        }
        if (stats.orderRefunding() > 0) {
            builder.alert(OverviewAlertLevel.WARNING, "有 " + stats.orderRefunding()
                    + " 单退款进行中，钱卡在支付侧，盯回调确认到账", null);
        }
        long aftersalePending = stats.aftersaleCreated() + stats.aftersaleWaitReturn();
        if (stats.aftersaleCreated() > 0) {
            builder.alert(OverviewAlertLevel.WARNING, "有 " + stats.aftersaleCreated()
                    + " 单售后待审核，买家等着给说法", null);
        }
        if (stats.aftersaleWaitReturn() > 0) {
            builder.alert(OverviewAlertLevel.INFO, "有 " + stats.aftersaleWaitReturn()
                    + " 单售后待退货，等买家寄回商品后结单", null);
        }
        if (stats.stockZero() > 0) {
            builder.alert(OverviewAlertLevel.WARNING, "有 " + stats.stockZero()
                    + " 个 SKU 零库存，在售规格下单会失败，要补货或下架", null);
        }
        if (stats.spuTotal() <= 0 && stats.orderTotal() <= 0) {
            builder.alert(OverviewAlertLevel.INFO, "市舶还是空的：没有上过货，也没有成交过一单", null);
        }
    }
}
