package com.bluewhale.file.domain.storage;

import java.io.InputStream;

/**
 * 文件存储插件 SPI。
 *
 * <p>每个实现以独立 jar 形式随服务类路径提供，并通过 Spring Boot 自动配置注册为 Bean；
 * 服务侧按 {@code whale.file.storage.type} 选择启用插件，新增存储介质只需新增插件模块，
 * 无需修改业务代码。插件以 {@code bucket + objectKey} 二元组定位对象。</p>
 *
 * @author BlueWhale
 */
public interface StoragePlugin {

    /**
     * 插件类型标识，与 {@code whale.file.storage.type} 配置值一致（如 {@code local}、{@code minio}）。
     *
     * @return 插件类型
     */
    String getType();

    /**
     * 插件展示名称，用于可观测性与运维排查。
     *
     * @return 展示名称
     */
    String getDisplayName();

    /**
     * 是否支持预签名下载 URL。
     *
     * @return 支持时返回 {@code true}
     */
    boolean supportsPresigned();

    /**
     * 存入文件内容。
     *
     * @param bucket 桶名称
     * @param objectKey 桶内对象键
     * @param in 待写入的内容输入流，由调用方负责关闭
     */
    void store(String bucket, String objectKey, InputStream in);

    /**
     * 读取文件内容。
     *
     * @param bucket 桶名称
     * @param objectKey 桶内对象键
     * @return 内容输入流，由调用方负责关闭
     */
    InputStream load(String bucket, String objectKey);

    /**
     * 删除文件内容。
     *
     * @param bucket 桶名称
     * @param objectKey 桶内对象键
     */
    void delete(String bucket, String objectKey);

    /**
     * 生成预签名下载 URL。
     *
     * @param bucket 桶名称
     * @param objectKey 桶内对象键
     * @param expiresSeconds 有效期（秒）
     * @return 预签名下载 URL
     */
    String presignedDownloadUrl(String bucket, String objectKey, int expiresSeconds);
}
