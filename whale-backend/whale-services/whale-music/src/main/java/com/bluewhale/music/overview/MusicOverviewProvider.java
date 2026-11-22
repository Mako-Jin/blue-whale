package com.bluewhale.music.overview;

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
 * 乐坊（MUSIC）概览：外城九处的第一处——曲库运营，S7 开工坊。
 *
 * <p><b>这一屏讲什么。</b>乐坊掌音律：架上有几支曲子能播、评论与申诉堆着
 * 几件没裁、多少曲子版权还没过目。它不重复任何一殿——翰林院管文档，
 * 乐坊看的是<b>内容从上传到可播的流水线卡在哪一节</b>：版权复核、上架、
 * 评论审核这条链，在 2D 后台要切曲库页、评论审核页、申诉页三处才看得全。</p>
 *
 * <p><b>口径不另造。</b>所有数字来自 {@link MusicOverviewStatsService}，
 * 与 2D 曲库 / 评论审核 / 申诉复核同一批表、同一套租户隔离插件；
 * 待办只数未出结论的一段（待审评论、待复核申诉），版权下架是处置结果不算欠账。</p>
 *
 * <p><b>取不到数就让它抛。</b>这里不 catch、不返回 0：库连不上与"一支曲子
 * 都没有"长得一模一样。SDK 会把异常降级成 stale/degraded，屏风上写"未接通"。</p>
 *
 * @author BlueWhale
 */
@Component
@RequiredArgsConstructor
public class MusicOverviewProvider implements OverviewProvider {

    /** 服务一级前缀，与网关路由一致 */
    private static final String SERVICE = "music";

    /** 城内节点 key（乐坊），与 {@code city-layout.json} 对齐 */
    private static final String NODE_KEY = "yuefang-stage";

    /** 权限码：复用 2D 歌曲台账同一码，不为 3D 单独发一套授权 */
    private static final String SONG_PERMISSION = "sys:music:song:list";

    /** 艺人库权限码：艺人台账入口用 */
    private static final String ARTIST_PERMISSION = "sys:music:artist:list";

    /** 歌曲台账列表接口：屏风页直接取这个路径（网关不剥 /music 前缀） */
    private static final String SONG_PAGE_PATH = "/music/v1/song/page";

    /** 艺人台账列表接口 */
    private static final String ARTIST_PAGE_PATH = "/music/v1/artist/page";

