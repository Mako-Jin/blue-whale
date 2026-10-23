package com.bluewhale.file.storage;

import com.bluewhale.file.config.MinioProperties;
import io.minio.GetObjectArgs;
import io.minio.GetObjectResponse;
import io.minio.GetPresignedObjectUrlArgs;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link MinioFileStorage} 以 Mockito 桩 {@link MinioClient} 校验上传、读取、删除的 SDK 调用与流回传。
 *
 * @author BlueWhale
 */
class MinioFileStorageTest {

    private MinioClient minioClient;

    private MinioFileStorage minioFileStorage;

    /**
     * 以桩化的 MinioClient 与配置构造被测对象。
     */
    @BeforeEach
    void setUp() {
        minioClient = mock(MinioClient.class);
        MinioProperties minioProperties = new MinioProperties();
        minioProperties.setBucket("bluewhale");
        minioFileStorage = new MinioFileStorage(minioClient, minioProperties);
    }

    @Test
    @DisplayName("store：调用 putObject 上传对象")
    void shouldStoreViaPutObject() throws Exception {
        byte[] content = "hello whale".getBytes(StandardCharsets.UTF_8);

        minioFileStorage.store("mybucket", "2026/10/19/a.txt", new ByteArrayInputStream(content));

        verify(minioClient).putObject(any(PutObjectArgs.class));
    }

    @Test
    @DisplayName("delete：调用 removeObject 删除对象")
    void shouldDeleteViaRemoveObject() throws Exception {
        minioFileStorage.delete("mybucket", "a.txt");

        verify(minioClient).removeObject(any(RemoveObjectArgs.class));
    }

    @Test
    @DisplayName("load：返回 getObject 的对象流")
    void shouldLoadViaGetObject() throws Exception {
        GetObjectResponse response = mock(GetObjectResponse.class);
        when(minioClient.getObject(any(GetObjectArgs.class))).thenReturn(response);

        InputStream in = minioFileStorage.load("mybucket", "2026/10/19/a.txt");

        assertSame(response, in);
        verify(minioClient).getObject(any(GetObjectArgs.class));
    }

    @Test
    @DisplayName("presignedDownloadUrl：以 GET + 指定过期秒数生成预签名 URL，桶为空回退默认桶")
    void shouldPresignDownloadUrl() throws Exception {
        String expected = "http://127.0.0.1:9000/bluewhale/2026/10/19/a.txt?X-Amz-Signature=x";
        when(minioClient.getPresignedObjectUrl(any(GetPresignedObjectUrlArgs.class))).thenReturn(expected);

        String url = minioFileStorage.presignedDownloadUrl(null, "2026/10/19/a.txt", 600);

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
