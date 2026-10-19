package com.bluewhale.file.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * MinIO 对象存储配置项（前缀 {@code whale.file.minio}）。
 *
 * <p>仅在 {@code whale.file.storage.type=minio} 时生效，用于构建 {@code MinioClient}；
 * 业务侧调用 {@link com.bluewhale.file.storage.FileStorage} 时传入桶名，桶名为空则回退到 {@link #bucket}。</p>
 *
 * @author BlueWhale
 */
@Data
@ConfigurationProperties(prefix = "whale.file.minio")
public class MinioProperties {

    /** MinIO 服务端点，形如 {@code http://127.0.0.1:9000} */
    private String endpoint;

    /** 访问密钥 */
    private String accessKey;

    /** 密钥口令 */
    private String secretKey;

    /** 默认桶名，业务传入桶名为空时回退使用；默认 {@code bluewhale} */
    private String bucket = "bluewhale";

    /** 区域（可选），未配置时由 SDK 自动探测 */
    private String region;
}
