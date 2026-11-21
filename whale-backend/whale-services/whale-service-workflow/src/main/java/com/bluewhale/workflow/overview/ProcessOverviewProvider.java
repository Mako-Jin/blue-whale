package com.bluewhale.workflow.overview;

import com.bluewhale.overview.OverviewContext;
import com.bluewhale.overview.OverviewProvider;
import com.bluewhale.overview.model.OverviewAlertLevel;
import com.bluewhale.overview.model.OverviewFormat;
import com.bluewhale.overview.model.OverviewSnapshot;
import com.bluewhale.overview.model.OverviewTone;
import com.bluewhale.overview.support.Overviews;
import com.bluewhale.workflow.service.ProcessOverviewStatQuery;
import com.bluewhale.workflow.service.ProcessOverviewStatQuery.Backlog;
import com.bluewhale.workflow.service.ProcessOverviewStatQuery.DayFlow;
import com.bluewhale.workflow.service.ProcessOverviewStatQuery.InstanceStat;
import com.bluewhale.workflow.service.ProcessOverviewStatQuery.OverdueStat;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 政事殿（工作流）概览 provider（S5 / T5.6）。
 *
 * <p><b>这一屏回答的问题：</b>在途的奏折有多少、今天批了几件、还有多少压着没批、
 * 有没有哪条审批链**断在半路**。审批系统的特点是"卡住不报错"——实例一直显示
 * 运行中，但没人有待办，于是既不会超时提醒、也不会失败告警，只有发起人在等。</p>
 *
 * <p><b>卡住实例（运行中且没有任何待办）单独占一张 KPI</b>，而不是只写在告警里：
 * 它是本屏最不可替代的一个数。其它三个（在途 / 办结 / 待办）在 2D 列表页翻一翻
 * 也能拼出来，而"运行中却没人有待办"只有把两张表对起来才看得见。</p>
 *
 * <p><b>办结只算审批通过</b>（{@code status = 1} 且看 {@code finished_at}）：
 * {@code finished_at} 本就只在通过时写入（V10），驳回与撤销停在 {@code update_time}。
 * 把三种终态混成一个"办结"，会让"办结数"在驳回率高的时候反而更高。</p>
 *
 * @author BlueWhale
 */
@Component
public class ProcessOverviewProvider implements OverviewProvider {

    /** 服务一级前缀（与网关 /workflow/** 路由一致） */
    private static final String SERVICE = "workflow";

    /** 城内节点 key（政事殿），与 city-layout.json 对齐 */
    private static final String NODE_KEY = "zhengshi-hall";

    /** 概览权限码：与 2D 流程实例页同一码，不为 3D 单独发一套授权 */
    private static final String PERMISSION = "sys:workflow:instance:list";

    /** 屏风页（流程实例台账）路径：与 2D 后台同路径 */
    private static final String INSTANCE_PAGE_PATH = "/workflow/v1/instance/page";

    /** 趋势天数（含今天） */
    private static final int TREND_DAYS = 7;

    /** 超时待办告警阈值：到这个量级就不是"某个人今天没空" */
    private static final long OVERDUE_ALERT_COUNT = 10L;

    /** 积压柱图取前几名（屏风柱图只放得下 5 个类目） */
    private static final int BACKLOG_TOP_N = 5;

    /** 柱图类目名最大字数：再长会把标签列挤没 */
    private static final int CATEGORY_MAX_CHARS = 8;

    /** 横轴标签：月-日 */
    private static final DateTimeFormatter DAY_LABEL = DateTimeFormatter.ofPattern("MM-dd", Locale.CHINA);

    /** 统计口径 */
    private final ProcessOverviewStatQuery statQuery;

    /**
     * 构造 provider
     *
     * @param statQuery 统计口径
     */
    public ProcessOverviewProvider(ProcessOverviewStatQuery statQuery) {
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
        return "政事殿";
    }

