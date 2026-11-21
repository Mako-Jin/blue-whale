package com.bluewhale.job.overview;

import com.bluewhale.job.service.JobScheduleStatQuery;
import com.bluewhale.job.service.JobScheduleStatQuery.DayRuns;
import com.bluewhale.job.service.JobScheduleStatQuery.FailTop;
import com.bluewhale.job.service.JobScheduleStatQuery.JobCount;
import com.bluewhale.job.service.JobScheduleStatQuery.RunStat;
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
 * {@link JobOverviewProvider} 单元测试（T5.5）。
 *
 * <p>钉住五件容易悄悄漂移的事：① KPI 顺序与口径（屏风按位置排版，换序没人报错）；
 * ② 趋势缺日必须补 0；③ 近 7 日无失败时柱图不能给空类目（空类目在屏风上画成一条白线）；
 * ④ 告警的两条判据——"全部静默"是调度停摆、"失败多"是任务自身报错，各自成立且可并存；
 * ⑤ 部分任务静默时不告警（周级任务在 24 小时窗口里本来就是空的）。</p>
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
class JobOverviewProviderTest {

    /** 测试租户 */
    private static final long TENANT_ID = 7L;

    @Mock
    private JobScheduleStatQuery statQuery;

    @InjectMocks
    private JobOverviewProvider provider;

    /**
     * 正常形态：四张 KPI（顺序固定）、两张图、一个任务入口、无告警
     */
    @Test
    @DisplayName("正常：四张 KPI + 两张图 + 任务入口，无静默无失败不告警")
    void shouldBuildKpisChartsAndLink() {
        stubBaseline(new JobCount(30L, 26L), new RunStat(40L, 40L, 0L), 0L, 0L,
                List.of(), List.of(new FailTop("归档任务", 0L)));

        OverviewSnapshot snapshot = provider.build(contextWith(Set.of("sys:job:list")));

        assertEquals("job", snapshot.service());
        assertEquals("shichen-hall", snapshot.nodeKey());
        assertEquals(List.of("jobs", "runs", "failures", "quiet"),
                snapshot.kpis().stream().map(OverviewKpi::key).toList());
        assertEquals(30d, kpi(snapshot, "jobs"));
        assertEquals(40d, kpi(snapshot, "runs"));
        assertEquals(0d, kpi(snapshot, "failures"));
        assertEquals(0d, kpi(snapshot, "quiet"));
        assertEquals(2, snapshot.charts().size());
        assertEquals(1, snapshot.links().size());
        assertEquals("jobs", snapshot.links().get(0).key());
        assertEquals("/job/v1/info/page", snapshot.links().get(0).path());
        assertTrue(snapshot.alerts().isEmpty());
    }

    /**
     * 今日执行 KPI 的副标：只数已结束的执行，成功率按成功/已结束算
     */
    @Test
    @DisplayName("今日执行：副标写成功数与成功率（分母是已结束执行）")
    void shouldShowSuccessHint() {
        stubBaseline(new JobCount(10L, 10L), new RunStat(40L, 30L, 10L), 3L, 0L,
                List.of(), List.of());

        OverviewSnapshot snapshot = provider.build(contextWith(Set.of("sys:job:list")));

        String hint = snapshot.kpis().stream().filter(item -> "runs".equals(item.key()))
                .findFirst().orElseThrow().hint();
        assertEquals("成功 30 · 75%", hint);
    }

    /**
     * 趋势序列：近 7 天、缺日补 0、今天在最后一位
     */
    @Test
    @DisplayName("趋势：近 7 天序列缺日补 0，今天在最后一位")
    void shouldZeroFillMissingDays() {
        LocalDate today = LocalDate.now();
        stubBaseline(new JobCount(5L, 5L), new RunStat(0L, 0L, 0L), 0L, 0L,
                List.of(new DayRuns(today, 9L), new DayRuns(today.minusDays(6), 3L)),
                List.of());

        OverviewSnapshot snapshot = provider.build(contextWith(Set.of("sys:job:list")));

        List<Double> values = snapshot.charts().get(0).series().get(0).values();
        assertEquals(7, values.size());
        assertEquals(List.of(3d, 0d, 0d, 0d, 0d, 0d, 9d), values);
    }

    /**
     * 近 7 日无失败：柱图给一个占位类目与 0 值
     */
    @Test
    @DisplayName("近 7 日无失败：失败柱图保留一个占位类目")
    void shouldKeepPlaceholderWhenNoFailure() {
        stubBaseline(new JobCount(5L, 5L), new RunStat(20L, 20L, 0L), 0L, 0L, List.of(), List.of());

        OverviewSnapshot snapshot = provider.build(contextWith(Set.of("sys:job:list")));

        assertEquals(List.of("近 7 日无失败"), snapshot.charts().get(1).categories());
        assertEquals(List.of(0d), snapshot.charts().get(1).series().get(0).values());
    }

    /**
     * 失败 Top 类目：超长任务名截断，避免横轴被挤没
     */
    @Test
    @DisplayName("失败 Top：超长任务名截断到 8 字")
    void shouldTruncateLongJobName() {
        stubBaseline(new JobCount(5L, 5L), new RunStat(20L, 18L, 2L), 1L, 0L, List.of(),
                List.of(new FailTop("每日全量数据归档与清理任务", 2L)));

        OverviewSnapshot snapshot = provider.build(contextWith(Set.of("sys:job:list")));

        assertEquals(List.of("每日全量数据归档…"), snapshot.charts().get(1).categories());
        assertEquals(List.of(2d), snapshot.charts().get(1).series().get(0).values());
    }

