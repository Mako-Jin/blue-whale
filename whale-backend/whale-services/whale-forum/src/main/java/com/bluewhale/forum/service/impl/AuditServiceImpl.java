package com.bluewhale.forum.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.tenant.TenantContext;
import com.bluewhale.forum.common.ForumActor;
import com.bluewhale.forum.common.ForumErrorCodes;
import com.bluewhale.forum.config.ForumProperties;
import com.bluewhale.forum.dto.AuditItemVO;
import com.bluewhale.forum.entity.ForumPost;
import com.bluewhale.forum.entity.ForumThread;
import com.bluewhale.forum.mapper.ForumPostMapper;
import com.bluewhale.forum.mapper.ForumThreadMapper;
import com.bluewhale.forum.service.AuditService;
import com.bluewhale.forum.service.ExpService;
import com.bluewhale.forum.service.FollowService;
import com.bluewhale.forum.service.ForumNotifyService;
import com.bluewhale.forum.service.ModerationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 审核台实现。
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class AuditServiceImpl implements AuditService {

    /** 待审 */
    private static final String STATUS_PENDING = "PENDING";

    /** 已发布 */
    private static final String STATUS_PUBLISHED = "PUBLISHED";

    /** 已驳回 */
    private static final String STATUS_REJECTED = "REJECTED";

    /** 对象类型：帖子 */
    private static final String TYPE_THREAD = "THREAD";

    /** 对象类型：楼层 */
    private static final String TYPE_POST = "POST";

    /** 摘要截断长度（用于列表预览） */
    private static final int PREVIEW_LENGTH = 120;

    private final ForumThreadMapper threadMapper;

    private final ForumPostMapper postMapper;

    private final ModerationService moderationService;

    private final ForumNotifyService notifyService;

    private final ForumProperties properties;

    private final ExpService expService;

    private final FollowService followService;

    @Override
    public PageResult<AuditItemVO> page(String targetType, Long current, Long size, ForumActor actor) {
        TenantContext.requireId();
        long pageNo = current == null || current < 1 ? 1 : Math.min(current, properties.getMaxPageNumber());
        long pageSize = size == null || size < 1
                ? properties.getPageSizeDefault()
                : Math.min(size, properties.getPageSizeMax());
        if (TYPE_POST.equalsIgnoreCase(targetType)) {
            return pagePosts(pageNo, pageSize);
        }
        return pageThreads(pageNo, pageSize);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void pass(String targetType, Long targetId, String remark, ForumActor actor) {
        TenantContext.requireId();
        if (TYPE_POST.equalsIgnoreCase(targetType)) {
            ForumPost post = requirePendingPost(targetId);
            post.setStatus(STATUS_PUBLISHED);
            postMapper.updateById(post);
            // 待审楼层落库时没计入回复数，通过时才 +1
            threadMapper.increaseReplyCount(post.getThreadId(), post.getAuthorId(), LocalDateTime.now());
            moderationService.record(TYPE_POST, targetId, "AUDIT_PASS", remark, actor.userId());
            return;
        }
        ForumThread thread = requirePendingThread(targetId);
        thread.setStatus(STATUS_PUBLISHED);
        thread.setPublishTime(LocalDateTime.now());
        threadMapper.updateById(thread);
        moderationService.record(TYPE_THREAD, targetId, "AUDIT_PASS", remark, actor.userId());
        // 待审帖通过转发布，此处补发帖经验（直接发布的在 create 里已发）
        if (thread.getAnonymous() == null || thread.getAnonymous() != 1) {
            expService.award(thread.getAuthorId(), ExpService.EVENT_THREAD_PUBLISH,
                    properties.getExpThreadPublish(), thread.getId());
        }
        // 审核通过才算「发布」，此时才通知关注者（待审阶段通知等于推死链）
        followService.notifyNewThread(thread.getId(), thread.getBoardId(), thread.getAuthorId(),
                thread.getTitle(), thread.getAnonymous());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reject(String targetType, Long targetId, String remark, ForumActor actor) {
        TenantContext.requireId();
        if (remark == null || remark.isBlank()) {
            throw new BizException(ForumErrorCodes.AUDIT_REMARK_REQUIRED, "forum.audit.reject-remark-required",
                    "驳回必须填写意见，否则用户无从修改");
        }
        if (TYPE_POST.equalsIgnoreCase(targetType)) {
            ForumPost post = requirePendingPost(targetId);
            post.setStatus(STATUS_REJECTED);
            postMapper.updateById(post);
            Long recordId = moderationService.record(TYPE_POST, targetId, "AUDIT_REJECT", remark, actor.userId());
            notifyService.notifyModeration(post.getAuthorId(), TYPE_POST, targetId, "AUDIT_REJECT", remark, recordId);
            return;
        }
        ForumThread thread = requirePendingThread(targetId);
        thread.setStatus(STATUS_REJECTED);
        threadMapper.updateById(thread);
        Long recordId = moderationService.record(TYPE_THREAD, targetId, "AUDIT_REJECT", remark, actor.userId());
        notifyService.notifyModeration(thread.getAuthorId(), TYPE_THREAD, targetId, "AUDIT_REJECT", remark, recordId);
    }

    /**
     * 帖子待审分页。
     *
     * @param pageNo 页码
     * @param pageSize 每页条数
     * @return 分页结果
     */
    private PageResult<AuditItemVO> pageThreads(long pageNo, long pageSize) {
        IPage<ForumThread> page = threadMapper.selectPage(new Page<>(pageNo, pageSize),
                new QueryWrapper<ForumThread>()
                        .eq("status", STATUS_PENDING)
                        .orderByAsc("create_time"));
        List<AuditItemVO> records = new ArrayList<>(page.getRecords().size());
        for (ForumThread thread : page.getRecords()) {
            AuditItemVO vo = new AuditItemVO();
            vo.setTargetType(TYPE_THREAD);
            vo.setTargetId(thread.getId());
            vo.setThreadId(thread.getId());
            vo.setTitle(thread.getTitle());
            vo.setSummary(preview(thread.getContent()));
            vo.setAuthorId(thread.getAuthorId());
            vo.setBoardId(thread.getBoardId());
            vo.setStatus(thread.getStatus());
            vo.setCreateTime(thread.getCreateTime());
            records.add(vo);
        }
        return PageResult.of(records, page.getTotal(), page.getCurrent(), page.getSize());
    }

    /**
     * 楼层待审分页（标题取所属帖子）。
     *
     * @param pageNo 页码
     * @param pageSize 每页条数
     * @return 分页结果
     */
    private PageResult<AuditItemVO> pagePosts(long pageNo, long pageSize) {
        IPage<ForumPost> page = postMapper.selectPage(new Page<>(pageNo, pageSize),
                new QueryWrapper<ForumPost>()
                        .eq("status", STATUS_PENDING)
                        .orderByAsc("create_time"));
        List<Long> threadIds = page.getRecords().stream().map(ForumPost::getThreadId).distinct().toList();
        Map<Long, ForumThread> threads = new HashMap<>();
        if (!threadIds.isEmpty()) {
            for (ForumThread thread : threadMapper.selectByIds(threadIds)) {
                threads.put(thread.getId(), thread);
            }
        }
        List<AuditItemVO> records = new ArrayList<>(page.getRecords().size());
        for (ForumPost post : page.getRecords()) {
            ForumThread thread = threads.get(post.getThreadId());
            AuditItemVO vo = new AuditItemVO();
            vo.setTargetType(TYPE_POST);
            vo.setTargetId(post.getId());
            vo.setThreadId(post.getThreadId());
            vo.setTitle(thread == null ? "（帖子已删除）" : thread.getTitle());
            vo.setSummary(preview(post.getContent()));
            vo.setAuthorId(post.getAuthorId());
            vo.setBoardId(thread == null ? null : thread.getBoardId());
            vo.setFloor(post.getFloor());
            vo.setStatus(post.getStatus());
            vo.setCreateTime(post.getCreateTime());
            records.add(vo);
        }
        return PageResult.of(records, page.getTotal(), page.getCurrent(), page.getSize());
    }

    /**
     * 取待审帖子，状态不对直接拒绝（防止重复审核把已发布帖打回）。
     *
     * @param threadId 帖子ID
     * @return 帖子
     */
    private ForumThread requirePendingThread(Long threadId) {
        ForumThread thread = threadId == null ? null : threadMapper.selectById(threadId);
        if (thread == null) {
            throw new BizException(ForumErrorCodes.THREAD_NOT_FOUND, "forum.thread.not-found-or-deleted",
                    "帖子不存在或已删除");
        }
        if (!STATUS_PENDING.equals(thread.getStatus())) {
            throw new BizException(ForumErrorCodes.THREAD_STATE_ILLEGAL, "forum.audit.thread-not-pending",
                    "该帖当前状态为 " + thread.getStatus() + "，不在待审队列", thread.getStatus());
        }
        return thread;
    }

    /**
     * 取待审楼层。
     *
     * @param postId 楼层ID
     * @return 楼层
     */
    private ForumPost requirePendingPost(Long postId) {
        ForumPost post = postId == null ? null : postMapper.selectById(postId);
        if (post == null) {
            throw new BizException(ForumErrorCodes.THREAD_NOT_FOUND, "forum.post.not-found-or-deleted",
                    "楼层不存在或已删除");
        }
        if (!STATUS_PENDING.equals(post.getStatus())) {
            throw new BizException(ForumErrorCodes.THREAD_STATE_ILLEGAL, "forum.audit.post-not-pending",
                    "该楼层当前状态为 " + post.getStatus() + "，不在待审队列", post.getStatus());
        }
        return post;
    }

    /**
     * 富文本转纯文本摘要。
     *
     * @param content 正文
     * @return 摘要
     */
    private String preview(String content) {
        if (content == null) {
            return "";
        }
        String plain = content.replaceAll("<[^>]+>", " ").replaceAll("\\s+", " ").trim();
        return plain.length() > PREVIEW_LENGTH ? plain.substring(0, PREVIEW_LENGTH) + "…" : plain;
    }
}
