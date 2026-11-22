package com.bluewhale.video.overview;

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
 * 影戏院（VIDEO）概览：外城九处第二处——内容的整条流水线。
 *
 * <p><b>这一屏讲什么。</b>影戏院放片子：库里有多少能播、多少压在待审没人放行、
 * 多少评论等着过目、多少电影的授权快到期了。它也不重复别处——翰林院看文档、
 * 乐坊看曲子，影戏院看的是<b>片子从上传到可播、再到授权失效的全程</b>：
 * 待审、上线、评论、版权到期这条链，在 2D 后台要切内容页、评论审核页、
 * 电影授权页三处才看得全。</p>
 *
 * <p><b>口径不另造。</b>所有数字来自 {@link VideoOverviewStatsService}，
 * 与 2D 内容管理 / 评论审核 / 电影台账同一批表、同一套租户隔离插件；
 * 待办只数未出结论的一段（待审内容、待审评论），版权下架是处置结果不算欠账。
 * 授权到期窗口 7 天，与 2D 运营统计默认窗口同一口径。</p>
 *
 * <p><b>取不到数就让它抛。</b>这里不 catch、不返回 0：库连不上与"一部片子都没有"
 * 长得一模一样。SDK 会把异常降级成 stale/degraded，屏风上写"未接通"。</p>
 *
 * @author BlueWhale
 */
@Component
@RequiredArgsConstructor
public class VideoOverviewProvider implements OverviewProvider {

    /** 服务一级前缀，与网关路由一致 */
    private static final String SERVICE = "video";

    /** 城内节点 key（影戏院），与 {@code city-layout.json} 对齐 */
    private static final String NODE_KEY = "yingxi-stage";

    /** 权限码：复用 2D 内容台账同一码，不为 3D 单独发一套授权 */
    private static final String CONTENT_PERMISSION = "sys:video:content:list";

    /** 电影台账权限码：运营入口用 */
    private static final String MOVIE_PERMISSION = "sys:video:movie:manage";

    /** 内容台账列表接口：屏风页直接取这个路径（网关不剥 /video 前缀） */
    private static final String CONTENT_PAGE_PATH = "/video/v1/admin/content/page";

    /** 电影台账列表接口 */
    private static final String MOVIE_PAGE_PATH = "/video/v1/admin/movies/page";

