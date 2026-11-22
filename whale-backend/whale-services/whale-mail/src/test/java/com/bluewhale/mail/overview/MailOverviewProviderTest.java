package com.bluewhale.mail.overview;

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

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link MailOverviewProvider} 单元测试：四张牌的口径提示、三类欠账语气、
 * 账号同步四态柱图、告警分条、空库不误报。
 *
 * @author BlueWhale
 */
class MailOverviewProviderTest {

    private final MailOverviewStatsService statsService = mock(MailOverviewStatsService.class);

    private final MailOverviewProvider provider = new MailOverviewProvider(statsService);

    /**
     * 构造一份邮驿账本。
     *
     * @param total      在册账号
     * @param enabled    启用账号
     * @param disabled   停用账号
     * @param fresh      近一日已同步
     * @param overdue    超时未同步
     * @param never      从未同步
     * @param off        未开同步
     * @param quotaTight 配额吃紧
     * @param bounce7d   近七日退信
     * @param bounceHard 其中永久退信
     * @return 统计快照
     */
    private static MailOverviewStats stats(long total, long enabled, long disabled, long fresh, long overdue,
            long never, long off, long quotaTight, long bounce7d, long bounceHard) {
        return new MailOverviewStats(total, enabled, disabled, fresh, overdue, never, off,
                quotaTight, bounce7d, bounceHard);
    }

