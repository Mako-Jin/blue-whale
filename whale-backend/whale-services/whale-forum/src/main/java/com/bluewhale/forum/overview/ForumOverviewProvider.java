package com.bluewhale.forum.overview;

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

import java.util.List;

/**
 * 清议堂（FORUM）概览：外城九处第三处——城里人议论的地方。
 *
 * <p><b>这一屏讲什么。</b>清议堂是帖子，但只看"有多少帖"没用：真正要盯的是
 * <b>议事的三道门</b>——作者交了稿等着放行（待审帖）、有人被冒犯要个说法
 * （待受理举报）、作者对驳回或处置不服（待理申诉）。这三笔都是"人不来催
 * 数字不会自己变红"的欠账，所以各占一张黄灯牌。</p>
 *
 * <p><b>与别处不重复。</b>传信司看会话、邮驿看信件、文库看归档，清议堂看的是
 * <b>公开讨论的秩序</b>：内容质量（悬榜 / 题金榜）与治理待办（待审 / 举报 /
 * 申诉）两条线，在 2D 后台要切审核队列、举报工单、申诉处理三页才看得全。</p>
 *
 * <p><b>口径不另造。</b>所有数字来自 {@link ForumOverviewStatsService}，与 2D
 * 管理端同一批表、同一套租户隔离插件。封禁只报存量：那是处置<b>出了结论</b>
 * 的结果，催办的是"举报没人受理"，不是"封了多少人"。</p>
 *
 * <p><b>取不到数就让它抛。</b>这里不 catch、不返回 0：库连不上与"一条帖子都没有"
 * 长得一模一样。SDK 会把异常降级成 stale/degraded，屏风上写"未接通"。</p>
 *
 * @author BlueWhale
 */
@Component
@RequiredArgsConstructor
public class ForumOverviewProvider implements OverviewProvider {

    /** 服务一级前缀，与网关路由一致 */
    private static final String SERVICE = "forum";

    /** 城内节点 key（清议堂），与 {@code city-layout.json} 对齐 */
    private static final String NODE_KEY = "qingyi-stage";

    /** 权限码：管理端入口（本屏含待审 / 举报 / 申诉三笔治理账，普通浏览权限不该看到） */
    private static final String MANAGE_PERMISSION = "sys:forum:manage:list";

    /** 浏览权限码：帖子列表是公开账，与治理账分开 */
    private static final String BROWSE_PERMISSION = "sys:forum:browse";

    /** 审核权限码：审核队列入口用 */
    private static final String AUDIT_PERMISSION = "sys:forum:manage:audit";

    /** 处置权限码：举报工单入口用 */
    private static final String DISPOSE_PERMISSION = "sys:forum:manage:dispose";

    /** 帖子列表接口（公开账，屏风主页面直接取这个路径；网关不剥 /forum 前缀） */
    private static final String THREAD_PAGE_PATH = "/forum/v1/thread/page";

    /** 审核队列接口（待审帖） */
    private static final String AUDIT_PAGE_PATH = "/forum/v1/admin/audit/page";

    /** 举报工单接口 */
    private static final String REPORT_PAGE_PATH = "/forum/v1/admin/report/page";

