package com.bluewhale.twin.overview;

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

import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 观星台（TWIN）概览：外城九处第七处——全城的"数字孪生"从这里看。
 *
 * <p><b>这一屏讲什么。</b>孪生平台的价值不在"搭了几座场景"，而在<b>场景里的事还在
 * 被人盯着</b>：设备状态越界时有没有告警落下来、告警落下来有没有人确认、
 * 场景改完有没有发布成冻结版。所以四张牌排的是场景 / 星标 / 未恢复告警 / 发布快照，
 * 主图是近七日的告警触发数——它掉到 0 的那一刻，孪生要么真的安稳，要么早就没人评了。</p>
 *
 * <p><b>为什么"未发布"要单独报警。</b>草稿场景在编辑器里看着一切正常，但运行时的
 * 绑定评估只跑已发布版；改完没发布，等于改了但没生效。这是孪生最典型的静默失败：
 * 界面上什么都没坏，只是没有一条告警会因为这个改动而响。</p>
 *
 * <p><b>与别处不重复。</b>清议堂看讨论秩序、邮驿看信件收发、传信司看即时会话、
 * 舆图司看空间数据，观星台看的是<b>设备与场景的运行态</b>——它对应 3D 世界里
 * 那座浑天仪：星标越多、告警越红，盘上就越热闹。</p>
 *
 * <p><b>取不到数就让它抛。</b>这里不 catch、不返回 0：库连不上与"真的没有告警"
 * 长得一模一样。SDK 会把异常降级成 stale/degraded，屏风上写"未接通"。</p>
 *
 * @author BlueWhale
 */
@Component
@RequiredArgsConstructor
public class TwinOverviewProvider implements OverviewProvider {

    /** 服务一级前缀，与网关路由一致 */
    private static final String SERVICE = "twin";

    /** 城内节点 key（观星台），与 {@code city-layout.json} 对齐 */
    private static final String NODE_KEY = "guanxing-stage";

    /** 权限码：孪生场景（本服务全类唯一码，场景/标注/告警共用） */
    private static final String SCENE_PERMISSION = "sys:twin:scene";

    /** 场景台账接口（分页参数是 current/size，与平台统一口径一致） */
    private static final String SCENE_PAGE_PATH = "/twin/v1/scenes/page";

    /** 柱图横轴口径：月-日 */
    private static final DateTimeFormatter DAY_LABEL = DateTimeFormatter.ofPattern("MM-dd");

