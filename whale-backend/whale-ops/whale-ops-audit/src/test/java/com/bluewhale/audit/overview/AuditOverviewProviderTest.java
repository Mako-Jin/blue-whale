package com.bluewhale.audit.overview;

import com.bluewhale.audit.service.AuditOverviewStatQuery;
import com.bluewhale.audit.service.AuditOverviewStatQuery.DayAudit;
import com.bluewhale.audit.service.AuditOverviewStatQuery.OperateTypeCount;
import com.bluewhale.overview.OverviewContext;
import com.bluewhale.overview.model.OverviewAlertLevel;
import com.bluewhale.overview.model.OverviewKpi;
import com.bluewhale.overview.model.OverviewSnapshot;
import com.bluewhale.overview.model.OverviewTone;
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
 * {@link AuditOverviewProvider} 单元测试（S5 / T5.9）。
 *
 * <p>钉住五件容易悄悄漂移的事：① KPI 顺序与口径（屏风按位置排版，换序没人报错）；
 * ② 留痕断流的两个判据——"今日 0 条"不等于断流，真正看的是最近一条距今多久，
 * 且从未写入记 -1 不算故障；③ 归档陈旧与断流两条告警根因不同、可并存；
 * ④ 趋势缺日补 0、无数据时柱图给占位类目；⑤ 无租户/无权限时的降级与裁剪。</p>
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
class AuditOverviewProviderTest {

    /** 测试租户 */
    private static final long TENANT_ID = 7L;

    /** 概览权限码 */
    private static final String PERMISSION = "sys:audit:log:list";

    @Mock
    private AuditOverviewStatQuery statQuery;

    @InjectMocks
    private AuditOverviewProvider provider;

    @Test
    @DisplayName("正常：四张 KPI + 两张图 + 日志入口，无告警")
    void shouldBuildKpisChartsAndLink() {
        LocalDateTime now = LocalDateTime.now();
        stubBaseline(120L, 0L, now.minusMinutes(5), now.minusDays(1),
                List.of(), List.of(new OperateTypeCount("QUERY", 80L)));

        OverviewSnapshot snapshot = provider.build(contextWith(Set.of(PERMISSION)));

        assertEquals("audit", snapshot.service());
        assertEquals("qiju-hall", snapshot.nodeKey());
        assertEquals(List.of("today", "failed", "silence", "stale"),
                snapshot.kpis().stream().map(OverviewKpi::key).toList());
        assertEquals(120d, kpi(snapshot, "today"));
        assertEquals(0d, kpi(snapshot, "failed"));
        assertEquals(2, snapshot.charts().size());
        assertEquals(1, snapshot.links().size());
        assertEquals("logs", snapshot.links().get(0).key());
        assertEquals("/audit/v1/log/page", snapshot.links().get(0).path());
        assertTrue(snapshot.alerts().isEmpty());
    }

    @Test
    @DisplayName("留痕断流：最近一条在 30 小时前 → BAD tone + CRITICAL 告警")
    void shouldAlertOnSilence() {
        LocalDateTime now = LocalDateTime.now();
        stubBaseline(120L, 0L, now.minusHours(30), now.minusDays(1), List.of(), List.of());

        OverviewSnapshot snapshot = provider.build(contextWith(Set.of(PERMISSION)));

        assertEquals(OverviewTone.BAD, tone(snapshot, "silence"));
        assertTrue(kpi(snapshot, "silence") >= 30 * 3600d);
        assertEquals(1, snapshot.alerts().size());
        assertEquals(OverviewAlertLevel.CRITICAL, snapshot.alerts().get(0).level());
        assertTrue(snapshot.alerts().get(0).text().contains("断流"), snapshot.alerts().get(0).text());
    }

    @Test
    @DisplayName("断流副标：超 2h 无新审计时提示查摄入链路")
    void shouldHintOnSilence() {
        LocalDateTime now = LocalDateTime.now();
        stubBaseline(120L, 0L, now.minusHours(3), now.minusDays(1), List.of(), List.of());

        OverviewSnapshot snapshot = provider.build(contextWith(Set.of(PERMISSION)));

        assertEquals(OverviewTone.WARN, tone(snapshot, "silence"));
        assertTrue(hint(snapshot, "silence").contains("摄入链路"), hint(snapshot, "silence"));
        assertTrue(snapshot.alerts().isEmpty());
    }

    @Test
    @DisplayName("从未写入：断流记 -1，不算故障、不告警，并留下说明")
    void shouldTreatNeverWrittenAsUnknown() {
        stubBaseline(0L, 0L, null, null, List.of(), List.of());

        OverviewSnapshot snapshot = provider.build(contextWith(Set.of(PERMISSION)));

        assertEquals(-1d, kpi(snapshot, "silence"));
        assertEquals(OverviewTone.FLAT, tone(snapshot, "silence"));
        assertTrue(snapshot.notes().stream().anyMatch(note -> note.contains("尚无审计记录")));
        assertTrue(snapshot.alerts().isEmpty());
    }

    @Test
    @DisplayName("归档陈旧：最后成功在 5 天前 → WARN 告警")
    void shouldAlertOnArchiveStale() {
        LocalDateTime now = LocalDateTime.now();
        stubBaseline(120L, 0L, now.minusMinutes(5), now.minusDays(5), List.of(), List.of());

        OverviewSnapshot snapshot = provider.build(contextWith(Set.of(PERMISSION)));

        assertEquals(OverviewTone.WARN, tone(snapshot, "stale"));
        assertTrue(kpi(snapshot, "stale") >= 5d);
        assertEquals(1, snapshot.alerts().size());
        assertEquals(OverviewAlertLevel.WARNING, snapshot.alerts().get(0).level());
        assertTrue(snapshot.alerts().get(0).text().contains("归档"), snapshot.alerts().get(0).text());
    }

