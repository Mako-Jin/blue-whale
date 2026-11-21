package com.bluewhale.form.overview;

import com.bluewhale.form.service.FormOverviewStatQuery;
import com.bluewhale.form.service.FormOverviewStatQuery.DaySubmits;
import com.bluewhale.form.service.FormOverviewStatQuery.DefinitionCount;
import com.bluewhale.form.service.FormOverviewStatQuery.FormTop;
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
 * 考功殿（表单台账）概览 provider（S5 / T5.7）。
 *
 * <p><b>这一屏回答的问题：</b>表单还在被人填吗、今天录了几条、有多少条卡在审批里、
 * 主表和物理表有没有对不上的数据。表单是"业务自己录、系统只负责存"的部件，
 * 它坏了不会有人报错——只会有人事后发现某张表里少了几行。</p>
 *
 * <p><b>「待修差异」单独占一张 KPI，这是本殿的核心。</b>主记录 {@code form_data} 与
 * 各版本物理表 {@code form_data_{code}_v{n}} 双写，任一侧失败就留下单向数据：
 * 列表读主表 → 有这一行；点开详情读物理表 → 字段全空。<b>全程不报错</b>，
 * 只有当事人觉得"我明明填了"。这条数只有把两边对起来才看得见，所以必须单列，
 * 不能并进"数据总量"。</p>
 *
 * <p><b>超时待审按 {@code update_time} 判，不按 {@code create_time}。</b>审批中的行只被
 * 审批回调改动，两者等价；但驳回后重提的行会被 {@code update_time} 正确地重新计时。
 * 24 小时是有意的粗粒度：审批本来就允许隔夜，按小时报只会制造噪音。</p>
 *
 * <p><b>只数有效数据</b>（{@code deleted = 0 AND status = 1}）：作废行还在表里，
 * 算进提交量会让考核口径单向偏大，而且越清理越对不上。</p>
 *
 * @author BlueWhale
 */
@Component
public class FormOverviewProvider implements OverviewProvider {

    /** 服务一级前缀（与网关 /form/** 路由一致） */
    private static final String SERVICE = "form";

    /** 城内节点 key（考功殿），与 city-layout.json 对齐 */
    private static final String NODE_KEY = "kaogong-hall";

    /** 概览权限码：与 2D 表单定义页同一码，不为 3D 单独发一套授权 */
    private static final String PERMISSION = "sys:form:list";

    /** 屏风页（表单定义列表）路径：与 2D 后台同路径 */
    private static final String FORM_PAGE_PATH = "/form/v1/definition/page";

    /** 趋势天数（含今天） */
    private static final int TREND_DAYS = 7;

    /** 提交量 Top 取前几名（屏风柱图横轴只放得下 5 个类目） */
    private static final int TOP_N = 5;

    /** 待审批超时判定窗口：超过它就算"卡住了"，而不是"还没轮到" */
    private static final Duration PENDING_STALE_WINDOW = Duration.ofHours(24);

    /** 柱图类目名最大字数：再长会把纵轴挤没 */
    private static final int CATEGORY_MAX_CHARS = 6;

    /** 横轴标签：月-日 */
    private static final DateTimeFormatter DAY_LABEL = DateTimeFormatter.ofPattern("MM-dd", Locale.CHINA);

    /** 统计口径 */
    private final FormOverviewStatQuery statQuery;

    /**
     * 构造 provider
     *
     * @param statQuery 统计口径
     */
    public FormOverviewProvider(FormOverviewStatQuery statQuery) {
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
        return "考功殿";
    }

