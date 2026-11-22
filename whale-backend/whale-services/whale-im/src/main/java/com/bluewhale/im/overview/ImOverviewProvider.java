package com.bluewhale.im.overview;

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
 * 传信司（IM）概览：外城九处第五处——城里的即时传信都从这里过。
 *
 * <p><b>这一屏讲什么。</b>IM 有两件事最容易"看起来正常"：① 会话列表永远很长，
 * 哪怕三个月没人说话；② 机器人一直在，哪怕它每次都在等一个没人点的确认。
 * 所以这一屏的主图不是会话数，而是<b>近七日每天到底发出多少条消息</b>——
 * 这条线掉到 0 的时候，会话数一点都不会变。</p>
 *
 * <p><b>与别处不重复。</b>邮驿看异步信件的链路（同步/配额/退信）、通政殿看站内通知的
 * 投递、这里看的是<b>人与人（以及人与机器人）的即时会话</b>：今天还有谁在说话、
 * 哪些会话已经静了七天、机器人有没有卡在等确认。</p>
 *
 * <p><b>口径不另造。</b>所有数字来自 {@link ImOverviewStatsService}，与 2D 会话列表
 * 同一批表、同一套租户隔离插件。消息只数"正常"状态：撤回的消息已经不算传出去了。</p>
 *
 * <p><b>取不到数就让它抛。</b>这里不 catch、不返回 0：库连不上与"一个会话都没有"
 * 长得一模一样。SDK 会把异常降级成 stale/degraded，屏风上写"未接通"。</p>
 *
 * @author BlueWhale
 */
@Component
@RequiredArgsConstructor
public class ImOverviewProvider implements OverviewProvider {

    /** 服务一级前缀，与网关路由一致 */
    private static final String SERVICE = "im";

    /** 城内节点 key（传信司），与 {@code city-layout.json} 对齐 */
    private static final String NODE_KEY = "chuanxin-stage";

    /** 权限码：IM 会话与消息（whale-im 全类只有这一个码，不臆造 browse / manage） */
    private static final String CHAT_PERMISSION = "sys:im:chat";

    /** 会话列表接口（返回普通数组，不是分页对象；屏风页按数组兼容） */
    private static final String CONVERSATION_LIST_PATH = "/im/v1/conversations";

    /** 柱图日期标签：屏风宽度只放得下"月-日" */
    private static final DateTimeFormatter DAY_LABEL = DateTimeFormatter.ofPattern("MM-dd");