    /** 影戏院统计口径 */
    private final VideoOverviewStatsService statsService;

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
        return "影戏院";
    }

    @Override
    public String subtitle() {
        return "内容运营";
    }

    @Override
    public String permissionCode() {
        return CONTENT_PERMISSION;
    }

    @Override
    public OverviewSnapshot build(OverviewContext context) {
        VideoOverviewStats stats = statsService.snapshot(context == null ? null : context.tenantId());
        Overviews.Builder builder = Overviews.builder(SERVICE, NODE_KEY, title())
                .subtitle(subtitle() + " · 内容流水线")
                .note("口径：待办只数未出结论的一段（待审内容 / 待审评论）；授权到期窗口 "
                        + "7 天；版权下架是处置结果不算欠账");
        appendKpis(builder, stats);
        appendStatusChart(builder, stats);
        appendLinks(builder);
        appendAlerts(builder, stats);
        return builder.build();
    }

    /**
     * 四张牌：已发布内容、待审内容、待审评论、授权将到期。
     *
     * <p>后三张是这一屏的语气所在：投稿人等着放行（待审内容）、观众等着留言过审
     * （待审评论）、电影授权快到期不处理就会被迫下线（授权将到期）——
     * 任何一张不为 0 都该黄。已发布只报数，语气留给告警条。</p>
     *
     * @param builder 组装器
     * @param stats   统计快照
     */
    private static void appendKpis(Overviews.Builder builder, VideoOverviewStats stats) {
        builder.kpi(Overviews.count("published", "已发布内容", stats.contentPublished(), OverviewTone.FLAT,
                        publishedHint(stats)))
                .kpi(Overviews.count("pending-review", "待审内容", stats.contentPendingReview(),
                        warnIfPositive(stats.contentPendingReview()), pendingHint(stats)))
                .kpi(Overviews.count("comment-pending", "待审评论", stats.commentPending(),
                        warnIfPositive(stats.commentPending()), commentHint(stats)))
                .kpi(Overviews.count("license-expiring", "授权将到期", stats.licenseExpiring(),
                        warnIfPositive(stats.licenseExpiring()), licenseHint(stats)));
    }

    /**
     * 有账就黄，没账就绿：影戏院三张运营牌共用的语气规则。
     *
     * @param count 计数
     * @return 语气
     */
    private static OverviewTone warnIfPositive(long count) {
        return count > 0 ? OverviewTone.WARN : OverviewTone.GOOD;
    }

    /**
     * 已发布牌的提示语：架上成色——草稿与下线各压着几部。
     *
     * @param stats 统计快照
     * @return 提示语
     */
    private static String publishedHint(VideoOverviewStats stats) {
        if (stats.contentTotal() <= 0) {
            return "还没有一部片子";
        }
        return "草稿 " + stats.contentDraft() + " · 已下线 " + stats.contentOffline()
                + " · 累计播放 " + stats.totalPlay();
    }

    /**
     * 待审内容牌的提示语：投稿人等着放行。
     *
     * @param stats 统计快照
     * @return 提示语
     */
    private static String pendingHint(VideoOverviewStats stats) {
        return stats.contentPendingReview() > 0 ? "送审中，等着运营过目" : "没有待审内容";
    }

    /**
     * 待审评论牌的提示语：先审后发或先发后审，压着都是观众等在门外。
     *
     * @param stats 统计快照
     * @return 提示语
     */
    private static String commentHint(VideoOverviewStats stats) {
        return stats.commentPending() > 0 ? "压着没放行，观众等不到自己的留言" : "评论没有积压";
    }

    /**
     * 授权将到期牌的提示语：电影授权是外部合同，过期就得下线。
     *
     * @param stats 统计快照
     * @return 提示语
     */
    private static String licenseHint(VideoOverviewStats stats) {
        return stats.licenseExpiring() > 0 ? "7 天内到期，不续签就得下线" : "近期没有授权到期";
    }

    /**
     * 内容发布状态分布柱图：四态全景。
     *
     * <p><b>为什么是分布不是趋势。</b>影戏院的屏风账是存量不是流量：库结构
     * （多少压在待审、多少已下线占着地方）比"今天传了几部"先要看。
     * 四态码值固定（{@code PublishState}），柱图比折线少一根轴的歧义。</p>
     *
     * @param builder 组装器
     * @param stats   统计快照
     */
    private static void appendStatusChart(Overviews.Builder builder, VideoOverviewStats stats) {
        List<String> categories = List.of("草稿", "待审", "已发布", "已下线");
        List<Double> values = List.of((double) stats.contentDraft(), (double) stats.contentPendingReview(),
                (double) stats.contentPublished(), (double) stats.contentOffline());
        builder.chart(Overviews.chart("content-status", "内容发布状态分布", "bar", OverviewFormat.COUNT,
                categories, new OverviewSeries("内容数", values)));
    }

    /**
     * 入口：内容台账 + 电影台账。
     *
     * <p>内容是影戏院主账，排第一；电影台账给"授权与元数据"一个直通入口。
     * 评论审核不单摆：2D 里都从内容进，屏风上硬凑只会把入口铺满。</p>
     *
     * @param builder 组装器
     */
    private static void appendLinks(Overviews.Builder builder) {
        builder.link(Overviews.link("contents", "内容台账", CONTENT_PAGE_PATH, CONTENT_PERMISSION));
        builder.link(Overviews.link("movies", "电影台账", MOVIE_PAGE_PATH, MOVIE_PERMISSION));
    }

    /**
     * 告警条：待审内容、待审评论、授权到期、版权下架存量、空库。
     *
     * <p>这些事"数字本身不会喊"：{@code 5} 部待审内容不会自己变红，
     * {@code 0} 部片子与"今天没快照"看起来一样。版权下架是处置完了的结果，
     * 只提醒存量不催办；草稿与已下线不进告警——投稿人自己的事，
     * 运营要盯的是自己这一侧的欠账。</p>
     *
     * @param builder 组装器
     * @param stats   统计快照
     */
    private static void appendAlerts(Overviews.Builder builder, VideoOverviewStats stats) {
        if (stats.contentPendingReview() > 0) {
            builder.alert(OverviewAlertLevel.WARNING, "有 " + stats.contentPendingReview()
                    + " 部内容待审，投稿人等着放行", null);
        }
        if (stats.commentPending() > 0) {
            builder.alert(OverviewAlertLevel.WARNING, "有 " + stats.commentPending()
                    + " 条评论待审，压着没放行，观众等不到自己的留言", null);
        }
        if (stats.licenseExpiring() > 0) {
            builder.alert(OverviewAlertLevel.WARNING, "有 " + stats.licenseExpiring()
                    + " 部电影授权 7 天内到期，不续签就得下线", null);
        }
        if (stats.copyrightTakedown() > 0) {
            builder.alert(OverviewAlertLevel.INFO, "存量 " + stats.copyrightTakedown()
                    + " 部版权下架内容，处置已出结论，如需恢复走版权复核流程", null);
        }
        if (stats.contentTotal() <= 0) {
            builder.alert(OverviewAlertLevel.INFO, "影戏院还是空的：一部片子都没有", null);
        }
    }
}
