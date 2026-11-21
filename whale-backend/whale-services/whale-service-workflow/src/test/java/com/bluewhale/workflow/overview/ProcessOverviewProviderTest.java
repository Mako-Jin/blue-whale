package com.bluewhale.workflow.overview;

import com.bluewhale.overview.OverviewContext;
import com.bluewhale.overview.model.OverviewAlertLevel;
import com.bluewhale.overview.model.OverviewKpi;
import com.bluewhale.overview.model.OverviewSnapshot;
import com.bluewhale.workflow.service.ProcessOverviewStatQuery;
import com.bluewhale.workflow.service.ProcessOverviewStatQuery.Backlog;
import com.bluewhale.workflow.service.ProcessOverviewStatQuery.DayFlow;
import com.bluewhale.workflow.service.ProcessOverviewStatQuery.InstanceStat;
import com.bluewhale.workflow.service.ProcessOverviewStatQuery.OverdueStat;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

/**
 * {@link ProcessOverviewProvider} 单元测试（T5.6）。
 *
 * <p>钉住六件容易悄悄漂移的事：① KPI 顺序与口径（屏风按位置排版，换序没人报错）；
 * ② 趋势两条序列都必须按 7 天补 0（缺一天横轴就会错位一天）；
 * ③ 当前无待办时柱图不能给空类目（空类目在屏风上画成一条白线，看着像加载失败）；
 * ④ 告警两条判据——"链断了"与"人在拖"根因不同，各自成立且可并存；
 * ⑤ 超时时长向上取整（59 分钟不能显示成 0 小时）；⑥ 无租户上下文时四个数全 0
 * 但必须留口径说明。</p>
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
class ProcessOverviewProviderTest {

    /** 测试租户 */
    private static final long TENANT_ID = 7L;

    @Mock
    private ProcessOverviewStatQuery statQuery;

    @InjectMocks
    private ProcessOverviewProvider provider;

    /**
     * 正常形态：四张 KPI（顺序固定）、两张图（趋势两条序列）、一个入口、无告警
     */
    @Test
    @DisplayName("正常：四张 KPI + 两张图 + 实例入口，无超时无卡住不告警")
    void shouldBuildKpisChartsAndLink() {
        stubBaseline(new InstanceStat(12L, 30L, 8L), 5L, new OverdueStat(0L, 0L), 0L,
                List.of(), List.of(new Backlog("部门负责人审批", 5L)));

        OverviewSnapshot snapshot = provider.build(contextWith(Set.of("sys:workflow:instance:list")));

        assertEquals("workflow", snapshot.service());
        assertEquals("zhengshi-hall", snapshot.nodeKey());
        assertEquals(List.of("running", "finished", "pending", "stuck"),
                snapshot.kpis().stream().map(OverviewKpi::key).toList());
        assertEquals(12d, kpi(snapshot, "running"));
        assertEquals(8d, kpi(snapshot, "finished"));
        assertEquals(5d, kpi(snapshot, "pending"));
        assertEquals(0d, kpi(snapshot, "stuck"));
        assertEquals(2, snapshot.charts().size());
        assertEquals(2, snapshot.charts().get(0).series().size());
        assertEquals(1, snapshot.links().size());
        assertEquals("/workflow/v1/instance/page", snapshot.links().get(0).path());
        assertTrue(snapshot.alerts().isEmpty());
    }

    /**
     * 在途 KPI 的副标：发起量放在"运行中"下面，省得看的人自己去两个数里相减
     */
    @Test
    @DisplayName("运行中 KPI：副标写今日发起数")
    void shouldShowStartedHint() {
        stubBaseline(new InstanceStat(9L, 21L, 3L), 0L, new OverdueStat(0L, 0L), 0L,
                List.of(), List.of());

        OverviewSnapshot snapshot = provider.build(contextWith(Set.of("sys:workflow:instance:list")));

        String hint = snapshot.kpis().stream().filter(item -> "running".equals(item.key()))
                .findFirst().orElseThrow().hint();
        assertEquals("今日发起 21", hint);
    }

    /**
     * 趋势序列：两条序列都近 7 天、缺日补 0、今天在最后一位
     */
    @Test
    @DisplayName("趋势：发起与办结两条序列各 7 天，缺日补 0")
    void shouldZeroFillMissingDays() {
        LocalDate today = LocalDate.now();
        stubBaseline(new InstanceStat(2L, 4L, 3L), 1L, new OverdueStat(0L, 0L), 0L,
                List.of(new DayFlow(today, 4L, 3L), new DayFlow(today.minusDays(6), 2L, 1L)),
                List.of());

        OverviewSnapshot snapshot = provider.build(contextWith(Set.of("sys:workflow:instance:list")));

        assertEquals(List.of(2d, 0d, 0d, 0d, 0d, 0d, 4d),
                snapshot.charts().get(0).series().get(0).values());
        assertEquals(List.of(1d, 0d, 0d, 0d, 0d, 0d, 3d),
                snapshot.charts().get(0).series().get(1).values());
        assertEquals(7, snapshot.charts().get(0).categories().size());
    }

    /**
     * 当前无待办：柱图给一个占位类目与 0 值
     */
    @Test
    @DisplayName("无待办：积压柱图保留一个占位类目")
    void shouldKeepPlaceholderWhenNoBacklog() {
        stubBaseline(new InstanceStat(0L, 0L, 0L), 0L, new OverdueStat(0L, 0L), 0L,
                List.of(), List.of());

        OverviewSnapshot snapshot = provider.build(contextWith(Set.of("sys:workflow:instance:list")));

        assertEquals(List.of("当前无待办"), snapshot.charts().get(1).categories());
        assertEquals(List.of(0d), snapshot.charts().get(1).series().get(0).values());
    }

    /**
     * 积压类目：超长节点名截断，避免标签列被挤没
     */
    @Test
    @DisplayName("积压 Top：超长节点名截断到 8 字")
    void shouldTruncateLongNodeName() {
        stubBaseline(new InstanceStat(3L, 3L, 0L), 6L, new OverdueStat(0L, 0L), 0L, List.of(),
                List.of(new Backlog("集团财务共享中心复核节点", 6L)));

        OverviewSnapshot snapshot = provider.build(contextWith(Set.of("sys:workflow:instance:list")));

        assertEquals(List.of("集团财务共享中心…"), snapshot.charts().get(1).categories());
        assertEquals(List.of(6d), snapshot.charts().get(1).series().get(0).values());
    }

    /**
     * 链断了：运行中但无待办 → CRITICAL
     */
    @Test
    @DisplayName("告警：运行中且无待办的实例 → CRITICAL（审批链断了）")
    void shouldAlertOnStuckInstances() {
        stubBaseline(new InstanceStat(3L, 1L, 0L), 0L, new OverdueStat(0L, 0L), 3L,
                List.of(), List.of());

        OverviewSnapshot snapshot = provider.build(contextWith(Set.of("sys:workflow:instance:list")));

        assertEquals(1, snapshot.alerts().size());
        assertEquals(OverviewAlertLevel.CRITICAL, snapshot.alerts().get(0).level());
        assertTrue(snapshot.alerts().get(0).text().contains("审批链"), snapshot.alerts().get(0).text());
        assertTrue(snapshot.alerts().get(0).text().contains("3"), snapshot.alerts().get(0).text());
    }

    /**
     * 超时到量：10 个（阈值含）→ CRITICAL
     */
    @Test
    @DisplayName("告警：待办超时 10 个 → CRITICAL")
    void shouldAlertOnOverdueCount() {
        stubBaseline(new InstanceStat(10L, 1L, 0L), 12L, new OverdueStat(10L, 120L), 0L,
                List.of(), List.of());

        OverviewSnapshot snapshot = provider.build(contextWith(Set.of("sys:workflow:instance:list")));

        assertEquals(1, snapshot.alerts().size());
        assertEquals(OverviewAlertLevel.CRITICAL, snapshot.alerts().get(0).level());
        assertTrue(snapshot.alerts().get(0).text().contains("10"), snapshot.alerts().get(0).text());
    }

    /**
     * 少量超时：1 个 → WARNING（一个人今天没空，不该升级成事故）
     */
    @Test
    @DisplayName("告警：只有 1 个待办超时 → WARNING")
    void shouldWarnOnSingleOverdue() {
        stubBaseline(new InstanceStat(2L, 2L, 0L), 1L, new OverdueStat(1L, 30L), 0L,
                List.of(), List.of());

        OverviewSnapshot snapshot = provider.build(contextWith(Set.of("sys:workflow:instance:list")));

        assertEquals(1, snapshot.alerts().size());
        assertEquals(OverviewAlertLevel.WARNING, snapshot.alerts().get(0).level());
        assertTrue(snapshot.alerts().get(0).text().contains("1 小时"),
                snapshot.alerts().get(0).text());
    }

    /**
     * 时长向上取整：90 分钟 → 2 小时（向下取整会说成 1 小时，59 分钟会说成 0 小时）
     */
    @Test
    @DisplayName("超时时长向上取整：90 分钟 → 2 小时")
    void shouldRoundOverdueMinutesUp() {
        stubBaseline(new InstanceStat(2L, 2L, 0L), 1L, new OverdueStat(1L, 90L), 0L,
                List.of(), List.of());

        OverviewSnapshot snapshot = provider.build(contextWith(Set.of("sys:workflow:instance:list")));

        assertTrue(snapshot.alerts().get(0).text().contains("2 小时"), snapshot.alerts().get(0).text());
    }

    /**
     * 两条告警可并存：链断了同时还有超时（前者是缺陷，后者是人在拖）
     */
    @Test
    @DisplayName("告警可并存：卡住 + 超时各一条")
    void shouldKeepBothAlerts() {
        stubBaseline(new InstanceStat(5L, 5L, 0L), 12L, new OverdueStat(12L, 60L), 2L,
                List.of(), List.of());

        OverviewSnapshot snapshot = provider.build(contextWith(Set.of("sys:workflow:instance:list")));

        assertEquals(2, snapshot.alerts().size());
        assertTrue(snapshot.alerts().stream().anyMatch(alert -> alert.text().contains("审批链")));
        assertTrue(snapshot.alerts().stream().anyMatch(alert -> alert.text().contains("待办超时")));
    }

    /**
     * 无租户上下文：四个数都是 0，但必须留下"为什么是 0"的说明
     */
    @Test
    @DisplayName("无租户上下文：KPI 全 0 并留下口径说明")
    void shouldDegradeWithoutTenant() {
        OverviewSnapshot snapshot = provider.build(OverviewContext.of(1L, null, Set.of()));

        assertEquals(0d, kpi(snapshot, "running"));
        assertEquals(0d, kpi(snapshot, "pending"));
        assertEquals(0d, kpi(snapshot, "stuck"));
        assertTrue(snapshot.notes().stream().anyMatch(note -> note.contains("未识别租户")));
    }

    /**
     * 无权限码：入口不下发（下发再让前端隐藏等于把入口清单白送）
     */
    @Test
    @DisplayName("无实例权限：不下发入口")
    void shouldHideLinkWithoutPermission() {
        stubBaseline(new InstanceStat(2L, 2L, 0L), 0L, new OverdueStat(0L, 0L), 0L,
                List.of(), List.of());

        OverviewSnapshot snapshot = provider.build(contextWith(Set.of("sys:user:list")));

        assertFalse(snapshot.links().stream().anyMatch(link -> "instances".equals(link.key())));
    }

    /**
     * 造上下文
     *
     * @param permissions 权限码
     * @return 上下文
     */
    private OverviewContext contextWith(Set<String> permissions) {
        return OverviewContext.of(1L, TENANT_ID, permissions);
    }

    /**
     * 桩：六个统计口全部按参数返回
     *
     * @param instance 实例统计
     * @param pending  待办总数
     * @param overdue  超时统计
     * @param stuck    卡住实例数
     * @param trend    按天趋势
     * @param backlog  积压 Top
     */
    private void stubBaseline(InstanceStat instance, long pending, OverdueStat overdue, long stuck,
                              List<DayFlow> trend, List<Backlog> backlog) {
        when(statQuery.instanceStat(anyLong(), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(instance);
        when(statQuery.pendingCount(anyLong())).thenReturn(pending);
        when(statQuery.overdue(anyLong(), any(LocalDateTime.class))).thenReturn(overdue);
        when(statQuery.stuckInstances(anyLong())).thenReturn(stuck);
        when(statQuery.dailyFlow(anyLong(), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(trend);
        when(statQuery.pendingBacklog(anyLong(), anyInt())).thenReturn(backlog);
    }

    /**
     * 取指标值
     *
     * @param snapshot 快照
     * @param key      指标键
     * @return 数值
     */
    private double kpi(OverviewSnapshot snapshot, String key) {
        return snapshot.kpis().stream().filter(item -> key.equals(item.key()))
                .findFirst().orElseThrow().value();
    }
}
