package com.bluewhale.monitor.overview;

import com.bluewhale.overview.model.OverviewSnapshot;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link HubProbe} 主指标排版测试：无快照、空指标列表与有指标三种口径。
 *
 * @author BlueWhale
 */
class HubProbeTest {

    private static OverviewSnapshot snapshot(List<com.bluewhale.overview.model.OverviewKpi> kpis) {
        return new OverviewSnapshot("erp", "hubu-hall", "户部", "经营驾驶舱", "2026-11-21T03:00:00+08:00",
                com.bluewhale.overview.model.OverviewState.LIVE, kpis, List.of(), List.of(), List.of(), List.of());
    }

    @Test
    @DisplayName("无快照：主指标显示破折号，而不是抛空指针")
    void shouldReturnDashWhenSnapshotMissing() {
        HubProbe probe = new HubProbe("hubu-hall", "户部", HubHallState.DEGRADED, "问询超时", null);

        assertThat(probe.firstKpi()).isNull();
        assertThat(probe.headline()).isEqualTo("—");
    }

    @Test
    @DisplayName("指标列表为 null：同样按无数据处理")
    void shouldReturnDashWhenKpisNull() {
        HubProbe probe = new HubProbe("hubu-hall", "户部", HubHallState.DEGRADED, "空响应", snapshot(null));

        assertThat(probe.firstKpi()).isNull();
        assertThat(probe.headline()).isEqualTo("—");
    }

    @Test
    @DisplayName("指标列表为空：主指标显示破折号")
    void shouldReturnDashWhenKpisEmpty() {
        HubProbe probe = new HubProbe("hubu-hall", "户部", HubHallState.LIVE, "实时", snapshot(List.of()));

        assertThat(probe.firstKpi()).isNull();
        assertThat(probe.headline()).isEqualTo("—");
    }

    @Test
    @DisplayName("有指标：取第一条并按契约口径排版（金额 ¥ + 千分位）")
    void shouldFormatFirstKpi() {
        OverviewSnapshot snapshot = snapshot(List.of(
                com.bluewhale.overview.support.Overviews.money("revenue", "营收", 1234),
                com.bluewhale.overview.support.Overviews.count("orders", "订单", 88)));

        HubProbe probe = new HubProbe("hubu-hall", "户部", HubHallState.LIVE, "实时", snapshot);

        assertThat(probe.firstKpi().key()).isEqualTo("revenue");
        assertThat(probe.headline()).isEqualTo("营收 ¥1,234");
    }
}
