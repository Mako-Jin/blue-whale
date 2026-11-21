package com.bluewhale.auth.starter.overview;

import com.bluewhale.auth.application.port.AuthSecurityStatQuery;
import com.bluewhale.auth.application.port.AuthSecurityStatQuery.LoginDayCount;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * {@link AuthOverviewProvider} 单元测试（T5.2）。
 *
 * <p>钉住三件容易悄悄漂移的事：① KPI 顺序与口径（屏风按位置排版，换序没人报错）；
 * ② 趋势缺日必须补 0（跳过空日会把断档画成连续）；③ 告警两条规则只出一条
 * （同时报"次数超了"和"占比超了"，值班的人会以为出了两个问题）。</p>
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
class AuthOverviewProviderTest {

    /** 测试租户 */
    private static final long TENANT_ID = 7L;

    @Mock
    private AuthSecurityStatQuery statQuery;

    @InjectMocks
    private AuthOverviewProvider provider;

    /**
     * 正常形态：四张 KPI（顺序固定）、两张图、一个登录历史入口、无告警
     */
    @Test
    @DisplayName("正常：四张 KPI + 两张图 + 登录历史入口，今日无失败不告警")
    void shouldBuildKpisChartsAndLink() {
        LocalDate today = LocalDate.now();
        when(statQuery.countEnabledUsers(TENANT_ID)).thenReturn(42L);
        when(statQuery.countActiveSessions(eq(TENANT_ID), any(LocalDateTime.class))).thenReturn(9L);
        when(statQuery.countLoginByDay(eq(TENANT_ID), any(), any())).thenReturn(List.of(
                new LoginDayCount(today, "SUCCESS", 18L),
                new LoginDayCount(today, "LOGOUT", 4L),
                new LoginDayCount(today.minusDays(1), "SUCCESS", 12L),
                new LoginDayCount(today.minusDays(1), "FAILURE", 1L)));

        OverviewSnapshot snapshot = provider.build(contextWith(Set.of("sys:login:history:list")));

        assertEquals("auth", snapshot.service());
        assertEquals("shumi-hall", snapshot.nodeKey());
        assertEquals(List.of("users", "sessions", "logins", "failures"),
                snapshot.kpis().stream().map(OverviewKpi::key).toList());
        assertEquals(42d, kpi(snapshot, "users"));
        assertEquals(9d, kpi(snapshot, "sessions"));
        assertEquals(18d, kpi(snapshot, "logins"));
        assertEquals(0d, kpi(snapshot, "failures"));
        assertEquals(2, snapshot.charts().size());
        assertEquals(1, snapshot.links().size());
        assertEquals("login-history", snapshot.links().get(0).key());
        assertEquals("/login-history/v1/page", snapshot.links().get(0).path());
        assertTrue(snapshot.alerts().isEmpty());
    }

    /**
     * 趋势序列：近 7 天、缺日补 0、最后一位是今天
     */
    @Test
    @DisplayName("趋势：近 7 天序列缺日补 0，今天在最后一位")
    void shouldZeroFillMissingDays() {
        LocalDate today = LocalDate.now();
        when(statQuery.countEnabledUsers(anyLong())).thenReturn(0L);
        when(statQuery.countActiveSessions(anyLong(), any(LocalDateTime.class))).thenReturn(0L);
        when(statQuery.countLoginByDay(anyLong(), any(), any())).thenReturn(List.of(
                new LoginDayCount(today, "SUCCESS", 3L),
                new LoginDayCount(today.minusDays(6), "FAILURE", 2L)));

        OverviewSnapshot snapshot = provider.build(contextWith(Set.of("sys:login:history:list")));

        List<Double> success = snapshot.charts().get(0).series().get(0).values();
        List<Double> failure = snapshot.charts().get(0).series().get(1).values();
        assertEquals(7, success.size());
        assertEquals(List.of(0d, 0d, 0d, 0d, 0d, 0d, 3d), success);
        assertEquals(List.of(2d, 0d, 0d, 0d, 0d, 0d, 0d), failure);
    }

