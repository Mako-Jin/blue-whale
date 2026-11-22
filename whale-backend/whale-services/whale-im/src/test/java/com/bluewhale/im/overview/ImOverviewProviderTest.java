package com.bluewhale.im.overview;

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

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link ImOverviewProvider} 单元测试：四张牌的口径提示、两张欠账牌的语气、
 * 近七日消息柱图、单入口与权限码、告警分条、空库不误报。
 *
 * @author BlueWhale
 */
class ImOverviewProviderTest {

    /** 柱图用的七天：与快照里 recentDays 对齐 */
    private static final List<LocalDate> DAYS = List.of(
            LocalDate.of(2026, 11, 17), LocalDate.of(2026, 11, 18), LocalDate.of(2026, 11, 19),
            LocalDate.of(2026, 11, 20), LocalDate.of(2026, 11, 21), LocalDate.of(2026, 11, 22),
            LocalDate.of(2026, 11, 23));

    private final ImOverviewStatsService statsService = mock(ImOverviewStatsService.class);

    private final ImOverviewProvider provider = new ImOverviewProvider(statsService);

    /**
     * 构造一份传信司账本。
     *
     * @param total 会话总数
     * @param single 单聊
     * @param group 群聊
     * @param bot 机器人会话
     * @param activeToday 今日活跃会话
     * @param idle7d 静默会话
     * @param messageToday 今日消息
     * @param message7d 近七日消息
     * @param botEnabled 启用绑定
     * @param pendingConfirm 待确认运行
     * @param runFailed7d 近七日失败运行
     * @param daily 逐日消息
     * @return 统计快照
     */
    private static ImOverviewStats stats(long total, long single, long group, long bot, long activeToday,
            long idle7d, long messageToday, long message7d, long botEnabled, long pendingConfirm,
            long runFailed7d, long... daily) {
        List<ImOverviewStats.DailyCount> days = new ArrayList<>(DAYS.size());
        for (int i = 0; i < DAYS.size(); i++) {
            days.add(new ImOverviewStats.DailyCount(DAYS.get(i), i < daily.length ? daily[i] : 0L));
        }
        return new ImOverviewStats(total, single, group, bot, activeToday, idle7d,
                messageToday, message7d, botEnabled, pendingConfirm, runFailed7d, days);
    }

    /**
     * 一屏"平安无事"的账本：12 个会话、今天 40 条、没有静默也没有待确认。
     *
     * @return 统计快照
     */
    private static ImOverviewStats quiet() {
        return stats(12L, 8L, 2L, 2L, 3L, 0L, 40L, 210L, 2L, 0L, 0L, 20L, 30L, 25L, 35L, 40L, 20L, 40L);
    }

    /**
     * 按 key 索引指标。
     *
     * @param snapshot 快照
     * @return key → 指标
     */
    private static Map<String, OverviewKpi> kpiMap(OverviewSnapshot snapshot) {
        return snapshot.kpis().stream().collect(Collectors.toMap(OverviewKpi::key, kpi -> kpi));
    }

    @Test
    @DisplayName("元信息与权限码：IM 全类只有一个码，不臆造 browse / manage")
    void shouldExposeMetadata() {
        assertThat(provider.service()).isEqualTo("im");
        assertThat(provider.nodeKey()).isEqualTo("chuanxin-stage");
        assertThat(provider.title()).isEqualTo("传信司");
        assertThat(provider.subtitle()).isEqualTo("即时传信");
        assertThat(provider.permissionCode()).isEqualTo("sys:im:chat");
    }

    @Test
    @DisplayName("租户 ID 从上下文透传给统计服务")
    void shouldPassTenantIdThrough() {
        when(statsService.snapshot(9001L)).thenReturn(quiet());

        OverviewSnapshot snapshot = provider.build(OverviewContext.of(7L, 9001L, Set.of()));

        assertThat(snapshot.service()).isEqualTo("im");
        assertThat(snapshot.nodeKey()).isEqualTo("chuanxin-stage");
        assertThat(snapshot.subtitle()).contains("会话与驿路");
        assertThat(snapshot.notes()).anySatisfy(note -> assertThat(note)
                .contains("七日")
                .contains("撤回"));
    }

