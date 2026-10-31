package com.bluewhale.music.application.service.impl;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.music.application.command.SongSaveCommand;
import com.bluewhale.music.application.service.CopyrightAgreementAppService;
import com.bluewhale.music.application.service.SongAppService;
import com.bluewhale.music.domain.exception.MusicErrorCode;
import com.bluewhale.music.domain.model.CopyrightState;
import com.bluewhale.music.domain.model.PublishState;
import com.bluewhale.music.domain.model.Song;
import com.bluewhale.music.domain.model.SongVisibility;
import com.bluewhale.music.domain.repository.SongRepository;
import com.bluewhale.music.domain.repository.TagRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 用户上传与作品管理应用服务单元测试。
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
class UserSongAppServiceImplTest {

    @Mock
    private SongRepository songRepository;

    @Mock
    private TagRepository tagRepository;

    @Mock
    private SongAppService songAppService;

    @Mock
    private CopyrightAgreementAppService agreementAppService;

    @InjectMocks
    private UserSongAppServiceImpl userSongAppService;

    /**
     * 未签署当前版权协议时上传被拒绝，且不会创建歌曲。
     */
    @Test
    void shouldRejectUploadWithoutAgreement() {
        doThrow(MusicErrorCode.exception(MusicErrorCode.AGREEMENT_REQUIRED))
                .when(agreementAppService).assertAccepted(1L);
        BizException exception = assertThrows(BizException.class,
                () -> userSongAppService.upload(saveCommand(), 1L));
        assertEquals(MusicErrorCode.AGREEMENT_REQUIRED, exception.getCode());
        verify(songAppService, never()).create(any(), anyLong());
    }

    /**
     * 已签协议但未声明作品来源时上传被拒绝（M5-3）。
     */
    @Test
    void shouldRejectUploadWithoutSource() {
        BizException exception = assertThrows(BizException.class,
                () -> userSongAppService.upload(saveCommand(), 1L));
        assertEquals(MusicErrorCode.COPYRIGHT_SOURCE_REQUIRED, exception.getCode());
        verify(songAppService, never()).create(any(), anyLong());
    }

    /**
     * 不能编辑他人作品。
     */
    @Test
    void shouldRejectUpdateOfOthersSong() {
        when(songRepository.findById(9L)).thenReturn(Optional.of(songOf(1L)));
        BizException exception = assertThrows(BizException.class,
                () -> userSongAppService.update(9L, saveCommand(), 2L));
        assertEquals(MusicErrorCode.SONG_NOT_OWNED, exception.getCode());
    }

    /**
     * 自听作品对非上传者按不存在处理。
     */
    @Test
    void shouldHidePrivateSongFromOthers() {
        Song song = songOf(1L);
        song.setVisibility(SongVisibility.PRIVATE);
        song.setPublishState(PublishState.PUBLISHED);
        song.setCopyrightState(CopyrightState.VALID);
        when(songRepository.findById(5L)).thenReturn(Optional.of(song));
        BizException exception = assertThrows(BizException.class,
                () -> userSongAppService.publicDetail(5L, 2L));
        assertEquals(MusicErrorCode.SONG_NOT_FOUND, exception.getCode());
    }

    /**
     * 编辑他人自听作品时按不存在处理，不泄露作品存在性（M5-1）。
     */
    @Test
    void shouldHidePrivateSongOnUpdateByOthers() {
        Song song = songOf(1L);
        song.setVisibility(SongVisibility.PRIVATE);
        when(songRepository.findById(9L)).thenReturn(Optional.of(song));
        BizException exception = assertThrows(BizException.class,
                () -> userSongAppService.update(9L, saveCommand(), 2L));
        assertEquals(MusicErrorCode.SONG_NOT_FOUND, exception.getCode());
        verify(songAppService, never()).update(anyLong(), any());
    }

    /**
     * 删除他人自听作品同样按不存在处理（M5-1）。
     */
    @Test
    void shouldHidePrivateSongOnDeleteByOthers() {
        Song song = songOf(1L);
        song.setVisibility(SongVisibility.PRIVATE);
        when(songRepository.findById(9L)).thenReturn(Optional.of(song));
        BizException exception = assertThrows(BizException.class,
                () -> userSongAppService.delete(9L, 2L));
        assertEquals(MusicErrorCode.SONG_NOT_FOUND, exception.getCode());
        verify(songAppService, never()).delete(9L);
    }

    /**
     * 构造最小可用的歌曲保存命令。
     *
     * @return 保存命令
     */
    private SongSaveCommand saveCommand() {
        SongSaveCommand command = new SongSaveCommand();
        command.setArtistId(2L);
        command.setTitle("晴天");
        command.setAudioFileId(3L);
        command.setDurationMs(269000L);
        return command;
    }

    /**
     * 构造指定上传者的歌曲。
     *
     * @param uploaderId 上传者 ID
     * @return 歌曲
     */
    private Song songOf(Long uploaderId) {
        Song song = new Song();
        song.setId(9L);
        song.setUploaderId(uploaderId);
        song.setVisibility(SongVisibility.PUBLIC);
        song.setPublishState(PublishState.PUBLISHED);
        song.setCopyrightState(CopyrightState.VALID);
        return song;
    }
}
