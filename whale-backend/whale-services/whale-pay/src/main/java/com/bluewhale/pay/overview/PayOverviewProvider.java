package com.bluewhale.pay.overview;

import com.bluewhale.overview.OverviewContext;
import com.bluewhale.overview.OverviewProvider;
import com.bluewhale.overview.model.OverviewAlertLevel;
import com.bluewhale.overview.model.OverviewFormat;
import com.bluewhale.overview.model.OverviewSnapshot;
import com.bluewhale.overview.model.OverviewTone;
import com.bluewhale.overview.support.Overviews;
import com.bluewhale.pay.service.PayOverviewStatQuery;
import com.bluewhale.pay.service.PayOverviewStatQuery.ChannelAmount;
import com.bluewhale.pay.service.PayOverviewStatQuery.DayAmount;
import com.bluewhale.pay.service.PayOverviewStatQuery.PaidStat;
import com.bluewhale.pay.service.PayOverviewStatQuery.PendingStat;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
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
 * 度支殿（支付结算）概览 provider（S5 / T5.3）。
 *
 * <p><b>这一屏回答的问题：</b>今天收进来多少钱、成交几笔、有多少单卡在待支付、
 * 退了多少。四个数都按"钱"的口径取，不按"单据状态"取——状态是过程，
 * 收款与退款才是结果。</p>
 *
 * <p><b>为什么"今日收款"按 {@code pay_time} 而不是 {@code create_time}：</b>
 * 今天建的单可能三天后才付款，钱是后到账的；按建单时间算，今天的收款额会随
 * "昨天建的单今天付了"而凭空少一块。反之趋势图也必须是同一口径，否则 KPI 与图上
 * 今天的柱子永远对不上。</p>
 *
 * <p><b>退款不冲减收款：</b>收款是既成事实，退款是另一笔动作。把两者相减会让
 * "今日收款"在退款发生时变小，看起来像"今天没收到钱"，而实际是"收到了又退回去"。
 * 所以退款单列一张 KPI。</p>
 *
 * @author BlueWhale
 */
@Component
public class PayOverviewProvider implements OverviewProvider {

    /** 服务一级前缀（与网关 /pay/** 路由一致） */
    private static final String SERVICE = "pay";

    /** 城内节点 key（度支殿），与 city-layout.json 对齐 */
    private static final String NODE_KEY = "duzhi-hall";

    /** 概览权限码：与 2D 支付单管理页同一码，不为 3D 单独发一套授权 */
    private static final String PERMISSION = "sys:pay:list";

    /** 屏风页（支付单管理）路径：与 2D 后台同路径 */
    private static final String ORDER_PAGE_PATH = "/pay/order/page";

    /** 趋势天数（含今天） */
    private static final int TREND_DAYS = 7;

    /** 今日支付失败告警阈值：超过它多半是渠道或配置出了问题 */
    private static final long FAILED_ALERT_COUNT = 5L;

    /** 超时未支付告警阈值：到这个量级就不是"用户在犹豫"，而是回调丢了 */
    private static final long STALE_PENDING_ALERT = 10L;

    /** 待支付单"超时"判定：超过它仍未支付，按回调丢失排查 */
    private static final Duration STALE_AFTER = Duration.ofHours(24);

    /** 横轴标签：月-日 */
    private static final DateTimeFormatter DAY_LABEL = DateTimeFormatter.ofPattern("MM-dd", Locale.CHINA);

    /** 渠道缺失时的展示名 */
    private static final String UNKNOWN_CHANNEL = "未知渠道";

    /** 统计口径 */
    private final PayOverviewStatQuery statQuery;

    /**
     * 构造 provider
     *
     * @param statQuery 统计口径
     */
    public PayOverviewProvider(PayOverviewStatQuery statQuery) {
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
        return "度支殿";
    }

    @Override
    public String subtitle() {
        return "支付结算";
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
        LocalDateTime now = LocalDateTime.now();

        /* 口径实现返回 null 时按零处理：概览是常驻轮询，不能因为一次空返回
           把整座殿标成"没数"——"今天没有收款"与"取不到数"是两件事。 */
        PaidStat paid = paidOrZero(statQuery.paidBetween(tenantId, dayStart, dayEnd));
        long createdToday = statQuery.createdBetween(tenantId, dayStart, dayEnd);
        PendingStat pending = pendingOrZero(statQuery.pending(tenantId, now.minus(STALE_AFTER)));
        BigDecimal refund = refundOrZero(statQuery.refundBetween(tenantId, dayStart, dayEnd));
        long failed = statQuery.failedPaymentsBetween(tenantId, dayStart, dayEnd);
        List<DayAmount> trend = statQuery.paidByDay(tenantId,
                today.minusDays(TREND_DAYS - 1L).atStartOfDay(), dayEnd);
        List<ChannelAmount> channels = statQuery.paidByChannel(tenantId, dayStart, dayEnd);
        Map<LocalDate, BigDecimal> byDay = indexByDay(trend);

        Overviews.Builder builder = Overviews.builder(SERVICE, NODE_KEY, title())
                .subtitle(subtitle() + " · " + DAY_LABEL.format(today))
                .kpi(Overviews.money("paid", "今日收款", paid.amount(), OverviewTone.GOOD, "已到账"))
                .kpi(Overviews.count("paidOrders", "今日成交", paid.orders(), OverviewTone.FLAT,
                        "今日建单 " + createdToday))
                .kpi(Overviews.count("pending", "待支付", pending.pending(),
                        pending.stale() > 0 ? OverviewTone.WARN : OverviewTone.FLAT, pendingHint(pending)))
                .kpi(Overviews.money("refund", "今日退款", refund,
                        refund.signum() > 0 ? OverviewTone.WARN : OverviewTone.FLAT, "成功退款"))
                .chart(Overviews.chart("paid-trend", "近 " + TREND_DAYS + " 日收款",
                        "line", OverviewFormat.MONEY, labels(today),
                        Overviews.series("收款", seriesOf(byDay, today))))
                .chart(Overviews.chart("channel-mix", "今日渠道构成", "bar", OverviewFormat.MONEY,
                        channelLabels(channels),
                        Overviews.series("金额", channelValues(channels))))
                .note("口径：收款按支付时间（PAID/REFUNDED 的实际到账额）、退款按成功退款流水、"
                        + "待支付含超时未支付，均限本租户");
        if (tenantId == null) {
            /* 没租户上下文时四个数都会是 0——不写清楚，看的人会以为今天真的没生意。 */
            builder.note("未识别租户上下文，仅展示空口径（请从网关携带租户头访问）");
        }
        if (context != null && context.hasPermission(PERMISSION)) {
            builder.link(Overviews.link("pay-orders", "支付单管理", ORDER_PAGE_PATH, PERMISSION));
        }
        appendAlerts(builder, failed, pending);
        return builder.build();
    }

