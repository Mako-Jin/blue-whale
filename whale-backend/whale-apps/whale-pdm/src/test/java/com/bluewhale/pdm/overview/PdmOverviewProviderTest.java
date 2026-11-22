package com.bluewhale.pdm.overview;

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
 * {@link PdmOverviewProvider} 单元测试：四张牌的口径提示、变更单语气、
 * 类型分布柱图、告警分条。
 *
 * @author BlueWhale
 */
class PdmOverviewProviderTest {

    private final PdmOverviewStatsService statsService = mock(PdmOverviewStatsService.class);

    private final PdmOverviewProvider provider = new PdmOverviewProvider(statsService);

    /**
     * 构造一份将作账本。
     *
     * @param partEnabled   在册物料
     * @param partDisabled  停用物料
     * @param bomReleased   生效 BOM
     * @param bomDraft      草稿 BOM
     * @param bomTotal      BOM 总版数
     * @param chgOpen       未关闭变更
     * @param chgAssessing  评估/审批中变更
     * @param chgExecuting  执行中变更
     * @param docReleased   已发布图纸
     * @param docDraft      草稿图纸
     * @param docCheckedOut 借出未还图纸
     * @return 统计快照
     */
    private static PdmOverviewStats stats(long partEnabled, long partDisabled,
            long bomReleased, long bomDraft, long bomTotal,
            long chgOpen, long chgAssessing, long chgExecuting,
            long docReleased, long docDraft, long docCheckedOut) {
        return new PdmOverviewStats(partEnabled, partDisabled,
                List.of(new PdmOverviewStats.TypeCount("成品", 30),
                        new PdmOverviewStats.TypeCount("部件", 20),
                        new PdmOverviewStats.TypeCount("零件", 40),
                        new PdmOverviewStats.TypeCount("原材料", 30)),
                bomReleased, bomDraft, bomTotal,
                chgOpen, chgAssessing, chgExecuting,
                docReleased, docDraft, docCheckedOut);
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
    @DisplayName("元信息与权限码：复用 2D 物料台账同一权限码，不为 3D 单独发授权")
    void shouldExposeMetadata() {
        assertThat(provider.service()).isEqualTo("pdm");
        assertThat(provider.nodeKey()).isEqualTo("jiangzuo-bureau");
        assertThat(provider.title()).isEqualTo("将作");
        assertThat(provider.subtitle()).isEqualTo("产品数据");
        assertThat(provider.permissionCode()).isEqualTo("sys:pdm:part:list");
    }

    @Test
    @DisplayName("四张牌：各自带口径提示，未关闭变更拆评估/审批与执行中两段")
    void shouldBuildFourKpisWithHints() {
        when(statsService.snapshot()).thenReturn(stats(120L, 8L, 12L, 5L, 40L, 7L, 3L, 2L, 25L, 6L, 2L));

        Map<String, OverviewKpi> kpis = kpiMap(provider.build(null));

        assertThat(kpis.get("parts").value()).isEqualTo(120d);
        assertThat(kpis.get("parts").hint()).isEqualTo("停用 8 件");
        assertThat(kpis.get("boms").hint()).isEqualTo("草稿 5 · 共 40 版");
        assertThat(kpis.get("chg-open").value()).isEqualTo(7d);
        assertThat(kpis.get("chg-open").hint()).isEqualTo("评估/审批 3 · 执行中 2");
        assertThat(kpis.get("docs").hint()).isEqualTo("草稿 6 · 借出 2");
    }

    @Test
    @DisplayName("变更语气：有单压着给黄灯，清空了给绿灯")
    void shouldToneOpenChanges() {
        when(statsService.snapshot()).thenReturn(stats(120L, 8L, 12L, 5L, 40L, 7L, 3L, 2L, 25L, 6L, 2L));
        assertThat(kpiMap(provider.build(null)).get("chg-open").tone()).isEqualTo(OverviewTone.WARN);

        when(statsService.snapshot()).thenReturn(stats(120L, 8L, 12L, 0L, 40L, 0L, 0L, 0L, 25L, 6L, 2L));
        assertThat(kpiMap(provider.build(null)).get("chg-open").tone()).isEqualTo(OverviewTone.GOOD);
        assertThat(kpiMap(provider.build(null)).get("chg-open").hint()).isEqualTo("没有压着的变更单");
    }

    @Test
    @DisplayName("类型分布柱图：四种类型按固定顺序，格式是计数")
    void shouldChartTypeDistribution() {
        when(statsService.snapshot()).thenReturn(stats(120L, 8L, 12L, 5L, 40L, 7L, 3L, 2L, 25L, 6L, 2L));

        OverviewChart chart = provider.build(null).charts().get(0);

        assertThat(chart.type()).isEqualTo("bar");
        assertThat(chart.format()).isEqualTo(OverviewFormat.COUNT);
        assertThat(chart.categories()).containsExactly("成品", "部件", "零件", "原材料");
        assertThat(chart.series()).hasSize(1);
        assertThat(chart.series().get(0).values()).containsExactly(30d, 20d, 40d, 30d);
    }

    @Test
    @DisplayName("入口：物料台账，权限码复用 2D 物料列表")
    void shouldLinkPartPage() {
        when(statsService.snapshot()).thenReturn(stats(120L, 8L, 12L, 5L, 40L, 7L, 3L, 2L, 25L, 6L, 2L));

        List<OverviewLink> links = provider.build(null).links();

        assertThat(links).singleElement().satisfies(link -> {
            assertThat(link.path()).isEqualTo("/pdm/v1/part/page");
            assertThat(link.permission()).isEqualTo("sys:pdm:part:list");
        });
    }

    @Test
    @DisplayName("告警：待批变更与图纸借出是黄灯，执行中与草稿 BOM 是说明")
    void shouldSeparateWarningsFromInfo() {
        when(statsService.snapshot()).thenReturn(stats(120L, 8L, 12L, 5L, 40L, 7L, 3L, 2L, 25L, 6L, 2L));

        OverviewSnapshot snapshot = provider.build(null);

        List<String> warnings = snapshot.alerts().stream()
                .filter(alert -> alert.level() == OverviewAlertLevel.WARNING)
                .map(OverviewAlert::text)
                .toList();
        List<String> infos = snapshot.alerts().stream()
                .filter(alert -> alert.level() == OverviewAlertLevel.INFO)
                .map(OverviewAlert::text)
                .toList();
        assertThat(warnings).anyMatch(text -> text.contains("3 张变更单卡在评估或审批"))
                .anyMatch(text -> text.contains("2 份图纸被检出未还"));
        assertThat(infos).anyMatch(text -> text.contains("2 张变更单正在执行"))
                .anyMatch(text -> text.contains("5 份 BOM 草稿"));
    }

    @Test
    @DisplayName("有物料但没有生效 BOM：单独报警，料单没人核对比没物料更危险")
    void shouldWarnWhenPartsExistWithoutReleasedBom() {
        when(statsService.snapshot()).thenReturn(stats(120L, 8L, 0L, 5L, 8L, 0L, 0L, 0L, 25L, 6L, 0L));

        List<String> texts = provider.build(null).alerts().stream()
                .map(OverviewAlert::text)
                .toList();

        assertThat(texts).anyMatch(text -> text.contains("没有任何生效 BOM"));
    }

    @Test
    @DisplayName("没有任何物料：四张牌都空但只提示无账可理，不误报变更积压")
    void shouldNotBlameEmptyLedgers() {
        when(statsService.snapshot()).thenReturn(stats(0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L));

        OverviewSnapshot snapshot = provider.build(null);

        assertThat(kpiMap(snapshot).get("parts").hint()).isEqualTo("还没有物料主数据");
        assertThat(snapshot.alerts())
                .filteredOn(alert -> alert.level() == OverviewAlertLevel.WARNING)
                .isEmpty();
        assertThat(snapshot.alerts()).extracting(OverviewAlert::text)
                .anyMatch(text -> text.contains("无账可理"));
    }
}
