package com.bluewhale.erp.analytics.interfaces.overview;

import com.bluewhale.erp.analytics.application.service.AnalyticsAppService;
import com.bluewhale.erp.analytics.application.view.CockpitView;
import com.bluewhale.overview.OverviewContext;
import com.bluewhale.overview.OverviewProvider;
import com.bluewhale.overview.model.OverviewAlertLevel;
import com.bluewhale.overview.model.OverviewSnapshot;
import com.bluewhale.overview.model.OverviewTone;
import com.bluewhale.overview.support.Overviews;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

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
                .note("口径：驾驶舱快照 " + safeSnapshotNo(cockpit));
        if (alerts > 0) {
            builder.alert(OverviewAlertLevel.WARNING, "有 " + alerts + " 条未关闭预警", alerts);
        }
        return builder.build();
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
