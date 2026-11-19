package com.bluewhale.forum.service.impl;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.tenant.TenantContext;
import com.bluewhale.forum.common.ForumActor;
import com.bluewhale.forum.common.ForumErrorCodes;
import com.bluewhale.forum.entity.ForumModerationRecord;
import com.bluewhale.forum.entity.ForumPost;
import com.bluewhale.forum.entity.ForumThread;
import com.bluewhale.forum.mapper.ForumModerationRecordMapper;
import com.bluewhale.forum.mapper.ForumPostMapper;
import com.bluewhale.forum.mapper.ForumThreadMapper;
import com.bluewhale.forum.config.ForumProperties;
import com.bluewhale.forum.service.BanService;
import com.bluewhale.forum.service.ExpService;
import com.bluewhale.forum.service.ForumNotifyService;
import com.bluewhale.forum.service.ModerationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 处置实现。
 *
 * <p>删除顺序固定为「先改写编号 → 再逻辑删除」：反过来会留下永远无法重建的编号
 * （唯一键仍指向已删行）。</p>
 *
 * <p>每次处置都写轨迹并发通知，<b>两步都不允许失败静默</b>：轨迹写不进去说明
 * 审计链断了，必须让事务回滚。</p>
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class ModerationServiceImpl implements ModerationService {

    /** 已发布 */
    private static final String STATUS_PUBLISHED = "PUBLISHED";

    /** 已删除 */
    private static final String STATUS_DELETED = "DELETED";

    /** 已移除（楼层） */
    private static final String STATUS_REMOVED = "REMOVED";

    private final ForumThreadMapper threadMapper;

    private final ForumPostMapper postMapper;

    private final ForumModerationRecordMapper recordMapper;

    private final BanService banService;

    private final ForumNotifyService notifyService;

    private final ExpService expService;

    private final ForumProperties properties;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeThread(Long threadId, String reason, ForumActor actor) {
        requireReason(reason);
        ForumThread thread = threadMapper.selectById(threadId);
        if (thread == null) {
            throw new BizException(ForumErrorCodes.THREAD_NOT_FOUND, "forum.thread.not-found-or-deleted",
                    "帖子不存在或已删除");
        }
        threadMapper.renameNoForDelete(thread.getId(), thread.getThreadNo() + "#D" + thread.getId());
        thread.setStatus(STATUS_DELETED);
        threadMapper.updateById(thread);
        threadMapper.deleteById(thread.getId());
        Long recordId = record("THREAD", threadId, "REMOVE", reason, actor.userId());
        notifyService.notifyModeration(thread.getAuthorId(), "THREAD", threadId, "REMOVE", reason, recordId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removePost(Long postId, String reason, ForumActor actor) {
        requireReason(reason);
        ForumPost post = postMapper.selectById(postId);
        if (post == null) {
            throw new BizException(ForumErrorCodes.THREAD_NOT_FOUND, "forum.post.not-found-or-deleted",
                    "楼层不存在或已删除");
        }
        if (STATUS_PUBLISHED.equals(post.getStatus())) {
            // 只有可见楼层才占着回复数，删待审楼层不递减，否则计数会偏小
            threadMapper.decreaseReplyCount(post.getThreadId());
        }
        post.setStatus(STATUS_REMOVED);
        postMapper.updateById(post);
        postMapper.deleteById(post.getId());
        Long recordId = record("POST", postId, "REMOVE", reason, actor.userId());
        notifyService.notifyModeration(post.getAuthorId(), "POST", postId, "REMOVE", reason, recordId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void ban(Long userId, String banType, int days, String reason, ForumActor actor, Long reportId) {
        requireReason(reason);
        banService.ban(userId, banType, days, reason, actor.userId(), reportId);
        Long recordId = record("USER", userId, banType, reason, actor.userId());
        notifyService.notifyModeration(userId, "USER", userId, banType, reason, recordId);
    }

    @Override
    public int release(Long userId, ForumActor actor) {
        int released = banService.release(userId, actor.userId());
        record("USER", userId, "RELEASE", "解除处置", actor.userId());
        return released;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void featureThread(Long threadId, String reason, ForumActor actor) {
        requireReason(reason);
        ForumThread thread = requireThread(threadId);
        if (thread.getEssence() != null && thread.getEssence() == 1) {
            // 已是精华：幂等返回。不重复留痕、更不能重复发经验——唯一键挡得住经验，
            // 但每次都写一条 ESSENCE 轨迹会把审计流水灌满重复记录
            return;
        }
        thread.setEssence(1);
        threadMapper.updateById(thread);
        Long recordId = record("THREAD", threadId, "ESSENCE", reason, actor.userId());
        notifyService.notifyModeration(thread.getAuthorId(), "THREAD", threadId, "ESSENCE", reason, recordId);
        if (!isAnonymous(thread)) {
            expService.award(thread.getAuthorId(), ExpService.EVENT_ESSENCE_AWARD,
                    properties.getExpEssenceAward(), threadId);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unfeatureThread(Long threadId, String reason, ForumActor actor) {
        requireReason(reason);
        ForumThread thread = requireThread(threadId);
        if (thread.getEssence() == null || thread.getEssence() != 1) {
            return;
        }
        thread.setEssence(0);
        threadMapper.updateById(thread);
        Long recordId = record("THREAD", threadId, "UNESSENCE", reason, actor.userId());
        notifyService.notifyModeration(thread.getAuthorId(), "THREAD", threadId, "UNESSENCE", reason, recordId);
    }

    /**
     * 取可处置的帖子（已删/已移除不可处置）。
     *
     * @param threadId 帖子ID
     * @return 帖子
     */
    private ForumThread requireThread(Long threadId) {
        ForumThread thread = threadMapper.selectById(threadId);
        if (thread == null || STATUS_DELETED.equals(thread.getStatus())
                || STATUS_REMOVED.equals(thread.getStatus())) {
            throw new BizException(ForumErrorCodes.THREAD_NOT_FOUND, "forum.thread.not-found-or-deleted",
                    "帖子不存在或已删除");
        }
        return thread;
    }

    /**
     * 匿名帖不计经验（F-07-04 硬规则：匿名刷分零成本）。
     *
     * @param thread 帖子
     * @return 是否匿名
     */
    private boolean isAnonymous(ForumThread thread) {
        return thread.getAnonymous() != null && thread.getAnonymous() == 1;
    }

    @Override
    public Long record(String targetType, Long targetId, String action, String reason, Long operatorId) {
        ForumModerationRecord row = new ForumModerationRecord();
        row.setTenantId(TenantContext.requireId());
        row.setTargetType(targetType);
        row.setTargetId(targetId);
        row.setAction(action);
        row.setReason(reason);
        row.setOperatorId(operatorId);
        row.setCreateTime(LocalDateTime.now());
        recordMapper.insert(row);
        return row.getId();
    }

    /**
     * 处置原因必填：没有原因的删帖/禁言无法向用户解释，也无法在申诉时自证。
     *
     * @param reason 原因
     */
    private void requireReason(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "forum.moderation.reason-required", "处置原因必填");
        }
    }
}
