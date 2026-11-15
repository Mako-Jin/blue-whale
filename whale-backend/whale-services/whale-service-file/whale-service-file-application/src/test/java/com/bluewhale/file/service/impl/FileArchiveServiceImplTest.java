package com.bluewhale.file.service.impl;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.file.config.FileProperties;
import com.bluewhale.file.model.result.FileArchiveEntryResult;
import com.bluewhale.file.model.result.FileInfoResult;
import com.bluewhale.file.service.FileInfoService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link FileArchiveServiceImpl} 打包条目命名、上限校验与流式 ZIP 内容单元测试。
 *
 * @author BlueWhale
 */
class FileArchiveServiceImplTest {

    private static final int PARAM_ERROR_CODE = 400;

    private static final long UPLOADER_ID = 9L;

    private final FileInfoService fileInfoService = mock(FileInfoService.class);

    private final FileProperties fileProperties = new FileProperties();

    private final FileArchiveServiceImpl archiveService =
            new FileArchiveServiceImpl(fileInfoService, fileProperties);

    /**
     * 构造文件元数据结果
     *
     * @param id 文件ID
     * @param originalName 原始文件名
     * @param size 字节数
     * @return 文件元数据
     */
    private static FileInfoResult info(long id, String originalName, long size) {
        return new FileInfoResult(id, "stored-" + id, originalName, "bluewhale",
                "tenants/1/2026/11/15/" + id, size, "application/octet-stream", null, UPLOADER_ID, null);
    }

    @Test
    @DisplayName("prepare：重复 ID 去重，同名文件在扩展名前追加序号")
    void shouldDedupeIdsAndEntryNames() {
        when(fileInfoService.getDetail(1L, UPLOADER_ID, false)).thenReturn(info(1L, "report.pdf", 10L));
        when(fileInfoService.getDetail(2L, UPLOADER_ID, false)).thenReturn(info(2L, "report.pdf", 20L));

        List<FileArchiveEntryResult> entries = archiveService.prepare(List.of(1L, 2L, 1L), UPLOADER_ID, false);

        assertEquals(2, entries.size());
        assertEquals("report.pdf", entries.get(0).entryName());
        assertEquals("report (1).pdf", entries.get(1).entryName());
        assertEquals(20L, entries.get(1).fileSize());
        verify(fileInfoService).getDetail(2L, UPLOADER_ID, false);
    }

    @Test
    @DisplayName("prepare：条目名剥离目录、剔除控制字符，非法名回落占位名")
    void shouldSanitizeEntryNames() {
        when(fileInfoService.getDetail(1L, UPLOADER_ID, false)).thenReturn(info(1L, "../etc/passwd", 10L));
        when(fileInfoService.getDetail(2L, UPLOADER_ID, false)).thenReturn(info(2L, "bad\u0001name.txt", 10L));
        when(fileInfoService.getDetail(3L, UPLOADER_ID, false)).thenReturn(info(3L, "..", 10L));

        List<FileArchiveEntryResult> entries = archiveService.prepare(List.of(1L, 2L, 3L), UPLOADER_ID, false);

        assertEquals("passwd", entries.get(0).entryName());
        assertEquals("bad_name.txt", entries.get(1).entryName());
        assertEquals("file", entries.get(2).entryName());
    }

    @Test
    @DisplayName("prepare：超过文件数上限整体拒绝且不读取文件")
    void shouldRejectOversizedFileCount() {
        fileProperties.getBatch().setMaxFiles(1);

        BizException ex = assertThrows(BizException.class,
                () -> archiveService.prepare(List.of(1L, 2L), UPLOADER_ID, false));

        assertEquals(PARAM_ERROR_CODE, ex.getCode());
        assertTrue(ex.getMessage().contains("最多打包"));
        verify(fileInfoService, never()).getDetail(anyLong(), anyLong(), anyBoolean());
    }

    @Test
    @DisplayName("prepare：未压缩总字节超出上限整体拒绝")
    void shouldRejectOversizedTotalBytes() {
        fileProperties.getBatch().setMaxZipBytes(10L);
        when(fileInfoService.getDetail(1L, UPLOADER_ID, false)).thenReturn(info(1L, "a.txt", 6L));
        when(fileInfoService.getDetail(2L, UPLOADER_ID, false)).thenReturn(info(2L, "b.txt", 6L));

        BizException ex = assertThrows(BizException.class,
                () -> archiveService.prepare(List.of(1L, 2L), UPLOADER_ID, false));

        assertEquals(PARAM_ERROR_CODE, ex.getCode());
        assertTrue(ex.getMessage().contains("总大小"));
    }

    @Test
    @DisplayName("prepare：存在不可见文件时整体失败")
    void shouldFailWhenAnyFileInvisible() {
        when(fileInfoService.getDetail(1L, UPLOADER_ID, false)).thenReturn(info(1L, "a.txt", 6L));
        when(fileInfoService.getDetail(2L, UPLOADER_ID, false))
                .thenThrow(new BizException(ErrorCode.NOT_FOUND));

        BizException ex = assertThrows(BizException.class,
                () -> archiveService.prepare(List.of(1L, 2L), UPLOADER_ID, false));

        assertEquals(ErrorCode.NOT_FOUND.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("prepare：空列表拒绝")
    void shouldRejectEmptyIds() {
        BizException ex = assertThrows(BizException.class,
                () -> archiveService.prepare(List.of(), UPLOADER_ID, false));

        assertEquals(PARAM_ERROR_CODE, ex.getCode());
    }

    @Test
    @DisplayName("writeZip：按条目名与内容写出 ZIP")
    void shouldStreamZipEntries() throws IOException {
        List<FileArchiveEntryResult> entries = List.of(
                new FileArchiveEntryResult(1L, "a.txt", 5L),
                new FileArchiveEntryResult(2L, "a (1).txt", 4L));
        when(fileInfoService.openContent(1L, UPLOADER_ID, false))
                .thenReturn(new ByteArrayInputStream("alpha".getBytes(StandardCharsets.UTF_8)));
        when(fileInfoService.openContent(2L, UPLOADER_ID, false))
                .thenReturn(new ByteArrayInputStream("beta".getBytes(StandardCharsets.UTF_8)));
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        archiveService.writeZip(entries, UPLOADER_ID, false, out);

        Map<String, String> contents = unzip(out.toByteArray());
        assertEquals(2, contents.size());
        assertEquals("alpha", contents.get("a.txt"));
        assertEquals("beta", contents.get("a (1).txt"));
    }

    /**
     * 解压 ZIP 字节为「条目名 → 文本内容」
     *
     * @param bytes ZIP 字节
     * @return 条目内容映射（保持条目顺序）
     * @throws IOException 解压失败时抛出
     */
    private static Map<String, String> unzip(byte[] bytes) throws IOException {
        Map<String, String> contents = new LinkedHashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry entry = zip.getNextEntry();
            while (entry != null) {
                contents.put(entry.getName(), new String(zip.readAllBytes(), StandardCharsets.UTF_8));
                entry = zip.getNextEntry();
            }
        }
        return contents;
    }
}
