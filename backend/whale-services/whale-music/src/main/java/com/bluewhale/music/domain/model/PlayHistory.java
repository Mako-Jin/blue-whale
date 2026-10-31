package com.bluewhale.music.domain.model;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 播放历史（每用户每歌曲仅保留一条，重复上报更新进度与最近播放时间）。
 *
 * <p>只有可播放歌曲才会写入；按用户保留 90 天，超期由清理任务删除。</p>
 *
 * @author BlueWhale
 */
@Data
public class PlayHistory {

    /** 主键 */
    private Long id;

    /** 用户 ID */
    private Long userId;

    /** 歌曲 ID */
    private Long songId;

    /** 播放进度（毫秒） */
    private Long progressMs;

    /** 播放来源：HOME / SEARCH / PLAYLIST / ALBUM / ARTIST / MY */
    private String source;

    /** 设备标识：WEB / MOBILE / OTHER */
    private String device;

    /** 最近播放时间 */
    private LocalDateTime playedAt;
}
