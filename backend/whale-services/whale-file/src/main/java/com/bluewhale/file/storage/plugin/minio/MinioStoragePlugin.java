package com.bluewhale.file.storage.plugin.minio;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.file.storage.api.StoragePlugin;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.errors.ErrorResponseException;
import io.minio.http.Method;
import org.springframework.util.StringUtils;

import java.io.InputStream;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * MinIO / S3 兼容对象存储插件。
 *
 * <p>以传入的 {@code bucket} 作为桶名（为空时回退 {@link MinioStorageProperties#getBucket()}），
 * {@code objectKey} 作为对象键；写入前按配置自动创建缺失桶，使 job/audit 归档等
 * 独立桶无需人工预建。所有 SDK 受检异常统一包装为 {@link BizException}，
 * 读取缺失对象抛出 {@link ErrorCode#NOT_FOUND}，其余抛出 {@link ErrorCode#SYSTEM_ERROR}。</p>
 *
 * @author BlueWhale
 */
public class MinioStoragePlugin implements StoragePlugin {

    /** 插件类型标识 */
    public static final String TYPE = "minio";

    /** MinIO 分片大小：10MB；流长度未知时用于分片上传 */
    private static final long PART_SIZE = 10485760L;

    private final MinioClient minioClient;

    private final MinioStorageProperties properties;

    /** 已确认存在的桶，避免每次写入都探测 */
    private final Set<String> knownBuckets = ConcurrentHashMap.newKeySet();

    /**
     * 以客户端与配置构造对象存储插件。
     *
     * @param minioClient MinIO 客户端
     * @param properties 对象存储配置
     */
    public MinioStoragePlugin(MinioClient minioClient, MinioStorageProperties properties) {
        this.minioClient = minioClient;
        this.properties = properties;
    }

    @Override
    public String getType() {
        return TYPE;
    }

    @Override
    public String getDisplayName() {
        return "MinIO / S3 兼容对象存储";
    }

    @Override
    public boolean supportsPresigned() {
        return true;
    }

    @Override
    public void store(String bucket, String objectKey, InputStream in) {
        String resolvedBucket = resolveBucket(bucket);
        ensureBucket(resolvedBucket);
        try {
            PutObjectArgs args = PutObjectArgs.builder()
                    .bucket(resolvedBucket)
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
        return properties.getBucket();
    }

    /**
     * 确保目标桶存在：已确认过的桶直接跳过，缺失时按配置自动创建。
     *
     * <p>多实例并发创建同名桶时，后到者可能收到「已存在」错误，此时以桶探测结果为准，
     * 仍不存在才视为初始化失败。</p>
     *
     * @param bucket 目标桶名称
     * @throws BizException 桶初始化失败时抛出 {@code SYSTEM_ERROR}
     */
    private void ensureBucket(String bucket) {
        if (!properties.isAutoCreateBucket() || knownBuckets.contains(bucket)) {
            return;
        }
        synchronized (knownBuckets) {
            if (knownBuckets.contains(bucket)) {
                return;
            }
            try {
                if (!minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) {
                    minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
                }
                knownBuckets.add(bucket);
            } catch (Exception e) {
                if (existsQuietly(bucket)) {
                    knownBuckets.add(bucket);
                    return;
                }
                throw new BizException(ErrorCode.SYSTEM_ERROR.getCode(), "对象存储桶初始化失败: " + bucket);
            }
        }
    }

    /**
     * 探测桶是否存在，探测异常时按不存在处理。
     *
     * @param bucket 目标桶名称
     * @return 桶存在时返回 {@code true}
     */
    private boolean existsQuietly(String bucket) {
        try {
            return minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
        } catch (Exception e) {
            return false;
        }
    }
}
