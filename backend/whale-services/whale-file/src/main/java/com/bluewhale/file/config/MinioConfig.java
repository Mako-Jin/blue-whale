package com.bluewhale.file.config;

import io.minio.MinioClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

/**
 * MinIO 客户端装配：仅当 {@code whale.file.storage.type=minio} 时生效。
 *
 * <p>由 {@link MinioProperties} 构建单例 {@link MinioClient}，注入 {@code MinioFileStorage} 使用；
 * 区域（region）已配置时透传给 SDK，否则交由 SDK 自动探测。</p>
 *
 * @author BlueWhale
 */
@Configuration
@ConditionalOnProperty(name = "whale.file.storage.type", havingValue = "minio")
public class MinioConfig {

    /**
     * 构建 MinIO 客户端单例。
     *
     * @param minioProperties MinIO 配置项
     * @return 由端点与凭据构建的 {@link MinioClient} 实例
     */
    @Bean
    public MinioClient minioClient(MinioProperties minioProperties) {
        MinioClient.Builder builder = MinioClient.builder()
                .endpoint(minioProperties.getEndpoint())
                .credentials(minioProperties.getAccessKey(), minioProperties.getSecretKey());
        if (StringUtils.hasText(minioProperties.getRegion())) {
            builder.region(minioProperties.getRegion());
        }
        return builder.build();
    }
}
