package com.bluewhale.file.storage.plugin.local;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
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
 * {@link LocalStoragePlugin} 本地落盘、读取、删除与目录穿越防护单元测试。
 *
 * @author BlueWhale
 */
class LocalStoragePluginTest {

    @TempDir
    Path tempDir;

    private LocalStoragePlugin plugin;

    /**
     * 以临时目录作为存储根目录构造被测插件。
     */
    @BeforeEach
    void setUp() {
        LocalStorageProperties properties = new LocalStorageProperties();
        properties.setRoot(tempDir.toString());
        plugin = new LocalStoragePlugin(properties);
    }

    @Test
    @DisplayName("插件元信息：type=local、展示名非空、不支持预签名")
    void shouldExposeMetadata() {
        assertEquals(LocalStoragePlugin.TYPE, plugin.getType());
        assertEquals("本地文件系统", plugin.getDisplayName());
        assertFalse(plugin.supportsPresigned());
    }

    @Test
    @DisplayName("store/load：按 root/bucket/objectKey 落盘且内容往返一致")
    void shouldStoreAndLoad() throws IOException {
        byte[] content = "hello whale".getBytes(StandardCharsets.UTF_8);
        plugin.store("bluewhale", "2026/10/19/a.txt", new ByteArrayInputStream(content));

        assertTrue(Files.exists(tempDir.resolve("bluewhale").resolve("2026/10/19/a.txt")));
        try (InputStream in = plugin.load("bluewhale", "2026/10/19/a.txt")) {
            assertArrayEquals(content, in.readAllBytes());
        }
    }

    @Test
    @DisplayName("load(offset,length)：仅返回请求区间字节")
    void shouldLoadRange() throws IOException {
        byte[] content = "hello whale".getBytes(StandardCharsets.UTF_8);
        plugin.store("bluewhale", "range.txt", new ByteArrayInputStream(content));

        try (InputStream in = plugin.load("bluewhale", "range.txt", 6, 5)) {
            assertArrayEquals("whale".getBytes(StandardCharsets.UTF_8), in.readAllBytes());
        }
    }

    @Test
    @DisplayName("load：文件不存在抛 NOT_FOUND")
    void shouldThrowWhenLoadMissing() {
        BizException ex = assertThrows(BizException.class, () -> plugin.load("bluewhale", "missing.txt"));

        assertEquals(ErrorCode.NOT_FOUND.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("delete：物理文件被移除")
    void shouldDelete() {
        byte[] content = "x".getBytes(StandardCharsets.UTF_8);
        plugin.store("bluewhale", "a.txt", new ByteArrayInputStream(content));
        Path stored = tempDir.resolve("bluewhale").resolve("a.txt");

        plugin.delete("bluewhale", "a.txt");

        assertFalse(Files.exists(stored));
    }

    @Test
    @DisplayName("目录穿越：objectKey 含 .. 抛参数错误")
    void shouldRejectTraversal() {
        byte[] content = "x".getBytes(StandardCharsets.UTF_8);
        ByteArrayInputStream in = new ByteArrayInputStream(content);

        BizException ex = assertThrows(BizException.class,
                () -> plugin.store("bluewhale", "../evil.txt", in));

        assertEquals(ErrorCode.PARAM_ERROR.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("参数校验：桶名为空或含 .. 段、对象键为空白时抛参数错误")
    void shouldRejectBlankBucketOrObjectKey() {
        byte[] content = "x".getBytes(StandardCharsets.UTF_8);

        assertEquals(ErrorCode.PARAM_ERROR.getCode(), assertThrows(BizException.class,
                () -> plugin.store(null, "a.txt", new ByteArrayInputStream(content))).getCode());
        assertEquals(ErrorCode.PARAM_ERROR.getCode(), assertThrows(BizException.class,
                () -> plugin.store(" ", "a.txt", new ByteArrayInputStream(content))).getCode());
        assertEquals(ErrorCode.PARAM_ERROR.getCode(), assertThrows(BizException.class,
                () -> plugin.store("..", "a.txt", new ByteArrayInputStream(content))).getCode());
        assertEquals(ErrorCode.PARAM_ERROR.getCode(), assertThrows(BizException.class,
                () -> plugin.load("bluewhale", " ")).getCode());
    }

    @Test
    @DisplayName("合法文件名：含连续点但不含 .. 路径段时正常落盘")
    void shouldAllowDotsInsideFileName() {
        byte[] content = "x".getBytes(StandardCharsets.UTF_8);

        plugin.store("bluewhale", "report..2024.pdf", new ByteArrayInputStream(content));

        assertTrue(Files.exists(tempDir.resolve("bluewhale").resolve("report..2024.pdf")));
    }

    @Test
    @DisplayName("presignedDownloadUrl：本地插件不支持，抛参数错误")
    void shouldRejectPresign() {
        BizException ex = assertThrows(BizException.class,
                () -> plugin.presignedDownloadUrl("bluewhale", "a.txt", 600));

        assertEquals(ErrorCode.PARAM_ERROR.getCode(), ex.getCode());
        assertTrue(ex.getMessage().contains("不支持预签名"));
    }
}