    /** 观星台统计口径 */
    private final TwinOverviewStatsService statsService;

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
        return "观星台";
    }

    @Override
    public String subtitle() {
        return "星象与孪生";
    }

    @Override
    public String permissionCode() {
        return SCENE_PERMISSION;
    }

    @Override
    public OverviewSnapshot build(OverviewContext context) {
        TwinOverviewStats stats = statsService.snapshot(context == null ? null : context.tenantId());
        Overviews.Builder builder = Overviews.builder(SERVICE, NODE_KEY, title())
                .subtitle(subtitle() + " · 场景 · 告警 · 快照")
                .note("口径：未恢复取 resolved=0（与告警抽屉同源）；草稿与停用都不参与运行时评估，"
                        + "只有已发布场景会被浑天仪读出来");
        appendKpis(builder, stats);
        appendTrendChart(builder, stats);
        appendLinks(builder);
        appendAlerts(builder, stats);
        return builder.build();
    }

    /**
     * 四张牌：场景、星标、未恢复告警、发布快照。
     *
     * <p>只有"未恢复告警"带语气：场景少、星标少只是还没搭完，不是坏事；
     * 告警挂着没恢复才是真的有事。快照数翻灰灯——它说明"发布过几版"，
     * 是家底不是指标。</p>
     *
     * @param builder 组装器
     * @param stats   统计快照
     */
    private static void appendKpis(Overviews.Builder builder, TwinOverviewStats stats) {
        builder.kpi(Overviews.count("scenes", "观星场景", stats.sceneTotal(), OverviewTone.FLAT,
                        sceneHint(stats)))
                .kpi(Overviews.count("markers", "星标", stats.markerEnabled(), OverviewTone.FLAT,
                        markerHint(stats)))
                .kpi(Overviews.count("alerts", "未恢复告警", stats.alertUnresolved(),
                        alertTone(stats), alertHint(stats)))
                .kpi(Overviews.count("snapshots", "发布快照", stats.snapshotTotal(), OverviewTone.FLAT,
                        snapshotHint(stats)));
    }

    /**
     * 场景的提示语：真正能运行的是已发布场景。
     *
     * @param stats 统计快照
     * @return 提示语
     */
    private static String sceneHint(TwinOverviewStats stats) {
        if (stats.sceneTotal() <= 0) {
            return "还没有场景";
        }
        if (stats.scenePublished() > 0) {
            return "已发布 " + stats.scenePublished();
        }
        return stats.sceneDraft() > 0 ? "全是草稿" : "都不在运行";
    }

    /**
     * 星标的提示语：停用的不画到浑天仪上。
     *
     * @param stats 统计快照
     * @return 提示语
     */
    private static String markerHint(TwinOverviewStats stats) {
        if (stats.markerTotal() <= 0) {
            return "场景里还没有星标";
        }
        if (stats.markerDisabled() <= 0) {
            return "全部启用";
        }
        return "另停用 " + stats.markerDisabled();
    }

    /**
     * 告警的语气：有严重就红，有未恢复就黄，全清才绿。
     *
     * @param stats 统计快照
     * @return 语气
     */
    private static OverviewTone alertTone(TwinOverviewStats stats) {
        if (stats.alertCritical() > 0) {
            return OverviewTone.BAD;
        }
        return stats.alertUnresolved() > 0 ? OverviewTone.WARN : OverviewTone.GOOD;
    }

    /**
     * 告警的提示语：牌面只有一行，先说最要紧的那一档。
     *
     * @param stats 统计快照
     * @return 提示语
     */
    private static String alertHint(TwinOverviewStats stats) {
        if (stats.alertCritical() > 0) {
            return "严重 " + stats.alertCritical();
        }
        if (stats.alertUnacked() > 0) {
            return "待确认 " + stats.alertUnacked();
        }
        return stats.alertUnresolved() > 0 ? "均已确认" : "一切安好";
    }

    /**
     * 快照的提示语：近七日有没有发过版。
     *
     * @param stats 统计快照
     * @return 提示语
     */
    private static String snapshotHint(TwinOverviewStats stats) {
        if (stats.snapshotTotal() <= 0) {
            return "还没有发布过";
        }
        return stats.snapshotRecent() > 0 ? "近七日 +" + stats.snapshotRecent() : "近七日无发布";
    }

    /**
     * 近七日告警触发数：孪生还在不在被监测，一眼看完。
     *
     * <p><b>为什么是告警趋势而不是实体分布。</b>实体与星标是存量，画成分布只说明
     * "场景有多大"；告警按天落，能回答"哪天之后就不响了"。七根柱子刚好放满屏风中部，
     * 再多就要横向滚动——屏风上套滚动是最难用的组合。</p>
     *
     * @param builder 组装器
     * @param stats   统计快照
     */
    private static void appendTrendChart(Overviews.Builder builder, TwinOverviewStats stats) {
        List<String> categories = stats.recentDays().stream()
                .map(day -> day.day().format(DAY_LABEL))
                .toList();
        List<Double> values = stats.recentDays().stream()
                .map(day -> (double) day.count())
                .toList();
        builder.chart(Overviews.chart("alert-trend", "近七日告警", "bar", OverviewFormat.COUNT,
                categories, new OverviewSeries("告警数", values)));
    }

    /**
     * 入口：场景台账。
     *
     * <p>只登记一个入口。实体树、绑定、告警都挂在某个场景下，2D 里没有"先选场景"
     * 就打得开的独立列表页；硬凑"3D 查看器"这类入口会渲染成禁用标签——屏风上
     * 多一个点不动的按钮，比少一个更让人怀疑自己没权限。</p>
     *
     * @param builder 组装器
     */
    private static void appendLinks(Overviews.Builder builder) {
        builder.link(Overviews.link("twin-scenes", "场景台账", SCENE_PAGE_PATH, SCENE_PERMISSION));
    }

    /**
     * 告警条：严重 → 待确认 → 未恢复 → 没发布 → 星标全停 → 近七日安静。
     *
     * <p>顺序按"明天会不会坏事"排：严重告警最前；"一个场景都没发布"排在中段，
     * 因为它不会立刻报错，却会让后面所有的改动都不生效。近七日无告警只报 INFO，
     * 而且是好消息——周末没人越界是正常的，天天报警等于教人忽略告警。</p>
     *
     * @param builder 组装器
     * @param stats   统计快照
     */
    private static void appendAlerts(Overviews.Builder builder, TwinOverviewStats stats) {
        if (stats.alertCritical() > 0) {
            builder.alert(OverviewAlertLevel.CRITICAL, "有 " + stats.alertCritical()
                    + " 条严重告警未恢复，绑定对应的设备状态越界后一直没回来", null);
        }
        if (stats.alertUnacked() > 0) {
            builder.alert(OverviewAlertLevel.WARNING, "还有 " + stats.alertUnacked()
                    + " 条告警没有人工确认，值班的人可能还不知道", null);
        } else if (stats.alertUnresolved() > 0) {
            builder.alert(OverviewAlertLevel.INFO, stats.alertUnresolved()
                    + " 条告警未恢复，但都已确认", null);
        }
        if (stats.sceneTotal() <= 0) {
            builder.alert(OverviewAlertLevel.INFO, "观星台还空着：一座孪生场景都还没有搭", null);
        } else if (stats.scenePublished() <= 0) {
            builder.alert(OverviewAlertLevel.WARNING, stats.sceneTotal()
                    + " 座场景一座都没发布，运行时只评已发布版，改动不会生效", null);
        } else if (stats.markerEnabled() <= 0) {
            builder.alert(OverviewAlertLevel.INFO, "已发布场景里没有启用的星标，浑天仪上只有空盘", null);
        }
        if (stats.weekAlerts() <= 0 && stats.alertUnresolved() <= 0) {
            builder.alert(OverviewAlertLevel.INFO, "近七日没有一条告警，设备一直安分", null);
        }
    }
}