    @Test
    @DisplayName("上下文为空时不编租户：统计服务收到 null 并自行抛（宁可降级不串数）")
    void shouldPassNullTenantWhenContextMissing() {
        when(statsService.snapshot(null)).thenThrow(new IllegalArgumentException("tenantId is required"));

        assertThatThrownBy(() -> provider.build(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("四张牌口径正确：会话只报存量，今日消息跟数量走语气")
    void shouldBuildFourKpis() {
        when(statsService.snapshot(9001L)).thenReturn(quiet());

        Map<String, OverviewKpi> kpis = kpiMap(provider.build(OverviewContext.of(7L, 9001L, Set.of())));

        assertThat(kpis).containsOnlyKeys("conversations", "messages-today", "silent", "pending-confirm");
        assertThat(kpis.get("conversations").value()).isEqualTo(12.0);
        assertThat(kpis.get("conversations").tone()).isEqualTo(OverviewTone.FLAT);
        assertThat(kpis.get("conversations").hint()).isEqualTo("单聊 8 · 机器人 2");
        assertThat(kpis.get("messages-today").value()).isEqualTo(40.0);
        assertThat(kpis.get("messages-today").tone()).isEqualTo(OverviewTone.GOOD);
        assertThat(kpis.get("messages-today").hint()).isEqualTo("近七日 210 条");
        assertThat(kpis.get("silent").value()).isZero();
        assertThat(kpis.get("pending-confirm").value()).isZero();
    }

    @Test
    @DisplayName("两张欠账牌有账就黄，没账就绿；今天 0 条只报平色，凌晨不该是黄灯")
    void shouldToneDebtKpis() {
        when(statsService.snapshot(9001L)).thenReturn(stats(12L, 8L, 2L, 2L, 0L, 3L, 0L, 210L, 2L, 2L, 1L));

        Map<String, OverviewKpi> dirty = kpiMap(provider.build(OverviewContext.of(7L, 9001L, Set.of())));

        assertThat(dirty.get("silent").value()).isEqualTo(3.0);
        assertThat(dirty.get("silent").tone()).isEqualTo(OverviewTone.WARN);
        assertThat(dirty.get("pending-confirm").value()).isEqualTo(2.0);
        assertThat(dirty.get("pending-confirm").tone()).isEqualTo(OverviewTone.WARN);
        assertThat(dirty.get("messages-today").tone()).isEqualTo(OverviewTone.FLAT);

        when(statsService.snapshot(9001L)).thenReturn(quiet());
        Map<String, OverviewKpi> clean = kpiMap(provider.build(OverviewContext.of(7L, 9001L, Set.of())));
        assertThat(clean.get("silent").tone()).isEqualTo(OverviewTone.GOOD);
        assertThat(clean.get("pending-confirm").tone()).isEqualTo(OverviewTone.GOOD);
    }

    @Test
    @DisplayName("会话总数为 0 时说'还没有会话'，不显示'单聊 0 · 机器人 0'")
    void shouldSpeakPlainlyWhenEmpty() {
        when(statsService.snapshot(9001L)).thenReturn(stats(0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L));

        Map<String, OverviewKpi> kpis = kpiMap(provider.build(OverviewContext.of(7L, 9001L, Set.of())));

        assertThat(kpis.get("conversations").hint()).isEqualTo("还没有会话");
    }

    @Test
    @DisplayName("近七日消息柱图：七根柱子按日期升序，标签是月-日")
    void shouldBuildTrendChart() {
        when(statsService.snapshot(9001L)).thenReturn(quiet());

        List<OverviewChart> charts = provider.build(OverviewContext.of(7L, 9001L, Set.of())).charts();

        assertThat(charts).hasSize(1);
        OverviewChart chart = charts.get(0);
        assertThat(chart.key()).isEqualTo("message-trend");
        assertThat(chart.type()).isEqualTo("bar");
        assertThat(chart.format()).isEqualTo(OverviewFormat.COUNT);
        assertThat(chart.categories()).containsExactly("11-17", "11-18", "11-19", "11-20", "11-21", "11-22", "11-23");
        assertThat(chart.series().get(0).values()).containsExactly(20.0, 30.0, 25.0, 35.0, 40.0, 20.0, 40.0);
    }

    @Test
    @DisplayName("只摆一个入口：会话列表，权限码 sys:im:chat")
    void shouldLinkConversationListOnly() {
        when(statsService.snapshot(9001L)).thenReturn(quiet());

        List<OverviewLink> links = provider.build(OverviewContext.of(7L, 9001L, Set.of())).links();

        assertThat(links).hasSize(1);
        assertThat(links.get(0).key()).isEqualTo("conversations");
        assertThat(links.get(0).path()).isEqualTo("/im/v1/conversations");
        assertThat(links.get(0).permission()).isEqualTo("sys:im:chat");
    }

    @Test
    @DisplayName("告警分条：待确认与失败黄，静默只是提示")
    void shouldSplitAlerts() {
        when(statsService.snapshot(9001L)).thenReturn(stats(12L, 8L, 2L, 2L, 3L, 3L, 40L, 210L, 2L, 2L, 1L));

        List<OverviewAlert> alerts = provider.build(OverviewContext.of(7L, 9001L, Set.of())).alerts();

        assertThat(alerts).filteredOn(alert -> alert.text().contains("等人工确认"))
                .singleElement()
                .satisfies(alert -> {
                    assertThat(alert.level()).isEqualTo(OverviewAlertLevel.WARNING);
                    assertThat(alert.text()).contains("2 次");
                });
        assertThat(alerts).filteredOn(alert -> alert.text().contains("智能体运行失败"))
                .singleElement()
                .satisfies(alert -> {
                    assertThat(alert.level()).isEqualTo(OverviewAlertLevel.WARNING);
                    assertThat(alert.text()).contains("1 次");
                });
        assertThat(alerts).filteredOn(alert -> alert.text().contains("七日没有新消息"))
                .singleElement()
                .satisfies(alert -> assertThat(alert.level()).isEqualTo(OverviewAlertLevel.INFO));
    }

    @Test
    @DisplayName("平安无事时不发告警：没有待确认、没有失败、没有静默")
    void shouldNotAlertWhenQuiet() {
        when(statsService.snapshot(9001L)).thenReturn(quiet());

        List<OverviewAlert> alerts = provider.build(OverviewContext.of(7L, 9001L, Set.of())).alerts();

        assertThat(alerts).isEmpty();
    }

    @Test
    @DisplayName("今日 0 条与没有机器人绑定各留一条提示，不叠成四条 0")
    void shouldExplainQuietDayAndMissingBot() {
        when(statsService.snapshot(9001L)).thenReturn(stats(5L, 4L, 1L, 0L, 0L, 0L, 0L, 80L, 0L, 0L, 0L));

        List<OverviewAlert> alerts = provider.build(OverviewContext.of(7L, 9001L, Set.of())).alerts();

        assertThat(alerts).filteredOn(alert -> alert.text().contains("今日还没有新消息")).hasSize(1);
        assertThat(alerts).filteredOn(alert -> alert.text().contains("还没有启用机器人绑定")).hasSize(1);
        assertThat(alerts).allSatisfy(alert -> assertThat(alert.level()).isEqualTo(OverviewAlertLevel.INFO));
    }

    @Test
    @DisplayName("一个会话都没有时只留一条空库提示")
    void shouldOnlyNoteEmptyConversation() {
        when(statsService.snapshot(9001L)).thenReturn(stats(0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L));

        OverviewSnapshot snapshot = provider.build(OverviewContext.of(7L, 9001L, Set.of()));

        assertThat(snapshot.alerts()).singleElement()
                .satisfies(alert -> {
                    assertThat(alert.level()).isEqualTo(OverviewAlertLevel.INFO);
                    assertThat(alert.text()).contains("还空着");
                });
    }
}