    /**
     * 收款统计兜底
     *
     * @param paid 口径返回值，可空
     * @return 非空统计
     */
    private PaidStat paidOrZero(PaidStat paid) {
        return paid == null ? new PaidStat(0L, BigDecimal.ZERO) : paid;
    }

    /**
     * 待支付统计兜底
     *
     * @param pending 口径返回值，可空
     * @return 非空统计
     */
    private PendingStat pendingOrZero(PendingStat pending) {
        return pending == null ? new PendingStat(0L, 0L) : pending;
    }

    /**
     * 金额兜底
     *
     * @param amount 口径返回值，可空
     * @return 非空金额
     */
    private BigDecimal refundOrZero(BigDecimal amount) {
        return amount == null ? BigDecimal.ZERO : amount;
    }

    /**
     * 待支付 KPI 的副标：超时数与总数分开说
     *
     * @param pending 待支付统计
     * @return 副标文案
     */
    private String pendingHint(PendingStat pending) {
        if (pending.stale() > 0) {
            return "其中超时 " + pending.stale();
        }
        return "笔";
    }

    /**
     * 把按天聚合折成"日期 → 金额"
     *
     * @param rows 聚合行
     * @return 日期索引；入参为空时返回空表
     */
    private Map<LocalDate, BigDecimal> indexByDay(List<DayAmount> rows) {
        Map<LocalDate, BigDecimal> byDay = new HashMap<>();
        if (rows == null) {
            return byDay;
        }
        for (DayAmount row : rows) {
            if (row.day() != null && row.amount() != null) {
                byDay.merge(row.day(), row.amount(), BigDecimal::add);
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
     * @return 每天一个金额
     */
    private Number[] seriesOf(Map<LocalDate, BigDecimal> byDay, LocalDate today) {
        Number[] values = new Number[TREND_DAYS];
        for (int i = 0; i < TREND_DAYS; i++) {
            values[i] = byDay.getOrDefault(today.minusDays(TREND_DAYS - 1L - i), BigDecimal.ZERO);
        }
        return values;
    }

    /**
     * 渠道图横轴
     *
     * @param channels 渠道聚合
     * @return 渠道展示名；今日无收款时给一个占位类目（空类目图会画成一条白线）
     */
    private List<String> channelLabels(List<ChannelAmount> channels) {
        if (channels == null || channels.isEmpty()) {
            return List.of("今日无收款");
        }
        List<String> labels = new ArrayList<>(channels.size());
        for (ChannelAmount channel : channels) {
            labels.add(displayChannel(channel.channel()));
        }
        return labels;
    }

    /**
     * 渠道图数值
     *
     * @param channels 渠道聚合
     * @return 每个渠道的金额；今日无收款时给一个 0
     */
    private Number[] channelValues(List<ChannelAmount> channels) {
        if (channels == null || channels.isEmpty()) {
            return new Number[]{BigDecimal.ZERO};
        }
        Number[] values = new Number[channels.size()];
        for (int i = 0; i < channels.size(); i++) {
            BigDecimal amount = channels.get(i).amount();
            values[i] = amount == null ? BigDecimal.ZERO : amount;
        }
        return values;
    }

    /**
     * 渠道编码转展示名
     *
     * @param channel 渠道编码
     * @return 展示名
     */
    private String displayChannel(String channel) {
        if (channel == null || channel.isBlank() || "UNKNOWN".equals(channel)) {
            return UNKNOWN_CHANNEL;
        }
        return channel;
    }

    /**
     * 追加告警：失败先判、超时补一刀
     *
     * <p>两条可以同时存在：失败笔数是"渠道坏了"，超时未支付是"回调丢了"，
     * 处置动作不同（一个查渠道配置、一个查回调与幂等），所以要分开报。</p>
     *
     * @param builder 组装器
     * @param failed  今日支付失败笔数
     * @param pending 待支付统计
     */
    private void appendAlerts(Overviews.Builder builder, long failed, PendingStat pending) {
        if (failed >= FAILED_ALERT_COUNT) {
            builder.alert(OverviewAlertLevel.CRITICAL,
                    "今日支付失败 " + failed + " 笔，请查渠道与失败流水", Math.toIntExact(failed));
        }
        if (pending.stale() >= STALE_PENDING_ALERT) {
            builder.alert(OverviewAlertLevel.WARNING,
                    pending.stale() + " 笔支付单超过 24 小时未支付，回调可能丢失",
                    Math.toIntExact(pending.stale()));
        }
    }
}
