package com.bluewhale.file.storage.plugin.minio;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.file.domain.storage.StorageObject;
import io.minio.BucketExistsArgs;
import io.minio.CopyObjectArgs;
import io.minio.GetObjectArgs;
import io.minio.GetObjectResponse;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.ListObjectsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.Result;
import io.minio.errors.ErrorResponseException;
import io.minio.http.Method;
import io.minio.messages.Bucket;
import io.minio.messages.ErrorResponse;
import io.minio.messages.Item;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
    @DisplayName("move：复制到目标桶后删除源对象")
    void shouldCopyThenRemoveSource() throws Exception {
        when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(true);

        plugin.move("bluewhale", "tenants/1/a.txt", "archive");

        ArgumentCaptor<CopyObjectArgs> copyCaptor = ArgumentCaptor.forClass(CopyObjectArgs.class);
        verify(minioClient).copyObject(copyCaptor.capture());
        assertEquals("archive", copyCaptor.getValue().bucket());
        ArgumentCaptor<RemoveObjectArgs> removeCaptor = ArgumentCaptor.forClass(RemoveObjectArgs.class);
        verify(minioClient).removeObject(removeCaptor.capture());
        assertEquals("bluewhale", removeCaptor.getValue().bucket());
    }

    @Test
    @DisplayName("listObjects：遍历全部桶的递归对象列表")
    void shouldListObjectsAcrossBuckets() throws Exception {
        Bucket bucket = mock(Bucket.class);
        when(bucket.name()).thenReturn("bluewhale");
        Item item = mock(Item.class);
        when(item.objectName()).thenReturn("tenants/1/a.txt");
        when(item.size()).thenReturn(5L);
        @SuppressWarnings("unchecked")
        Result<Item> result = mock(Result.class);
        when(result.get()).thenReturn(item);
        when(minioClient.listBuckets()).thenReturn(List.of(bucket));
        when(minioClient.listObjects(any(ListObjectsArgs.class))).thenReturn(List.of(result));

        List<StorageObject> objects = plugin.listObjects();

        assertEquals(1, objects.size());
        assertEquals("bluewhale", objects.get(0).bucket());
        assertEquals("tenants/1/a.txt", objects.get(0).objectKey());
        assertEquals(5L, objects.get(0).size());
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
    @DisplayName("load：仅对象/桶不存在映射 NOT_FOUND，权限拒绝映射 SYSTEM_ERROR")
    void shouldDistinguishLoadErrors() throws Exception {
        ErrorResponseException missing = errorResponse("NoSuchKey");
        when(minioClient.getObject(any(GetObjectArgs.class))).thenThrow(missing);
        assertEquals(ErrorCode.NOT_FOUND.getCode(), assertThrows(BizException.class,
                () -> plugin.load("mybucket", "missing.txt")).getCode());

        ErrorResponseException denied = errorResponse("AccessDenied");
        when(minioClient.getObject(any(GetObjectArgs.class))).thenThrow(denied);
        assertEquals(ErrorCode.SYSTEM_ERROR.getCode(), assertThrows(BizException.class,
                () -> plugin.load("mybucket", "a.txt")).getCode());
    }

    @Test
    @DisplayName("presignedDownloadUrl：超过 7 天的有效期截断到上限")
    void shouldClampPresignedExpiry() throws Exception {
        when(minioClient.getPresignedObjectUrl(any(GetPresignedObjectUrlArgs.class))).thenReturn("url");

        plugin.presignedDownloadUrl("mybucket", "a.txt", 9999999);

        ArgumentCaptor<GetPresignedObjectUrlArgs> captor =
                ArgumentCaptor.forClass(GetPresignedObjectUrlArgs.class);
        verify(minioClient).getPresignedObjectUrl(captor.capture());
        assertEquals(604800, captor.getValue().expiry());
    }

    /**
     * 构造带指定错误码的 SDK 错误响应异常。
     *
     * @param code SDK 错误码
     * @return 桩化的错误响应异常
     */
    private static ErrorResponseException errorResponse(String code) {
        ErrorResponseException exception = mock(ErrorResponseException.class);
        ErrorResponse response = mock(ErrorResponse.class);
        when(exception.errorResponse()).thenReturn(response);
        when(response.code()).thenReturn(code);
        return exception;
    }
}
