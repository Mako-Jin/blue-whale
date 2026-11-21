package com.bluewhale.lms.analytics.interfaces.overview;

import com.bluewhale.lms.analytics.application.service.AnalyticsAppService;
import com.bluewhale.lms.analytics.application.view.AnalyticsOverviewView;
import com.bluewhale.lms.analytics.application.view.MetricSnapshotView;
import com.bluewhale.lms.common.LmsActor;
import com.bluewhale.lms.common.LmsPermissions;
import com.bluewhale.overview.OverviewContext;
import com.bluewhale.overview.OverviewProvider;
import com.bluewhale.overview.model.OverviewAlertLevel;
import com.bluewhale.overview.model.OverviewFormat;
import com.bluewhale.overview.model.OverviewKpi;
import com.bluewhale.overview.model.OverviewSeries;
import com.bluewhale.overview.model.OverviewSnapshot;
import com.bluewhale.overview.model.OverviewTone;
import com.bluewhale.overview.support.Overviews;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 杏坛（LMS）概览：内城十二坊的第五个坊——教化。
 *
 * <p><b>这一屏讲什么。</b>杏坛掌教化：城里有几个该读书的人、有多少人真的开始读了、
 * 开始读的人里有多少读完、考试有多少人过关。它不重复任何一殿——吏部看考勤、
 * 工部看产线、礼部看客户、兵部看哨探；杏坛看的是<b>人有没有在长本事</b>，
 * 而这件事在 2D 后台要切到学习看板、再滚动到三块指标才看得全。</p>
 *
 * <p><b>口径不另造。</b>所有数字来自 {@link AnalyticsAppService#overview}，
 * 与 2D 学习看板同一个方法：有当日快照读快照、没有就实时算一份但不落库。
 * 三个比率（覆盖 / 完成 / 通过）本来是百分数，进契约前统一除以 100 ——
 * 契约的 {@link OverviewFormat#PERCENT} 吃的是 0.94 这种比率，
 * 直接把 94 塞进去，屏风上会显示「9400.0%」。</p>
 *
 * <p><b>取不到数就让它抛。</b>这里不 catch、不返回 0：库连不上与"今天没人在学"
 * 长得一模一样。SDK 会把异常降级成 stale/degraded，屏风上写"未接通"。</p>
 *
 * @author BlueWhale
 */
@Component
@RequiredArgsConstructor
public class LmsOverviewProvider implements OverviewProvider {

    /** 服务一级前缀，与网关路由一致 */
    private static final String SERVICE = "lms";

    /** 城内节点 key（杏坛坊），与 {@code city-layout.json} 对齐 */
    private static final String NODE_KEY = "xingtan-hall";

    /** 权限码：复用 2D 学习看板同一码，不为 3D 单独发一套授权 */
    private static final String PERMISSION = LmsPermissions.ANALYTICS_VIEW;

    /** 课程目录列表接口：屏风页直接取这个路径 */
    private static final String COURSE_PAGE_PATH = "/lms/course/page";

    /** 百分数 → 比率的除数 */
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    /** 比率保留位数，与契约 {@code 94.0%} 的展示精度一致 */
    private static final int RATE_SCALE = 4;

    /** 学习覆盖率"健康"分界（%）：≥ 它给绿灯 */
    private static final BigDecimal COVERAGE_GOOD = BigDecimal.valueOf(80);

    /** 学习覆盖率"及格"分界（%）：≥ 它给黄灯，低于它给红灯 */
    private static final BigDecimal COVERAGE_WARN = BigDecimal.valueOf(60);

    /** 学习完成率"健康"分界（%）：分母是已开始学的人 */
    private static final BigDecimal COMPLETION_GOOD = BigDecimal.valueOf(80);

    /** 学习完成率"及格"分界（%） */
    private static final BigDecimal COMPLETION_WARN = BigDecimal.valueOf(50);

    /** 考试通过率"健康"分界（%） */
    private static final BigDecimal PASS_GOOD = BigDecimal.valueOf(80);

    /** 考试通过率"及格"分界（%） */
    private static final BigDecimal PASS_WARN = BigDecimal.valueOf(60);

    /** 趋势最多取多少个统计日：30 个点在 830px 里会糊成一条粗线 */
    private static final int TREND_POINTS = 14;

    /** 横轴标签格式：契约的 categories 只放短标签，年份由副标题交代 */
    private static final DateTimeFormatter DAY_LABEL = DateTimeFormatter.ofPattern("MM-dd");

    /** 学习看板口径 */
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
        return "杏坛";
    }

    @Override
    public String subtitle() {
        return "学习教化";
    }

    @Override
    public String permissionCode() {
        return PERMISSION;
    }

    @Override
    public OverviewSnapshot build(OverviewContext context) {
        AnalyticsOverviewView view = analyticsAppService.overview(null, actorOf(context));
        Overviews.Builder builder = Overviews.builder(SERVICE, NODE_KEY, title())
                .subtitle(subtitle() + " · 近 " + TREND_POINTS + " 个统计日")
                .note(noteOf(view));
        appendKpis(builder, view);
        appendTrendChart(builder, view);
        appendLinks(builder);
        appendAlerts(builder, view);
        return builder.build();
    }

    /**
     * 由概览上下文还原 LMS 操作人。
     *
     * <p>只带 userId / 角色 / 权限，不带用户名：学习看板是聚合数，不显示人名。
     * 上下文为空（内网直连、老调用方）时给匿名——看板只读，不像写操作那样必须失败关闭。</p>
     *
     * @param context 概览上下文，可空
     * @return 操作人
     */
    private static LmsActor actorOf(OverviewContext context) {
        if (context == null) {
            return LmsActor.anonymous();
        }
        return new LmsActor(context.userId(), null, context.roleCodes(), context.permissionCodes());
    }

    /**
     * 口径说明：把三个分母写清楚，再加学分总量。
     *
     * <p>「覆盖率」与「完成率」最容易被读成同一个东西——覆盖率的分母是在职人数、
     * 完成率的分母是已开始学的人。同一屏幕上两个百分比并排时，不写分母就等于让人猜。</p>
     *
     * @param view 看板总览
     * @return 说明文字
     */
    private static String noteOf(AnalyticsOverviewView view) {
        return "口径：覆盖 = 已学 ÷ 在职（" + view.getLearnerTotal() + "/" + view.getMemberTotal()
                + "），完成 = 学完 ÷ 已学（" + view.getLearnerCompleted() + "/" + view.getLearnerTotal()
                + "），通过 = 通过 ÷ 参考（" + view.getExamPassedTotal() + "/" + view.getExamTakerTotal()
                + "）；学分累计 " + plain(view.getCreditTotal()) + " 分，"
                + view.getCreditEarners() + " 人获得";
    }

    /**
     * 四张牌：在册学员、学习覆盖、学习完成、考试通过。
     *
     * <p>四张都是"比率与人数成对出现"——只给 63% 看不出是 63/100 还是 6300/10000，
     * 所以每张比率牌的提示语都写出分子分母。</p>
     *
     * @param builder 组装器
     * @param view    看板总览
     */
    private static void appendKpis(Overviews.Builder builder, AnalyticsOverviewView view) {
        builder.kpi(Overviews.count("learners", "在册学员", view.getMemberTotal(), OverviewTone.FLAT,
                        "已开始学 " + view.getLearnerTotal() + " 人"))
                .kpi(coverageKpi(view))
                .kpi(completionKpi(view))
                .kpi(passRateKpi(view));
    }

    /**
     * 学习覆盖牌。
     *
     * @param view 看板总览
     * @return 指标
     */
    private static OverviewKpi coverageKpi(AnalyticsOverviewView view) {
        boolean noMembers = view.getMemberTotal() <= 0;
        return Overviews.percent("coverage", "学习覆盖", ratio(view.getCoverageRate()),
                noMembers ? OverviewTone.FLAT
                        : tone(view.getCoverageRate(), COVERAGE_GOOD, COVERAGE_WARN),
                noMembers ? "尚无在职人数"
                        : "已学 " + view.getLearnerTotal() + "/" + view.getMemberTotal());
    }

    /**
     * 学习完成牌。
     *
     * @param view 看板总览
     * @return 指标
     */
    private static OverviewKpi completionKpi(AnalyticsOverviewView view) {
        boolean noLearners = view.getLearnerTotal() <= 0;
        return Overviews.percent("completion", "学习完成", ratio(view.getCompletionRate()),
                noLearners ? OverviewTone.FLAT
                        : tone(view.getCompletionRate(), COMPLETION_GOOD, COMPLETION_WARN),
                noLearners ? "尚无人开始学"
                        : "学完 " + view.getLearnerCompleted() + "/" + view.getLearnerTotal());
    }

    /**
     * 考试通过牌。
     *
     * @param view 看板总览
     * @return 指标
     */
    private static OverviewKpi passRateKpi(AnalyticsOverviewView view) {
        boolean noTakers = view.getExamTakerTotal() <= 0;
        return Overviews.percent("pass-rate", "考试通过", ratio(view.getPassRate()),
                noTakers ? OverviewTone.FLAT : tone(view.getPassRate(), PASS_GOOD, PASS_WARN),
                noTakers ? "本月无人参考"
                        : "通过 " + view.getExamPassedTotal() + "/" + view.getExamTakerTotal());
    }

    /**
     * 近若干统计日的趋势折线：学习人数与完成人数。
     *
     * <p><b>为什么两条线放在一张图里。</b>两个序列同量纲（都是人数），可以直接比；
     * 覆盖率与通过率是比率，混进来 Y 轴就没法标度。两条线的间距就是"开始了但没学完"的人——
     * 这个差在看板上正是最该被看见的那一块。</p>
     *
     * <p><b>为什么只取最近 14 个点。</b>看板快照留 30 天，30 个点在 830px 宽的屏风上
     * 每个点只有 27px，密集段会糊成一条粗线；两周的窗口足够看出"这两周有没有人在学"。</p>
     *
     * @param builder 组装器
     * @param view    看板总览
     */
    private static void appendTrendChart(Overviews.Builder builder, AnalyticsOverviewView view) {
        List<MetricSnapshotView> trend = view.getTrend();
        if (trend == null || trend.isEmpty()) {
            return;
        }
        List<MetricSnapshotView> recent = trend.size() > TREND_POINTS
                ? trend.subList(trend.size() - TREND_POINTS, trend.size()) : trend;
        List<String> categories = recent.stream()
                .map(point -> point.getStatDate() == null ? "" : DAY_LABEL.format(point.getStatDate()))
                .toList();
        List<Double> learners = recent.stream().map(point -> (double) point.getLearnerTotal()).toList();
        List<Double> completed = recent.stream().map(point -> (double) point.getLearnerCompleted()).toList();
        builder.chart(Overviews.chart("learn-trend", "学习与完成人数走势", "line", OverviewFormat.COUNT,
                categories,
                new OverviewSeries("学习人数", learners),
                new OverviewSeries("完成人数", completed)));
    }

    /**
     * 入口：课程目录。
     *
     * <p>只挂课程一个入口：培训班、考试、证书在 2D 里才有报名与交卷操作，
     * 城里点进去也只能看；课程目录是纯只读的清单，正适合摆在屏风上。</p>
     *
     * @param builder 组装器
     */
    private static void appendLinks(Overviews.Builder builder) {
        builder.link(Overviews.link("courses", "课程目录", COURSE_PAGE_PATH, LmsPermissions.COURSE_LIST));
    }

    /**
     * 告警条：无人可教、覆盖偏低、学了没学完、通过率低、快照未生成、趋势不可用。
     *
     * <p>前四条是"数字本身不会喊"的事：{@code 62%} 不会自己变红，{@code 0} 个学员与
     * "今天没人来上课"看起来一样。后两条说的是"这一屏的数从哪来"——
     * 今天没快照说明刷新任务没跑，趋势空说明从来没有跑过，两件事的处理方式不同。</p>
     *
     * @param builder 组装器
     * @param view    看板总览
     */
    private static void appendAlerts(Overviews.Builder builder, AnalyticsOverviewView view) {
        if (view.getMemberTotal() == 0) {
            builder.alert(OverviewAlertLevel.INFO, "还没有员工档案，杏坛暂时无生可教", null);
        }
        if (view.getMemberTotal() > 0 && below(view.getCoverageRate(), COVERAGE_WARN)) {
            builder.alert(OverviewAlertLevel.WARNING, "学习覆盖率仅 " + pct(view.getCoverageRate())
                    + "，多数人还没开始学", null);
        }
        if (view.getLearnerTotal() > 0 && below(view.getCompletionRate(), COMPLETION_WARN)) {
            builder.alert(OverviewAlertLevel.WARNING, "已开始学的人里只有 " + pct(view.getCompletionRate())
                    + " 学完，课程可能太长或没人催", null);
        }
        if (view.getExamTakerTotal() > 0 && below(view.getPassRate(), PASS_WARN)) {
            builder.alert(OverviewAlertLevel.WARNING, "考试通过率 " + pct(view.getPassRate())
                    + "，低于六成", null);
        }
        if (view.isLive()) {
            builder.alert(OverviewAlertLevel.INFO, "今日还没有看板快照，这一屏是实时算的（不会写库）", null);
        }
        if (view.getTrend() == null || view.getTrend().isEmpty()) {
            builder.alert(OverviewAlertLevel.INFO, "还没有历史快照，趋势线要等刷新任务跑过才有", null);
        }
    }

    /**
     * 百分数 → 契约比率。
     *
     * <p>{@code 94 → 0.94}。本服务的三个比率由 {@code MetricSnapshot#rate} 乘过 100，
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
     * 比率语气：分母为 0（比率为 0 且没数据）时用灰，不把"还没开始"画成红灯。
     *
     * @param percent   百分数，可空
     * @param good      绿灯分界
     * @param warn      黄灯分界
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
