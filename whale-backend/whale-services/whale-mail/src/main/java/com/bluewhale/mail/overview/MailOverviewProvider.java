package com.bluewhale.mail.overview;

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
 * 邮驿（MAIL）概览：外城九处第四处——城里的信件从这里进出。
 *
 * <p><b>这一屏讲什么。</b>平台里的信不是"推"进来的，是同步流程按 IMAP 一趟趟
 * 拉进来的。所以邮驿的主账不是"存了多少封信"，而是<b>信还在不在流动</b>：
 * 有多少邮箱一天内真的拉到过新信、有多少超时没动静、有多少开了同步却一次没跑成。
 * 同步一停，屏风本身不会报错——它只会一直显示旧信，这才是最难发现的一类故障。</p>
 *
 * <p><b>与别处不重复。</b>清议堂看公开讨论的秩序、传信司看即时会话、文库看归档，
 * 邮驿看的是<b>异步信件的收发链路</b>：账号同步（拉到没有）、配额（还能不能收）、
 * 退信（送没送到）。三件事分别对应"进来""存住""出去"，正好是本殿的三道门。</p>
 *
 * <p><b>口径不另造。</b>所有数字来自 {@link MailOverviewStatsService}，与 2D 管理端
 * 同一批表、同一套租户隔离插件。退信只数近 7 日并区分永久/临时：永久退信说明
 * 地址废了，临时退信对端多半会自动重投，混成一个数会让值班的人分不清要不要动手。</p>
 *
 * <p><b>取不到数就让它抛。</b>这里不 catch、不返回 0：库连不上与"一个账号都没开"
 * 长得一模一样。SDK 会把异常降级成 stale/degraded，屏风上写"未接通"。</p>
 *
 * @author BlueWhale
 */
@Component
@RequiredArgsConstructor
public class MailOverviewProvider implements OverviewProvider {

    /** 服务一级前缀，与网关路由一致 */
    private static final String SERVICE = "mail";

    /** 城内节点 key（邮驿），与 {@code city-layout.json} 对齐 */
    private static final String NODE_KEY = "youyi-stage";

    /** 权限码：邮箱账号清单（本屏统的是全租户的邮箱，不是某个人的收件箱） */
    private static final String ACCOUNT_PERMISSION = "sys:mail:account:list";

    /** 权限码：读信（邮件列表与退信回执都用它，与 2D 后台一致） */
    private static final String MESSAGE_PERMISSION = "sys:mail:message:list";

    /** 邮件列表接口（网关不剥 /mail 前缀；服务端按当前用户取默认账号） */
    private static final String MESSAGE_PAGE_PATH = "/mail/v1/message/page";

    /** 邮箱账号清单接口（2D 里是 list 不是 page，别照抄其他殿的 page） */
    private static final String ACCOUNT_LIST_PATH = "/mail/v1/account/list";

    /** 退信回执接口 */
    private static final String BOUNCE_PAGE_PATH = "/mail/v1/delivery/report/page";

