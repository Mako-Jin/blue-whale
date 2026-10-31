package com.bluewhale.music.domain.repository;

import java.util.Map;

/**
 * 播放计数缓冲端口（Redis 累加，定时回刷数据库）。
 *
 * @author BlueWhale
 */
public interface PlayCountRepository {

    /**
     * 播放计数加一。
     *
     * @param songId 歌曲 ID
     */
    void increment(Long songId);

    /**
     * 原子取出并清空全部待回刷的播放增量。
     *
     * @return 歌曲 ID → 增量；无待回刷数据返回空映射
     */
    Map<Long, Long> drain();
}
