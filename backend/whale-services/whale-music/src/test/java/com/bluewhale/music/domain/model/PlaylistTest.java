package com.bluewhale.music.domain.model;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.music.domain.exception.MusicErrorCode;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 歌单聚合根上限、去重、重排与权限规则单元测试。
 *
 * @author BlueWhale
 */
class PlaylistTest {

    /** 所有者用户 */
    private static final Long OWNER_ID = 7L;

    /** 其他用户 */
    private static final Long OTHER_USER = 8L;

    /** 单歌单歌曲上限 */
    private static final int MAX_SONGS = 500;

    /**
     * 加歌按顺序生成连续排序号并同步歌曲数量。
     */
    @Test
    void shouldAddSongsWithContinuousSortNo() {
        Playlist playlist = ownedPlaylist();
        assertTrue(playlist.addSong(1L, OWNER_ID, MAX_SONGS));
        assertTrue(playlist.addSong(2L, OWNER_ID, MAX_SONGS));
        assertEquals(0, playlist.getSongs().get(0).getSortNo());
        assertEquals(1, playlist.getSongs().get(1).getSortNo());
        assertEquals(2, playlist.getSongCount());
    }

    /**
     * 重复加入同一歌曲幂等，不新增条目。
     */
    @Test
    void shouldIgnoreDuplicateSong() {
        Playlist playlist = ownedPlaylist();
        assertTrue(playlist.addSong(1L, OWNER_ID, MAX_SONGS));
        assertFalse(playlist.addSong(1L, OWNER_ID, MAX_SONGS));
        assertEquals(1, playlist.getSongs().size());
        assertEquals(1, playlist.getSongCount());
    }

    /**
     * 非法歌曲 ID 按参数错误拒绝。
     */
    @Test
    void shouldRejectInvalidSongId() {
        Playlist playlist = ownedPlaylist();
        BizException exception = assertThrows(BizException.class,
                () -> playlist.addSong(null, OWNER_ID, MAX_SONGS));
        assertEquals(MusicErrorCode.PLAYLIST_SONG_INVALID, exception.getCode());
        assertEquals(MusicErrorCode.PLAYLIST_SONG_INVALID,
                assertThrows(BizException.class, () -> playlist.addSong(0L, OWNER_ID, MAX_SONGS)).getCode());
    }

    /**
     * 达到上限后继续加歌被拒绝。
     */
    @Test
    void shouldRejectSongBeyondLimit() {
        Playlist playlist = ownedPlaylist();
        playlist.setSongs(List.of(songItem(1L, 0)));
        playlist.setSongCount(1);
        BizException exception = assertThrows(BizException.class,
                () -> playlist.addSong(2L, OWNER_ID, 1));
        assertEquals(MusicErrorCode.PLAYLIST_SONG_LIMIT, exception.getCode());
    }

    /**
     * 移除歌曲后重新编号，重复移除幂等。
     */
    @Test
    void shouldRemoveSongAndRenumber() {
        Playlist playlist = ownedPlaylist();
        playlist.setSongs(new ArrayList<>(List.of(songItem(1L, 0), songItem(2L, 1), songItem(3L, 2))));
        playlist.setSongCount(3);
        assertTrue(playlist.removeSong(2L));
        assertEquals(List.of(1L, 3L), playlist.getSongs().stream().map(PlaylistSong::getSongId).toList());
        assertEquals(1, playlist.getSongs().get(1).getSortNo());
        assertEquals(2, playlist.getSongCount());
        assertFalse(playlist.removeSong(2L));
    }

    /**
     * 合法重排按给定顺序更新排序号。
     */
    @Test
    void shouldReorderSongs() {
        Playlist playlist = ownedPlaylist();
        playlist.setSongs(new ArrayList<>(List.of(songItem(1L, 0), songItem(2L, 1), songItem(3L, 2))));
        playlist.reorder(List.of(3L, 1L, 2L));
        assertEquals(List.of(3L, 1L, 2L), playlist.getSongs().stream().map(PlaylistSong::getSongId).toList());
        assertEquals(0, playlist.getSongs().get(0).getSortNo());
        assertEquals(2, playlist.getSongs().get(2).getSortNo());
    }

    /**
     * 缺失、多余或重复的歌曲 ID 均按参数错误拒绝。
     */
    @Test
    void shouldRejectInvalidReorder() {
        Playlist playlist = ownedPlaylist();
        playlist.setSongs(new ArrayList<>(List.of(songItem(1L, 0), songItem(2L, 1))));
        assertEquals(MusicErrorCode.PLAYLIST_SONG_ORDER_INVALID,
                assertThrows(BizException.class, () -> playlist.reorder(List.of(1L))).getCode());
        assertEquals(MusicErrorCode.PLAYLIST_SONG_ORDER_INVALID,
                assertThrows(BizException.class, () -> playlist.reorder(List.of(1L, 2L, 3L))).getCode());
        assertEquals(MusicErrorCode.PLAYLIST_SONG_ORDER_INVALID,
                assertThrows(BizException.class, () -> playlist.reorder(List.of(1L, 1L))).getCode());
    }

    /**
     * 标题为空或超长被拒绝，合法标题去除首尾空白。
     */
    @Test
    void shouldValidateRename() {
        Playlist playlist = ownedPlaylist();
        assertEquals(MusicErrorCode.PLAYLIST_TITLE_INVALID,
                assertThrows(BizException.class, () -> playlist.rename("  ")).getCode());
        String tooLong = "a".repeat(121);
        assertEquals(MusicErrorCode.PLAYLIST_TITLE_INVALID,
                assertThrows(BizException.class, () -> playlist.rename(tooLong)).getCode());
        playlist.rename("  我的歌单  ");
        assertEquals("我的歌单", playlist.getTitle());
    }

    /**
     * 非所有者操作歌单被拒绝。
     */
    @Test
    void shouldRejectNonOwnerOperation() {
        Playlist playlist = ownedPlaylist();
        BizException exception = assertThrows(BizException.class, () -> playlist.assertOwnedBy(OTHER_USER));
        assertEquals(MusicErrorCode.PLAYLIST_NOT_OWNED, exception.getCode());
    }

    /**
     * 公开歌单对租户内可见，私有歌单仅所有者可见。
     */
    @Test
    void shouldRespectVisibility() {
        Playlist playlist = ownedPlaylist();
        assertTrue(playlist.visibleTo(OTHER_USER));
        assertTrue(playlist.visibleTo(null));
        playlist.setVisibility(PlaylistVisibility.PRIVATE);
        assertTrue(playlist.visibleTo(OWNER_ID));
        assertFalse(playlist.visibleTo(OTHER_USER));
        assertFalse(playlist.visibleTo(null));
    }

    /**
     * 构造归属测试所有者的公开用户歌单。
     *
     * @return 歌单
     */
    private Playlist ownedPlaylist() {
        Playlist playlist = new Playlist();
        playlist.setOwnerId(OWNER_ID);
        playlist.setPlaylistType(PlaylistType.USER);
        playlist.setVisibility(PlaylistVisibility.PUBLIC);
        return playlist;
    }

    /**
     * 构造歌单歌曲条目。
     *
     * @param songId 歌曲 ID
     * @param sortNo 排序号
     * @return 歌单歌曲
     */
    private PlaylistSong songItem(Long songId, int sortNo) {
        PlaylistSong item = new PlaylistSong();
        item.setSongId(songId);
        item.setSortNo(sortNo);
        return item;
    }
}