    @Override
    public String subtitle() {
        return "表单台账";
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
           标成"没数"——"今天没人填表"与"取不到数"是两件事。 */
        DefinitionCount forms = definitionOrZero(statQuery.definitionCount(tenantId));
        long todaySubmits = statQuery.submittedBetween(tenantId, dayStart, dayEnd);
        long pending = statQuery.countByBizStatus(tenantId, FormOverviewStatQuery.BIZ_PENDING);
        long stalePending = statQuery.staleByBizStatus(tenantId, FormOverviewStatQuery.BIZ_PENDING,
                now.minus(PENDING_STALE_WINDOW));
        long diffPending = statQuery.pendingReconcile(tenantId);
        Map<LocalDate, Long> submitsByDay = indexByDay(
                statQuery.dailySubmits(tenantId, trendStart, dayEnd));
        List<FormTop> topForms = statQuery.topForms(tenantId, trendStart, dayEnd, TOP_N);

        Overviews.Builder builder = Overviews.builder(SERVICE, NODE_KEY, title())
                .subtitle(subtitle() + " · " + DAY_LABEL.format(today))
                .kpi(Overviews.count("forms", "启用表单", forms.enabled(), OverviewTone.FLAT,
                        definitionHint(forms)))
                .kpi(Overviews.count("submits", "今日提交", todaySubmits, OverviewTone.FLAT,
                        "近 " + TREND_DAYS + " 日 " + totalOf(submitsByDay) + " 条"))
                .kpi(Overviews.count("pending", "待审批", pending,
                        stalePending > 0L ? OverviewTone.WARN : OverviewTone.FLAT,
                        pendingHint(pending, stalePending)))
                .kpi(Overviews.count("diff", "待修差异", diffPending,
                        diffPending > 0L ? OverviewTone.WARN : OverviewTone.FLAT,
                        "主表与物理表不一致"))
                .chart(Overviews.chart("submit-trend", "近 " + TREND_DAYS + " 日提交", "line",
                        OverviewFormat.COUNT, labels(today),
                        Overviews.series("提交", seriesOf(submitsByDay, today))))
                .chart(Overviews.chart("form-top", "近 " + TREND_DAYS + " 日提交最多", "bar",
                        OverviewFormat.COUNT, topLabels(topForms),
                        Overviews.series("提交", topValues(topForms))))
                .note("口径：只数有效数据（未删除且未作废），待审批为累计量、超时按 24 小时窗口，"
                        + "差异数为与物理表对账后仍待处理的条数，均限本租户");
        if (tenantId == null) {
            /* 直连本服务时租户上下文由 /form/** 的守卫拦下，但 provider 也可能被
               进程内直接调用（单测、总控）。四种数都会是 0，不写清楚会被当成"表单全停了"。 */
            builder.note("未识别租户上下文，仅展示空口径（请从网关携带租户头访问）");
        }
        if (context != null && context.hasPermission(PERMISSION)) {
            builder.link(Overviews.link("forms", "表单定义", FORM_PAGE_PATH, PERMISSION));
        }
        appendAlerts(builder, diffPending, stalePending);
        return builder.build();
    }

    /**
     * 定义数兜底
     *
     * @param count 口径返回值，可空
     * @return 非空统计
     */
    private DefinitionCount definitionOrZero(DefinitionCount count) {
        return count == null ? new DefinitionCount(0L, 0L) : count;
    }

    /**
     * 表单数 KPI 的副标：启用与停用分开说
     *
     * <p>停用过的定义仍占着编码与物理表，不单独报出来，看的人会以为"基数是 12 个"，
     * 而实际对外可填的只有 9 个。</p>
     *
     * @param count 定义统计
     * @return 副标文案
     */
    private String definitionHint(DefinitionCount count) {
        long disabled = Math.max(0L, count.definitions() - count.enabled());
        return "共 " + count.definitions() + " 个定义 · 停用 " + disabled;
    }

    /**
     * 待审批 KPI 的副标
     *
     * @param pending      累计待审批条数
     * @param stalePending 其中超过观察窗口未动的条数
     * @return 副标文案
     */
    private String pendingHint(long pending, long stalePending) {
        if (pending <= 0L) {
            return "已全部办结";
        }
        return stalePending > 0L
                ? "其中超 " + PENDING_STALE_WINDOW.toHours() + "h 未动 " + stalePending
                : "均在 " + PENDING_STALE_WINDOW.toHours() + "h 内";
    }

    /**
     * 把按天聚合折成"日期 → 条数"，并顺带算出窗口总量
     *
     * @param rows 聚合行
     * @return 日期索引；入参为空时返回空表
     */
    private Map<LocalDate, Long> indexByDay(List<DaySubmits> rows) {
        Map<LocalDate, Long> byDay = new HashMap<>();
        if (rows == null) {
            return byDay;
        }
        for (DaySubmits row : rows) {
            if (row.day() != null) {
                byDay.merge(row.day(), row.submits(), Long::sum);
            }
        }
        return byDay;
    }

