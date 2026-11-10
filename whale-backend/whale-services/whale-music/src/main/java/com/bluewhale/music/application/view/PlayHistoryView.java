package com.bluewhale.music.application.view;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 最近播放条目视图。
 *
 * @author BlueWhale
 */
@Data
public class PlayHistoryView {

    /** 歌曲 ID */
    private Long songId;

    /** 歌曲标题 */
    private String title;

    /** 歌手 ID */
    private Long artistId;

    /** 封面文件 ID */
    private Long coverFileId;

    /** 时长（毫秒） */
    private Long durationMs;

    /** 最近进度（毫秒） */
    private Long progressMs;

    /** 播放来源 */
    private String source;

    /** 最近播放时间 */
    private LocalDateTime playedAt;

    /** 当前是否仍可播放 */
    private boolean playable;
}
