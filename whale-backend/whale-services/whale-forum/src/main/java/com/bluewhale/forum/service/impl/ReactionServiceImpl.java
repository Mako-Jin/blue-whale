package com.bluewhale.forum.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.tenant.TenantContext;
import com.bluewhale.forum.common.ForumActor;
import com.bluewhale.forum.common.ForumErrorCodes;
import com.bluewhale.forum.common.enums.ReactionType;
import com.bluewhale.forum.config.ForumProperties;
import com.bluewhale.forum.dto.ReactionStateVO;
import com.bluewhale.forum.entity.ForumReaction;
import com.bluewhale.forum.entity.ForumThread;
import com.bluewhale.forum.mapper.ForumReactionMapper;
import com.bluewhale.forum.mapper.ForumThreadMapper;
import com.bluewhale.forum.service.ExpService;
import com.bluewhale.forum.service.ReactionService;
import com.bluewhale.forum.service.ThreadService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Set;

/**
 * 互动服务实现。
 *
 * <p>三条硬规则：</p>
 * <ul>
 *   <li>写入用 {@code INSERT IGNORE}、取消用物理 DELETE，靠唯一键做幂等，
 *       <b>不靠抛重复键异常</b>——异常控制流会把事务标成 rollback-only，
 *       后面的计数更新就不再提交，表现为「点了没反应也没报错」；</li>
 *   <li>计数只用 {@code SET x = x ± 1}，绝不「读出来 +1 再写回」；</li>
 *   <li>返回前回读计数，前端不本地加减——并发下本地值与真实值只会越差越远。</li>
 * </ul>
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class ReactionServiceImpl implements ReactionService {

    /** 目标类型：帖子（楼层互动 M4 再开） */
    private static final String TARGET_THREAD = "THREAD";

    private final ForumReactionMapper reactionMapper;

    private final ForumThreadMapper threadMapper;

    private final ThreadService threadService;

    private final ReactionStateReader reactionStateReader;

    private final ForumProperties properties;

    private final ExpService expService;

    @Override
    public ReactionStateVO toggle(Long threadId, String reactionType, boolean enable, ForumActor actor) {
        ReactionType type = parseType(reactionType);
        // 可见性校验交给帖子服务：不可见/不存在统一 30001，不泄露存在性
        ForumThread thread = threadService.requireVisibleThread(threadId, actor);
        if (enable) {
            assertRate(actor.userId());
            ForumReaction reaction = new ForumReaction();
            reaction.setId(IdWorker.getId());
            reaction.setTenantId(TenantContext.requireId());
            reaction.setUserId(actor.userId());
            reaction.setTargetType(TARGET_THREAD);
            reaction.setTargetId(threadId);
            reaction.setReactionType(type.name());
            if (reactionMapper.insertIgnore(reaction) > 0) {
                changeCounter(threadId, type, 1);
                if (type == ReactionType.LIKE) {
                    // 被投花经验（F-07-04）：给帖作者 +2，匿名帖不计；
                    // 取消再投不会重复发——经验唯一键按帖子幂等
                    if (!isAnonymous(thread)) {
                        expService.award(thread.getAuthorId(), ExpService.EVENT_LIKE_GIVEN,
                                properties.getExpLikeGiven(), threadId);
                    }
                }
            }
        } else if (reactionMapper.deletePhysical(actor.userId(), TARGET_THREAD, threadId, type.name()) > 0) {
            changeCounter(threadId, type, -1);
        }
        return state(threadId, actor);
    }

    @Override
    public ReactionStateVO state(Long threadId, ForumActor actor) {
        Set<String> mine = myReactionTypes(threadId, actor.userId());
        ForumThread thread = threadMapper.selectById(threadId);
        ReactionStateVO vo = new ReactionStateVO();
        vo.setLiked(mine.contains(ReactionType.LIKE.name()));
        vo.setCollected(mine.contains(ReactionType.COLLECT.name()));
        vo.setLikeCount(thread == null || thread.getLikeCount() == null ? 0 : thread.getLikeCount());
        vo.setCollectCount(thread == null || thread.getCollectCount() == null ? 0 : thread.getCollectCount());
        return vo;
    }

    @Override
    public Set<String> myReactionTypes(Long threadId, Long userId) {
        // 读侧复用 ReactionStateReader：帖子服务也要读同一份状态，独立成 Bean 才不会互相注入成环
        return reactionStateReader.typesOf(threadId, userId);
    }

    /**
     * 解析互动类型（大小写不敏感，非法值按参数错误返回）。
     *
     * @param reactionType 原始类型文本
     * @return 互动类型枚举
     */
    private ReactionType parseType(String reactionType) {
        String normalized = reactionType == null ? "" : reactionType.trim().toUpperCase();
        try {
            return ReactionType.valueOf(normalized);
        } catch (IllegalArgumentException e) {
            throw new BizException(ErrorCode.PARAM_ERROR, "互动类型仅支持 LIKE（投花）或 COLLECT（藏帖）");
        }
    }

    /**
     * 互动固定窗口限流（防脚本刷投花；计数走 DB，多实例口径一致）。
     *
     * @param userId 用户ID
     */
    private void assertRate(Long userId) {
        int max = properties.getReactionWindowMax();
        if (max <= 0) {
            return;
        }
        Long recent = reactionMapper.selectCount(new QueryWrapper<ForumReaction>()
                .eq("user_id", userId)
                .ge("create_time", LocalDateTime.now().minusSeconds(properties.getReactionWindowSeconds())));
        if (recent != null && recent >= max) {
            throw new BizException(ForumErrorCodes.RATE_LIMITED,
                    "操作过于频繁，请稍后再试");
        }
    }

    /**
     * 增减帖子上的计数（下限 0）。
     *
     * @param threadId 帖子ID
     * @param type 互动类型
     * @param delta 增量（+1 / -1）
     */
    private void changeCounter(Long threadId, ReactionType type, int delta) {
        if (type == ReactionType.LIKE) {
            threadMapper.changeLikeCount(threadId, delta);
        } else {
            threadMapper.changeCollectCount(threadId, delta);
        }
    }

    /**
     * 匿名帖不计经验（F-07-04 硬规则）。
     *
     * @param thread 帖子
     * @return 是否匿名
     */
    private boolean isAnonymous(ForumThread thread) {
        return thread.getAnonymous() != null && thread.getAnonymous() == 1;
    }
}
