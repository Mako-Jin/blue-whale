package com.bluewhale.music.domain.model;

import lombok.Data;

/**
 * 歌曲播放热度排行项（近 N 天窗口聚合结果）。
 *
 * @author BlueWhale
 */
@Data
public class SongPlayRank {

    /** 歌曲 ID */
    private Long songId;

    /** 窗口内播放次数（按用户去重后的记录数） */
    private Long playCount;
}
