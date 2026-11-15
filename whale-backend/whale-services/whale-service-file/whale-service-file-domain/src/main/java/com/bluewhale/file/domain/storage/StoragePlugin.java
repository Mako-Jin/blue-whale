package com.bluewhale.file.domain.storage;

import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.domain.exception.DomainException;

import java.io.EOFException;
import java.io.IOException;
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
     * 读取文件内容的指定字节区间（语义对齐 HTTP Range）。
     *
     * <p>默认实现先跳过 {@code offset} 字节，再以 {@code length} 截断；对象存储插件可覆盖为
     * 原生分段读取，避免从头跳过带来的额外 IO。</p>
     *
     * @param bucket 桶名称
     * @param objectKey 桶内对象键
     * @param offset 起始偏移（字节，从 0 开始）
     * @param length 读取长度（字节），必须为正数
     * @return 仅包含请求区间的内容输入流，由调用方负责关闭
     * @throws DomainException 区间越过对象末尾时抛出 {@code PARAM_ERROR}；其他读取失败抛出 {@code SYSTEM_ERROR}
     */
    default InputStream load(String bucket, String objectKey, long offset, long length) {
        InputStream in = load(bucket, objectKey);
        try {
            in.skipNBytes(offset);
            return new LimitedInputStream(in, length);
        } catch (EOFException e) {
            closeQuietly(in);
            throw new DomainException(ErrorCode.PARAM_ERROR, "读取区间超出对象末尾");
        } catch (IOException e) {
            closeQuietly(in);
            throw new DomainException(ErrorCode.SYSTEM_ERROR);
        }
    }

    /**
     * 静默关闭输入流，用于区间读取失败时的资源回收。
     *
     * @param in 待关闭的输入流
     */
    private static void closeQuietly(InputStream in) {
        try {
            in.close();
        } catch (IOException ignored) {
            // 关闭失败不影响抛出的原始异常
        }
    }

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

    /**
     * 探测介质可用性，供 {@code /actuator/health} 汇总。
     *
     * <p>默认实现返回可用，兼容未实现探测能力的第三方插件；内置 local / minio 插件均覆盖本方法，
     * 分别校验本地根目录可写与对象存储连接/默认桶存在。</p>
     *
     * @return 健康状态
     */
    default StorageHealth health() {
        return StorageHealth.up("插件未提供健康检查");
    }
}
