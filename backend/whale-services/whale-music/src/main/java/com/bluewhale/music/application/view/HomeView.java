package com.bluewhale.music.application.view;

import lombok.Data;

import java.util.List;

/**
 * 音乐首页聚合视图。
 *
 * @author BlueWhale
 */
@Data
public class HomeView {

    /** 热门歌曲（按播放 / 收藏倒序） */
    private List<PublicSongView> hotSongs;

    /** 新歌（按上架时间倒序） */
    private List<PublicSongView> newSongs;

    /** 官方歌单 */
    private List<PlaylistView> officialPlaylists;

    /** 热门标签 */
    private List<TagView> tags;
}