    /**
     * 失败告警：今日失败 5 次（阈值含）→ CRITICAL
     */
    @Test
    @DisplayName("告警：今日失败 5 次 → CRITICAL")
    void shouldAlertOnFailureCount() {
        stubBaseline(new JobCount(10L, 10L), new RunStat(20L, 15L, 5L), 2L, 0L, List.of(), List.of());

        OverviewSnapshot snapshot = provider.build(contextWith(Set.of("sys:job:list")));

        assertEquals(1, snapshot.alerts().size());
        assertEquals(OverviewAlertLevel.CRITICAL, snapshot.alerts().get(0).level());
        assertTrue(snapshot.alerts().get(0).text().contains("5"), snapshot.alerts().get(0).text());
        assertTrue(snapshot.alerts().get(0).text().contains("2"), snapshot.alerts().get(0).text());
    }

    /**
     * 失败占比告警：失败 3 次（未到量阈值）、样本 10、占比 30% → WARNING
     */
    @Test
    @DisplayName("告警：失败占比 30% 且样本足够 → WARNING")
    void shouldAlertOnFailureRate() {
        stubBaseline(new JobCount(10L, 10L), new RunStat(10L, 7L, 3L), 3L, 0L, List.of(), List.of());

        OverviewSnapshot snapshot = provider.build(contextWith(Set.of("sys:job:list")));

        assertEquals(1, snapshot.alerts().size());
        assertEquals(OverviewAlertLevel.WARNING, snapshot.alerts().get(0).level());
        assertTrue(snapshot.alerts().get(0).text().contains("30%"), snapshot.alerts().get(0).text());
    }

    /**
     * 样本太小不谈占比：失败 2 次、样本 4（占比 50%）不告警
     */
    @Test
    @DisplayName("告警：样本不足 10 时不算占比（2 失败 / 4 次不报）")
    void shouldNotAlertOnRateWithSmallSample() {
        stubBaseline(new JobCount(10L, 10L), new RunStat(4L, 2L, 2L), 1L, 0L, List.of(), List.of());

        OverviewSnapshot snapshot = provider.build(contextWith(Set.of("sys:job:list")));

        assertTrue(snapshot.alerts().isEmpty());
    }

    /**
     * 全部静默：运行中任务全都没有近 24 小时执行记录 → CRITICAL（调度器停摆）
     */
    @Test
    @DisplayName("告警：运行中任务全部静默 → CRITICAL（调度器停摆）")
    void shouldAlertWhenAllRunningAreSilent() {
        stubBaseline(new JobCount(4L, 4L), new RunStat(0L, 0L, 0L), 0L, 4L, List.of(), List.of());

        OverviewSnapshot snapshot = provider.build(contextWith(Set.of("sys:job:list")));

        assertEquals(1, snapshot.alerts().size());
        assertEquals(OverviewAlertLevel.CRITICAL, snapshot.alerts().get(0).level());
        assertTrue(snapshot.alerts().get(0).text().contains("调度器"), snapshot.alerts().get(0).text());
    }

    /**
     * 部分静默：3 个任务里只有 1 个静默 → 不告警（周级任务本就是空的）
     */
    @Test
    @DisplayName("部分静默不告警：只展示静默数量")
    void shouldNotAlertOnPartialSilence() {
        stubBaseline(new JobCount(3L, 3L), new RunStat(12L, 12L, 0L), 0L, 1L, List.of(), List.of());

        OverviewSnapshot snapshot = provider.build(contextWith(Set.of("sys:job:list")));

        assertEquals(1d, kpi(snapshot, "quiet"));
        assertTrue(snapshot.alerts().isEmpty());
    }

    /**
     * 无租户上下文：四个数都是 0，但必须留下"为什么是 0"的说明
     */
    @Test
    @DisplayName("无租户上下文：KPI 全 0 并留下口径说明")
    void shouldDegradeWithoutTenant() {
        OverviewSnapshot snapshot = provider.build(OverviewContext.of(1L, null, Set.of("sys:job:list")));

        assertEquals(0d, kpi(snapshot, "jobs"));
        assertEquals(0d, kpi(snapshot, "runs"));
        assertTrue(snapshot.notes().stream().anyMatch(note -> note.contains("未识别租户")));
    }

    /**
     * 无权限码：入口不下发（下发再让前端隐藏等于把入口清单白送）
     */
    @Test
    @DisplayName("无任务权限：不下发入口")
    void shouldHideLinkWithoutPermission() {
        stubBaseline(new JobCount(5L, 5L), new RunStat(10L, 10L, 0L), 0L, 0L, List.of(), List.of());

        OverviewSnapshot snapshot = provider.build(contextWith(Set.of("sys:user:list")));

        assertFalse(snapshot.links().stream().anyMatch(link -> "jobs".equals(link.key())));
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
     * @param jobs     任务统计
     * @param today    今日执行统计
     * @param failedJobs 今日失败任务数
     * @param silent   静默任务数
     * @param trend    按天趋势
     * @param failTop  失败 Top
     */
    private void stubBaseline(JobCount jobs, RunStat today, long failedJobs, long silent,
                              List<DayRuns> trend, List<FailTop> failTop) {
        when(statQuery.jobCount(anyLong())).thenReturn(jobs);
        when(statQuery.runsBetween(anyLong(), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(today);
        when(statQuery.failedJobCount(anyLong(), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(failedJobs);
        when(statQuery.silentRunning(anyLong(), any(LocalDateTime.class))).thenReturn(silent);
        when(statQuery.dailyRuns(anyLong(), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(trend);
        when(statQuery.failTop(anyLong(), any(LocalDateTime.class), any(LocalDateTime.class), anyInt()))
                .thenReturn(failTop);
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
