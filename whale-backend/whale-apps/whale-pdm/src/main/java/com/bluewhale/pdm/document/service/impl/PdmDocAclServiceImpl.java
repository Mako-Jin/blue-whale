package com.bluewhale.pdm.document.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.pdm.converter.PdmConverter;
import com.bluewhale.pdm.document.dto.PdmDocAclSaveRequest;
import com.bluewhale.pdm.document.entity.PdmDocAcl;
import com.bluewhale.pdm.document.entity.PdmDocDocument;
import com.bluewhale.pdm.document.mapper.PdmDocAclMapper;
import com.bluewhale.pdm.document.mapper.PdmDocDocumentMapper;
import com.bluewhale.pdm.document.service.PdmDocAclService;
import com.bluewhale.pdm.document.service.PdmDocFolderService;
import com.bluewhale.pdm.document.support.PdmDocAclConstants;
import com.bluewhale.pdm.document.support.PdmDocAclDecision;
import com.bluewhale.pdm.document.support.PdmDocViewer;
import com.bluewhale.pdm.document.vo.PdmDocAclVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 图文档 ACL 规则服务实现
 *
 * <p>资源必须存在；主体标识按类型归一：{@code ALL → "0"}、
 * {@code USER} 为用户 ID 文本、{@code ROLE} 为角色 code。
 * 唯一键重复时直接返回既有规则，保证 PUT 幂等。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PdmDocAclServiceImpl implements PdmDocAclService {

    private static final long MAX_SUBJECT_ID_LENGTH = 64L;

    private final PdmDocAclMapper aclMapper;

    private final PdmDocDocumentMapper documentMapper;

    private final PdmDocFolderService folderService;

    private final PdmDocAclResolver aclResolver;

    @Override
    public List<PdmDocAclVO> list(String resourceType, Long resourceId, PdmDocViewer viewer) {
        String type = normalize(resourceType);
        assertValidResourceType(type);
        assertResourceExists(type, resourceId);
        assertManage(type, resourceId, viewer);
        return aclMapper.selectList(new LambdaQueryWrapper<PdmDocAcl>()
                        .eq(PdmDocAcl::getResourceType, type)
                        .eq(PdmDocAcl::getResourceId, resourceId)
                        .orderByAsc(PdmDocAcl::getSubjectType)
                        .orderByAsc(PdmDocAcl::getAction)
                        .orderByAsc(PdmDocAcl::getId))
                .stream()
                .map(PdmConverter.INSTANCE::toAclVo)
                .toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PdmDocAclVO save(PdmDocAclSaveRequest request, PdmDocViewer viewer) {
        String resourceType = normalize(request.getResourceType());
        assertValidResourceType(resourceType);
        assertResourceExists(resourceType, request.getResourceId());
        assertManage(resourceType, request.getResourceId(), viewer);
        String subjectType = normalize(request.getSubjectType());
        assertValidSubjectType(subjectType);
        String action = normalize(request.getAction());
        assertValidAction(action);
        String effect = normalize(request.getEffect());
        assertValidEffect(effect);
        String subjectId = normalizeSubjectId(subjectType, request.getSubjectId());

        PdmDocAcl existing = findRule(resourceType, request.getResourceId(), subjectType, subjectId,
                action, effect);
        if (existing != null) {
            return PdmConverter.INSTANCE.toAclVo(existing);
        }
        PdmDocAcl rule = new PdmDocAcl();
        rule.setResourceType(resourceType);
        rule.setResourceId(request.getResourceId());
        rule.setSubjectType(subjectType);
        rule.setSubjectId(subjectId);
        rule.setAction(action);
        rule.setEffect(effect);
        try {
            aclMapper.insert(rule);
        } catch (DuplicateKeyException ex) {
            log.info("PDM 图文档 ACL 规则已存在，按幂等处理: resource={}/{}, subject={}/{}, action={}, effect={}",
                    resourceType, request.getResourceId(), subjectType, subjectId, action, effect);
            PdmDocAcl duplicated = findRule(resourceType, request.getResourceId(), subjectType, subjectId,
                    action, effect);
            if (duplicated == null) {
                throw ex;
            }
            return PdmConverter.INSTANCE.toAclVo(duplicated);
        }
        return PdmConverter.INSTANCE.toAclVo(rule);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id, PdmDocViewer viewer) {
        if (id == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "biz.pdm.document.c-l-rule-found", "ACL 规则不存在");
        }
        PdmDocAcl rule = aclMapper.selectById(id);
        if (rule == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "biz.pdm.document.c-l-rule-found-2", "ACL 规则不存在");
        }
        assertManage(rule.getResourceType(), rule.getResourceId(), viewer);
        aclMapper.deleteById(id);
    }

    /**
     * 校验访问者拥有资源的 MANAGE 能力
     *
     * @param resourceType 资源类型
     * @param resourceId   资源 ID
     * @param viewer       访问者
     */
    private void assertManage(String resourceType, Long resourceId, PdmDocViewer viewer) {
        if (viewer == null || viewer.userId() == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "biz.pdm.document.identity-missing-access-via-gateway", "缺少用户身份，请通过网关访问");
        }
        PdmDocAclDecision decision;
        PdmDocDocument document = null;
        if (PdmDocAclConstants.RESOURCE_DOCUMENT.equals(resourceType)) {
            document = documentMapper.selectById(resourceId);
            if (document == null) {
                throw new BizException(ErrorCode.NOT_FOUND, "biz.pdm.document.document-found", "文档不存在");
            }
            decision = aclResolver.decideDocument(document.getId(), document.getFolderId(), viewer,
                    PdmDocAclConstants.ACTION_MANAGE);
        } else {
            decision = aclResolver.decideFolder(resourceId, viewer, PdmDocAclConstants.ACTION_MANAGE);
        }
        if (decision == PdmDocAclDecision.DENY) {
            throw new BizException(ErrorCode.FORBIDDEN, "biz.pdm.document.permission-manage-resource", "无权管理该资源的权限");
        }
        if (decision == PdmDocAclDecision.ALLOW) {
            return;
        }
        if (viewer.canManage()) {
            return;
        }
        if (document != null && document.getOwnerId() != null && document.getOwnerId().equals(viewer.userId())) {
            return;
        }
        throw new BizException(ErrorCode.FORBIDDEN, "biz.pdm.document.permission-manage-resource-2", "无权管理该资源的权限");
    }

    /**
     * 校验资源存在
     *
     * @param resourceType 资源类型
     * @param resourceId   资源 ID
     */
    private void assertResourceExists(String resourceType, Long resourceId) {
        if (resourceId == null) {
            throw new BizException(ErrorCode.PARAM_ERROR, "biz.pdm.document.resource-id-empty", "资源ID不能为空");
        }
        if (PdmDocAclConstants.RESOURCE_FOLDER.equals(resourceType)) {
            folderService.get(resourceId);
            return;
        }
        if (documentMapper.selectById(resourceId) == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "biz.pdm.document.document-found-2", "文档不存在");
        }
    }

    /**
     * 查询唯一键对应的规则
     *
     * @param resourceType 资源类型
     * @param resourceId   资源 ID
     * @param subjectType  主体类型
     * @param subjectId    主体标识
     * @param action       动作
     * @param effect       效果
     * @return 规则，不存在返回 {@code null}
     */
    private PdmDocAcl findRule(String resourceType, Long resourceId, String subjectType, String subjectId,
                               String action, String effect) {
        List<PdmDocAcl> rules = aclMapper.selectList(new LambdaQueryWrapper<PdmDocAcl>()
                .eq(PdmDocAcl::getResourceType, resourceType)
                .eq(PdmDocAcl::getResourceId, resourceId)
                .eq(PdmDocAcl::getSubjectType, subjectType)
                .eq(PdmDocAcl::getSubjectId, subjectId)
                .eq(PdmDocAcl::getAction, action)
                .eq(PdmDocAcl::getEffect, effect)
                .last("LIMIT 1"));
        return rules.isEmpty() ? null : rules.get(0);
    }

    /**
     * 校验资源类型合法
     *
     * @param resourceType 资源类型
     */
    private void assertValidResourceType(String resourceType) {
        if (!PdmDocAclConstants.isValidResourceType(resourceType)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "biz.pdm.document.resource-type-invalid", "资源类型不合法");
        }
    }

    /**
     * 校验主体类型合法
     *
     * @param subjectType 主体类型
     */
    private void assertValidSubjectType(String subjectType) {
        if (!PdmDocAclConstants.isValidSubjectType(subjectType)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "biz.pdm.document.main-body-type-invalid", "主体类型不合法");
        }
    }

    /**
     * 校验动作合法
     *
     * @param action 动作
     */
    private void assertValidAction(String action) {
        if (!PdmDocAclConstants.isValidAction(action)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "biz.pdm.document.action-invalid", "动作不合法");
        }
    }

    /**
     * 校验效果合法
     *
     * @param effect 效果
     */
    private void assertValidEffect(String effect) {
        if (!PdmDocAclConstants.isValidEffect(effect)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "biz.pdm.document.effect-invalid", "效果不合法");
        }
    }

    /**
     * 按主体类型归一主体标识
     *
     * @param subjectType 主体类型
     * @param subjectId   原始主体标识
     * @return 归一后的主体标识
     */
    private String normalizeSubjectId(String subjectType, String subjectId) {
        if (PdmDocAclConstants.SUBJECT_ALL.equals(subjectType)) {
            return PdmDocAclConstants.ALL_SUBJECT_ID;
        }
        if (!StringUtils.hasText(subjectId)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "biz.pdm.document.subject-identifier-empty", "主体标识不能为空");
        }
        String normalized = subjectId.trim();
        if (normalized.length() > MAX_SUBJECT_ID_LENGTH) {
            throw new BizException(ErrorCode.PARAM_ERROR, "biz.pdm.document.subject-identifier-exceed-64-characters", "主体标识不能超过 64 个字符");
        }
        if (PdmDocAclConstants.SUBJECT_USER.equals(subjectType)) {
            try {
                long userId = Long.parseLong(normalized);
                if (userId <= 0) {
                    throw new BizException(ErrorCode.PARAM_ERROR, "biz.pdm.document.id-invalid", "用户ID不合法");
                }
                return String.valueOf(userId);
            } catch (NumberFormatException ex) {
                throw new BizException(ErrorCode.PARAM_ERROR, "biz.pdm.document.id-numeric", "用户ID必须为数字");
            }
        }
        return normalized;
    }

    /**
     * 归一化枚举取值
     *
     * @param value 原始值
     * @return 去空白并转大写后的值
     */
    private String normalize(String value) {
        return value == null ? null : value.trim().toUpperCase();
    }
}