    /** 清议堂统计口径 */
    private final ForumOverviewStatsService statsService;

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
        return "清议堂";
    }

    @Override
    public String subtitle() {
        return "论坛议事";
    }

    @Override
    public String permissionCode() {
        return MANAGE_PERMISSION;
    }

    @Override
    public OverviewSnapshot build(OverviewContext context) {
        ForumOverviewStats stats = statsService.snapshot(context == null ? null : context.tenantId());
        Overviews.Builder builder = Overviews.builder(SERVICE, NODE_KEY, title())
                .subtitle(subtitle() + " · 议事与秩序")
                .note("口径：待办只数未出结论的一段（待审帖 / 待受理与处理中举报 / 待理申诉）；"
                        + "封禁是处置结果，只报存量");
        appendKpis(builder, stats);
        appendStatusChart(builder, stats);
        appendLinks(builder);
        appendAlerts(builder, stats);
        return builder.build();
    }

    /**
     * 四张牌：在议帖、待审帖、待受理举报、待理申诉。
     *
     * <p>后三张是这一屏的语气所在：作者等着放行、被冒犯的人等一个说法、
     * 被驳回的作者等复核——任何一张不为 0 都该黄。在议帖只报数，
     * 语气留给告警条：帖子多不是坏事。</p>
     *
     * @param builder 组装器
     * @param stats   统计快照
     */
    private static void appendKpis(Overviews.Builder builder, ForumOverviewStats stats) {
        builder.kpi(Overviews.count("published", "在议帖子", stats.threadPublished(), OverviewTone.FLAT,
                        publishedHint(stats)))
                .kpi(Overviews.count("pending", "待审帖子", stats.threadPending(),
                        warnIfPositive(stats.threadPending()), pendingHint(stats)))
                .kpi(Overviews.count("reports", "待受理举报", stats.reportPending(),
                        warnIfPositive(stats.reportPending()), reportHint(stats)))
                .kpi(Overviews.count("appeals", "待理申诉", stats.appealPending(),
                        warnIfPositive(stats.appealPending()), appealHint(stats)));
    }

    /**
     * 有账就黄，没账就绿：清议堂三张治理牌共用的语气规则。
     *
     * @param count 计数
     * @return 语气
     */
    private static OverviewTone warnIfPositive(long count) {
        return count > 0 ? OverviewTone.WARN : OverviewTone.GOOD;
    }

    /**
     * 在议帖的提示语：议事成色——悬榜几篇、题金榜几篇。
     *
     * <p>不写"累计回复"：那要走一次聚合 SQL 或 {@code InterceptorIgnore} 手工拼
     * {@code tenant_id}，为一句提示语开一条跨租户风险的路不值。</p>
     *
     * @param stats 统计快照
     * @return 提示语
     */
    private static String publishedHint(ForumOverviewStats stats) {
        if (stats.threadTotal() <= 0) {
            return "还没有人开帖";
        }
        return "悬榜 " + stats.threadPinned() + " · 题金榜 " + stats.threadEssence()
                + " · 已解决 " + stats.threadSolved();
    }

    /**
     * 待审帖的提示语：作者交了稿，等着版主过目。
     *
     * @param stats 统计快照
     * @return 提示语
     */
    private static String pendingHint(ForumOverviewStats stats) {
        return stats.threadPending() > 0 ? "作者交了稿，等着放行" : "没有待审帖子";
    }

    /**
     * 待受理举报的提示语：SLA 是承诺，超时由定时任务升级。
     *
     * @param stats 统计快照
     * @return 提示语
     */
    private static String reportHint(ForumOverviewStats stats) {
        return stats.reportPending() > 0 ? "含处理中，SLA 24h，超时升级" : "没有待受理举报";
    }

    /**
     * 待理申诉的提示语：被驳回或被处置的作者在等复核。
     *
     * @param stats 统计快照
     * @return 提示语
     */
    private static String appealHint(ForumOverviewStats stats) {
        return stats.appealPending() > 0 ? "作者对驳回或处置不服，等复核" : "没有待理申诉";
    }

    /**
     * 帖子状态分布柱图：五态全景。
     *
     * <p><b>为什么是分布不是趋势。</b>清议堂的屏风账是存量不是流量：多少压在待审、
     * 多少在议、多少已解决/关闭/驳回——这五档决定"这地方现在是什么状态"，
     * 比"今天发了几帖"先要看。五态码值固定（{@code ThreadStatus}），柱图
     * 比折线少一根轴的歧义。</p>
     *
     * @param builder 组装器
     * @param stats   统计快照
     */
    private static void appendStatusChart(Overviews.Builder builder, ForumOverviewStats stats) {
        List<String> categories = List.of("待审", "在议", "已解决", "已关闭", "已驳回");
        List<Double> values = List.of((double) stats.threadPending(), (double) stats.threadPublished(),
                (double) stats.threadSolved(), (double) stats.threadClosed(), (double) stats.threadRejected());
        builder.chart(Overviews.chart("thread-status", "帖子状态分布", "bar", OverviewFormat.COUNT,
                categories, new OverviewSeries("帖子数", values)));
    }

    /**
     * 入口：帖子列表 + 审核队列 + 举报工单。
     *
     * <p>帖子列表排第一（清议堂的主账）；审核队列与举报工单是治理动作的落点，
     * 屏风上点得进去才叫"能办事"。申诉不单摆入口：它在 2D 里从被驳回的帖子进，
     * 屏风上硬凑只会把入口铺满（数量仍由待理申诉那张牌盯着）。</p>
     *
     * @param builder 组装器
     */
    private static void appendLinks(Overviews.Builder builder) {
        builder.link(Overviews.link("threads", "帖子列表", THREAD_PAGE_PATH, BROWSE_PERMISSION));
        builder.link(Overviews.link("audit", "审核队列", AUDIT_PAGE_PATH, AUDIT_PERMISSION));
        builder.link(Overviews.link("reports", "举报工单", REPORT_PAGE_PATH, DISPOSE_PERMISSION));
    }

    /**
     * 告警条：待审帖、举报待受理、申诉待理、封禁存量、空库。
     *
     * <p>这些事"数字本身不会喊"：{@code 5} 条待审不会自己变红，{@code 0} 条帖子
     * 与"今天没快照"看起来一样。封禁只提醒存量不催办——清议堂这一屏催的是
     * 受理与放行，不是人数。</p>
     *
     * @param builder 组装器
     * @param stats   统计快照
     */
    private static void appendAlerts(Overviews.Builder builder, ForumOverviewStats stats) {
        if (stats.threadPending() > 0) {
            builder.alert(OverviewAlertLevel.WARNING, "有 " + stats.threadPending()
                    + " 条帖子待审，作者等着放行", null);
        }
        if (stats.reportPending() > 0) {
            builder.alert(OverviewAlertLevel.WARNING, "有 " + stats.reportPending()
                    + " 张举报待受理，SLA 24 小时，超时会被升级", null);
        }
        if (stats.appealPending() > 0) {
            builder.alert(OverviewAlertLevel.WARNING, "有 " + stats.appealPending()
                    + " 条申诉待理，作者对驳回或处置不服，等复核", null);
        }
        if (stats.banActive() > 0) {
            builder.alert(OverviewAlertLevel.INFO, "当前有 " + stats.banActive()
                    + " 名用户处于禁言或封号，解除走处置流程", null);
        }
        if (stats.threadTotal() <= 0) {
            builder.alert(OverviewAlertLevel.INFO, "清议堂还空着：一条帖子都没有", null);
        }
    }
}
