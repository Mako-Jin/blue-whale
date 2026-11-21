package com.bluewhale.form.overview;

import com.bluewhale.form.service.FormOverviewStatQuery;
import com.bluewhale.form.service.FormOverviewStatQuery.DaySubmits;
import com.bluewhale.form.service.FormOverviewStatQuery.DefinitionCount;
import com.bluewhale.form.service.FormOverviewStatQuery.FormTop;
import com.bluewhale.overview.OverviewContext;
import com.bluewhale.overview.model.OverviewAlertLevel;
import com.bluewhale.overview.model.OverviewKpi;
import com.bluewhale.overview.model.OverviewSnapshot;
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
 * {@link FormOverviewProvider} 单元测试（T5.7）。
 *
 * <p>钉住六件容易悄悄漂移的事：① KPI 顺序与口径（屏风按位置排版，换序没人报错）；
 * ② "近 7 日 N 条"必须与趋势图那 7 根柱子是同一份数据（两处各查一次迟早对不上）；
 * ③ 趋势缺日补 0、无提交时柱图留占位类目（空类目在屏风上画成一条白线）；
 * ④ 待审批的副标要区分"超时"与"都在窗口内"——两者处置动作完全不同；
 * ⑤ 两条告警根因不同、可以并存，且差异是 CRITICAL；⑥ 无租户 / 无权限的降级形态。</p>
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
class FormOverviewProviderTest {

    /** 测试租户 */
    private static final long TENANT_ID = 7L;

    @Mock
    private FormOverviewStatQuery statQuery;

    @InjectMocks
    private FormOverviewProvider provider;

    /**
     * 正常形态：四张 KPI（顺序固定）、两张图、一个入口、无告警
     */
    @Test
    @DisplayName("正常：四张 KPI + 两张图 + 表单入口，无差异无超时不告警")
    void shouldBuildKpisChartsAndLink() {
        stubBaseline(new DefinitionCount(12L, 9L), 5L, 3L, 0L, 0L,
                List.of(new DaySubmits(LocalDate.now(), 5L)),
                List.of(new FormTop("leave", "请假单", 5L)));

        OverviewSnapshot snapshot = provider.build(contextWith(Set.of("sys:form:list")));

        assertEquals("form", snapshot.service());
        assertEquals("kaogong-hall", snapshot.nodeKey());
        assertEquals(List.of("forms", "submits", "pending", "diff"),
                snapshot.kpis().stream().map(OverviewKpi::key).toList());
        assertEquals(9d, kpi(snapshot, "forms"));
        assertEquals(5d, kpi(snapshot, "submits"));
        assertEquals(3d, kpi(snapshot, "pending"));
        assertEquals(0d, kpi(snapshot, "diff"));
        assertEquals(2, snapshot.charts().size());
        assertEquals(1, snapshot.links().size());
        assertEquals("forms", snapshot.links().get(0).key());
        assertEquals("/form/v1/definition/page", snapshot.links().get(0).path());
        assertTrue(snapshot.alerts().isEmpty());
    }

    /**
     * 启用表单 KPI：副标必须报出停用数，否则考核基数会一直虚高
     */
    @Test
    @DisplayName("启用表单：副标写「共 N 个定义 · 停用 M」")
    void shouldShowDefinitionHint() {
        stubBaseline(new DefinitionCount(12L, 9L), 0L, 0L, 0L, 0L, List.of(), List.of());

        OverviewSnapshot snapshot = provider.build(contextWith(Set.of("sys:form:list")));

        assertEquals("共 12 个定义 · 停用 3", hintOf(snapshot, "forms"));
    }

    /**
     * 今日提交 KPI：副标的"近 7 日"必须等于趋势序列之和
     */
    @Test
    @DisplayName("今日提交：副标「近 7 日 N 条」与趋势序列同源")
    void shouldReuseTrendForWeeklyHint() {
        LocalDate today = LocalDate.now();
        stubBaseline(new DefinitionCount(3L, 3L), 2L, 0L, 0L, 0L,
                List.of(new DaySubmits(today, 2L), new DaySubmits(today.minusDays(3), 6L)),
                List.of());

        OverviewSnapshot snapshot = provider.build(contextWith(Set.of("sys:form:list")));

        assertEquals("近 7 日 8 条", hintOf(snapshot, "submits"));
        List<Double> values = snapshot.charts().get(0).series().get(0).values();
        assertEquals(8d, values.stream().mapToDouble(Double::doubleValue).sum());
    }