    @Override
    public String subtitle() {
        return "工作流";
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

        /* 口径实现返回 null / 空表时按零处理：概览是常驻轮询，不能因为一次空返回把整座殿
           标成"没数"——"今天没人发起流程"与"取不到数"是两件事。 */
        InstanceStat instance = statOrZero(statQuery.instanceStat(tenantId, dayStart, dayEnd));
        long pending = statQuery.pendingCount(tenantId);
        OverdueStat overdue = overdueOrZero(statQuery.overdue(tenantId, now));
        long stuck = statQuery.stuckInstances(tenantId);
        Map<LocalDate, DayFlow> flowByDay = indexByDay(
                statQuery.dailyFlow(tenantId, trendStart, dayEnd));
        List<Backlog> backlog = statQuery.pendingBacklog(tenantId, BACKLOG_TOP_N);

        Overviews.Builder builder = Overviews.builder(SERVICE, NODE_KEY, title())
                .subtitle(subtitle() + " · " + DAY_LABEL.format(today))
                .kpi(Overviews.count("running", "运行中", instance.running(), OverviewTone.FLAT,
                        "今日发起 " + instance.startedToday()))
                .kpi(Overviews.count("finished", "今日办结", instance.finishedToday(), OverviewTone.FLAT,
                        "审批通过"))
                .kpi(Overviews.count("pending", "待办任务", pending,
                        overdue.overdue() > 0L ? OverviewTone.WARN : OverviewTone.FLAT,
                        "已超时 " + overdue.overdue()))
                .kpi(Overviews.count("stuck", "卡住实例", stuck,
                        stuck > 0L ? OverviewTone.WARN : OverviewTone.FLAT,
                        "运行中但无待办"))
                .chart(Overviews.chart("flow-trend", "近 " + TREND_DAYS + " 日发起 / 办结", "line",
                        OverviewFormat.COUNT, labels(today),
                        Overviews.series("发起", startedSeries(flowByDay, today)),
                        Overviews.series("办结", finishedSeries(flowByDay, today))))
                .chart(Overviews.chart("node-backlog", "待办积压 Top " + BACKLOG_TOP_N, "bar",
                        OverviewFormat.COUNT, backlogLabels(backlog),
                        Overviews.series("待办", backlogValues(backlog))))
                .note("口径：办结只算审批通过（驳回 / 撤销停在 update_time，不计入），"
                        + "卡住实例指运行中但没有任何待办任务的实例，均限本租户");
        if (tenantId == null) {
            /* 没租户上下文时四个数都会是 0——不写清楚，看的人会以为审批全都停了。 */
            builder.note("未识别租户上下文，仅展示空口径（请从网关携带租户头访问）");
        }
        if (context != null && context.hasPermission(PERMISSION)) {
            builder.link(Overviews.link("instances", "流程实例", INSTANCE_PAGE_PATH, PERMISSION));
        }
        appendAlerts(builder, overdue, stuck);
        return builder.build();
    }

    /**
     * 实例统计兜底
     *
     * @param stat 口径返回值，可空
     * @return 非空统计
     */
    private InstanceStat statOrZero(InstanceStat stat) {
        return stat == null ? new InstanceStat(0L, 0L, 0L) : stat;
    }

    /**
     * 超时统计兜底
     *
     * @param stat 口径返回值，可空
     * @return 非空统计
     */
    private OverdueStat overdueOrZero(OverdueStat stat) {
        return stat == null ? new OverdueStat(0L, 0L) : stat;
    }

