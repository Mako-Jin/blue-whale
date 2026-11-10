package com.bluewhale.pdm.document.service.impl;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.pdm.document.dto.PdmDocAclSaveRequest;
import com.bluewhale.pdm.document.entity.PdmDocAcl;
import com.bluewhale.pdm.document.entity.PdmDocDocument;
import com.bluewhale.pdm.document.mapper.PdmDocAclMapper;
import com.bluewhale.pdm.document.mapper.PdmDocDocumentMapper;
import com.bluewhale.pdm.document.service.PdmDocFolderService;
import com.bluewhale.pdm.document.support.PdmDocAclConstants;
import com.bluewhale.pdm.document.support.PdmDocAclDecision;
import com.bluewhale.pdm.document.support.PdmDocViewer;
import com.bluewhale.pdm.document.vo.PdmDocAclVO;
import com.bluewhale.pdm.document.vo.PdmDocFolderVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ACL 规则服务单元测试。
 *
 * <p>覆盖主体归一、非法取值、唯一键幂等与 MANAGE 鉴权。</p>
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
class PdmDocAclServiceImplTest {

    @Mock
    private PdmDocAclMapper aclMapper;

    @Mock
    private PdmDocDocumentMapper documentMapper;

    @Mock
    private PdmDocFolderService folderService;

    @Mock
    private PdmDocAclResolver aclResolver;

    @InjectMocks
    private PdmDocAclServiceImpl aclService;

    /**
     * 非法资源类型被拒绝。
     */
    @Test
    @DisplayName("非法资源类型返回参数错误")
    void saveShouldRejectInvalidResourceType() {
        PdmDocAclSaveRequest request = request("INVALID", PdmDocAclConstants.SUBJECT_ALL, "0",
                PdmDocAclConstants.ACTION_VIEW, PdmDocAclConstants.EFFECT_ALLOW);

        BizException exception = assertThrows(BizException.class,
                () -> aclService.save(request, managerViewer()));

        assertEquals(ErrorCode.PARAM_ERROR.getCode(), exception.getCode());
    }

    /**
     * ALL 主体强制归一为 0。
     */
    @Test
    @DisplayName("ALL 主体归一为 0")
    void saveShouldNormalizeAllSubject() {
        stubFolderManage();
        when(aclMapper.selectList(any())).thenReturn(List.of());
        when(aclMapper.insert(any(PdmDocAcl.class))).thenAnswer(invocation -> {
            PdmDocAcl rule = invocation.getArgument(0);
            rule.setId(11L);
            return 1;
        });
        PdmDocAclSaveRequest request = request(PdmDocAclConstants.RESOURCE_FOLDER,
                PdmDocAclConstants.SUBJECT_ALL, "7",
                PdmDocAclConstants.ACTION_VIEW, PdmDocAclConstants.EFFECT_ALLOW);

        PdmDocAclVO view = aclService.save(request, managerViewer());

        assertEquals("0", view.getSubjectId());
        assertEquals("FOLDER", view.getResourceType());
    }

    /**
     * USER 主体必须为数字。
     */
    @Test
    @DisplayName("USER 主体必须为数字")
    void saveShouldRejectNonNumericUser() {
        stubFolderManage();
        PdmDocAclSaveRequest request = request(PdmDocAclConstants.RESOURCE_FOLDER,
                PdmDocAclConstants.SUBJECT_USER, "abc",
                PdmDocAclConstants.ACTION_VIEW, PdmDocAclConstants.EFFECT_DENY);

        BizException exception = assertThrows(BizException.class,
                () -> aclService.save(request, managerViewer()));

        assertEquals(ErrorCode.PARAM_ERROR.getCode(), exception.getCode());
        verify(aclMapper, never()).insert(any(PdmDocAcl.class));
    }