    /**
     * 趋势序列：近 7 天、缺日补 0、今天在最后一位
     */
    @Test
    @DisplayName("趋势：近 7 天序列缺日补 0，今天在最后一位")
    void shouldZeroFillMissingDays() {
        LocalDate today = LocalDate.now();
        stubBaseline(new DefinitionCount(1L, 1L), 9L, 0L, 0L, 0L,
                List.of(new DaySubmits(today, 9L), new DaySubmits(today.minusDays(6), 4L)),
                List.of());

        OverviewSnapshot snapshot = provider.build(contextWith(Set.of("sys:form:list")));

        List<Double> values = snapshot.charts().get(0).series().get(0).values();
        assertEquals(7, values.size());
        assertEquals(List.of(4d, 0d, 0d, 0d, 0d, 0d, 9d), values);
    }

    /**
     * 近 7 日无提交：柱图给一个占位类目与 0 值
     */
    @Test
    @DisplayName("近 7 日无提交：Top 柱图保留一个占位类目")
    void shouldKeepPlaceholderWhenNoSubmit() {
        stubBaseline(new DefinitionCount(1L, 1L), 0L, 0L, 0L, 0L, List.of(), List.of());

        OverviewSnapshot snapshot = provider.build(contextWith(Set.of("sys:form:list")));

        assertEquals(List.of("近 7 日无提交"), snapshot.charts().get(1).categories());
        assertEquals(List.of(0d), snapshot.charts().get(1).series().get(0).values());
    }

    /**
     * Top 类目：超长表单名截断，避免横轴被挤没
     */
    @Test
    @DisplayName("Top 表单：超长表单名截断到 6 字")
    void shouldTruncateLongFormName() {
        stubBaseline(new DefinitionCount(1L, 1L), 7L, 0L, 0L, 0L, List.of(),
                List.of(new FormTop("expense", "差旅费用报销申请单", 7L)));

        OverviewSnapshot snapshot = provider.build(contextWith(Set.of("sys:form:list")));

        assertEquals(List.of("差旅费用报销…"), snapshot.charts().get(1).categories());
        assertEquals(List.of(7d), snapshot.charts().get(1).series().get(0).values());
    }

    /**
     * 定义已删除（名称为空）：类目回落编码，不能显示空标签
     */
    @Test
    @DisplayName("Top 表单：名称缺失时回落表单编码")
    void shouldFallbackToCodeWhenNameMissing() {
        stubBaseline(new DefinitionCount(1L, 1L), 3L, 0L, 0L, 0L, List.of(),
                List.of(new FormTop("old_form", null, 3L)));

        OverviewSnapshot snapshot = provider.build(contextWith(Set.of("sys:form:list")));

        assertEquals(List.of("old_fo…"), snapshot.charts().get(1).categories());
    }

    /**
     * 差异告警：主从不一致 → CRITICAL（用户看到的数据与实际存储不是一回事）
     */
    @Test
    @DisplayName("告警：有未修对账差异 → CRITICAL")
    void shouldAlertOnReconcileDiff() {
        stubBaseline(new DefinitionCount(2L, 2L), 4L, 1L, 0L, 2L, List.of(), List.of());

        OverviewSnapshot snapshot = provider.build(contextWith(Set.of("sys:form:list")));

        assertEquals(1, snapshot.alerts().size());
        assertEquals(OverviewAlertLevel.CRITICAL, snapshot.alerts().get(0).level());
        assertTrue(snapshot.alerts().get(0).text().contains("2"), snapshot.alerts().get(0).text());
        assertTrue(snapshot.alerts().get(0).text().contains("物理表"), snapshot.alerts().get(0).text());
    }

    /**
     * 超时待审告警：WARNING（慢不等于坏，隔夜审批本来就有）
     */
    @Test
    @DisplayName("告警：待审批超 24 小时 → WARNING")
    void shouldWarnOnStalePending() {
        stubBaseline(new DefinitionCount(2L, 2L), 4L, 6L, 3L, 0L, List.of(), List.of());

        OverviewSnapshot snapshot = provider.build(contextWith(Set.of("sys:form:list")));

        assertEquals(1, snapshot.alerts().size());
        assertEquals(OverviewAlertLevel.WARNING, snapshot.alerts().get(0).level());
        assertTrue(snapshot.alerts().get(0).text().contains("3"), snapshot.alerts().get(0).text());
        assertEquals("其中超 24h 未动 3", hintOf(snapshot, "pending"));
    }

