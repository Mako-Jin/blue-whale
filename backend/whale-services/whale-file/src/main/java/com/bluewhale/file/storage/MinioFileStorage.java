package com.bluewhale.file.storage;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.file.config.MinioProperties;
import io.minio.GetObjectArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.errors.ErrorResponseException;
import io.minio.http.Method;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.InputStream;
import java.util.concurrent.TimeUnit;

/**
 * MinIO 对象存储实现：仅当 {@code whale.file.storage.type=minio} 时装配。
 *
 * <p>以传入的 {@code bucket} 作为 MinIO 桶名（为空时回退 {@link MinioProperties#getBucket()}），
 * {@code objectKey} 作为对象键；所有 SDK 受检异常统一包装为 {@link BizException}，
 * 其中读取缺失对象时抛出 {@link ErrorCode#NOT_FOUND}，其余抛出 {@link ErrorCode#SYSTEM_ERROR}。</p>
 *
 * @author BlueWhale
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "whale.file.storage.type", havingValue = "minio")
public class MinioFileStorage implements FileStorage {

    /** MinIO 分片大小：10MB；流长度未知时用于分片上传 */
    private static final long PART_SIZE = 10485760L;

    private final MinioClient minioClient;

    private final MinioProperties minioProperties;

    @Override
    public void store(String bucket, String objectKey, InputStream in) {
        try {
            PutObjectArgs args = PutObjectArgs.builder()
                    .bucket(resolveBucket(bucket))
                    .object(objectKey)
                    .stream(in, -1, PART_SIZE)
                    .build();
            minioClient.putObject(args);
        } catch (Exception e) {
            throw new BizException(ErrorCode.SYSTEM_ERROR);
        }
    }

    @Override
    public InputStream load(String bucket, String objectKey) {
        try {
            GetObjectArgs args = GetObjectArgs.builder()
                    .bucket(resolveBucket(bucket))
                    .object(objectKey)
                    .build();
            return minioClient.getObject(args);
        } catch (ErrorResponseException e) {
            throw new BizException(ErrorCode.NOT_FOUND);
        } catch (Exception e) {
            throw new BizException(ErrorCode.SYSTEM_ERROR);
        }
    }

    @Override
    public void delete(String bucket, String objectKey) {
        try {
            RemoveObjectArgs args = RemoveObjectArgs.builder()
                    .bucket(resolveBucket(bucket))
                    .object(objectKey)
                    .build();
            minioClient.removeObject(args);
        } catch (Exception e) {
            throw new BizException(ErrorCode.SYSTEM_ERROR);
        }
    }

    @Override
    public String presignedDownloadUrl(String bucket, String objectKey, int expiresSeconds) {
        try {
            GetPresignedObjectUrlArgs args = GetPresignedObjectUrlArgs.builder()
                    .method(Method.GET)
                    .bucket(resolveBucket(bucket))
                    .object(objectKey)
                    .expiry(expiresSeconds, TimeUnit.SECONDS)
                    .build();
            return minioClient.getPresignedObjectUrl(args);
        } catch (Exception e) {
            throw new BizException(ErrorCode.SYSTEM_ERROR);
        }
    }

    /**
     * 解析实际使用的桶名：传入桶名为空时回退到配置的默认桶。
     *
     * @param bucket 调用方传入的桶名称
     * @return 非空的桶名称
     */
    private String resolveBucket(String bucket) {
        if (StringUtils.hasText(bucket)) {
            return bucket;
        }
        return minioProperties.getBucket();
    }
}
