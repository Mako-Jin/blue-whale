package com.bluewhale.music.overview;

import com.bluewhale.overview.OverviewContext;
import com.bluewhale.overview.model.OverviewAlert;
import com.bluewhale.overview.model.OverviewAlertLevel;
import com.bluewhale.overview.model.OverviewChart;
import com.bluewhale.overview.model.OverviewFormat;
import com.bluewhale.overview.model.OverviewKpi;
import com.bluewhale.overview.model.OverviewLink;
import com.bluewhale.overview.model.OverviewSnapshot;
import com.bluewhale.overview.model.OverviewTone;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link MusicOverviewProvider} 单元测试：四张牌的口径提示、运营语气、
 * 歌曲三态柱图、告警分条、空库不误报。
 *
 * @author BlueWhale
 */
class MusicOverviewProviderTest {

    private final MusicOverviewStatsService statsService = mock(MusicOverviewStatsService.class);

    private final MusicOverviewProvider provider = new MusicOverviewProvider(statsService);

    /**
     * 构造一份乐坊账本。
     *
     * @param songDraft         草稿
     * @param songPublished     已上架
     * @param songOffline       已下架
     * @param copyrightDeclared 版权待复核
     * @param copyrightValid    版权有效
     * @param copyrightTakedown 版权下架
     * @param commentPending    待审评论
     * @param appealPending     待复核申诉
     * @return 统计快照
     */
    private static MusicOverviewStats stats(long songDraft, long songPublished, long songOffline,
            long copyrightDeclared, long copyrightValid, long copyrightTakedown,
            long commentPending, long appealPending) {
        return new MusicOverviewStats(songDraft, songPublished, songOffline,
                copyrightDeclared, copyrightValid, copyrightTakedown,
                commentPending, appealPending);
    }

    /**
     * 按 key 索引指标
     *
     * @param snapshot 快照
     * @return key → 指标
     */
    private static Map<String, OverviewKpi> kpiMap(OverviewSnapshot snapshot) {
        return snapshot.kpis().stream().collect(Collectors.toMap(OverviewKpi::key, kpi -> kpi));
    }

    @Test
    @DisplayName("元信息与权限码：复用 2D 歌曲台账同一权限码，不为 3D 单独发授权")
    void shouldExposeMetadata() {
        assertThat(provider.service()).isEqualTo("music");
        assertThat(provider.nodeKey()).isEqualTo("yuefang-stage");
        assertThat(provider.title()).isEqualTo("乐坊");
        assertThat(provider.subtitle()).isEqualTo("曲库运营");
        assertThat(provider.permissionCode()).isEqualTo("sys:music:song:list");
    }

    @Test
    @DisplayName("租户 ID 从上下文透传给统计服务：缺租户由统计服务抛（另一例），provider 不吞")
    void shouldPassTenantIdThrough() {
        when(statsService.snapshot(9001L)).thenReturn(stats(12L, 40L, 6L, 3L, 33L, 4L, 7L, 2L));

        provider.build(OverviewContext.of(42L, 9001L, null, null));

        verify(statsService).snapshot(9001L);
    }

    @Test
    @DisplayName("四张牌：待审评论/待复核申诉/版权待复核有账就黄，上架牌只报数")
    void shouldBuildFourKpisWithTones() {
        when(statsService.snapshot(9001L)).thenReturn(stats(12L, 40L, 6L, 3L, 33L, 4L, 7L, 2L));

        Map<String, OverviewKpi> kpis = kpiMap(provider.build(context()));

        assertThat(kpis.get("published").value()).isEqualTo(40d);
        assertThat(kpis.get("published").hint()).isEqualTo("草稿 12 · 已下架 6");
        assertThat(kpis.get("comment-pending").tone()).isEqualTo(OverviewTone.WARN);
        assertThat(kpis.get("comment-pending").hint()).isEqualTo("先审后发，压着没放行");
        assertThat(kpis.get("appeal-pending").tone()).isEqualTo(OverviewTone.WARN);
        assertThat(kpis.get("appeal-pending").hint()).isEqualTo("对下架不服，等着复核");
        assertThat(kpis.get("copyright-declared").tone()).isEqualTo(OverviewTone.WARN);
        assertThat(kpis.get("copyright-declared").hint()).isEqualTo("协议已签，等运营过目");
    }

