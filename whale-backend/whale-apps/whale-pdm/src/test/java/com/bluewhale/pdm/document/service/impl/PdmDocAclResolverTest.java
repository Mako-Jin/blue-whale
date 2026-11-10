package com.bluewhale.pdm.document.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.bluewhale.pdm.document.entity.PdmDocAcl;
import com.bluewhale.pdm.document.mapper.PdmDocAclMapper;
import com.bluewhale.pdm.document.service.PdmDocFolderService;
import com.bluewhale.pdm.document.support.PdmDocAclConstants;
import com.bluewhale.pdm.document.support.PdmDocAclDecision;
import com.bluewhale.pdm.document.support.PdmDocViewer;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

/**
 * ACL 解析器单元测试。
 *
 * <p>覆盖目录继承、DENY 优先、三类主体匹配与列表排除集合。</p>
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
class PdmDocAclResolverTest {

    @Mock
    private PdmDocAclMapper aclMapper;

    @Mock
    private PdmDocFolderService folderService;

    @InjectMocks
    private PdmDocAclResolver resolver;

    /**
     * 初始化 MyBatis-Plus 表元数据。
     */
    @BeforeAll
    static void initTableInfo() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                PdmDocAcl.class);
    }

    /**
     * 无规则时返回 ABSTAIN。
     */
    @Test
    @DisplayName("无规则返回 ABSTAIN")
    void decideDocumentShouldAbstainWithoutRules() {
        when(folderService.ancestorFolderIds(1L)).thenReturn(List.of(1L));
        when(aclMapper.selectList(any())).thenReturn(List.of(), List.of());

        assertEquals(PdmDocAclDecision.ABSTAIN,
                resolver.decideDocument(1L, 1L, viewer(7L), PdmDocAclConstants.ACTION_VIEW));
    }

    /**
     * 文档级 DENY 命中当前用户。
     */
    @Test
    @DisplayName("文档级 DENY 命中用户")
    void decideDocumentShouldDenyForUserRule() {
        PdmDocAcl rule = rule(PdmDocAclConstants.RESOURCE_DOCUMENT, 1L,
                PdmDocAclConstants.SUBJECT_USER, "7", PdmDocAclConstants.ACTION_VIEW,
                PdmDocAclConstants.EFFECT_DENY);
        when(folderService.ancestorFolderIds(1L)).thenReturn(List.of(1L));
        when(aclMapper.selectList(any())).thenReturn(List.of(rule), List.of());

        assertEquals(PdmDocAclDecision.DENY,
                resolver.decideDocument(1L, 1L, viewer(7L), PdmDocAclConstants.ACTION_VIEW));
    }

    /**
     * 目录级 DENY 对子目录文档生效。
     */
    @Test
    @DisplayName("目录级 DENY 继承到子目录")
    void decideDocumentShouldInheritFolderDeny() {
        PdmDocAcl rule = rule(PdmDocAclConstants.RESOURCE_FOLDER, 2L,
                PdmDocAclConstants.SUBJECT_ALL, PdmDocAclConstants.ALL_SUBJECT_ID,
                PdmDocAclConstants.ACTION_VIEW, PdmDocAclConstants.EFFECT_DENY);
        when(folderService.ancestorFolderIds(3L)).thenReturn(List.of(1L, 2L, 3L));
        when(aclMapper.selectList(any())).thenReturn(List.of(), List.of(rule));

        assertEquals(PdmDocAclDecision.DENY,
                resolver.decideDocument(5L, 3L, viewer(7L), PdmDocAclConstants.ACTION_VIEW));
    }

    /**
     * 文档 ALLOW 不能突破目录 DENY。
     */
    @Test
    @DisplayName("DENY 优先于 ALLOW")
    void decideDocumentShouldPreferDeny() {
        PdmDocAcl allow = rule(PdmDocAclConstants.RESOURCE_DOCUMENT, 5L,
                PdmDocAclConstants.SUBJECT_USER, "7", PdmDocAclConstants.ACTION_VIEW,
                PdmDocAclConstants.EFFECT_ALLOW);
        PdmDocAcl deny = rule(PdmDocAclConstants.RESOURCE_FOLDER, 2L,
                PdmDocAclConstants.SUBJECT_ALL, PdmDocAclConstants.ALL_SUBJECT_ID,
                PdmDocAclConstants.ACTION_VIEW, PdmDocAclConstants.EFFECT_DENY);
        when(folderService.ancestorFolderIds(3L)).thenReturn(List.of(1L, 2L, 3L));
        when(aclMapper.selectList(any())).thenReturn(List.of(allow), List.of(deny));

        assertEquals(PdmDocAclDecision.DENY,
                resolver.decideDocument(5L, 3L, viewer(7L), PdmDocAclConstants.ACTION_VIEW));
    }

    /**
     * ROLE 规则按角色码匹配，未命中返回 ABSTAIN。
     */
    @Test
    @DisplayName("ROLE 规则按角色码匹配")
    void decideDocumentShouldMatchRoleCode() {
        PdmDocAcl rule = rule(PdmDocAclConstants.RESOURCE_DOCUMENT, 1L,
                PdmDocAclConstants.SUBJECT_ROLE, "pdm_member", PdmDocAclConstants.ACTION_VIEW,
                PdmDocAclConstants.EFFECT_ALLOW);
        when(folderService.ancestorFolderIds(1L)).thenReturn(List.of(1L));
        when(aclMapper.selectList(any())).thenReturn(List.of(rule), List.of());

        assertEquals(PdmDocAclDecision.ALLOW,
                resolver.decideDocument(1L, 1L, viewer(7L), PdmDocAclConstants.ACTION_VIEW));

        PdmDocAcl other = rule(PdmDocAclConstants.RESOURCE_DOCUMENT, 1L,
                PdmDocAclConstants.SUBJECT_ROLE, "pdm_manager", PdmDocAclConstants.ACTION_VIEW,
                PdmDocAclConstants.EFFECT_DENY);
        when(aclMapper.selectList(any())).thenReturn(List.of(other), List.of());

        assertEquals(PdmDocAclDecision.ABSTAIN,
                resolver.decideDocument(1L, 1L,
                        new PdmDocViewer(7L, Set.of("pdm_member")), PdmDocAclConstants.ACTION_VIEW));
    }

    /**
     * DENY 目录规则展开为子树，DENY 文档规则去重。
     */
    @Test
    @DisplayName("列表排除集合展开目录子树")
    void deniedSetsShouldExpandFolders() {
        PdmDocAcl folderRule = rule(PdmDocAclConstants.RESOURCE_FOLDER, 10L,
                PdmDocAclConstants.SUBJECT_ALL, PdmDocAclConstants.ALL_SUBJECT_ID,
                PdmDocAclConstants.ACTION_VIEW, PdmDocAclConstants.EFFECT_DENY);
        when(aclMapper.selectList(any())).thenReturn(List.of(folderRule));
        when(folderService.subtreeFolderIds(anyList())).thenReturn(List.of(10L, 11L));

        assertEquals(List.of(10L, 11L), resolver.deniedFolderIds(viewer(7L)));

        PdmDocAcl documentRule = rule(PdmDocAclConstants.RESOURCE_DOCUMENT, 20L,
                PdmDocAclConstants.SUBJECT_USER, "7", PdmDocAclConstants.ACTION_VIEW,
                PdmDocAclConstants.EFFECT_DENY);
        PdmDocAcl duplicate = rule(PdmDocAclConstants.RESOURCE_DOCUMENT, 20L,
                PdmDocAclConstants.SUBJECT_ALL, PdmDocAclConstants.ALL_SUBJECT_ID,
                PdmDocAclConstants.ACTION_VIEW, PdmDocAclConstants.EFFECT_DENY);
        when(aclMapper.selectList(any())).thenReturn(List.of(documentRule, duplicate));

        assertEquals(List.of(20L), resolver.deniedDocumentIds(viewer(7L)));
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
     * 构造 ACL 规则。
     *
     * @param resourceType 资源类型
     * @param resourceId   资源 ID
     * @param subjectType  主体类型
     * @param subjectId    主体标识
     * @param action       动作
     * @param effect       效果
     * @return 规则
     */
    private PdmDocAcl rule(String resourceType, Long resourceId, String subjectType,
                           String subjectId, String action, String effect) {
        PdmDocAcl rule = new PdmDocAcl();
        rule.setResourceType(resourceType);
        rule.setResourceId(resourceId);
        rule.setSubjectType(subjectType);
        rule.setSubjectId(subjectId);
        rule.setAction(action);
        rule.setEffect(effect);
        return rule;
    }
}
