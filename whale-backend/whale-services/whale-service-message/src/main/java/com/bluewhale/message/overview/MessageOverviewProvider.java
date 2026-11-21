package com.bluewhale.message.overview;

import com.bluewhale.message.service.MessageOverviewStatQuery;
import com.bluewhale.message.service.MessageOverviewStatQuery.ChannelFail;
import com.bluewhale.message.service.MessageOverviewStatQuery.DayDelivery;
import com.bluewhale.message.service.MessageOverviewStatQuery.DeliveryStat;
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
 * 通政殿（消息投递）概览 provider（S5 / T5.8）。
 *
 * <p><b>这一屏回答的问题：</b>信送出多少、送得到吗、有没有卡在半路、有没有被退回。
 * 消息服务是"调用方认为成功、收件人什么也没收到"的重灾区：<b>积压未发</b>（状态还停在
 * PENDING）既不报错也不告警，<b>退信</b>（地址失效）会一直重试却永远失败。两个数只能
 * 分开看，处置动作也不同——前者去看投递线程与补偿任务，后者去改接收人数据。</p>
 *
 * <p><b>投递按"渠道 × 接收人"计数，不是消息条数。</b>一条消息发 3 个渠道给 10 个人 =
 * 30 条投递；对运维来说每一次投递都是一次真实的发送尝试，按消息数算会把"1 条发给了 10 人"
 * 显示成 1，失败率也就失去意义。</p>
 *
 * <p><b>成功率口径与 2D 投递看板逐字一致</b>（成功 = 已送达 + 已读，失败 = 失败 + 退信），
 * 否则城里说 92%、后台说 88%。小样本不谈比率：5 条里失败 1 条就是 80%，报出来只是噪音。</p>
 *
 * @author BlueWhale
 */
@Component
public class MessageOverviewProvider implements OverviewProvider {

    /** 服务一级前缀（与网关 /message/** 路由一致） */
    private static final String SERVICE = "message";

    /** 城内节点 key（通政殿），与 city-layout.json 对齐 */
    private static final String NODE_KEY = "tongzheng-hall";

    /** 概览权限码：与 2D 消息记录页同一码，不为 3D 单独发一套授权 */
    private static final String PERMISSION = "sys:message:record:list";

    /** 屏风页（消息记录）路径：与 2D 后台同路径 */
    private static final String RECORD_PAGE_PATH = "/message/v1/record/page";

    /** 趋势天数（含今天） */
    private static final int TREND_DAYS = 7;

    /** 最近窗口：KPI 里的送达率与退信都看这段 */
    private static final Duration RECENT_WINDOW = Duration.ofHours(24);

    /**
     * 积压判定阈值。
     *
     * <p>异步补偿的回收阈值是 120 秒（{@code whale.message.send.recover-threshold-seconds}），
     * 这里取 10 分钟，是它的 5 倍余量：卡住两分钟说明补偿还在正常干活，
     * 卡住十分钟说明补偿也没救回来——那时候人必须知道。</p>
     */
    private static final Duration BACKLOG_AFTER = Duration.ofMinutes(10);

    /** 送达率告警线 */
    private static final double RATE_WARN = 0.9D;

    /** 谈送达率的最小样本：样本太少时百分比没有意义 */
    private static final long RATE_MIN_SAMPLE = 20L;

    /** 失败渠道 TopN（屏风柱图横轴只放得下四五个类目） */
    private static final int FAIL_TOP_N = 5;

    /** 横轴标签：月-日 */
    private static final DateTimeFormatter DAY_LABEL = DateTimeFormatter.ofPattern("MM-dd", Locale.CHINA);

    /** 统计口径 */
    private final MessageOverviewStatQuery statQuery;

    /**
     * 构造 provider
     *
     * @param statQuery 统计口径
     */
    public MessageOverviewProvider(MessageOverviewStatQuery statQuery) {
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
        return "通政殿";
    }