    /**
     * 窗口内总提交量
     *
     * <p>复用趋势那份聚合，不再单独打一次库：两个数天然一致，
     * 也不会出现"图上 7 根柱子加起来 40、副标写 41"这种没法解释的偏差。</p>
     *
     * @param byDay 日期索引
     * @return 合计
     */
    private long totalOf(Map<LocalDate, Long> byDay) {
        long total = 0L;
        for (Long value : byDay.values()) {
            total += value;
        }
        return total;
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
     * @return 每天一个条数
     */
    private Number[] seriesOf(Map<LocalDate, Long> byDay, LocalDate today) {
        Number[] values = new Number[TREND_DAYS];
        for (int i = 0; i < TREND_DAYS; i++) {
            values[i] = byDay.getOrDefault(today.minusDays(TREND_DAYS - 1L - i), 0L);
        }
        return values;
    }

    /**
     * 提交 Top 图横轴
     *
     * @param rows 聚合行
     * @return 表单名（截断）；无提交时给一个占位类目
     *         ——空类目在屏风上会画成一条白线，看着像加载失败
     */
    private List<String> topLabels(List<FormTop> rows) {
        if (rows == null || rows.isEmpty()) {
            return List.of("近 " + TREND_DAYS + " 日无提交");
        }
        List<String> labels = new ArrayList<>(rows.size());
        for (FormTop row : rows) {
            labels.add(shortName(row.formName() == null ? row.formCode() : row.formName()));
        }
        return labels;
    }

    /**
     * 提交 Top 图数值
     *
     * @param rows 聚合行
     * @return 每个表单的提交条数；无提交时给一个 0
     */
    private Number[] topValues(List<FormTop> rows) {
        if (rows == null || rows.isEmpty()) {
            return new Number[]{0L};
        }
        Number[] values = new Number[rows.size()];
        for (int i = 0; i < rows.size(); i++) {
            values[i] = rows.get(i).submits();
        }
        return values;
    }

    /**
     * 表单名截断（柱图类目位只放得下几个字）
     *
     * <p>只做展示截断，不参与匹配：两个长名截成一样时柱图上看不出区别，
     * 但要点名到具体表单还得看表单定义页。</p>
     *
     * @param name 表单名或编码
     * @return 截断后的展示名
     */
    private String shortName(String name) {
        if (name == null || name.isBlank()) {
            return "未知表单";
        }
        return name.length() <= CATEGORY_MAX_CHARS
                ? name
                : name.substring(0, CATEGORY_MAX_CHARS) + "…";
    }

    /**
     * 追加告警：主从不一致与审批卡住各一条
     *
     * <p>两条根因不同，可以同时出现：差异是"写库那一步失败了"（要看对账页与物理表），
     * 超时待审是"流程没人处理"（要去找审批人）。混成一条会让人先去催一个
     * 根本没人有待办的流程。</p>
     *
     * <p>差异给 {@code CRITICAL}：它意味着**用户看到的数据和实际存储的数据不是一回事**，
     * 而且不修就一直在。超时待审给 {@code WARNING}：慢不等于坏，隔夜审批本来就有。</p>
     *
     * @param builder      组装器
     * @param diffPending  待修差异条数
     * @param stalePending 超时待审批条数
     */
    private void appendAlerts(Overviews.Builder builder, long diffPending, long stalePending) {
        if (diffPending > 0L) {
            builder.alert(OverviewAlertLevel.CRITICAL,
                    "有 " + diffPending + " 条数据主表与物理表不一致，列表里看得到、详情可能是空的",
                    (int) Math.min(diffPending, Integer.MAX_VALUE));
        }
        if (stalePending > 0L) {
            builder.alert(OverviewAlertLevel.WARNING,
                    "有 " + stalePending + " 条数据卡在审批中超过 "
                            + PENDING_STALE_WINDOW.toHours() + " 小时（审批中只读，业务方既改不了也删不了）",
                    (int) Math.min(stalePending, Integer.MAX_VALUE));
        }
    }
}
