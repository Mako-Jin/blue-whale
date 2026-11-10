package com.bluewhale.video.application.scheduler;

import com.bluewhale.video.domain.repository.ContentRepository;
import com.bluewhale.video.domain.repository.PlayCountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 播放计数回刷任务：每 5 分钟把 Redis 累加的播放增量原子累加回 {@code vd_content.play_count}。
 *
 * <p>增量通过 {@code GETDEL} 原子取出，回刷期间新产生的播放会写入新的 key，
 * 不会丢失；后台任务无租户上下文，租户插件不加条件，可覆盖全部租户。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PlayCountFlushScheduler {

    private final PlayCountRepository playCountRepository;

    private final ContentRepository contentRepository;

    /**
     * 每 5 分钟回刷播放计数。
     */
    @Scheduled(cron = "0 */5 * * * ?")
    public void flush() {
        Map<Long, Long> increments = playCountRepository.drainDeltas();
        for (Map.Entry<Long, Long> entry : increments.entrySet()) {
            if (entry.getValue() != null && entry.getValue() > 0) {
                contentRepository.addPlayCount(entry.getKey(), entry.getValue());
            }
        }
        log.info("视频播放计数回刷完成: contents={}, total={}", increments.size(), totalOf(increments));
    }

    /**
     * 汇总本次回刷的播放增量。
     *
     * @param increments 内容 ID → 增量
     * @return 增量总和
     */
    private long totalOf(Map<Long, Long> increments) {
        long total = 0L;
        for (Long value : increments.values()) {
            if (value != null) {
                total += value;
            }
        }
        return total;
    }
}
