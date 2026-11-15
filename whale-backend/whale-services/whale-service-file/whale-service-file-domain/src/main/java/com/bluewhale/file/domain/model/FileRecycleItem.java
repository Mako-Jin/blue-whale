package com.bluewhale.file.domain.model;

import java.time.LocalDateTime;

/**
 * 回收站文件投影（只读查询模型）。
 *
 * <p>回收站需要展示真实删除时间，并驱动到期物理清理（需要存储定位与落盘介质），
 * 这些字段不属于文件聚合的业务行为，故以投影承载，避免为聚合引入仅查询使用的状态。</p>
 *
 * @param id 文件主键
 * @param fileName 存储文件名
 * @param originalName 原始文件名
 * @param bucket 桶名称
 * @param storageType 落盘时的存储插件类型，可为 {@code null}
 * @param objectKey 对象键
 * @param fileSize 文件字节数
 * @param contentType MIME 类型
 * @param status 文件状态码
 * @param uploaderId 上传用户 ID，可为 {@code null}
 * @param createTime 创建时间
 * @param updateTime 最后更新时间
 * @param deleteTime 逻辑删除时间，可为 {@code null}（历史数据未回填时）
 * @author BlueWhale
 */
public record FileRecycleItem(Long id, String fileName, String originalName, String bucket, String storageType,
                              String objectKey, Long fileSize, String contentType, Integer status, Long uploaderId,
                              LocalDateTime createTime, LocalDateTime updateTime, LocalDateTime deleteTime) {
}
