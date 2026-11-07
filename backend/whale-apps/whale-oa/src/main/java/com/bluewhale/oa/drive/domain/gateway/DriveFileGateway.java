package com.bluewhale.oa.drive.domain.gateway;

import java.util.Optional;

/**
 * 文件服务网关：读取文件元数据与内容。
 *
 * @author BlueWhale
 */
public interface DriveFileGateway {

    /**
     * 查询文件元数据。
     *
     * @param fileId whale-file 文件ID
     * @return 元数据；不存在返回空
     */
    Optional<FileMeta> findFile(Long fileId);

    /**
     * 读取文件内容。
     *
     * @param fileId whale-file 文件ID
     * @return 文件字节；不可用返回 null
     */
    byte[] content(Long fileId);

    /**
     * 文件元数据快照。
     *
     * @param id           文件ID
     * @param originalName 原始文件名
     * @param fileSize     字节数
     * @param contentType  MIME 类型
     * @param uploaderId   上传人用户ID
     * @param deleted      逻辑删除标记
     * @param status       文件状态
     */
    record FileMeta(Long id, String originalName, Long fileSize, String contentType,
                    Long uploaderId, Integer deleted, Integer status) {
    }
}
