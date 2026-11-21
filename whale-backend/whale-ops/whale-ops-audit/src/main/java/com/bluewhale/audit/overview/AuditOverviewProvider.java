package com.bluewhale.audit.overview;

import com.bluewhale.audit.service.AuditOverviewStatQuery;
import com.bluewhale.audit.service.AuditOverviewStatQuery.DayAudit;
import com.bluewhale.audit.service.AuditOverviewStatQuery.OperateTypeCount;
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
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 起居殿（审计留痕）概览 provider（S5 / T5.9，只读）。
 *
 * <p><b>这一屏回答的问题：</b>审计本身还活着吗？——审计服务最怕的不是漏抓一次违规，
 * 而是"该记的那段时间是空白"。摄入链路断开、归档作业停摆都不会报错：进程还在、健康探针还绿、
 * 指标还在暴露，等到要查账才发现关键窗口没留下证据。所以除了今日审计数/失败数两张常规牌，
 * 专门给两张"静默失效"牌：</p>
 * <ul>
 *   <li><b>留痕断流时长</b>：最近一条审计距今多久。超过阈值说明写入链路可能已断（比"今日 0 条"
 *       更准——空闲环境今日 0 条是正常的，但连续一天一条都没有不正常）。</li>
 *   <li><b>归档陈旧天数</b>：归档作业心跳最后成功距今几天。与指标 {@code audit.job.stale.days} 同口径；
 *       超过阈值说明归档也没跑，审计证据正在裸奔（触发条件比断流宽松，单独一档告警）。</li>
 * </ul>
 *
 * <p><b>两条告警根因不同、可并存</b>：断流是"写不进来"（CRITICAL，先查摄入），
 * 归档停摆是"记下来了但没归档"（WARNING，去查 retention 作业）。混成一条会让人先去翻归档日志，
 * 而真正的问题是审计根本没写进去。</p>
 *
 * <p><b>全程只读</b>：概览被 30s 轮询，provider 内只有 count/select，无任何写库。</p>
 *
 * @author BlueWhale
 */
@Component
public class AuditOverviewProvider implements OverviewProvider {

    /** 服务一级前缀（与网关 /audit/** 路由一致） */
    private static final String SERVICE = "audit";

    /** 城内节点 key（起居殿），与 city-layout.json 对齐 */
    private static final String NODE_KEY = "qiju-hall";

    /** 概览权限码：与 2D 审计日志页同一码，不为 3D 单独发一套授权 */
    private static final String PERMISSION = "sys:audit:log:list";

    /** 屏风页（审计日志）路径：与 2D 后台同路径 */
    private static final String LOG_PAGE_PATH = "/audit/v1/log/page";

    /** 趋势天数（含今天） */
    private static final int TREND_DAYS = 7;

    /** 失败/操作类型统计的近窗（今日审计用当天，这两项看近 24h 更平滑） */
    private static final Duration RECENT_WINDOW = Duration.ofHours(24);

    /** 留痕断流告警阈值：24 小时一条审计都没有 = 写入链路基本可判故障 */
    private static final Duration SILENCE_CRITICAL = Duration.ofHours(24);

    /** 留痕断流"值得注意"阈值（副标提示用，不升告警）：2 小时 */
    private static final Duration SILENCE_WARN = Duration.ofHours(2);

    /** 归档陈旧告警阈值（天），与 application.yml stale-alert-days 默认 2 一致 */
    private static final long ARCHIVE_STALE_DAYS = 2L;

    /** 操作类型 TopN（屏风柱图横轴只放得下五六个类目） */
    private static final int TYPE_TOP_N = 5;

    /** 横轴标签：月-日 */
    private static final DateTimeFormatter DAY_LABEL = DateTimeFormatter.ofPattern("MM-dd", Locale.CHINA);

    /** 统计口径 */
    private final AuditOverviewStatQuery statQuery;

    /**
     * 构造 provider
     *
     * @param statQuery 统计口径
     */
    public AuditOverviewProvider(AuditOverviewStatQuery statQuery) {
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
        return "起居殿";
    }

