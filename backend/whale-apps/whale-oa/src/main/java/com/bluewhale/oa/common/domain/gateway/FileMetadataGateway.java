package com.bluewhale.oa.common.domain.gateway;

import com.bluewhale.oa.common.domain.model.FileMetadata;

import java.util.Optional;

/**
 * 文件元数据读取端口（whale-file）。
 *
 * <p>供附件归属校验等共享场景使用，实现位于基础设施层，应用层只依赖本端口。</p>
 *
 * @author BlueWhale
 */
public interface FileMetadataGateway {

    /**
     * 查询文件元数据。
     *
     * @param fileId 文件ID
     * @return 元数据；文件不存在时返回空
     */
    Optional<FileMetadata> findFile(Long fileId);
}
