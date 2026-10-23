package com.bluewhale.file.storage.plugin.minio;

import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.GetObjectResponse;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.http.Method;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link MinioStoragePlugin} 以 Mockito 桩 {@link MinioClient} 校验桶初始化、上传、读取、删除与预签名。
 *
 * @author BlueWhale
 */
class MinioStoragePluginTest {

    private MinioClient minioClient;

    private MinioStorageProperties properties;

    private MinioStoragePlugin plugin;

    /**
     * 以桩化的 MinioClient 与配置构造被测插件。
     */
    @BeforeEach
    void setUp() {
        minioClient = mock(MinioClient.class);
        properties = new MinioStorageProperties();
        properties.setBucket("bluewhale");
        plugin = new MinioStoragePlugin(minioClient, properties);
    }

    @Test
    @DisplayName("插件元信息：type=minio、支持预签名")
    void shouldExposeMetadata() {
        assertEquals(MinioStoragePlugin.TYPE, plugin.getType());
        assertTrue(plugin.supportsPresigned());
    }

    @Test
    @DisplayName("store：桶已存在时直接 putObject 上传对象")
    void shouldStoreViaPutObject() throws Exception {
        when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(true);
        byte[] content = "hello whale".getBytes(StandardCharsets.UTF_8);

        plugin.store("mybucket", "2026/10/19/a.txt", new ByteArrayInputStream(content));

        verify(minioClient).putObject(any(PutObjectArgs.class));
        verify(minioClient, never()).makeBucket(any(MakeBucketArgs.class));
    }

    @Test
    @DisplayName("store：桶缺失时自动创建（解锁归档独立桶）")
    void shouldCreateBucketWhenMissing() throws Exception {
        when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(false);
        byte[] content = "archive".getBytes(StandardCharsets.UTF_8);

        plugin.store("bluewhale-audit-archive", "2026/10/19/a.csv", new ByteArrayInputStream(content));

        verify(minioClient).makeBucket(any(MakeBucketArgs.class));
        verify(minioClient).putObject(any(PutObjectArgs.class));
    }

    @Test
    @DisplayName("store：关闭自动建桶时只上传不探测")
    void shouldSkipBucketCreationWhenDisabled() throws Exception {
        properties.setAutoCreateBucket(false);
        byte[] content = "archive".getBytes(StandardCharsets.UTF_8);

        plugin.store("bluewhale", "a.txt", new ByteArrayInputStream(content));

        verify(minioClient, never()).bucketExists(any(BucketExistsArgs.class));
        verify(minioClient).putObject(any(PutObjectArgs.class));
    }

    @Test
    @DisplayName("delete：调用 removeObject 删除对象")
    void shouldDeleteViaRemoveObject() throws Exception {
        plugin.delete("mybucket", "a.txt");

        verify(minioClient).removeObject(any(RemoveObjectArgs.class));
    }

    @Test
    @DisplayName("load：返回 getObject 的对象流")
    void shouldLoadViaGetObject() throws Exception {
        GetObjectResponse response = mock(GetObjectResponse.class);
        when(minioClient.getObject(any(GetObjectArgs.class))).thenReturn(response);

        InputStream in = plugin.load("mybucket", "2026/10/19/a.txt");

        assertSame(response, in);
        verify(minioClient).getObject(any(GetObjectArgs.class));
    }

    @Test
    @DisplayName("presignedDownloadUrl：以 GET + 指定过期秒数生成预签名 URL，桶为空回退默认桶")
    void shouldPresignDownloadUrl() throws Exception {
        String expected = "http://127.0.0.1:9000/bluewhale/2026/10/19/a.txt?X-Amz-Signature=x";
        when(minioClient.getPresignedObjectUrl(any(GetPresignedObjectUrlArgs.class))).thenReturn(expected);

        String url = plugin.presignedDownloadUrl(null, "2026/10/19/a.txt", 600);

        assertEquals(expected, url);
        ArgumentCaptor<GetPresignedObjectUrlArgs> captor =
                ArgumentCaptor.forClass(GetPresignedObjectUrlArgs.class);
        verify(minioClient).getPresignedObjectUrl(captor.capture());
        assertEquals(Method.GET, captor.getValue().method());
        assertEquals(600, captor.getValue().expiry());
        assertEquals("bluewhale", captor.getValue().bucket());
        assertEquals("2026/10/19/a.txt", captor.getValue().object());
    }
}
