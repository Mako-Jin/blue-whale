package com.bluewhale.job.overview;

import com.bluewhale.job.service.JobScheduleStatQuery;
import com.bluewhale.job.service.JobScheduleStatQuery.DayRuns;
import com.bluewhale.job.service.JobScheduleStatQuery.FailTop;
import com.bluewhale.job.service.JobScheduleStatQuery.JobCount;
import com.bluewhale.job.service.JobScheduleStatQuery.RunStat;
import com.bluewhale.overview.OverviewContext;
import com.bluewhale.overview.OverviewProvider;
import com.bluewhale.overview.model.OverviewAlertLevel;
import com.bluewhale.overview.model.OverviewFormat;
import com.bluewhale.overview.model.OverviewSnapshot;
import com.bluewhale.overview.model.OverviewTone;
import com.bluewhale.overview.support.Overviews;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 时辰殿（定时调度）概览 provider（S5 / T5.5）。
 *
 * <p><b>这一屏回答的问题：</b>任务还在按点跑吗、今天跑了多少次、失败了几次、有没有任务
 * 悄悄停了。定时任务是典型的"不做声就是正常"的部件——它坏了没人报错，只有下游数据
 * 慢慢变旧；所以这一屏重点不在"多少任务"，而在**"最后一次动是什么时候"**。</p>
 *
 * <p><b>只数已结束的执行</b>（{@code status IN (1, -1)}）：执行中的行在结束时才回写，
 * 把它算进分母会让成功率每隔几秒跳一次，看的人分不清是故障还是刷新。</p>
 *
 * <p><b>静默任务只展示、不单独告警。</b>判据只有"近 24 小时有没有执行记录"，不看 cron 周期
 * ——周级任务在这个窗口里本来就该是空的，逐个报等于制造噪音。只有当**所有**运行中任务
 * 一起静默时才发 `CRITICAL`：那是调度器停摆（或租户上下文丢了），不是某个任务周期长。</p>
 *
 * @author BlueWhale
 */
@Component
public class JobOverviewProvider implements OverviewProvider {

    /** 服务一级前缀（与网关 /job/** 路由一致） */
    private static final String SERVICE = "job";

    /** 城内节点 key（时辰殿），与 city-layout.json 对齐 */
    private static final String NODE_KEY = "shichen-hall";

    /** 概览权限码：与 2D 定时任务页同一码，不为 3D 单独发一套授权 */
    private static final String PERMISSION = "sys:job:list";

    /** 屏风页（定时任务管理）路径：与 2D 后台同路径 */
    private static final String JOB_PAGE_PATH = "/job/v1/info/page";

    /** 趋势天数（含今天） */
    private static final int TREND_DAYS = 7;

    /** 失败次数告警阈值：到这个量级就不是"偶发一次超时" */
    private static final long FAILED_ALERT_COUNT = 5L;

    /** 失败占比告警阈值 */
    private static final double FAILED_RATE_ALERT = 0.3D;

    /** 谈失败占比的最小样本：样本太少时占比没有意义（1 失败 0 成功就是 100%） */
    private static final long RATE_MIN_SAMPLE = 10L;

    /** 静默判定窗口：超过它一次都没执行，按"没在跑"计入观察值 */
    private static final Duration SILENT_WINDOW = Duration.ofHours(24);

    /** 失败 Top 取前几名（屏风柱图横轴只放得下 5 个类目） */
    private static final int FAIL_TOP_N = 5;

    /** 柱图类目名最大字数：再长会把纵轴挤没 */
    private static final int CATEGORY_MAX_CHARS = 8;

    /** 横轴标签：月-日 */
    private static final DateTimeFormatter DAY_LABEL = DateTimeFormatter.ofPattern("MM-dd", Locale.CHINA);

    /** 统计口径 */
    private final JobScheduleStatQuery statQuery;

