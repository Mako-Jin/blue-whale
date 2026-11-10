package com.bluewhale.music.application.view;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 歌单内歌曲视图：在用户侧歌曲视图基础上附带歌单内排序号。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class PlaylistSongView extends PublicSongView {

    /** 歌单内排序号，从 0 开始 */
    private Integer sortNo;
}
