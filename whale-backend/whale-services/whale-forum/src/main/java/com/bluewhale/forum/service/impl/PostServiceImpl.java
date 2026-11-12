package com.bluewhale.forum.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.tenant.TenantContext;
import com.bluewhale.forum.common.ForumActor;
import com.bluewhale.forum.common.ForumErrorCodes;
import com.bluewhale.forum.common.RichTextSanitizer;
import com.bluewhale.forum.config.ForumProperties;
import com.bluewhale.forum.dto.PostCreateRequest;
import com.bluewhale.forum.dto.PostVO;
import com.bluewhale.forum.entity.ForumPost;
import com.bluewhale.forum.entity.ForumThread;
import com.bluewhale.forum.mapper.ForumPostMapper;
import com.bluewhale.forum.mapper.ForumThreadMapper;
import com.bluewhale.forum.service.BoardService;
import com.bluewhale.forum.service.PostService;
import com.bluewhale.forum.service.ThreadService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 楼层服务实现：回帖与楼层分页。
 *
 * <p>楼层号由 {@code LAST_INSERT_ID(expr)} 单语句原子分配，正常路径零重试；
 * 仅当唯一键真的撞上（{@code next_floor} 被人工改坏）才按现有楼层重算一次。</p>
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class PostServiceImpl implements PostService {

    /** 允许回帖的状态（CLOSED 不接受回复） */
    private static final List<String> REPLYABLE_STATUSES = List.of("PUBLISHED", "SOLVED");

    private final ForumPostMapper postMapper;

    private final ForumPostWriter postWriter;

    private final ThreadService threadService;

    private final BoardService boardService;

    private final ForumProperties properties;

    private final ForumThreadMapper threadMapper;

    @Override
    public PostVO create(Long threadId, PostCreateRequest request, ForumActor actor) {
        TenantContext.requireId();
        ForumThread thread = threadService.requireVisibleThread(threadId, actor);
        boardService.requireVisibleBoard(thread.getBoardId(), actor);
        if (!REPLYABLE_STATUSES.contains(thread.getStatus())) {
            throw new BizException(ForumErrorCodes.THREAD_STATE_ILLEGAL,
                    "帖子当前状态不接受回复（可能已关闭或审核中）");
        }
        String content = RichTextSanitizer.sanitize(request.getContent());
        if (content == null || content.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "回复内容不能为空");
        }
        if (content.length() > properties.getMaxContentLength()) {
            throw new BizException(ForumErrorCodes.CONTENT_TOO_LONG,
                    "回复不超过 " + properties.getMaxContentLength() + " 字符");
        }
        assertReplyRate(actor.userId());

        try {
            ForumPost post = postWriter.append(threadId, actor.userId(), content,
                    request.getReplyToPostId(), properties.getMaxPostsPerThread());
            return toVO(post);
        } catch (DuplicateKeyException e) {
            // 正常路径不会走到这里：唯一键只可能在 next_floor 被人工改坏时撞上。
            // 按现有楼层重算一次即可自愈，再失败说明数据已不可信，如实报错。
            threadMapper.syncNextFloor(threadId);
            try {
                ForumPost post = postWriter.append(threadId, actor.userId(), content,
                        request.getReplyToPostId(), properties.getMaxPostsPerThread());
                return toVO(post);
            } catch (DuplicateKeyException retryFailure) {
                throw new BizException(ForumErrorCodes.FLOOR_CONFLICT,
                        "楼层号分配冲突，请联系管理员重算楼层（本次未占用楼层）");
            }
        }
    }

    @Override
    public PageResult<PostVO> page(Long threadId, Long current, Long size, ForumActor actor) {
        TenantContext.requireId();
        ForumThread thread = threadService.requireVisibleThread(threadId, actor);
        boardService.requireVisibleBoard(thread.getBoardId(), actor);
        long pageNo = current == null || current < 1 ? 1 : current;
        long pageSize = size == null || size < 1
                ? properties.getPageSizeDefault()
                : Math.min(size, properties.getPageSizeMax());
        IPage<ForumPost> page = postMapper.selectPage(
                new Page<>(Math.min(pageNo, properties.getMaxPageNumber()), pageSize),
                Wrappers.<ForumPost>lambdaQuery()
                        .eq(ForumPost::getThreadId, threadId)
                        .isNotNull(ForumPost::getFloor)
                        .orderByAsc(ForumPost::getFloor));
        List<PostVO> records = new ArrayList<>(page.getRecords().size());
        for (ForumPost post : page.getRecords()) {
            records.add(toVO(post));
        }
        return PageResult.of(records, page.getTotal(), page.getCurrent(), page.getSize());
    }

    /**
     * 回帖最小间隔（DB 计数，多实例一致）。
     *
     * @param userId 用户ID
     */
    private void assertReplyRate(Long userId) {
        Long recent = postMapper.selectCount(Wrappers.<ForumPost>lambdaQuery()
                .eq(ForumPost::getAuthorId, userId)
                .ge(ForumPost::getCreateTime,
                        LocalDateTime.now().minusSeconds(properties.getReplyIntervalSeconds())));
        if (recent != null && recent > 0) {
            throw new BizException(ForumErrorCodes.RATE_LIMITED,
                    "回复过于频繁，请 " + properties.getReplyIntervalSeconds() + " 秒后再试");
        }
    }

    /**
     * 实体转视图。
     *
     * @param post 楼层实体
     * @return 视图对象
     */
    private PostVO toVO(ForumPost post) {
        PostVO vo = new PostVO();
        vo.setId(post.getId());
        vo.setFloor(post.getFloor());
        vo.setAuthorId(post.getAuthorId());
        vo.setAnonymous(post.getAnonymous());
        vo.setAnonymousNo(post.getAnonymousNo());
        vo.setContent(post.getContent());
        vo.setLikeCount(post.getLikeCount());
        vo.setReplyToPostId(post.getReplyToPostId());
        vo.setCreateTime(post.getCreateTime());
        return vo;
    }
}
