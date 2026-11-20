package com.bluewhale.forum.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.tenant.TenantContext;
import com.bluewhale.forum.common.ForumActor;
import com.bluewhale.forum.config.ForumProperties;
import com.bluewhale.forum.dto.FollowStateVO;
import com.bluewhale.forum.dto.FollowVO;
import com.bluewhale.forum.entity.ForumFollow;
import com.bluewhale.forum.mapper.ForumFollowMapper;
import com.bluewhale.forum.service.BoardService;
import com.bluewhale.forum.service.FollowService;
import com.bluewhale.forum.service.ForumNotifyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 结契（关注）服务实现。
 *
 * <p>关注对象合法性在写入口一次性校验：版块必须可见，作者不能是自己。
 * 不反查 auth 用户目录——关注不是强一致场景，改由「通知发不出去」自然消解。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FollowServiceImpl implements FollowService {

    /** outbox 业务类型：关注对象有新帖 */
    private static final String BIZ_FOLLOW = "FOLLOW";

    private final ForumFollowMapper followMapper;

    private final BoardService boardService;

    private final ForumNotifyService notifyService;

    private final ForumProperties properties;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FollowStateVO follow(String targetType, Long targetId, ForumActor actor) {
        String type = normalizeType(targetType);
        assertTarget(type, targetId, actor);
        ForumFollow follow = new ForumFollow();
        follow.setId(IdWorker.getId());
        follow.setTenantId(TenantContext.requireId());
        follow.setUserId(actor.userId());
        follow.setTargetType(type);
        follow.setTargetId(targetId);
        follow.setCreateTime(LocalDateTime.now());
        // 已关注再点不报错：唯一键兜住并发与连点
        followMapper.insertIgnore(follow);
        return stateOf(true);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FollowStateVO unfollow(String targetType, Long targetId, ForumActor actor) {
        String type = normalizeType(targetType);
        followMapper.delete(new LambdaQueryWrapper<ForumFollow>()
                .eq(ForumFollow::getUserId, actor.userId())
                .eq(ForumFollow::getTargetType, type)
                .eq(ForumFollow::getTargetId, targetId));
        return stateOf(false);
    }

    @Override
    public FollowStateVO state(String targetType, Long targetId, ForumActor actor) {
        String type = normalizeType(targetType);
        long count = followMapper.selectCount(new LambdaQueryWrapper<ForumFollow>()
                .eq(ForumFollow::getUserId, actor.userId())
                .eq(ForumFollow::getTargetType, type)
                .eq(ForumFollow::getTargetId, targetId));
        return stateOf(count > 0);
    }

    @Override
    public PageResult<FollowVO> myFollows(String targetType, long current, long size, ForumActor actor) {
        long pageNo = current < 1 ? 1 : current;
        String type = targetType == null || targetType.isBlank() ? null : normalizeType(targetType);
        LambdaQueryWrapper<ForumFollow> wrapper = new LambdaQueryWrapper<ForumFollow>()
                .eq(ForumFollow::getUserId, actor.userId())
                .eq(type != null, ForumFollow::getTargetType, type)
                .orderByDesc(ForumFollow::getCreateTime)
                .orderByDesc(ForumFollow::getId);
        Page<ForumFollow> page = followMapper.selectPage(new Page<>(pageNo, normalizeSize(size)), wrapper);
        List<FollowVO> records = page.getRecords().stream().map(this::toVO).toList();
        return PageResult.of(records, page.getTotal(), pageNo, normalizeSize(size));
    }

    @Override
    public int notifyNewThread(Long threadId, Long boardId, Long authorId, String title, Integer anonymous) {
        if (threadId == null || boardId == null || authorId == null) {
            return 0;
        }
        Long tenantId = TenantContext.requireId();
        int limit = properties.getFollowNotifyLimit();
        List<Long> receivers = anonymous != null && anonymous == 1
                ? followMapper.selectBoardFollowerIds(tenantId, boardId, limit)
                : followMapper.selectFollowerIds(tenantId, authorId, boardId, limit);
        if (receivers.isEmpty()) {
            return 0;
        }
        String safeTitle = title == null ? "新帖" : title;
        int sent = 0;
        for (Long receiverId : receivers) {
            notifyService.enqueue(receiverId, BIZ_FOLLOW, threadId,
                    "forum:follow:thread:" + threadId + ":" + receiverId,
                    "你关注的对象有新帖", "《" + safeTitle + "》已发布，点击查看。");
            sent++;
        }
        log.debug("关注新帖通知：threadId={}, receivers={}", threadId, sent);
        return sent;
    }

    /**
     * 校验并归一化对象类型。
     *
     * @param targetType 原始类型
     * @return USER / BOARD
     */
    private String normalizeType(String targetType) {
        String type = targetType == null ? "" : targetType.trim().toUpperCase();
        if (!TARGET_USER.equals(type) && !TARGET_BOARD.equals(type)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "forum.follow.type-invalid", "关注类型仅支持 USER（作者）或 BOARD（版块）");
        }
        return type;
    }

    /**
     * 校验关注对象：版块必须可见，作者不能是自己。
     *
     * @param type 归一化类型
     * @param targetId 对象ID
     * @param actor 当前操作者
     */
    private void assertTarget(String type, Long targetId, ForumActor actor) {
        if (targetId == null) {
            throw new BizException(ErrorCode.PARAM_ERROR, "forum.follow.target-required", "关注对象ID不能为空");
        }
        if (TARGET_USER.equals(type)) {
            if (targetId.equals(actor.userId())) {
                throw new BizException(ErrorCode.PARAM_ERROR, "forum.follow.self-forbidden", "不能关注自己");
            }
            return;
        }
        // 不可见与不存在同样 30001，不泄露版块存在性
        boardService.requireVisibleBoard(targetId, actor);
    }

    /**
     * 构造关注态。
     *
     * @param following 是否已关注
     * @return 关注态
     */
    private FollowStateVO stateOf(boolean following) {
        FollowStateVO vo = new FollowStateVO();
        vo.setFollowing(following);
        return vo;
    }

    /**
     * 实体转视图。
     *
     * @param follow 关注关系
     * @return 视图
     */
    private FollowVO toVO(ForumFollow follow) {
        FollowVO vo = new FollowVO();
        vo.setTargetType(follow.getTargetType());
        vo.setTargetId(follow.getTargetId());
        vo.setCreateTime(follow.getCreateTime());
        return vo;
    }

    /**
     * 归一化分页条数。
     *
     * @param size 原始条数
     * @return 合法条数
     */
    private long normalizeSize(long size) {
        if (size < 1) {
            return properties.getPageSizeDefault();
        }
        return Math.min(size, properties.getPageSizeMax());
    }
}
