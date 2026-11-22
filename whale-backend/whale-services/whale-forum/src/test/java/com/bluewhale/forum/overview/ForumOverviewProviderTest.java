package com.bluewhale.forum.overview;

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
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link ForumOverviewProvider} 单元测试：四张牌的口径提示、治理语气、
 * 帖子五态柱图、告警分条、空库不误报。
 *
 * @author BlueWhale
 */
class ForumOverviewProviderTest {

    private final ForumOverviewStatsService statsService = mock(ForumOverviewStatsService.class);

    private final ForumOverviewProvider provider = new ForumOverviewProvider(statsService);

    /**
     * 构造一份清议堂账本。
     *
     * @param pending   待审帖
     * @param published 在议帖
     * @param solved    已解决
     * @param closed    已关闭
     * @param rejected  已驳回
     * @param pinned    悬榜
     * @param essence   题金榜
     * @param reports   待受理举报
     * @param appeals   待理申诉
     * @param bans      封禁中用户
     * @return 统计快照
     */
    private static ForumOverviewStats stats(long pending, long published, long solved, long closed, long rejected,
            long pinned, long essence, long reports, long appeals, long bans) {
        return new ForumOverviewStats(pending, published, solved, closed, rejected,
                pinned, essence, reports, appeals, bans);
    }

