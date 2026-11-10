package com.bluewhale.video.application.scheduler;

import com.bluewhale.video.config.VideoProperties;
import com.bluewhale.video.domain.repository.WatchHistoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 观看历史保留期清理任务单元测试（M5-7）。
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
class WatchHistoryCleanupSchedulerTest {

    /** 默认保留天数（与 VideoProperties 默认值一致） */
    private static final int RETENTION_DAYS = 180;

    /** 单批删除条数（与实现常量一致） */
    private static final int BATCH_LIMIT = 500;

    /** 单次任务最大批数（与实现常量一致） */
    private static final int MAX_BATCHES = 20;

    @Mock
    private WatchHistoryRepository watchHistoryRepository;

    private VideoProperties videoProperties;

    private WatchHistoryCleanupScheduler scheduler;

    /**
     * 构造任务实例并设置保留天数。
     */
    @BeforeEach
    void setUp() {
        videoProperties = new VideoProperties();
        videoProperties.setHistoryRetentionDays(RETENTION_DAYS);
        scheduler = new WatchHistoryCleanupScheduler(watchHistoryRepository, videoProperties);
    }

    /**
     * 单批不足上限时立即结束，且截止时间为「当前 - 保留天数」。
     */
    @Test
    void shouldStopWhenBatchBelowLimit() {
        when(watchHistoryRepository.deleteWatchedBefore(any(LocalDateTime.class), anyInt()))
                .thenReturn(BATCH_LIMIT - 100);

        scheduler.cleanup();

        ArgumentCaptor<LocalDateTime> captor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(watchHistoryRepository).deleteWatchedBefore(captor.capture(), anyInt());
        assertCutoff(captor.getValue(), RETENTION_DAYS);
    }

    /**
     * 满批时继续下一批，达到单次最大批数后停止（避免单次大事务）。
     */
    @Test
    void shouldStopAtMaxBatches() {
        when(watchHistoryRepository.deleteWatchedBefore(any(LocalDateTime.class), anyInt()))
                .thenReturn(BATCH_LIMIT);

        scheduler.cleanup();

        verify(watchHistoryRepository, times(MAX_BATCHES))
                .deleteWatchedBefore(any(LocalDateTime.class), anyInt());
    }

    /**
     * 保留天数非法（0 / 负数）时按 1 天下限处理。
     */
    @Test
    void shouldUseMinimumRetentionWhenConfiguredInvalid() {
        videoProperties.setHistoryRetentionDays(0);
        when(watchHistoryRepository.deleteWatchedBefore(any(LocalDateTime.class), anyInt()))
                .thenReturn(0);

        scheduler.cleanup();

        ArgumentCaptor<LocalDateTime> captor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(watchHistoryRepository).deleteWatchedBefore(captor.capture(), anyInt());
        assertCutoff(captor.getValue(), 1);
    }

    /**
     * 清理异常向上抛出：调度中心据此判定失败并重试/告警，吞掉会让执行流水长期显示成功。
     */
    @Test
    void shouldPropagateCleanupFailure() {
        when(watchHistoryRepository.deleteWatchedBefore(any(LocalDateTime.class), anyInt()))
                .thenThrow(new IllegalStateException("数据库抖动"));

        assertThrows(IllegalStateException.class, () -> scheduler.cleanup());

        verify(watchHistoryRepository).deleteWatchedBefore(any(LocalDateTime.class), anyInt());
    }

    /**
     * 断言截止时间与「当前 - 保留天数」的偏差在 1 分钟内。
     *
     * @param captured 实际传入的截止时间
     * @param days 期望保留天数
     */
    private void assertCutoff(LocalDateTime captured, int days) {
        LocalDateTime expected = LocalDateTime.now().minusDays(days);
        long minutes = Math.abs(Duration.between(expected, captured).toMinutes());
        assertTrue(minutes <= 1, "cutoff=" + captured + " expected=" + expected);
    }
}