    /**
     * 一屏"平安无事"的账本：20 个账号都在同步、配额宽裕、七天没退信。
     *
     * @return 统计快照
     */
    private static MailOverviewStats quiet() {
        return stats(20L, 20L, 0L, 20L, 0L, 0L, 0L, 0L, 0L, 0L);
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
    @DisplayName("元信息与权限码：统的是全租户邮箱，走账号清单码")
    void shouldExposeMetadata() {
        assertThat(provider.service()).isEqualTo("mail");
        assertThat(provider.nodeKey()).isEqualTo("youyi-stage");
        assertThat(provider.title()).isEqualTo("邮驿");
        assertThat(provider.subtitle()).isEqualTo("邮件收发");
        assertThat(provider.permissionCode()).isEqualTo("sys:mail:account:list");
    }

    @Test
    @DisplayName("租户 ID 从上下文透传给统计服务")
    void shouldPassTenantIdThrough() {
        when(statsService.snapshot(9001L)).thenReturn(quiet());

        OverviewSnapshot snapshot = provider.build(OverviewContext.of(7L, 9001L, Set.of()));

        assertThat(snapshot.service()).isEqualTo("mail");
        assertThat(snapshot.nodeKey()).isEqualTo("youyi-stage");
        assertThat(snapshot.subtitle()).contains("驿路与信件");
        assertThat(snapshot.notes()).anySatisfy(note -> assertThat(note)
                .contains("24 小时")
                .contains("近 7 日"));
    }

    @Test
    @DisplayName("上下文为空时不编租户：统计服务收到 null 并自行抛（宁可降级不串数）")
    void shouldPassNullTenantWhenContextMissing() {
        when(statsService.snapshot(null)).thenThrow(new IllegalArgumentException("tenantId is required"));

        assertThatThrownBy(() -> provider.build(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("四张牌口径正确：在册只数启用，三类欠账各自独立")
    void shouldBuildFourKpis() {
        when(statsService.snapshot(9001L)).thenReturn(stats(25L, 22L, 3L, 18L, 2L, 2L, 3L, 1L, 4L, 3L));

        Map<String, OverviewKpi> kpis = kpiMap(provider.build(OverviewContext.of(7L, 9001L, Set.of())));

        assertThat(kpis).containsOnlyKeys("mailboxes", "sync-overdue", "quota-tight", "bounce");
        assertThat(kpis.get("mailboxes").value()).isEqualTo(22.0);
        assertThat(kpis.get("mailboxes").hint()).isEqualTo("另停用 3 个");
        assertThat(kpis.get("sync-overdue").value()).isEqualTo(2.0);
        assertThat(kpis.get("quota-tight").value()).isEqualTo(1.0);
        assertThat(kpis.get("bounce").value()).isEqualTo(4.0);
        assertThat(kpis.get("bounce").hint()).isEqualTo("永久 3 · 临时 1");
    }

    @Test
    @DisplayName("三类欠账有账就黄，没账就绿")
    void shouldToneDebtKpis() {
        when(statsService.snapshot(9001L)).thenReturn(stats(25L, 22L, 3L, 18L, 2L, 2L, 3L, 1L, 4L, 3L));
        Map<String, OverviewKpi> dirty = kpiMap(provider.build(OverviewContext.of(7L, 9001L, Set.of())));
        assertThat(dirty.get("sync-overdue").tone()).isEqualTo(OverviewTone.WARN);
        assertThat(dirty.get("quota-tight").tone()).isEqualTo(OverviewTone.WARN);
        assertThat(dirty.get("bounce").tone()).isEqualTo(OverviewTone.WARN);
        // 在册邮箱不是欠账：开得多说明用得多，不该报警
        assertThat(dirty.get("mailboxes").tone()).isEqualTo(OverviewTone.FLAT);

        when(statsService.snapshot(9001L)).thenReturn(quiet());
        Map<String, OverviewKpi> clean = kpiMap(provider.build(OverviewContext.of(7L, 9001L, Set.of())));
        assertThat(clean.get("sync-overdue").tone()).isEqualTo(OverviewTone.GOOD);
        assertThat(clean.get("quota-tight").tone()).isEqualTo(OverviewTone.GOOD);
        assertThat(clean.get("bounce").tone()).isEqualTo(OverviewTone.GOOD);
        assertThat(clean.get("bounce").hint()).isEqualTo("近七日没有退信");
    }

    @Test
    @DisplayName("全绿的账本提示语说人话，不写 0 个")
    void shouldSpeakPlainlyWhenQuiet() {
        when(statsService.snapshot(9001L)).thenReturn(quiet());

        Map<String, OverviewKpi> kpis = kpiMap(provider.build(OverviewContext.of(7L, 9001L, Set.of())));

        assertThat(kpis.get("mailboxes").hint()).isEqualTo("全部启用");
        assertThat(kpis.get("sync-overdue").hint()).isEqualTo("同步都跟得上");
        assertThat(kpis.get("quota-tight").hint()).isEqualTo("配额都还宽裕");
    }

    @Test
    @DisplayName("账号同步四态柱图：四格互斥，合计等于在册数")
    void shouldBuildSyncChart() {
        when(statsService.snapshot(9001L)).thenReturn(stats(25L, 22L, 3L, 18L, 2L, 2L, 3L, 1L, 4L, 3L));

        List<OverviewChart> charts = provider.build(OverviewContext.of(7L, 9001L, Set.of())).charts();

        assertThat(charts).hasSize(1);
        OverviewChart chart = charts.get(0);
        assertThat(chart.key()).isEqualTo("sync-health");
        assertThat(chart.type()).isEqualTo("bar");
        assertThat(chart.format()).isEqualTo(OverviewFormat.COUNT);
        assertThat(chart.categories()).containsExactly("近一日已同步", "超时未同步", "从未同步", "未开同步");
        List<Double> values = chart.series().get(0).values();
        assertThat(values).containsExactly(18.0, 2.0, 2.0, 3.0);
        assertThat(values.stream().mapToDouble(Double::doubleValue).sum()).isEqualTo(25.0);
    }

    @Test
    @DisplayName("三个入口：邮件列表、邮箱账号、退信回执，权限码各归各位")
    void shouldLinkThreeEntries() {
        when(statsService.snapshot(9001L)).thenReturn(quiet());

        List<OverviewLink> links = provider.build(OverviewContext.of(7L, 9001L, Set.of())).links();

        assertThat(links).extracting(OverviewLink::key).containsExactly("inbox", "accounts", "bounces");
        assertThat(links).extracting(OverviewLink::path).containsExactly(
                "/mail/v1/message/page", "/mail/v1/account/list", "/mail/v1/delivery/report/page");
        /* 账号清单是 list 不是 page：照抄其他殿的 page 会让入口点进去 404 */
        assertThat(links.get(1).path()).endsWith("/account/list");
        assertThat(links.get(1).permission()).isEqualTo("sys:mail:account:list");
        assertThat(links.get(0).permission()).isEqualTo("sys:mail:message:list");
    }

    @Test
    @DisplayName("告警分条：超时与配额、永久退信黄，从未同步与临时退信只是提示")
    void shouldSplitAlerts() {
        when(statsService.snapshot(9001L)).thenReturn(stats(25L, 22L, 3L, 18L, 2L, 2L, 3L, 1L, 4L, 3L));

        List<OverviewAlert> alerts = provider.build(OverviewContext.of(7L, 9001L, Set.of())).alerts();

        assertThat(alerts).filteredOn(alert -> alert.text().contains("超过一天没同步"))
                .singleElement()
                .satisfies(alert -> {
                    assertThat(alert.level()).isEqualTo(OverviewAlertLevel.WARNING);
                    assertThat(alert.text()).contains("2 个邮箱");
                });
        assertThat(alerts).filteredOn(alert -> alert.text().contains("配额已用八成"))
                .singleElement()
                .satisfies(alert -> assertThat(alert.level()).isEqualTo(OverviewAlertLevel.WARNING));
        assertThat(alerts).filteredOn(alert -> alert.text().contains("永久退信"))
                .singleElement()
                .satisfies(alert -> {
                    assertThat(alert.level()).isEqualTo(OverviewAlertLevel.WARNING);
                    assertThat(alert.text()).contains("3 封");
                });
        assertThat(alerts).filteredOn(alert -> alert.text().contains("一次没跑成"))
                .singleElement()
                .satisfies(alert -> assertThat(alert.level()).isEqualTo(OverviewAlertLevel.INFO));
        assertThat(alerts).filteredOn(alert -> alert.text().contains("临时退信"))
                .singleElement()
                .satisfies(alert -> {
                    assertThat(alert.level()).isEqualTo(OverviewAlertLevel.INFO);
                    assertThat(alert.text()).contains("1 封");
                });
    }

    @Test
    @DisplayName("没有退信时不发退信告警，避免天天报警教人忽略")
    void shouldNotAlertWhenNoBounce() {
        when(statsService.snapshot(9001L)).thenReturn(quiet());

        List<OverviewAlert> alerts = provider.build(OverviewContext.of(7L, 9001L, Set.of())).alerts();

        assertThat(alerts).isEmpty();
    }

    @Test
    @DisplayName("一个账号都没有时只留一条空库提示，不叠四条 0 的告警")
    void shouldOnlyNoteEmptyMailbox() {
        when(statsService.snapshot(9001L)).thenReturn(stats(0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L));

        OverviewSnapshot snapshot = provider.build(OverviewContext.of(7L, 9001L, Set.of()));

        assertThat(snapshot.alerts()).singleElement()
                .satisfies(alert -> {
                    assertThat(alert.level()).isEqualTo(OverviewAlertLevel.INFO);
                    assertThat(alert.text()).contains("还空着");
                });
        assertThat(kpiMap(snapshot).get("mailboxes").hint()).isEqualTo("还没有开通邮箱");
    }
}
