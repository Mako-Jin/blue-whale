package com.bluewhale.oa.common.application.service;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.oa.common.OaErrorCode;
import com.bluewhale.oa.common.domain.gateway.FileMetadataGateway;
import com.bluewhale.oa.common.domain.model.FileMetadata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link AttachmentOwnershipGuard} 单元测试：附件归属校验拒绝分支与放行分支。
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
class AttachmentOwnershipGuardTest {

    @Mock
    private FileMetadataGateway fileMetadataGateway;

    private AttachmentOwnershipGuard guard;

    /**
     * 构造被测守卫。
     */
    @BeforeEach
    void setUp() {
        guard = new AttachmentOwnershipGuard(fileMetadataGateway);
    }

    @Test
    @DisplayName("fileId 为空或非法时跳过校验，不调用文件服务")
    void shouldSkipWhenFileIdNullOrInvalid() {
        guard.assertReferable(null, 100L);
        guard.assertReferable(0L, 100L);

        verifyNoInteractions(fileMetadataGateway);
    }

    @Test
    @DisplayName("文件不存在时拒绝引用（40443）")
    void shouldRejectWhenFileMissing() {
        when(fileMetadataGateway.findFile(1L)).thenReturn(Optional.empty());

        BizException ex = assertThrows(BizException.class, () -> guard.assertReferable(1L, 100L));

        assertEquals(OaErrorCode.ATTACHMENT_NOT_FOUND, ex.getCode());
    }

    @Test
    @DisplayName("文件已删除或停用时拒绝引用（40443）")
    void shouldRejectWhenFileDeletedOrDisabled() {
        when(fileMetadataGateway.findFile(2L)).thenReturn(Optional.of(meta(2L, 100L, 1, 1)));
        when(fileMetadataGateway.findFile(3L)).thenReturn(Optional.of(meta(3L, 100L, 0, 0)));

        BizException deleted = assertThrows(BizException.class, () -> guard.assertReferable(2L, 100L));
        BizException disabled = assertThrows(BizException.class, () -> guard.assertReferable(3L, 100L));

        assertEquals(OaErrorCode.ATTACHMENT_NOT_FOUND, deleted.getCode());
        assertEquals(OaErrorCode.ATTACHMENT_NOT_FOUND, disabled.getCode());
    }

    @Test
    @DisplayName("上传人为空或非本人时禁止引用（40307）")
    void shouldRejectWhenNotOwnedByCurrentUser() {
        when(fileMetadataGateway.findFile(4L)).thenReturn(Optional.of(meta(4L, null, 0, 1)));
        when(fileMetadataGateway.findFile(5L)).thenReturn(Optional.of(meta(5L, 200L, 0, 1)));

        BizException anonymous = assertThrows(BizException.class, () -> guard.assertReferable(4L, 100L));
        BizException foreign = assertThrows(BizException.class, () -> guard.assertReferable(5L, 100L));

        assertEquals(OaErrorCode.ATTACHMENT_FORBIDDEN, anonymous.getCode());
        assertEquals(OaErrorCode.ATTACHMENT_FORBIDDEN, foreign.getCode());
    }

    @Test
    @DisplayName("本人上传且文件正常时放行")
    void shouldPassWhenOwnedByCurrentUser() {
        when(fileMetadataGateway.findFile(6L)).thenReturn(Optional.of(meta(6L, 100L, 0, 1)));

        assertDoesNotThrow(() -> guard.assertReferable(6L, 100L));
    }

    /**
     * 构造文件元数据。
     *
     * @param fileId 文件ID
     * @param uploaderId 上传人
     * @param deleted 删除标记
     * @param status 状态
     * @return 元数据
     */
    private static FileMetadata meta(Long fileId, Long uploaderId, int deleted, int status) {
        return new FileMetadata(fileId, "a.pdf", 2048L, "application/pdf", uploaderId, deleted, status);
    }
}
