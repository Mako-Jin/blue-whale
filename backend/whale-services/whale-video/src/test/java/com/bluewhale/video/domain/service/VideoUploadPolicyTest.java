package com.bluewhale.video.domain.service;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.video.domain.exception.VideoErrorCode;
import com.bluewhale.video.domain.model.FileAsset;
import com.bluewhale.video.domain.model.UploadRule;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 视频上传校验策略单元测试（M1-2）：MIME 白名单、大小上限与 magic bytes。
 *
 * @author BlueWhale
 */
class VideoUploadPolicyTest {

    /** 视频 MIME 白名单 */
    private static final Set<String> VIDEO_TYPES = Set.of("video/mp4", "video/webm");

    /** 图片 MIME 白名单 */
    private static final Set<String> IMAGE_TYPES = Set.of("image/jpeg", "image/png", "image/webp");

    /** 视频类型上限：2GB / 2 小时 */
    private static final UploadRule RULE = new UploadRule(2_147_483_648L, 7_200_000L);

    /** 合法 MP4 文件头（偏移 4 起为 {@code ftyp}） */
    private static final byte[] MP4_HEAD = {0, 0, 0, 0x18, 'f', 't', 'y', 'p', 'm', 'p', '4', '2'};

    /** 合法 WebM 文件头 */
    private static final byte[] WEBM_HEAD = {0x1A, 0x45, (byte) 0xDF, (byte) 0xA3};

    /**
     * 白名单内的 MP4 通过校验，并按 magic bytes 识别为 MP4。
     */
    @Test
    void shouldAcceptMp4ByMagicBytes() {
        assertEquals("MP4", VideoUploadPolicy.validate(
                new FileAsset(1L, "video/mp4", 1024L, 1L, true), MP4_HEAD, VIDEO_TYPES, RULE));
    }

    /**
     * 白名单内的 WebM 通过校验，并按 magic bytes 识别为 WEBM。
     */
    @Test
    void shouldAcceptWebmByMagicBytes() {
        assertEquals("WEBM", VideoUploadPolicy.validate(
                new FileAsset(1L, "video/webm", 1024L, 1L, true), WEBM_HEAD, VIDEO_TYPES, RULE));
    }

    /**
     * 文件头无法识别时按声明 MIME 推断格式。
     */
    @Test
    void shouldFallbackToMimeWhenHeadUnknown() {
        assertEquals("MP4", VideoUploadPolicy.validate(
                new FileAsset(1L, "video/mp4", 1024L, 1L, true), new byte[] {0, 0, 0, 0}, VIDEO_TYPES, RULE));
        assertEquals("MP4", VideoUploadPolicy.validate(
                new FileAsset(1L, "video/mp4", 1024L, 1L, true), null, VIDEO_TYPES, RULE));
    }

    /**
     * 不在白名单内的 MIME 被拒绝（40003）。
     */
    @Test
    void shouldRejectMimeOutsideWhitelist() {
        assertEquals(VideoErrorCode.VIDEO_FORMAT_NOT_ALLOWED,
                assertThrows(BizException.class, () -> VideoUploadPolicy.validate(
                        new FileAsset(1L, "video/avi", 1024L, 1L, true), null, VIDEO_TYPES, RULE)).getCode());
    }

    /**
     * 超过类型大小上限被拒绝（40005）。
     */
    @Test
    void shouldRejectOversizeFile() {
        assertEquals(VideoErrorCode.UPLOAD_FILE_TOO_LARGE,
                assertThrows(BizException.class, () -> VideoUploadPolicy.validate(
                        new FileAsset(1L, "video/mp4", RULE.maxBytes() + 1, 1L, true),
                        MP4_HEAD, VIDEO_TYPES, RULE)).getCode());
    }

    /**
     * 文件不存在、已删除或字节数为 0 均按上传文件非法处理（40006）。
     */
    @Test
    void shouldRejectUnusableAsset() {
        assertEquals(VideoErrorCode.UPLOAD_FILE_INVALID,
                assertThrows(BizException.class, () -> VideoUploadPolicy.validate(
                        null, MP4_HEAD, VIDEO_TYPES, RULE)).getCode());
        assertEquals(VideoErrorCode.UPLOAD_FILE_INVALID,
                assertThrows(BizException.class, () -> VideoUploadPolicy.validate(
                        new FileAsset(1L, "video/mp4", 1024L, 1L, false), MP4_HEAD, VIDEO_TYPES, RULE)).getCode());
        assertEquals(VideoErrorCode.UPLOAD_FILE_INVALID,
                assertThrows(BizException.class, () -> VideoUploadPolicy.validate(
                        new FileAsset(0L, "video/mp4", 1024L, 1L, true), MP4_HEAD, VIDEO_TYPES, RULE)).getCode());
        assertEquals(VideoErrorCode.UPLOAD_FILE_TOO_LARGE,
                assertThrows(BizException.class, () -> VideoUploadPolicy.validate(
                        new FileAsset(1L, "video/mp4", 0L, 1L, true), MP4_HEAD, VIDEO_TYPES, RULE)).getCode());
    }

    /**
     * 声明 MIME 与实际文件头不一致时拒绝（防伪装上传）。
     */
    @Test
    void shouldRejectMismatchedMagicBytes() {
        assertEquals(VideoErrorCode.VIDEO_FORMAT_NOT_ALLOWED,
                assertThrows(BizException.class, () -> VideoUploadPolicy.validate(
                        new FileAsset(1L, "video/mp4", 1024L, 1L, true), WEBM_HEAD, VIDEO_TYPES, RULE)).getCode());
    }

    /**
     * 封面图片白名单独立校验（40004）。
     */
    @Test
    void shouldValidateImageWhitelist() {
        VideoUploadPolicy.validateImage(new FileAsset(2L, "image/png", 512L, 1L, true), IMAGE_TYPES);
        assertEquals(VideoErrorCode.IMAGE_FORMAT_NOT_ALLOWED,
                assertThrows(BizException.class, () -> VideoUploadPolicy.validateImage(
                        new FileAsset(2L, "application/pdf", 512L, 1L, true), IMAGE_TYPES)).getCode());
        assertEquals(VideoErrorCode.UPLOAD_FILE_INVALID,
                assertThrows(BizException.class, () -> VideoUploadPolicy.validateImage(
                        new FileAsset(2L, "image/png", 512L, 1L, false), IMAGE_TYPES)).getCode());
    }

    /**
     * 文件头长度不足或内容未知时无法识别格式。
     */
    @Test
    void shouldDetectFormatOnlyForKnownHeaders() {
        assertNull(VideoUploadPolicy.detectFormat(null));
        assertNull(VideoUploadPolicy.detectFormat(new byte[0]));
        assertNull(VideoUploadPolicy.detectFormat(new byte[] {0x1A, 0x45}));
        assertNull(VideoUploadPolicy.detectFormat(new byte[] {0, 0, 0, 0, 'a', 'b', 'c', 'd'}));
        assertEquals("MP4", VideoUploadPolicy.detectFormat(MP4_HEAD));
        assertEquals("WEBM", VideoUploadPolicy.detectFormat(WEBM_HEAD));
    }
}
