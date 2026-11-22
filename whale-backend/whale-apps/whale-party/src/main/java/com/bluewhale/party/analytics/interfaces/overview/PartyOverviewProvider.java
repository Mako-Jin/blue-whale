package com.bluewhale.party.analytics.interfaces.overview;

import com.bluewhale.overview.OverviewContext;
import com.bluewhale.overview.OverviewProvider;
import com.bluewhale.overview.model.OverviewAlertLevel;
import com.bluewhale.overview.model.OverviewFormat;
import com.bluewhale.overview.model.OverviewKpi;
import com.bluewhale.overview.model.OverviewSeries;
import com.bluewhale.overview.model.OverviewSnapshot;
import com.bluewhale.overview.model.OverviewTone;
import com.bluewhale.overview.support.Overviews;
import com.bluewhale.party.analytics.application.service.AnalyticsAppService;
import com.bluewhale.party.analytics.application.view.MetricItemView;
import com.bluewhale.party.analytics.application.view.MetricOverviewView;
import com.bluewhale.party.analytics.domain.model.MetricType;
import com.bluewhale.party.common.PartyActor;
import com.bluewhale.party.common.PartyPermissions;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 宣抚（PARTY）概览：内城十二坊的第十二个坊——党务党建。
 *
 * <p><b>这一屏讲什么。</b>宣抚院掌党务：城里有几名在册党员、党费收得齐不齐、
 * 三会一课有没有按期开展、组织生活有多少人到场。它不重复任何一殿——
 * 吏部看考勤、杏坛看学习；宣抚看的是<b>组织生活正不正常</b>，
 * 这件事在 2D 后台要切到党务看板、再逐项对指标才看得全。</p>
 *
 * <p><b>口径不另造。</b>所有数字来自 {@link AnalyticsAppService#partyOverview}，
 * 与 2D 党务看板同一个方法：有当日快照读快照、没有就实时算一份但不落库。
 * 比率本来是百分数，进契约前统一除以 100——契约的
 * {@link OverviewFormat#PERCENT} 吃的是 0.94 这种比率，
 * 直接把 94 塞进去，屏风上会显示「9400.0%」。</p>
 *
 * <p><b>取不到数就让它抛。</b>这里不 catch、不返回 0：库连不上与
 * "一个党员都没有"长得一模一样。SDK 会把异常降级成 stale/degraded，
 * 屏风上写"未接通"。</p>
 *
 * @author BlueWhale
 */
@Component
@RequiredArgsConstructor
public class PartyOverviewProvider implements OverviewProvider {

    /** 服务一级前缀，与网关路由一致 */
    private static final String SERVICE = "party";

    /** 城内节点 key（宣抚坊），与 {@code city-layout.json} 对齐 */
    private static final String NODE_KEY = "xuanfu-court";

    /** 权限码：复用 2D 党务看板同一码，不为 3D 单独发一套授权 */
    private static final String PERMISSION = PartyPermissions.METRIC_LIST;

    /** 党员台账列表接口：屏风页直接取这个路径 */
    private static final String MEMBER_PAGE_PATH = "/party/v1/member/page";

    /** 百分数 → 比率的除数 */
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    /** 比率保留位数，与契约 {@code 94.0%} 的展示精度一致 */
    private static final int RATE_SCALE = 4;

    /** 党费收缴率"健康"分界（%）：≥ 它给绿灯 */
    private static final BigDecimal FEE_GOOD = BigDecimal.valueOf(95);

    /** 党费收缴率"及格"分界（%）：≥ 它给黄灯，低于它给红灯 */
    private static final BigDecimal FEE_WARN = BigDecimal.valueOf(80);

    /** 三会一课开展率"健康"分界（%） */
    private static final BigDecimal ACTIVITY_GOOD = BigDecimal.valueOf(90);

    /** 三会一课开展率"及格"分界（%） */
    private static final BigDecimal ACTIVITY_WARN = BigDecimal.valueOf(70);

    /** 组织生活参会率"健康"分界（%） */
    private static final BigDecimal ATTENDANCE_GOOD = BigDecimal.valueOf(85);

    /** 组织生活参会率"及格"分界（%） */
    private static final BigDecimal ATTENDANCE_WARN = BigDecimal.valueOf(60);

    /** 党务看板口径 */
    private final AnalyticsAppService analyticsAppService;

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
        return "宣抚";
    }

    @Override
    public String subtitle() {
        return "党务党建";
    }

    @Override
    public String permissionCode() {
        return PERMISSION;
    }

    @Override
    public OverviewSnapshot build(OverviewContext context) {
        MetricOverviewView view = analyticsAppService.partyOverview(null, null, actorOf(context));
        Map<MetricType, MetricItemView> metrics = indexItems(view);
        Overviews.Builder builder = Overviews.builder(SERVICE, NODE_KEY, title())
                .subtitle(subtitle() + " · 党务看板")
                .note(noteOf(metrics));
        appendKpis(builder, metrics);
        appendAgeChart(builder, metrics);
        appendLinks(builder);
        appendAlerts(builder, metrics, view);
        return builder.build();
    }

    /**
     * 由概览上下文还原党务操作人。
     *
     * <p>只带 userId / 角色 / 权限，不带用户名：党务看板是聚合数，不显示人名。
     * 上下文为空（内网直连、老调用方）时给匿名——看板只读，不像写操作那样必须失败关闭。</p>
     *
     * @param context 概览上下文，可空
     * @return 操作人
     */
    private static PartyActor actorOf(OverviewContext context) {
        if (context == null) {
            return PartyActor.anonymous();
        }
        return new PartyActor(
                context.userId(), null, context.roleCodes(), context.permissionCodes());
    }

    /**
     * 指标按类型码建索引：缺哪个指标补哪个，取数处不用判空。
     *
     * @param view 看板总览
     * @return 类型 → 指标
     */
    private static Map<MetricType, MetricItemView> indexItems(MetricOverviewView view) {
        Map<MetricType, MetricItemView> metrics = new EnumMap<>(MetricType.class);
        for (MetricItemView item : view.getItems()) {
            try {
                metrics.put(MetricType.valueOf(item.getMetricType()), item);
            } catch (IllegalArgumentException ignored) {
                /* 看板侧新加的指标码 3D 还不认识：跳过，不炸整屏 */
            }
        }
        return metrics;
    }

    /**
     * 口径说明：把三个比率的分母写清楚。
     *
     * <p>「收缴率」的分母是应收、「开展率」的分母是应开展场次、「参会率」的分母是
     * 应到人数——同一屏幕上三个百分比并排时，不写分母就等于让人猜。</p>
     *
     * @param metrics 指标索引
     * @return 说明文字
     */
    private static String noteOf(Map<MetricType, MetricItemView> metrics) {
        MetricItemView fee = metrics.get(MetricType.FEE_COLLECTION_AMOUNT);
        MetricItemView hold = metrics.get(MetricType.ACTIVITY_HOLD);
        MetricItemView attend = metrics.get(MetricType.ACTIVITY_ATTENDANCE);
        return "口径：收缴率 = 实收 ÷ 应收（" + fraction(fee) + "），"
                + "开展率 = 已开展 ÷ 应开展（" + fraction(hold) + "），"
                + "参会率 = 实到 ÷ 应到（" + fraction(attend) + "）";
    }

    /**
     * 四张牌：在册党员、党费收缴、三会一课、组织生活参会。
     *
     * <p>三张比率牌都"比率与分子分母成对出现"——只给 87% 看不出是 87/100
     * 还是 870/1000，所以每张牌的提示语都写出分子分母或笔数。</p>
     *
     * @param builder 组装器
     * @param metrics 指标索引
     */
    private static void appendKpis(Overviews.Builder builder, Map<MetricType, MetricItemView> metrics) {
        MetricItemView total = metrics.get(MetricType.MEMBER_TOTAL);
        MetricItemView probation = metrics.get(MetricType.PROBATION_DUE);
        builder.kpi(Overviews.count("members", "在册党员", count(total), OverviewTone.FLAT,
                        total == null ? "党务看板还没有数据"
                                : "待转正预备党员 " + count(probation) + " 人"))
                .kpi(feeKpi(metrics.get(MetricType.FEE_COLLECTION_AMOUNT)))
                .kpi(holdKpi(metrics.get(MetricType.ACTIVITY_HOLD)))
                .kpi(attendanceKpi(metrics.get(MetricType.ACTIVITY_ATTENDANCE)));
    }

    /**
     * 党费收缴牌。
     *
     * @param fee 收缴率指标，可空
     * @return 指标
     */
    private static OverviewKpi feeKpi(MetricItemView fee) {
        if (fee == null) {
            return Overviews.percent("fee-rate", "党费收缴", BigDecimal.ZERO, OverviewTone.FLAT,
                    "党务看板还没有数据");
        }
        return Overviews.percent("fee-rate", "党费收缴", ratio(fee.getRate()),
                tone(fee.getRate(), FEE_GOOD, FEE_WARN), "实收 " + fraction(fee));
    }

    /**
     * 三会一课牌。
     *
     * @param hold 开展率指标，可空
     * @return 指标
     */
    private static OverviewKpi holdKpi(MetricItemView hold) {
        if (hold == null) {
            return Overviews.percent("activity-hold", "三会一课", BigDecimal.ZERO, OverviewTone.FLAT,
                    "党务看板还没有数据");
        }
        return Overviews.percent("activity-hold", "三会一课", ratio(hold.getRate()),
                tone(hold.getRate(), ACTIVITY_GOOD, ACTIVITY_WARN), "已开展 " + fraction(hold));
    }

    /**
     * 组织生活参会牌。
     *
     * @param attend 参会率指标，可空
     * @return 指标
     */
    private static OverviewKpi attendanceKpi(MetricItemView attend) {
        if (attend == null) {
            return Overviews.percent("attendance", "组织生活参会", BigDecimal.ZERO, OverviewTone.FLAT,
                    "党务看板还没有数据");
        }
        return Overviews.percent("attendance", "组织生活参会", ratio(attend.getRate()),
                tone(attend.getRate(), ATTENDANCE_GOOD, ATTENDANCE_WARN), "实到 " + fraction(attend));
    }

    /**
     * 党员年龄结构柱图：六段年龄分桶。
     *
     * <p><b>为什么是结构不是趋势。</b>党务的账是存量不是流量：党员数今天比昨天
     * 多一人没有多少信息量，"队伍里什么年龄占大头"才是组织要看的结构。
     * 分桶在服务端定死（年龄未知单独一桶），屏风不译码。</p>
     *
     * @param builder 组装器
     * @param metrics 指标索引
     */
    private static void appendAgeChart(Overviews.Builder builder, Map<MetricType, MetricItemView> metrics) {
        List<MetricType> buckets = List.of(MetricType.MEMBER_AGE_UNDER_30, MetricType.MEMBER_AGE_30_39,
                MetricType.MEMBER_AGE_40_49, MetricType.MEMBER_AGE_50_59,
                MetricType.MEMBER_AGE_60_PLUS, MetricType.MEMBER_AGE_UNKNOWN);
        List<String> categories = buckets.stream().map(MetricType::getLabel).toList();
        List<Double> values = buckets.stream().map(type -> (double) count(metrics.get(type))).toList();
        builder.chart(Overviews.chart("age-structure", "党员年龄结构", "bar", OverviewFormat.COUNT,
                categories, new OverviewSeries("党员数", values)));
    }

    /**
     * 入口：党员台账。
     *
     * @param builder 组装器
     */
    private static void appendLinks(Overviews.Builder builder) {
        builder.link(Overviews.link("members", "党员台账", MEMBER_PAGE_PATH,
                PartyPermissions.MEMBER_LIST));
    }

    /**
     * 告警条：收缴率低、开展率低、参会率低、待转正积压、换届到期、无党员、快照未生成。
     *
     * <p>前几条是"数字本身不会喊"的事：{@code 72%} 不会自己变红，
     * {@code 0} 个党员与"今天没快照"看起来一样。</p>
     *
     * @param builder 组装器
     * @param metrics 指标索引
     * @param view    看板总览
     */
    private static void appendAlerts(Overviews.Builder builder, Map<MetricType, MetricItemView> metrics,
            MetricOverviewView view) {
        MetricItemView fee = metrics.get(MetricType.FEE_COLLECTION_AMOUNT);
        MetricItemView hold = metrics.get(MetricType.ACTIVITY_HOLD);
        MetricItemView attend = metrics.get(MetricType.ACTIVITY_ATTENDANCE);
        MetricItemView probation = metrics.get(MetricType.PROBATION_DUE);
        MetricItemView termDue = metrics.get(MetricType.BRANCH_TERM_DUE);
        MetricItemView total = metrics.get(MetricType.MEMBER_TOTAL);

        if (fee != null && below(fee.getRate(), FEE_WARN)) {
            builder.alert(OverviewAlertLevel.WARNING, "党费收缴率仅 " + pct(fee.getRate())
                    + "，低于八成，欠缴要逐人催", null);
        }
        if (hold != null && below(hold.getRate(), ACTIVITY_WARN)) {
            builder.alert(OverviewAlertLevel.WARNING, "三会一课开展率仅 " + pct(hold.getRate())
                    + "，组织生活欠账", null);
        }
        if (attend != null && below(attend.getRate(), ATTENDANCE_WARN)) {
            builder.alert(OverviewAlertLevel.WARNING, "组织生活参会率仅 " + pct(attend.getRate())
                    + "，多数人没到场", null);
        }
        if (probation != null && count(probation) > 0) {
            builder.alert(OverviewAlertLevel.INFO, "有 " + count(probation)
                    + " 名预备党员到了转正期，转正得有人办手续", null);
        }
        if (termDue != null && numeratorOf(termDue) > 0) {
            builder.alert(OverviewAlertLevel.WARNING, "有 " + numeratorOf(termDue)
                    + " 个支部到了换届期，到期不换要出问题", null);
        }
        if (total == null || count(total) <= 0) {
            builder.alert(OverviewAlertLevel.INFO, "还没有在册党员，宣抚暂时无账可理", null);
        }
        if (view.isLive()) {
            builder.alert(OverviewAlertLevel.INFO, "今日还没有党务快照，这一屏是实时算的（不会写库）", null);
        }
    }

    /**
     * 指标计数：count 型指标取分子（快照的 numerator 就是数量）。
     *
     * @param item 指标，可空
     * @return 数量
     */
    private static long count(MetricItemView item) {
        return item == null || item.getNumerator() == null ? 0L : item.getNumerator().longValue();
    }

    /**
     * 指标分子。
     *
     * @param item 指标，可空
     * @return 分子
     */
    private static long numeratorOf(MetricItemView item) {
        return count(item);
    }

    /**
     * 分子分母文案：写成「a/b」，没有分母时只写分子。
     *
     * @param item 指标，可空
     * @return 文案
     */
    private static String fraction(MetricItemView item) {
        if (item == null || item.getNumerator() == null) {
            return "0/0";
        }
        if (item.getDenominator() == null) {
            return plain(item.getNumerator());
        }
        return plain(item.getNumerator()) + "/" + plain(item.getDenominator());
    }

    /**
     * 百分数 → 契约比率。
     *
     * <p>{@code 94 → 0.94}。本服务的比率由 {@code MetricSnapshot#rate} 乘过 100，
     * 而契约的 PERCENT 自己会乘 100 显示，不除就会得到「9400.0%」。</p>
     *
     * @param percent 百分数，可空
     * @return 比率
     */
    private static BigDecimal ratio(BigDecimal percent) {
        if (percent == null) {
            return BigDecimal.ZERO;
        }
        return percent.divide(HUNDRED, RATE_SCALE, RoundingMode.HALF_UP);
    }

    /**
     * 比率语气：分界以上绿灯、及格线以上黄灯、以下红灯。
     *
     * @param percent 百分数，可空
     * @param good    绿灯分界
     * @param warn    黄灯分界
     * @return 语气
     */
    private static OverviewTone tone(BigDecimal percent, BigDecimal good, BigDecimal warn) {
        if (percent == null) {
            return OverviewTone.FLAT;
        }
        if (percent.compareTo(good) >= 0) {
            return OverviewTone.GOOD;
        }
        return percent.compareTo(warn) >= 0 ? OverviewTone.WARN : OverviewTone.BAD;
    }

    /**
     * 百分数是否低于分界（空值视为不低：没有分母时不该报警）。
     *
     * @param percent  百分数，可空
     * @param boundary 分界
     * @return 低于分界返回 true
     */
    private static boolean below(BigDecimal percent, BigDecimal boundary) {
        return percent != null && percent.compareTo(boundary) < 0;
    }

    /**
     * 百分数文案：取整，避免告警里出现「63.4783%」。
     *
     * @param percent 百分数，可空
     * @return 文案
     */
    private static String pct(BigDecimal percent) {
        if (percent == null) {
            return "0%";
        }
        return percent.setScale(0, RoundingMode.HALF_UP).toPlainString() + "%";
    }

    /**
     * 数值文案：去掉无意义的尾零（{@code 12.00 → 12}）。
     *
     * @param value 数值，可空
     * @return 文案
     */
    private static String plain(BigDecimal value) {
        if (value == null) {
            return "0";
        }
        BigDecimal stripped = value.stripTrailingZeros();
        return stripped.scale() < 0 ? stripped.setScale(0).toPlainString() : stripped.toPlainString();
    }
}
