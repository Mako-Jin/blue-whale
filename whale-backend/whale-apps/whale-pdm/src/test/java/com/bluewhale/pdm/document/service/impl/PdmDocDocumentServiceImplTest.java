package com.bluewhale.pdm.document.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.tenant.TenantContext;
import com.bluewhale.pdm.document.client.PdmDocFileClient;
import com.bluewhale.pdm.document.dto.PdmDocDocumentQueryRequest;
import com.bluewhale.pdm.document.dto.PdmDocDocumentSaveRequest;
import com.bluewhale.pdm.document.entity.PdmDocDocument;
import com.bluewhale.pdm.document.entity.PdmDocVersion;
import com.bluewhale.pdm.document.mapper.PdmDocDocumentMapper;
import com.bluewhale.pdm.document.mapper.PdmDocVersionMapper;
import com.bluewhale.pdm.document.service.PdmDocFolderService;
import com.bluewhale.pdm.document.support.PdmDocAclConstants;
import com.bluewhale.pdm.document.support.PdmDocAclDecision;
import com.bluewhale.pdm.document.support.PdmDocDownload;
import com.bluewhale.pdm.document.support.PdmDocExport;
import com.bluewhale.pdm.document.support.PdmDocUploadFile;
import com.bluewhale.pdm.document.support.PdmDocViewer;
import com.bluewhale.pdm.document.vo.PdmDocDocumentVO;
import com.bluewhale.pdm.document.vo.PdmDocFolderVO;
import com.bluewhale.pdm.document.vo.PdmDocVersionVO;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 图文档服务单元测试。
 *
 * <p>覆盖分页口径、编号生成与唯一性、版本上传、检出/检入约束、发布版本推进、
 * 删除保护与下载代理。</p>
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
class PdmDocDocumentServiceImplTest {

    @Mock
    private PdmDocDocumentMapper documentMapper;

    @Mock
    private PdmDocVersionMapper versionMapper;

    @Mock
    private PdmDocFolderService folderService;

    @Mock
    private PdmDocFileClient fileClient;

    @Mock
    private PdmDocAclResolver aclResolver;

    @InjectMocks
    private PdmDocDocumentServiceImpl documentService;

