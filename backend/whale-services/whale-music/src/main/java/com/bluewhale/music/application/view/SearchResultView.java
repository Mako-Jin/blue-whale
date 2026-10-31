package com.bluewhale.music.application.view;

import lombok.Data;

import java.util.List;

/**
 * 站内搜索结果视图（{@code ALL} 时返回各组 Top 与各自总数）。
 *
 * @author BlueWhale
 */
@Data
public class SearchResultView {

    /** 搜索关键词（已去除首尾空白） */
    private String keyword;

    /** 实际搜索类型 */
    private String type;

    /** 本次返回各组的命中总数之和 */
    private long total;

    /** 歌曲命中总数 */
    private long songTotal;

    /** 歌手命中总数 */
    private long artistTotal;

    /** 专辑命中总数 */
    private long albumTotal;

    /** 歌单命中总数 */
    private long playlistTotal;

    /** 歌曲结果 */
    private List<PublicSongView> songs;

    /** 歌手结果 */
    private List<ArtistView> artists;

    /** 专辑结果 */
    private List<AlbumView> albums;

    /** 歌单结果 */
    private List<PlaylistView> playlists;
}
