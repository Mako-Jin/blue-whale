package com.bluewhale.music.domain.service;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.music.domain.exception.MusicErrorCode;
import com.bluewhale.music.domain.model.FileAsset;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 上传准入策略单元测试（M5-2）。
 *
 * @author BlueWhale
 */
class UploadPolicyTest {

    /** 音频白名单 */
    private static final List<String> AUDIO_FORMATS = List.of("audio/mpeg", "audio/aac", "audio/flac");

    /** 封面白名单 */
    private static final List<String> COVER_FORMATS = List.of("image/jpeg", "image/png", "image/webp");

    /** 大小上限 100MB */
    private static final long MAX_BYTES = 104857600L;

    /**
     * 白名单内音频通过校验，大小与类型大小写、参数均不敏感。
     */
    @Test
    void shouldAcceptAllowedAudio() {
        UploadPolicy.assertAudio(new FileAsset(1L, "audio/mpeg", 1024L, 1L, true), AUDIO_FORMATS, MAX_BYTES);
        UploadPolicy.assertAudio(new FileAsset(2L, "Audio/FLAC; codecs=flac", 2048L, 1L, true),
                AUDIO_FORMATS, MAX_BYTES);
    }

    /**
     * 不在白名单内的类型被拒绝（40016）。
     */
    @Test
    void shouldRejectAudioTypeOutsideWhitelist() {
        BizException exception = assertThrows(BizException.class, () -> UploadPolicy.assertAudio(
                new FileAsset(1L, "audio/wav", 1024L, 1L, true), AUDIO_FORMATS, MAX_BYTES));
        assertEquals(MusicErrorCode.AUDIO_FORMAT_NOT_ALLOWED, exception.getCode());
    }

    /**
     * 超大小上限被拒绝（40018），类型未提供按非法类型处理。
     */
    @Test
    void shouldRejectOversizeAudio() {
        BizException exception = assertThrows(BizException.class, () -> UploadPolicy.assertAudio(
                new FileAsset(1L, "audio/mpeg", MAX_BYTES + 1, 1L, true), AUDIO_FORMATS, MAX_BYTES));
        assertEquals(MusicErrorCode.UPLOAD_FILE_TOO_LARGE, exception.getCode());
    }

    /**
     * 文件不存在或不可用按上传文件非法处理（40019）。
     */
    @Test
    void shouldRejectUnusableAsset() {
        BizException exception = assertThrows(BizException.class, () -> UploadPolicy.assertAudio(
                new FileAsset(1L, "audio/mpeg", 1024L, 1L, false), AUDIO_FORMATS, MAX_BYTES));
        assertEquals(MusicErrorCode.UPLOAD_FILE_INVALID, exception.getCode());

        assertEquals(MusicErrorCode.UPLOAD_FILE_INVALID,
                assertThrows(BizException.class, () -> UploadPolicy.assertAudio(null, AUDIO_FORMATS, MAX_BYTES))
                        .getCode());
        assertEquals(MusicErrorCode.UPLOAD_FILE_INVALID,
                assertThrows(BizException.class, () -> UploadPolicy.assertAudio(
                        new FileAsset(1L, "audio/mpeg", 0L, 1L, true), AUDIO_FORMATS, MAX_BYTES)).getCode());
    }

    /**
     * 封面白名单独立校验（40017）。
     */
    @Test
    void shouldValidateCoverFormats() {
        UploadPolicy.assertCover(new FileAsset(1L, "image/png", 512L, 1L, true), COVER_FORMATS, MAX_BYTES);
        BizException exception = assertThrows(BizException.class, () -> UploadPolicy.assertCover(
                new FileAsset(1L, "application/pdf", 512L, 1L, true), COVER_FORMATS, MAX_BYTES));
        assertEquals(MusicErrorCode.COVER_FORMAT_NOT_ALLOWED, exception.getCode());
    }

    /**
     * 类型匹配工具方法：空白类型、空白名单均不命中。
     */
    @Test
    void shouldMatchContentTypeCaseInsensitively() {
        FileAsset asset = new FileAsset(1L, " audio/mpeg ", 1024L, 1L, true);
        assertTrue(asset.hasType(AUDIO_FORMATS));
        assertFalse(asset.hasType(List.of()));
        assertFalse(new FileAsset(1L, null, 1024L, 1L, true).hasType(AUDIO_FORMATS));
    }
}