    @Override
    public String subtitle() {
        return "审计留痕";
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
        LocalDateTime dayStart = today.atStartOfDay();
        LocalDateTime dayEnd = today.plusDays(1L).atStartOfDay();
        LocalDateTime trendStart = today.minusDays(TREND_DAYS - 1L).atStartOfDay();
        LocalDateTime recentStart = now.minus(RECENT_WINDOW);

        long todayAudits = statQuery.todayCount(tenantId, dayStart, dayEnd);
        long recentFailed = statQuery.failedCount(tenantId, recentStart, now);
        LocalDateTime lastAudit = statQuery.lastAuditTime(tenantId);
        long silenceSeconds = silenceSeconds(lastAudit, now);
        Long archiveLastSuccess = toEpochSeconds(statQuery.archiveLastSuccessAt());
        long archiveStaleDays = staleDays(archiveLastSuccess, now);
        Map<LocalDate, Long> auditsByDay = indexByDay(statQuery.dailyAudits(tenantId, trendStart, dayEnd));
        List<OperateTypeCount> typeTop = statQuery.topOperateTypes(tenantId, trendStart, dayEnd, TYPE_TOP_N);

        boolean silent = silenceSeconds >= SILENCE_CRITICAL.getSeconds();
        boolean archiveStale = archiveStaleDays >= ARCHIVE_STALE_DAYS;

        Overviews.Builder builder = Overviews.builder(SERVICE, NODE_KEY, title())
                .subtitle(subtitle() + " · " + DAY_LABEL.format(today))
                .kpi(Overviews.count("today", "今日审计", todayAudits, OverviewTone.FLAT,
                        "本租户 · 今日写入的审计记录"))
                .kpi(Overviews.count("failed", "失败操作", recentFailed,
                        recentFailed > 0L ? OverviewTone.WARN : OverviewTone.FLAT,
                        "近 24h · biz_status=0"))
                .kpi(Overviews.kpi("silence", "留痕断流", silenceSeconds, OverviewFormat.DURATION,
                        silenceTone(silenceSeconds), silenceHint(silenceSeconds)))
                .kpi(Overviews.count("stale", "归档陈旧", archiveStaleDays,
                        archiveStale ? OverviewTone.WARN : OverviewTone.FLAT,
                        "归档作业最后成功距今 · 天"))
                .chart(Overviews.chart("audit-trend", "近 " + TREND_DAYS + " 日审计", "line",
                        OverviewFormat.COUNT, labels(today),
                        Overviews.series("审计", seriesOf(auditsByDay, today))))
                .chart(Overviews.chart("operate-type", "近 " + TREND_DAYS + " 日操作类型", "bar",
                        OverviewFormat.COUNT, typeLabels(typeTop),
                        Overviews.series("条数", typeValues(typeTop))))
                .note("口径：断流 = 当前时刻减本租户最近一条审计写入时刻（从未写入记 -1，不算故障）；"
                        + "归档陈旧 = 归档作业 audit.log.retention 最后成功距今天数，与指标 audit.job.stale.days 同口径。"
                        + "审计记录 append-only，概览全程只读。");
        if (tenantId == null) {
            /* 无租户上下文时全部计数为 0、断流为 -1——不写清楚，看的人会以为是新系统没数据。 */
            builder.note("未识别租户上下文，仅展示空口径（请从网关携带租户头访问）");
        }
        if (lastAudit == null && tenantId != null) {
            builder.note("本租户尚无审计记录，断流时长不可判定（记 -1）");
        }
        if (context != null && context.hasPermission(PERMISSION)) {
            builder.link(Overviews.link("logs", "审计日志", LOG_PAGE_PATH, PERMISSION));
        }
        appendAlerts(builder, silenceSeconds, archiveStaleDays);
        return builder.build();
    }

    /**
     * 留痕断流秒数
     *
     * @param lastAudit 最近写入时刻，可空
     * @param now       当前时刻
     * @return 断流秒数（向下取整，最小 0）；从未写入返回 -1
     */
    private long silenceSeconds(LocalDateTime lastAudit, LocalDateTime now) {
        if (lastAudit == null) {
            return -1L;
        }
        long seconds = Duration.between(lastAudit, now).getSeconds();
        return Math.max(0L, seconds);
    }

