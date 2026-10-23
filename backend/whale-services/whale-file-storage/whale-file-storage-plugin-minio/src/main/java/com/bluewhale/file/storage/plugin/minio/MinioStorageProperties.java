package com.bluewhale.file.storage.plugin.minio;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * MinIO / S3 兼容对象存储插件配置项（前缀 {@code whale.file.minio}）。
 *
 * <p>仅当配置了 {@code endpoint} 时插件才会装配；{@code whale.file.storage.type=minio}
 * 时由服务侧注册表选为活动插件。桶名为空时回退到 {@link #bucket}。</p>
 *
 * @author BlueWhale
 */
@Data
@ConfigurationProperties(prefix = "whale.file.minio")
public class MinioStorageProperties {

    /** 服务端点，形如 {@code http://127.0.0.1:9000} */
    private String endpoint;

    /** 访问密钥 */
    private String accessKey;

    /** 密钥口令 */
    private String secretKey;

    /** 默认桶名，业务传入桶名为空时回退使用；默认 {@code bluewhale} */
    private String bucket = "bluewhale";

    /** 区域（可选），未配置时由 SDK 自动探测 */
    private String region;

    /** 写入前自动创建缺失桶；默认 {@code true}，用于解锁 job/audit 归档等独立桶场景 */
    private boolean autoCreateBucket = true;
}
