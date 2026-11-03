package com.bluewhale.video.domain.repository;

import com.bluewhale.video.domain.model.FileAsset;

import java.util.Optional;

/**
 * 文件资产元数据端口（实现方调用 {@code whale-file} 内网接口）。
 *
 * @author BlueWhale
 */
public interface FileAssetPort {

    /**
     * 读取文件元数据。
     *
     * @param fileId 文件 ID
     * @param tenantId 租户 ID
     * @return 文件资产；不存在返回空
     */
    Optional<FileAsset> find(Long fileId, Long tenantId);
}
