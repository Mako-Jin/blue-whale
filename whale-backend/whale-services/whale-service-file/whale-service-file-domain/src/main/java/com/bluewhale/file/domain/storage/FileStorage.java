package com.bluewhale.file.domain.storage;

import com.bluewhale.common.domain.exception.DomainException;

import java.io.InputStream;

/**
 * 文件存储端口：屏蔽底层存储介质差异，由插件注册表委派给当前活动插件。
 *
 * <p>定位由 {@code bucket + objectKey} 二元组完成；本地磁盘、MinIO/S3 等介质以独立插件
 * 提供，业务侧无需感知具体实现。</p>
 *
 * @author BlueWhale
 */
public interface FileStorage {

    /**
     * 存入文件内容。
     *
     * @param bucket 桶名称
     * @param objectKey 桶内对象键（形如 {@code yyyy/MM/dd/<uuid>.<ext>}）
     * @param in 待写入的内容输入流，由调用方负责关闭
     */
    void store(String bucket, String objectKey, InputStream in);

    /**
     * 读取文件内容（使用当前活动插件）。
     *
     * @param bucket 桶名称
     * @param objectKey 桶内对象键
     * @return 内容输入流，由调用方负责关闭
     */
    default InputStream load(String bucket, String objectKey) {
        return load(bucket, objectKey, null);
    }

    /**
     * 按落盘时的存储插件类型读取文件内容。
     *
     * <p>文件元数据记录 {@code storageType} 后，切换 {@code whale.file.storage.type} 不影响存量文件读取；
     * 类型为空时回落当前活动插件，兼容回填前的历史数据。</p>
     *
     * @param bucket 桶名称
     * @param objectKey 桶内对象键
     * @param storageType 落盘时的插件类型，可为 {@code null}
     * @return 内容输入流，由调用方负责关闭
     * @throws DomainException 类型对应的插件未装配时抛出 {@code SYSTEM_ERROR}
     */
    InputStream load(String bucket, String objectKey, String storageType);

    /**
     * 读取文件内容的指定字节区间（语义对齐 HTTP Range）。
     *
     * <p>由当前活动插件实现：对象存储可原生分段读取，本地介质按偏移跳过并截断长度，
     * 保证响应体不超过声明长度。</p>
     *
     * @param bucket 桶名称
     * @param objectKey 桶内对象键
     * @param offset 起始偏移（字节，从 0 开始）
     * @param length 读取长度（字节），必须为正数
     * @return 仅包含请求区间的内容输入流，由调用方负责关闭
     * @throws DomainException 读取失败或区间越过对象末尾时抛出 {@code SYSTEM_ERROR}
     */
    default InputStream load(String bucket, String objectKey, long offset, long length) {
        return load(bucket, objectKey, offset, length, null);
    }

    /**
     * 按落盘时的存储插件类型读取指定字节区间。
     *
     * @param bucket 桶名称
     * @param objectKey 桶内对象键
     * @param offset 起始偏移（字节，从 0 开始）
     * @param length 读取长度（字节），必须为正数
     * @param storageType 落盘时的插件类型，可为 {@code null}
     * @return 仅包含请求区间的内容输入流，由调用方负责关闭
     * @throws DomainException 类型对应的插件未装配时抛出 {@code SYSTEM_ERROR}
     */
    InputStream load(String bucket, String objectKey, long offset, long length, String storageType);

    /**
     * 删除文件内容。
     *
     * @param bucket 桶名称
     * @param objectKey 桶内对象键
     */
    void delete(String bucket, String objectKey);

    /**
     * 生成预签名下载 URL（使用当前活动插件）。
     *
     * <p>默认实现不支持预签名（本地文件系统无此能力），由对象存储插件覆盖；
     * 调用方无需感知具体存储类型。</p>
     *
     * @param bucket 桶名称
     * @param objectKey 桶内对象键
     * @param expiresSeconds 有效期（秒）
     * @return 预签名下载 URL
     * @throws DomainException 当前存储插件不支持预签名时抛出参数错误
     */
    default String presignedDownloadUrl(String bucket, String objectKey, int expiresSeconds) {
        return presignedDownloadUrl(bucket, objectKey, expiresSeconds, null);
    }

    /**
     * 按落盘时的存储插件类型生成预签名下载 URL。
     *
     * @param bucket 桶名称
     * @param objectKey 桶内对象键
     * @param expiresSeconds 有效期（秒）
     * @param storageType 落盘时的插件类型，可为 {@code null}（回落活动插件）
     * @return 预签名下载 URL
     * @throws DomainException 插件不支持预签名或未装配时抛出
     */
    String presignedDownloadUrl(String bucket, String objectKey, int expiresSeconds, String storageType);
}
