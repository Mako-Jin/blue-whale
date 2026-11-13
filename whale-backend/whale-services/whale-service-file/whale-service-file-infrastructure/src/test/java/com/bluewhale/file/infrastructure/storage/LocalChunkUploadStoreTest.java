package com.bluewhale.file.infrastructure.storage;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.file.config.FileProperties;
import com.bluewhale.file.domain.storage.ChunkUploadMetadata;
import com.bluewhale.file.domain.storage.ChunkUploadSessionId;
import com.bluewhale.file.domain.storage.MergedChunkUpload;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.util.DigestUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link LocalChunkUploadStore} 本机磁盘分片暂存：会话初始化、分片暂存、合并与清理单元测试。
 *
 * @author BlueWhale
 */
class LocalChunkUploadStoreTest {

    private static final int PARAM_ERROR_CODE = 400;

    @TempDir
    Path tempDir;

    private LocalChunkUploadStore store;

    /**
     * 以临时目录作为存储根目录构造被测实现
     */
    @BeforeEach
    void setUp() {
        FileProperties properties = new FileProperties();
        properties.getStorage().setRoot(tempDir.toString());
        store = new LocalChunkUploadStore(properties);
    }

    /**
     * 构造分片会话元数据
     *
     * @param size 文件总字节数
     * @return 会话元数据
     */
    private static ChunkUploadMetadata metadata(long size) {
        return new ChunkUploadMetadata("video.bin", "video/mp4", size);
    }

    /**
     * 暂存单个分片
     *
     * @param sessionId 会话标识
     * @param index 分片索引
     * @param content 分片文本内容
     */
    private void storePart(ChunkUploadSessionId sessionId, int index, String content) {
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        store.storePart(sessionId, index, new ByteArrayInputStream(bytes), bytes.length);
    }

    @Test
    @DisplayName("initialize：创建会话目录并允许重复初始化以支持续传")
    void shouldInitializeAndResume() {
        ChunkUploadSessionId sessionId = ChunkUploadSessionId.random();

        store.initialize(sessionId, metadata(12L));
        storePart(sessionId, 0, "hello");

        store.initialize(sessionId, metadata(12L));

        assertTrue(store.exists(sessionId));
        assertEquals(List.of(0), store.uploadedPartIndexes(sessionId));
        assertTrue(Files.isDirectory(tempDir.resolve(".chunk-upload").resolve(sessionId.value())));
    }

    @Test
    @DisplayName("uploadedPartIndexes：按索引升序返回已暂存分片，忽略非分片文件")
    void shouldListUploadedIndexesInOrder() throws IOException {
        ChunkUploadSessionId sessionId = ChunkUploadSessionId.random();
        store.initialize(sessionId, metadata(12L));

        storePart(sessionId, 1, " whale!");
        storePart(sessionId, 0, "hello");
        Files.writeString(tempDir.resolve(".chunk-upload").resolve(sessionId.value()).resolve("notes.txt"), "x");

        assertEquals(List.of(0, 1), store.uploadedPartIndexes(sessionId));
    }

    @Test
    @DisplayName("uploadedPartIndexes：会话不存在时返回空列表")
    void shouldReturnEmptyIndexesWhenSessionMissing() {
        assertTrue(store.uploadedPartIndexes(ChunkUploadSessionId.random()).isEmpty());
    }

    @Test
    @DisplayName("merge：按索引顺序合并，并回传大小、MIME 类型与 MD5")
    void shouldMergeParts() throws IOException {
        ChunkUploadSessionId sessionId = ChunkUploadSessionId.random();
        store.initialize(sessionId, metadata(12L));
        storePart(sessionId, 0, "hello");
        storePart(sessionId, 1, " whale!");

        byte[] expected = "hello whale!".getBytes(StandardCharsets.UTF_8);
        try (MergedChunkUpload merged = store.merge(sessionId, 2)) {
            assertEquals(expected.length, merged.size());
            assertEquals("video/mp4", merged.contentType());
            assertEquals(DigestUtils.md5DigestAsHex(expected), merged.md5());
            assertArrayEquals(expected, merged.content().readAllBytes());
        }
    }

    @Test
    @DisplayName("merge：缺少分片时抛参数错误并提示索引")
    void shouldRejectMissingPart() {
        ChunkUploadSessionId sessionId = ChunkUploadSessionId.random();
        store.initialize(sessionId, metadata(12L));
        storePart(sessionId, 0, "hello");

        BizException ex = assertThrows(BizException.class, () -> store.merge(sessionId, 2));

        assertEquals(PARAM_ERROR_CODE, ex.getCode());
        assertTrue(ex.getMessage().contains("缺少分片索引: 1"));
        assertFalse(Files.exists(tempDir.resolve(".chunk-upload").resolve(sessionId.value()).resolve("merged.bin")),
                "缺片失败时应清理合并半成品");
    }

