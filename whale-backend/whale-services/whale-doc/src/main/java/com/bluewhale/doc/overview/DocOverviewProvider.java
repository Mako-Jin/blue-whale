package com.bluewhale.doc.overview;

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
 * 翰林（DOC）概览：内城十二坊的第十一个坊——文档知识。
 *
 * <p><b>这一屏讲什么。</b>翰林院掌文书：城里有几处文书房（空间）、
 * 存了多少卷文稿（页面）、有多少篇卡在发布审批、回收站里堆了几棵待处置的稿子。
 * 它不重复任何一殿——藏书殿管文件本身，翰林看的是<b>写出来的内容走到了哪一步</b>：
 * 草稿、发布、审批、归档、回收站这条流水线，在 2D 后台要切空间页、审批流、
 * 回收站三处才看得全。</p>
 *
 * <p><b>口径不另造。</b>所有数字来自 {@link DocOverviewStatsService}，
 * 与 2D 空间页 / 回收站同一批表、同一套租户隔离插件；回收站按"棵"数
 * （整棵删除子树算一棵），与 2D 回收站按根节点操作同一口径。</p>
 *
 * <p><b>取不到数就让它抛。</b>这里不 catch、不返回 0：库连不上与"一个空间都没建"
 * 长得一模一样。SDK 会把异常降级成 stale/degraded，屏风上写"未接通"。</p>
 *
 * @author BlueWhale
 */
@Component
@RequiredArgsConstructor
public class DocOverviewProvider implements OverviewProvider {

    /** 服务一级前缀，与网关路由一致 */
    private static final String SERVICE = "doc";

    /** 城内节点 key（翰林院），与 {@code city-layout.json} 对齐 */
    private static final String NODE_KEY = "hanlin-academy";

    /** 权限码：复用 2D 空间台账同一码，不为 3D 单独发一套授权 */
    private static final String PERMISSION = "sys:doc:space:list";

    /** 空间台账列表接口：屏风页直接取这个路径 */
    private static final String SPACE_PAGE_PATH = "/doc/space/page";

