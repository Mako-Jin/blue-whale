package com.bluewhale.file.domain.storage;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;

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
     * <p>默认实现不支持预签名（本地文件系统无此能力），由对象存储插件覆盖；
     * 调用方无需感知具体存储类型。</p>
     *
     * @param bucket 桶名称
     * @param objectKey 桶内对象键
     * @param expiresSeconds 有效期（秒）
     * @return 预签名下载 URL
     * @throws BizException 当前存储插件不支持预签名时抛出参数错误
     */
    default String presignedDownloadUrl(String bucket, String objectKey, int expiresSeconds) {
        throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "当前存储插件不支持预签名下载");
    }
}