    /**
     * 一屏"平安无事"的账本：没有待办、没有封禁，但有 12 条在议帖。
     *
     * @return 统计快照
     */
    private static ForumOverviewStats quiet() {
        return stats(0L, 12L, 3L, 2L, 1L, 1L, 2L, 0L, 0L, 0L);
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
    @DisplayName("元信息与权限码：本屏含治理账，走管理端入口码")
    void shouldExposeMetadata() {
        assertThat(provider.service()).isEqualTo("forum");
        assertThat(provider.nodeKey()).isEqualTo("qingyi-stage");
        assertThat(provider.title()).isEqualTo("清议堂");
        assertThat(provider.subtitle()).isEqualTo("论坛议事");
        assertThat(provider.permissionCode()).isEqualTo("sys:forum:manage:list");
    }

    @Test
    @DisplayName("租户 ID 从上下文透传给统计服务")
    void shouldPassTenantIdThrough() {
        when(statsService.snapshot(9001L)).thenReturn(quiet());

        OverviewSnapshot snapshot = provider.build(OverviewContext.of(7L, 9001L, Set.of()));

        assertThat(snapshot.service()).isEqualTo("forum");
        assertThat(snapshot.nodeKey()).isEqualTo("qingyi-stage");
        assertThat(snapshot.subtitle()).contains("议事与秩序");
        assertThat(snapshot.notes()).anySatisfy(note -> assertThat(note)
                .contains("未出结论")
                .contains("封禁是处置结果"));
    }

    @Test
    @DisplayName("上下文为空时不编租户：统计服务收到 null 并自行抛（宁可降级不串数）")
    void shouldPassNullTenantWhenContextMissing() {
        when(statsService.snapshot(null)).thenThrow(new IllegalArgumentException("tenantId is required"));

        assertThatThrownBy(() -> provider.build(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("四张牌：在议帖灰灯只报数，三张治理牌有账就黄")
    void shouldToneKpisByGovernanceBacklog() {
        when(statsService.snapshot(9001L)).thenReturn(stats(0L, 12L, 3L, 2L, 1L, 1L, 2L, 0L, 0L, 0L));

        OverviewSnapshot snapshot = provider.build(OverviewContext.of(7L, 9001L, Set.of()));
        Map<String, OverviewKpi> kpis = kpiMap(snapshot);

        assertThat(kpis).containsOnlyKeys("published", "pending", "reports", "appeals");
        assertThat(kpis.get("published").tone()).isEqualTo(OverviewTone.FLAT);
        assertThat(kpis.get("published").value()).isEqualTo(12.0);
        assertThat(kpis.get("pending").tone()).isEqualTo(OverviewTone.GOOD);
        assertThat(kpis.get("reports").tone()).isEqualTo(OverviewTone.GOOD);
        assertThat(kpis.get("appeals").tone()).isEqualTo(OverviewTone.GOOD);
    }

    @Test
    @DisplayName("三张治理牌任一不为 0 就转黄：待审 2 / 举报 4 / 申诉 1")
    void shouldWarnWhenGovernanceBacklogPositive() {
        when(statsService.snapshot(9001L)).thenReturn(stats(2L, 12L, 3L, 2L, 1L, 1L, 2L, 4L, 1L, 3L));

        Map<String, OverviewKpi> kpis = kpiMap(provider.build(OverviewContext.of(7L, 9001L, Set.of())));

        assertThat(kpis.get("pending").tone()).isEqualTo(OverviewTone.WARN);
        assertThat(kpis.get("pending").hint()).contains("等着放行");
        assertThat(kpis.get("reports").tone()).isEqualTo(OverviewTone.WARN);
        assertThat(kpis.get("reports").hint()).contains("SLA 24h");
        assertThat(kpis.get("appeals").tone()).isEqualTo(OverviewTone.WARN);
        assertThat(kpis.get("appeals").hint()).contains("等复核");
    }

    @Test
    @DisplayName("在议帖提示语写议事成色：悬榜 + 题金榜 + 已解决")
    void shouldHintPublishedComposition() {
        when(statsService.snapshot(9001L)).thenReturn(stats(0L, 12L, 3L, 2L, 1L, 1L, 2L, 0L, 0L, 0L));

        Map<String, OverviewKpi> kpis = kpiMap(provider.build(OverviewContext.of(7L, 9001L, Set.of())));

        assertThat(kpis.get("published").hint())
                .isEqualTo("悬榜 1 · 题金榜 2 · 已解决 3");
    }

    @Test
    @DisplayName("一条帖子都没有时四张牌都给「还没有」的人话，而不只是一个 0")
    void shouldSpeakPlainlyWhenEmpty() {
        when(statsService.snapshot(9001L)).thenReturn(stats(0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L));

        OverviewSnapshot snapshot = provider.build(OverviewContext.of(7L, 9001L, Set.of()));
        Map<String, OverviewKpi> kpis = kpiMap(snapshot);

        assertThat(kpis.get("published").hint()).isEqualTo("还没有人开帖");
        assertThat(kpis.get("pending").hint()).isEqualTo("没有待审帖子");
        assertThat(kpis.get("reports").hint()).isEqualTo("没有待受理举报");
        assertThat(kpis.get("appeals").hint()).isEqualTo("没有待理申诉");
    }

    @Test
    @DisplayName("柱子是帖子五态分布：待审/在议/已解决/已关闭/已驳回")
    void shouldRenderThreadStatusBarChart() {
        when(statsService.snapshot(9001L)).thenReturn(stats(2L, 12L, 3L, 2L, 1L, 1L, 2L, 4L, 1L, 3L));

        OverviewSnapshot snapshot = provider.build(OverviewContext.of(7L, 9001L, Set.of()));
        OverviewChart chart = snapshot.charts().get(0);

        assertThat(snapshot.charts()).hasSize(1);
        assertThat(chart.key()).isEqualTo("thread-status");
        assertThat(chart.type()).isEqualTo("bar");
        assertThat(chart.format()).isEqualTo(OverviewFormat.COUNT);
        assertThat(chart.categories()).containsExactly("待审", "在议", "已解决", "已关闭", "已驳回");
        assertThat(chart.series().get(0).values()).containsExactly(2.0, 12.0, 3.0, 2.0, 1.0);
    }

    @Test
    @DisplayName("三个入口：帖子列表（公开账）+ 审核队列 + 举报工单，各带自己的权限码")
    void shouldExposeThreeLinks() {
        when(statsService.snapshot(9001L)).thenReturn(quiet());

        OverviewSnapshot snapshot = provider.build(OverviewContext.of(7L, 9001L, Set.of()));

        assertThat(snapshot.links()).extracting(OverviewLink::key)
                .containsExactly("threads", "audit", "reports");
        assertThat(snapshot.links().get(0).permission()).isEqualTo("sys:forum:browse");
        assertThat(snapshot.links().get(1).permission()).isEqualTo("sys:forum:manage:audit");
        assertThat(snapshot.links().get(2).permission()).isEqualTo("sys:forum:manage:dispose");
        assertThat(snapshot.links().get(0).path()).isEqualTo("/forum/v1/thread/page");
        assertThat(snapshot.links().get(1).path()).isEqualTo("/forum/v1/admin/audit/page");
        assertThat(snapshot.links().get(2).path()).isEqualTo("/forum/v1/admin/report/page");
    }

    @Test
    @DisplayName("三笔治理待办各出一条告警，封禁只报存量（INFO 不催办）")
    void shouldSplitGovernanceAlerts() {
        when(statsService.snapshot(9001L)).thenReturn(stats(2L, 12L, 3L, 2L, 1L, 1L, 2L, 4L, 1L, 3L));

        OverviewSnapshot snapshot = provider.build(OverviewContext.of(7L, 9001L, Set.of()));
        List<OverviewAlert> alerts = snapshot.alerts();

        assertThat(alerts).hasSize(4);
        assertThat(alerts).filteredOn(alert -> alert.level() == OverviewAlertLevel.WARNING)
                .extracting(OverviewAlert::text)
                .anySatisfy(text -> assertThat(text).contains("2 条帖子待审"))
                .anySatisfy(text -> assertThat(text).contains("4 张举报待受理"))
                .anySatisfy(text -> assertThat(text).contains("1 条申诉待理"));
        OverviewAlert banAlert = alerts.stream()
                .filter(alert -> alert.text().contains("禁言或封号"))
                .findFirst()
                .orElseThrow();
        assertThat(banAlert.level()).isEqualTo(OverviewAlertLevel.INFO);
        assertThat(banAlert.text()).contains("3 名用户");
    }

    @Test
    @DisplayName("空库只留一条 INFO：不拿 0 去喊三声")
    void shouldEmitSingleInfoWhenEmpty() {
        when(statsService.snapshot(9001L)).thenReturn(stats(0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L));

        OverviewSnapshot snapshot = provider.build(OverviewContext.of(7L, 9001L, Set.of()));

        assertThat(snapshot.alerts()).hasSize(1);
        assertThat(snapshot.alerts().get(0).level()).isEqualTo(OverviewAlertLevel.INFO);
        assertThat(snapshot.alerts().get(0).text()).contains("还空着");
    }
}
