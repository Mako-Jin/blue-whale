package com.bluewhale.monitor.overview;

import com.bluewhale.monitor.vo.HubHallDetailVO;
import com.bluewhale.monitor.vo.HubHallRowVO;
import com.bluewhale.overview.model.OverviewAlert;
import com.bluewhale.overview.model.OverviewAlertLevel;
import com.bluewhale.overview.model.OverviewKpi;
import com.bluewhale.overview.model.OverviewSnapshot;
import com.bluewhale.overview.model.OverviewState;
import com.bluewhale.overview.support.Overviews;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link HubAggregate} 明细映射测试：降级殿、告警拼接、空指标与坏时间字段。
 *
 * <p>这些分支在 happy path 里走不到，却正是值班时最常看到的情况 —— 下游挂了、
 * 字段缺了、时间格式坏了，屏风页仍要能画出来。</p>
 *
 * @author BlueWhale
 */
class HubAggregateTest {

    /**
     * 造一份下游快照
     *
     * @param generatedAt 数据时刻，可空/空白/非法
     * @param kpis 指标，可空
     * @param alerts 告警，可空
     * @param notes 说明，可空
     * @return 快照
     */
    private static OverviewSnapshot snapshot(String generatedAt, List<OverviewKpi> kpis,
                                             List<OverviewAlert> alerts, List<String> notes) {
        return new OverviewSnapshot("erp", "hubu-hall", "户部", "经营驾驶舱 · 本月", generatedAt,
                OverviewState.LIVE, kpis, List.of(), alerts, List.of(), notes);
    }

    /**
     * 造一次聚合结果
     *
     * @param probes 逐殿结果
     * @return 聚合结果
     */
    private static HubAggregate aggregate(HubProbe... probes) {
        OverviewSnapshot summary = OverviewSnapshot.empty("monitor", "shushu-hall", "数枢殿", "总控", null, null);
        return new HubAggregate(summary, List.of(probes));
    }

    private static OverviewKpi oneKpi() {
        return Overviews.count("orders", "订单", 88);
    }

    @Test
    @DisplayName("未通殿详情：无子标题、无指标行、说明用降级原文、数据时刻为空")
    void shouldRenderDegradedDetail() {
        HubAggregate subject = aggregate(
                new HubProbe("gongbu-hall", "工部", HubHallState.DEGRADED, "问询超时", null));

        HubHallDetailVO detail = subject.detail("gongbu-hall");

        assertThat(detail.subtitle()).isNull();
        assertThat(detail.updatedLabel()).isEmpty();
        assertThat(detail.note()).isEqualTo("问询超时");
        assertThat(detail.lines()).isEmpty();
        assertThat(subject.rows().get(0).headline()).isEqualTo("—");
        assertThat(subject.rows().get(0).updatedLabel()).isEmpty();
    }

    @Test
    @DisplayName("下游未给 alerts：说明不追加空壳分隔符，直接返回原文")
    void shouldHandleNullAlerts() {
        OverviewSnapshot snapshot = snapshot("2026-11-21T03:00:00+08:00", List.of(oneKpi()), null, List.of("口径：演示"));
        HubAggregate subject = aggregate(
                new HubProbe("hubu-hall", "户部", HubHallState.LIVE, "实时", snapshot));

        HubHallDetailVO detail = subject.detail("hubu-hall");

        assertThat(detail.note()).isEqualTo("实时");
        assertThat(detail.updatedLabel()).isEqualTo("11-21 03:00");
    }

    @Test
    @DisplayName("下游告警：count 为 null/1 时只写文案，大于 1 时追加 ×N")
    void shouldAppendAlertCount() {
        List<OverviewAlert> alerts = List.of(
                new OverviewAlert(OverviewAlertLevel.WARNING, "订单超期", null, null),
                new OverviewAlert(OverviewAlertLevel.WARNING, "库存不足", 1, null),
                new OverviewAlert(OverviewAlertLevel.CRITICAL, "支付失败", 3, null));
        OverviewSnapshot snapshot = snapshot("2026-11-21T03:00:00+08:00", List.of(oneKpi()), alerts, List.of());
        HubAggregate subject = aggregate(
                new HubProbe("hubu-hall", "户部", HubHallState.LIVE, "实时", snapshot));

        HubHallDetailVO detail = subject.detail("hubu-hall");

        assertThat(detail.note()).isEqualTo("实时 · 订单超期；库存不足；支付失败 ×3");
    }

    @Test
    @DisplayName("下游 kpis 为 null：详情指标行为空，表格行仍可展示")
    void shouldHandleNullKpis() {
        OverviewSnapshot snapshot = snapshot("2026-11-21T03:00:00+08:00", null, List.of(), List.of());
        HubAggregate subject = aggregate(
                new HubProbe("hubu-hall", "户部", HubHallState.STALE, "陈旧", snapshot));

        HubHallDetailVO detail = subject.detail("hubu-hall");

        assertThat(detail.lines()).isEmpty();
        assertThat(subject.rows().get(0).headline()).isEqualTo("—");
    }

    @Test
    @DisplayName("数据时刻为 null/空白/非法：空串兜底，坏值原样返回，不让整行消失")
    void shouldFallbackForBadGeneratedAt() {
        HubAggregate subject = aggregate(
                new HubProbe("a", "甲殿", HubHallState.LIVE, "实时",
                        snapshot(null, List.of(oneKpi()), List.of(), List.of())),
                new HubProbe("b", "乙殿", HubHallState.LIVE, "实时",
                        snapshot("   ", List.of(oneKpi()), List.of(), List.of())),
                new HubProbe("c", "丙殿", HubHallState.LIVE, "实时",
                        snapshot("not-a-date", List.of(oneKpi()), List.of(), List.of())));

        assertThat(subject.rows()).extracting(HubHallRowVO::updatedLabel)
                .containsExactly("", "", "not-a-date");
    }

    @Test
    @DisplayName("节点不在册：详情返回 null")
    void shouldReturnNullForUnknownNode() {
        HubAggregate subject = aggregate(new HubProbe("hubu-hall", "户部", HubHallState.LIVE, "实时",
                snapshot("2026-11-21T03:00:00+08:00", List.of(oneKpi()), List.of(), List.of())));

        assertThat(subject.detail("nope")).isNull();
    }
}
