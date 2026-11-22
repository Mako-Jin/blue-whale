package com.bluewhale.iot.overview;

import com.bluewhale.iot.dto.IotDayCount;
import com.bluewhale.iot.dto.IotStats;
import com.bluewhale.iot.service.IotStatsService;
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

import java.time.LocalDateTime;
import java.util.List;

/**
 * 兵部（IoT）概览：内城十二坊的第四个坊——物联感知。
 *
 * <p><b>这一屏讲什么。</b>兵部掌哨探：全城有多少个"探子"（设备）、有几个还在回报、
 * 近一天收上来多少条消息、有没有指令发出去就没了下文。它不重复任何一殿：
 * 户部看钱、工部看产线、吏部看欠账、礼部看客户，兵部看的是**感知链路是否还活着**——
 * 设备离线、遥测断流、指令无回执，这三件事在 2D 后台都要点进两级页面才看得见。</p>
 *
 * <p><b>口径不另造。</b>所有数字来自 {@link IotStatsService}，与 2D 设备页同一批表、
 * 同一套租户隔离插件；在线率的分子分母定义写在 {@link IotStats} 上，屏风只排版。</p>
 *
 * <p><b>取不到数就让它抛。</b>这里不 catch、不返回 0：数据库连不上与"一台设备都没有"
 * 长得一模一样，是本项目最高频的静默失效。SDK 会把异常降级成 stale/degraded，
 * 屏风上写"未接通"而不是"设备 0 台"。</p>
 *
 * @author BlueWhale
 */
@Component
@RequiredArgsConstructor
public class IotOverviewProvider implements OverviewProvider {

    /** 服务一级前缀，与网关路由一致 */
    private static final String SERVICE = "iot";

    /** 城内节点 key（兵部坊），与 {@code city-layout.json} 对齐 */
    private static final String NODE_KEY = "bingbu-hall";

    /** 权限码：复用 2D 设备管理同一码，不为 3D 单独发一套授权 */
    private static final String PERMISSION = "sys:iot:device";

    /** 设备台账列表接口：屏风页直接取这个路径，不再单发一套只读接口 */
    private static final String DEVICE_PAGE_PATH = "/iot/v1/devices/page";

    /** 在线率"健康"分界：≥ 它给绿灯 */
    private static final double ONLINE_GOOD = 0.9d;

    /** 在线率"偏高但未达标"分界：≥ 它给黄灯，低于它给红灯 */
    private static final double ONLINE_WARN = 0.7d;

