package com.bluewhale.doc.application.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.tenant.TenantContext;
import com.bluewhale.doc.domain.exception.DocErrorCode;
import com.bluewhale.doc.domain.model.DocMemberRole;
import com.bluewhale.doc.infrastructure.persistence.entity.DocPage;
import com.bluewhale.doc.infrastructure.persistence.entity.DocPageTag;
import com.bluewhale.doc.infrastructure.persistence.entity.DocTag;
import com.bluewhale.doc.infrastructure.persistence.mapper.DocPageMapper;
import com.bluewhale.doc.infrastructure.persistence.mapper.DocPageTagMapper;
import com.bluewhale.doc.infrastructure.persistence.mapper.DocTagMapper;
import com.bluewhale.doc.infrastructure.persistence.query.DocTagRow;
import com.bluewhale.doc.interfaces.dto.DocTagRequest;
import com.bluewhale.doc.interfaces.vo.DocTagVO;
import com.bluewhale.doc.support.DocAccessGuard;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 文档标签应用服务。
 *
 * <p>标签按空间隔离，空间内名称唯一；页面标签为全量替换。删除标签时同时清理关联。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocTagService {

    /** 单页标签上限 */
    private static final int MAX_TAGS_PER_PAGE = 20;

    private final DocTagMapper tagMapper;

    private final DocPageTagMapper pageTagMapper;

    private final DocPageMapper pageMapper;

    private final DocAccessGuard accessGuard;

    private final DocPageActivityService activityService;

    /**
     * 查询空间标签及当前用户可读页面数量。
     *
     * @param spaceId 空间ID
     * @param userId  当前用户ID
     * @return 标签列表
     */
    public List<DocTagVO> list(Long spaceId, Long userId) {
        accessGuard.requireSpaceRead(spaceId, userId);
        return tagMapper.selectWithPageCount(TenantContext.requireId(), userId, spaceId)
                .stream()
                .map(this::toVO)
                .toList();
    }

    /**
     * 创建空间标签。
     *
     * @param spaceId 空间ID
     * @param request 标签内容
     * @param userId  当前用户ID
     * @return 新建标签
     */
    @Transactional(rollbackFor = Exception.class)
    public DocTagVO create(Long spaceId, DocTagRequest request, Long userId) {
        accessGuard.requireMember(spaceId, userId, DocMemberRole.EDITOR);
        String name = normalizeName(request.getName());
        long exists = tagMapper.selectCount(Wrappers.<DocTag>lambdaQuery()
                .eq(DocTag::getSpaceId, spaceId)
                .eq(DocTag::getName, name));
        if (exists > 0) {
            throw new BizException(ErrorCode.PARAM_ERROR, "doc.tag.already-exists", "标签已存在");
        }
        DocTag tag = new DocTag();
        tag.setSpaceId(spaceId);
        tag.setName(name);
        tag.setColor(normalizeColor(request.getColor()));
        try {
            tagMapper.insert(tag);
        } catch (DuplicateKeyException ex) {
            throw new BizException(ErrorCode.PARAM_ERROR, "doc.tag.already-exists", "标签已存在");
        }
        return toVO(tag);
    }

    /**
     * 更新标签名称与颜色。
     *
     * @param tagId   标签ID
     * @param request 标签内容
     * @param userId  当前用户ID
     * @return 更新后的标签
     */
    @Transactional(rollbackFor = Exception.class)
    public DocTagVO update(Long tagId, DocTagRequest request, Long userId) {
        DocTag tag = requireTag(tagId);
        accessGuard.requireMember(tag.getSpaceId(), userId, DocMemberRole.EDITOR);
        String name = normalizeName(request.getName());
        long exists = tagMapper.selectCount(Wrappers.<DocTag>lambdaQuery()
                .eq(DocTag::getSpaceId, tag.getSpaceId())
                .eq(DocTag::getName, name)
                .ne(DocTag::getId, tagId));
        if (exists > 0) {
            throw new BizException(ErrorCode.PARAM_ERROR, "doc.tag.already-exists", "标签已存在");
        }
        tag.setName(name);
        tag.setColor(normalizeColor(request.getColor()));
        tagMapper.updateById(tag);
        return toVO(tag);
    }

    /**
     * 删除标签及其页面关联。
     *
     * @param tagId  标签ID
     * @param userId 当前用户ID
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long tagId, Long userId) {
        DocTag tag = requireTag(tagId);
        accessGuard.requireMember(tag.getSpaceId(), userId, DocMemberRole.EDITOR);
        long tenantId = TenantContext.requireId();
        pageTagMapper.deleteByTag(tenantId, tagId);
        tagMapper.deletePhysical(tenantId, tagId);
        log.info("文档标签已删除: tagId={}, operator={}", tagId, userId);
    }

    /**
     * 查询页面标签。
     *
     * @param pageId 页面ID
     * @param userId 当前用户ID
     * @return 标签列表
     */
    public List<DocTagVO> pageTags(Long pageId, Long userId) {
        DocPage page = accessGuard.requirePage(pageId);
        if (!accessGuard.canRead(page, userId)) {
            throw DocErrorCode.exception(DocErrorCode.PAGE_NOT_FOUND);
        }
        List<DocPageTag> relations = pageTagMapper.selectList(Wrappers.<DocPageTag>lambdaQuery()
                .eq(DocPageTag::getPageId, pageId));
        if (relations.isEmpty()) {
            return List.of();
        }
        List<Long> tagIds = relations.stream().map(DocPageTag::getTagId).toList();
        Map<Long, DocTag> tags = tagMapper.selectBatchIds(tagIds).stream()
                .collect(Collectors.toMap(DocTag::getId, Function.identity()));
        List<DocTagVO> result = new ArrayList<>();
        for (DocPageTag relation : relations) {
            DocTag tag = tags.get(relation.getTagId());
            if (tag != null) {
                result.add(toVO(tag));
            }
        }
        return result;
    }

    /**
     * 全量替换页面标签。
     *
     * @param pageId 页面ID
     * @param tagIds 标签ID列表
     * @param userId 当前用户ID
     * @return 更新后的页面标签
     */
    @Transactional(rollbackFor = Exception.class)
    public List<DocTagVO> updatePageTags(Long pageId, List<Long> tagIds, Long userId) {
        DocPage page = accessGuard.requirePage(pageId);
        accessGuard.requirePageEdit(page, userId);
        LinkedHashSet<Long> distinct = new LinkedHashSet<>();
        if (tagIds != null) {
            tagIds.stream().filter(id -> id != null && id > 0).forEach(distinct::add);
        }
        if (distinct.size() > MAX_TAGS_PER_PAGE) {
            throw new BizException(ErrorCode.PARAM_ERROR, "doc.tag.page-limit-exceeded",
                    "单页标签不能超过 " + MAX_TAGS_PER_PAGE + " 个", MAX_TAGS_PER_PAGE);
        }
        List<DocTag> tags = distinct.isEmpty() ? List.of() : tagMapper.selectBatchIds(distinct);
        if (tags.size() != distinct.size()
                || tags.stream().anyMatch(tag -> !page.getSpaceId().equals(tag.getSpaceId()))) {
            throw new BizException(ErrorCode.PARAM_ERROR, "doc.tag.not-in-space", "标签不存在或不属于当前空间");
        }
        long tenantId = TenantContext.requireId();
        pageTagMapper.deleteByPage(tenantId, pageId);
        for (DocTag tag : tags) {
            DocPageTag relation = new DocPageTag();
            relation.setPageId(pageId);
            relation.setTagId(tag.getId());
            pageTagMapper.insert(relation);
        }
        activityService.record(pageId, "TAG", userId, "更新页面标签 " + tags.size() + " 个");
        return tags.stream().map(this::toVO).toList();
    }

    /**
     * 读取标签。
     *
     * @param tagId 标签ID
     * @return 标签
     */
    private DocTag requireTag(Long tagId) {
        DocTag tag = tagMapper.selectById(tagId);
        if (tag == null) {
            throw new BizException(ErrorCode.PARAM_ERROR, "doc.tag.not-found", "标签不存在");
        }
        return tag;
    }

    /**
     * 规范化标签名称：去首尾空白、压缩内部空白、限长 64。
     *
     * @param raw 原始名称
     * @return 规范名称
     */
    private String normalizeName(String raw) {
        String name = raw == null ? "" : raw.trim().replaceAll("\\s+", " ");
        if (name.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "doc.tag.name-empty", "标签名称不能为空");
        }
        return name.length() <= 64 ? name : name.substring(0, 64);
    }

    /**
     * 规范化颜色。
     *
     * @param raw 原始颜色
     * @return 颜色；空值返回 {@code null}
     */
    private String normalizeColor(String raw) {
        return raw == null || raw.isBlank() ? null : raw.trim();
    }

    /**
     * 实体转标签视图。
     *
     * @param tag 标签
     * @return 标签视图
     */
    private DocTagVO toVO(DocTag tag) {
        DocTagVO vo = new DocTagVO();
        vo.setId(tag.getId());
        vo.setName(tag.getName());
        vo.setColor(tag.getColor());
        return vo;
    }

    /**
     * 行转标签视图（带页面数）。
     *
     * @param row 标签行
     * @return 标签视图
     */
    private DocTagVO toVO(DocTagRow row) {
        DocTagVO vo = new DocTagVO();
        vo.setId(row.getTagId());
        vo.setName(row.getName());
        vo.setColor(row.getColor());
        vo.setPageCount(row.getPageCount());
        return vo;
    }
}
