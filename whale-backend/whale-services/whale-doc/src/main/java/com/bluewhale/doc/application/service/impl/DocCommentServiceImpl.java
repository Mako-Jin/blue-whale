package com.bluewhale.doc.application.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.tenant.TenantContext;
import com.bluewhale.doc.application.service.DocCommentService;
import com.bluewhale.doc.application.service.DocNotificationService;
import com.bluewhale.doc.application.service.DocPageActivityService;
import com.bluewhale.doc.domain.exception.DocErrorCode;
import com.bluewhale.doc.domain.model.DocMemberRole;
import com.bluewhale.doc.infrastructure.persistence.entity.DocComment;
import com.bluewhale.doc.infrastructure.persistence.entity.DocCommentRead;
import com.bluewhale.doc.infrastructure.persistence.entity.DocPage;
import com.bluewhale.doc.infrastructure.persistence.mapper.DocCommentMapper;
import com.bluewhale.doc.infrastructure.persistence.mapper.DocCommentReadMapper;
import com.bluewhale.doc.interfaces.dto.DocCommentCreateRequest;
import com.bluewhale.doc.interfaces.vo.DocCommentUnreadVO;
import com.bluewhale.doc.interfaces.vo.DocCommentVO;
import com.bluewhale.doc.support.DocAccessGuard;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 页面评论应用服务实现。
 *
 * <p>评论与回复仅对页面可读且为空间成员或 ACL 授权用户开放；解决/删除由评论作者或空间
 * ADMIN 及以上执行；删除主评论时级联逻辑删除其回复。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocCommentServiceImpl implements DocCommentService {

    /** 主评论父ID */
    private static final long MAIN_COMMENT_PARENT = 0L;

    /** 已解决 */
    private static final int RESOLVED_YES = 1;

    /** 未解决 */
    private static final int RESOLVED_NO = 0;

    /** 评论正文中的 @用户ID 模式 */
    private static final Pattern MENTION_PATTERN = Pattern.compile("@(\\d{1,19})");

    private final DocCommentMapper commentMapper;

    private final DocCommentReadMapper readMapper;

    private final DocAccessGuard accessGuard;

    private final DocNotificationService notificationService;

    private final DocPageActivityService activityService;

    @Override
    public List<DocCommentVO> list(Long pageId, Boolean resolved, Long userId) {
        DocPage page = accessGuard.requirePage(pageId);
        if (!accessGuard.canRead(page, userId)) {
            throw DocErrorCode.exception(DocErrorCode.PAGE_NOT_FOUND);
        }
        Integer flag = resolved == null ? null : (resolved ? RESOLVED_YES : RESOLVED_NO);
        List<DocComment> comments = commentMapper.selectList(Wrappers.<DocComment>lambdaQuery()
                .eq(DocComment::getPageId, pageId)
                .eq(flag != null, DocComment::getResolved, flag)
                .orderByAsc(DocComment::getCreateTime));
        return assemble(comments);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DocCommentVO create(Long pageId, DocCommentCreateRequest request, Long userId) {
        DocPage page = accessGuard.requirePage(pageId);
        if (!accessGuard.canComment(page, userId)) {
            throw DocErrorCode.exception(DocErrorCode.SPACE_ACCESS_DENIED);
        }
        Long parentId = request.getParentId() == null ? MAIN_COMMENT_PARENT : request.getParentId();
        if (parentId != MAIN_COMMENT_PARENT) {
            assertReplyTarget(pageId, parentId);
        }
        DocComment comment = new DocComment();
        comment.setPageId(pageId);
        comment.setParentId(parentId);
        comment.setAnchorBlockId(parentId == MAIN_COMMENT_PARENT ? request.getAnchorBlockId() : null);
        comment.setAnchorText(parentId == MAIN_COMMENT_PARENT ? request.getAnchorText() : null);
        comment.setContent(request.getContent().trim());
        comment.setAuthorId(userId);
        comment.setResolved(RESOLVED_NO);
        commentMapper.insert(comment);
        notificationService.commentMention(TenantContext.requireId(), page, comment.getId(), userId,
                resolveMentions(request));
        activityService.record(pageId, "COMMENT", userId,
                parentId == MAIN_COMMENT_PARENT ? "新增评论" : "回复评论");
        return toVO(comment);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DocCommentVO resolve(Long commentId, Boolean resolved, Long userId) {
        DocComment comment = requireComment(commentId);
        assertAuthorOrAdmin(comment, userId);
        boolean target = resolved == null || resolved;
        LambdaUpdateWrapper<DocComment> wrapper = Wrappers.lambdaUpdate();
        wrapper.eq(DocComment::getId, commentId)
                .set(DocComment::getResolved, target ? RESOLVED_YES : RESOLVED_NO)
                .set(DocComment::getResolvedBy, target ? userId : null)
                .set(DocComment::getResolvedAt, target ? LocalDateTime.now() : null)
                .set(DocComment::getUpdateTime, LocalDateTime.now());
        commentMapper.update(null, wrapper);
        return toVO(commentMapper.selectById(commentId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long commentId, Long userId) {
        DocComment comment = requireComment(commentId);
        assertAuthorOrAdmin(comment, userId);
        if (comment.getParentId() != null && comment.getParentId() == MAIN_COMMENT_PARENT) {
            commentMapper.delete(Wrappers.<DocComment>lambdaQuery()
                    .eq(DocComment::getPageId, comment.getPageId())
                    .eq(DocComment::getParentId, commentId));
        }
        commentMapper.deleteById(commentId);
        log.info("评论已删除: commentId={}, operator={}", commentId, userId);
    }

    @Override
    public DocCommentUnreadVO unread(Long pageId, Long userId) {
        DocPage page = accessGuard.requirePage(pageId);
        if (!accessGuard.canRead(page, userId)) {
            throw DocErrorCode.exception(DocErrorCode.PAGE_NOT_FOUND);
        }
        DocCommentRead read = readMapper.selectOne(Wrappers.<DocCommentRead>lambdaQuery()
                .eq(DocCommentRead::getPageId, pageId)
                .eq(DocCommentRead::getUserId, userId));
        long lastReadId = read == null || read.getLastReadCommentId() == null ? 0L : read.getLastReadCommentId();
        long unread = commentMapper.selectCount(Wrappers.<DocComment>lambdaQuery()
                .eq(DocComment::getPageId, pageId)
                .gt(DocComment::getId, lastReadId)
                .ne(DocComment::getAuthorId, userId));
        DocCommentUnreadVO vo = new DocCommentUnreadVO();
        vo.setUnread(unread);
        vo.setLastReadCommentId(lastReadId);
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DocCommentUnreadVO markRead(Long pageId, Long userId) {
        DocPage page = accessGuard.requirePage(pageId);
        if (!accessGuard.canRead(page, userId)) {
            throw DocErrorCode.exception(DocErrorCode.PAGE_NOT_FOUND);
        }
        List<DocComment> latest = commentMapper.selectList(Wrappers.<DocComment>lambdaQuery()
                .eq(DocComment::getPageId, pageId)
                .orderByDesc(DocComment::getId)
                .last("LIMIT 1"));
        long maxId = latest.isEmpty() || latest.get(0).getId() == null ? 0L : latest.get(0).getId();
        LocalDateTime now = LocalDateTime.now();
        DocCommentRead read = readMapper.selectOne(Wrappers.<DocCommentRead>lambdaQuery()
                .eq(DocCommentRead::getPageId, pageId)
                .eq(DocCommentRead::getUserId, userId));
        if (read == null) {
            read = new DocCommentRead();
            read.setPageId(pageId);
            read.setUserId(userId);
            read.setLastReadCommentId(maxId);
            read.setLastReadAt(now);
            readMapper.insert(read);
        } else {
            read.setLastReadCommentId(Math.max(maxId, read.getLastReadCommentId() == null ? 0L : read.getLastReadCommentId()));
            read.setLastReadAt(now);
            read.setUpdateTime(now);
            readMapper.updateById(read);
        }
        return unread(pageId, userId);
    }

    /**
     * 解析评论中提及的用户ID（显式列表优先，并合并正文 {@code @用户ID}）。
     *
     * @param request 评论请求
     * @return 去重后的用户ID
     */
    private List<Long> resolveMentions(DocCommentCreateRequest request) {
        Set<Long> ids = new LinkedHashSet<>();
        if (request.getMentionedUserIds() != null) {
            ids.addAll(request.getMentionedUserIds());
        }
        Matcher matcher = MENTION_PATTERN.matcher(request.getContent());
        while (matcher.find() && ids.size() < 10) {
            try {
                ids.add(Long.valueOf(matcher.group(1)));
            } catch (NumberFormatException ignored) {
                // 超出范围的内容按普通文本处理
            }
        }
        return new ArrayList<>(ids);
    }

    /**
     * 校验回复目标为同页主评论。
     *
     * @param pageId   页面ID
     * @param parentId 父评论ID
     */
    private void assertReplyTarget(Long pageId, Long parentId) {
        DocComment parent = commentMapper.selectById(parentId);
        if (parent == null || !pageId.equals(parent.getPageId())
                || (parent.getParentId() != null && parent.getParentId() != MAIN_COMMENT_PARENT)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "doc.comment.reply-target-not-found", "回复目标评论不存在");
        }
    }

    /**
     * 读取评论。
     *
     * @param commentId 评论ID
     * @return 评论
     */
    private DocComment requireComment(Long commentId) {
        DocComment comment = commentMapper.selectById(commentId);
        if (comment == null) {
            throw DocErrorCode.exception(DocErrorCode.PAGE_NOT_FOUND);
        }
        return comment;
    }

    /**
     * 校验当前用户为评论作者或空间管理员。
     *
     * @param comment 评论
     * @param userId  用户ID
     */
    private void assertAuthorOrAdmin(DocComment comment, Long userId) {
        if (comment.getAuthorId().equals(userId)) {
            return;
        }
        DocPage page = accessGuard.requirePage(comment.getPageId());
        accessGuard.requireMember(page.getSpaceId(), userId, DocMemberRole.ADMIN);
    }

    /**
     * 组装主评论与回复。
     *
     * @param comments 评论列表
     * @return 主评论列表
     */
    private List<DocCommentVO> assemble(List<DocComment> comments) {
        Map<Long, DocCommentVO> mainComments = new LinkedHashMap<>();
        List<DocCommentVO> roots = new ArrayList<>();
        for (DocComment comment : comments) {
            if (comment.getParentId() == null || comment.getParentId() == MAIN_COMMENT_PARENT) {
                DocCommentVO vo = toVO(comment);
                mainComments.put(comment.getId(), vo);
                roots.add(vo);
            }
        }
        for (DocComment comment : comments) {
            if (comment.getParentId() != null && comment.getParentId() != MAIN_COMMENT_PARENT) {
                DocCommentVO parent = mainComments.get(comment.getParentId());
                if (parent != null) {
                    parent.getReplies().add(toVO(comment));
                }
            }
        }
        return roots;
    }

    /**
     * 实体转评论视图。
     *
     * @param comment 评论
     * @return 评论视图
     */
    private DocCommentVO toVO(DocComment comment) {
        DocCommentVO vo = new DocCommentVO();
        vo.setId(comment.getId());
        vo.setPageId(comment.getPageId());
        vo.setParentId(comment.getParentId());
        vo.setAnchorBlockId(comment.getAnchorBlockId());
        vo.setAnchorText(comment.getAnchorText());
        vo.setContent(comment.getContent());
        vo.setAuthorId(comment.getAuthorId());
        vo.setResolved(comment.getResolved() != null && comment.getResolved() == RESOLVED_YES);
        vo.setResolvedBy(comment.getResolvedBy());
        vo.setResolvedAt(comment.getResolvedAt());
        vo.setCreateTime(comment.getCreateTime());
        return vo;
    }
}
