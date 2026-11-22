package com.bluewhale.doc.application.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.tenant.TenantContext;
import com.bluewhale.doc.application.service.DocAttachmentContent;
import com.bluewhale.doc.config.DocProperties;
import com.bluewhale.doc.infrastructure.client.DocFileClient;
import com.bluewhale.doc.infrastructure.persistence.entity.DocPage;
import com.bluewhale.doc.infrastructure.persistence.entity.DocPageAttachment;
import com.bluewhale.doc.infrastructure.persistence.mapper.DocPageAttachmentMapper;
import com.bluewhale.doc.interfaces.vo.DocPageAttachmentVO;
import com.bluewhale.doc.support.DocAccessGuard;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link DocAttachmentServiceImpl} 附件上传、下载与引用管理行为断言。
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DocAttachmentServiceImplTest {

    @Mock
    private DocPageAttachmentMapper attachmentMapper;

    @Mock
    private DocAccessGuard accessGuard;

    @Mock
    private DocFileClient fileClient;

    @Mock
    private DocProperties docProperties;

    @InjectMocks
    private DocAttachmentServiceImpl service;

    /**
     * 初始化 MyBatis-Plus 表信息，使 Lambda 查询列名解析可在纯单测中工作。
     */
    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, DocPageAttachment.class);
    }

    @BeforeEach
    void setUp() {
        TenantContext.set(1L);
        // Mock 默认返回 0 会误触体积上限，默认放开到 1MB，超限用例自行覆盖
        org.mockito.Mockito.lenient().when(docProperties.getAttachmentMaxBytes()).thenReturn(1_048_576L);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void listShouldRequireReadAndMapAttachments() {
        DocPageAttachment attachment = attachment(55L, 7L, 100L);
        attachment.setCreateTime(LocalDateTime.of(2026, 1, 2, 3, 4));
        when(attachmentMapper.selectList(any())).thenReturn(List.of(attachment));

        List<DocPageAttachmentVO> result = service.list(7L, 2L);

        verify(accessGuard).requirePageRead(7L, 2L);
        assertThat(result).singleElement().satisfies(vo -> {
            assertThat(vo.getId()).isEqualTo(55L);
            assertThat(vo.getFileId()).isEqualTo(100L);
            assertThat(vo.getContentUrl()).isEqualTo("/doc/v1/page/7/attachment/55/content");
            assertThat(vo.getCreateTime()).isEqualTo(LocalDateTime.of(2026, 1, 2, 3, 4));
        });
    }

    @Test
    void uploadShouldRejectNullFile() {
        when(accessGuard.requirePage(7L)).thenReturn(page(7L, "DRAFT"));

        assertThatThrownBy(() -> service.upload(7L, null, "ATTACHMENT", 2L))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("附件不能为空");
        verify(fileClient, never()).upload(any(), any(), anyLong(), anyLong());
    }

    @Test
    void uploadShouldRejectEmptyFile() {
        when(accessGuard.requirePage(7L)).thenReturn(page(7L, "DRAFT"));
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(true);

        assertThatThrownBy(() -> service.upload(7L, file, "ATTACHMENT", 2L))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("附件不能为空");
    }

    @Test
    void uploadShouldRejectOversizedFile() {
        when(accessGuard.requirePage(7L)).thenReturn(page(7L, "DRAFT"));
        when(docProperties.getAttachmentMaxBytes()).thenReturn(10L);
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getSize()).thenReturn(11L);

        assertThatThrownBy(() -> service.upload(7L, file, "ATTACHMENT", 2L))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getCode())
                .isEqualTo(40006);
        verify(fileClient, never()).upload(any(), any(), anyLong(), anyLong());
    }

    @Test
    void uploadShouldRejectArchivedPage() {
        when(accessGuard.requirePage(7L)).thenReturn(page(7L, "ARCHIVED"));

        assertThatThrownBy(() -> service.upload(7L, mock(MultipartFile.class), "ATTACHMENT", 2L))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("已归档页面不可编辑附件");
    }

    @Test
    void uploadShouldInsertNewReference() {
        when(accessGuard.requirePage(7L)).thenReturn(page(7L, "DRAFT"));
        MultipartFile file = file("a.txt", 10L);
        DocFileClient.UploadedFile uploaded = new DocFileClient.UploadedFile(100L, "a.txt", "text/plain", 10L);
        when(fileClient.upload(file, "whale-doc", 2L, 1L)).thenReturn(uploaded);
        when(attachmentMapper.selectAny(1L, 7L, 100L)).thenReturn(null);
        when(attachmentMapper.insert(any(DocPageAttachment.class))).thenAnswer(invocation -> {
            DocPageAttachment entity = invocation.getArgument(0);
            entity.setId(55L);
            return 1;
        });

        DocPageAttachmentVO vo = service.upload(7L, file, "inline", 2L);

        ArgumentCaptor<DocPageAttachment> captor = ArgumentCaptor.forClass(DocPageAttachment.class);
        verify(attachmentMapper).insert(captor.capture());
        assertThat(captor.getValue().getPageId()).isEqualTo(7L);
        assertThat(captor.getValue().getFileId()).isEqualTo(100L);
        assertThat(captor.getValue().getUsageType()).isEqualTo("INLINE");
        assertThat(captor.getValue().getSortNo()).isZero();
        assertThat(vo.getContentUrl()).isEqualTo("/doc/v1/page/7/attachment/55/content");
        assertThat(vo.getOriginalName()).isEqualTo("a.txt");
    }

    @Test
    void uploadShouldReactivateExistingReference() {
        when(accessGuard.requirePage(7L)).thenReturn(page(7L, "DRAFT"));
        MultipartFile file = file("a.txt", 10L);
        DocFileClient.UploadedFile uploaded = new DocFileClient.UploadedFile(100L, "a.txt", "text/plain", 10L);
        when(fileClient.upload(file, "whale-doc", 2L, 1L)).thenReturn(uploaded);
        DocPageAttachment existing = attachment(55L, 7L, 100L);
        when(attachmentMapper.selectAny(1L, 7L, 100L)).thenReturn(existing);
        when(attachmentMapper.selectById(55L)).thenReturn(existing);

        service.upload(7L, file, "ATTACHMENT", 2L);

        verify(attachmentMapper).reactivate(1L, 55L, "a.txt", "text/plain", 10L, "ATTACHMENT");
        verify(attachmentMapper, never()).insert(any(DocPageAttachment.class));
    }

    @Test
    void uploadShouldFailWhenFileServiceReturnsNoFileId() {
        when(accessGuard.requirePage(7L)).thenReturn(page(7L, "DRAFT"));
        MultipartFile file = file("a.txt", 10L);
        when(fileClient.upload(file, "whale-doc", 2L, 1L))
                .thenReturn(new DocFileClient.UploadedFile(null, "a.txt", "text/plain", 10L));

        assertThatThrownBy(() -> service.upload(7L, file, "ATTACHMENT", 2L))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getCode())
                .isEqualTo(500);
        verify(attachmentMapper, never()).insert(any(DocPageAttachment.class));
    }

    @Test
    void uploadShouldFallbackNameAndContentType() {
        when(accessGuard.requirePage(7L)).thenReturn(page(7L, "DRAFT"));
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getSize()).thenReturn(10L);
        when(file.getOriginalFilename()).thenReturn(null);
        when(fileClient.upload(file, "whale-doc", 2L, 1L))
                .thenReturn(new DocFileClient.UploadedFile(100L, " ", null, 10L));
        when(attachmentMapper.insert(any(DocPageAttachment.class))).thenReturn(1);

        service.upload(7L, file, null, 2L);

        ArgumentCaptor<DocPageAttachment> captor = ArgumentCaptor.forClass(DocPageAttachment.class);
        verify(attachmentMapper).insert(captor.capture());
        assertThat(captor.getValue().getOriginalName()).isEqualTo("file");
        assertThat(captor.getValue().getContentType()).isEqualTo("application/octet-stream");
        assertThat(captor.getValue().getUsageType()).isEqualTo("ATTACHMENT");
    }

    @Test
    void deleteShouldRejectAttachmentOfOtherPage() {
        when(accessGuard.requirePage(7L)).thenReturn(page(7L, "DRAFT"));
        DocPageAttachment attachment = attachment(55L, 8L, 100L);
        when(attachmentMapper.selectById(55L)).thenReturn(attachment);

        assertThatThrownBy(() -> service.delete(7L, 55L, 2L))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getCode())
                .isEqualTo(40401);
        verify(attachmentMapper, never()).deleteById(anyLong());
    }

    @Test
    void deleteShouldRemoveReference() {
        when(accessGuard.requirePage(7L)).thenReturn(page(7L, "DRAFT"));
        when(attachmentMapper.selectById(55L)).thenReturn(attachment(55L, 7L, 100L));

        service.delete(7L, 55L, 2L);

        verify(attachmentMapper).deleteById(55L);
    }

    @Test
    void contentShouldProxyBytesWithDefaultContentType() {
        when(accessGuard.requirePage(7L)).thenReturn(page(7L, "DRAFT"));
        DocPageAttachment attachment = attachment(55L, 7L, 100L);
        attachment.setContentType(" ");
        when(attachmentMapper.selectById(55L)).thenReturn(attachment);
        when(fileClient.content(100L)).thenReturn(new byte[]{1, 2, 3});

        DocAttachmentContent content = service.content(7L, 55L, 2L);

        assertThat(content.content()).containsExactly(1, 2, 3);
        assertThat(content.contentType()).isEqualTo("application/octet-stream");
        assertThat(content.filename()).isEqualTo("a.txt");
    }

    @Test
    void contentShouldThrowWhenAttachmentMissing() {
        when(accessGuard.requirePage(7L)).thenReturn(page(7L, "DRAFT"));
        when(attachmentMapper.selectById(55L)).thenReturn(null);

        assertThatThrownBy(() -> service.content(7L, 55L, 2L))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getCode())
                .isEqualTo(40401);
        verify(fileClient, never()).content(anyLong());
    }

    /**
     * 构造可上传文件。
     *
     * @param name 文件名
     * @param size 字节数
     * @return 文件
     */
    private MultipartFile file(String name, long size) {
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getSize()).thenReturn(size);
        when(file.getOriginalFilename()).thenReturn(name);
        return file;
    }

    /**
     * 构造页面。
     *
     * @param id     页面ID
     * @param status 状态
     * @return 页面
     */
    private DocPage page(Long id, String status) {
        DocPage page = new DocPage();
        page.setId(id);
        page.setStatus(status);
        return page;
    }

    /**
     * 构造附件引用。
     *
     * @param id     引用ID
     * @param pageId 页面ID
     * @param fileId 文件ID
     * @return 附件引用
     */
    private DocPageAttachment attachment(Long id, Long pageId, Long fileId) {
        DocPageAttachment attachment = new DocPageAttachment();
        attachment.setId(id);
        attachment.setPageId(pageId);
        attachment.setFileId(fileId);
        attachment.setOriginalName("a.txt");
        attachment.setContentType("text/plain");
        attachment.setFileSize(10L);
        attachment.setUsageType("ATTACHMENT");
        attachment.setSortNo(0);
        return attachment;
    }
}
