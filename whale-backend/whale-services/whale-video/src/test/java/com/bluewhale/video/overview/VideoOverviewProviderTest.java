package com.bluewhale.video.overview;

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

import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link VideoOverviewProvider} 单元测试：四张牌的口径提示、运营语气、
 * 内容四态柱图、告警分条、空库不误报。
 *
 * @author BlueWhale
 */
class VideoOverviewProviderTest {

    private final VideoOverviewStatsService statsService = mock(VideoOverviewStatsService.class);

    private final VideoOverviewProvider provider = new VideoOverviewProvider(statsService);

    /**
     * 构造一份影戏院账本。
     *
     * @param draft          草稿
     * @param pendingReview  待审
     * @param published      已发布
     * @param offline        已下线
     * @param commentPending 待审评论
     * @param takedown       版权下架
     * @param licenseExpiring 授权将到期
     * @param totalPlay      累计播放
     * @return 统计快照
     */
    private static VideoOverviewStats stats(long draft, long pendingReview, long published, long offline,
            long commentPending, long takedown, long licenseExpiring, long totalPlay) {
        return new VideoOverviewStats(draft, pendingReview, published, offline,
                commentPending, takedown, licenseExpiring, totalPlay);
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
    @DisplayName("元信息与权限码：复用 2D 内容台账同一权限码，不为 3D 单独发授权")
    void shouldExposeMetadata() {
        assertThat(provider.service()).isEqualTo("video");
        assertThat(provider.nodeKey()).isEqualTo("yingxi-stage");
        assertThat(provider.title()).isEqualTo("影戏院");
        assertThat(provider.subtitle()).isEqualTo("内容运营");
        assertThat(provider.permissionCode()).isEqualTo("sys:video:content:list");
    }

    @Test
    @DisplayName("租户 ID 从上下文透传给统计服务")
    void shouldPassTenantIdThrough() {
        when(statsService.snapshot(9001L)).thenReturn(stats(4L, 2L, 30L, 3L, 6L, 1L, 2L, 128000L));

        OverviewSnapshot snapshot = provider.build(OverviewContext.of(7L, 9001L, java.util.Set.of()));

        assertThat(snapshot.kpis()).hasSize(4);
    }

    @Test
    @DisplayName("上下文为空时传 null 给统计服务，由统计服务抛（缺租户不猜）")
    void shouldPassNullTenantWhenContextMissing() {
        when(statsService.snapshot(null)).thenThrow(new IllegalArgumentException("tenantId is required"));

        assertThatThrownBy(() -> provider.build(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("四张牌：已发布灰、三张运营牌有账就黄")
    void shouldToneKpisByBusinessPressure() {
        when(statsService.snapshot(1L)).thenReturn(stats(4L, 2L, 30L, 3L, 6L, 1L, 2L, 128000L));

        Map<String, OverviewKpi> kpis = kpiMap(provider.build(OverviewContext.of(7L, 1L, java.util.Set.of())));

        assertThat(kpis).containsOnlyKeys("published", "pending-review", "comment-pending", "license-expiring");
        assertThat(kpis.get("published").tone()).isEqualTo(OverviewTone.FLAT);
        assertThat(kpis.get("pending-review").tone()).isEqualTo(OverviewTone.WARN);
        assertThat(kpis.get("comment-pending").tone()).isEqualTo(OverviewTone.WARN);
        assertThat(kpis.get("license-expiring").tone()).isEqualTo(OverviewTone.WARN);
    }

    @Test
    @DisplayName("运营牌为 0 时转绿灯：没有欠账就不喊事")
    void shouldTurnGreenWhenNoPendingWork() {
        when(statsService.snapshot(1L)).thenReturn(stats(0L, 0L, 12L, 0L, 0L, 0L, 0L, 500L));

        Map<String, OverviewKpi> kpis = kpiMap(provider.build(OverviewContext.of(7L, 1L, java.util.Set.of())));

        assertThat(kpis.get("pending-review").tone()).isEqualTo(OverviewTone.GOOD);
        assertThat(kpis.get("comment-pending").tone()).isEqualTo(OverviewTone.GOOD);
        assertThat(kpis.get("license-expiring").tone()).isEqualTo(OverviewTone.GOOD);
    }

    @Test
    @DisplayName("已发布牌写出架上成色：草稿 / 已下线 / 累计播放")
    void shouldDescribePublishedHint() {
        when(statsService.snapshot(1L)).thenReturn(stats(4L, 2L, 30L, 3L, 6L, 1L, 2L, 128000L));

        Map<String, OverviewKpi> kpis = kpiMap(provider.build(OverviewContext.of(7L, 1L, java.util.Set.of())));

        assertThat(kpis.get("published").hint()).contains("草稿 4", "已下线 3", "累计播放 128000");
    }

    @Test
    @DisplayName("空库时已发布牌说人话：还没有一部片子")
    void shouldSayEmptyForFreshTenant() {
        when(statsService.snapshot(1L)).thenReturn(stats(0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L));

        Map<String, OverviewKpi> kpis = kpiMap(provider.build(OverviewContext.of(7L, 1L, java.util.Set.of())));

        assertThat(kpis.get("published").hint()).isEqualTo("还没有一部片子");
    }

    @Test
    @DisplayName("柱图是内容四态分布：草稿 / 待审 / 已发布 / 已下线")
    void shouldChartFourPublishStates() {
        when(statsService.snapshot(1L)).thenReturn(stats(4L, 2L, 30L, 3L, 6L, 1L, 2L, 128000L));

        OverviewSnapshot snapshot = provider.build(OverviewContext.of(7L, 1L, java.util.Set.of()));

        assertThat(snapshot.charts()).hasSize(1);
        OverviewChart chart = snapshot.charts().get(0);
        assertThat(chart.key()).isEqualTo("content-status");
        assertThat(chart.type()).isEqualTo("bar");
        assertThat(chart.format()).isEqualTo(OverviewFormat.COUNT);
        assertThat(chart.categories()).containsExactly("草稿", "待审", "已发布", "已下线");
        assertThat(chart.series().get(0).values()).containsExactly(4.0, 2.0, 30.0, 3.0);
    }

    @Test
    @DisplayName("两个入口：内容台账 + 电影台账，路径带服务前缀")
    void shouldExposeTwoLinks() {
        when(statsService.snapshot(1L)).thenReturn(stats(0L, 0L, 10L, 0L, 0L, 0L, 0L, 10L));

        OverviewSnapshot snapshot = provider.build(OverviewContext.of(7L, 1L, java.util.Set.of()));

        assertThat(snapshot.links()).extracting(OverviewLink::path)
                .containsExactly("/video/v1/admin/content/page", "/video/v1/admin/movies/page");
        assertThat(snapshot.links()).extracting(OverviewLink::permission)
                .containsExactly("sys:video:content:list", "sys:video:movie:manage");
    }

    @Test
    @DisplayName("告警分条：三黄催办 + 一条版权下架存量说明")
    void shouldSplitAlerts() {
        when(statsService.snapshot(1L)).thenReturn(stats(4L, 2L, 30L, 3L, 6L, 1L, 2L, 128000L));

        OverviewSnapshot snapshot = provider.build(OverviewContext.of(7L, 1L, java.util.Set.of()));

        assertThat(snapshot.alerts()).extracting(OverviewAlert::level).containsExactly(
                OverviewAlertLevel.WARNING, OverviewAlertLevel.WARNING,
                OverviewAlertLevel.WARNING, OverviewAlertLevel.INFO);
        assertThat(snapshot.alerts().get(0).text()).contains("2 部内容待审");
        assertThat(snapshot.alerts().get(1).text()).contains("6 条评论待审");
        assertThat(snapshot.alerts().get(2).text()).contains("2 部电影授权 7 天内到期");
        assertThat(snapshot.alerts().get(3).text()).contains("1 部版权下架内容");
    }

    @Test
    @DisplayName("空库只报一条 INFO：不把 0 条待审当成催办")
    void shouldAlertEmptyLibraryOnly() {
        when(statsService.snapshot(1L)).thenReturn(stats(0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L));

        OverviewSnapshot snapshot = provider.build(OverviewContext.of(7L, 1L, java.util.Set.of()));

        assertThat(snapshot.alerts()).hasSize(1);
        assertThat(snapshot.alerts().get(0).level()).isEqualTo(OverviewAlertLevel.INFO);
        assertThat(snapshot.alerts().get(0).text()).contains("影戏院还是空的");
    }
}
