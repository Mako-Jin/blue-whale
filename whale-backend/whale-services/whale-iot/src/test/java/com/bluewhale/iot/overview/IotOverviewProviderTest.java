package com.bluewhale.iot.overview;

import com.bluewhale.iot.dto.IotDayCount;
import com.bluewhale.iot.dto.IotStats;
import com.bluewhale.iot.service.IotStatsService;
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

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link IotOverviewProvider} 单元测试：在线率口径（停用不进分母）、指令欠账、
 * 上报断流、图表对齐与入口权限。
 *
 * @author BlueWhale
 */
class IotOverviewProviderTest {

    private final IotStatsService statsService = mock(IotStatsService.class);

    private final IotOverviewProvider provider = new IotOverviewProvider(statsService);

    /**
     * 构造一份统计快照；近 7 日趋势固定为「昨天 40 条、今天 0 条」，便于断言柱图长度。
     *
     * @param deviceTotal     设备总数
     * @param deviceActive    启用设备数
     * @param deviceOnline    在线设备数
     * @param telemetry24h    近 24h 上报条数
     * @param pendingCommands 待回执指令数
     * @param rulesTotal      规则总数
     * @param rulesEnabled    启用规则数
     * @return 统计快照
     */
    private static IotStats stats(long deviceTotal, long deviceActive, long deviceOnline,
            long telemetry24h, long pendingCommands, long rulesTotal, long rulesEnabled) {
        List<IotDayCount> days = new ArrayList<>();
        for (int day = 1; day <= 7; day++) {
            days.add(new IotDayCount("11-0" + day, day == 7 ? 0L : 40L));
        }
        return new IotStats(deviceTotal, deviceActive, deviceOnline, telemetry24h, days,
                pendingCommands, rulesTotal, rulesEnabled);
    }

