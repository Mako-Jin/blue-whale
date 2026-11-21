package com.bluewhale.auth.starter.overview;

import com.bluewhale.auth.application.port.AuthSecurityStatQuery;
import com.bluewhale.auth.application.port.AuthSecurityStatQuery.LoginDayCount;
import com.bluewhale.overview.OverviewContext;
import com.bluewhale.overview.OverviewProvider;
import com.bluewhale.overview.model.OverviewAlertLevel;
import com.bluewhale.overview.model.OverviewFormat;
import com.bluewhale.overview.model.OverviewSnapshot;
import com.bluewhale.overview.model.OverviewTone;
import com.bluewhale.overview.support.Overviews;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 枢密殿（权限认证）概览：城内身份与授权这一路的第一个真节点（T5.2）。
 *
 * <p><b>为什么指标选这四个。</b>枢密殿值班时真正要回答的是三件事：还有人能登录吗
 * （启用账号）、现在有多少人在线（在用会话）、今天登录正不正常（成功 / 失败）。
 * 失败数是唯一需要"越红越好"的指标，所以它单独一张牌，语气随数值走。</p>
 *
 * <p><b>为什么不在这里 catch 异常。</b>数据库挂了就让它抛——SDK 会降级成
 * {@code stale}（沿用上次快照）或 {@code degraded}（闭门态）。provider 自己吞掉异常
 * 返回四个 0，会让"认证库挂了"长得跟"这个租户一个人都没有"一模一样。</p>
 *
 * @author BlueWhale
 */
@Component
@RequiredArgsConstructor
public class AuthOverviewProvider implements OverviewProvider {

    /** 服务一级前缀（与网关 /auth/** 路由一致） */
    private static final String SERVICE = "auth";

    /** 城内节点 key（枢密殿），与 city-layout.json 对齐 */
    private static final String NODE_KEY = "shumi-hall";

    /** 概览权限码：与 2D 登录历史页同一码，不为 3D 单独发一套授权 */
    private static final String PERMISSION = "sys:login:history:list";

    /** 屏风页（登录历史）路径：与 2D 后台同路径，网关 /login-history/v1/** 已登记 */
    private static final String HISTORY_PAGE_PATH = "/login-history/v1/page";

    /** 登录结果码：成功 */
    private static final String RESULT_SUCCESS = "SUCCESS";

    /** 登录结果码：失败 */
    private static final String RESULT_FAILURE = "FAILURE";

    /** 登录结果码：登出 */
    private static final String RESULT_LOGOUT = "LOGOUT";

    /** 趋势天数（含今天） */
    private static final int TREND_DAYS = 7;

    /** 今日失败次数告警阈值：超过它基本可以判定有人在撞库 */
    private static final long FAILURE_ALERT_COUNT = 20L;

    /** 失败占比告警的最小样本量：5 次以下谈占比没有意义（1 失败 0 成功 = 100%） */
    private static final long FAILURE_RATIO_MIN_COUNT = 5L;

    /** 失败占比告警阈值 */
    private static final double FAILURE_RATIO_ALERT = 0.3d;

    /** 横轴标签：月-日 */
    private static final DateTimeFormatter DAY_LABEL = DateTimeFormatter.ofPattern("MM-dd", Locale.CHINA);

