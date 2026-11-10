package com.bluewhale.doc.application.service.impl;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.tenant.TenantContext;
import com.bluewhale.doc.application.service.DocPageActivityService;
import com.bluewhale.doc.application.service.DocRecycleService;
import com.bluewhale.doc.domain.exception.DocErrorCode;
import com.bluewhale.doc.domain.model.DocMemberRole;
import com.bluewhale.doc.infrastructure.persistence.entity.DocPage;
import com.bluewhale.doc.infrastructure.persistence.mapper.DocCommentMapper;
import com.bluewhale.doc.infrastructure.persistence.mapper.DocCommentReadMapper;
import com.bluewhale.doc.infrastructure.persistence.mapper.DocPageAclMapper;
import com.bluewhale.doc.infrastructure.persistence.mapper.DocPageActivityMapper;
import com.bluewhale.doc.infrastructure.persistence.mapper.DocPageAttachmentMapper;
import com.bluewhale.doc.infrastructure.persistence.mapper.DocPageLinkMapper;
import com.bluewhale.doc.infrastructure.persistence.mapper.DocPageMapper;
import com.bluewhale.doc.infrastructure.persistence.mapper.DocPageRecentMapper;
import com.bluewhale.doc.infrastructure.persistence.mapper.DocPageStarMapper;
import com.bluewhale.doc.infrastructure.persistence.mapper.DocPageTagMapper;
import com.bluewhale.doc.infrastructure.persistence.mapper.DocPageVersionMapper;
import com.bluewhale.doc.infrastructure.persistence.query.DocTrashPageRow;
import com.bluewhale.doc.infrastructure.persistence.query.DocTrashRootRow;
import com.bluewhale.doc.interfaces.vo.DocRecycleItemVO;
import com.bluewhale.doc.support.DocAccessGuard;
import com.bluewhale.doc.support.DocSlugs;
import com.bluewhale.doc.config.DocProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 文档回收站应用服务实现（M2）。
 *
 * <p>删除时将子树每个节点的 {@code deleted=1} 并记录 {@code deleted_at/deleted_by/trash_root_id}，
 * slug 改写为墓碑形态；评论、附件与收藏保留，恢复后原样可见。彻底清除才物理删除关联数据。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocRecycleServiceImpl implements DocRecycleService {

    /** 回收站根节点父ID */
    private static final long ROOT_PARENT_ID = 0L;

    /** 分页最大条数 */
    private static final int MAX_PAGE_SIZE = 100;

    /** 清空回收站单轮根节点数量 */
    private static final int CLEAR_BATCH_SIZE = 200;

    /** 清空回收站最大轮次，防止异常数据导致死循环 */
    private static final int MAX_CLEAR_ROUNDS = 50;

    private final DocPageMapper pageMapper;

    private final DocPageVersionMapper versionMapper;

    private final DocCommentMapper commentMapper;

    private final DocPageAttachmentMapper attachmentMapper;

    private final DocPageStarMapper starMapper;

    private final DocPageAclMapper aclMapper;

    private final DocPageTagMapper tagMapper;

    private final DocPageLinkMapper linkMapper;

    private final DocPageRecentMapper recentMapper;

    private final DocCommentReadMapper commentReadMapper;

    private final DocPageActivityMapper activityMapper;

    private final DocPageActivityService activityService;

    private final DocAccessGuard accessGuard;

    private final DocProperties docProperties;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void trash(Long pageId, Long userId) {
        Long tenantId = TenantContext.requireId();
        DocPage page = accessGuard.requirePage(pageId);
        accessGuard.requireMember(page.getSpaceId(), userId, DocMemberRole.ADMIN);
        List<DocTrashPageRow> subtree = pageMapper.selectSubtree(tenantId, pageId);
        if (subtree.isEmpty()) {
            throw DocErrorCode.exception(DocErrorCode.PAGE_NOT_FOUND);
        }
        LocalDateTime now = LocalDateTime.now();
        for (DocTrashPageRow row : subtree) {
            pageMapper.trashPage(tenantId, row.getPageId(),
                    DocSlugs.trashSlug(row.getSlug(), row.getPageId()), userId, now, pageId);
        }
        activityService.record(pageId, "TRASH", userId, "移入回收站 " + subtree.size() + " 个页面");
        log.info("页面子树已移入回收站: root={}, pages={}, operator={}", pageId, subtree.size(), userId);
    }

    @Override
    public PageResult<DocRecycleItemVO> page(Long spaceId, long current, long size, Long userId) {
        Long tenantId = TenantContext.requireId();
        accessGuard.requireMember(spaceId, userId, DocMemberRole.ADMIN);
        long pageNo = Math.max(current, 1L);
        int pageSize = (int) Math.min(Math.max(size, 1L), MAX_PAGE_SIZE);
        long total = pageMapper.countTrashRoots(tenantId, spaceId);
        List<DocTrashRootRow> rows = pageMapper.selectTrashRoots(tenantId, spaceId,
                (pageNo - 1) * pageSize, pageSize);
        List<DocRecycleItemVO> records = new ArrayList<>(rows.size());
        for (DocTrashRootRow row : rows) {
            records.add(toVO(row));
        }
        return PageResult.of(records, total, pageNo, pageSize);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void restore(Long pageId, Long userId) {
        Long tenantId = TenantContext.requireId();
        DocPage root = requireTrashRoot(tenantId, pageId);
        accessGuard.requireMember(root.getSpaceId(), userId, DocMemberRole.ADMIN);
        List<DocTrashPageRow> subtree = pageMapper.selectTrashSubtree(tenantId, pageId);
        if (subtree.isEmpty()) {
            throw DocErrorCode.exception(DocErrorCode.PAGE_NOT_FOUND);
        }
        assertParentRestorable(tenantId, root, subtree);
        LocalDateTime now = LocalDateTime.now();
        for (DocTrashPageRow row : subtree) {
            pageMapper.restorePage(tenantId, row.getPageId(),
                    resolveRestoreSlug(tenantId, row), now);
        }
        activityService.record(pageId, "UNTRASH", userId, "从回收站恢复 " + subtree.size() + " 个页面");
        log.info("回收站页面已恢复: root={}, pages={}, operator={}", pageId, subtree.size(), userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void purge(Long pageId, Long userId) {
        Long tenantId = TenantContext.requireId();
        DocPage root = requireTrashRoot(tenantId, pageId);
        accessGuard.requireMember(root.getSpaceId(), userId, DocMemberRole.ADMIN);
        purgeRoot(tenantId, pageId);
        log.info("回收站页面已彻底清除: root={}, operator={}", pageId, userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void purgeSpace(Long spaceId, Long userId) {
        Long tenantId = TenantContext.requireId();
        accessGuard.requireMember(spaceId, userId, DocMemberRole.ADMIN);
        for (int round = 0; round < MAX_CLEAR_ROUNDS; round++) {
            List<DocTrashRootRow> roots = pageMapper.selectTrashRoots(tenantId, spaceId, 0, CLEAR_BATCH_SIZE);
            if (roots.isEmpty()) {
                break;
            }
            for (DocTrashRootRow root : roots) {
                purgeRoot(tenantId, root.getPageId());
            }
        }
        log.info("空间回收站已清空: spaceId={}, operator={}", spaceId, userId);
    }

    @Override
    public List<DocTrashRootRow> expiredRoots(LocalDateTime cutoff, int limit) {
        return pageMapper.selectExpiredTrashRoots(cutoff, limit);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void purgeExpired(Long tenantId, Long rootId) {
        purgeRoot(tenantId, rootId);
        log.info("回收站到期自动清理: tenant={}, root={}", tenantId, rootId);
    }

    /**
     * 读取并校验回收站根节点。
     *
     * @param tenantId 租户ID
     * @param pageId   页面ID
     * @return 回收站根页面
     */
    private DocPage requireTrashRoot(Long tenantId, Long pageId) {
        DocPage page = pageMapper.selectByIdIncludeDeleted(tenantId, pageId);
        if (page == null || page.getDeleted() == null || page.getDeleted() != 1) {
            throw DocErrorCode.exception(DocErrorCode.PAGE_NOT_FOUND);
        }
        if (page.getTrashRootId() == null || !page.getTrashRootId().equals(pageId)) {
            throw DocErrorCode.exception(DocErrorCode.RECYCLE_NOT_ROOT);
        }
        return page;
    }

    /**
     * 校验原父页面仍可用，并校验恢复后层级不超过上限。
     *
     * @param tenantId 租户ID
     * @param root     回收站根页面
     * @param subtree  回收站子树
     */
    private void assertParentRestorable(Long tenantId, DocPage root, List<DocTrashPageRow> subtree) {
        Long parentId = root.getParentId();
        int parentDepth = 0;
        if (parentId != null && parentId != ROOT_PARENT_ID) {
            DocPage parent = pageMapper.selectByIdIncludeDeleted(tenantId, parentId);
            if (parent == null || parent.getDeleted() == null || parent.getDeleted() != 0
                    || !root.getSpaceId().equals(parent.getSpaceId())) {
                throw DocErrorCode.exception(DocErrorCode.RECYCLE_PARENT_INVALID);
            }
            parentDepth = liveDepth(tenantId, parent);
        }
        if (parentDepth + subtreeHeight(subtree, root.getId()) > docProperties.getMaxDepth()) {
            throw DocErrorCode.exception(DocErrorCode.TREE_DEPTH_EXCEEDED);
        }
    }

    /**
     * 计算存活父页面的层级（根为 0）。
     *
     * @param tenantId 租户ID
     * @param parent   父页面
     * @return 父页面层级
     */
    private int liveDepth(Long tenantId, DocPage parent) {
        int depth = 1;
        Long cursor = parent.getParentId();
        while (cursor != null && cursor != ROOT_PARENT_ID) {
            depth++;
            if (depth > docProperties.getMaxDepth()) {
                throw DocErrorCode.exception(DocErrorCode.TREE_DEPTH_EXCEEDED);
            }
            DocPage ancestor = pageMapper.selectByIdIncludeDeleted(tenantId, cursor);
            if (ancestor == null || ancestor.getDeleted() == null || ancestor.getDeleted() != 0) {
                throw DocErrorCode.exception(DocErrorCode.RECYCLE_PARENT_INVALID);
            }
            cursor = ancestor.getParentId();
        }
        return depth;
    }

    /**
     * 计算回收站子树高度（自身为 1）。
     *
     * @param subtree 子树页面
     * @param rootId  根页面ID
     * @return 子树高度
     */
    private int subtreeHeight(List<DocTrashPageRow> subtree, Long rootId) {
        Map<Long, List<DocTrashPageRow>> children = new HashMap<>();
        for (DocTrashPageRow row : subtree) {
            children.computeIfAbsent(row.getParentId(), key -> new ArrayList<>()).add(row);
        }
        return 1 + maxChildHeight(children, rootId, 0);
    }

    /**
     * 递归计算回收站子树最大高度。
     *
     * @param children 父ID到子节点的映射
     * @param pageId   当前页面ID
     * @param guard    递归层数保护
     * @return 子节点最大高度
     */
    private int maxChildHeight(Map<Long, List<DocTrashPageRow>> children, Long pageId, int guard) {
        if (guard > docProperties.getMaxDepth()) {
            throw DocErrorCode.exception(DocErrorCode.TREE_DEPTH_EXCEEDED);
        }
        int max = 0;
        for (DocTrashPageRow child : children.getOrDefault(pageId, List.of())) {
            max = Math.max(max, 1 + maxChildHeight(children, child.getPageId(), guard + 1));
        }
        return max;
    }

    /**
     * 计算恢复后可用 slug：优先还原原 slug，被占用时回退为 {@code p-{id}}。
     *
     * @param tenantId 租户ID
     * @param row      回收站页面
     * @return 可用 slug
     */
    private String resolveRestoreSlug(Long tenantId, DocTrashPageRow row) {
        String base = DocSlugs.normalize(DocSlugs.restoreBase(row.getSlug(), row.getPageId()), row.getPageId());
        if (slugAvailable(tenantId, row, base)) {
            return base;
        }
        for (int attempt = 0; attempt < 20; attempt++) {
            String candidate = attempt == 0 ? "p-" + row.getPageId() : "p-" + row.getPageId() + "-" + attempt;
            if (slugAvailable(tenantId, row, candidate)) {
                return candidate;
            }
        }
        throw DocErrorCode.exception(DocErrorCode.SLUG_DUPLICATED);
    }

    /**
     * 判断 slug 在空间中是否未被占用。
     *
     * @param tenantId 租户ID
     * @param row      回收站页面
     * @param slug     候选 slug
     * @return 可用返回 {@code true}
     */
    private boolean slugAvailable(Long tenantId, DocTrashPageRow row, String slug) {
        return pageMapper.countSlugIncludeDeleted(tenantId, row.getSpaceId(), slug, row.getPageId()) == 0;
    }

    /**
     * 物理删除一个回收站子树及其关联数据。
     *
     * @param tenantId 租户ID
     * @param rootId   回收站根页面ID
     */
    private void purgeRoot(Long tenantId, Long rootId) {
        List<DocTrashPageRow> subtree = pageMapper.selectTrashSubtree(tenantId, rootId);
        if (subtree.isEmpty()) {
            return;
        }
        List<Long> pageIds = new ArrayList<>(subtree.size());
        for (DocTrashPageRow row : subtree) {
            pageIds.add(row.getPageId());
        }
        versionMapper.deleteByPageIds(tenantId, pageIds);
        commentMapper.deleteByPageIds(tenantId, pageIds);
        commentReadMapper.deleteByPageIds(tenantId, pageIds);
        attachmentMapper.deleteByPageIds(tenantId, pageIds);
        starMapper.deleteByPageIds(tenantId, pageIds);
        aclMapper.deleteByPageIds(tenantId, pageIds);
        tagMapper.deleteByPageIds(tenantId, pageIds);
        linkMapper.deleteByPageIds(tenantId, pageIds);
        recentMapper.deleteByPageIds(tenantId, pageIds);
        activityMapper.deleteByPageIds(tenantId, pageIds);
        pageMapper.purgeSubtree(tenantId, rootId);
    }

    /**
     * 查询行转回收站条目视图。
     *
     * @param row 查询行
     * @return 回收站条目
     */
    private DocRecycleItemVO toVO(DocTrashRootRow row) {
        DocRecycleItemVO vo = new DocRecycleItemVO();
        vo.setPageId(row.getPageId());
        vo.setTitle(row.getTitle());
        vo.setPageType(row.getPageType());
        vo.setParentId(row.getParentId());
        vo.setParentTitle(row.getParentTitle());
        vo.setDeletedBy(row.getDeletedBy());
        vo.setDeletedAt(row.getDeletedAt());
        vo.setChildCount(row.getChildCount() == null ? 0L : row.getChildCount());
        return vo;
    }
}
