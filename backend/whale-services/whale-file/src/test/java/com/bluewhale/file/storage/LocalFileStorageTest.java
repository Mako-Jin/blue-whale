package com.bluewhale.file.storage;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.file.config.FileProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link LocalFileStorage} 本地落盘、读取、删除与目录穿越防护单元测试。
 *
 * @author BlueWhale
 */
class LocalFileStorageTest {

    @TempDir
    Path tempDir;

    private LocalFileStorage localStorage;

    /**
     * 以临时目录作为存储根目录构造被测对象。
     */
    @BeforeEach
    void setUp() {
        FileProperties properties = new FileProperties();
        properties.getStorage().setRoot(tempDir.toString());
        localStorage = new LocalFileStorage(properties);
    }

    @Test
    @DisplayName("store/load：按 root/bucket/objectKey 落盘且内容往返一致")
    void shouldStoreAndLoad() throws IOException {
        byte[] content = "hello whale".getBytes(StandardCharsets.UTF_8);
        localStorage.store("bluewhale", "2026/10/19/a.txt", new ByteArrayInputStream(content));

        assertTrue(Files.exists(tempDir.resolve("bluewhale").resolve("2026/10/19/a.txt")));
        try (InputStream in = localStorage.load("bluewhale", "2026/10/19/a.txt")) {
            assertArrayEquals(content, in.readAllBytes());
        }
    }

    @Test
    @DisplayName("load：文件不存在抛 NOT_FOUND")
    void shouldThrowWhenLoadMissing() {
        BizException ex = assertThrows(BizException.class, () -> localStorage.load("bluewhale", "missing.txt"));

        assertEquals(ErrorCode.NOT_FOUND.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("delete：物理文件被移除")
    void shouldDelete() {
        byte[] content = "x".getBytes(StandardCharsets.UTF_8);
        localStorage.store("bluewhale", "a.txt", new ByteArrayInputStream(content));
        Path stored = tempDir.resolve("bluewhale").resolve("a.txt");

        localStorage.delete("bluewhale", "a.txt");

        assertFalse(Files.exists(stored));
    }

    @Test
    @DisplayName("目录穿越：objectKey 含 .. 抛参数错误")
    void shouldRejectTraversal() {
        byte[] content = "x".getBytes(StandardCharsets.UTF_8);
        ByteArrayInputStream in = new ByteArrayInputStream(content);

        BizException ex = assertThrows(BizException.class,
                () -> localStorage.store("bluewhale", "../evil.txt", in));

        assertEquals(ErrorCode.PARAM_ERROR.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("presignedDownloadUrl：local 模式不支持，抛参数错误并提示仅 MinIO 支持")
    void shouldRejectPresignOnLocalStorage() {
        BizException ex = assertThrows(BizException.class,
                () -> localStorage.presignedDownloadUrl("bluewhale", "a.txt", 600));

        assertEquals(ErrorCode.PARAM_ERROR.getCode(), ex.getCode());
        assertTrue(ex.getMessage().contains("仅 MinIO 存储支持预签名"));
    }
}
