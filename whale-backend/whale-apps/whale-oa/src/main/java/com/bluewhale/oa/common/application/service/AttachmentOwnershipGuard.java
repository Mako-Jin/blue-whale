package com.bluewhale.oa.common.application.service;

import com.bluewhale.oa.common.OaErrorCode;
import com.bluewhale.oa.common.domain.gateway.FileMetadataGateway;
import com.bluewhale.oa.common.domain.model.FileMetadata;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * 附件归属守卫：登记业务附件前核验文件归属，阻断跨用户引用。
 *
 * <p>本类是 OA 内<b>唯一</b>的附件归属校验实现。请假/报销/用章/公文等业务在保存
 * 前端传来的 {@code fileId} 前，以及文件引用登记（{@code FileRefAppServiceImpl}）
 * 落库前，都必须经本守卫回查 whale-file 元数据：文件必须存在、未删除、状态正常，
 * 且上传人为当前用户（与网盘 {@code DriveAppServiceImpl} 的登记校验同一口径）。</p>
 *
 * <p>返回元数据而非 {@code void}：放行后调用方可直接复用文件名 / 大小 / MIME，
 * 避免为拿同样的字段再打一次文件服务。</p>
 *
 * @author BlueWhale
 */
@Component
@RequiredArgsConstructor
public class AttachmentOwnershipGuard {

    /** 文件状态：正常 */
    private static final int FILE_STATUS_NORMAL = 1;

    /** 逻辑删除：未删除 */
    private static final int NOT_DELETED = 0;

    private final FileMetadataGateway fileMetadataGateway;

    /**
     * 校验文件可被当前用户引用，通过后返回元数据。
     *
     * @param fileId 文件ID；为空或非正数时跳过校验并返回 {@code null}（由调用方决定必填性）
     * @param userId 当前用户ID
     * @return 文件元数据；{@code fileId} 为空时返回 {@code null}
     */
    public FileMetadata assertReferable(Long fileId, Long userId) {
        if (fileId == null || fileId <= 0) {
            return null;
        }
        FileMetadata meta = fileMetadataGateway.findFile(fileId)
                .orElseThrow(() -> OaErrorCode.exception(OaErrorCode.ATTACHMENT_NOT_FOUND));
        if (meta.deleted() != NOT_DELETED || meta.status() != FILE_STATUS_NORMAL) {
            throw OaErrorCode.exception(OaErrorCode.ATTACHMENT_NOT_FOUND);
        }
        if (meta.uploaderId() == null || !Objects.equals(meta.uploaderId(), userId)) {
            throw OaErrorCode.exception(OaErrorCode.ATTACHMENT_FORBIDDEN);
        }
        return meta;
    }
}