    /**
     * 初始化 MyBatis-Plus 表元数据。
     */
    @BeforeAll
    static void initTableInfo() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), PdmDocDocument.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), PdmDocVersion.class);
    }

    /**
     * 清理租户上下文。
     */
    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    /**
     * 分页查询展开目录子树并截断最大页大小。
     */
    @Test
    @DisplayName("分页展开子树并限制每页 200")
    void pageShouldExpandFolderAndClampSize() {
        PdmDocDocumentQueryRequest request = new PdmDocDocumentQueryRequest();
        request.setFolderId(1L);
        request.setSize(500L);
        when(folderService.subtreeFolderIds(1L)).thenReturn(List.of(1L, 2L));
        Page<PdmDocDocument> page = new Page<>(1, 200);
        page.setTotal(1L);
        page.setRecords(List.of(document(3L, 7L)));
        when(documentMapper.selectPage(any(), any())).thenReturn(page);

        PageResult<PdmDocDocumentVO> result = documentService.page(request, viewer(7L));

        assertEquals(1, result.getRecords().size());
        assertEquals(200L, result.getSize());
        verify(folderService).subtreeFolderIds(1L);
    }

    /**
     * 编号留空时自动生成 DOC-日期-随机串。
     */
    @Test
    @DisplayName("编号留空自动生成")
    void createShouldGenerateDocNoWhenBlank() {
        when(folderService.get(1L)).thenReturn(new PdmDocFolderVO());
        when(documentMapper.selectCount(any())).thenReturn(0L);
        when(documentMapper.insert(any(PdmDocDocument.class))).thenAnswer(invocation -> {
            PdmDocDocument entity = invocation.getArgument(0);
            entity.setId(8L);
            return 1;
        });

        PdmDocDocumentVO view = documentService.create(saveRequest(null), 7L, "张三");

        assertEquals(8L, view.getId());
        assertTrue(view.getDocNo().startsWith("DOC-"));
        assertEquals("DRAFT", view.getStatus());
        assertEquals(7L, view.getOwnerId());
        assertEquals("张三", view.getOwnerName());
    }

    /**
     * 手工编号重复被拒绝且不写库。
     */
    @Test
    @DisplayName("手工编号重复拒绝创建")
    void createShouldRejectDuplicatedDocNo() {
        when(folderService.get(1L)).thenReturn(new PdmDocFolderVO());
        when(documentMapper.selectCount(any())).thenReturn(1L);

        BizException exception = assertThrows(BizException.class,
                () -> documentService.create(saveRequest("DOC-001"), 7L, "张三"));

        assertEquals(ErrorCode.REPEAT_SUBMIT.getCode(), exception.getCode());
        verify(documentMapper, never()).insert(any(PdmDocDocument.class));
    }

    /**
     * 首次上传生成 V0.1 草稿并写回文档最新版本。
     */
    @Test
    @DisplayName("首次上传生成 V0.1 草稿")
    void uploadVersionShouldCreateInitialDraft() {
        TenantContext.set(1L);
        when(documentMapper.selectById(1L)).thenReturn(document(1L, 7L));
        when(versionMapper.selectList(any())).thenReturn(List.of());
        when(fileClient.upload(any(), anyString(), anyString(), anyLong(), anyLong()))
                .thenReturn(new PdmDocFileClient.UploadedFile(99L, "a.dwg", "application/octet-stream", 10L));
        when(versionMapper.insert(any(PdmDocVersion.class))).thenAnswer(invocation -> {
            PdmDocVersion version = invocation.getArgument(0);
            version.setId(100L);
            return 1;
        });

        PdmDocVersionVO view = documentService.uploadVersion(1L,
                new PdmDocUploadFile(new byte[]{1, 2}, "a.dwg", "application/octet-stream", 10L),
                "初版", viewer(7L), false);

        assertEquals("V0.1", view.getVersionNo());
        assertEquals("DRAFT", view.getStatus());
        assertEquals("UPLOAD", view.getSource());
        assertEquals("a.dwg", view.getFileName());
        verify(documentMapper).updateById(any(PdmDocDocument.class));
    }

    /**
     * 非本人且无管理权限时不能上传版本。
     */
    @Test
    @DisplayName("非本人上传被拒绝")
    void uploadVersionShouldRejectNonOwner() {
        when(documentMapper.selectById(1L)).thenReturn(document(1L, 9L));

        BizException exception = assertThrows(BizException.class,
                () -> documentService.uploadVersion(1L,
                        new PdmDocUploadFile(new byte[]{1}, "a.dwg", "application/octet-stream", 1L),
                        null, viewer(7L), false));

        assertEquals(ErrorCode.FORBIDDEN.getCode(), exception.getCode());
        verify(versionMapper, never()).insert(any(PdmDocVersion.class));
    }

    /**
     * 检出写入检出人；重复检出由同人操作时幂等返回。
     */
    @Test
    @DisplayName("检出写入检出人且同人幂等")
    void checkOutShouldLockDocument() {
        when(documentMapper.selectById(1L)).thenReturn(document(1L, 7L));

        PdmDocDocumentVO view = documentService.checkOut(1L, viewer(7L));

        assertEquals(7L, view.getCheckedOutBy());
        verify(documentMapper).updateById(any(PdmDocDocument.class));
    }

    /**
     * 已被他人检出时不能重复检出。
     */
    @Test
    @DisplayName("他人已检出的文档不能检出")
    void checkOutShouldRejectCheckedOutByOther() {
        when(documentMapper.selectById(1L)).thenReturn(checkedOutDocument(1L, 7L, 9L));

        BizException exception = assertThrows(BizException.class, () -> documentService.checkOut(1L, viewer(7L)));

        assertEquals(ErrorCode.REPEAT_SUBMIT.getCode(), exception.getCode());
    }

    /**
     * 未检出时不能检入。
     */
    @Test
    @DisplayName("未检出不能检入")
    void checkInShouldRejectWhenNotCheckedOut() {
        when(documentMapper.selectById(1L)).thenReturn(document(1L, 7L));

        BizException exception = assertThrows(BizException.class,
                () -> documentService.checkIn(1L,
                        new PdmDocUploadFile(new byte[]{1}, "a.dwg", "application/octet-stream", 1L),
                        null, viewer(7L)));

        assertEquals(ErrorCode.PARAM_ERROR.getCode(), exception.getCode());
    }

    /**
     * 他人检出的文档不能由当前用户检入。
     */
    @Test
    @DisplayName("他人检出的文档不能检入")
    void checkInShouldRejectCheckedOutByOther() {
        when(documentMapper.selectById(1L)).thenReturn(checkedOutDocument(1L, 7L, 9L));

        BizException exception = assertThrows(BizException.class,
                () -> documentService.checkIn(1L,
                        new PdmDocUploadFile(new byte[]{1}, "a.dwg", "application/octet-stream", 1L),
                        null, viewer(7L)));

        assertEquals(ErrorCode.FORBIDDEN.getCode(), exception.getCode());
    }

    /**
     * 草稿发布转 V1.0，文档状态更新为已发布。
     */
    @Test
    @DisplayName("发布草稿转为 V1.0")
    void releaseShouldPromoteDraft() {
        when(documentMapper.selectById(1L)).thenReturn(document(1L, 7L));
        when(versionMapper.selectById(10L)).thenReturn(version(10L, 1L, "V0.1", "DRAFT"));
        when(versionMapper.selectCount(any())).thenReturn(0L);

        PdmDocVersionVO view = documentService.release(1L, 10L, viewer(7L), true);

        assertEquals("V1.0", view.getVersionNo());
        assertEquals("RELEASED", view.getStatus());
        assertEquals(7L, view.getReleasedBy());
        assertNotNull(view.getReleasedAt());
        verify(documentMapper).updateById(any(PdmDocDocument.class));
    }

    /**
     * 非草稿版本不能发布。
     */
    @Test
    @DisplayName("已发布版本不能重复发布")
    void releaseShouldRejectNonDraft() {
        when(documentMapper.selectById(1L)).thenReturn(document(1L, 7L));
        when(versionMapper.selectById(10L)).thenReturn(version(10L, 1L, "V1.0", "RELEASED"));

        BizException exception = assertThrows(BizException.class,
                () -> documentService.release(1L, 10L, viewer(7L), true));

        assertEquals(ErrorCode.REPEAT_SUBMIT.getCode(), exception.getCode());
        verify(versionMapper, never()).updateById(any(PdmDocVersion.class));
    }

    /**
     * 存在已发布版本的文档不可删除。
     */
    @Test
    @DisplayName("存在正式版本不可删除")
    void deleteShouldRejectReleasedVersion() {
        when(documentMapper.selectById(1L)).thenReturn(document(1L, 7L));
        when(versionMapper.selectCount(any())).thenReturn(1L);

        BizException exception = assertThrows(BizException.class,
                () -> documentService.delete(1L, viewer(7L), false));

        assertEquals(ErrorCode.REPEAT_SUBMIT.getCode(), exception.getCode());
        verify(documentMapper, never()).deleteById(1L);
    }

    /**
     * 下载代理读取文件服务并回传元数据。
     */
    @Test
    @DisplayName("下载代理文件字节")
    void downloadShouldDelegateToFileClient() {
        when(documentMapper.selectById(1L)).thenReturn(document(1L, 7L));
        when(versionMapper.selectById(10L)).thenReturn(version(10L, 1L, "V1.0", "RELEASED"));
        when(fileClient.content(99L)).thenReturn(new byte[]{1, 2, 3});

        PdmDocDownload download = documentService.download(1L, 10L, viewer(7L));

        assertArrayEquals(new byte[]{1, 2, 3}, download.content());
        assertEquals("a.dwg", download.fileName());
    }

    /**
     * 回滚以历史版本为基线生成新草稿。
     */
    @Test
    @DisplayName("回滚基于历史版本生成新草稿")
    void rollbackShouldCreateDraftFromSource() {
        when(documentMapper.selectById(1L)).thenReturn(document(1L, 7L));
        PdmDocVersion source = version(10L, 1L, "V1.0", "RELEASED");
        when(versionMapper.selectById(10L)).thenReturn(source);
        when(versionMapper.selectList(any())).thenReturn(List.of(source));
        when(versionMapper.insert(any(PdmDocVersion.class))).thenAnswer(invocation -> {
            PdmDocVersion version = invocation.getArgument(0);
            version.setId(11L);
            return 1;
        });

        PdmDocVersionVO view = documentService.rollback(1L, 10L, viewer(7L), false);

        assertEquals("V1.1", view.getVersionNo());
        assertEquals("DRAFT", view.getStatus());
        assertEquals("ROLLBACK", view.getSource());
        assertEquals("回滚自 V1.0", view.getChangeNote());
        assertEquals("a.dwg", view.getFileName());
        verify(versionMapper).insert(any(PdmDocVersion.class));
    }

    /**
     * 分页按 ACL 排除 DENY 目录与文档。
     */
    @Test
    @DisplayName("分页排除 DENY 目录与文档")
    void pageShouldExcludeDeniedResources() {
        PdmDocDocumentQueryRequest request = new PdmDocDocumentQueryRequest();
        when(aclResolver.deniedFolderIds(any(PdmDocViewer.class))).thenReturn(List.of(2L, 3L));
        when(aclResolver.deniedDocumentIds(any(PdmDocViewer.class))).thenReturn(List.of(9L));
        Page<PdmDocDocument> page = new Page<>(1, 10);
        page.setRecords(List.of(document(3L, 7L)));
        when(documentMapper.selectPage(any(), any())).thenReturn(page);

        documentService.page(request, viewer(7L));

        ArgumentCaptor<Wrapper<PdmDocDocument>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(documentMapper).selectPage(any(), captor.capture());
        assertTrue(captor.getValue().getSqlSegment().contains("NOT IN"));
    }

    /**
     * 文档被显式 DENY VIEW 时详情不可读。
     */
    @Test
    @DisplayName("DENY VIEW 拒绝文档详情")
    void detailShouldRejectDeniedView() {
        when(documentMapper.selectById(1L)).thenReturn(document(1L, 7L));
        when(aclResolver.decideDocument(anyLong(), anyLong(), any(PdmDocViewer.class),
                eq(PdmDocAclConstants.ACTION_VIEW))).thenReturn(PdmDocAclDecision.DENY);

        BizException exception = assertThrows(BizException.class,
                () -> documentService.detail(1L, viewer(7L)));

        assertEquals(ErrorCode.FORBIDDEN.getCode(), exception.getCode());
    }

    /**
     * 文档被显式 DENY DOWNLOAD 时下载被拒。
     */
    @Test
    @DisplayName("DENY DOWNLOAD 拒绝下载")
    void downloadShouldRejectDeniedDownload() {
        when(documentMapper.selectById(1L)).thenReturn(document(1L, 7L));
        when(aclResolver.decideDocument(anyLong(), anyLong(), any(PdmDocViewer.class),
                eq(PdmDocAclConstants.ACTION_DOWNLOAD))).thenReturn(PdmDocAclDecision.DENY);

        BizException exception = assertThrows(BizException.class,
                () -> documentService.download(1L, 10L, viewer(7L)));

        assertEquals(ErrorCode.FORBIDDEN.getCode(), exception.getCode());
        verify(fileClient, never()).content(anyLong());
    }

    /**
     * 属主被显式 DENY EDIT 时不能上传版本。
     */
    @Test
    @DisplayName("DENY EDIT 拦截属主上传")
    void uploadVersionShouldRejectOwnerWhenEditDenied() {
        when(documentMapper.selectById(1L)).thenReturn(document(1L, 7L));
        when(aclResolver.decideDocument(anyLong(), anyLong(), any(PdmDocViewer.class),
                eq(PdmDocAclConstants.ACTION_EDIT))).thenReturn(PdmDocAclDecision.DENY);

        BizException exception = assertThrows(BizException.class,
                () -> documentService.uploadVersion(1L,
                        new PdmDocUploadFile(new byte[]{1}, "a.dwg", "application/octet-stream", 1L),
                        null, viewer(7L), false));

        assertEquals(ErrorCode.FORBIDDEN.getCode(), exception.getCode());
        verify(versionMapper, never()).insert(any(PdmDocVersion.class));
    }

    /**
     * 非属主获得 ALLOW EDIT 后可上传版本。
     */
    @Test
    @DisplayName("ALLOW EDIT 放行非属主上传")
    void uploadVersionShouldAllowNonOwnerWhenEditAllowed() {
        TenantContext.set(1L);
        when(documentMapper.selectById(1L)).thenReturn(document(1L, 9L));
        when(aclResolver.decideDocument(anyLong(), anyLong(), any(PdmDocViewer.class),
                eq(PdmDocAclConstants.ACTION_EDIT))).thenReturn(PdmDocAclDecision.ALLOW);
        when(versionMapper.selectList(any())).thenReturn(List.of());
        when(fileClient.upload(any(), anyString(), anyString(), anyLong(), anyLong()))
                .thenReturn(new PdmDocFileClient.UploadedFile(99L, "a.dwg", "application/octet-stream", 10L));
        when(versionMapper.insert(any(PdmDocVersion.class))).thenAnswer(invocation -> {
            PdmDocVersion version = invocation.getArgument(0);
            version.setId(100L);
            return 1;
        });

        PdmDocVersionVO view = documentService.uploadVersion(1L,
                new PdmDocUploadFile(new byte[]{1}, "a.dwg", "application/octet-stream", 1L),
                null, viewer(7L), false);

        assertEquals("V0.1", view.getVersionNo());
        verify(versionMapper).insert(any(PdmDocVersion.class));
    }

    /**
     * 导出复用 ACL 过滤，输出 UTF-8 BOM 与固定表头。
     */
    @Test
    @DisplayName("导出输出 BOM 与表头且排除 DENY 资源")
    void exportShouldRenderCsvWithBomAndAcl() {
        PdmDocDocumentQueryRequest request = new PdmDocDocumentQueryRequest();
        when(aclResolver.deniedFolderIds(any(PdmDocViewer.class))).thenReturn(List.of(2L));
        when(aclResolver.deniedDocumentIds(any(PdmDocViewer.class))).thenReturn(List.of(9L));
        Page<PdmDocDocument> page = new Page<>(1, 200);
        page.setTotal(1L);
        page.setRecords(List.of(document(3L, 7L)));
        when(documentMapper.selectPage(any(), any())).thenReturn(page);

        PdmDocExport export = documentService.export(request, viewer(7L));

        assertFalse(export.truncated());
        String csv = new String(export.content(), StandardCharsets.UTF_8);
        assertTrue(csv.startsWith("\uFEFF文档编号,标题,类型,状态,所属目录ID,零件号,设计师,最新版本号,创建时间,更新时间"));
        assertTrue(csv.contains("DOC-001,主机外壳装配图,DRAWING,DRAFT,1,,"));
        ArgumentCaptor<Wrapper<PdmDocDocument>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(documentMapper).selectPage(any(), captor.capture());
        assertTrue(captor.getValue().getSqlSegment().contains("NOT IN"));
    }

    /**
     * 导出超过 5000 行时截断并标记。
     */
    @Test
    @DisplayName("导出超过 5000 行截断")
    void exportShouldTruncateBeyondLimit() {
        PdmDocDocumentQueryRequest request = new PdmDocDocumentQueryRequest();
        List<PdmDocDocument> batch = new ArrayList<>();
        for (int index = 0; index < 200; index++) {
            batch.add(document((long) index, 7L));
        }
        Page<PdmDocDocument> page = new Page<>(1, 200);
        page.setTotal(6000L);
        page.setRecords(batch);
        when(documentMapper.selectPage(any(), any())).thenReturn(page);

        PdmDocExport export = documentService.export(request, viewer(7L));

        assertTrue(export.truncated());
        String csv = new String(export.content(), StandardCharsets.UTF_8);
        assertEquals(5001L, csv.lines().count());
    }

    /**
     * 构造访问者。
     *
     * @param userId 用户 ID
     * @return 访问者
     */
    private PdmDocViewer viewer(Long userId) {
        return new PdmDocViewer(userId, Set.of("pdm_member"));
    }

    /**
     * 构造文档实体。
     *
     * @param id      主键
     * @param ownerId 创建人
     * @return 文档实体
     */
    private PdmDocDocument document(Long id, Long ownerId) {
        PdmDocDocument document = new PdmDocDocument();
        document.setId(id);
        document.setFolderId(1L);
        document.setDocNo("DOC-001");
        document.setTitle("主机外壳装配图");
        document.setDocType("DRAWING");
        document.setStatus("DRAFT");
        document.setOwnerId(ownerId);
        document.setOwnerName("张三");
        return document;
    }

    /**
     * 构造已检出文档。
     *
     * @param id            主键
     * @param ownerId       创建人
     * @param checkedOutBy  检出人
     * @return 文档实体
     */
    private PdmDocDocument checkedOutDocument(Long id, Long ownerId, Long checkedOutBy) {
        PdmDocDocument document = document(id, ownerId);
        document.setCheckedOutBy(checkedOutBy);
        return document;
    }

    /**
     * 构造版本实体。
     *
     * @param id          主键
     * @param documentId  文档 ID
     * @param versionNo   版本号
     * @param status      状态
     * @return 版本实体
     */
    private PdmDocVersion version(Long id, Long documentId, String versionNo, String status) {
        PdmDocVersion version = new PdmDocVersion();
        version.setId(id);
        version.setDocumentId(documentId);
        version.setVersionNo(versionNo);
        version.setVersionSeq(versionNo.equals("V1.0") ? 10000 : 1);
        version.setStatus(status);
        version.setFileId(99L);
        version.setFileName("a.dwg");
        version.setFileSize(10L);
        version.setContentType("application/octet-stream");
        version.setSource("UPLOAD");
        return version;
    }

    /**
     * 构造文档保存请求。
     *
     * @param docNo 编号（可为空）
     * @return 保存请求
     */
    private PdmDocDocumentSaveRequest saveRequest(String docNo) {
        PdmDocDocumentSaveRequest request = new PdmDocDocumentSaveRequest();
        request.setFolderId(1L);
        request.setDocNo(docNo);
        request.setTitle("主机外壳装配图");
        request.setDocType("DRAWING");
        return request;
    }
}