    /** 翰林统计口径 */
    private final DocOverviewStatsService statsService;

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
        return "翰林";
    }

    @Override
    public String subtitle() {
        return "文档知识";
    }

    @Override
    public String permissionCode() {
        return PERMISSION;
    }

    @Override
    public OverviewSnapshot build(OverviewContext context) {
        DocOverviewStats stats = statsService.snapshot(context == null ? null : context.tenantId());
        Overviews.Builder builder = Overviews.builder(SERVICE, NODE_KEY, title())
                .subtitle(subtitle() + " · 文书四本账")
                .note("口径：正式页面 = 未入回收站（含目录节点）；回收站按棵数"
                        + "（整棵删除子树算一棵）；待审批 = 卡在发布审批的页面");
        appendKpis(builder, stats);
        appendStatusChart(builder, stats);
        appendLinks(builder);
        appendAlerts(builder, stats);
        return builder.build();
    }

    /**
     * 四张牌：文档空间、文稿页面、待发布审批、回收站。
     *
     * <p>"待发布审批"是这一屏唯一带语气的牌：发布审批是人家的把关环节，
     * 压着不批，作者就一直改不下去——这是翰林要盯的头等事。
     * 其余三张牌只报数，语气留给告警条。</p>
     *
     * @param builder 组装器
     * @param stats   统计快照
     */
    private static void appendKpis(Overviews.Builder builder, DocOverviewStats stats) {
        builder.kpi(Overviews.count("spaces", "文档空间", stats.spaceActive(), OverviewTone.FLAT,
                        spaceHint(stats)))
                .kpi(Overviews.count("pages", "文稿页面", stats.pageTotal(), OverviewTone.FLAT,
                        pageHint(stats)))
                .kpi(Overviews.count("pending", "待发布审批", stats.pagePendingApproval(),
                        pendingTone(stats), pendingHint(stats)))
                .kpi(Overviews.count("trash", "回收站", stats.trashRoots(), OverviewTone.FLAT,
                        trashHint(stats)));
    }

    /**
     * 空间牌的提示语：归档空间是沉淀下来的旧账，值得说一句。
     *
     * @param stats 统计快照
     * @return 提示语
     */
    private static String spaceHint(DocOverviewStats stats) {
        if (stats.spaceActive() <= 0 && stats.spaceArchived() <= 0) {
            return "还没有建文书房";
        }
        return "归档 " + stats.spaceArchived() + " 处";
    }

    /**
     * 页面牌的提示语：写出发布/草稿两段，避免"100 篇"看不出成色。
     *
     * @param stats 统计快照
     * @return 提示语
     */
    private static String pageHint(DocOverviewStats stats) {
        return "已发布 " + stats.pagePublished() + " · 草稿 " + stats.pageDraft();
    }

    /**
     * 待审批牌的语气：有稿压着就黄，没有就绿。
     *
     * @param stats 统计快照
     * @return 语气
     */
    private static OverviewTone pendingTone(DocOverviewStats stats) {
        return stats.pagePendingApproval() > 0 ? OverviewTone.WARN : OverviewTone.GOOD;
    }

    /**
     * 待审批牌的提示语：没有积压时说清楚而不是留空。
     *
     * @param stats 统计快照
     * @return 提示语
     */
    private static String pendingHint(DocOverviewStats stats) {
        return stats.pagePendingApproval() > 0 ? "发布审批积压，要人去核" : "发布审批没有积压";
    }

    /**
     * 回收站牌的提示语：按棵数，删多深的子树都算一棵。
     *
     * @param stats 统计快照
     * @return 提示语
     */
    private static String trashHint(DocOverviewStats stats) {
        return stats.trashRoots() > 0 ? stats.trashRoots() + " 棵删除子树待处置" : "回收站是空的";
    }

    /**
     * 页面状态分布柱图：草稿 / 已发布 / 待审批 / 已归档。
     *
     * <p><b>为什么是分布不是趋势。</b>翰林的账是存量不是流量：页面今天比昨天
     * 多几篇没有多少信息量，"100 篇里 70 篇还是草稿"这种结构才是文书管理
     * 要先看的东西。状态码值固定四种，柱图比折线少一根轴的歧义。</p>
     *
     * @param builder 组装器
     * @param stats   统计快照
     */
    private static void appendStatusChart(Overviews.Builder builder, DocOverviewStats stats) {
        List<String> categories = List.of("草稿", "已发布", "待审批", "已归档");
        List<Double> values = List.of((double) stats.pageDraft(), (double) stats.pagePublished(),
                (double) stats.pagePendingApproval(), (double) stats.pageArchived());
        builder.chart(Overviews.chart("page-status", "文稿状态分布", "bar", OverviewFormat.COUNT,
                categories, new OverviewSeries("页面数", values)));
    }

    /**
     * 入口：空间台账。
     *
     * <p>只挂空间一个入口：页面按空间树形组织（没有租户级页面清单），
     * 点进空间再看树才是 2D 的正确姿势；屏风上硬凑一张跨空间页面大表
     * 只会误导。回收站在 2D 按空间进，也同理不单摆。</p>
     *
     * @param builder 组装器
     */
    private static void appendLinks(Overviews.Builder builder) {
        builder.link(Overviews.link("spaces", "空间台账", SPACE_PAGE_PATH, PERMISSION));
    }

    /**
     * 告警条：审批积压、回收站积压、有稿未发布、空间全归档、无空间。
     *
     * <p>这些事"数字本身不会喊"：{@code 5} 篇待审批不会自己变红，
     * {@code 0} 个空间与"今天没快照"看起来一样。</p>
     *
     * @param builder 组装器
     * @param stats   统计快照
     */
    private static void appendAlerts(Overviews.Builder builder, DocOverviewStats stats) {
        if (stats.pagePendingApproval() > 0) {
            builder.alert(OverviewAlertLevel.WARNING, "有 " + stats.pagePendingApproval()
                    + " 篇页面卡在发布审批，作者改不了也撤不回，需要人去核", null);
        }
        if (stats.trashRoots() > 0) {
            builder.alert(OverviewAlertLevel.INFO, "回收站里有 " + stats.trashRoots()
                    + " 棵删除子树，恢复或彻底清除前一直占着位置", null);
        }
        if (stats.pageTotal() > 0 && stats.pagePublished() == 0) {
            builder.alert(OverviewAlertLevel.INFO, "有页面在写但还没有任何一篇发布过，"
                    + "城里搜不到这些内容", null);
        }
        if (stats.spaceActive() <= 0 && stats.spaceArchived() > 0) {
            builder.alert(OverviewAlertLevel.INFO, "所有文书房都已归档，页面还在但不可再编辑", null);
        }
        if (stats.spaceActive() <= 0 && stats.spaceArchived() <= 0) {
            builder.alert(OverviewAlertLevel.INFO, "还没有建文书房，翰林暂时无账可理", null);
        }
    }
}
