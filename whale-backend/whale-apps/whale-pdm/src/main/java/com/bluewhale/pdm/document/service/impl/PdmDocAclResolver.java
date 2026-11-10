package com.bluewhale.pdm.document.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bluewhale.pdm.document.entity.PdmDocAcl;
import com.bluewhale.pdm.document.mapper.PdmDocAclMapper;
import com.bluewhale.pdm.document.service.PdmDocFolderService;
import com.bluewhale.pdm.document.support.PdmDocAclConstants;
import com.bluewhale.pdm.document.support.PdmDocAclDecision;
import com.bluewhale.pdm.document.support.PdmDocViewer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 图文档 ACL 有效权限解析
 *
 * <p>文档的有效规则由文档自身规则与所在目录祖先链上的目录规则组成；
 * 任一命中主体的 {@code DENY} 优先于全部 {@code ALLOW}，无命中时返回
 * {@link PdmDocAclDecision#ABSTAIN}，由调用方回落既有属主/管理员规则。</p>
 *
 * @author BlueWhale
 */
@Component
@RequiredArgsConstructor
public class PdmDocAclResolver {

    private final PdmDocAclMapper aclMapper;

    private final PdmDocFolderService folderService;

    /**
     * 解析文档上指定动作的有效权限
     *
     * @param documentId 文档 ID
     * @param folderId   文档所在目录 ID
     * @param viewer     访问者
     * @param action     动作
     * @return 有效权限判定
     */
    public PdmDocAclDecision decideDocument(Long documentId, Long folderId, PdmDocViewer viewer,
                                             String action) {
        List<PdmDocAcl> documentRules = aclMapper.selectList(new LambdaQueryWrapper<PdmDocAcl>()
                .eq(PdmDocAcl::getResourceType, PdmDocAclConstants.RESOURCE_DOCUMENT)
                .eq(PdmDocAcl::getResourceId, documentId)
                .eq(PdmDocAcl::getAction, action));
        List<PdmDocAcl> folderRules = folderId == null
                ? List.of()
                : selectFolderRules(folderService.ancestorFolderIds(folderId), action);
        return evaluate(documentRules, folderRules, viewer);
    }

    /**
     * 解析目录上指定动作的有效权限
     *
     * @param folderId 目录 ID
     * @param viewer   访问者
     * @param action   动作
     * @return 有效权限判定
     */
    public PdmDocAclDecision decideFolder(Long folderId, PdmDocViewer viewer, String action) {
        return evaluate(List.of(), selectFolderRules(folderService.ancestorFolderIds(folderId), action), viewer);
    }

    /**
     * 查询当前用户在文档列表中应排除的目录 ID 集合
     *
     * <p>取命中用户的 {@code VIEW + DENY} 目录规则，并展开为目录子树。</p>
     *
     * @param viewer 访问者
     * @return 应排除的目录 ID；无规则返回空
     */
    public List<Long> deniedFolderIds(PdmDocViewer viewer) {
        List<PdmDocAcl> rules = aclMapper.selectList(new LambdaQueryWrapper<PdmDocAcl>()
                .eq(PdmDocAcl::getResourceType, PdmDocAclConstants.RESOURCE_FOLDER)
                .eq(PdmDocAcl::getAction, PdmDocAclConstants.ACTION_VIEW)
                .eq(PdmDocAcl::getEffect, PdmDocAclConstants.EFFECT_DENY));
        List<Long> roots = rules.stream()
                .filter(rule -> matches(rule, viewer))
                .map(PdmDocAcl::getResourceId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        return folderService.subtreeFolderIds(roots);
    }

    /**
     * 查询当前用户在文档列表中应排除的文档 ID 集合
     *
     * @param viewer 访问者
     * @return 应排除的文档 ID；无规则返回空
     */
    public List<Long> deniedDocumentIds(PdmDocViewer viewer) {
        List<PdmDocAcl> rules = aclMapper.selectList(new LambdaQueryWrapper<PdmDocAcl>()
                .eq(PdmDocAcl::getResourceType, PdmDocAclConstants.RESOURCE_DOCUMENT)
                .eq(PdmDocAcl::getAction, PdmDocAclConstants.ACTION_VIEW)
                .eq(PdmDocAcl::getEffect, PdmDocAclConstants.EFFECT_DENY));
        return rules.stream()
                .filter(rule -> matches(rule, viewer))
                .map(PdmDocAcl::getResourceId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    /**
     * 查询指定目录集合上某动作的规则
     *
     * @param folderIds 目录 ID 集合
     * @param action    动作
     * @return 规则列表
     */
    private List<PdmDocAcl> selectFolderRules(List<Long> folderIds, String action) {
        if (folderIds == null || folderIds.isEmpty()) {
            return List.of();
        }
        return aclMapper.selectList(new LambdaQueryWrapper<PdmDocAcl>()
                .eq(PdmDocAcl::getResourceType, PdmDocAclConstants.RESOURCE_FOLDER)
                .in(PdmDocAcl::getResourceId, folderIds)
                .eq(PdmDocAcl::getAction, action));
    }

    /**
     * 按 DENY 优先规则合并文档级与目录级规则
     *
     * @param documentRules 文档级规则
     * @param folderRules   目录级规则
     * @param viewer        访问者
     * @return 有效权限判定
     */
    private PdmDocAclDecision evaluate(List<PdmDocAcl> documentRules, List<PdmDocAcl> folderRules,
                                       PdmDocViewer viewer) {
        List<PdmDocAcl> matched = new ArrayList<>();
        for (PdmDocAcl rule : documentRules) {
            if (matches(rule, viewer)) {
                matched.add(rule);
            }
        }
        for (PdmDocAcl rule : folderRules) {
            if (matches(rule, viewer)) {
                matched.add(rule);
            }
        }
        if (matched.isEmpty()) {
            return PdmDocAclDecision.ABSTAIN;
        }
        boolean denied = matched.stream()
                .anyMatch(rule -> PdmDocAclConstants.EFFECT_DENY.equals(rule.getEffect()));
        if (denied) {
            return PdmDocAclDecision.DENY;
        }
        boolean allowed = matched.stream()
                .anyMatch(rule -> PdmDocAclConstants.EFFECT_ALLOW.equals(rule.getEffect()));
        return allowed ? PdmDocAclDecision.ALLOW : PdmDocAclDecision.ABSTAIN;
    }

    /**
     * 判断规则是否命中访问者
     *
     * @param rule   规则
     * @param viewer 访问者
     * @return 命中返回 {@code true}
     */
    private boolean matches(PdmDocAcl rule, PdmDocViewer viewer) {
        if (viewer == null) {
            return false;
        }
        String subjectType = rule.getSubjectType();
        if (PdmDocAclConstants.SUBJECT_ALL.equals(subjectType)) {
            return true;
        }
        if (PdmDocAclConstants.SUBJECT_USER.equals(subjectType)) {
            return viewer.userId() != null
                    && Objects.equals(String.valueOf(viewer.userId()), rule.getSubjectId());
        }
        if (PdmDocAclConstants.SUBJECT_ROLE.equals(subjectType)) {
            return viewer.roles() != null && rule.getSubjectId() != null
                    && viewer.roles().contains(rule.getSubjectId());
        }
        return false;
    }
}
