package com.bluewhale.doc.application.service.impl;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.doc.application.service.DocPageCopyService;
import com.bluewhale.doc.application.service.DocPageLinkService;
import com.bluewhale.doc.application.service.DocPageService;
import com.bluewhale.doc.config.DocProperties;
import com.bluewhale.doc.domain.exception.DocErrorCode;
import com.bluewhale.doc.domain.model.DocMemberRole;
import com.bluewhale.doc.domain.model.DocPageStatus;
import com.bluewhale.doc.infrastructure.persistence.entity.DocPage;
import com.bluewhale.doc.infrastructure.persistence.entity.DocPageAttachment;
import com.bluewhale.doc.infrastructure.persistence.mapper.DocPageAttachmentMapper;
import com.bluewhale.doc.infrastructure.persistence.mapper.DocPageMapper;
import com.bluewhale.doc.interfaces.dto.DocPageDuplicateRequest;
import com.bluewhale.doc.interfaces.vo.DocPageVO;
import com.bluewhale.doc.support.DocAccessGuard;
import com.bluewhale.doc.support.DocContentSupport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 页面复制应用服务实现。
 *
 * <p>复制子树时逐节点重建 slug，并仅复制源页面自己登记的附件引用（含文件ID），
 * 再重写正文中的附件访问地址，避免引用他人页面附件造成越权。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocPageCopyServiceImpl implements DocPageCopyService {

    /** 根父ID */
    private static final long ROOT_PARENT_ID = 0L;

    /** 单次复制的最大节点数 */
    private static final int MAX_COPY_NODES = 500;

    /** 标题最大长度 */
    private static final int TITLE_MAX_LENGTH = 200;

    /** 副本标题后缀 */
    private static final String COPY_SUFFIX = " - 副本";

    /** 附件地址模式 */
    private static final Pattern ATTACHMENT_URL =
            Pattern.compile("/doc/v1/page/(\\d+)/attachment/(\\d+)/content");

    private final DocPageMapper pageMapper;

    private final DocPageAttachmentMapper attachmentMapper;

    private final DocAccessGuard accessGuard;

    private final DocContentSupport contentSupport;

    private final DocProperties docProperties;

    private final DocPageService pageService;

    private final DocPageLinkService linkService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DocPageVO duplicate(Long pageId, DocPageDuplicateRequest request, Long userId) {
        DocPage source = accessGuard.requirePageRead(pageId, userId);
        Long targetSpaceId = request.getSpaceId() == null ? source.getSpaceId() : request.getSpaceId();
        accessGuard.requireMember(targetSpaceId, userId, DocMemberRole.EDITOR);
        Long targetParent = request.getParentId() == null ? ROOT_PARENT_ID : request.getParentId();
        validateParent(targetSpaceId, targetParent);
        boolean includeChildren = request.getIncludeChildren() == null || request.getIncludeChildren();
        List<DocPage> nodes = collectSubtree(source, includeChildren, userId);
        assertDepth(targetSpaceId, targetParent, nodes);
        Long newRootId = copyNodes(nodes, source, targetSpaceId, targetParent, request.getTitle(), userId);
        log.info("页面已复制: source={}, target={}, nodes={}, operator={}",
                pageId, newRootId, nodes.size(), userId);
        return pageService.detail(newRootId, userId);
    }

    /**
     * 收集待复制的子树（根在前，按层级顺序）。
     *
     * @param source          源页面
     * @param includeChildren 是否包含子页面
     * @param userId          当前用户ID（过滤无权限的受限子页面）
     * @return 待复制节点
     */
    private List<DocPage> collectSubtree(DocPage source, boolean includeChildren, Long userId) {
        List<DocPage> all = pageMapper.selectList(Wrappers.<DocPage>lambdaQuery()
                .eq(DocPage::getSpaceId, source.getSpaceId())
                .orderByAsc(DocPage::getSortNo)
                .orderByAsc(DocPage::getId));
        if (!includeChildren) {
            return List.of(source);
        }
        Map<Long, List<DocPage>> children = new HashMap<>();
        for (DocPage page : all) {
            children.computeIfAbsent(page.getParentId(), key -> new ArrayList<>()).add(page);
        }
        List<DocPage> ordered = new ArrayList<>();
        collect(source, children, ordered, userId);
        if (ordered.size() > MAX_COPY_NODES) {
            throw new BizException(ErrorCode.PARAM_ERROR, "doc.page.copy-limit-exceeded", "单次复制的页面数量超过上限");
        }
        return ordered;
    }

    /**
     * 深度优先收集节点。
     *
     * @param node     当前节点
     * @param children 子节点映射
     * @param ordered  收集结果
     * @param userId   当前用户ID
     */
    private void collect(DocPage node, Map<Long, List<DocPage>> children, List<DocPage> ordered,
                         Long userId) {
        ordered.add(node);
        if (ordered.size() > MAX_COPY_NODES) {
            throw new BizException(ErrorCode.PARAM_ERROR, "doc.page.copy-limit-exceeded", "单次复制的页面数量超过上限");
        }
        for (DocPage child : children.getOrDefault(node.getId(), List.of())) {
            if (!accessGuard.canRead(child, userId)) {
                continue;
            }
            collect(child, children, ordered, userId);
        }
    }

    /**
     * 校验目标父页面归属与层级。
     *
     * @param spaceId  目标空间
     * @param parentId 目标父页面
     */
    private void validateParent(Long spaceId, Long parentId) {
        if (parentId == null || parentId == ROOT_PARENT_ID) {
            return;
        }
        DocPage parent = pageMapper.selectById(parentId);
        if (parent == null || !spaceId.equals(parent.getSpaceId())) {
            throw DocErrorCode.exception(DocErrorCode.PAGE_NOT_FOUND);
        }
    }

    /**
     * 校验目标深度不超限。
     *
     * @param spaceId      目标空间
     * @param targetParent 目标父页面
     * @param nodes        待复制节点
     */
    private void assertDepth(Long spaceId, Long targetParent, List<DocPage> nodes) {
        int parentDepth = depthOf(spaceId, targetParent);
        int subtreeHeight = 1;
        Map<Long, DocPage> byId = new LinkedHashMap<>();
        nodes.forEach(node -> byId.put(node.getId(), node));
        for (DocPage node : nodes) {
            subtreeHeight = Math.max(subtreeHeight, heightOf(node, byId));
        }
        if (parentDepth + subtreeHeight > docProperties.getMaxDepth()) {
            throw DocErrorCode.exception(DocErrorCode.TREE_DEPTH_EXCEEDED);
        }
    }

    /**
     * 计算目标父页面层级（根为 0）。
     *
     * @param spaceId  空间
     * @param parentId 父页面
     * @return 层级
     */
    private int depthOf(Long spaceId, Long parentId) {
        if (parentId == null || parentId == ROOT_PARENT_ID) {
            return 0;
        }
        int depth = 1;
        DocPage parent = pageMapper.selectById(parentId);
        int guard = 0;
        while (parent != null && parent.getParentId() != null && parent.getParentId() != ROOT_PARENT_ID) {
            depth++;
            if (guard++ > docProperties.getMaxDepth()) {
                throw DocErrorCode.exception(DocErrorCode.TREE_DEPTH_EXCEEDED);
            }
            parent = pageMapper.selectById(parent.getParentId());
        }
        return depth;
    }

    /**
     * 计算节点在待复制集合内的高度。
     *
     * @param node 节点
     * @param byId 集合索引
     * @return 高度，自身为 1
     */
    private int heightOf(DocPage node, Map<Long, DocPage> byId) {
        int height = 1;
        for (DocPage other : byId.values()) {
            if (node.getId().equals(other.getParentId())) {
                height = Math.max(height, 1 + heightOf(other, byId));
            }
        }
        return height;
    }

    /**
     * 复制节点集合，返回新根页面ID。
     *
     * @param nodes        待复制节点（根在前）
     * @param source       源根页面
     * @param targetSpace  目标空间
     * @param targetParent 目标父页面
     * @param title        根页面新标题
     * @param userId       操作人
     * @return 新根页面ID
     */
    private Long copyNodes(List<DocPage> nodes, DocPage source, Long targetSpace, Long targetParent,
                           String title, Long userId) {
        Map<Long, Long> idMapping = new HashMap<>();
        Long rootId = null;
        long nextSort = nextSortNo(targetSpace, targetParent);
        for (DocPage node : nodes) {
            boolean isRoot = node.getId().equals(source.getId());
            Long newId = IdWorker.getId();
            Long newParent = isRoot ? targetParent : idMapping.get(node.getParentId());
            if (!isRoot && newParent == null) {
                continue;
            }
            DocPage copy = buildCopy(node, newId, targetSpace, newParent, isRoot, title, userId, nextSort++);
            pageMapper.insert(copy);
            copyAttachments(node, copy, userId);
            linkService.refresh(copy.getId(), copy.getContentJson());
            idMapping.put(node.getId(), newId);
            if (isRoot) {
                rootId = newId;
            }
        }
        if (rootId == null) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "doc.page.copy-failed", "页面复制失败");
        }
        return rootId;
    }

    /**
     * 构建页面副本实体。
     *
     * @param node      源节点
     * @param newId     新页面ID
     * @param spaceId   目标空间
     * @param parentId  目标父页面
     * @param isRoot    是否根节点
     * @param title     根节点新标题
     * @param userId    操作人
     * @param sortNo    排序
     * @return 页面副本
     */
    private DocPage buildCopy(DocPage node, Long newId, Long spaceId, Long parentId, boolean isRoot,
                              String title, Long userId, long sortNo) {
        DocPage copy = new DocPage();
        copy.setId(newId);
        copy.setSpaceId(spaceId);
        copy.setParentId(parentId);
        copy.setTitle(isRoot ? resolveTitle(title, node.getTitle()) : node.getTitle());
        copy.setSlug("p-" + newId);
        copy.setPageType(node.getPageType());
        copy.setContentJson(node.getContentJson());
        copy.setContentText(contentSupport.extractText(node.getContentJson()));
        copy.setStatus(DocPageStatus.DRAFT.name());
        copy.setRevision(0L);
        copy.setSortNo(sortNo);
        copy.setIcon(node.getIcon());
        copy.setAuthorId(userId);
        copy.setLastEditorId(userId);
        return copy;
    }

    /**
     * 解析根页面副本标题。
     *
     * @param requested 请求标题
     * @param original  原标题
     * @return 标题
     */
    private String resolveTitle(String requested, String original) {
        String value = requested;
        if (value == null || value.isBlank()) {
            value = (original == null ? "未命名页面" : original) + COPY_SUFFIX;
        }
        value = value.trim();
        return value.length() > TITLE_MAX_LENGTH ? value.substring(0, TITLE_MAX_LENGTH) : value;
    }

    /**
     * 复制源页面附件引用并重写正文附件地址。
     *
     * @param source 源页面
     * @param copy   新页面
     * @param userId 操作人
     */
    private void copyAttachments(DocPage source, DocPage copy, Long userId) {
        List<DocPageAttachment> attachments = attachmentMapper.selectList(
                Wrappers.<DocPageAttachment>lambdaQuery()
                        .eq(DocPageAttachment::getPageId, source.getId())
                        .orderByAsc(DocPageAttachment::getSortNo));
        if (attachments.isEmpty()) {
            return;
        }
        Map<Long, Long> mapped = new HashMap<>();
        for (DocPageAttachment attachment : attachments) {
            DocPageAttachment item = new DocPageAttachment();
            item.setPageId(copy.getId());
            item.setFileId(attachment.getFileId());
            item.setOriginalName(attachment.getOriginalName());
            item.setContentType(attachment.getContentType());
            item.setFileSize(attachment.getFileSize());
            item.setUsageType(attachment.getUsageType());
            item.setSortNo(attachment.getSortNo());
            attachmentMapper.insert(item);
            mapped.put(attachment.getId(), item.getId());
        }
        copy.setContentJson(rewriteAttachmentUrls(copy.getContentJson(), source.getId(), copy.getId(), mapped));
        pageMapper.updateById(copy);
    }

    /**
     * 重写正文中的附件访问地址。
     *
     * @param contentJson  正文 JSON
     * @param oldPageId    源页面ID
     * @param newPageId    新页面ID
     * @param attachmentId 附件ID映射
     * @return 重写后的正文 JSON
     */
    private String rewriteAttachmentUrls(String contentJson, Long oldPageId, Long newPageId,
                                         Map<Long, Long> attachmentId) {
        if (contentJson == null || contentJson.isEmpty()) {
            return contentJson;
        }
        Matcher matcher = ATTACHMENT_URL.matcher(contentJson);
        StringBuilder builder = new StringBuilder();
        while (matcher.find()) {
            Long mappedId = attachmentId.get(Long.valueOf(matcher.group(2)));
            String replacement = mappedId == null ? matcher.group()
                    : "/doc/v1/page/" + newPageId + "/attachment/" + mappedId + "/content";
            matcher.appendReplacement(builder, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(builder);
        return builder.toString();
    }

    /**
     * 计算目标父页面下个排序值。
     *
     * @param spaceId  空间
     * @param parentId 父页面
     * @return 排序值
     */
    private long nextSortNo(Long spaceId, Long parentId) {
        List<DocPage> latest = pageMapper.selectList(Wrappers.<DocPage>lambdaQuery()
                .eq(DocPage::getSpaceId, spaceId)
                .eq(DocPage::getParentId, parentId)
                .orderByDesc(DocPage::getSortNo)
                .last("LIMIT 1"));
        return latest.isEmpty() ? 0L : (latest.get(0).getSortNo() == null ? 0L : latest.get(0).getSortNo() + 1);
    }
}
