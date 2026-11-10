package com.bluewhale.video.domain.repository;

import java.util.Map;

/**
 * 播放次数聚合端口（Redis 增量 + 定时回刷 {@code vd_content.play_count}）。
 *
 * @author BlueWhale
 */
public interface PlayCountRepository {

    /**
     * 累加内容播放次数。
     *
     * @param contentId 内容 ID
     */
    void increment(Long contentId);

    /**
     * 读取并清空待回刷的增量。
     *
     * @return 内容 ID → 增量；无待回刷数据返回空映射
     */
    Map<Long, Long> drainDeltas();
}
