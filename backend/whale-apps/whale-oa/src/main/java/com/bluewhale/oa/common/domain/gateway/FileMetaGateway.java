package com.bluewhale.oa.common.domain.gateway;

import java.util.Optional;

/**
 * 文件服务元数据网关：供附件登记前校验文件归属与状态。
 *
 * @author BlueWhale
 */
public interface FileMetaGateway {

    /**
     * 查询文件元数据。
     *
     * @param fileId whale-file 文件ID
     * @return 元数据；文件不存在返回空
     */
    Optional<FileMeta> findFile(Long fileId);

    /**
     * 文件元数据快照。
     *
     * @param id           文件ID
     * @param originalName 原始文件名
     * @param fileSize     字节数
     * @param contentType  MIME 类型
     * @param uploaderId   上传人用户ID
     * @param deleted      逻辑删除标记：0 未删除 / 1 已删除
     * @param status       文件状态：1 正常 / 0 禁用 / 2 归档锁定
     */
    record FileMeta(Long id, String originalName, Long fileSize, String contentType,
                    Long uploaderId, Integer deleted, Integer status) {
    }
}
