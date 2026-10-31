package com.bluewhale.music.application.service.impl;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.tenant.TenantContext;
import com.bluewhale.music.application.command.AddPlaylistSongCommand;
import com.bluewhale.music.application.command.PlaylistSaveCommand;
import com.bluewhale.music.application.view.PlaylistView;
import com.bluewhale.music.config.MusicProperties;
import com.bluewhale.music.domain.exception.MusicErrorCode;
import com.bluewhale.music.domain.model.CopyrightState;
import com.bluewhale.music.domain.model.Playlist;
import com.bluewhale.music.domain.model.PlaylistSong;
import com.bluewhale.music.domain.model.PlaylistType;
import com.bluewhale.music.domain.model.PlaylistVisibility;
import com.bluewhale.music.domain.model.PublishState;
import com.bluewhale.music.domain.model.Song;
import com.bluewhale.music.domain.model.SongVisibility;
import com.bluewhale.music.domain.repository.HomeCacheRepository;
import com.bluewhale.music.domain.repository.PlaylistRepository;
import com.bluewhale.music.domain.repository.SongRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 歌单应用服务单元测试：加歌上限、重复幂等、越权与官方歌单只读。
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
class PlaylistAppServiceImplTest {

    /** 测试租户 */
    private static final Long TENANT_ID = 1L;

    /** 歌单所有者 */
    private static final Long OWNER_ID = 7L;

    /** 其他用户 */
    private static final Long OTHER_USER = 8L;

    /** 用户歌单 ID */
    private static final Long PLAYLIST_ID = 1L;

    /** 官方歌单 ID */
    private static final Long OFFICIAL_ID = 2L;

    /** 歌曲 ID */
    private static final Long SONG_ID = 9L;

    /** 单歌单歌曲上限 */
    private static final int MAX_SONGS = 500;

    @Mock
    private PlaylistRepository playlistRepository;

    @Mock
    private SongRepository songRepository;

    @Mock
    private MusicProperties musicProperties;

    @Mock
    private HomeCacheRepository homeCacheRepository;

    @InjectMocks
    private PlaylistAppServiceImpl playlistAppService;

    /**
     * 清理测试线程的租户上下文。
     */
    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    /**
     * 所有者向空歌单加歌时写入关系并同步歌曲数量。
     */
    @Test
    void shouldAddSongToOwnedPlaylist() {
        when(playlistRepository.findById(PLAYLIST_ID)).thenReturn(Optional.of(userPlaylist()));
        when(playlistRepository.findSongs(PLAYLIST_ID)).thenReturn(List.of());
        when(songRepository.findById(SONG_ID)).thenReturn(Optional.of(playableSong(SONG_ID)));
        when(musicProperties.getPlaylistMaxSongs()).thenReturn(MAX_SONGS);

        PlaylistView view = playlistAppService.addSong(PLAYLIST_ID, addSongCommand(), OWNER_ID);

        ArgumentCaptor<PlaylistSong> captor = ArgumentCaptor.forClass(PlaylistSong.class);
        verify(playlistRepository).insertSong(captor.capture());
        assertEquals(SONG_ID, captor.getValue().getSongId());
        assertEquals(OWNER_ID, captor.getValue().getAddedBy());
        verify(playlistRepository).update(any(Playlist.class));
        assertEquals(PLAYLIST_ID, view.getId());
    }

    /**
     * 重复加入同一歌曲幂等：不新增关系、不更新歌单。
     */
    @Test
    void shouldAddDuplicateSongIdempotently() {
        when(playlistRepository.findById(PLAYLIST_ID)).thenReturn(Optional.of(userPlaylist()));
        when(playlistRepository.findSongs(PLAYLIST_ID)).thenReturn(List.of(songItem(SONG_ID, 0)));
        when(songRepository.findById(SONG_ID)).thenReturn(Optional.of(playableSong(SONG_ID)));
        when(musicProperties.getPlaylistMaxSongs()).thenReturn(MAX_SONGS);

        playlistAppService.addSong(PLAYLIST_ID, addSongCommand(), OWNER_ID);

        verify(playlistRepository, never()).insertSong(any(PlaylistSong.class));
        verify(playlistRepository, never()).update(any(Playlist.class));
    }

    /**
     * 歌单歌曲数达到上限时拒绝加歌。
     */
    @Test
    void shouldRejectSongBeyondLimit() {
        when(playlistRepository.findById(PLAYLIST_ID)).thenReturn(Optional.of(userPlaylist()));
        when(playlistRepository.findSongs(PLAYLIST_ID)).thenReturn(List.of(songItem(1L, 0)));
        when(songRepository.findById(SONG_ID)).thenReturn(Optional.of(playableSong(SONG_ID)));
        when(musicProperties.getPlaylistMaxSongs()).thenReturn(1);

        BizException exception = assertThrows(BizException.class,
                () -> playlistAppService.addSong(PLAYLIST_ID, addSongCommand(), OWNER_ID));

        assertEquals(MusicErrorCode.PLAYLIST_SONG_LIMIT, exception.getCode());
        verify(playlistRepository, never()).insertSong(any(PlaylistSong.class));
    }

    /**
     * 私有歌单对非所有者按不存在处理（404）。
     */
    @Test
    void shouldHidePrivatePlaylistFromOthers() {
        Playlist playlist = userPlaylist();
        playlist.setVisibility(PlaylistVisibility.PRIVATE);
        when(playlistRepository.findById(PLAYLIST_ID)).thenReturn(Optional.of(playlist));

        BizException exception = assertThrows(BizException.class,
                () -> playlistAppService.detail(PLAYLIST_ID, OTHER_USER));

        assertEquals(MusicErrorCode.PLAYLIST_NOT_FOUND, exception.getCode());
    }

