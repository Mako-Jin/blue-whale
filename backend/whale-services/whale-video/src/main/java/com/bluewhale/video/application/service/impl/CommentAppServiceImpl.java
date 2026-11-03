package com.bluewhale.video.application.service.impl;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.video.application.command.CommentSubmitCommand;
import com.bluewhale.video.application.service.CommentAppService;
import com.bluewhale.video.application.view.CommentAuditView;
import com.bluewhale.video.application.view.CommentView;
import com.bluewhale.video.config.VideoProperties;
import com.bluewhale.video.domain.exception.VideoErrorCode;
import com.bluewhale.video.domain.model.CommentStatus;
import com.bluewhale.video.domain.model.Content;
import com.bluewhale.video.domain.model.ContentComment;
import com.bluewhale.video.domain.repository.CommentLikeRepository;
import com.bluewhale.video.domain.repository.CommentRepository;
import com.bluewhale.video.domain.repository.ContentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 内容评论应用服务实现。
 *
 * <p>用户侧仅展示可见评论与一级回复；默认先发后审，新评论进入审核队列。
 * 审核通过与隐藏幂等，评论数随可见性流转同步增减。评论正文为纯文本，
 * 不做任何 HTML 透传。</p>
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class CommentAppServiceImpl implements CommentAppService {

    /** 单页最大条数 */
    private static final long MAX_PAGE_SIZE = 200L;

    /** 默认每页条数 */
    private static final long DEFAULT_PAGE_SIZE = 20L;

    /** 顶级评论父 ID */
    private static final long TOP_LEVEL_PARENT_ID = 0L;

    /** 审核队列默认状态 */
    private static final CommentStatus DEFAULT_AUDIT_STATUS = CommentStatus.PENDING;

    private final CommentRepository commentRepository;

    private final ContentRepository contentRepository;

    private final CommentLikeRepository commentLikeRepository;

    private final VideoProperties videoProperties;

    @Override
    public PageResult<CommentView> page(Long contentId, Long userId, long current, long size) {
        requireVisibleContent(contentId, userId);
        long pageNo = Math.max(current, 1L);
        long pageSize = clampSize(size);
        List<ContentComment> roots = commentRepository.pageVisibleRoots(
                contentId, (pageNo - 1) * pageSize, pageSize);
        List<Long> rootIds = roots.stream().map(ContentComment::getId).toList();
        List<ContentComment> replies = rootIds.isEmpty() ? Collections.emptyList()
                : commentRepository.findVisibleReplies(rootIds);
        Set<Long> liked = likedIds(userId, roots, replies);
        Map<Long, List<CommentView>> replyMap = replies.stream()
                .map(comment -> toView(comment, liked.contains(comment.getId())))
                .collect(Collectors.groupingBy(CommentView::getParentId));
        List<CommentView> records = new ArrayList<>(roots.size());
        for (ContentComment root : roots) {
            CommentView view = toView(root, liked.contains(root.getId()));
            view.setReplies(replyMap.getOrDefault(root.getId(), Collections.emptyList()));
            records.add(view);
        }
        return PageResult.of(records, commentRepository.countVisible(contentId), pageNo, pageSize);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CommentView submit(Long contentId, CommentSubmitCommand command, Long userId) {
        Content content = requireVisibleContent(contentId, userId);
        if (!content.commentable()) {
            throw VideoErrorCode.exception(VideoErrorCode.CONTENT_NOT_PLAYABLE);
        }
        ContentComment comment = new ContentComment();
        comment.setContentId(contentId);
        comment.setUserId(userId);
        comment.setParentId(resolveParentId(contentId, command.getParentId()));
        comment.setContent(ContentComment.normalize(command.getContent()));
        comment.setStatus(videoProperties.isCommentPreAudit() ? CommentStatus.PENDING : CommentStatus.VISIBLE);
        comment.setLikeCount(0);
        comment.setCreateTime(LocalDateTime.now());
        commentRepository.insert(comment);
        if (comment.visible()) {
            contentRepository.addCommentCount(contentId, 1L);
        }
        return toView(comment, false);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long commentId, Long userId) {
        ContentComment comment = requireComment(commentId);
        if (!userId.equals(comment.getUserId())) {
            throw VideoErrorCode.exception(VideoErrorCode.COMMENT_NOT_FOUND);
        }
        List<ContentComment> replies = comment.getParentId() != null
                && comment.getParentId() == TOP_LEVEL_PARENT_ID
                ? commentRepository.findVisibleReplies(List.of(commentId)) : Collections.emptyList();
        long delta = comment.visible() ? 1L : 0L;
        for (ContentComment reply : replies) {
            if (reply.visible()) {
                delta++;
            }
            commentRepository.delete(reply.getId());
        }
        commentRepository.delete(commentId);
        if (delta > 0L) {
            contentRepository.addCommentCount(comment.getContentId(), -delta);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void like(Long commentId, Long userId) {
        ContentComment comment = requireVisibleComment(commentId);
        if (commentLikeRepository.add(userId, commentId)) {
            commentRepository.addLikeCount(comment.getId(), 1L);
        }
    }

    @Override
    public PageResult<CommentAuditView> auditPage(String status, long current, long size) {
        long pageNo = Math.max(current, 1L);
        long pageSize = clampSize(size);
        CommentStatus parsed = parseAuditStatus(status);
        List<ContentComment> comments = commentRepository.pageByStatus(
                parsed, (pageNo - 1) * pageSize, pageSize);
        return PageResult.of(comments.stream().map(this::toAuditView).toList(),
                commentRepository.countByStatus(parsed), pageNo, pageSize);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CommentAuditView audit(Long commentId, boolean approve) {
        ContentComment comment = requireComment(commentId);
        CommentStatus target = approve ? CommentStatus.VISIBLE : CommentStatus.HIDDEN;
        if (comment.getStatus() == target) {
            return toAuditView(comment);
        }
        boolean wasVisible = comment.visible();
        commentRepository.updateStatus(commentId, target);
        comment.setStatus(target);
        if (target == CommentStatus.VISIBLE && !wasVisible) {
            contentRepository.addCommentCount(comment.getContentId(), 1L);
        } else if (target != CommentStatus.VISIBLE && wasVisible) {
            contentRepository.addCommentCount(comment.getContentId(), -1L);
        }
        return toAuditView(comment);
    }

    /**
     * 批量查询当前用户已点赞的评论 ID。
     *
     * @param userId 用户 ID
     * @param roots 顶级评论
     * @param replies 回复
     * @return 已点赞评论 ID 集合
     */
    private Set<Long> likedIds(Long userId, List<ContentComment> roots, List<ContentComment> replies) {
        if (userId == null) {
            return Collections.emptySet();
        }
        List<Long> ids = new ArrayList<>(roots.size() + replies.size());
        roots.forEach(comment -> ids.add(comment.getId()));
        replies.forEach(comment -> ids.add(comment.getId()));
        return ids.isEmpty() ? Collections.emptySet()
                : new HashSet<>(commentLikeRepository.findLiked(userId, ids));
    }

    /**
     * 加载用户可见内容，不可见按不存在处理。
     *
     * @param contentId 内容 ID
     * @param userId 用户 ID
     * @return 内容
     */
    private Content requireVisibleContent(Long contentId, Long userId) {
        return contentRepository.findById(contentId)
                .filter(content -> content.visibleTo(userId, false))
                .orElseThrow(() -> VideoErrorCode.exception(VideoErrorCode.CONTENT_NOT_FOUND));
    }

    /**
     * 加载评论，不存在时抛出业务异常。
     *
     * @param commentId 评论 ID
     * @return 评论
     */
    private ContentComment requireComment(Long commentId) {
        return commentRepository.findById(commentId)
                .orElseThrow(() -> VideoErrorCode.exception(VideoErrorCode.COMMENT_NOT_FOUND));
    }

    /**
     * 加载可见评论，不可见按不存在处理。
     *
     * @param commentId 评论 ID
     * @return 评论
     */
    private ContentComment requireVisibleComment(Long commentId) {
        return commentRepository.findById(commentId)
                .filter(ContentComment::visible)
                .orElseThrow(() -> VideoErrorCode.exception(VideoErrorCode.COMMENT_NOT_FOUND));
    }

    /**
     * 解析回复目标：顶级返回 0；二级回复统一归并到顶级评论。
     *
     * @param contentId 内容 ID
     * @param parentId 请求父评论 ID
     * @return 实际父评论 ID
     */
    private Long resolveParentId(Long contentId, Long parentId) {
        if (parentId == null || parentId <= 0L) {
            return TOP_LEVEL_PARENT_ID;
        }
        ContentComment parent = commentRepository.findById(parentId)
                .filter(comment -> comment.getContentId().equals(contentId) && comment.visible())
                .orElseThrow(() -> VideoErrorCode.exception(VideoErrorCode.COMMENT_PARENT_INVALID));
        return parent.getParentId() != null && parent.getParentId() > 0L
                ? parent.getParentId() : parent.getId();
    }

    /**
     * 解析审核状态过滤，空或非法值回落默认待审。
     *
     * @param status 状态文本
     * @return 评论状态
     */
    private CommentStatus parseAuditStatus(String status) {
        if (status == null || status.isBlank()) {
            return DEFAULT_AUDIT_STATUS;
        }
        try {
            return CommentStatus.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return DEFAULT_AUDIT_STATUS;
        }
    }

    /**
     * 评论转视图。
     *
     * @param comment 评论
     * @param liked 当前用户是否已点赞
     * @return 评论视图
     */
    private CommentView toView(ContentComment comment, boolean liked) {
        CommentView view = new CommentView();
        fill(view, comment, liked);
        return view;
    }

    /**
     * 评论转审核视图（补充审核状态）。
     *
     * @param comment 评论
     * @return 评论审核视图
     */
    private CommentAuditView toAuditView(ContentComment comment) {
        CommentAuditView view = new CommentAuditView();
        fill(view, comment, false);
        view.setStatus(comment.getStatus() == null ? null : comment.getStatus().name());
        return view;
    }

    /**
     * 写入评论通用字段。
     *
     * @param view 目标视图
     * @param comment 评论
     * @param liked 当前用户是否已点赞
     */
    private void fill(CommentView view, ContentComment comment, boolean liked) {
        view.setId(comment.getId());
        view.setContentId(comment.getContentId());
        view.setUserId(comment.getUserId());
        view.setParentId(comment.getParentId());
        view.setContent(comment.getContent());
        view.setLikeCount(comment.getLikeCount());
        view.setLiked(liked);
        view.setCreateTime(comment.getCreateTime());
        view.setReplies(Collections.emptyList());
    }

    /**
     * 限制分页大小。
     *
     * @param size 请求条数
     * @return 归一化条数
     */
    private long clampSize(long size) {
        return size <= 0 ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);
    }
}
