package com.bluewhale.file.storage.plugin.minio;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import io.minio.MinioClient;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.util.StringUtils;

/**
 * MinIO / S3 对象存储插件自动配置。
 *
 * <p>仅当配置了 {@code whale.file.minio.endpoint} 时装配；未部署对象存储的环境不加载本插件，
 * 由本地存储插件兜底。是否选为活动插件仍由 {@code whale.file.storage.type} 决定。</p>
 *
 * @author BlueWhale
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "whale.file.minio", name = "endpoint")
@EnableConfigurationProperties(MinioStorageProperties.class)
public class MinioStorageAutoConfiguration {

    /**
     * 构建 MinIO 客户端单例。
     *
     * <p>凭据为空时启动即失败：SDK 的空凭据不会在构建期报错，会延迟到首次请求才以 403 暴露，
     * 提前校验可避免“启动成功、首次上传才失败”的排障成本。</p>
     *
     * @param properties 对象存储配置
     * @return 由端点与凭据构建的 {@link MinioClient} 实例
     * @throws BizException 访问密钥或密钥口令为空时抛出 {@code SYSTEM_ERROR}
     */
    @Bean
    @ConditionalOnMissingBean(MinioClient.class)
    public MinioClient minioClient(MinioStorageProperties properties) {
        if (!StringUtils.hasText(properties.getAccessKey()) || !StringUtils.hasText(properties.getSecretKey())) {
            throw new BizException(ErrorCode.SYSTEM_ERROR.getCode(), "file.storage.minio-credentials-missing",
                    "MinIO 访问凭据未配置: whale.file.minio.access-key / secret-key");
        }
        MinioClient.Builder builder = MinioClient.builder()
                .endpoint(properties.getEndpoint())
                .credentials(properties.getAccessKey(), properties.getSecretKey());
        if (StringUtils.hasText(properties.getRegion())) {
            builder.region(properties.getRegion());
        }
        return builder.build();
    }

    /**
     * 注册 MinIO 存储插件 Bean。
     *
     * @param minioClient MinIO 客户端
     * @param properties 对象存储配置
     * @return 对象存储插件
     */
    @Bean
    @ConditionalOnMissingBean(MinioStoragePlugin.class)
    public MinioStoragePlugin minioStoragePlugin(MinioClient minioClient, MinioStorageProperties properties) {
        return new MinioStoragePlugin(minioClient, properties);
    }
}
