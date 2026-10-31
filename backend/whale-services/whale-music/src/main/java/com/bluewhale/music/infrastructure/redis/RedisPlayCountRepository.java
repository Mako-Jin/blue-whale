package com.bluewhale.music.infrastructure.redis;

import com.bluewhale.music.domain.repository.PlayCountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 播放计数 Redis 实现。
 *
 * <p>key 约定 {@code whale:music:play:count:{songId}}，每次真正开流执行 {@code INCR}；
 * 回刷任务通过 {@code SCAN} 找到待回刷 key 并用 {@code GETDEL} 原子取出并删除，
 * 回刷期间的新增量会写入新的 key，不会丢失。</p>
 *
 * @author BlueWhale
 */
@Repository
@RequiredArgsConstructor
public class RedisPlayCountRepository implements PlayCountRepository {

    /** 播放计数 key 前缀 */
    private static final String COUNT_KEY_PREFIX = "whale:music:play:count:";

    /** SCAN 单批建议条数 */
    private static final long SCAN_COUNT = 100L;

    /** 歌曲 ID 最小值 */
    private static final long MIN_SONG_ID = 1L;

    private final StringRedisTemplate stringRedisTemplate;

    @Override
    public void increment(Long songId) {
        if (songId == null || songId < MIN_SONG_ID) {
            return;
        }
        stringRedisTemplate.opsForValue().increment(key(songId));
    }

    @Override
    public Map<Long, Long> drain() {
        Set<String> keys = new HashSet<>();
        try (Cursor<String> cursor = stringRedisTemplate.scan(ScanOptions.scanOptions()
                .match(COUNT_KEY_PREFIX + "*").count(SCAN_COUNT).build())) {
            cursor.forEachRemaining(keys::add);
        }
        Map<Long, Long> increments = new HashMap<>();
        for (String key : keys) {
            String value = stringRedisTemplate.opsForValue().getAndDelete(key);
            addIncrement(increments, key, value);
        }
        return increments;
    }

    /**
     * 解析并累计单首歌的播放增量。
     *
     * @param increments 增量聚合结果
     * @param key Redis key
     * @param value Redis 值
     */
    private void addIncrement(Map<Long, Long> increments, String key, String value) {
        if (value == null) {
            return;
        }
        try {
            Long songId = Long.valueOf(key.substring(COUNT_KEY_PREFIX.length()));
            Long delta = Long.valueOf(value);
            if (delta > 0) {
                increments.put(songId, delta);
            }
        } catch (NumberFormatException ex) {
            return;
        }
    }

    /**
     * 拼装播放计数 key。
     *
     * @param songId 歌曲 ID
     * @return Redis key
     */
    private String key(Long songId) {
        return COUNT_KEY_PREFIX + songId;
    }
}
