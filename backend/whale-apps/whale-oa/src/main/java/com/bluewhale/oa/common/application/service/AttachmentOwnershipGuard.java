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
 * <p>请假/报销/用章/公文等业务在保存前端传来的 {@code fileId} 前，必须先经本守卫
 * 回查 whale-file 元数据：文件必须存在、未删除、状态正常，且上传人为当前用户
 * （与网盘 {@code DriveAppServiceImpl} 的登记校验同一口径）。</p>
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
     * 校验文件可被当前用户引用；{@code fileId} 为空时跳过（由调用方决定必填性）。
     *
     * @param fileId 文件ID
     * @param userId 当前用户ID
     */
    public void assertReferable(Long fileId, Long userId) {
        if (fileId == null || fileId <= 0) {
            return;
        }
        FileMetadata meta = fileMetadataGateway.findFile(fileId)
                .orElseThrow(() -> OaErrorCode.exception(OaErrorCode.ATTACHMENT_NOT_FOUND));
        if (meta.deleted() != NOT_DELETED || meta.status() != FILE_STATUS_NORMAL) {
            throw OaErrorCode.exception(OaErrorCode.ATTACHMENT_NOT_FOUND);
        }
        if (meta.uploaderId() == null || !Objects.equals(meta.uploaderId(), userId)) {
            throw OaErrorCode.exception(OaErrorCode.ATTACHMENT_FORBIDDEN);
        }
    }
}