    /**
     * 留痕断流牌的语气
     *
     * @param seconds 断流秒数，-1 表示从未写入
     * @return 语气
     */
    private OverviewTone silenceTone(long seconds) {
        if (seconds < 0L) {
            return OverviewTone.FLAT;
        }
        if (seconds >= SILENCE_CRITICAL.getSeconds()) {
            return OverviewTone.BAD;
        }
        if (seconds >= SILENCE_WARN.getSeconds()) {
            return OverviewTone.WARN;
        }
        return OverviewTone.FLAT;
    }

    /**
     * 留痕断流牌的副标
     *
     * @param seconds 断流秒数，-1 表示从未写入
     * @return 副标文案
     */
    private String silenceHint(long seconds) {
        if (seconds < 0L) {
            return "尚无审计记录";
        }
        if (seconds >= SILENCE_WARN.getSeconds()) {
            return "超 " + SILENCE_WARN.toHours() + "h 无新审计 · 请查摄入链路";
        }
        return "最近一条审计距今 · 秒";
    }

    /**
     * 归档陈旧天数
     *
     * @param archiveLastSuccessEpoch 归档最后成功时刻（epoch 秒），null 表示从未成功
     * @param now                     当前时刻
     * @return 距今天数；从未成功返回 -1
     */
    private long staleDays(Long archiveLastSuccessEpoch, LocalDateTime now) {
        if (archiveLastSuccessEpoch == null) {
            return -1L;
        }
        long days = Duration.ofSeconds(now.atZone(ZoneId.systemDefault()).toEpochSecond()
                - archiveLastSuccessEpoch).toDays();
        return Math.max(0L, days);
    }

    /**
     * 转 epoch 秒
     *
     * @param time 时刻，可空
     * @return epoch 秒；入参为空返回 null
     */
    private Long toEpochSeconds(LocalDateTime time) {
        return time == null ? null : time.atZone(ZoneId.systemDefault()).toEpochSecond();
    }

    /**
     * 把按天聚合折成"日期 → 条数"
     *
     * @param rows 聚合行
     * @return 日期索引；入参为空时返回空表
     */
    private Map<LocalDate, Long> indexByDay(List<DayAudit> rows) {
        Map<LocalDate, Long> byDay = new HashMap<>();
        if (rows == null) {
            return byDay;
        }
        for (DayAudit row : rows) {
            if (row.day() != null) {
                byDay.merge(row.day(), row.audits(), Long::sum);
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
     * 趋势图数值（缺数据的日期补 0，顺序与横轴一致）
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
     * 操作类型图横轴
     *
     * @param rows 聚合行
     * @return 类型名；无数据时给占位类目（空类目会画成白线，像加载失败）
     */
    private List<String> typeLabels(List<OperateTypeCount> rows) {
        if (rows == null || rows.isEmpty()) {
            return List.of("近 " + TREND_DAYS + " 日无审计");
        }
        List<String> labels = new ArrayList<>(rows.size());
        for (OperateTypeCount row : rows) {
            labels.add(row.operateType());
        }
        return labels;
    }

    /**
     * 操作类型图数值
     *
     * @param rows 聚合行
     * @return 每个类型的条数；无数据时给一个 0
     */
    private Number[] typeValues(List<OperateTypeCount> rows) {
        if (rows == null || rows.isEmpty()) {
            return new Number[]{0L};
        }
        Number[] values = new Number[rows.size()];
        for (int i = 0; i < rows.size(); i++) {
            values[i] = rows.get(i).audits();
        }
        return values;
    }

    /**
     * 追加告警：留痕断流与归档陈旧各一条，根因不同可并存
     *
     * @param builder        组装器
     * @param silenceSeconds 断流秒数
     * @param archiveStale   归档陈旧天数
     */
    private void appendAlerts(Overviews.Builder builder, long silenceSeconds, long archiveStale) {
        if (silenceSeconds >= SILENCE_CRITICAL.getSeconds()) {
            builder.alert(OverviewAlertLevel.CRITICAL,
                    "审计留痕已断流 " + Math.round(silenceSeconds / 3600D) + " 小时，写入链路可能已断",
                    (int) Math.min(Integer.MAX_VALUE, silenceSeconds / 3600L));
        }
        if (archiveStale >= ARCHIVE_STALE_DAYS) {
            builder.alert(OverviewAlertLevel.WARNING,
                    "归档作业 " + archiveStale + " 天未成功，审计证据未归档",
                    (int) archiveStale);
        }
    }
}