    /**
     * 把按天聚合折成"日期 → 发起 / 办结"
     *
     * @param rows 聚合行
     * @return 日期索引；入参为空时返回空表
     */
    private Map<LocalDate, DayFlow> indexByDay(List<DayFlow> rows) {
        Map<LocalDate, DayFlow> byDay = new HashMap<>();
        if (rows == null) {
            return byDay;
        }
        for (DayFlow row : rows) {
            if (row.day() != null) {
                byDay.merge(row.day(), row, (left, right) ->
                        new DayFlow(left.day(), left.started() + right.started(),
                                left.finished() + right.finished()));
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
     * 趋势图发起序列（缺数据的日期补 0，且顺序与横轴一致）
     *
     * @param byDay 日期索引
     * @param today 今天
     * @return 每天一个数值
     */
    private Number[] startedSeries(Map<LocalDate, DayFlow> byDay, LocalDate today) {
        Number[] values = new Number[TREND_DAYS];
        for (int i = 0; i < TREND_DAYS; i++) {
            DayFlow row = byDay.get(today.minusDays(TREND_DAYS - 1L - i));
            values[i] = row == null ? 0L : row.started();
        }
        return values;
    }

    /**
     * 趋势图办结序列（与发起序列同一套横轴，缺数据补 0）
     *
     * @param byDay 日期索引
     * @param today 今天
     * @return 每天一个数值
     */
    private Number[] finishedSeries(Map<LocalDate, DayFlow> byDay, LocalDate today) {
        Number[] values = new Number[TREND_DAYS];
        for (int i = 0; i < TREND_DAYS; i++) {
            DayFlow row = byDay.get(today.minusDays(TREND_DAYS - 1L - i));
            values[i] = row == null ? 0L : row.finished();
        }
        return values;
    }

    /**
     * 积压柱图横轴
     *
     * @param rows 聚合行
     * @return 节点名（截断）；当前无待办时给一个占位类目
     *         ——空类目在屏风上会画成一条白线，看着像加载失败
     */
    private List<String> backlogLabels(List<Backlog> rows) {
        if (rows == null || rows.isEmpty()) {
            return List.of("当前无待办");
        }
        List<String> labels = new ArrayList<>(rows.size());
        for (Backlog row : rows) {
            labels.add(shortName(row.nodeName()));
        }
        return labels;
    }

    /**
     * 积压柱图数值
     *
     * @param rows 聚合行
     * @return 每个节点的待办数；无待办时给一个 0
     */
    private Number[] backlogValues(List<Backlog> rows) {
        if (rows == null || rows.isEmpty()) {
            return new Number[]{0L};
        }
        Number[] values = new Number[rows.size()];
        for (int i = 0; i < rows.size(); i++) {
            values[i] = rows.get(i).pending();
        }
        return values;
    }

    /**
     * 节点名截断（柱图类目位只放得下十来个字）
     *
     * <p>只做展示截断，不参与匹配：两个长名截成一样时柱图上看不出区别，
     * 但要点名到具体节点还得看实例详情里的审批链。</p>
     *
     * @param name 节点名
     * @return 截断后的展示名
     */
    private String shortName(String name) {
        if (name == null || name.isBlank()) {
            return "未命名节点";
        }
        return name.length() <= CATEGORY_MAX_CHARS
                ? name
                : name.substring(0, CATEGORY_MAX_CHARS) + "…";
    }

    /**
     * 追加告警：超时积压与审批链卡死
     *
     * <p>两条根因不同，可以同时出现：超时是"人在拖"（催办、加签、转办），
     * 卡住是"链断了"（任务被取消却没有下一跳）。前者是管理动作，后者是缺陷，
     * 混成一条会让值班的人先去催一个根本没人有待办的流程。</p>
     *
     * @param builder 组装器
     * @param overdue 超时统计
     * @param stuck   卡住的实例数
     */
    private void appendAlerts(Overviews.Builder builder, OverdueStat overdue, long stuck) {
        if (stuck > 0L) {
            builder.alert(OverviewAlertLevel.CRITICAL,
                    "运行中流程 " + stuck + " 个没有任何待办，审批链可能断了",
                    (int) stuck);
        }
        if (overdue.overdue() >= OVERDUE_ALERT_COUNT) {
            builder.alert(OverviewAlertLevel.CRITICAL,
                    "待办超时 " + overdue.overdue() + " 个，最久已超时 " + hours(overdue.worstMinutes()) + " 小时",
                    (int) overdue.overdue());
        } else if (overdue.overdue() > 0L) {
            builder.alert(OverviewAlertLevel.WARNING,
                    "待办超时 " + overdue.overdue() + " 个，最久已超时 " + hours(overdue.worstMinutes()) + " 小时",
                    (int) overdue.overdue());
        }
    }

    /**
     * 分钟折成小时（向上取整）
     *
     * <p>向下取整会把"超时 59 分钟"显示成 0 小时——那正是最该被看见的一档。</p>
     *
     * @param minutes 分钟数
     * @return 小时数，至少 1；入参非正时返回 0
     */
    private long hours(long minutes) {
        if (minutes <= 0L) {
            return 0L;
        }
        return (minutes + 59L) / 60L;
    }
}
