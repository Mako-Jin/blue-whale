package com.bluewhale.erp.analytics.interfaces.overview;

import com.bluewhale.erp.analytics.application.service.AnalyticsAppService;
import com.bluewhale.erp.analytics.application.view.CockpitView;
import com.bluewhale.overview.OverviewContext;
import com.bluewhale.overview.OverviewProvider;
import com.bluewhale.overview.model.OverviewAlertLevel;
import com.bluewhale.overview.model.OverviewFormat;
import com.bluewhale.overview.model.OverviewSnapshot;
import com.bluewhale.overview.model.OverviewTone;
import com.bluewhale.overview.support.Overviews;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

/**
 * 户部（ERP）概览：三维王城数据厅的第一个标杆节点。
 *
 * <p><b>口径不另造。</b>三个指标全部取自 {@link AnalyticsAppService#getCockpit(String)} ——
 * 与 2D 后台经营驾驶舱同一个方法、同一份快照。3D 城若要"自己的口径"，
 * 迟早会出现"城里的营收和后台不一样"，而这类分歧没人能判断谁是对的。</p>
 *
 * <p><b>为什么不在这里 catch 异常。</b>数据库挂了就让它抛，SDK 会降级成
 * {@code stale}（沿用上次快照）或 {@code degraded}（闭门态）。
 * provider 自己吞掉异常返回空 KPI，会让"接口挂了"长得跟"本月没有业务"一模一样。</p>
 *
 * @author BlueWhale
 */
@Component
@RequiredArgsConstructor
public class ErpOverviewProvider implements OverviewProvider {

    /** 权限码：与 2D 驾驶舱同一码，避免为 3D 单独发一套授权 */
    private static final String PERMISSION = "sys:erp:bi:cockpit:list";

    /** 屏风页（采购订单）的权限码：与 2D 采购列表同一码 */
    private static final String PURCHASE_PERMISSION = "sys:erp:purchase:list";

    /** 采购订单列表接口：屏风页 T4.5 直接取这个路径，不再单发一套只读接口 */
    private static final String PURCHASE_PAGE_PATH = "/erp/scm/purchase-order/page";

    /** 城内节点 key（户部坊），与 city-layout.json 对齐 */
    private static final String NODE_KEY = "hubu-hall";

    /** 驾驶舱应用服务 */
    private final AnalyticsAppService analyticsAppService;

    @Override
    public String service() {
        return "erp";
    }

    @Override
    public String nodeKey() {
        return NODE_KEY;
    }

    @Override
    public String title() {
        return "户部";
    }

    @Override
    public String subtitle() {
        return "经营驾驶舱";
    }

    @Override
    public String permissionCode() {
        return PERMISSION;
    }

    @Override
    public OverviewSnapshot build(OverviewContext context) {
        CockpitView cockpit = analyticsAppService.getCockpit(null);
        int alerts = cockpit.getAlertCount() == null ? 0 : cockpit.getAlertCount();
        Overviews.Builder builder = Overviews.builder(service(), nodeKey(), title())
                .subtitle(subtitle() + " · " + safePeriod(cockpit))
                .kpi(Overviews.money("revenue", "营收", cockpit.getRevenueAmount()))
                .kpi(Overviews.count("orders", "订单", cockpit.getOrderCount()))
                .kpi(Overviews.count("alerts", "预警", alerts,
                        alerts > 0 ? OverviewTone.WARN : OverviewTone.GOOD, "未关闭"))
                /* 三项金额来自同一份快照的三列，画成柱状图不引入任何新口径：
                   营收 = 成本 + 利润 的关系在图上可直接看出来，比三张 KPI 更有信息量。 */
                .chart(Overviews.chart("pnl", "本期 营收 / 成本 / 利润", "bar", OverviewFormat.MONEY,
                        List.of("营收", "成本", "利润"),
                        Overviews.series("金额",
                                amount(cockpit.getRevenueAmount()),
                                amount(cockpit.getCostAmount()),
                                amount(cockpit.getProfitAmount()))))
                .note("口径：驾驶舱快照 " + safeSnapshotNo(cockpit));
        /* 入口按权限裁剪：没有采购列表权限的人，连"能点进去"这件事都不该知道。
           截到前端再隐藏等于把入口清单白送，契约 §2.5 明确要求 provider 裁。 */
        if (context != null && context.hasPermission(PURCHASE_PERMISSION)) {
            builder.link(Overviews.link("purchase-orders", "采购订单", PURCHASE_PAGE_PATH, PURCHASE_PERMISSION));
        }
        if (alerts > 0) {
            builder.alert(OverviewAlertLevel.WARNING, "有 " + alerts + " 条未关闭预警", alerts);
        }
        return builder.build();
    }

    /**
     * 金额兜底：快照未生成时不要下发 null（图表里 null 会变成 NaN 柱，整张卡静默变白）
     *
     * @param value 金额
     * @return 非空金额，缺省 0
     */
    private static double amount(BigDecimal value) {
        return value == null ? 0d : value.doubleValue();
    }

    /**
     * 统计期间兜底：快照未生成时不要往下发 null（前端拼副标题会得到 "null"）
     *
     * @param cockpit 驾驶舱视图
     * @return 期间文本
     */
    private String safePeriod(CockpitView cockpit) {
        String period = cockpit.getPeriodCode();
        return period == null || period.isBlank() ? "当期" : period;
    }

    /**
     * 快照编号兜底
     *
     * @param cockpit 驾驶舱视图
     * @return 快照编号文本
     */
    private String safeSnapshotNo(CockpitView cockpit) {
        String snapshotNo = cockpit.getSnapshotNo();
        return snapshotNo == null || snapshotNo.isBlank() ? "未生成" : snapshotNo;
    }
}