    @Override
    public String subtitle() {
        return "消息投递";
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

        /* 口径实现返回 null 时按零处理：概览是常驻轮询，不能因为一次空返回把整座殿
           标成"没数"——"今天没发消息"与"取不到数"是两件事。 */
        long todaySent = statQuery.sentCount(tenantId, dayStart, dayEnd);
        DeliveryStat recent = deliveryOrZero(statQuery.deliveryStat(tenantId, recentStart, now));
        long backlog = statQuery.backlog(tenantId, now.minus(BACKLOG_AFTER));
        Map<LocalDate, Long> sentByDay = indexByDay(
                statQuery.dailyDeliveries(tenantId, trendStart, dayEnd));
        List<ChannelFail> failTop = statQuery.failedChannels(tenantId, trendStart, dayEnd, FAIL_TOP_N);
        double rate = rate(recent);
        boolean lowRate = recent.total() >= RATE_MIN_SAMPLE && rate < RATE_WARN;

        Overviews.Builder builder = Overviews.builder(SERVICE, NODE_KEY, title())
                .subtitle(subtitle() + " · " + DAY_LABEL.format(today))
                .kpi(Overviews.count("today", "今日投递", todaySent, OverviewTone.FLAT,
                        "本租户 · 按渠道 × 接收人计"))
                .kpi(Overviews.percent("rate", "送达率", rate,
                        lowRate ? OverviewTone.WARN : OverviewTone.FLAT, rateHint(recent)))
                .kpi(Overviews.count("backlog", "积压未发", backlog,
                        backlog > 0L ? OverviewTone.WARN : OverviewTone.FLAT,
                        "超 " + BACKLOG_AFTER.toMinutes() + " 分钟未投出"))
                .kpi(Overviews.count("bounced", "退信", recent.bounced(),
                        recent.bounced() > 0L ? OverviewTone.WARN : OverviewTone.FLAT,
                        "近 24h · 地址失效，重试无用"))
                .chart(Overviews.chart("sent-trend", "近 " + TREND_DAYS + " 日投递", "line",
                        OverviewFormat.COUNT, labels(today),
                        Overviews.series("投递", seriesOf(sentByDay, today))))
                .chart(Overviews.chart("channel-fail", "近 " + TREND_DAYS + " 日失败最多渠道", "bar",
                        OverviewFormat.COUNT, failLabels(failTop),
                        Overviews.series("失败", failValues(failTop))))
                .note("口径：投递按渠道 × 接收人各记一条（一条消息发 3 个渠道给 10 人 = 30 条）；"
                        + "送达率与 2D 投递看板同口径（成功 = 已送达 + 已读，失败 = 失败 + 退信），"
                        + "取近 24 小时；积压 = 状态仍在待投递 / 投递中且超过 "
                        + BACKLOG_AFTER.toMinutes() + " 分钟");
        if (tenantId == null) {
            /* 没租户上下文时四个数都会是 0——不写清楚，看的人会以为今天一条消息都没发。 */
            builder.note("未识别租户上下文，仅展示空口径（请从网关携带租户头访问）");
        }
        if (context != null && context.hasPermission(PERMISSION)) {
            builder.link(Overviews.link("records", "消息记录", RECORD_PAGE_PATH, PERMISSION));
        }
        appendAlerts(builder, backlog, recent);
        return builder.build();
    }

    /**
     * 投递统计兜底
     *
     * @param stat 口径返回值，可空
     * @return 非空统计
     */
    private DeliveryStat deliveryOrZero(DeliveryStat stat) {
        return stat == null ? new DeliveryStat(0L, 0L, 0L, 0L) : stat;
    }

    /**
     * 计算送达率（0~1）
     *
     * @param stat 投递统计
     * @return 送达率；无投递时为 0
     */
    private double rate(DeliveryStat stat) {
        if (stat.total() <= 0L) {
            return 0D;
        }
        return stat.success() * 1D / stat.total();
    }

    /**
     * 送达率 KPI 的副标：成功与失败分开说，避免"100% 送达"其实只发了 1 条
     *
     * @param stat 近 24 小时投递统计
     * @return 副标文案
     */
    private String rateHint(DeliveryStat stat) {
        if (stat.total() <= 0L) {
            return "近 24h 无投递";
        }
        return "近 24h · 成功 " + stat.success() + " / 失败 " + stat.failed();
    }

    /**
     * 把按天聚合折成"日期 → 条数"
     *
     * @param rows 聚合行
     * @return 日期索引；入参为空时返回空表
     */
    private Map<LocalDate, Long> indexByDay(List<DayDelivery> rows) {
        Map<LocalDate, Long> byDay = new HashMap<>();
        if (rows == null) {
            return byDay;
        }
        for (DayDelivery row : rows) {
            if (row.day() != null) {
                byDay.merge(row.day(), row.deliveries(), Long::sum);
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
     * 失败渠道图横轴
     *
     * @param rows 聚合行
     * @return 渠道名；近 7 日无失败时给一个占位类目
     *         ——空类目在屏风上会画成一条白线，看着像加载失败
     */
    private List<String> failLabels(List<ChannelFail> rows) {
        if (rows == null || rows.isEmpty()) {
            return List.of("近 " + TREND_DAYS + " 日无失败");
        }
        List<String> labels = new ArrayList<>(rows.size());
        for (ChannelFail row : rows) {
            labels.add(row.channel());
        }
        return labels;
    }

    /**
     * 失败渠道图数值
     *
     * @param rows 聚合行
     * @return 每个渠道的失败条数；无失败时给一个 0
     */
    private Number[] failValues(List<ChannelFail> rows) {
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
     * 追加告警：积压与退信各一条
     *
     * <p>两条根因不同，可以同时出现：积压是"消息收下了没送出去"（投递线程 / 补偿任务坏了，
     * 业务方却已经认为发送成功）；退信是"送到了但对方不存在"（重试永远失败，只能改数据）。
     * 混成一条会让人先去重试一批根本不可能成功的投递。</p>
     *
     * @param builder 组装器
     * @param backlog 积压条数
     * @param recent  近 24 小时投递统计
     */
    private void appendAlerts(Overviews.Builder builder, long backlog, DeliveryStat recent) {
        if (backlog > 0L) {
            builder.alert(OverviewAlertLevel.CRITICAL,
                    backlog + " 条消息超过 " + BACKLOG_AFTER.toMinutes() + " 分钟仍未投出，"
                            + "业务方已认为发送成功",
                    (int) backlog);
        }
        if (recent.bounced() > 0L) {
            builder.alert(OverviewAlertLevel.WARNING,
                    "近 24 小时 " + recent.bounced() + " 条退信，接收地址可能已失效（重试不会成功）",
                    (int) recent.bounced());
        }
    }
}