    @Test
    @DisplayName("两条告警并存：断流 CRITICAL 在前、归档陈旧 WARNING 在后")
    void shouldKeepBothAlerts() {
        LocalDateTime now = LocalDateTime.now();
        stubBaseline(120L, 0L, now.minusHours(30), now.minusDays(5), List.of(), List.of());

        OverviewSnapshot snapshot = provider.build(contextWith(Set.of(PERMISSION)));

        assertEquals(2, snapshot.alerts().size());
        assertEquals(OverviewAlertLevel.CRITICAL, snapshot.alerts().get(0).level());
        assertEquals(OverviewAlertLevel.WARNING, snapshot.alerts().get(1).level());
    }

    @Test
    @DisplayName("失败操作：近 24h 有失败 → WARN tone")
    void shouldWarnOnFailures() {
        LocalDateTime now = LocalDateTime.now();
        stubBaseline(120L, 3L, now.minusMinutes(5), now.minusDays(1), List.of(), List.of());

        OverviewSnapshot snapshot = provider.build(contextWith(Set.of(PERMISSION)));

        assertEquals(3d, kpi(snapshot, "failed"));
        assertEquals(OverviewTone.WARN, tone(snapshot, "failed"));
    }

    @Test
    @DisplayName("趋势：近 7 天序列缺日补 0，今天在最后一位")
    void shouldZeroFillMissingDays() {
        LocalDateTime now = LocalDateTime.now();
        LocalDate today = LocalDate.now();
        stubBaseline(9L, 0L, now.minusMinutes(5), now.minusDays(1),
                List.of(new DayAudit(today, 9L), new DayAudit(today.minusDays(6), 3L)), List.of());

        OverviewSnapshot snapshot = provider.build(contextWith(Set.of(PERMISSION)));

        List<Double> values = snapshot.charts().get(0).series().get(0).values();
        assertEquals(7, values.size());
        assertEquals(List.of(3d, 0d, 0d, 0d, 0d, 0d, 9d), values);
    }

    @Test
    @DisplayName("近 7 日无审计：两张图都保留占位类目")
    void shouldKeepPlaceholderWhenEmpty() {
        LocalDateTime now = LocalDateTime.now();
        stubBaseline(0L, 0L, now.minusMinutes(5), now.minusDays(1), List.of(), List.of());

        OverviewSnapshot snapshot = provider.build(contextWith(Set.of(PERMISSION)));

        assertEquals(List.of("近 7 日无审计"), snapshot.charts().get(1).categories());
        assertEquals(List.of(0d), snapshot.charts().get(1).series().get(0).values());
    }

    @Test
    @DisplayName("无租户上下文：KPI 全 0/断流 -1 并留下口径说明")
    void shouldDegradeWithoutTenant() {
        OverviewSnapshot snapshot = provider.build(OverviewContext.of(1L, null, Set.of(PERMISSION)));

        assertEquals(0d, kpi(snapshot, "today"));
        assertEquals(-1d, kpi(snapshot, "silence"));
        assertTrue(snapshot.notes().stream().anyMatch(note -> note.contains("未识别租户")));
        assertTrue(snapshot.alerts().isEmpty());
    }

    @Test
    @DisplayName("无审计日志权限：不下发入口")
    void shouldHideLinkWithoutPermission() {
        LocalDateTime now = LocalDateTime.now();
        stubBaseline(5L, 0L, now.minusMinutes(5), now.minusDays(1), List.of(), List.of());

        OverviewSnapshot snapshot = provider.build(contextWith(Set.of("sys:user:list")));

        assertFalse(snapshot.links().stream().anyMatch(link -> "logs".equals(link.key())));
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
     * 打桩：六个统计口全部按参数返回
     */
    private void stubBaseline(long todayAudits, long failed, LocalDateTime lastAudit,
                              LocalDateTime archiveLastSuccess, List<DayAudit> trend,
                              List<OperateTypeCount> typeTop) {
        when(statQuery.todayCount(anyLong(), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(todayAudits);
        when(statQuery.failedCount(anyLong(), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(failed);
        when(statQuery.lastAuditTime(anyLong())).thenReturn(lastAudit);
        when(statQuery.dailyAudits(anyLong(), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(trend);
        when(statQuery.topOperateTypes(anyLong(), any(LocalDateTime.class), any(LocalDateTime.class),
                anyInt())).thenReturn(typeTop);
        when(statQuery.archiveLastSuccessAt()).thenReturn(archiveLastSuccess);
    }

    private double kpi(OverviewSnapshot snapshot, String key) {
        return kpiItem(snapshot, key).value();
    }

    private OverviewTone tone(OverviewSnapshot snapshot, String key) {
        return kpiItem(snapshot, key).tone();
    }

    private String hint(OverviewSnapshot snapshot, String key) {
        return kpiItem(snapshot, key).hint();
    }

    private OverviewKpi kpiItem(OverviewSnapshot snapshot, String key) {
        return snapshot.kpis().stream().filter(item -> key.equals(item.key()))
                .findFirst().orElseThrow();
    }
}