    /** 邮驿统计口径 */
    private final MailOverviewStatsService statsService;

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
        return "邮驿";
    }

    @Override
    public String subtitle() {
        return "邮件收发";
    }

    @Override
    public String permissionCode() {
        return ACCOUNT_PERMISSION;
    }

    @Override
    public OverviewSnapshot build(OverviewContext context) {
        MailOverviewStats stats = statsService.snapshot(context == null ? null : context.tenantId());
        Overviews.Builder builder = Overviews.builder(SERVICE, NODE_KEY, title())
                .subtitle(subtitle() + " · 驿路与信件")
                .note("口径：同步按 24 小时判新鲜；配额按已用 ≥80% 算吃紧（配额未知的不算）；"
                        + "退信只数近 7 日，永久与临时分开");
        appendKpis(builder, stats);
        appendSyncChart(builder, stats);
        appendLinks(builder);
        appendAlerts(builder, stats);
        return builder.build();
    }

    /**
     * 四张牌：在册邮箱、同步超时、配额吃紧、近七日退信。
     *
     * <p>后三张是这一屏的语气所在：超时说明信进不来、吃紧说明信快存不住、
     * 退信说明信出不去——任何一张不为 0 都该黄。在册邮箱只报数，
     * 语气留给告警条：开得多不是坏事。</p>
     *
     * @param builder 组装器
     * @param stats   统计快照
     */
    private static void appendKpis(Overviews.Builder builder, MailOverviewStats stats) {
        builder.kpi(Overviews.count("mailboxes", "在册邮箱", stats.accountEnabled(), OverviewTone.FLAT,
                        mailboxHint(stats)))
                .kpi(Overviews.count("sync-overdue", "同步超时", stats.syncOverdue(),
                        warnIfPositive(stats.syncOverdue()), overdueHint(stats)))
                .kpi(Overviews.count("quota-tight", "配额吃紧", stats.quotaTight(),
                        warnIfPositive(stats.quotaTight()), quotaHint(stats)))
                .kpi(Overviews.count("bounce", "近七日退信", stats.bounce7d(),
                        warnIfPositive(stats.bounce7d()), bounceHint(stats)));
    }

    /**
     * 有账就黄，没账就绿：邮驿三张欠账牌共用的语气规则。
     *
     * @param count 计数
     * @return 语气
     */
    private static OverviewTone warnIfPositive(long count) {
        return count > 0 ? OverviewTone.WARN : OverviewTone.GOOD;
    }

    /**
     * 在册邮箱的提示语：只报启用的，停用的单独提一句。
     *
     * @param stats 统计快照
     * @return 提示语
     */
    private static String mailboxHint(MailOverviewStats stats) {
        if (stats.accountTotal() <= 0) {
            return "还没有开通邮箱";
        }
        return stats.accountDisabled() > 0 ? "另停用 " + stats.accountDisabled() + " 个" : "全部启用";
    }

    /**
     * 同步超时的提示语：超时意味着屏风上的信停在过去。
     *
     * @param stats 统计快照
     * @return 提示语
     */
    private static String overdueHint(MailOverviewStats stats) {
        return stats.syncOverdue() > 0 ? "超过一天没拉到新信" : "同步都跟得上";
    }

    /**
     * 配额吃紧的提示语：八成是提醒线，不是上限。
     *
     * @param stats 统计快照
     * @return 提示语
     */
    private static String quotaHint(MailOverviewStats stats) {
        /* 牌面提示只有一行的宽度：13 个字会被 ellipsis 截掉（实测"已用八成以上，该清理…"），
     "该清理"这层意思交给告警条说。 */
        return stats.quotaTight() > 0 ? "已用八成以上" : "配额都还宽裕";
    }

    /**
     * 退信的提示语：永久与临时分开，两者的处置完全不同。
     *
     * @param stats 统计快照
     * @return 提示语
     */
    private static String bounceHint(MailOverviewStats stats) {
        if (stats.bounce7d() <= 0) {
            return "近七日没有退信";
        }
        long soft = stats.bounce7d() - stats.bounceHard7d();
        return "永久 " + stats.bounceHard7d() + " · 临时 " + soft;
    }

    /**
     * 账号同步四态柱图：驿路通不通，一眼看完。
     *
     * <p><b>为什么是分布不是趋势。</b>邮驿的账是存量不是流量：多少账号在正常拉信、
     * 多少掉队、多少从没跑成、多少压根没开同步——这四格决定"这座驿站现在是什么
     * 状态"。四态互斥且合计等于在册总数，柱高加起来就是全租户，不会出现
     * "四根柱子加起来比总数多"这种把两套口径混在一张图里的错。</p>
     *
     * @param builder 组装器
     * @param stats   统计快照
     */
    private static void appendSyncChart(Overviews.Builder builder, MailOverviewStats stats) {
        List<String> categories = List.of("近一日已同步", "超时未同步", "从未同步", "未开同步");
        List<Double> values = List.of((double) stats.syncFresh(), (double) stats.syncOverdue(),
                (double) stats.syncNever(), (double) stats.syncOff());
        builder.chart(Overviews.chart("sync-health", "账号同步分布", "bar", OverviewFormat.COUNT,
                categories, new OverviewSeries("邮箱数", values)));
    }

    /**
     * 入口：邮件列表 + 邮箱账号 + 退信回执。
     *
     * <p>邮件列表排第一（邮驿的主账，读的是当前用户默认账号的收件箱）；邮箱账号是
     * 运维落点；退信回执是"信没送到"的现场。配规则与同步触发不单摆入口：
     * 两者在 2D 里都是低频动作，屏风上硬凑只会把入口铺满。</p>
     *
     * @param builder 组装器
     */
    private static void appendLinks(Overviews.Builder builder) {
        builder.link(Overviews.link("inbox", "邮件列表", MESSAGE_PAGE_PATH, MESSAGE_PERMISSION));
        builder.link(Overviews.link("accounts", "邮箱账号", ACCOUNT_LIST_PATH, ACCOUNT_PERMISSION));
        builder.link(Overviews.link("bounces", "退信回执", BOUNCE_PAGE_PATH, MESSAGE_PERMISSION));
    }

    /**
     * 告警条：同步超时、从未同步、配额吃紧、永久退信、临时退信、空库。
     *
     * <p>这些事"数字本身不会喊"：{@code 3} 个账号超时不会自己变红，{@code 0} 个账号
     * 与"今天没快照"看起来一样。临时退信只报 INFO：对端繁忙与限流会自愈，
     * 天天报警等于教人忽略告警。</p>
     *
     * @param builder 组装器
     * @param stats   统计快照
     */
    private static void appendAlerts(Overviews.Builder builder, MailOverviewStats stats) {
        if (stats.syncOverdue() > 0) {
            builder.alert(OverviewAlertLevel.WARNING, "有 " + stats.syncOverdue()
                    + " 个邮箱超过一天没同步，屏风上的信会越来越旧", null);
        }
        if (stats.syncNever() > 0) {
            builder.alert(OverviewAlertLevel.INFO, "有 " + stats.syncNever()
                    + " 个邮箱开了同步但一次没跑成，先验协议端点与口令", null);
        }
        if (stats.quotaTight() > 0) {
            builder.alert(OverviewAlertLevel.WARNING, "有 " + stats.quotaTight()
                    + " 个邮箱配额已用八成以上，再满就收不进新信", null);
        }
        if (stats.bounceHard7d() > 0) {
            builder.alert(OverviewAlertLevel.WARNING, "近七日有 " + stats.bounceHard7d()
                    + " 封永久退信（5.x.x），地址多半已失效，重发也是白发", null);
        }
        long soft = stats.bounce7d() - stats.bounceHard7d();
        if (soft > 0) {
            builder.alert(OverviewAlertLevel.INFO, "近七日有 " + soft
                    + " 封临时退信（4.x.x），对端繁忙或限流，会自动重投", null);
        }
        if (stats.accountTotal() <= 0) {
            builder.alert(OverviewAlertLevel.INFO, "邮驿还空着：一个邮箱账号都没开", null);
        }
    }
}