    @Test
    @DisplayName("清空状态：三张运营牌全绿，上架牌说明没上过曲")
    void shouldToneClearedState() {
        when(statsService.snapshot(9001L)).thenReturn(stats(0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L));

        Map<String, OverviewKpi> kpis = kpiMap(provider.build(context()));

        assertThat(kpis.get("comment-pending").tone()).isEqualTo(OverviewTone.GOOD);
        assertThat(kpis.get("comment-pending").hint()).isEqualTo("评论没有积压");
        assertThat(kpis.get("appeal-pending").tone()).isEqualTo(OverviewTone.GOOD);
        assertThat(kpis.get("appeal-pending").hint()).isEqualTo("申诉没有积压");
        assertThat(kpis.get("copyright-declared").tone()).isEqualTo(OverviewTone.GOOD);
        assertThat(kpis.get("published").hint()).isEqualTo("还没有上过曲");
    }

    @Test
    @DisplayName("歌曲三态柱图：固定顺序草稿/已上架/已下架，格式是计数")
    void shouldChartSongStatusDistribution() {
        when(statsService.snapshot(9001L)).thenReturn(stats(12L, 40L, 6L, 3L, 33L, 4L, 7L, 2L));

        OverviewChart chart = provider.build(context()).charts().get(0);

        assertThat(chart.type()).isEqualTo("bar");
        assertThat(chart.format()).isEqualTo(OverviewFormat.COUNT);
        assertThat(chart.categories()).containsExactly("草稿", "已上架", "已下架");
        assertThat(chart.series()).hasSize(1);
        assertThat(chart.series().get(0).values()).containsExactly(12d, 40d, 6d);
    }

    @Test
    @DisplayName("入口：歌曲台账为主账，艺人台账并列，各带自己的权限码")
    void shouldLinkSongAndArtistPages() {
        when(statsService.snapshot(9001L)).thenReturn(stats(12L, 40L, 6L, 3L, 33L, 4L, 7L, 2L));

        List<OverviewLink> links = provider.build(context()).links();

        assertThat(links).extracting(OverviewLink::path)
                .containsExactly("/music/v1/song/page", "/music/v1/artist/page");
        assertThat(links).extracting(OverviewLink::permission)
                .containsExactly("sys:music:song:list", "sys:music:artist:list");
    }

    @Test
    @DisplayName("告警：待审评论/待复核申诉/版权待复核是黄灯；版权下架存量只说明")
    void shouldSeparateWarningsFromInfo() {
        when(statsService.snapshot(9001L)).thenReturn(stats(12L, 40L, 6L, 3L, 33L, 4L, 7L, 2L));

        OverviewSnapshot snapshot = provider.build(context());

        List<String> warnings = snapshot.alerts().stream()
                .filter(alert -> alert.level() == OverviewAlertLevel.WARNING)
                .map(OverviewAlert::text)
                .toList();
        List<String> infos = snapshot.alerts().stream()
                .filter(alert -> alert.level() == OverviewAlertLevel.INFO)
                .map(OverviewAlert::text)
                .toList();
        assertThat(warnings).anyMatch(text -> text.contains("7 条评论待审"))
                .anyMatch(text -> text.contains("2 单申诉待复核"))
                .anyMatch(text -> text.contains("3 支曲子版权待复核"));
        assertThat(infos).anyMatch(text -> text.contains("4 支版权下架"));
    }

    @Test
    @DisplayName("只有版权下架存量（待办全清）：不出黄灯，只提示翻案走申诉")
    void shouldNotBlameTakedownOnly() {
        when(statsService.snapshot(9001L)).thenReturn(stats(0L, 40L, 0L, 0L, 36L, 4L, 0L, 0L));

        OverviewSnapshot snapshot = provider.build(context());

        assertThat(snapshot.alerts())
                .filteredOn(alert -> alert.level() == OverviewAlertLevel.WARNING)
                .isEmpty();
        assertThat(snapshot.alerts()).extracting(OverviewAlert::text)
                .anyMatch(text -> text.contains("4 支版权下架"));
    }

    @Test
    @DisplayName("空库：一支曲子都没有，只说明不出黄灯")
    void shouldNotBlameEmptyLibrary() {
        when(statsService.snapshot(9001L)).thenReturn(stats(0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L));

        OverviewSnapshot snapshot = provider.build(context());

        assertThat(snapshot.alerts())
                .filteredOn(alert -> alert.level() == OverviewAlertLevel.WARNING)
                .isEmpty();
        assertThat(snapshot.alerts()).extracting(OverviewAlert::text)
                .anyMatch(text -> text.contains("乐坊还是空的"));
    }

    /**
     * 上下文：总控聚合时会显式传租户，屏风直连时由 SDK 控制器从请求头取。
     *
     * @return 概览上下文
     */
    private static OverviewContext context() {
        return OverviewContext.of(42L, 9001L, null, null);
    }
}
