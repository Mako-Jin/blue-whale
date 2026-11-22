package com.bluewhale.map.overview;

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
 * 舆图司（MAP）概览：外城九处第六处——全城的地图能力从这里发。
 *
 * <p><b>这一屏讲什么。</b>地图服务是"外呼型"能力：地址解析、POI、路径都打到供应商，
 * 每一次都吃配额。所以本殿的主账不是"库里有几个点"，而是<b>今天还用得动吗</b>：
 * 今日调了多少次、上限还剩多少、近七日是一天比一天少还是一直为 0。配额打满之后
 * 供应商会直接返回错误码，用户看到的却是"地图坏了"——这一屏就是提前把话说明白。</p>
 *
 * <p><b>与别处不重复。</b>清议堂看讨论秩序、邮驿看信件收发、传信司看即时会话，
 * 舆图司看的是<b>空间数据的家底</b>：打点（业务对象在哪）、围栏（谁管哪一片）、
 * 解析缓存（问过的地址记住了没有）。三件事对应舆图的三个图层——点、界、注记。</p>
 *
 * <p><b>取不到数就让它抛。</b>这里不 catch、不返回 0：库连不上与"今天真的没人用地图"
 * 长得一模一样。SDK 会把异常降级成 stale/degraded，屏风上写"未接通"。</p>
 *
 * @author BlueWhale
 */
@Component
@RequiredArgsConstructor
public class MapOverviewProvider implements OverviewProvider {

    /** 服务一级前缀，与网关路由一致 */
    private static final String SERVICE = "map";

    /** 城内节点 key（舆图司），与 {@code city-layout.json} 对齐 */
    private static final String NODE_KEY = "yutu-stage";

    /** 权限码：供应商状态与配额（运维口径，2D 里只授给租户管理员） */
    private static final String PROVIDER_PERMISSION = "sys:map:provider:view";

    /** 权限码：围栏查询（围栏台账入口用它） */
    private static final String FENCE_PERMISSION = "sys:map:fence:query";

    /** 围栏台账接口（分页参数是 page/size，不是 current/size） */
    private static final String FENCE_PAGE_PATH = "/map/v1/fence/page";

    /** 供应商健康与配额接口 */
    private static final String PROVIDER_HEALTH_PATH = "/map/v1/provider/health";

    /** 配额告警线（百分比）：用到八成该提醒扩容或降调用 */
    static final int QUOTA_WARN_PERCENT = 80;

    /** 柱图横轴口径：月-日 */
    private static final DateTimeFormatter DAY_LABEL = DateTimeFormatter.ofPattern("MM-dd");