    /** 统计口径 */
    private final AuthSecurityStatQuery statQuery;

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
        return "枢密殿";
    }

    @Override
    public String subtitle() {
        return "身份与授权";
    }

    @Override
    public String permissionCode() {
        return PERMISSION;
    }

    @Override
    public OverviewSnapshot build(OverviewContext context) {
        Long tenantId = context == null ? null : context.tenantId();
        LocalDate today = LocalDate.now();
        LocalDateTime now = LocalDateTime.now();

        long enabledUsers = statQuery.countEnabledUsers(tenantId);
        long activeSessions = statQuery.countActiveSessions(tenantId, now);
        List<LoginDayCount> trend = statQuery.countLoginByDay(tenantId,
                today.minusDays(TREND_DAYS - 1L), today.plusDays(1L));
        Map<LocalDate, long[]> byDay = pivot(trend);

        long[] todayCounts = byDay.getOrDefault(today, new long[]{0L, 0L, 0L});
        long successToday = todayCounts[0];
        long failureToday = todayCounts[1];
        long logoutToday = todayCounts[2];

        Overviews.Builder builder = Overviews.builder(SERVICE, NODE_KEY, title())
                .subtitle(subtitle() + " · " + DAY_LABEL.format(today))
                .kpi(Overviews.count("users", "启用账号", enabledUsers))
                .kpi(Overviews.count("sessions", "在用会话", activeSessions))
                .kpi(Overviews.count("logins", "今日登录", successToday, OverviewTone.GOOD, "成功"))
                .kpi(Overviews.count("failures", "今日失败", failureToday,
                        failureToday > 0 ? OverviewTone.WARN : OverviewTone.GOOD, "次"))
                /* 趋势两张图取同一份聚合：分开查会在跨零点时对不上，
                   而"今日失败 3 次、图上今天却有 5 根"这种矛盾没人能解释。 */
                .chart(Overviews.chart("login-trend", "近 " + TREND_DAYS + " 日登录（成功 / 失败）",
                        "line", OverviewFormat.COUNT, labels(today),
                        Overviews.series("成功", seriesOf(byDay, today, 0)),
                        Overviews.series("失败", seriesOf(byDay, today, 1))))
                .chart(Overviews.chart("today-mix", "今日登录结果构成", "bar", OverviewFormat.COUNT,
                        List.of("成功", "失败", "登出"),
                        Overviews.series("次数", successToday, failureToday, logoutToday)))
                .note("口径：启用账号（未删除且启用）、在用会话（未撤销且未过期）、"
                        + "登录结果按事件时间统计，均限本租户");
        if (tenantId == null) {
            /* 没租户上下文时四个数都会是 0——不写清楚，看的人会以为这个租户真的没人。 */
            builder.note("未识别租户上下文，仅展示空口径（请从网关携带租户头访问）");
        }
        if (context != null && context.hasPermission(PERMISSION)) {
            builder.link(Overviews.link("login-history", "登录历史", HISTORY_PAGE_PATH, PERMISSION));
        }
        appendAlert(builder, successToday, failureToday);
        return builder.build();
    }

    /**
     * 把聚合行按天归并成三槽（成功 / 失败 / 登出）
     *
     * @param rows 聚合行
     * @return 日期 → 三槽次数
     */
    private Map<LocalDate, long[]> pivot(List<LoginDayCount> rows) {
        Map<LocalDate, long[]> byDay = new HashMap<>();
        if (rows == null) {
            return byDay;
        }
        for (LoginDayCount row : rows) {
            int slot = slotOf(row.loginResult());
            if (row.day() == null || slot < 0) {
                continue;
            }
            byDay.computeIfAbsent(row.day(), day -> new long[3])[slot] += row.total();
        }
        return byDay;
    }

    /**
     * 结果码 → 槽位
     *
     * @param loginResult 结果码
     * @return 0 成功 / 1 失败 / 2 登出；未知结果返回 -1（不认识的结果不猜，直接不计）
     */
    private int slotOf(String loginResult) {
        if (RESULT_SUCCESS.equals(loginResult)) {
            return 0;
        }
        if (RESULT_FAILURE.equals(loginResult)) {
            return 1;
        }
        if (RESULT_LOGOUT.equals(loginResult)) {
            return 2;
        }
        return -1;
    }

    /**
     * 横轴标签：近 {@value #TREND_DAYS} 天，最后一个是今天
     *
     * @param today 今天
     * @return 标签列表
     */
    private List<String> labels(LocalDate today) {
        List<String> labels = new ArrayList<>(TREND_DAYS);
        for (int i = TREND_DAYS - 1; i >= 0; i--) {
            labels.add(DAY_LABEL.format(today.minus(i, ChronoUnit.DAYS)));
        }
        return List.copyOf(labels);
    }

    /**
     * 取某槽在近 {@value #TREND_DAYS} 天上的序列（缺日补 0）
     *
     * <p>缺日必须补 0：直接跳过空日会让折线把"8 天前的数据"接到"昨天"，
     * 图上看起来是连续登录，实际中间停了三天。</p>
     *
     * @param byDay 按天聚合
     * @param today 今天
     * @param slot 槽位
     * @return 数值序列
     */
    private Number[] seriesOf(Map<LocalDate, long[]> byDay, LocalDate today, int slot) {
        Number[] values = new Number[TREND_DAYS];
        for (int i = 0; i < TREND_DAYS; i++) {
            LocalDate day = today.minus(TREND_DAYS - 1L - i, ChronoUnit.DAYS);
            long[] counts = byDay.get(day);
            values[i] = counts == null ? 0L : counts[slot];
        }
        return values;
    }

    /**
     * 追加登录失败告警
     *
     * <p>两条规则只出一条：次数先判，占比作补刀。同时报两条会让值班的人
     * 分不清"是量大事"还是"是比例事"，而这两件事的处置动作是一样的（去看登录历史）。</p>
     *
     * @param builder 组装器
     * @param successToday 今日成功次数
     * @param failureToday 今日失败次数
     */
    private void appendAlert(Overviews.Builder builder, long successToday, long failureToday) {
        if (failureToday >= FAILURE_ALERT_COUNT) {
            builder.alert(OverviewAlertLevel.CRITICAL,
                    "今日登录失败 " + failureToday + " 次，疑似撞库，请查登录历史", (int) failureToday);
            return;
        }
        long attempts = successToday + failureToday;
        if (attempts > 0 && failureToday >= FAILURE_RATIO_MIN_COUNT
                && (double) failureToday / attempts >= FAILURE_RATIO_ALERT) {
            builder.alert(OverviewAlertLevel.WARNING,
                    String.format(Locale.CHINA, "今日失败占比 %.0f%%，高于常态",
                            failureToday * 100d / attempts), (int) failureToday);
        }
    }
}