    /**
     * 次数告警优先：失败 25 次直接 CRITICAL，且不再追加占比告警
     */
    @Test
    @DisplayName("告警：今日失败 25 次 → 只出一条 CRITICAL")
    void shouldAlertOnManyFailures() {
        stubCounts(30L, 25L);

        OverviewSnapshot snapshot = provider.build(contextWith(Set.of("sys:login:history:list")));

        assertEquals(1, snapshot.alerts().size());
        assertEquals(OverviewAlertLevel.CRITICAL, snapshot.alerts().get(0).level());
        assertEquals(25, snapshot.alerts().get(0).count());
        assertTrue(snapshot.alerts().get(0).text().contains("25"));
    }

    /**
     * 占比告警：失败 6 / 成功 10（37.5%）超过 30% 且样本够
     */
    @Test
    @DisplayName("告警：失败占比 37.5%（样本 16）→ WARNING")
    void shouldAlertOnHighFailureRatio() {
        stubCounts(10L, 6L);

        OverviewSnapshot snapshot = provider.build(contextWith(Set.of("sys:login:history:list")));

        assertEquals(1, snapshot.alerts().size());
        assertEquals(OverviewAlertLevel.WARNING, snapshot.alerts().get(0).level());
        assertTrue(snapshot.alerts().get(0).text().contains("38%"), snapshot.alerts().get(0).text());
    }

    /**
     * 样本太小不谈占比：1 失败 0 成功是 100%，但那是有人手滑，不是撞库
     */
    @Test
    @DisplayName("告警：失败 2 次（样本 2）→ 不告警")
    void shouldNotAlertOnSmallSample() {
        stubCounts(0L, 2L);

        OverviewSnapshot snapshot = provider.build(contextWith(Set.of("sys:login:history:list")));

        assertTrue(snapshot.alerts().isEmpty());
    }

    /**
     * 无租户上下文：四个数都是 0，但必须留下"为什么是 0"的说明
     */
    @Test
    @DisplayName("无租户上下文：KPI 全 0 并留下口径说明，不查库")
    void shouldDegradeWithoutTenant() {
        OverviewSnapshot snapshot = provider.build(OverviewContext.of(1L, null, Set.of("sys:login:history:list")));

        assertEquals(0d, kpi(snapshot, "users"));
        assertEquals(0d, kpi(snapshot, "sessions"));
        assertTrue(snapshot.notes().stream().anyMatch(note -> note.contains("未识别租户")));
    }

    /**
     * 无权限码：入口不下发（下发再让前端隐藏等于把入口清单白送）
     */
    @Test
    @DisplayName("无登录历史权限：不下发入口")
    void shouldHideLinkWithoutPermission() {
        stubCounts(5L, 0L);

        OverviewSnapshot snapshot = provider.build(contextWith(Set.of("sys:user:list")));

        assertFalse(snapshot.links().stream().anyMatch(link -> "login-history".equals(link.key())));
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
     * 桩：用户数 / 会话数固定，今日登录成功与失败按参数
     *
     * @param success 今日成功次数
     * @param failure 今日失败次数
     */
    private void stubCounts(long success, long failure) {
        when(statQuery.countEnabledUsers(anyLong())).thenReturn(12L);
        when(statQuery.countActiveSessions(anyLong(), any(LocalDateTime.class))).thenReturn(3L);
        List<LoginDayCount> rows = new ArrayList<>();
        LocalDate today = LocalDate.now();
        if (success > 0) {
            rows.add(new LoginDayCount(today, "SUCCESS", success));
        }
        if (failure > 0) {
            rows.add(new LoginDayCount(today, "FAILURE", failure));
        }
        when(statQuery.countLoginByDay(anyLong(), any(), any())).thenReturn(rows);
    }

    /**
     * 取指标值
     *
     * @param snapshot 快照
     * @param key 指标键
     * @return 数值
     */
    private double kpi(OverviewSnapshot snapshot, String key) {
        return snapshot.kpis().stream().filter(item -> key.equals(item.key()))
                .findFirst().orElseThrow().value();
    }
}
