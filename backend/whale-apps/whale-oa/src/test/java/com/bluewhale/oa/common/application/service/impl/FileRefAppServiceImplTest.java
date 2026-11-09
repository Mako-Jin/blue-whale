package com.bluewhale.oa.common.application.service.impl;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.oa.common.OaErrorCode;
import com.bluewhale.oa.common.application.command.FileRefRegisterCommand;
import com.bluewhale.oa.common.domain.gateway.FileMetaGateway;
import com.bluewhale.oa.common.domain.model.FileRef;
import com.bluewhale.oa.common.domain.repository.FileRefRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 附件引用登记校验测试：文件存在性、状态与上传人归属。
 */
@ExtendWith(MockitoExtension.class)
class FileRefAppServiceImplTest {

    @Mock
    private FileRefRepository fileRefRepository;

    @Mock
    private FileMetaGateway fileMetaGateway;

    private FileRefAppServiceImpl fileRefAppService;

    /**
     * 手动构造被测服务，避免 Spring 容器启动。
     */
    @BeforeEach
    void setUp() {
        fileRefAppService = new FileRefAppServiceImpl(fileRefRepository, fileMetaGateway);
    }

    @Test
    @DisplayName("register：文件不存在时拒绝登记")
    void shouldRejectWhenFileMissing() {
        when(fileMetaGateway.findFile(9L)).thenReturn(Optional.empty());

        BizException exception = assertThrows(BizException.class, () -> fileRefAppService.register(command()));

        assertEquals(OaErrorCode.FILE_REF_INVALID, exception.getCode());
    }

    @Test
    @DisplayName("register：已删除文件拒绝登记")
    void shouldRejectDeletedFile() {
        when(fileMetaGateway.findFile(9L)).thenReturn(Optional.of(meta(100L, 1, 1)));

        BizException exception = assertThrows(BizException.class, () -> fileRefAppService.register(command()));

        assertEquals(OaErrorCode.FILE_REF_INVALID, exception.getCode());
    }

    @Test
    @DisplayName("register：非正常状态（归档锁定）拒绝登记")
    void shouldRejectLockedFile() {
        when(fileMetaGateway.findFile(9L)).thenReturn(Optional.of(meta(100L, 0, 2)));

        BizException exception = assertThrows(BizException.class, () -> fileRefAppService.register(command()));

        assertEquals(OaErrorCode.FILE_REF_INVALID, exception.getCode());
    }

    @Test
    @DisplayName("register：非本人上传的文件拒绝登记")
    void shouldRejectOtherUsersFile() {
        when(fileMetaGateway.findFile(9L)).thenReturn(Optional.of(meta(200L, 0, 1)));

        BizException exception = assertThrows(BizException.class, () -> fileRefAppService.register(command()));

        assertEquals(OaErrorCode.FILE_REF_FORBIDDEN, exception.getCode());
    }

    @Test
    @DisplayName("register：本人文件登记成功，文件名/大小/MIME 以文件服务元数据为准")
    void shouldRegisterWithServerMeta() {
        when(fileMetaGateway.findFile(9L)).thenReturn(Optional.of(meta(100L, 0, 1)));
        when(fileRefRepository.insertIfAbsent(any(FileRef.class)))
                .thenAnswer(invocation -> Optional.of(invocation.getArgument(0)));

        fileRefAppService.register(command());

        ArgumentCaptor<FileRef> captor = ArgumentCaptor.forClass(FileRef.class);
        verify(fileRefRepository).insertIfAbsent(captor.capture());
        FileRef saved = captor.getValue();
        assertEquals("服务端原始文件名.pdf", saved.getFileName());
        assertEquals(2048L, saved.getFileSize());
        assertEquals("application/pdf", saved.getContentType());
        assertEquals(100L, saved.getUploadedBy());
    }

    /**
     * 构造登记命令。
     *
     * @return 登记命令
     */
    private static FileRefRegisterCommand command() {
        FileRefRegisterCommand command = new FileRefRegisterCommand();
        command.setBizType("LEAVE");
        command.setBizId(1L);
        command.setFileId(9L);
        command.setFileName("客户端伪造名.exe");
        command.setFileSize(1L);
        command.setContentType("application/octet-stream");
        command.setUploadedBy(100L);
        return command;
    }

    /**
     * 构造文件元数据。
     *
     * @param uploaderId 上传人
     * @param deleted    逻辑删除标记
     * @param status     文件状态
     * @return 元数据
     */
    private static FileMetaGateway.FileMeta meta(Long uploaderId, int deleted, int status) {
        return new FileMetaGateway.FileMeta(9L, "服务端原始文件名.pdf", 2048L,
                "application/pdf", uploaderId, deleted, status);
    }
}
