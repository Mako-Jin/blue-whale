package com.bluewhale.doc.application.service.impl;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.tenant.TenantContext;
import com.bluewhale.doc.application.service.DocApprovalService;
import com.bluewhale.doc.application.service.DocArchiveEvent;
import com.bluewhale.doc.application.service.DocCollabService;
import com.bluewhale.doc.application.service.DocPageActivityService;
import com.bluewhale.doc.application.service.DocPageLinkService;
import com.bluewhale.doc.application.service.DocPageRecentService;
import com.bluewhale.doc.application.service.DocPageService;
import com.bluewhale.doc.application.service.DocRecycleService;
import com.bluewhale.doc.application.service.DocVersionService;
import com.bluewhale.doc.application.service.support.DocPageMutationSupport;
import com.bluewhale.doc.config.DocProperties;
import com.bluewhale.doc.domain.exception.DocErrorCode;
import com.bluewhale.doc.domain.model.DocMemberRole;
import com.bluewhale.doc.domain.model.DocPageStatus;
import com.bluewhale.doc.domain.model.DocPageType;
import com.bluewhale.doc.infrastructure.persistence.entity.DocPage;
import com.bluewhale.doc.infrastructure.persistence.entity.DocPageStar;
import com.bluewhale.doc.infrastructure.persistence.entity.DocSpace;
import com.bluewhale.doc.infrastructure.persistence.mapper.DocPageMapper;
import com.bluewhale.doc.infrastructure.persistence.mapper.DocPageStarMapper;
import com.bluewhale.doc.interfaces.dto.DocPageCreateRequest;
import com.bluewhale.doc.interfaces.dto.DocPageMoveRequest;
import com.bluewhale.doc.interfaces.dto.DocPageSaveRequest;
import com.bluewhale.doc.interfaces.vo.DocBreadcrumbVO;
import com.bluewhale.doc.interfaces.vo.DocPageSaveVO;
import com.bluewhale.doc.interfaces.vo.DocPageTreeVO;
import com.bluewhale.doc.interfaces.vo.DocPageVO;
import com.bluewhale.doc.support.DocAccessGuard;
import com.bluewhale.doc.support.DocContentSupport;
import com.bluewhale.doc.support.DocSlugs;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 文档页面与页面树应用服务实现。
 *
 * <p>正文保存采用 {@code baseRevision} 条件更新；slug 在空间内唯一（含逻辑删除行）；
 * 页面移动前校验环与树深度；M2 删除改为「整棵子树移入回收站」，由
 * {@link DocRecycleService} 统一处理恢复与彻底清除。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocPageServiceImpl implements DocPageService {

    /** 根节点父ID */
    private static final long ROOT_PARENT_ID = 0L;

    /** 标题最大长度 */
    private static final int TITLE_MAX_LENGTH = 200;

    /** 发布快照类型 */
    private static final String KIND_PUBLISH = "PUBLISH";

    private final DocPageMapper pageMapper;

    private final DocPageStarMapper starMapper;

    private final DocRecycleService recycleService;

    private final DocCollabService collabService;

    private final DocAccessGuard accessGuard;

    private final DocPageLinkService linkService;

    private final DocPageRecentService recentService;

    private final DocPageActivityService activityService;

    private final DocContentSupport contentSupport;

    private final DocPageMutationSupport mutationSupport;

    private final DocVersionService versionService;

    private final DocProperties docProperties;

    private final DocApprovalService approvalService;

    private final ApplicationEventPublisher eventPublisher;

    private final ObjectMapper objectMapper;

    @Override
    public List<DocPageTreeVO> tree(Long spaceId, Long userId) {
        accessGuard.requireSpaceRead(spaceId, userId);
        DocMemberRole role = accessGuard.roleOf(spaceId, userId);
        List<DocPage> pages = pageMapper.selectList(Wrappers.<DocPage>lambdaQuery()
                .eq(DocPage::getSpaceId, spaceId)
                .eq(role == null, DocPage::getStatus, DocPageStatus.PUBLISHED.name())
                .orderByAsc(DocPage::getSortNo)
                .orderByAsc(DocPage::getId));
        return buildTree(accessGuard.filterReadable(pages, userId));
    }

    @Override
    public DocPageVO detail(Long pageId, Long userId) {
        DocPage page = accessGuard.requirePageRead(pageId, userId);
        approvalService.syncIfPending(page);
        page = accessGuard.requirePage(pageId);
        DocSpace space = accessGuard.requireSpace(page.getSpaceId());
        recentService.recordVisit(pageId, userId);
        boolean starred = isStarred(pageId, userId);
        List<DocBreadcrumbVO> breadcrumb = breadcrumbOf(page);
        return toVO(page, space, userId, starred, breadcrumb, true);
    }

    @Override
    public DocPageVO bySlug(Long spaceId, String slug, Long userId) {
        DocPage page = pageMapper.selectOne(Wrappers.<DocPage>lambdaQuery()
                .eq(DocPage::getSpaceId, spaceId)
                .eq(DocPage::getSlug, slug));
        if (page == null) {
            throw DocErrorCode.exception(DocErrorCode.PAGE_NOT_FOUND);
        }
        return detail(page.getId(), userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DocPageVO create(DocPageCreateRequest request, Long userId) {
        accessGuard.requireMember(request.getSpaceId(), userId, DocMemberRole.EDITOR);
        Long parentId = request.getParentId() == null ? ROOT_PARENT_ID : request.getParentId();
        int parentDepth = resolveParentDepth(request.getSpaceId(), parentId);
        if (parentDepth + 1 > docProperties.getMaxDepth()) {
            throw DocErrorCode.exception(DocErrorCode.TREE_DEPTH_EXCEEDED);
        }
        Long pageId = IdWorker.getId();
        String slug = resolveSlug(request.getSpaceId(), request.getSlug(), pageId);
        String contentJson = stringifyContent(request.getContentJson());
        contentSupport.requireWithinLimit(contentJson);
        DocPage page = new DocPage();
        page.setId(pageId);
        page.setSpaceId(request.getSpaceId());
        page.setParentId(parentId);
        page.setTitle(request.getTitle().trim());
        page.setSlug(slug);
        page.setPageType(resolvePageType(request.getPageType()).name());
        page.setContentJson(contentJson);
        page.setContentText(contentSupport.extractText(contentJson));
        page.setStatus(DocPageStatus.DRAFT.name());
        page.setRevision(0L);
        page.setIcon(request.getIcon());
        page.setSortNo(request.getSortNo() == null
                ? nextSortNo(request.getSpaceId(), parentId) : request.getSortNo());
        page.setAuthorId(userId);
        page.setLastEditorId(userId);
        pageMapper.insert(page);
        linkService.refresh(pageId, contentJson);
        activityService.record(pageId, "CREATE", userId, "创建页面");
        log.info("页面已创建: id={}, spaceId={}, slug={}, author={}", pageId, request.getSpaceId(), slug, userId);
        return detail(pageId, userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DocPageSaveVO save(Long pageId, DocPageSaveRequest request, Long userId) {
        DocPage page = requireEditablePage(pageId, userId);
        assertNotCollaborating(pageId);
        String title = normalizeTitle(request.getTitle());
        String contentJson = resolveSaveContent(page, request.getContentJson());
        if (title == null && contentJson == null) {
            throw new BizException(ErrorCode.PARAM_ERROR, "没有需要保存的内容");
        }
        String contentText = contentJson == null ? null : contentSupport.extractText(contentJson);
        DocPage updated = mutationSupport.apply(page, request.getBaseRevision(), title, contentJson,
                contentText, userId);
        if (contentJson != null) {
            linkService.refresh(updated.getId(), updated.getContentJson());
        }
        versionService.maybeAutoSnapshot(updated, userId);
        activityService.record(pageId, "SAVE", userId, contentJson != null ? "保存正文" : "更新标题");
        log.info("页面已保存: id={}, revision={}, editor={}", pageId, updated.getRevision(), userId);
        return toSaveVO(updated);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DocPageSaveVO updateTitle(Long pageId, DocPageSaveRequest request, Long userId) {
        DocPage page = requireEditablePage(pageId, userId);
        assertNotCollaborating(pageId);
        String title = normalizeTitle(request.getTitle());
        if (title == null) {
            throw new BizException(ErrorCode.PARAM_ERROR, "标题不能为空");
        }
        DocPage updated = mutationSupport.apply(page, request.getBaseRevision(), title, null, null, userId);
        activityService.record(pageId, "TITLE", userId, "更新标题");
        return toSaveVO(updated);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DocPageSaveVO publish(Long pageId, Long userId) {
        DocPage page = requireEditablePage(pageId, userId);
        page.setStatus(DocPageStatus.PUBLISHED.name());
        page.setPublishedRevision(page.getRevision());
        page.setPublishedAt(LocalDateTime.now());
        page.setUpdateTime(LocalDateTime.now());
        pageMapper.updateById(page);
        DocPage updated = pageMapper.selectById(pageId);
        versionService.snapshot(updated, KIND_PUBLISH, null, userId);
        activityService.record(pageId, "PUBLISH", userId, "发布页面 r" + updated.getRevision());
        log.info("页面已发布: id={}, revision={}, operator={}", pageId, updated.getRevision(), userId);
        return toSaveVO(updated);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DocPageSaveVO archive(Long pageId, Long userId) {
        DocPage page = requireEditablePage(pageId, userId);
        page.setStatus(DocPageStatus.ARCHIVED.name());
        page.setArchivedAt(LocalDateTime.now());
        page.setUpdateTime(LocalDateTime.now());
        pageMapper.updateById(page);
        if (docProperties.isArchiveEnabled() && DocPageType.PAGE.name().equals(page.getPageType())) {
            eventPublisher.publishEvent(new DocArchiveEvent(TenantContext.requireId(), pageId, userId));
        }
        activityService.record(pageId, "ARCHIVE", userId, "归档页面");
        log.info("页面已归档: id={}, operator={}", pageId, userId);
        return toSaveVO(pageMapper.selectById(pageId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DocPageSaveVO restore(Long pageId, Long userId) {
        DocPage page = accessGuard.requirePage(pageId);
        accessGuard.requirePageEdit(page, userId);
        LambdaUpdateWrapper<DocPage> wrapper = Wrappers.lambdaUpdate();
        wrapper.eq(DocPage::getId, pageId)
                .set(DocPage::getStatus, DocPageStatus.DRAFT.name())
                .set(DocPage::getArchivedAt, null)
                .set(DocPage::getUpdateTime, LocalDateTime.now());
        pageMapper.update(null, wrapper);
        activityService.record(pageId, "RESTORE", userId, "取消归档");
        log.info("页面已取消归档: id={}, operator={}", pageId, userId);
        return toSaveVO(pageMapper.selectById(pageId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void move(Long pageId, DocPageMoveRequest request, Long userId) {
        DocPage page = requireEditablePage(pageId, userId);
        Long targetParentId = request.getParentId() == null ? ROOT_PARENT_ID : request.getParentId();
        assertNoCycle(pageId, targetParentId);
        int targetDepth = resolveParentDepth(page.getSpaceId(), targetParentId);
        if (targetDepth + subtreeHeight(pageId, page.getSpaceId()) > docProperties.getMaxDepth()) {
            throw DocErrorCode.exception(DocErrorCode.TREE_DEPTH_EXCEEDED);
        }
        LambdaUpdateWrapper<DocPage> wrapper = Wrappers.lambdaUpdate();
        wrapper.eq(DocPage::getId, pageId)
                .set(DocPage::getParentId, targetParentId)
                .set(DocPage::getUpdateTime, LocalDateTime.now())
                .set(request.getSortNo() != null, DocPage::getSortNo, request.getSortNo());
        pageMapper.update(null, wrapper);
        activityService.record(pageId, "MOVE", userId, "移动页面");
        log.info("页面已移动: id={}, parentId={}, operator={}", pageId, targetParentId, userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long pageId, Long userId) {
        recycleService.trash(pageId, userId);
    }

    /**
     * 协作会话活跃时拒绝普通 REST 保存，避免覆盖 Yjs 内存正文。
     *
     * @param pageId 页面ID
     */
    private void assertNotCollaborating(Long pageId) {
        if (collabService.isActive(pageId)) {
            throw DocErrorCode.exception(DocErrorCode.COLLAB_ACTIVE);
        }
    }

    /**
     * 读取页面并校验编辑权限与状态。
     *
     * @param pageId 页面ID
     * @param userId 用户ID
     * @return 页面
     */
    private DocPage requireEditablePage(Long pageId, Long userId) {
        DocPage page = accessGuard.requirePage(pageId);
        accessGuard.requirePageEdit(page, userId);
        if (DocPageStatus.ARCHIVED.name().equals(page.getStatus())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "已归档页面不可编辑");
        }
        if (DocPageStatus.PENDING_APPROVAL.name().equals(page.getStatus())) {
            throw DocErrorCode.exception(DocErrorCode.APPROVAL_STATE_CONFLICT);
        }
        return page;
    }

    /**
     * 解析保存正文：仅 PAGE 接受正文，FOLDER 忽略。
     *
     * @param page 页面
     * @param node 正文 JSON
     * @return 序列化后的正文；未提交返回 {@code null}
     */
    private String resolveSaveContent(DocPage page, JsonNode node) {
        if (node == null || DocPageType.FOLDER.name().equals(page.getPageType())) {
            return null;
        }
        String contentJson = stringifyContent(node);
        contentSupport.requireWithinLimit(contentJson);
        return contentJson;
    }

    /**
     * 序列化正文 JSON。
     *
     * @param node JSON 节点
     * @return JSON 字符串；null 输入返回 {@code null}
     */
    private String stringifyContent(JsonNode node) {
        if (node == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(node);
        } catch (RuntimeException ex) {
            throw new BizException(ErrorCode.PARAM_ERROR, "正文 JSON 无法解析");
        }
    }

    /**
     * 规范化标题。
     *
     * @param title 原始标题
     * @return 标题；null 输入返回 {@code null}
     */
    private String normalizeTitle(String title) {
        if (title == null) {
            return null;
        }
        String trimmed = title.trim();
        if (trimmed.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "标题不能为空");
        }
        if (trimmed.length() > TITLE_MAX_LENGTH) {
            throw new BizException(ErrorCode.PARAM_ERROR, "页面标题不能超过 200 字");
        }
        return trimmed;
    }

    /**
     * 解析节点类型，非法值回落 PAGE。
     *
     * @param pageType 节点类型
     * @return 节点类型
     */
    private DocPageType resolvePageType(DocPageType pageType) {
        return pageType == null ? DocPageType.PAGE : pageType;
    }

    /**
     * 校验并生成页面 slug。
     *
     * @param spaceId 空间ID
     * @param raw     原始 slug
     * @param pageId  页面ID
     * @return 空间内唯一的 slug
     */
    private String resolveSlug(Long spaceId, String raw, Long pageId) {
        String slug = DocSlugs.normalize(raw, pageId);
        if (pageMapper.countSlugIncludeDeleted(TenantContext.requireId(), spaceId, slug, 0L) > 0) {
            throw DocErrorCode.exception(DocErrorCode.SLUG_DUPLICATED);
        }
        return slug;
    }

    /**
     * 计算同级下一个排序值。
     *
     * @param spaceId  空间ID
     * @param parentId 父页面ID
     * @return 排序值
     */
    private Long nextSortNo(Long spaceId, Long parentId) {
        List<DocPage> latest = pageMapper.selectList(Wrappers.<DocPage>lambdaQuery()
                .eq(DocPage::getSpaceId, spaceId)
                .eq(DocPage::getParentId, parentId)
                .orderByDesc(DocPage::getSortNo)
                .last("LIMIT 1"));
        return latest.isEmpty() ? 0L : latest.get(0).getSortNo() + 1;
    }

    /**
     * 校验父页面属于同一空间并计算其层级（根节点为 1）。
     *
     * @param spaceId  空间ID
     * @param parentId 父页面ID，0 表示根
     * @return 父页面层级；根为 0
     */
    private int resolveParentDepth(Long spaceId, Long parentId) {
        if (parentId == null || parentId == ROOT_PARENT_ID) {
            return 0;
        }
        DocPage parent = pageMapper.selectById(parentId);
        if (parent == null || !spaceId.equals(parent.getSpaceId())) {
            throw DocErrorCode.exception(DocErrorCode.PAGE_NOT_FOUND);
        }
        int depth = 1;
        Long cursor = parent.getParentId();
        while (cursor != null && cursor != ROOT_PARENT_ID) {
            depth++;
            if (depth > docProperties.getMaxDepth()) {
                throw DocErrorCode.exception(DocErrorCode.TREE_DEPTH_EXCEEDED);
            }
            DocPage ancestor = pageMapper.selectById(cursor);
            if (ancestor == null) {
                break;
            }
            cursor = ancestor.getParentId();
        }
        return depth;
    }

    /**
     * 校验移动目标不是当前页面自身或其子孙。
     *
     * @param pageId       页面ID
     * @param targetParent 目标父页面ID
     */
    private void assertNoCycle(Long pageId, Long targetParent) {
        Long cursor = targetParent;
        int guard = 0;
        while (cursor != null && cursor != ROOT_PARENT_ID) {
            if (pageId.equals(cursor)) {
                throw DocErrorCode.exception(DocErrorCode.TREE_CYCLE);
            }
            if (guard++ > docProperties.getMaxDepth() + 1) {
                throw DocErrorCode.exception(DocErrorCode.TREE_CYCLE);
            }
            DocPage parent = pageMapper.selectById(cursor);
            if (parent == null) {
                return;
            }
            cursor = parent.getParentId();
        }
    }

    /**
     * 计算页面子树高度（自身为 1）。
     *
     * @param pageId  页面ID
     * @param spaceId 空间ID
     * @return 子树高度
     */
    private int subtreeHeight(Long pageId, Long spaceId) {
        List<DocPage> pages = pageMapper.selectList(Wrappers.<DocPage>lambdaQuery()
                .eq(DocPage::getSpaceId, spaceId));
        Map<Long, List<DocPage>> children = new HashMap<>();
        for (DocPage item : pages) {
            children.computeIfAbsent(item.getParentId(), key -> new ArrayList<>()).add(item);
        }
        return 1 + maxChildHeight(children, pageId, 0);
    }

    /**
     * 递归计算子节点最大高度。
     *
     * @param children 父ID到子页面的映射
     * @param pageId   页面ID
     * @param guard    递归层数保护
     * @return 子节点最大高度
     */
    private int maxChildHeight(Map<Long, List<DocPage>> children, Long pageId, int guard) {
        if (guard > docProperties.getMaxDepth()) {
            throw DocErrorCode.exception(DocErrorCode.TREE_DEPTH_EXCEEDED);
        }
        List<DocPage> nodes = children.getOrDefault(pageId, List.of());
        int max = 0;
        for (DocPage child : nodes) {
            max = Math.max(max, 1 + maxChildHeight(children, child.getId(), guard + 1));
        }
        return max;
    }

    /**
     * 构建页面树，父节点不可见的节点提升为根。
     *
     * @param pages 页面列表
     * @return 树根列表
     */
    private List<DocPageTreeVO> buildTree(List<DocPage> pages) {
        Map<Long, DocPageTreeVO> nodes = new LinkedHashMap<>();
        for (DocPage page : pages) {
            nodes.put(page.getId(), toTreeVO(page));
        }
        List<DocPageTreeVO> roots = new ArrayList<>();
        for (DocPage page : pages) {
            DocPageTreeVO node = nodes.get(page.getId());
            DocPageTreeVO parent = page.getParentId() == null ? null : nodes.get(page.getParentId());
            if (parent == null) {
                roots.add(node);
            } else {
                parent.getChildren().add(node);
            }
        }
        return roots;
    }

    /**
     * 实体转树节点。
     *
     * @param page 页面
     * @return 树节点
     */
    private DocPageTreeVO toTreeVO(DocPage page) {
        DocPageTreeVO vo = new DocPageTreeVO();
        vo.setId(page.getId());
        vo.setParentId(page.getParentId());
        vo.setTitle(page.getTitle());
        vo.setSlug(page.getSlug());
        vo.setPageType(page.getPageType());
        vo.setStatus(page.getStatus());
        vo.setIcon(page.getIcon());
        vo.setSortNo(page.getSortNo());
        vo.setRestricted(accessGuard.isRestricted(page));
        return vo;
    }

    /**
     * 构建面包屑（含当前页）。
     *
     * @param page 当前页面
     * @return 面包屑
     */
    private List<DocBreadcrumbVO> breadcrumbOf(DocPage page) {
        List<DocBreadcrumbVO> breadcrumb = new ArrayList<>();
        Long cursor = page.getId();
        int guard = 0;
        while (cursor != null && cursor != ROOT_PARENT_ID) {
            DocPage node = pageMapper.selectById(cursor);
            if (node == null) {
                break;
            }
            breadcrumb.add(toBreadcrumb(node));
            cursor = node.getParentId();
            if (guard++ > docProperties.getMaxDepth()) {
                break;
            }
        }
        List<DocBreadcrumbVO> ordered = new ArrayList<>();
        for (int i = breadcrumb.size() - 1; i >= 0; i--) {
            ordered.add(breadcrumb.get(i));
        }
        return ordered;
    }

    /**
     * 实体转面包屑节点。
     *
     * @param page 页面
     * @return 面包屑节点
     */
    private DocBreadcrumbVO toBreadcrumb(DocPage page) {
        DocBreadcrumbVO vo = new DocBreadcrumbVO();
        vo.setId(page.getId());
        vo.setTitle(page.getTitle());
        return vo;
    }

    /**
     * 判断当前用户是否已收藏页面。
     *
     * @param pageId 页面ID
     * @param userId 用户ID
     * @return 已收藏返回 {@code true}
     */
    private boolean isStarred(Long pageId, Long userId) {
        return starMapper.selectCount(Wrappers.<DocPageStar>lambdaQuery()
                .eq(DocPageStar::getPageId, pageId)
                .eq(DocPageStar::getUserId, userId)) > 0;
    }

    /**
     * 实体转页面视图。
     *
     * @param page        页面
     * @param space       空间
     * @param userId      当前用户ID
     * @param starred     是否已收藏
     * @param breadcrumb  面包屑
     * @param withContent 是否返回正文
     * @return 页面视图
     */
    private DocPageVO toVO(DocPage page, DocSpace space, Long userId, boolean starred,
                           List<DocBreadcrumbVO> breadcrumb, boolean withContent) {
        DocPageVO vo = new DocPageVO();
        vo.setId(page.getId());
        vo.setSpaceId(page.getSpaceId());
        vo.setSpaceName(space.getName());
        vo.setSpaceVisibility(space.getVisibility());
        vo.setParentId(page.getParentId());
        vo.setTitle(page.getTitle());
        vo.setSlug(page.getSlug());
        vo.setPageType(page.getPageType());
        vo.setContentJson(withContent ? page.getContentJson() : null);
        vo.setStatus(page.getStatus());
        vo.setRevision(page.getRevision());
        vo.setPublishedRevision(page.getPublishedRevision());
        vo.setSortNo(page.getSortNo());
        vo.setIcon(page.getIcon());
        vo.setAuthorId(page.getAuthorId());
        vo.setLastEditorId(page.getLastEditorId());
        vo.setPublishedAt(page.getPublishedAt());
        vo.setArchivedAt(page.getArchivedAt());
        vo.setApprovalStatus(page.getApprovalStatus());
        vo.setApprovalInstanceId(page.getApprovalInstanceId());
        vo.setApprovalSubmittedAt(page.getApprovalSubmittedAt());
        vo.setArchiveItemId(page.getArchiveItemId());
        vo.setArchiveAt(page.getArchiveAt());
        vo.setCreateTime(page.getCreateTime());
        vo.setUpdateTime(page.getUpdateTime());
        vo.setRestricted(accessGuard.isRestricted(page));
        vo.setCanEdit(accessGuard.canEdit(page, userId));
        vo.setCanComment(accessGuard.canComment(page, userId));
        vo.setCanManage(accessGuard.canManage(page, userId));
        vo.setStarred(starred);
        vo.setBreadcrumb(breadcrumb);
        return vo;
    }

    /**
     * 页面转保存结果。
     *
     * @param page 页面
     * @return 保存结果
     */
    private DocPageSaveVO toSaveVO(DocPage page) {
        DocPageSaveVO vo = new DocPageSaveVO();
        vo.setId(page.getId());
        vo.setRevision(page.getRevision());
        vo.setStatus(page.getStatus());
        vo.setSavedAt(page.getUpdateTime());
        return vo;
    }
}
