package com.bluewhale.im.overview;

import com.bluewhale.im.mapper.ImAgentBindingMapper;
import com.bluewhale.im.mapper.ImAgentRunMapper;
import com.bluewhale.im.mapper.ImConversationMapper;
import com.bluewhale.im.mapper.ImMessageMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link ImOverviewStatsService} 单元测试：租户闸口、调用顺序与逐日消息窗口。
 *
 * <p>{@code selectCount} 收到的是条件包装器，按调用顺序回放返回值比断言 SQL 片段稳，
 * 但也意味着加一次查询就要同步改这里。这是刻意接受的代价——逐日消息一旦错位，
 * 屏风上"哪一天掉的"会指到错误的那一根柱子，比测试脆弱更难查。</p>
 *
 * @author BlueWhale
 */
class ImOverviewStatsServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 23, 10, 30);

    private final ImConversationMapper conversationMapper = mock(ImConversationMapper.class);

    private final ImMessageMapper messageMapper = mock(ImMessageMapper.class);

    private final ImAgentBindingMapper bindingMapper = mock(ImAgentBindingMapper.class);

    private final ImAgentRunMapper runMapper = mock(ImAgentRunMapper.class);

    private final ImOverviewStatsService service = new ImOverviewStatsService(
            conversationMapper, messageMapper, bindingMapper, runMapper);

    /**
     * 按统计服务的调用顺序喂会话计数。
     *
     * @param total 会话总数
     * @param single 单聊
     * @param group 群聊
     * @param bot 机器人会话
     * @param activeToday 今日活跃
     * @param idle7d 静默
     */
    private void stubConversations(long total, long single, long group, long bot, long activeToday, long idle7d) {
        when(conversationMapper.selectCount(any())).thenReturn(total, single, group, bot, activeToday, idle7d);
    }

    /**
     * 按统计服务的调用顺序喂消息计数：今日、近七日，然后逐日七天。
     *
     * @param today 今日消息
     * @param week 近七日消息
     * @param daily 逐日消息
     */
    private void stubMessages(long today, long week, long... daily) {
        Long[] values = new Long[2 + daily.length];
        values[0] = today;
        values[1] = week;
        for (int i = 0; i < daily.length; i++) {
            values[2 + i] = daily[i];
        }
        AtomicInteger cursor = new AtomicInteger();
        when(messageMapper.selectCount(any())).thenAnswer(invocation ->
                values[Math.min(cursor.getAndIncrement(), values.length - 1)]);
    }

    /**
     * 按统计服务的调用顺序喂机器人两笔账。
     *
     * @param pending 待确认运行
     * @param failed 近七日失败运行
     */
    private void stubRuns(long pending, long failed) {
        when(runMapper.selectCount(any())).thenReturn(pending, failed);
    }

    @Test
    @DisplayName("缺租户直接抛：宁可让屏风降级，也不显示跨租户的和")
    void shouldRejectMissingTenant() {
        assertThatThrownBy(() -> service.snapshot(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tenantId");
    }

    @Test
    @DisplayName("一次快照：会话三态、今日/近七日、机器人两笔账各归各位")
    void shouldBuildSnapshot() {
        stubConversations(12L, 8L, 2L, 2L, 3L, 4L);
        stubMessages(40L, 210L, 20L, 30L, 25L, 35L, 40L, 20L, 40L);
        when(bindingMapper.selectCount(any())).thenReturn(2L);
        stubRuns(2L, 1L);

        ImOverviewStats stats = service.snapshot(9001L, NOW);

        assertThat(stats.conversationTotal()).isEqualTo(12L);
        assertThat(stats.conversationSingle()).isEqualTo(8L);
        assertThat(stats.conversationGroup()).isEqualTo(2L);
        assertThat(stats.conversationBot()).isEqualTo(2L);
        assertThat(stats.activeToday()).isEqualTo(3L);
        assertThat(stats.idle7d()).isEqualTo(4L);
        assertThat(stats.messageToday()).isEqualTo(40L);
        assertThat(stats.message7d()).isEqualTo(210L);
        assertThat(stats.botEnabled()).isEqualTo(2L);
        assertThat(stats.pendingConfirm()).isEqualTo(2L);
        assertThat(stats.runFailed7d()).isEqualTo(1L);
    }

    @Test
    @DisplayName("逐日消息七天且按日期升序：最早的在最前，今天在最后")
    void shouldReturnSevenAscendingDays() {
        stubConversations(1L, 1L, 0L, 0L, 1L, 0L);
        stubMessages(7L, 28L, 1L, 2L, 3L, 4L, 5L, 6L, 7L);
        when(bindingMapper.selectCount(any())).thenReturn(1L);
        stubRuns(0L, 0L);

        List<ImOverviewStats.DailyCount> days = service.snapshot(9001L, NOW).recentDays();

        assertThat(days).hasSize(7);
        assertThat(days.get(0).day()).isEqualTo(LocalDate.of(2026, 11, 17));
        assertThat(days.get(6).day()).isEqualTo(LocalDate.of(2026, 11, 23));
        assertThat(days).extracting(ImOverviewStats.DailyCount::count)
                .containsExactly(1L, 2L, 3L, 4L, 5L, 6L, 7L);
    }

    @Test
    @DisplayName("计数为 null 时按 0 处理：selectCount 无行时可能给 null")
    void shouldTreatNullCountAsZero() {
        when(conversationMapper.selectCount(any())).thenReturn(null);
        when(messageMapper.selectCount(any())).thenReturn((Long) null);
        when(bindingMapper.selectCount(any())).thenReturn((Long) null);
        when(runMapper.selectCount(any())).thenReturn((Long) null);

        ImOverviewStats stats = service.snapshot(9001L, NOW);

        assertThat(stats.conversationTotal()).isZero();
        assertThat(stats.message7d()).isZero();
        assertThat(stats.botEnabled()).isZero();
        assertThat(stats.pendingConfirm()).isZero();
        assertThat(stats.recentDays()).hasSize(7).allSatisfy(day -> assertThat(day.count()).isZero());
    }

    @Test
    @DisplayName("窗口常量：静默与趋势都看 7 天，消息只数正常态")
    void shouldUseDocumentedWindows() {
        assertThat(ImOverviewStatsService.IDLE_WINDOW_DAYS).isEqualTo(7);
        assertThat(ImOverviewStatsService.TREND_WINDOW_DAYS).isEqualTo(7);
        assertThat(ImOverviewStatsService.MESSAGE_STATUS_NORMAL).isEqualTo(1);
        assertThat(ImOverviewStatsService.RUN_WAITING_CONFIRM).isEqualTo("WAITING_CONFIRM");
        assertThat(ImOverviewStatsService.RUN_FAILED).isEqualTo("FAILED");
    }
}