    /** 传信司统计口径 */
    private final ImOverviewStatsService statsService;

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
        return "传信司";
    }

    @Override
    public String subtitle() {
        return "即时传信";
    }

    @Override
    public String permissionCode() {
        return CHAT_PERMISSION;
    }

    @Override
    public OverviewSnapshot build(OverviewContext context) {
        ImOverviewStats stats = statsService.snapshot(context == null ? null : context.tenantId());
        Overviews.Builder builder = Overviews.builder(SERVICE, NODE_KEY, title())
                .subtitle(subtitle() + " · 会话与驿路")
                .note("口径：会话按最后活跃时间判今日/静默（七日）；消息只数正常状态，"
                        + "撤回的不算；机器人两笔账只看待确认与近七日失败");
        appendKpis(builder, stats);
        appendTrendChart(builder, stats);
        appendLinks(builder);
        appendAlerts(builder, stats);
        return builder.build();
    }

    /**
     * 四张牌：会话总数、今日消息、静默会话、待确认。
     *
     * <p>会话总数只报数（存量大小不是坏事），后两张是欠账：一个会话静了七天
     * 可能只是群人散了，也可能是通知没送到；一次运行卡在待确认，写操作就永远
     * 停在半路。今日消息的语气跟数量走：有消息是绿的，0 条只报平色——
     * 凌晨三点看这一屏不该是黄灯。</p>
     *
     * @param builder 组装器
     * @param stats 统计快照
     */
    private static void appendKpis(Overviews.Builder builder, ImOverviewStats stats) {
        builder.kpi(Overviews.count("conversations", "会话总数", stats.conversationTotal(),
                        OverviewTone.FLAT, conversationHint(stats)))
                .kpi(Overviews.count("messages-today", "今日消息", stats.messageToday(),
                        stats.messageToday() > 0 ? OverviewTone.GOOD : OverviewTone.FLAT,
                        "近七日 " + stats.message7d() + " 条"))
                .kpi(Overviews.count("silent", "静默会话", stats.idle7d(),
                        warnIfPositive(stats.idle7d()), "七日没有新消息"))
                .kpi(Overviews.count("pending-confirm", "待确认", stats.pendingConfirm(),
                        warnIfPositive(stats.pendingConfirm()), "机器人在等人工点头"));
    }

    /**
     * 有账就黄，没账就绿：传信司两张欠账牌共用的语气规则。
     *
     * @param count 计数
     * @return 语气
     */
    private static OverviewTone warnIfPositive(long count) {
        return count > 0 ? OverviewTone.WARN : OverviewTone.GOOD;
    }

    /**
     * 会话总数的提示语：把单聊与机器人拆开说一句。
     *
     * <p>群聊不单独报：群的数量通常在个位数，占不了这一行的宽度；
     * 单聊与机器人会话才是"这一城有多少条一对一通路"。</p>
     *
     * @param stats 统计快照
     * @return 提示语
     */
    private static String conversationHint(ImOverviewStats stats) {
        if (stats.conversationTotal() <= 0) {
            return "还没有会话";
        }
        return "单聊 " + stats.conversationSingle() + " · 机器人 " + stats.conversationBot();
    }

    /**
     * 近七日消息量柱图：驿路通不通，看的是每天有没有人说话。
     *
     * <p><b>为什么是逐日而不是分类型。</b>按单聊/群聊/机器人分堆只能说明"谁在说"，
     * 说明不了"还在不在说"；这条曲线掉到 0 的那一刻，会话总数不会有任何变化。
     * 七根柱子刚好放满屏风中部，再多就要横向滚动——屏风上套滚动是最难用的组合。</p>
     *
     * @param builder 组装器
     * @param stats 统计快照
     */
    private static void appendTrendChart(Overviews.Builder builder, ImOverviewStats stats) {
        List<String> categories = stats.recentDays().stream()
                .map(day -> day.day().format(DAY_LABEL))
                .toList();
        List<Double> values = stats.recentDays().stream()
                .map(day -> (double) day.count())
                .toList();
        builder.chart(Overviews.chart("message-trend", "近七日消息量", "bar", OverviewFormat.COUNT,
                categories, new OverviewSeries("消息数", values)));
    }

    /**
     * 入口：会话列表。
     *
     * <p>只摆一个入口。IM 在 2D 里就是"一个应用、一个会话列表"，其余动作
     * （建群、拉人、确认写操作）都在会话内部完成，没有可独立落地的列表页；
     * 硬凑几个禁用标签只会让屏风看起来像坏了。</p>
     *
     * @param builder 组装器
     */
    private static void appendLinks(Overviews.Builder builder) {
        builder.link(Overviews.link("conversations", "会话列表", CONVERSATION_LIST_PATH, CHAT_PERMISSION));
    }

    /**
     * 告警条：待确认、运行失败、静默会话、今日无消息、无机器人绑定、空库。
     *
     * <p>静默会话只报 INFO：群散了是正常生命周期，天天报警等于教人忽略告警。
     * 待确认与失败报 WARNING：一个是写操作停在半路，一个是用户只看到
     * "助手开小差"，两件都要人去看一眼。</p>
     *
     * @param builder 组装器
     * @param stats 统计快照
     */
    private static void appendAlerts(Overviews.Builder builder, ImOverviewStats stats) {
        if (stats.pendingConfirm() > 0) {
            builder.alert(OverviewAlertLevel.WARNING, "有 " + stats.pendingConfirm()
                    + " 次机器人运行在等人工确认，写操作不会自己往下走", null);
        }
        if (stats.runFailed7d() > 0) {
            builder.alert(OverviewAlertLevel.WARNING, "近七日有 " + stats.runFailed7d()
                    + " 次智能体运行失败，用户那边只看到一句「助手开小差」", null);
        }
        if (stats.idle7d() > 0) {
            builder.alert(OverviewAlertLevel.INFO, "有 " + stats.idle7d()
                    + " 个会话七日没有新消息，可能是群人散了，也可能是消息没送到", null);
        }
        if (stats.conversationTotal() > 0 && stats.messageToday() <= 0) {
            builder.alert(OverviewAlertLevel.INFO, "今日还没有新消息：驿路是通的，只是没人来传", null);
        }
        if (stats.conversationTotal() > 0 && stats.botEnabled() <= 0) {
            builder.alert(OverviewAlertLevel.INFO, "还没有启用机器人绑定：助手不会在任何会话里应答", null);
        }
        if (stats.conversationTotal() <= 0) {
            builder.alert(OverviewAlertLevel.INFO, "传信司还空着：一个会话都没有", null);
        }
    }
}
