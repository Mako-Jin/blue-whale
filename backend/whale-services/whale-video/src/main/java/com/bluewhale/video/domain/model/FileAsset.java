package com.bluewhale.video.domain.model;

/**
 * 文件资产元数据（来自 {@code whale-file} 内网接口）。
 *
 * @param fileId 文件 ID
 * @param contentType 文件 MIME 类型
 * @param sizeBytes 文件字节数
 * @param uploaderId 上传者用户 ID（内部上传时为空）
 * @param usable 是否可用（状态正常且未删除）
 * @author BlueWhale
 */
public record FileAsset(Long fileId, String contentType, long sizeBytes, Long uploaderId, boolean usable) {

    /**
     * 判断文件是否可用。
     *
     * @return 状态正常且未删除返回 {@code true}
     */
    public boolean available() {
        return usable && fileId != null && fileId > 0;
    }
}