    /**
     * 具备设备管理权限的调用者
     *
     * @return 上下文
     */
    private static OverviewContext context() {
        return OverviewContext.of(2001L, 1L, Set.of("sys:iot:device"));
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
    @DisplayName("元信息与权限码：复用 2D 设备管理同一权限码，不为 3D 单独发授权")
    void shouldExposeMetadata() {
        assertThat(provider.service()).isEqualTo("iot");
        assertThat(provider.nodeKey()).isEqualTo("bingbu-hall");
        assertThat(provider.title()).isEqualTo("兵部");
        assertThat(provider.subtitle()).isEqualTo("物联感知");
        assertThat(provider.permissionCode()).isEqualTo("sys:iot:device");
    }

    @Test
    @DisplayName("在线率：停用设备不进分母（10 启用 8 在线 = 0.8），且提示语写出分子分母")
    void shouldComputeOnlineRateAgainstActiveOnly() {
        when(statsService.snapshot(any(LocalDateTime.class)))
                .thenReturn(stats(12L, 10L, 8L, 500L, 0L, 3L, 3L));

        OverviewKpi rate = kpiMap(provider.build(context())).get("online-rate");

        assertThat(rate.format()).isEqualTo(OverviewFormat.PERCENT);
        assertThat(rate.value()).isEqualTo(0.8d);
        assertThat(rate.tone()).isEqualTo(OverviewTone.WARN);
        assertThat(rate.hint()).isEqualTo("8 / 10 在线");
    }

    @Test
    @DisplayName("没有启用设备：在线率给灰灯并说明原因，不能让 0% 冒充全城失联")
    void shouldMarkRateFlatWhenNoActiveDevice() {
        when(statsService.snapshot(any(LocalDateTime.class)))
                .thenReturn(stats(3L, 0L, 0L, 0L, 0L, 0L, 0L));

        OverviewKpi rate = kpiMap(provider.build(context())).get("online-rate");

        assertThat(rate.tone()).isEqualTo(OverviewTone.FLAT);
        assertThat(rate.hint()).isEqualTo("尚无启用设备");
    }

    @Test
    @DisplayName("设备总数与在线数：总数含停用，牌面提示写出启用数")
    void shouldExposeDeviceCounts() {
        when(statsService.snapshot(any(LocalDateTime.class)))
                .thenReturn(stats(12L, 10L, 9L, 500L, 0L, 3L, 3L));

        Map<String, OverviewKpi> kpis = kpiMap(provider.build(context()));

        assertThat(kpis.get("devices").value()).isEqualTo(12d);
        assertThat(kpis.get("devices").hint()).isEqualTo("启用 10 台");
        assertThat(kpis.get("telemetry-24h").value()).isEqualTo(500d);
        assertThat(kpis.get("telemetry-24h").tone()).isEqualTo(OverviewTone.GOOD);
    }

    @Test
    @DisplayName("离线设备：牌面不出，告警条按台数合并成一条 WARNING")
    void shouldWarnOnOfflineDevices() {
        when(statsService.snapshot(any(LocalDateTime.class)))
                .thenReturn(stats(12L, 10L, 7L, 500L, 0L, 3L, 3L));

        OverviewSnapshot snapshot = provider.build(context());

        assertThat(snapshot.alerts())
                .filteredOn(alert -> alert.level() == OverviewAlertLevel.WARNING)
                .singleElement()
                .satisfies(alert -> {
                    assertThat(alert.text()).contains("3 台启用设备不在线");
                    assertThat(alert.count()).isEqualTo(3);
                });
    }

    @Test
    @DisplayName("待回指令：牌面转黄灯，并单独出一条告警（含 count）")
    void shouldWarnOnPendingCommands() {
        when(statsService.snapshot(any(LocalDateTime.class)))
                .thenReturn(stats(12L, 10L, 10L, 500L, 6L, 3L, 3L));

        OverviewSnapshot snapshot = provider.build(context());

        assertThat(kpiMap(snapshot).get("pending-cmd").value()).isEqualTo(6d);
        assertThat(kpiMap(snapshot).get("pending-cmd").tone()).isEqualTo(OverviewTone.WARN);
        assertThat(snapshot.alerts()).extracting(OverviewAlert::text)
                .anyMatch(text -> text.contains("6 条指令已发出但未收到回执"));
    }

    @Test
    @DisplayName("上报断流：有启用设备但近 24h 零上报，必须出告警——0 条与没人用长得一样")
    void shouldWarnWhenTelemetryDried() {
        when(statsService.snapshot(any(LocalDateTime.class)))
                .thenReturn(stats(12L, 10L, 10L, 0L, 0L, 3L, 3L));

        OverviewSnapshot snapshot = provider.build(context());

        assertThat(kpiMap(snapshot).get("telemetry-24h").tone()).isEqualTo(OverviewTone.FLAT);
        assertThat(snapshot.alerts()).extracting(OverviewAlert::text)
                .anyMatch(text -> text.contains("近 24 小时没有任何遥测上报"));
    }

    @Test
    @DisplayName("规则全停：出 INFO 告警，说明遥测不再触发告警")
    void shouldNoticeAllRulesDisabled() {
        when(statsService.snapshot(any(LocalDateTime.class)))
                .thenReturn(stats(12L, 10L, 10L, 500L, 0L, 4L, 0L));

        assertThat(provider.build(context()).alerts())
                .filteredOn(alert -> alert.level() == OverviewAlertLevel.INFO)
                .anySatisfy(alert -> assertThat(alert.text()).contains("4 条规则都已停用"));
    }

    @Test
    @DisplayName("趋势图：折线（7 根横条会把台账挤到只剩 3 行可见）、横轴 7 天、序列与横轴等长")
    void shouldChartSevenDayTrend() {
        when(statsService.snapshot(any(LocalDateTime.class)))
                .thenReturn(stats(12L, 10L, 10L, 500L, 0L, 3L, 3L));

        OverviewChart chart = provider.build(context()).charts().get(0);

        assertThat(chart.type()).isEqualTo("line");
        assertThat(chart.format()).isEqualTo(OverviewFormat.COUNT);
        assertThat(chart.categories()).hasSize(7);
        assertThat(chart.categories()).first().isEqualTo("11-01");
        assertThat(chart.categories()).last().isEqualTo("11-07");
        assertThat(chart.series()).singleElement()
                .satisfies(series -> assertThat(series.values()).hasSize(7));
        assertThat(chart.isAligned()).isTrue();
    }

    @Test
    @DisplayName("入口：设备台账，权限码复用 2D 设备管理")
    void shouldLinkDevicePage() {
        when(statsService.snapshot(any(LocalDateTime.class)))
                .thenReturn(stats(12L, 10L, 10L, 500L, 0L, 3L, 3L));

        List<OverviewLink> links = provider.build(context()).links();

        assertThat(links).singleElement().satisfies(link -> {
            assertThat(link.path()).isEqualTo("/iot/v1/devices/page");
            assertThat(link.permission()).isEqualTo("sys:iot:device");
        });
    }

    @Test
    @DisplayName("数据库异常：必须往上抛，由 SDK 降级成 stale/degraded，不能吞成 0 台设备")
    void shouldPropagateFailure() {
        when(statsService.snapshot(any(LocalDateTime.class)))
                .thenThrow(new IllegalStateException("db down"));

        assertThatThrownBy(() -> provider.build(context()))
                .isInstanceOf(IllegalStateException.class);
    }
}
