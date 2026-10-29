package com.bluewhale.doc.application.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.doc.application.service.DocPageService;
import com.bluewhale.doc.application.service.DocTemplateService;
import com.bluewhale.doc.domain.exception.DocErrorCode;
import com.bluewhale.doc.domain.model.DocMemberRole;
import com.bluewhale.doc.domain.model.DocPageType;
import com.bluewhale.doc.infrastructure.persistence.entity.DocTemplate;
import com.bluewhale.doc.infrastructure.persistence.mapper.DocTemplateMapper;
import com.bluewhale.doc.interfaces.dto.DocPageCreateRequest;
import com.bluewhale.doc.interfaces.dto.DocTemplateApplyRequest;
import com.bluewhale.doc.interfaces.dto.DocTemplateSaveRequest;
import com.bluewhale.doc.interfaces.vo.DocPageVO;
import com.bluewhale.doc.interfaces.vo.DocTemplateVO;
import com.bluewhale.doc.support.DocAccessGuard;
import com.bluewhale.doc.support.DocContentSupport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

/**
 * 文档模板应用服务实现。
 *
 * <p>模板正文与页面同构；从模板建页复用 {@link DocPageService#create}，
 * 由页面创建流程统一做体积、层级与 slug 校验。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocTemplateServiceImpl implements DocTemplateService {

    /** 租户级模板的 spaceId */
    private static final long TENANT_SCOPE = 0L;

    /** 启用状态 */
    private static final int STATUS_ENABLED = 1;

    private final DocTemplateMapper templateMapper;

    private final DocPageService pageService;

    private final DocAccessGuard accessGuard;

    private final DocContentSupport contentSupport;

    private final ObjectMapper objectMapper;

    @Override
    public List<DocTemplateVO> list(Long spaceId, Long userId) {
        if (spaceId != null && spaceId > TENANT_SCOPE) {
            accessGuard.requireSpaceRead(spaceId, userId);
        }
        boolean scoped = spaceId != null && spaceId > TENANT_SCOPE;
        List<DocTemplate> templates = templateMapper.selectList(Wrappers.<DocTemplate>lambdaQuery()
                .eq(DocTemplate::getStatus, STATUS_ENABLED)
                .and(scoped, wrapper -> wrapper.eq(DocTemplate::getSpaceId, TENANT_SCOPE)
                        .or().eq(DocTemplate::getSpaceId, spaceId))
                .eq(!scoped, DocTemplate::getSpaceId, TENANT_SCOPE)
                .orderByAsc(DocTemplate::getSortNo)
                .orderByDesc(DocTemplate::getId));
        return templates.stream().map(this::toVO).toList();
    }

    @Override
    public DocTemplateVO detail(Long templateId, Long userId) {
        return toVO(requireTemplate(templateId, userId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DocTemplateVO create(DocTemplateSaveRequest request, Long userId) {
        Long spaceId = request.getSpaceId() == null ? TENANT_SCOPE : request.getSpaceId();
        requireManage(spaceId, userId);
        String contentJson = stringify(request);
        DocTemplate template = new DocTemplate();
        applyFields(template, request, contentJson);
        template.setSpaceId(spaceId);
        template.setAuthorId(userId);
        template.setStatus(request.getStatus() == null ? STATUS_ENABLED : request.getStatus());
        template.setSortNo(request.getSortNo() == null ? 0 : request.getSortNo());
        templateMapper.insert(template);
        log.info("模板已创建: id={}, spaceId={}, operator={}", template.getId(), spaceId, userId);
        return toVO(template);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DocTemplateVO update(Long templateId, DocTemplateSaveRequest request, Long userId) {
        DocTemplate existing = requireTemplate(templateId, userId);
        requireManage(existing.getSpaceId(), userId);
        String contentJson = stringify(request);
        applyFields(existing, request, contentJson);
        if (request.getStatus() != null) {
            existing.setStatus(request.getStatus());
        }
        if (request.getSortNo() != null) {
            existing.setSortNo(request.getSortNo());
        }
        templateMapper.updateById(existing);
        log.info("模板已更新: id={}, operator={}", templateId, userId);
        return toVO(templateMapper.selectById(templateId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long templateId, Long userId) {
        DocTemplate existing = requireTemplate(templateId, userId);
        requireManage(existing.getSpaceId(), userId);
        templateMapper.deleteById(templateId);
        log.info("模板已删除: id={}, operator={}", templateId, userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DocPageVO apply(Long templateId, DocTemplateApplyRequest request, Long userId) {
        DocTemplate template = requireTemplate(templateId, userId);
        accessGuard.requireMember(request.getSpaceId(), userId, DocMemberRole.EDITOR);
        DocPageCreateRequest create = new DocPageCreateRequest();
        create.setSpaceId(request.getSpaceId());
        create.setParentId(request.getParentId() == null ? 0L : request.getParentId());
        create.setTitle(request.getTitle() == null || request.getTitle().isBlank()
                ? template.getName() : request.getTitle());
        create.setSlug(request.getSlug());
        create.setIcon(template.getIcon());
        create.setPageType(parseType(template.getPageType()));
        create.setContentJson(parseContent(template.getContentJson()));
        return pageService.create(create, userId);
    }

    /**
     * 读取模板并校验可读性。
     *
     * @param templateId 模板ID
     * @param userId     当前用户ID
     * @return 模板
     */
    private DocTemplate requireTemplate(Long templateId, Long userId) {
        DocTemplate template = templateMapper.selectById(templateId);
        if (template == null) {
            throw DocErrorCode.exception(DocErrorCode.TEMPLATE_NOT_FOUND);
        }
        if (template.getSpaceId() != null && template.getSpaceId() > TENANT_SCOPE) {
            accessGuard.requireSpaceRead(template.getSpaceId(), userId);
        }
        return template;
    }

    /**
     * 校验模板管理权限。
     *
     * @param spaceId 模板归属空间，0 表示租户级
     * @param userId  当前用户ID
     */
    private void requireManage(Long spaceId, Long userId) {
        if (spaceId != null && spaceId > TENANT_SCOPE) {
            accessGuard.requireMember(spaceId, userId, DocMemberRole.EDITOR);
        }
    }

    /**
     * 应用保存请求到实体。
     *
     * @param template    模板实体
     * @param request     保存请求
     * @param contentJson 正文 JSON
     */
    private void applyFields(DocTemplate template, DocTemplateSaveRequest request, String contentJson) {
        template.setName(request.getName().trim());
        template.setDescription(request.getDescription());
        template.setPageType(parseType(request.getPageType()).name());
        template.setIcon(request.getIcon());
        template.setContentJson(contentJson);
        template.setContentText(contentSupport.extractText(contentJson));
    }

    /**
     * 序列化模板正文并校验体积。
     *
     * @param request 保存请求
     * @return 正文 JSON；未提交返回 {@code null}
     */
    private String stringify(DocTemplateSaveRequest request) {
        if (request.getContentJson() == null) {
            return null;
        }
        try {
            String json = objectMapper.writeValueAsString(request.getContentJson());
            contentSupport.requireWithinLimit(json);
            return json;
        } catch (BizException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new BizException(ErrorCode.PARAM_ERROR, "模板正文 JSON 无法解析");
        }
    }

    /**
     * 解析节点类型，非法值回落 PAGE。
     *
     * @param value 节点类型文本
     * @return 节点类型
     */
    private DocPageType parseType(String value) {
        try {
            return DocPageType.valueOf(value == null ? DocPageType.PAGE.name() : value);
        } catch (RuntimeException ex) {
            return DocPageType.PAGE;
        }
    }

    /**
     * 解析模板正文 JSON 节点。
     *
     * @param contentJson 正文 JSON 文本
     * @return JSON 节点；空值返回 {@code null}
     */
    private tools.jackson.databind.JsonNode parseContent(String contentJson) {
        if (contentJson == null || contentJson.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(contentJson);
        } catch (RuntimeException ex) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "模板正文数据损坏");
        }
    }

    /**
     * 模板实体转视图。
     *
     * @param template 模板
     * @return 模板视图
     */
    private DocTemplateVO toVO(DocTemplate template) {
        DocTemplateVO vo = new DocTemplateVO();
        vo.setId(template.getId());
        vo.setSpaceId(template.getSpaceId());
        vo.setName(template.getName());
        vo.setDescription(template.getDescription());
        vo.setPageType(template.getPageType());
        vo.setIcon(template.getIcon());
        vo.setContentJson(template.getContentJson());
        vo.setStatus(template.getStatus());
        vo.setSortNo(template.getSortNo());
        vo.setAuthorId(template.getAuthorId());
        vo.setCreateTime(template.getCreateTime());
        vo.setUpdateTime(template.getUpdateTime());
        return vo;
    }
}
