package com.bluewhale.file.storage.plugin.minio;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.file.domain.storage.StorageHealth;
import com.bluewhale.file.domain.storage.StorageObject;
import com.bluewhale.file.domain.storage.StoragePlugin;
import io.minio.BucketExistsArgs;
import io.minio.CopyObjectArgs;
import io.minio.CopySource;
import io.minio.GetObjectArgs;
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
import io.minio.messages.Item;
import org.springframework.util.StringUtils;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * MinIO / S3 兼容对象存储插件。
 *
 * <p>以传入的 {@code bucket} 作为桶名（为空时回退 {@link MinioStorageProperties#getBucket()}），
 * {@code objectKey} 作为对象键；写入前按配置自动创建缺失桶，使 job/audit 归档等
 * 独立桶无需人工预建。所有 SDK 受检异常统一包装为 {@link BizException}：读取时仅对象/桶不存在
 * 映射为 {@link ErrorCode#NOT_FOUND}（权限、服务端错误等仍为 {@link ErrorCode#SYSTEM_ERROR}）。
 * {@code load} 返回的流由调用方关闭，否则会耗尽 SDK 连接。</p>
 *
 * @author BlueWhale
 */
public class MinioStoragePlugin implements StoragePlugin {

    /** 插件类型标识 */
    public static final String TYPE = "minio";

    /** MinIO 分片大小：10MB；流长度未知时用于分片上传 */
    private static final long PART_SIZE = 10485760L;

    /** 预签名 URL 有效期上限（秒）：MinIO/S3 最长 7 天，超出会被 SDK 拒绝 */
    private static final int MAX_PRESIGNED_EXPIRY_SECONDS = 604800;

    /** 对象不存在的 SDK 错误码 */
    private static final String NO_SUCH_KEY = "NoSuchKey";

    /** 桶不存在的 SDK 错误码 */
    private static final String NO_SUCH_BUCKET = "NoSuchBucket";

    private final MinioClient minioClient;

    private final MinioStorageProperties properties;

    /** 已确认存在的桶，避免每次写入都探测 */
    private final Set<String> knownBuckets = ConcurrentHashMap.newKeySet();

    /** 建桶临界区专用锁对象：不与集合实例共用锁语义，避免缓存实现变更时失效 */
    private final Object bucketLock = new Object();

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
        } catch (ErrorResponseException e) {
            // 桶可能已被外部删除：清掉缓存，后续写入重新探测/创建；当前失败无重试，
            // 避免在流可能已被消费的情况下重放导致写入不完整内容
            if (NO_SUCH_BUCKET.equals(errorCode(e))) {
                knownBuckets.remove(resolvedBucket);
            }
            throw new BizException(ErrorCode.SYSTEM_ERROR);
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
            throw notFoundOrSystemError(e);
        } catch (Exception e) {
            throw new BizException(ErrorCode.SYSTEM_ERROR);
        }
    }

    @Override
    public InputStream load(String bucket, String objectKey, long offset, long length) {
        try {
            GetObjectArgs args = GetObjectArgs.builder()
                    .bucket(resolveBucket(bucket))
                    .object(objectKey)
                    .offset(offset)
                    .length(length)
                    .build();
            return minioClient.getObject(args);
        } catch (ErrorResponseException e) {
            throw notFoundOrSystemError(e);
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
    public void move(String bucket, String objectKey, String targetBucket) {
        String sourceBucket = resolveBucket(bucket);
        String destBucket = resolveBucket(targetBucket);
        ensureBucket(destBucket);
        try {
            minioClient.copyObject(CopyObjectArgs.builder()
                    .bucket(destBucket)
                    .object(objectKey)
                    .source(CopySource.builder().bucket(sourceBucket).object(objectKey).build())
                    .build());
        } catch (ErrorResponseException e) {
            throw notFoundOrSystemError(e);
        } catch (Exception e) {
            throw new BizException(ErrorCode.SYSTEM_ERROR);
        }
        try {
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(sourceBucket)
                    .object(objectKey)
                    .build());
        } catch (Exception e) {
            // 复制已成功但源对象删除失败：两份对象共存，元数据仍指向源桶，
            // 读取不受影响；由运维按日志清理目标桶残留
            throw new BizException(ErrorCode.SYSTEM_ERROR.getCode(), "源对象删除失败: " + objectKey);
        }
    }

    @Override
    public String presignedDownloadUrl(String bucket, String objectKey, int expiresSeconds) {
        try {
            GetPresignedObjectUrlArgs args = GetPresignedObjectUrlArgs.builder()
                    .method(Method.GET)
                    .bucket(resolveBucket(bucket))
                    .object(objectKey)
                    // 插件层兜底截断到 S3 上限，避免绕过应用层直接调用时被 SDK 以参数异常拒绝
                    .expiry(Math.min(expiresSeconds, MAX_PRESIGNED_EXPIRY_SECONDS), TimeUnit.SECONDS)
                    .build();
            return minioClient.getPresignedObjectUrl(args);
        } catch (Exception e) {
            throw new BizException(ErrorCode.SYSTEM_ERROR);
        }
    }

    @Override
    public List<StorageObject> listObjects() {
        List<StorageObject> objects = new ArrayList<>();
        try {
            for (Bucket bucket : minioClient.listBuckets()) {
                Iterable<Result<Item>> results = minioClient.listObjects(ListObjectsArgs.builder()
                        .bucket(bucket.name())
                        .recursive(true)
                        .build());
                for (Result<Item> result : results) {
                    Item item = result.get();
                    objects.add(new StorageObject(bucket.name(), item.objectName(), item.size()));
                }
            }
        } catch (Exception e) {
            throw new BizException(ErrorCode.SYSTEM_ERROR.getCode(), "对象存储列举失败: " + e.getMessage());
        }
        return objects;
    }

    /**
     * 探测对象存储可用性：配置了默认桶时探测桶是否存在，否则以 {@code listBuckets} 验证连通性。
     *
     * @return 健康状态
     */
    @Override
    public StorageHealth health() {
        try {
            String bucket = properties.getBucket();
            if (StringUtils.hasText(bucket)) {
                boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
                return exists
                        ? StorageHealth.up("对象存储默认桶可用: " + bucket)
                        : StorageHealth.down("对象存储默认桶不存在: " + bucket);
            }
            minioClient.listBuckets();
            return StorageHealth.up("对象存储连接正常");
        } catch (Exception e) {
            return StorageHealth.down("对象存储不可达: " + e.getMessage());
        }
    }

    /**
     * 解析实际使用的桶名：传入桶名为空时回退到配置的默认桶。
     *
     * @param bucket 调用方传入的桶名称
     * @return 非空的桶名称
     * @throws BizException 传入桶名与默认桶均为空时抛出 {@code PARAM_ERROR}
     */
    private String resolveBucket(String bucket) {
        String resolved = StringUtils.hasText(bucket) ? bucket : properties.getBucket();
        if (!StringUtils.hasText(resolved)) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "对象存储桶名未配置");
        }
        return resolved;
    }

    /**
     * 将 SDK 错误响应映射为业务异常：仅对象/桶不存在视为 {@code NOT_FOUND}，
     * 权限拒绝、服务端错误等仍为 {@code SYSTEM_ERROR}，避免 403 被误报成“文件不存在”。
     *
     * @param e SDK 错误响应异常
     * @return 待抛出的业务异常
     */
    private static BizException notFoundOrSystemError(ErrorResponseException e) {
        String code = errorCode(e);
        return NO_SUCH_KEY.equals(code) || NO_SUCH_BUCKET.equals(code)
                ? new BizException(ErrorCode.NOT_FOUND)
                : new BizException(ErrorCode.SYSTEM_ERROR);
    }

    /**
     * 提取 SDK 错误码，响应体缺失时返回 {@code null}。
     *
     * @param e SDK 错误响应异常
     * @return 错误码（如 {@code NoSuchKey}），无法解析时返回 {@code null}
     */
    private static String errorCode(ErrorResponseException e) {
        return e.errorResponse() == null ? null : e.errorResponse().code();
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
        // 关闭自动建桶时不缓存：桶由外部管理、可能随时被删除，缓存会掩盖真实的 NoSuchBucket
        if (!properties.isAutoCreateBucket() || knownBuckets.contains(bucket)) {
            return;
        }
        synchronized (bucketLock) {
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
