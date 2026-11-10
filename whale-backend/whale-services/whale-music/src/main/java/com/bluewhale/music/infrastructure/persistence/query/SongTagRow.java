package com.bluewhale.music.infrastructure.persistence.query;

import lombok.Data;

/**
 * 歌曲-标签关系行（{@code mu_song_tag} 投影）。
 *
 * @author BlueWhale
 */
@Data
public class SongTagRow {

    /** 歌曲 ID */
    private Long songId;

    /** 标签 ID */
    private Long tagId;
}