    /**
     * 两条告警并存：差异是写库失败、超时是没人处理，处置动作不同，不能互相覆盖
     */
    @Test
    @DisplayName("告警：差异与超时待审同时出现，两条都在")
    void shouldKeepBothAlerts() {
        stubBaseline(new DefinitionCount(2L, 2L), 4L, 8L, 5L, 1L, List.of(), List.of());

        OverviewSnapshot snapshot = provider.build(contextWith(Set.of("sys:form:list")));

        assertEquals(2, snapshot.alerts().size());
        assertEquals(OverviewAlertLevel.CRITICAL, snapshot.alerts().get(0).level());
        assertEquals(OverviewAlertLevel.WARNING, snapshot.alerts().get(1).level());
        assertEquals(1d, kpi(snapshot, "diff"));
    }

    /**
     * 待审批为 0：副标给"已全部办结"，而不是一个空括号
     */
    @Test
    @DisplayName("待审批为 0：副标「已全部办结」")
    void shouldSayAllDoneWhenNothingPending() {
        stubBaseline(new DefinitionCount(2L, 2L), 1L, 0L, 0L, 0L, List.of(), List.of());

        OverviewSnapshot snapshot = provider.build(contextWith(Set.of("sys:form:list")));

        assertEquals("已全部办结", hintOf(snapshot, "pending"));
    }

    /**
     * 无租户上下文：四个数都是 0，但必须留下"为什么是 0"的说明
     */
    @Test
    @DisplayName("无租户上下文：KPI 全 0 并留下口径说明")
    void shouldDegradeWithoutTenant() {
        OverviewSnapshot snapshot = provider.build(OverviewContext.of(1L, null, Set.of("sys:form:list")));

        assertEquals(0d, kpi(snapshot, "forms"));
        assertEquals(0d, kpi(snapshot, "submits"));
        assertTrue(snapshot.notes().stream().anyMatch(note -> note.contains("未识别租户")));
    }

    /**
     * 无权限码：入口不下发（下发再让前端隐藏等于把入口清单白送）
     */
    @Test
    @DisplayName("无表单权限：不下发入口")
    void shouldHideLinkWithoutPermission() {
        stubBaseline(new DefinitionCount(2L, 2L), 1L, 0L, 0L, 0L, List.of(), List.of());

        OverviewSnapshot snapshot = provider.build(contextWith(Set.of("sys:user:list")));

        assertFalse(snapshot.links().stream().anyMatch(link -> "forms".equals(link.key())));
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
     * 桩：七个统计口全部按参数返回
     *
     * @param forms    定义统计
     * @param today    今日提交条数
     * @param pending  累计待审批条数
     * @param stale    超时待审批条数
     * @param diff     待修差异条数
     * @param trend    按天趋势
     * @param topForms 提交 Top
     */
    private void stubBaseline(DefinitionCount forms, long today, long pending, long stale, long diff,
                              List<DaySubmits> trend, List<FormTop> topForms) {
        when(statQuery.definitionCount(anyLong())).thenReturn(forms);
        when(statQuery.submittedBetween(anyLong(), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(today);
        when(statQuery.countByBizStatus(anyLong(), anyInt())).thenReturn(pending);
        when(statQuery.staleByBizStatus(anyLong(), anyInt(), any(LocalDateTime.class))).thenReturn(stale);
        when(statQuery.pendingReconcile(anyLong())).thenReturn(diff);
        when(statQuery.dailySubmits(anyLong(), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(trend);
        when(statQuery.topForms(anyLong(), any(LocalDateTime.class), any(LocalDateTime.class), anyInt()))
                .thenReturn(topForms);
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

    /**
     * 取指标副标
     *
     * @param snapshot 快照
     * @param key      指标键
     * @return 副标文案
     */
    private String hintOf(OverviewSnapshot snapshot, String key) {
        return snapshot.kpis().stream().filter(item -> key.equals(item.key()))
                .findFirst().orElseThrow().hint();
    }
}