    /** 乐坊统计口径 */
    private final MusicOverviewStatsService statsService;

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
        return "乐坊";
    }

    @Override
    public String subtitle() {
        return "曲库运营";
    }

    @Override
    public String permissionCode() {
        return SONG_PERMISSION;
    }

    @Override
    public OverviewSnapshot build(OverviewContext context) {
        MusicOverviewStats stats = statsService.snapshot(context == null ? null : context.tenantId());
        Overviews.Builder builder = Overviews.builder(SERVICE, NODE_KEY, title())
                .subtitle(subtitle() + " · 曲库三本账")
                .note("口径：待办只数未出结论的一段（待审评论 / 待复核申诉）；"
                        + "版权下架是处置结果不算欠账");
        appendKpis(builder, stats);
        appendStatusChart(builder, stats);
        appendLinks(builder);
        appendAlerts(builder, stats);
        return builder.build();
    }

    /**
     * 四张牌：已上架歌曲、待审评论、待复核申诉、版权待复核。
     *
     * <p>后三张是这一屏的语气所在：听评的人等着放行（待审评论）、上传者
     * 等着翻案（待复核申诉）、曲子签了协议等着过目（版权待复核）——
     * 任何一张不为 0 都该黄。已上架只报数，语气留给告警条。</p>
     *
     * @param builder 组装器
     * @param stats   统计快照
     */
    private static void appendKpis(Overviews.Builder builder, MusicOverviewStats stats) {
        builder.kpi(Overviews.count("published", "已上架歌曲", stats.songPublished(), OverviewTone.FLAT,
                        publishedHint(stats)))
                .kpi(Overviews.count("comment-pending", "待审评论", stats.commentPending(),
                        warnIfPositive(stats.commentPending()), commentHint(stats)))
                .kpi(Overviews.count("appeal-pending", "待复核申诉", stats.appealPending(),
                        warnIfPositive(stats.appealPending()), appealHint(stats)))
                .kpi(Overviews.count("copyright-declared", "版权待复核", stats.copyrightDeclared(),
                        warnIfPositive(stats.copyrightDeclared()), copyrightHint(stats)));
    }

    /**
     * 有账就黄，没账就绿：乐坊三张运营牌共用的语气规则。
     *
     * @param count 计数
     * @return 语气
     */
    private static OverviewTone warnIfPositive(long count) {
        return count > 0 ? OverviewTone.WARN : OverviewTone.GOOD;
    }

    /**
     * 上架牌的提示语：架上成色——草稿还压着几支。
     *
     * @param stats 统计快照
     * @return 提示语
     */
    private static String publishedHint(MusicOverviewStats stats) {
        long total = stats.songDraft() + stats.songPublished() + stats.songOffline();
        if (total <= 0) {
            return "还没有上过曲";
        }
        return "草稿 " + stats.songDraft() + " · 已下架 " + stats.songOffline();
    }

    /**
     * 待审评论牌的提示语：先审后发，积压就是听众等不到自己的留言。
     *
     * @param stats 统计快照
     * @return 提示语
     */
    private static String commentHint(MusicOverviewStats stats) {
        return stats.commentPending() > 0 ? "先审后发，压着没放行" : "评论没有积压";
    }

    /**
     * 待复核申诉牌的提示语：上传者对下架不服，等着给说法。
     *
     * @param stats 统计快照
     * @return 提示语
     */
    private static String appealHint(MusicOverviewStats stats) {
        return stats.appealPending() > 0 ? "对下架不服，等着复核" : "申诉没有积压";
    }

    /**
     * 牌照待复核牌的提示语：签了协议没过目，上架被卡在这一节。
     *
     * @param stats 统计快照
     * @return 提示语
     */
    private static String copyrightHint(MusicOverviewStats stats) {
        return stats.copyrightDeclared() > 0 ? "协议已签，等运营过目" : "版权复核没有积压";
    }

    /**
     * 歌曲发布状态分布柱图：三态全景。
     *
     * <p><b>为什么是分布不是趋势。</b>乐坊的屏风账是存量不是流量：曲库结构
     * （多少草稿压着没上、多少下架占着地方）比"今天传了几支"先要看。
     * 三态码值固定（{@code PublishState}），柱图比折线少一根轴的歧义。</p>
     *
     * @param builder 组装器
     * @param stats   统计快照
     */
    private static void appendStatusChart(Overviews.Builder builder, MusicOverviewStats stats) {
        List<String> categories = List.of("草稿", "已上架", "已下架");
        List<Double> values = List.of((double) stats.songDraft(), (double) stats.songPublished(),
                (double) stats.songOffline());
        builder.chart(Overviews.chart("song-status", "歌曲发布状态分布", "bar", OverviewFormat.COUNT,
                categories, new OverviewSeries("歌曲数", values)));
    }

    /**
     * 入口：歌曲台账 + 艺人台账。
     *
     * <p>歌曲是乐坊主账，排第一；艺人台账给"谁在唱"一个直通入口。
     * 评论与申诉不单摆：2D 里都从歌曲进，屏风上硬凑只会把入口铺满。</p>
     *
     * @param builder 组装器
     */
    private static void appendLinks(Overviews.Builder builder) {
        builder.link(Overviews.link("songs", "歌曲台账", SONG_PAGE_PATH, SONG_PERMISSION));
        builder.link(Overviews.link("artists", "艺人台账", ARTIST_PAGE_PATH, ARTIST_PERMISSION));
    }

    /**
     * 告警条：待审评论、待复核申诉、版权待复核、版权下架存量、空库。
     *
     * <p>这些事"数字本身不会喊"：{@code 5} 条待审评论不会自己变红，
     * {@code 0} 支曲子与"今天没快照"看起来一样。版权下架是处置完了的结果，
     * 只提醒存量不催办；草稿与已下架不进告警——上传者自己的事，运营要盯的
     * 是自己这一侧的欠账。</p>
     *
     * @param builder 组装器
     * @param stats   统计快照
     */
    private static void appendAlerts(Overviews.Builder builder, MusicOverviewStats stats) {
        if (stats.commentPending() > 0) {
            builder.alert(OverviewAlertLevel.WARNING, "有 " + stats.commentPending()
                    + " 条评论待审，先审后发模式下听众等不到留言放行", null);
        }
        if (stats.appealPending() > 0) {
            builder.alert(OverviewAlertLevel.WARNING, "有 " + stats.appealPending()
                    + " 单申诉待复核，上传者对版权下架不服，要人给说法", null);
        }
        if (stats.copyrightDeclared() > 0) {
            builder.alert(OverviewAlertLevel.WARNING, "有 " + stats.copyrightDeclared()
                    + " 支曲子版权待复核，协议已签但没过目，上架被卡在这一节", null);
        }
        if (stats.copyrightTakedown() > 0) {
            builder.alert(OverviewAlertLevel.INFO, "存量 " + stats.copyrightTakedown()
                    + " 支版权下架曲子，处置已出结论，如需翻案走申诉流程", null);
        }
        long total = stats.songDraft() + stats.songPublished() + stats.songOffline();
        if (total <= 0) {
            builder.alert(OverviewAlertLevel.INFO, "乐坊还是空的：曲库一支曲子都没有", null);
        }
    }
}
