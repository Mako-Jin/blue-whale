package com.bluewhale.mail.overview;

import com.bluewhale.mail.mapper.MailAccountMapper;
import com.bluewhale.mail.mapper.MailDeliveryReportMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link MailOverviewStatsService} 单元测试：租户闸口、窗口边界、四态派生。
 *
 * <p>四态派生是这段代码唯一有分支的逻辑（"新鲜"靠总数相减得出），
 * 因此下面用<b>按调用顺序</b>喂返回值的方式验证：{@code selectCount} 的入参是
 * 包装器对象，按条件回放比断言 SQL 片段稳，但也意味着加一次查询就要同步改这里。
 * 这是刻意接受的代价——派生算错会让柱图加起来不等于在册数，比测试脆弱更难查。</p>
 *
 * @author BlueWhale
 */
class MailOverviewStatsServiceTest {

    private final MailAccountMapper accountMapper = mock(MailAccountMapper.class);

    private final MailDeliveryReportMapper reportMapper = mock(MailDeliveryReportMapper.class);

    private final MailOverviewStatsService service = new MailOverviewStatsService(accountMapper, reportMapper);

    /**
     * 按统计服务的调用顺序喂账号计数。
     *
     * @param total       全部账号
     * @param enabled     启用账号
     * @param syncEnabled 开启同步
     * @param never       从未同步
     * @param overdue     超时未同步
     * @param quotaTight  配额吃紧
     */
    private void stubAccounts(long total, long enabled, long syncEnabled, long never, long overdue, long quotaTight) {
        when(accountMapper.selectCount(any())).thenReturn(total, enabled, syncEnabled, never, overdue, quotaTight);
    }

    /**
     * 按统计服务的调用顺序喂退信计数。
     *
     * @param bounce     近七日退信
     * @param bounceHard 其中永久退信
     */
    private void stubBounces(long bounce, long bounceHard) {
        when(reportMapper.selectCount(any())).thenReturn(bounce, bounceHard);
    }

    @Test
    @DisplayName("缺租户直接抛：宁可让屏风降级，也不显示跨租户的和")
    void shouldRejectMissingTenant() {
        assertThatThrownBy(() -> service.snapshot(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tenantId");
    }

    @Test
    @DisplayName("四态派生：新鲜 = 开启同步 − 从未同步 − 超时，停用 = 总数 − 启用")
    void shouldDeriveSyncBuckets() {
        stubAccounts(25L, 22L, 22L, 2L, 2L, 1L);
        stubBounces(4L, 3L);

        MailOverviewStats stats = service.snapshot(9001L);

        assertThat(stats.accountTotal()).isEqualTo(25L);
        assertThat(stats.accountEnabled()).isEqualTo(22L);
        assertThat(stats.accountDisabled()).isEqualTo(3L);
        assertThat(stats.syncFresh()).isEqualTo(18L);
        assertThat(stats.syncOverdue()).isEqualTo(2L);
        assertThat(stats.syncNever()).isEqualTo(2L);
        assertThat(stats.syncOff()).isEqualTo(3L);
        assertThat(stats.syncEnabled()).isEqualTo(22L);
        assertThat(stats.syncFresh() + stats.syncOverdue() + stats.syncNever() + stats.syncOff())
                .isEqualTo(stats.accountTotal());
        assertThat(stats.quotaTight()).isEqualTo(1L);
        assertThat(stats.bounce7d()).isEqualTo(4L);
        assertThat(stats.bounceHard7d()).isEqualTo(3L);
    }

    @Test
    @DisplayName("脏数据让差值为负时夹到 0，不把负柱画到基线以下")
    void shouldClampNegativeBuckets() {
        stubAccounts(5L, 5L, 5L, 4L, 4L, 0L);
        stubBounces(0L, 0L);

        MailOverviewStats stats = service.snapshot(9001L);

        assertThat(stats.syncFresh()).isZero();
    }

    @Test
    @DisplayName("窗口边界：同步看 24 小时，退信看 7 天")
    void shouldUseDocumentedWindows() {
        LocalDateTime now = LocalDateTime.of(2026, 11, 23, 10, 30);

        assertThat(MailOverviewStatsService.syncSince(now)).isEqualTo(now.minusHours(24));
        assertThat(MailOverviewStatsService.bounceSince(now)).isEqualTo(now.minusDays(7));
    }

    @Test
    @DisplayName("计数为 null 时按 0 处理：selectCount 无行时可能给 null")
    void shouldTreatNullCountAsZero() {
        when(accountMapper.selectCount(any())).thenReturn(null);
        when(reportMapper.selectCount(any())).thenReturn((Long) null);

        MailOverviewStats stats = service.snapshot(9001L);

        assertThat(stats.accountTotal()).isZero();
        assertThat(stats.bounce7d()).isZero();
    }
}