    @Test
    @DisplayName("merge/storePart：会话不存在时抛参数错误")
    void shouldRejectUnknownSession() {
        ChunkUploadSessionId sessionId = ChunkUploadSessionId.random();

        BizException mergeError = assertThrows(BizException.class, () -> store.merge(sessionId, 1));
        assertEquals(PARAM_ERROR_CODE, mergeError.getCode());

        byte[] bytes = "x".getBytes(StandardCharsets.UTF_8);
        BizException storeError = assertThrows(BizException.class, () -> store.storePart(sessionId, 0,
                new ByteArrayInputStream(bytes), bytes.length));
        assertEquals(PARAM_ERROR_CODE, storeError.getCode());
    }

    @Test
    @DisplayName("discard：删除会话目录，重复调用保持静默")
    void shouldDiscardSession() {
        ChunkUploadSessionId sessionId = ChunkUploadSessionId.random();
        store.initialize(sessionId, metadata(12L));
        storePart(sessionId, 0, "hello");

        store.discard(sessionId);

        assertFalse(store.exists(sessionId));
        assertFalse(Files.exists(tempDir.resolve(".chunk-upload").resolve(sessionId.value())));

        store.discard(sessionId);
    }

    @Test
    @DisplayName("exists：未初始化会话返回 false")
    void shouldReportMissingSession() {
        assertFalse(store.exists(ChunkUploadSessionId.random()));
    }

    @Test
    @DisplayName("initialize：清理超过保留期的暂存会话，保留活跃会话")
    void shouldPurgeExpiredSessionsOnInitialize() throws IOException {
        FileProperties properties = new FileProperties();
        properties.getStorage().setRoot(tempDir.toString());
        properties.setChunkUploadTtlHours(1L);
        LocalChunkUploadStore ttlStore = new LocalChunkUploadStore(properties);
        ChunkUploadSessionId expired = ChunkUploadSessionId.random();
        ChunkUploadSessionId active = ChunkUploadSessionId.random();
        ttlStore.initialize(expired, metadata(12L));
        ttlStore.initialize(active, metadata(12L));

        Path expiredDir = tempDir.resolve(".chunk-upload").resolve(expired.value());
        long stale = System.currentTimeMillis() - 2 * 3_600_000L;
        Files.setLastModifiedTime(expiredDir, FileTime.fromMillis(stale));
        Files.setLastModifiedTime(expiredDir.resolve("meta.properties"), FileTime.fromMillis(stale));

        // 使用新实例，保证本次初始化不被限频跳过
        LocalChunkUploadStore sweeper = new LocalChunkUploadStore(properties);
        sweeper.initialize(ChunkUploadSessionId.random(), metadata(12L));

        assertFalse(sweeper.exists(expired));
        assertTrue(sweeper.exists(active));
    }

    @Test
    @DisplayName("metadata：回传文件名、MIME、大小与 init 登记的生效上传上限")
    void shouldReturnRecordedMetadata() {
        ChunkUploadSessionId sessionId = ChunkUploadSessionId.random();
        store.initialize(sessionId, new ChunkUploadMetadata("video.bin", "video/mp4", 2147483648L,
                2147483648L, 8589934592L));

        ChunkUploadMetadata meta = store.metadata(sessionId);

        assertEquals("video.bin", meta.fileName());
        assertEquals("video/mp4", meta.contentType());
        assertEquals(2147483648L, meta.size());
        assertEquals(2147483648L, meta.maxSizeBytes());
        assertEquals(8589934592L, meta.quotaBytes());
        assertTrue(meta.hasRecordedLimits());
    }

    @Test
    @DisplayName("metadata：未登记上限返回未记录哨兵值，会话缺失返回 null")
    void shouldReturnNullMetadataWhenSessionMissing() {
        ChunkUploadSessionId sessionId = ChunkUploadSessionId.random();
        store.initialize(sessionId, metadata(12L));

        ChunkUploadMetadata meta = store.metadata(sessionId);
        assertEquals(ChunkUploadMetadata.LIMITS_NOT_RECORDED, meta.maxSizeBytes());
        assertFalse(meta.hasRecordedLimits());

        assertNull(store.metadata(ChunkUploadSessionId.random()));
    }

    @Test
    @DisplayName("storePart：异常读取流时抛系统错误")
    void shouldFailWhenStreamUnreadable() {
        ChunkUploadSessionId sessionId = ChunkUploadSessionId.random();
        store.initialize(sessionId, metadata(12L));
        InputStream broken = new InputStream() {
            @Override
            public int read() throws IOException {
                throw new IOException("boom");
            }
        };

        BizException ex = assertThrows(BizException.class,
                () -> store.storePart(sessionId, 0, broken, 1L));

        assertEquals(ErrorCode.SYSTEM_ERROR.getCode(), ex.getCode());
    }
}
