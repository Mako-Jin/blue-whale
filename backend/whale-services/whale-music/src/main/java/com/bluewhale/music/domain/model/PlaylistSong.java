package com.bluewhale.music.domain.model;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 歌单歌曲（歌单内的一首歌曲与排序）。
 *
 * <p>{@code (playlistId, songId)} 在库中唯一，同一歌曲在歌单内只出现一次。</p>
 *
 * @author BlueWhale
 */
@Data
public class PlaylistSong {

    /** 主键 */
    private Long id;

    /** 歌单 ID */
    private Long playlistId;

    /** 歌曲 ID */
    private Long songId;

    /** 歌单内排序，从 0 开始升序 */
    private Integer sortNo;

    /** 加入歌单的用户 ID */
    private Long addedBy;

    /** 加入时间 */
    private LocalDateTime createTime;
}
