package com.bluewhale.contract.overview;

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
import static org.mockito.Mockito.when;

/**
 * {@link CtOverviewProvider} 单元测试：四张牌的口径提示、到期与过期语气、
 * 金额分布柱图、告警分条。
 *
 * @author BlueWhale
 */
class CtOverviewProviderTest {

    private final CtOverviewStatsService statsService = mock(CtOverviewStatsService.class);

    private final CtOverviewProvider provider = new CtOverviewProvider(statsService);

    /**
     * 构造一份盟府账本。
     *
     * @param total           合同总数
     * @param effective       生效中
     * @param inFlight        在途
     * @param approving       审批中
     * @param signing         签署中
     * @param draft           起草未提交
     * @param rejectWithdrawn 驳回撤回
     * @param archived        已归档
     * @param terminated      已终止
     * @param expiringSoon    30 天内到期
     * @param expiredActive   已过期仍生效
     * @return 统计快照
     */
    private static CtOverviewStats stats(long total, long effective, long inFlight,
            long approving, long signing, long draft, long rejectWithdrawn,
            long archived, long terminated, long expiringSoon, long expiredActive) {
        return new CtOverviewStats(total, effective, inFlight, approving, signing,
                draft, rejectWithdrawn, archived, terminated, expiringSoon, expiredActive,
                List.of(new CtOverviewStats.CategoryAmount("采购", 1500),
                        new CtOverviewStats.CategoryAmount("销售", 2000),
                        new CtOverviewStats.CategoryAmount("服务", 300),
                        new CtOverviewStats.CategoryAmount("租赁", 0),
                        new CtOverviewStats.CategoryAmount("其他", 0)),
                1L);
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
    @DisplayName("元信息与权限码：复用 2D 合同台账同一权限码，不为 3D 单独发授权")
    void shouldExposeMetadata() {
        assertThat(provider.service()).isEqualTo("contract");
        assertThat(provider.nodeKey()).isEqualTo("mengfu-hall");
        assertThat(provider.title()).isEqualTo("盟府");
        assertThat(provider.subtitle()).isEqualTo("合同印章");
        assertThat(provider.permissionCode()).isEqualTo("sys:contract:list");
    }

    @Test
    @DisplayName("四张牌：各自带口径提示，在途拆审批与签署两段")
    void shouldBuildFourKpisWithHints() {
        when(statsService.snapshot()).thenReturn(stats(100L, 40L, 26L, 5L, 6L, 3L, 3L, 30L, 5L, 6L, 2L));

        Map<String, OverviewKpi> kpis = kpiMap(provider.build(null));

        assertThat(kpis.get("effective").value()).isEqualTo(40d);
        assertThat(kpis.get("effective").hint()).isEqualTo("已归档 30 · 已终止 5");
        assertThat(kpis.get("in-flight").value()).isEqualTo(26d);
        assertThat(kpis.get("in-flight").hint()).isEqualTo("审批中 5 · 签署中 6");
        assertThat(kpis.get("expiring").value()).isEqualTo(6d);
        assertThat(kpis.get("expiring").hint()).isEqualTo("30 天续签窗口，提前谈");
        assertThat(kpis.get("expired-active").value()).isEqualTo(2d);
        assertThat(kpis.get("expired-active").hint()).isEqualTo("要么续签要么终止，不能悬着");
    }

    @Test
    @DisplayName("到期语气：有合同快到期给黄灯；过期仍生效给红灯，清空了给绿灯")
    void shouldToneExpiringAndExpired() {
        when(statsService.snapshot()).thenReturn(stats(100L, 40L, 26L, 5L, 6L, 3L, 3L, 30L, 5L, 6L, 2L));
        assertThat(kpiMap(provider.build(null)).get("expiring").tone()).isEqualTo(OverviewTone.WARN);
        assertThat(kpiMap(provider.build(null)).get("expired-active").tone()).isEqualTo(OverviewTone.BAD);

        when(statsService.snapshot()).thenReturn(stats(100L, 40L, 0L, 0L, 0L, 3L, 3L, 30L, 5L, 0L, 0L));
        assertThat(kpiMap(provider.build(null)).get("expiring").tone()).isEqualTo(OverviewTone.FLAT);
        assertThat(kpiMap(provider.build(null)).get("expired-active").tone()).isEqualTo(OverviewTone.GOOD);
        assertThat(kpiMap(provider.build(null)).get("expired-active").hint()).isEqualTo("没有悬着的过期合同");
    }

    @Test
    @DisplayName("在途为 0：提示起草未提交；没有起草就说没有在途合同")
    void shouldHintIdleInFlight() {
        when(statsService.snapshot()).thenReturn(stats(100L, 40L, 0L, 0L, 0L, 3L, 3L, 30L, 5L, 0L, 0L));
        assertThat(kpiMap(provider.build(null)).get("in-flight").hint()).isEqualTo("有 3 份起草未提交");

        when(statsService.snapshot()).thenReturn(stats(0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L));
        assertThat(kpiMap(provider.build(null)).get("in-flight").hint()).isEqualTo("没有在途合同");
    }

    @Test
    @DisplayName("金额分布柱图：五个类别按固定顺序，格式是金额")
    void shouldChartMoneyDistribution() {
        when(statsService.snapshot()).thenReturn(stats(100L, 40L, 26L, 5L, 6L, 3L, 3L, 30L, 5L, 6L, 2L));

        OverviewChart chart = provider.build(null).charts().get(0);

        assertThat(chart.type()).isEqualTo("bar");
        assertThat(chart.format()).isEqualTo(OverviewFormat.MONEY);
        assertThat(chart.categories()).containsExactly("采购", "销售", "服务", "租赁", "其他");
        assertThat(chart.series()).hasSize(1);
        assertThat(chart.series().get(0).values()).containsExactly(1500d, 2000d, 300d, 0d, 0d);
    }

    @Test
    @DisplayName("入口：合同台账，权限码复用 2D 合同列表")
    void shouldLinkContractPage() {
        when(statsService.snapshot()).thenReturn(stats(100L, 40L, 26L, 5L, 6L, 3L, 3L, 30L, 5L, 6L, 2L));

        List<OverviewLink> links = provider.build(null).links();

        assertThat(links).singleElement().satisfies(link -> {
            assertThat(link.path()).isEqualTo("/contract/v1/page");
            assertThat(link.permission()).isEqualTo("sys:contract:list");
        });
    }

    @Test
    @DisplayName("告警：过期是红灯、即将到期是黄灯，外币/起草/驳回是说明")
    void shouldSeparateWarningsFromInfo() {
        when(statsService.snapshot()).thenReturn(stats(100L, 40L, 26L, 5L, 6L, 3L, 3L, 30L, 5L, 6L, 2L));

        OverviewSnapshot snapshot = provider.build(null);

        List<String> criticals = snapshot.alerts().stream()
                .filter(alert -> alert.level() == OverviewAlertLevel.CRITICAL)
                .map(OverviewAlert::text)
                .toList();
        List<String> warnings = snapshot.alerts().stream()
                .filter(alert -> alert.level() == OverviewAlertLevel.WARNING)
                .map(OverviewAlert::text)
                .toList();
        List<String> infos = snapshot.alerts().stream()
                .filter(alert -> alert.level() == OverviewAlertLevel.INFO)
                .map(OverviewAlert::text)
                .toList();
        assertThat(criticals).anyMatch(text -> text.contains("2 份合同已过期仍挂着生效"));
        assertThat(warnings).anyMatch(text -> text.contains("6 份合同 30 天内到期"));
        assertThat(infos).anyMatch(text -> text.contains("1 份外币生效合同未计入金额图"))
                .anyMatch(text -> text.contains("3 份合同起草后一直没提交"))
                .anyMatch(text -> text.contains("3 份合同被驳回或撤回"));
    }

    @Test
    @DisplayName("没有任何合同：只提示无账可理，不误报过期与到期")
    void shouldNotBlameEmptyLedgers() {
        when(statsService.snapshot()).thenReturn(stats(0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L));

        OverviewSnapshot snapshot = provider.build(null);

        assertThat(kpiMap(snapshot).get("effective").hint()).isEqualTo("还没有登记合同");
        assertThat(snapshot.alerts())
                .filteredOn(alert -> alert.level() == OverviewAlertLevel.WARNING
                        || alert.level() == OverviewAlertLevel.CRITICAL)
                .isEmpty();
        assertThat(snapshot.alerts()).extracting(OverviewAlert::text)
                .anyMatch(text -> text.contains("无账可理"));
    }
}
