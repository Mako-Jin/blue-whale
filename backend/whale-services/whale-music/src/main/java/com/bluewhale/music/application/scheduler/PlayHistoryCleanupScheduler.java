package com.bluewhale.music.application.scheduler;

import com.bluewhale.music.config.MusicProperties;
import com.bluewhale.music.domain.repository.PlayHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 播放历史清理任务：按保留期（默认 90 天）删除过期记录。
 *
 * <p>清理在无租户上下文的后台线程执行，统一租户插件对空上下文不追加条件，
 * 因此可一次覆盖全部租户；任务幂等，可重复执行。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PlayHistoryCleanupScheduler {

    private final PlayHistoryRepository playHistoryRepository;

    private final MusicProperties musicProperties;

    /**
     * 每日凌晨 3 点清理过期播放历史。
     */
    @Scheduled(cron = "0 0 3 * * ?")
    public void purgeExpiredHistory() {
        int days = Math.max(musicProperties.getPlayHistoryRetentionDays(), 1);
        LocalDateTime before = LocalDateTime.now().minusDays(days);
        int removed = playHistoryRepository.purgeBefore(before);
        log.info("播放历史清理完成: retentionDays={}, before={}, removed={}", days, before, removed);
    }
}
