package com.bluewhale.music.application.service.impl;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.music.config.MusicProperties;
import com.bluewhale.music.domain.exception.MusicErrorCode;
import com.bluewhale.music.domain.model.FileAsset;
import com.bluewhale.music.domain.repository.FileAssetPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 歌曲媒体资产校验单元测试（M5-2）。
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
class SongAssetValidatorImplTest {

    @Mock
    private FileAssetPort fileAssetPort;

    private MusicProperties musicProperties;

    private SongAssetValidatorImpl validator;

    @BeforeEach
    void setUp() {
        musicProperties = new MusicProperties();
        validator = new SongAssetValidatorImpl(fileAssetPort, musicProperties);
    }

    /**
     * 白名单内且未超限的音频通过。
     */
    @Test
    void shouldAcceptAllowedAudio() {
        when(fileAssetPort.find(3L, 1L)).thenReturn(Optional.of(
                new FileAsset(3L, "audio/mpeg", 1024L, 1L, true)));
        validator.assertAudio(3L, 1L);
        verify(fileAssetPort).find(3L, 1L);
    }

    /**
     * 非白名单类型（如 WAV）返回 40016。
     */
    @Test
    void shouldRejectAudioOutsideWhitelist() {
        when(fileAssetPort.find(3L, 1L)).thenReturn(Optional.of(
                new FileAsset(3L, "audio/wav", 1024L, 1L, true)));
        BizException exception = assertThrows(BizException.class,
                () -> validator.assertAudio(3L, 1L));
        assertEquals(MusicErrorCode.AUDIO_FORMAT_NOT_ALLOWED, exception.getCode());
    }

    /**
     * 超过配置上限返回 40018（上限按字节、配置可覆盖）。
     */
    @Test
    void shouldRejectOversizeAudio() {
        musicProperties.setUploadMaxBytes(2048L);
        when(fileAssetPort.find(3L, 1L)).thenReturn(Optional.of(
                new FileAsset(3L, "audio/mpeg", 4096L, 1L, true)));
        BizException exception = assertThrows(BizException.class,
                () -> validator.assertAudio(3L, 1L));
        assertEquals(MusicErrorCode.UPLOAD_FILE_TOO_LARGE, exception.getCode());
    }

    /**
     * 文件不存在 / 已删除返回 40019。
     */
    @Test
    void shouldRejectMissingAudio() {
        when(fileAssetPort.find(3L, 1L)).thenReturn(Optional.empty());
        BizException exception = assertThrows(BizException.class,
                () -> validator.assertAudio(3L, 1L));
        assertEquals(MusicErrorCode.UPLOAD_FILE_INVALID, exception.getCode());
    }

    /**
     * 封面为空时不做校验；封面类型非法返回 40017。
     */
    @Test
    void shouldValidateCoverOnlyWhenProvided() {
        validator.assertCover(null, 1L);
        verifyNoInteractions(fileAssetPort);

        when(fileAssetPort.find(9L, 1L)).thenReturn(Optional.of(
                new FileAsset(9L, "application/pdf", 512L, 1L, true)));
        BizException exception = assertThrows(BizException.class,
                () -> validator.assertCover(9L, 1L));
        assertEquals(MusicErrorCode.COVER_FORMAT_NOT_ALLOWED, exception.getCode());
    }

    /**
     * 文件非法时不写库（由调用方保证），这里确认异常在仓储之外提前抛出。
     */
    @Test
    void shouldNotQueryCoverWhenAudioInvalid() {
        when(fileAssetPort.find(anyLong(), any())).thenReturn(Optional.empty());
        assertThrows(BizException.class, () -> validator.assertAudio(3L, 2L));
        verify(fileAssetPort, never()).find(9L, 1L);
    }
}