    /**
     * 非所有者不能修改他人歌单。
     */
    @Test
    void shouldRejectMutationOfOthersPlaylist() {
        when(playlistRepository.findById(PLAYLIST_ID)).thenReturn(Optional.of(userPlaylist()));

        BizException exception = assertThrows(BizException.class,
                () -> playlistAppService.update(PLAYLIST_ID, saveCommand(), OTHER_USER));

        assertEquals(MusicErrorCode.PLAYLIST_NOT_OWNED, exception.getCode());
    }

    /**
     * 官方歌单用户侧只读，任何用户侧写操作被拒绝。
     */
    @Test
    void shouldRejectUserMutationOfOfficialPlaylist() {
        when(playlistRepository.findById(OFFICIAL_ID)).thenReturn(Optional.of(officialPlaylist()));

        BizException exception = assertThrows(BizException.class,
                () -> playlistAppService.update(OFFICIAL_ID, saveCommand(), OWNER_ID));

        assertEquals(MusicErrorCode.PLAYLIST_OFFICIAL_READONLY, exception.getCode());
    }

    /**
     * 官方歌单创建固定 owner 0、官方类型与公开可见，并失效租户首页缓存。
     */
    @Test
    void shouldCreateOfficialPlaylistAndEvictHomeCache() {
        TenantContext.set(TENANT_ID);

        playlistAppService.createOfficial(saveCommand());

        ArgumentCaptor<Playlist> captor = ArgumentCaptor.forClass(Playlist.class);
        verify(playlistRepository).insert(captor.capture());
        assertEquals(0L, captor.getValue().getOwnerId());
        assertEquals(PlaylistType.OFFICIAL, captor.getValue().getPlaylistType());
        assertEquals(PlaylistVisibility.PUBLIC, captor.getValue().getVisibility());
        verify(homeCacheRepository).evict(TENANT_ID);
    }

    /**
     * 运营可向官方歌单加歌，关系写入且歌单歌曲数量同步。
     */
    @Test
    void shouldAddSongToOfficialPlaylistAsOperator() {
        when(playlistRepository.findById(OFFICIAL_ID)).thenReturn(Optional.of(officialPlaylist()));
        when(playlistRepository.findSongs(OFFICIAL_ID)).thenReturn(List.of());
        when(songRepository.findById(SONG_ID)).thenReturn(Optional.of(playableSong(SONG_ID)));
        when(musicProperties.getPlaylistMaxSongs()).thenReturn(MAX_SONGS);

        PlaylistView view = playlistAppService.addSongAsOperator(OFFICIAL_ID, addSongCommand(), OWNER_ID);

        ArgumentCaptor<PlaylistSong> captor = ArgumentCaptor.forClass(PlaylistSong.class);
        verify(playlistRepository).insertSong(captor.capture());
        assertEquals(SONG_ID, captor.getValue().getSongId());
        verify(playlistRepository).update(any(Playlist.class));
        assertEquals(OFFICIAL_ID, view.getId());
    }

    /**
     * 用户歌单加歌仍要求所有者本人，运营视角亦不能越权。
     */
    @Test
    void shouldRejectAddSongToOthersUserPlaylist() {
        when(playlistRepository.findById(PLAYLIST_ID)).thenReturn(Optional.of(userPlaylist()));

        assertEquals(MusicErrorCode.PLAYLIST_NOT_OWNED,
                assertThrows(BizException.class,
                        () -> playlistAppService.addSong(PLAYLIST_ID, addSongCommand(), OTHER_USER)).getCode());
        assertEquals(MusicErrorCode.PLAYLIST_NOT_OWNED,
                assertThrows(BizException.class,
                        () -> playlistAppService.addSongAsOperator(PLAYLIST_ID, addSongCommand(), OTHER_USER))
                        .getCode());
    }

    /**
     * 构造归属测试所有者的公开用户歌单。
     *
     * @return 歌单
     */
    private Playlist userPlaylist() {
        Playlist playlist = new Playlist();
        playlist.setId(PLAYLIST_ID);
        playlist.setOwnerId(OWNER_ID);
        playlist.setPlaylistType(PlaylistType.USER);
        playlist.setVisibility(PlaylistVisibility.PUBLIC);
        playlist.setTitle("我的歌单");
        playlist.setSongCount(0);
        return playlist;
    }

    /**
     * 构造官方歌单。
     *
     * @return 歌单
     */
    private Playlist officialPlaylist() {
        Playlist playlist = new Playlist();
        playlist.setId(OFFICIAL_ID);
        playlist.setOwnerId(0L);
        playlist.setPlaylistType(PlaylistType.OFFICIAL);
        playlist.setVisibility(PlaylistVisibility.PUBLIC);
        return playlist;
    }

    /**
     * 构造可加入歌单的歌曲。
     *
     * @param songId 歌曲 ID
     * @return 歌曲
     */
    private Song playableSong(Long songId) {
        Song song = new Song();
        song.setId(songId);
        song.setUploaderId(OWNER_ID);
        song.setVisibility(SongVisibility.PUBLIC);
        song.setPublishState(PublishState.PUBLISHED);
        song.setCopyrightState(CopyrightState.VALID);
        song.setAudioFileId(10L);
        return song;
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

    /**
     * 构造加歌命令。
     *
     * @return 加歌命令
     */
    private AddPlaylistSongCommand addSongCommand() {
        AddPlaylistSongCommand command = new AddPlaylistSongCommand();
        command.setSongId(SONG_ID);
        return command;
    }

    /**
     * 构造歌单保存命令。
     *
     * @return 保存命令
     */
    private PlaylistSaveCommand saveCommand() {
        PlaylistSaveCommand command = new PlaylistSaveCommand();
        command.setTitle("我的歌单");
        command.setDescription("测试歌单");
        return command;
    }
}