    /** 兵部统计口径 */
    private final IotStatsService statsService;

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
        return "兵部";
    }

    @Override
    public String subtitle() {
        return "物联感知";
    }

    @Override
    public String permissionCode() {
        return PERMISSION;
    }

    @Override
    public OverviewSnapshot build(OverviewContext context) {
        IotStats stats = statsService.snapshot(LocalDateTime.now());
        Overviews.Builder builder = Overviews.builder(SERVICE, NODE_KEY, title())
                .subtitle(subtitle() + " · 近 7 日")
                .note("口径：设备与遥测取自 iot_device / iot_telemetry 同源表，"
                        + "在线率 = 启用中在线 ÷ 启用中（停用不计入分母）");
        appendKpis(builder, stats);
        appendTrendChart(builder, stats);
        appendLinks(builder);
        appendAlerts(builder, stats);
        return builder.build();
    }

    /**
     * 四张牌：设备总数、在线率、近 24h 上报、待回指令。
     *
     * <p>"待回指令"取 {@code pending + sent}：{@code sent} 是"已经投递到 EMQX 但设备没回执"，
     * 正是兵部要盯的那一段——写 {@code success} 之前的任何状态都还算欠账。
     * 只数 {@code pending} 会让"发出去 200 条、一条没回"显示成 0。</p>
     *
     * @param builder 组装器
     * @param stats   统计快照
     */
    private static void appendKpis(Overviews.Builder builder, IotStats stats) {
        builder.kpi(Overviews.count("devices", "设备总数", stats.deviceTotal(), OverviewTone.FLAT,
                        "启用 " + stats.deviceActive() + " 台"))
                .kpi(Overviews.percent("online-rate", "在线率", stats.onlineRate(),
                        onlineTone(stats), onlineHint(stats)))
                .kpi(Overviews.count("telemetry-24h", "近 24h 上报", stats.telemetry24h(),
                        stats.telemetry24h() > 0 ? OverviewTone.GOOD : OverviewTone.FLAT, "全设备合计"))
                .kpi(Overviews.count("pending-cmd", "待回指令", stats.pendingCommands(),
                        stats.pendingCommands() > 0 ? OverviewTone.WARN : OverviewTone.GOOD, "已发未回执"));
    }

    /**
     * 近 7 日遥测折线：一眼看出上报是断了还是只是掉了一点。
     *
     * <p><b>为什么是折线不是柱。</b>屏风只有 620px 高，横条图每条占一行——7 天就是 7 行、
     * 吃掉近 180px，把下面的设备台账挤到只剩 3 行可见（页签写着 6 行、实际要滚）。
     * 折线把同一组数压进 3em 里，且"时间序列"本来就该用线表达：断流那天线直接落到基线，
     * 比一根长度为 0 的横条更容易看出来。</p>
     *
     * <p>只画一根序列：设备数、在线率、指令数单位各不相同，混进一张图 Y 轴就没法标度。</p>
     *
     * @param builder 组装器
     * @param stats   统计快照
     */
    private static void appendTrendChart(Overviews.Builder builder, IotStats stats) {
        List<IotDayCount> days = stats.telemetryDays();
        if (days == null || days.isEmpty()) {
            return;
        }
        List<String> categories = days.stream().map(IotDayCount::label).toList();
        List<Double> values = days.stream().map(day -> (double) day.count()).toList();
        builder.chart(Overviews.chart("telemetry-trend", "近 7 日遥测上报", "line", OverviewFormat.COUNT,
                categories, new OverviewSeries("上报条数", values)));
    }

    /**
     * 入口：设备台账。
     *
     * <p>只挂设备一个入口：产品与物模型是配置物，屏风上读它们没有意义；
     * 指令日志与规则在 2D 里才有操作按钮，城里点进去也只能看，不如留在后台。</p>
     *
     * @param builder 组装器
     */
    private static void appendLinks(Overviews.Builder builder) {
        builder.link(Overviews.link("devices", "设备台账", DEVICE_PAGE_PATH, PERMISSION));
    }

    /**
     * 告警条：离线设备、待回指令、遥测断流、规则全停、无设备。
     *
     * <p>五条都是"数字本身不会喊"的事：{@code 3} 台离线不会自己变红，
     * {@code 0} 条上报与"没人用"看起来一样。第二条尤其重要——指令发出去没回执，
     * 在设备页里要逐条翻状态才看得出来。</p>
     *
     * @param builder 组装器
     * @param stats   统计快照
     */
    private static void appendAlerts(Overviews.Builder builder, IotStats stats) {
        long offline = stats.offlineDevices();
        if (offline > 0) {
            builder.alert(OverviewAlertLevel.WARNING, "有 " + offline + " 台启用设备不在线",
                    alertCount(offline));
        }
        if (stats.pendingCommands() > 0) {
            builder.alert(OverviewAlertLevel.WARNING, "有 " + stats.pendingCommands()
                    + " 条指令已发出但未收到回执", alertCount(stats.pendingCommands()));
        }
        if (stats.deviceActive() > 0 && stats.telemetry24h() == 0) {
            builder.alert(OverviewAlertLevel.WARNING, "近 24 小时没有任何遥测上报，上报链路可能已断", null);
        }
        if (stats.rulesTotal() > 0 && stats.rulesEnabled() == 0) {
            builder.alert(OverviewAlertLevel.INFO, "全部 " + stats.rulesTotal()
                    + " 条规则都已停用，遥测不再触发告警", null);
        }
        if (stats.deviceTotal() == 0) {
            builder.alert(OverviewAlertLevel.INFO, "还没有登记任何设备，兵部暂时无哨可派", null);
        }
    }

    /**
     * 在线率语气：没有启用设备 → 灰；≥90% → 绿；≥70% → 黄；其余 → 红
     *
     * @param stats 统计快照
     * @return 语气
     */
    private static OverviewTone onlineTone(IotStats stats) {
        if (stats.deviceActive() <= 0) {
            return OverviewTone.FLAT;
        }
        double rate = stats.onlineRate();
        if (rate >= ONLINE_GOOD) {
            return OverviewTone.GOOD;
        }
        return rate >= ONLINE_WARN ? OverviewTone.WARN : OverviewTone.BAD;
    }

    /**
     * 在线率牌的提示语：写出分子分母，避免"90%"到底是 9/10 还是 900/1000 说不清。
     *
     * @param stats 统计快照
     * @return 提示语
     */
    private static String onlineHint(IotStats stats) {
        if (stats.deviceActive() <= 0) {
            return "尚无启用设备";
        }
        return stats.deviceOnline() + " / " + stats.deviceActive() + " 在线";
    }

    /**
     * 告警计数：契约只收 {@code Integer}，超长时截顶而不是抛异常。
     *
     * @param value 计数
     * @return 截顶后的整数
     */
    private static Integer alertCount(long value) {
        return (int) Math.min(value, Integer.MAX_VALUE);
    }
}
