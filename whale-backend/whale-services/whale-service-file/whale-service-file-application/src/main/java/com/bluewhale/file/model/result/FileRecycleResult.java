package com.bluewhale.file.model.result;

import java.time.LocalDateTime;

/**
 * 回收站文件结果。
 *
 * <p>表未单独记录删除时间，{@code updateTime} 是最后一次业务更新时间，不代表删除时刻。</p>
 *
 * @param id 文件主键
 * @param fileName 存储文件名
 * @param originalName 原始文件名
 * @param bucket 桶名称
 * @param fileSize 文件字节数
 * @param contentType MIME 类型
 * @param status 文件状态码（0 禁用 / 1 正常 / 2 归档锁定）
 * @param uploaderId 上传用户 ID；系统上传为 {@code null}
 * @param createTime 创建时间
 * @param updateTime 最后更新时间
 * @author BlueWhale
 */
public record FileRecycleResult(Long id, String fileName, String originalName, String bucket, Long fileSize,
                                String contentType, Integer status, Long uploaderId,
                                LocalDateTime createTime, LocalDateTime updateTime) {
}
