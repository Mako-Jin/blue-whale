package com.bluewhale.music.application.service.impl;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.music.application.command.SongSaveCommand;
import com.bluewhale.music.application.service.SongAssetValidator;
import com.bluewhale.music.application.view.SongView;
import com.bluewhale.music.domain.exception.MusicErrorCode;
import com.bluewhale.music.domain.model.Artist;
import com.bluewhale.music.domain.model.CopyrightSource;
import com.bluewhale.music.domain.model.Song;
import com.bluewhale.music.domain.repository.ArtistRepository;
import com.bluewhale.music.domain.repository.HomeCacheRepository;
import com.bluewhale.music.domain.repository.SongRepository;
import com.bluewhale.music.domain.repository.TagRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 歌曲运营应用服务单元测试（M5 上传准入与来源留痕）。
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
class SongAppServiceImplTest {

    @Mock
    private SongRepository songRepository;

    @Mock
    private ArtistRepository artistRepository;

    @Mock
    private TagRepository tagRepository;

    @Mock
    private HomeCacheRepository homeCacheRepository;

    @Mock
    private SongAssetValidator songAssetValidator;

    @InjectMocks
    private SongAppServiceImpl songAppService;

    /**
     * 音频类型不在白名单时创建被拒绝，且不写库。
     */
    @Test
    void shouldRejectCreateWhenAudioNotWhitelisted() {
        when(artistRepository.findById(2L)).thenReturn(Optional.of(enabledArtist()));
        doThrow(MusicErrorCode.exception(MusicErrorCode.AUDIO_FORMAT_NOT_ALLOWED))
                .when(songAssetValidator).assertAudio(3L, null);

        BizException exception = assertThrows(BizException.class,
                () -> songAppService.create(command("ORIGINAL"), 1L));
        assertEquals(MusicErrorCode.AUDIO_FORMAT_NOT_ALLOWED, exception.getCode());
        verify(songRepository, never()).insert(any());
    }

    /**
     * 正常创建时把上传来源写入聚合根与视图。
     */
    @Test
    void shouldPersistCopyrightSourceOnCreate() {
        when(artistRepository.findById(2L)).thenReturn(Optional.of(enabledArtist()));
        ArgumentCaptor<Song> captor = ArgumentCaptor.forClass(Song.class);

        SongView view = songAppService.create(command("AUTHORIZED"), 1L);

        verify(songRepository).insert(captor.capture());
        assertEquals(CopyrightSource.AUTHORIZED, captor.getValue().getSource());
        assertEquals(CopyrightSource.AUTHORIZED, view.getSource());
    }

    /**
     * 来源取值非法时创建被拒绝（40021）。
     */
    @Test
    void shouldRejectCreateWhenSourceInvalid() {
        when(artistRepository.findById(2L)).thenReturn(Optional.of(enabledArtist()));

        BizException exception = assertThrows(BizException.class,
                () -> songAppService.create(command("BORROWED"), 1L));
        assertEquals(MusicErrorCode.COPYRIGHT_SOURCE_INVALID, exception.getCode());
        verify(songRepository, never()).insert(any());
    }

    /**
     * 构造启用中的歌手。
     *
     * @return 歌手
     */
    private Artist enabledArtist() {
        Artist artist = new Artist();
        artist.setId(2L);
        artist.setName("周杰伦");
        artist.setStatus(1);
        return artist;
    }

    /**
     * 构造最小可用的歌曲保存命令。
     *
     * @param source 作品来源
     * @return 保存命令
     */
    private SongSaveCommand command(String source) {
        SongSaveCommand command = new SongSaveCommand();
        command.setArtistId(2L);
        command.setTitle("晴天");
        command.setAudioFileId(3L);
        command.setDurationMs(269000L);
        command.setSource(source);
        return command;
    }
}
