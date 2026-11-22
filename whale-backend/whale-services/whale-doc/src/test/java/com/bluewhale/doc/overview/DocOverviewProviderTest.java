package com.bluewhale.doc.overview;

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
 * {@link DocOverviewProvider} 单元测试：四张牌的口径提示、待审批语气、
 * 状态分布柱图、告警分条、缺租户降级。
 *
 * @author BlueWhale
 */
class DocOverviewProviderTest {

    private final DocOverviewStatsService statsService = mock(DocOverviewStatsService.class);

    private final DocOverviewProvider provider = new DocOverviewProvider(statsService);

    /**
     * 构造一份翰林账本。
     *
     * @param spaceActive        正常空间
     * @param spaceArchived      归档空间
     * @param pageTotal          正式页面
     * @param pageDraft          草稿
     * @param pagePublished      已发布
     * @param pagePendingApproval 待审批
     * @param pageArchived       已归档
     * @param trashRoots         回收站棵数
     * @return 统计快照
     */
    private static DocOverviewStats stats(long spaceActive, long spaceArchived,
            long pageTotal, long pageDraft, long pagePublished,
            long pagePendingApproval, long pageArchived, long trashRoots) {
        return new DocOverviewStats(spaceActive, spaceArchived, pageTotal,
                pageDraft, pagePublished, pagePendingApproval, pageArchived, trashRoots);
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
    @DisplayName("元信息与权限码：复用 2D 空间台账同一权限码，不为 3D 单独发授权")
    void shouldExposeMetadata() {
        assertThat(provider.service()).isEqualTo("doc");
        assertThat(provider.nodeKey()).isEqualTo("hanlin-academy");
        assertThat(provider.title()).isEqualTo("翰林");
        assertThat(provider.subtitle()).isEqualTo("文档知识");
        assertThat(provider.permissionCode()).isEqualTo("sys:doc:space:list");
    }

    @Test
    @DisplayName("租户 ID 从上下文透传给统计服务：缺租户由统计服务抛（另一例），provider 不吞")
    void shouldPassTenantIdThrough() {
        when(statsService.snapshot(9001L))
                .thenReturn(stats(2L, 1L, 10L, 4L, 5L, 1L, 0L, 1L));

        provider.build(OverviewContext.of(42L, 9001L, null, null));

        verify(statsService).snapshot(9001L);
    }

    @Test
    @DisplayName("四张牌：各自带口径提示，待审批牌有积压时黄灯")
    void shouldBuildFourKpisWithHints() {
        when(statsService.snapshot(9001L)).thenReturn(stats(12L, 3L, 200L, 70L, 100L, 5L, 25L, 4L));

        Map<String, OverviewKpi> kpis = kpiMap(provider.build(context()));

        assertThat(kpis.get("spaces").value()).isEqualTo(12d);
        assertThat(kpis.get("spaces").hint()).isEqualTo("归档 3 处");
        assertThat(kpis.get("pages").hint()).isEqualTo("已发布 100 · 草稿 70");
        assertThat(kpis.get("pending").value()).isEqualTo(5d);
        assertThat(kpis.get("pending").tone()).isEqualTo(OverviewTone.WARN);
        assertThat(kpis.get("pending").hint()).isEqualTo("发布审批积压，要人去核");
        assertThat(kpis.get("trash").hint()).isEqualTo("4 棵删除子树待处置");
    }

    @Test
    @DisplayName("清空状态：待审批绿灯、回收站空、没建空间时写明白而不是留空")
    void shouldToneClearedState() {
        when(statsService.snapshot(9001L)).thenReturn(stats(0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L));

        Map<String, OverviewKpi> kpis = kpiMap(provider.build(context()));

        assertThat(kpis.get("pending").tone()).isEqualTo(OverviewTone.GOOD);
        assertThat(kpis.get("pending").hint()).isEqualTo("发布审批没有积压");
        assertThat(kpis.get("spaces").hint()).isEqualTo("还没有建文书房");
        assertThat(kpis.get("trash").hint()).isEqualTo("回收站是空的");
    }

    @Test
    @DisplayName("状态分布柱图：草稿/已发布/待审批/已归档按固定顺序，格式是计数")
    void shouldChartStatusDistribution() {
        when(statsService.snapshot(9001L)).thenReturn(stats(12L, 3L, 200L, 70L, 100L, 5L, 25L, 4L));

        OverviewChart chart = provider.build(context()).charts().get(0);

        assertThat(chart.type()).isEqualTo("bar");
        assertThat(chart.format()).isEqualTo(OverviewFormat.COUNT);
        assertThat(chart.categories()).containsExactly("草稿", "已发布", "待审批", "已归档");
        assertThat(chart.series()).hasSize(1);
        assertThat(chart.series().get(0).values()).containsExactly(70d, 100d, 5d, 25d);
    }

    @Test
    @DisplayName("入口：空间台账，权限码复用 2D 空间列表")
    void shouldLinkSpacePage() {
        when(statsService.snapshot(9001L)).thenReturn(stats(12L, 3L, 200L, 70L, 100L, 5L, 25L, 4L));

        List<OverviewLink> links = provider.build(context()).links();

        assertThat(links).singleElement().satisfies(link -> {
            assertThat(link.path()).isEqualTo("/doc/space/page");
            assertThat(link.permission()).isEqualTo("sys:doc:space:list");
        });
    }

    @Test
    @DisplayName("告警：待审批积压是黄灯，回收站与有稿未发布是说明")
    void shouldSeparateWarningsFromInfo() {
        when(statsService.snapshot(9001L)).thenReturn(stats(12L, 3L, 200L, 70L, 0L, 5L, 25L, 4L));

        OverviewSnapshot snapshot = provider.build(context());

        List<String> warnings = snapshot.alerts().stream()
                .filter(alert -> alert.level() == OverviewAlertLevel.WARNING)
                .map(OverviewAlert::text)
                .toList();
        List<String> infos = snapshot.alerts().stream()
                .filter(alert -> alert.level() == OverviewAlertLevel.INFO)
                .map(OverviewAlert::text)
                .toList();
        assertThat(warnings).anyMatch(text -> text.contains("5 篇页面卡在发布审批"));
        assertThat(infos).anyMatch(text -> text.contains("4 棵删除子树"))
                .anyMatch(text -> text.contains("还没有任何一篇发布过"));
    }

    @Test
    @DisplayName("空间全归档：页面还在但不可编辑，单独说明；不误报审批积压")
    void shouldNoteArchivedSpaces() {
        when(statsService.snapshot(9001L)).thenReturn(stats(0L, 5L, 80L, 0L, 60L, 0L, 20L, 0L));

        OverviewSnapshot snapshot = provider.build(context());

        assertThat(snapshot.alerts()).extracting(OverviewAlert::text)
                .anyMatch(text -> text.contains("所有文书房都已归档"));
        assertThat(snapshot.alerts())
                .filteredOn(alert -> alert.level() == OverviewAlertLevel.WARNING)
                .isEmpty();
    }

    @Test
    @DisplayName("空账本：只提示无账可理，不出黄灯")
    void shouldNotBlameEmptyLedgers() {
        when(statsService.snapshot(9001L)).thenReturn(stats(0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L));

        OverviewSnapshot snapshot = provider.build(context());

        assertThat(snapshot.alerts())
                .filteredOn(alert -> alert.level() == OverviewAlertLevel.WARNING)
                .isEmpty();
        assertThat(snapshot.alerts()).extracting(OverviewAlert::text)
                .anyMatch(text -> text.contains("无账可理"));
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