    /** 舆图司统计口径 */
    private final MapOverviewStatsService statsService;

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
        return "舆图司";
    }

    @Override
    public String subtitle() {
        return "舆图与疆界";
    }

    @Override
    public String permissionCode() {
        return PROVIDER_PERMISSION;
    }

    @Override
    public OverviewSnapshot build(OverviewContext context) {
        MapOverviewStats stats = statsService.snapshot(context == null ? null : context.tenantId());
        Overviews.Builder builder = Overviews.builder(SERVICE, NODE_KEY, title())
                .subtitle(subtitle() + " · 点 · 界 · 注记")
                .note("口径：调用量取供应商日台账（各供应商合计）；配额按已用 ≥80% 算吃紧，"
                        + "上限为 0 表示不限；缓存过期不等于要清，命中时会被覆盖");
        appendKpis(builder, stats);
        appendTrendChart(builder, stats);
        appendLinks(builder);
        appendAlerts(builder, stats);
        return builder.build();
    }

    /**
     * 四张牌：业务打点、围栏、解析缓存、今日调用。
     *
     * <p>前三张只报数（家底多不是坏事），语气留给第四张：调用量一旦逼近上限，
     * 后面所有用到地图的功能都会开始报错，而它们各自的报错都长得像"自己的 bug"。</p>
     *
     * @param builder 组装器
     * @param stats   统计快照
     */
    private static void appendKpis(Overviews.Builder builder, MapOverviewStats stats) {
        builder.kpi(Overviews.count("locations", "业务打点", stats.locationTotal(), OverviewTone.FLAT,
                        locationHint(stats)))
                .kpi(Overviews.count("fences", "围栏", stats.fenceTotal(), OverviewTone.FLAT,
                        fenceHint(stats)))
                .kpi(Overviews.count("cache", "解析缓存", stats.cacheTotal(), OverviewTone.FLAT,
                        cacheHint(stats)))
                .kpi(Overviews.count("calls", "今日调用", stats.quotaUsedToday(),
                        quotaTone(stats), quotaHint(stats)));
    }

    /**
     * 打点的提示语：一个都没有说明还没业务接进来。
     *
     * @param stats 统计快照
     * @return 提示语
     */
    private static String locationHint(MapOverviewStats stats) {
        return stats.locationTotal() > 0 ? "按业务对象唯一" : "还没有业务打点";
    }

    /**
     * 围栏的提示语：启用与停用分开说，停用的不参与范围判定。
     *
     * @param stats 统计快照
     * @return 提示语
     */
    private static String fenceHint(MapOverviewStats stats) {
        if (stats.fenceTotal() <= 0) {
            return "还没划过围栏";
        }
        if (stats.fenceDisabled() <= 0) {
            return "全部启用";
        }
        return "另停用 " + stats.fenceDisabled() + " 个";
    }

    /**
     * 缓存的提示语：过期条数比总条数更值得说。
     *
     * @param stats 统计快照
     * @return 提示语
     */
    private static String cacheHint(MapOverviewStats stats) {
        if (stats.cacheTotal() <= 0) {
            return "问过的地址还没记";
        }
        if (stats.cacheExpired() <= 0) {
            return "全部在有效期";
        }
        return "过期 " + stats.cacheExpired() + " 条";
    }

    /**
     * 今日调用的语气：逼近上限就黄，打满就红。
     *
     * @param stats 统计快照
     * @return 语气
     */
    private static OverviewTone quotaTone(MapOverviewStats stats) {
        if (stats.quotaLimitToday() <= 0) {
            return OverviewTone.FLAT;
        }
        if (stats.quotaUsedToday() >= stats.quotaLimitToday()) {
            return OverviewTone.BAD;
        }
        return stats.quotaUsedToday() * 100 >= stats.quotaLimitToday() * QUOTA_WARN_PERCENT
                ? OverviewTone.WARN : OverviewTone.GOOD;
    }

    /**
     * 今日调用的提示语：上限一并给出，单看已用读不出轻重。
     *
     * <p>牌面只有一行的宽度，长句子会被 ellipsis 截断——"上限 10000，还剩…"这种
     * 半句话比不给更糟，所以这里只写上限本身，占比交给告警条说。</p>
     *
     * @param stats 统计快照
     * @return 提示语
     */
    private static String quotaHint(MapOverviewStats stats) {
        if (stats.quotaLimitToday() <= 0) {
            return "供应商未限流";
        }
        return "上限 " + stats.quotaLimitToday();
    }

    /**
     * 近七日调用量：舆图还在不在被用，一眼看完。
     *
     * <p><b>为什么是趋势不是分布。</b>打点与围栏是存量，画成分布只说明"台账有多大"；
     * 调用量是流量，它能回答"哪天开始没人用了"。七根柱子刚好放满屏风中部，
     * 再多就要横向滚动——屏风上套滚动是最难用的组合。</p>
     *
     * @param builder 组装器
     * @param stats   统计快照
     */
    private static void appendTrendChart(Overviews.Builder builder, MapOverviewStats stats) {
        List<String> categories = stats.recentDays().stream()
                .map(day -> day.day().format(DAY_LABEL))
                .toList();
        List<Double> values = stats.recentDays().stream()
                .map(day -> (double) day.used())
                .toList();
        builder.chart(Overviews.chart("call-trend", "近七日调用量", "bar", OverviewFormat.COUNT,
                categories, new OverviewSeries("调用次数", values)));
    }

    /**
     * 入口：围栏台账 + 供应商状态。
     *
     * <p>两个入口都是"看账"的落点：围栏是本殿管的东西，供应商状态是配额的出处。
     * 打点与地理编码在 2D 里没有独立列表页（点位跟着业务对象走、解析是即时动作），
     * 硬凑两个禁用标签只会让屏风看起来像坏了。</p>
     *
     * @param builder 组装器
     */
    private static void appendLinks(Overviews.Builder builder) {
        builder.link(Overviews.link("fences", "围栏台账", FENCE_PAGE_PATH, FENCE_PERMISSION));
        builder.link(Overviews.link("providers", "供应商状态", PROVIDER_HEALTH_PATH, PROVIDER_PERMISSION));
    }

    /**
     * 告警条：配额、调用量、围栏与空库。
     *
     * <p>配额打满排在最前：这是唯一一件"明天就一定会坏事"的事。调用量为 0 只报 INFO——
     * 周末没人用地图是正常的，天天报警等于教人忽略告警。</p>
     *
     * @param builder 组装器
     * @param stats   统计快照
     */
    private static void appendAlerts(Overviews.Builder builder, MapOverviewStats stats) {
        if (stats.quotaLimitToday() > 0 && stats.quotaUsedToday() >= stats.quotaLimitToday()) {
            builder.alert(OverviewAlertLevel.CRITICAL, "今日配额已打满（" + stats.quotaUsedToday()
                    + " / " + stats.quotaLimitToday() + "），供应商会开始拒绝请求，地址解析与路径规划全停", null);
        } else if (stats.quotaLimitToday() > 0
                && stats.quotaUsedToday() * 100 >= stats.quotaLimitToday() * QUOTA_WARN_PERCENT) {
            builder.alert(OverviewAlertLevel.WARNING, "今日配额已用八成以上（" + stats.quotaUsedToday()
                    + " / " + stats.quotaLimitToday() + "），再涨就要限流或扩容", null);
        }
        long weekTotal = stats.recentDays().stream().mapToLong(MapOverviewStats.DailyUsage::used).sum();
        if (weekTotal <= 0) {
            builder.alert(OverviewAlertLevel.INFO, "近七日没有一次地图调用，这张舆图等于停摆", null);
        } else if (stats.quotaUsedToday() <= 0) {
            builder.alert(OverviewAlertLevel.INFO, "今日还没有人问过地图", null);
        }
        if (stats.locationTotal() <= 0 && stats.fenceTotal() <= 0) {
            builder.alert(OverviewAlertLevel.INFO, "舆图司还空着：既没有打点也没有围栏", null);
        } else if (stats.fenceTotal() <= 0) {
            builder.alert(OverviewAlertLevel.INFO, "还没有划过围栏，范围判定（打卡、配送）无从谈起", null);
        } else if (stats.fenceEnabled() <= 0) {
            builder.alert(OverviewAlertLevel.WARNING, "围栏全部停用，" + stats.fenceTotal()
                    + " 条边界一条也不参与判定", null);
        }
        if (stats.cacheTotal() > 0 && stats.cacheExpired() >= stats.cacheTotal()) {
            builder.alert(OverviewAlertLevel.INFO, "解析缓存已全部过期，下一轮查询会重新问供应商（会吃配额）", null);
        }
    }
}