    /**
     * 构造 provider
     *
     * @param statQuery 统计口径
     */
    public JobOverviewProvider(JobScheduleStatQuery statQuery) {
        this.statQuery = statQuery;
    }

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
        return "时辰殿";
    }

    @Override
    public String subtitle() {
        return "定时调度";
    }

    @Override
    public String permissionCode() {
        return PERMISSION;
    }

    @Override
    public OverviewSnapshot build(OverviewContext context) {
        Long tenantId = context == null ? null : context.tenantId();
        LocalDate today = LocalDate.now();
        LocalDateTime dayStart = today.atStartOfDay();
        LocalDateTime dayEnd = today.plusDays(1L).atStartOfDay();
        LocalDateTime trendStart = today.minusDays(TREND_DAYS - 1L).atStartOfDay();
        LocalDateTime now = LocalDateTime.now();

        /* 口径实现返回 null 时按零处理：概览是常驻轮询，不能因为一次空返回把整座殿
           标成"没数"——"今天没跑任务"与"取不到数"是两件事。 */
        JobCount jobs = countOrZero(statQuery.jobCount(tenantId));
        RunStat todayRuns = runOrZero(statQuery.runsBetween(tenantId, dayStart, dayEnd));
        long failedJobs = statQuery.failedJobCount(tenantId, dayStart, dayEnd);
        long silent = statQuery.silentRunning(tenantId, now.minus(SILENT_WINDOW));
        Map<LocalDate, Long> runsByDay = indexByDay(
                statQuery.dailyRuns(tenantId, trendStart, dayEnd));
        List<FailTop> failTop = statQuery.failTop(tenantId, trendStart, dayEnd, FAIL_TOP_N);

        Overviews.Builder builder = Overviews.builder(SERVICE, NODE_KEY, title())
                .subtitle(subtitle() + " · " + DAY_LABEL.format(today))
                .kpi(Overviews.count("jobs", "任务总数", jobs.jobs(), OverviewTone.FLAT,
                        runningHint(jobs)))
                .kpi(Overviews.count("runs", "今日执行", todayRuns.total(), OverviewTone.FLAT,
                        successHint(todayRuns)))
                .kpi(Overviews.count("failures", "今日失败", todayRuns.failure(),
                        todayRuns.failure() > 0 ? OverviewTone.WARN : OverviewTone.FLAT,
                        "涉及 " + failedJobs + " 个任务"))
                .kpi(Overviews.count("quiet", "静默任务", silent,
                        silent > 0 ? OverviewTone.WARN : OverviewTone.FLAT,
                        "运行中 · 24h 无执行"))
                .chart(Overviews.chart("run-trend", "近 " + TREND_DAYS + " 日执行", "line",
                        OverviewFormat.COUNT, labels(today),
                        Overviews.series("执行", seriesOf(runsByDay, today))))
                .chart(Overviews.chart("fail-top", "近 " + TREND_DAYS + " 日失败最多", "bar",
                        OverviewFormat.COUNT, failLabels(failTop),
                        Overviews.series("失败", failValues(failTop))))
                .note("口径：只数已结束的执行（成功 + 失败，不含执行中），静默任务按 24 小时窗口、"
                        + "只算运行中的任务，均限本租户");
        if (tenantId == null) {
            /* 没租户上下文时四个数都会是 0——不写清楚，看的人会以为任务全都停了。 */
            builder.note("未识别租户上下文，仅展示空口径（请从网关携带租户头访问）");
        }
        if (context != null && context.hasPermission(PERMISSION)) {
            builder.link(Overviews.link("jobs", "定时任务", JOB_PAGE_PATH, PERMISSION));
        }
        appendAlerts(builder, jobs, todayRuns, failedJobs, silent);
        return builder.build();
    }

    /**
     * 任务数兜底
     *
     * @param count 口径返回值，可空
     * @return 非空统计
     */
    private JobCount countOrZero(JobCount count) {
        return count == null ? new JobCount(0L, 0L) : count;
    }

    /**
     * 执行统计兜底
     *
     * @param stat 口径返回值，可空
     * @return 非空统计
     */
    private RunStat runOrZero(RunStat stat) {
        return stat == null ? new RunStat(0L, 0L, 0L) : stat;
    }

    /**
     * 任务总数 KPI 的副标：运行中与已停止分开说
     *
     * @param jobs 任务统计
     * @return 副标文案
     */
    private String runningHint(JobCount jobs) {
        long stopped = Math.max(0L, jobs.jobs() - jobs.running());
        return "运行中 " + jobs.running() + " · 已停 " + stopped;
    }

    /**
     * 今日执行 KPI 的副标：成功率只在有已结束执行时才有意义
     *
     * @param stat 今日执行统计
     * @return 副标文案
     */
    private String successHint(RunStat stat) {
        if (stat.total() <= 0L) {
            return "今日尚未执行";
        }
        long percent = Math.round(stat.success() * 100D / stat.total());
        return "成功 " + stat.success() + " · " + percent + "%";
    }

    /**
     * 把按天聚合折成"日期 → 次数"
     *
     * @param rows 聚合行
     * @return 日期索引；入参为空时返回空表
     */
    private Map<LocalDate, Long> indexByDay(List<DayRuns> rows) {
        Map<LocalDate, Long> byDay = new HashMap<>();
        if (rows == null) {
            return byDay;
        }
        for (DayRuns row : rows) {
            if (row.day() != null) {
                byDay.merge(row.day(), row.runs(), Long::sum);
            }
        }
        return byDay;
    }

    /**
     * 趋势图横轴（含今天共 7 天）
     *
     * @param today 今天
     * @return 标签列表，从最早到今天
     */
    private List<String> labels(LocalDate today) {
        List<String> labels = new ArrayList<>(TREND_DAYS);
        for (int i = TREND_DAYS - 1; i >= 0; i--) {
            labels.add(DAY_LABEL.format(today.minusDays(i)));
        }
        return labels;
    }

    /**
     * 趋势图数值（缺数据的日期补 0，且顺序与横轴一致）
     *
     * @param byDay 日期索引
     * @param today 今天
     * @return 每天一个次数
     */
    private Number[] seriesOf(Map<LocalDate, Long> byDay, LocalDate today) {
        Number[] values = new Number[TREND_DAYS];
        for (int i = 0; i < TREND_DAYS; i++) {
            values[i] = byDay.getOrDefault(today.minusDays(TREND_DAYS - 1L - i), 0L);
        }
        return values;
    }

    /**
     * 失败 Top 图横轴
     *
     * @param rows 聚合行
     * @return 任务名（截断）；近 7 日无失败时给一个占位类目
     *         ——空类目在屏风上会画成一条白线，看着像加载失败
     */
    private List<String> failLabels(List<FailTop> rows) {
        if (rows == null || rows.isEmpty()) {
            return List.of("近 " + TREND_DAYS + " 日无失败");
        }
        List<String> labels = new ArrayList<>(rows.size());
        for (FailTop row : rows) {
            labels.add(shortName(row.jobName()));
        }
        return labels;
    }

    /**
     * 失败 Top 图数值
     *
     * @param rows 聚合行
     * @return 每个任务的失败次数；无失败时给一个 0
     */
    private Number[] failValues(List<FailTop> rows) {
        if (rows == null || rows.isEmpty()) {
            return new Number[]{0L};
        }
        Number[] values = new Number[rows.size()];
        for (int i = 0; i < rows.size(); i++) {
            values[i] = rows.get(i).failures();
        }
        return values;
    }

    /**
     * 任务名截断（柱图类目位只放得下十来个字）
     *
     * <p>只做展示截断，不参与匹配：两个长名截成一样时柱图上看不出区别，
     * 但要点名到具体任务还得看执行日志页。</p>
     *
     * @param name 任务名
     * @return 截断后的展示名
     */
    private String shortName(String name) {
        if (name == null || name.isBlank()) {
            return "未命名任务";
        }
        return name.length() <= CATEGORY_MAX_CHARS
                ? name
                : name.substring(0, CATEGORY_MAX_CHARS) + "…";
    }

    /**
     * 追加告警：调度停摆与失败量各一条
     *
     * <p>两条根因不同，可以同时出现：全都静默是"调度器没在跑"，失败多但仍在跑是
     * "任务自己在报错"。前者要去看调度线程与租户上下文，后者去看执行日志。</p>
     *
     * <p>失败量先判（{@code CRITICAL}），占比作补刀（{@code WARNING}）——同一个处置动作
     * （去看执行日志），所以不并列。样本小于 {@value #RATE_MIN_SAMPLE} 不谈占比。</p>
     *
     * @param builder   组装器
     * @param jobs      任务统计
     * @param todayRuns 今日执行统计
     * @param failedJobs 今日出现过失败的任务数
     * @param silent    静默任务数
     */
    private void appendAlerts(Overviews.Builder builder, JobCount jobs, RunStat todayRuns,
                              long failedJobs, long silent) {
        if (jobs.running() > 0L && silent >= jobs.running()) {
            builder.alert(OverviewAlertLevel.CRITICAL,
                    "运行中任务 " + jobs.running() + " 个近 24 小时全部没有执行，调度器可能停摆",
                    (int) silent);
        }
        if (todayRuns.failure() >= FAILED_ALERT_COUNT) {
            builder.alert(OverviewAlertLevel.CRITICAL,
                    "今日任务失败 " + todayRuns.failure() + " 次，涉及 " + failedJobs + " 个任务",
                    (int) todayRuns.failure());
        } else if (todayRuns.total() >= RATE_MIN_SAMPLE
                && todayRuns.failure() * 1D / todayRuns.total() >= FAILED_RATE_ALERT) {
            long percent = Math.round(todayRuns.failure() * 100D / todayRuns.total());
            builder.alert(OverviewAlertLevel.WARNING,
                    "今日失败占比 " + percent + "%，涉及 " + failedJobs + " 个任务",
                    (int) todayRuns.failure());
        }
    }
}
