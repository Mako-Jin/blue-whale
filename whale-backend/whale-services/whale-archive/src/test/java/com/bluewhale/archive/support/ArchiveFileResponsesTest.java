package com.bluewhale.archive.support;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 档案文件响应支撑单元测试：内联预览判定与 Content-Disposition 构造。
 *
 * @author BlueWhale
 */
class ArchiveFileResponsesTest {

    @Test
    @DisplayName("内联预览：图片 / 音视频 / 文本 / PDF 允许，压缩包等其余类型按附件")
    void shouldRecognizeInlinePreviewTypes() {
        assertTrue(ArchiveFileResponses.isInlinePreviewable("image/png"));
        assertTrue(ArchiveFileResponses.isInlinePreviewable("video/mp4"));
        assertTrue(ArchiveFileResponses.isInlinePreviewable("audio/mpeg"));
        assertTrue(ArchiveFileResponses.isInlinePreviewable("text/plain; charset=UTF-8"));
        assertTrue(ArchiveFileResponses.isInlinePreviewable("application/pdf"));
        assertTrue(ArchiveFileResponses.isInlinePreviewable("IMAGE/PNG"));
        assertFalse(ArchiveFileResponses.isInlinePreviewable("application/zip"));
        assertFalse(ArchiveFileResponses.isInlinePreviewable("application/octet-stream"));
        assertFalse(ArchiveFileResponses.isInlinePreviewable(null));
        assertFalse(ArchiveFileResponses.isInlinePreviewable("  "));
    }

    @Test
    @DisplayName("MIME 解析：空白与非法值回落为 application/octet-stream")
    void shouldFallbackMediaType() {
        assertEquals(MediaType.parseMediaType("video/mp4"),
                ArchiveFileResponses.mediaType("video/mp4"));
        assertEquals(MediaType.APPLICATION_OCTET_STREAM, ArchiveFileResponses.mediaType(null));
        assertEquals(MediaType.APPLICATION_OCTET_STREAM, ArchiveFileResponses.mediaType(" "));
        assertEquals(MediaType.APPLICATION_OCTET_STREAM, ArchiveFileResponses.mediaType("非法 MIME"));
    }

    @Test
    @DisplayName("Content-Disposition：预览为 inline、下载为 attachment，并带 RFC 5987 文件名")
    void shouldBuildContentDisposition() {
        String inline = ArchiveFileResponses.contentDisposition("年度报告.pdf", true);
        assertTrue(inline.startsWith("inline;"));
        assertTrue(inline.contains("filename*=UTF-8''"));
        assertTrue(inline.contains("__.")); // ASCII 兜底把中文替换为下划线

        String attachment = ArchiveFileResponses.contentDisposition("report.pdf", false);
        assertTrue(attachment.startsWith("attachment; filename=\"report.pdf\""));
        assertTrue(attachment.contains("filename*=UTF-8''report.pdf"));
    }

    @Test
    @DisplayName("Content-Disposition：空文件名回落兜底名")
    void shouldFallbackFileName() {
        String header = ArchiveFileResponses.contentDisposition(null, false);
        assertTrue(header.contains("filename=\"archive-file\""));
        assertTrue(header.contains("filename*=UTF-8''archive-file"));
    }
}
