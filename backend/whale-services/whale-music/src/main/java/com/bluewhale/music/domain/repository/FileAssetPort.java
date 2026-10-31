package com.bluewhale.music.domain.repository;

import com.bluewhale.music.domain.model.FileAsset;

import java.util.Optional;

/**
 * 文件资产元数据端口（实现类在基础设施层调用 whale-file 内网接口）。
 *
 * @author BlueWhale
 */
public interface FileAssetPort {

    /**
     * 查询文件元数据。
     *
     * @param fileId 文件 ID
     * @param tenantId 租户 ID，可为 {@code null}
     * @return 文件元数据；文件不存在返回空
     */
    Optional<FileAsset> find(Long fileId, Long tenantId);
}