    /**
     * 唯一键重复时返回既有规则。
     */
    @Test
    @DisplayName("重复规则幂等返回")
    void saveShouldReturnExistingRule() {
        stubFolderManage();
        PdmDocAcl existing = new PdmDocAcl();
        existing.setId(12L);
        existing.setResourceType(PdmDocAclConstants.RESOURCE_FOLDER);
        existing.setResourceId(1L);
        existing.setSubjectType(PdmDocAclConstants.SUBJECT_USER);
        existing.setSubjectId("7");
        existing.setAction(PdmDocAclConstants.ACTION_VIEW);
        existing.setEffect(PdmDocAclConstants.EFFECT_DENY);
        when(aclMapper.selectList(any())).thenReturn(List.of(existing));
        PdmDocAclSaveRequest request = request(PdmDocAclConstants.RESOURCE_FOLDER,
                PdmDocAclConstants.SUBJECT_USER, "7",
                PdmDocAclConstants.ACTION_VIEW, PdmDocAclConstants.EFFECT_DENY);

        PdmDocAclVO view = aclService.save(request, managerViewer());

        assertEquals(12L, view.getId());
        verify(aclMapper, never()).insert(any(PdmDocAcl.class));
    }

    /**
     * 删除不存在的规则返回 404。
     */
    @Test
    @DisplayName("删除不存在的规则返回 404")
    void deleteShouldRejectMissingRule() {
        when(aclMapper.selectById(5L)).thenReturn(null);

        BizException exception = assertThrows(BizException.class,
                () -> aclService.delete(5L, managerViewer()));

        assertEquals(ErrorCode.NOT_FOUND.getCode(), exception.getCode());
    }

    /**
     * 显式 DENY MANAGE 时拒绝维护权限。
     */
    @Test
    @DisplayName("DENY MANAGE 拒绝维护权限")
    void deleteShouldRejectDeniedManage() {
        PdmDocAcl rule = new PdmDocAcl();
        rule.setId(13L);
        rule.setResourceType(PdmDocAclConstants.RESOURCE_DOCUMENT);
        rule.setResourceId(1L);
        when(aclMapper.selectById(13L)).thenReturn(rule);
        PdmDocDocument document = new PdmDocDocument();
        document.setId(1L);
        document.setFolderId(2L);
        document.setOwnerId(9L);
        when(documentMapper.selectById(1L)).thenReturn(document);
        when(aclResolver.decideDocument(anyLong(), anyLong(), any(PdmDocViewer.class),
                any())).thenReturn(PdmDocAclDecision.DENY);

        BizException exception = assertThrows(BizException.class,
                () -> aclService.delete(13L, new PdmDocViewer(7L, Set.of("pdm_member"))));

        assertEquals(ErrorCode.FORBIDDEN.getCode(), exception.getCode());
        verify(aclMapper, never()).deleteById(anyLong());
    }

    /**
     * 目录资源上的管理能力桩。
     */
    private void stubFolderManage() {
        when(folderService.get(1L)).thenReturn(new PdmDocFolderVO());
        when(aclResolver.decideFolder(anyLong(), any(PdmDocViewer.class), any()))
                .thenReturn(PdmDocAclDecision.ABSTAIN);
    }

    /**
     * 构造管理员访问者。
     *
     * @return 访问者
     */
    private PdmDocViewer managerViewer() {
        return new PdmDocViewer(7L, Set.of("pdm_manager"));
    }

    /**
     * 构造保存请求。
     *
     * @param resourceType 资源类型
     * @param subjectType  主体类型
     * @param subjectId    主体标识
     * @param action       动作
     * @param effect       效果
     * @return 保存请求
     */
    private PdmDocAclSaveRequest request(String resourceType, String subjectType, String subjectId,
                                         String action, String effect) {
        PdmDocAclSaveRequest request = new PdmDocAclSaveRequest();
        request.setResourceType(resourceType);
        request.setResourceId(1L);
        request.setSubjectType(subjectType);
        request.setSubjectId(subjectId);
        request.setAction(action);
        request.setEffect(effect);
        return request;
    }
}
