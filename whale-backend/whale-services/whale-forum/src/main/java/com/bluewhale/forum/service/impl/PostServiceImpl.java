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
import com.bluewhale.forum.dto.MyPostVO;
import com.bluewhale.forum.dto.PostCreateRequest;
import com.bluewhale.forum.dto.PostVO;
import com.bluewhale.forum.dto.QuotedPostVO;
import com.bluewhale.forum.entity.ForumPost;
import com.bluewhale.forum.entity.ForumThread;
import com.bluewhale.forum.mapper.ForumPostMapper;
import com.bluewhale.forum.mapper.ForumThreadMapper;
import com.bluewhale.forum.entity.ForumBoard;
import com.bluewhale.forum.service.BanService;
import com.bluewhale.forum.service.BoardService;
import com.bluewhale.forum.service.ForumNotifyService;
import com.bluewhale.forum.service.MentionService;
import com.bluewhale.forum.service.PostService;
import com.bluewhale.forum.service.SensitiveWordService;
import com.bluewhale.forum.service.ThreadService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

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

    /** 楼层正序 */
    private static final String ORDER_ASC = "asc";

    /** 楼层倒序 */
    private static final String ORDER_DESC = "desc";

    private final ForumPostMapper postMapper;

    private final ForumPostWriter postWriter;

    private final ThreadService threadService;

    private final BoardService boardService;

    private final ForumProperties properties;

    private final ForumThreadMapper threadMapper;

    private final SensitiveWordService sensitiveWordService;

    private final BanService banService;

    private final ForumNotifyService notifyService;

    private final MentionService mentionService;

    @Override
    public PostVO create(Long threadId, PostCreateRequest request, ForumActor actor) {
        TenantContext.requireId();
        ForumThread thread = threadService.requireVisibleThread(threadId, actor);
        ForumBoard board = boardService.requireVisibleBoard(thread.getBoardId(), actor);
        banService.assertNotMuted(actor.userId());
        if (!REPLYABLE_STATUSES.contains(thread.getStatus())) {
            throw new BizException(ForumErrorCodes.THREAD_STATE_ILLEGAL, "forum.post.not-replyable",
                    "帖子当前状态不接受回复（可能已关闭或审核中）");
        }
        String content = RichTextSanitizer.sanitize(request.getContent());
        if (content == null || content.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "forum.post.content-required", "回复内容不能为空");
        }
        if (content.length() > properties.getMaxContentLength()) {
            throw new BizException(ForumErrorCodes.CONTENT_TOO_LONG, "forum.post.content-too-long",
                    "回复不超过 " + properties.getMaxContentLength() + " 字符",
                    properties.getMaxContentLength());
        }
        assertReplyRate(actor.userId());

        SensitiveWordService.ScanResult scan = sensitiveWordService.scan(content);
        if (scan.hit()) {
            sensitiveWordService.recordHit("POST", null, actor.userId(), scan);
        }
        if (SensitiveWordService.ScanResult.BLOCK.equals(scan.action())) {
            throw new BizException(ForumErrorCodes.SENSITIVE_BLOCKED, "forum.post.sensitive-blocked",
                    "回复包含不允许发布的词，请修改后重试");
        }
        content = scan.text();
        // 审核版块或命中「转待审」词：楼层先落 PENDING，不计入回复数，通过后再 +1
        String status = (board.getAuditMode() != null && board.getAuditMode() == 1)
                || SensitiveWordService.ScanResult.PENDING.equals(scan.action())
                ? "PENDING" : "PUBLISHED";

        try {
            ForumPost post = postWriter.append(threadId, actor.userId(), content,
                    request.getReplyToPostId(), properties.getMaxPostsPerThread(), status);
            return finish(post, thread, actor);
        } catch (DuplicateKeyException e) {
            // 正常路径不会走到这里：唯一键只可能在 next_floor 被人工改坏时撞上。
            // 按现有楼层重算一次即可自愈，再失败说明数据已不可信，如实报错。
            threadMapper.syncNextFloor(threadId);
            try {
                ForumPost post = postWriter.append(threadId, actor.userId(), content,
                        request.getReplyToPostId(), properties.getMaxPostsPerThread(), status);
                return finish(post, thread, actor);
            } catch (DuplicateKeyException retryFailure) {
                throw new BizException(ForumErrorCodes.FLOOR_CONFLICT, "forum.post.floor-conflict",
                        "楼层号分配冲突，请联系管理员重算楼层（本次未占用楼层）");
            }
        }
    }

    @Override
    public PageResult<PostVO> page(Long threadId, Long current, Long size, ForumActor actor) {
        return page(threadId, current, size, ORDER_ASC, actor);
    }

    @Override
    public PageResult<PostVO> page(Long threadId, Long current, Long size, String order, ForumActor actor) {
        TenantContext.requireId();
        ForumThread thread = threadService.requireVisibleThread(threadId, actor);
        boardService.requireVisibleBoard(thread.getBoardId(), actor);
        long pageNo = current == null || current < 1 ? 1 : current;
        long pageSize = size == null || size < 1
                ? properties.getPageSizeDefault()
                : Math.min(size, properties.getPageSizeMax());
        boolean desc = isDescending(order);
        IPage<ForumPost> page = postMapper.selectPage(
                new Page<>(Math.min(pageNo, properties.getMaxPageNumber()), pageSize),
                Wrappers.<ForumPost>lambdaQuery()
                        .eq(ForumPost::getThreadId, threadId)
                        .eq(ForumPost::getStatus, "PUBLISHED")
                        .isNotNull(ForumPost::getFloor)
                        .orderByAsc(!desc, ForumPost::getFloor)
                        .orderByDesc(desc, ForumPost::getFloor));
        List<PostVO> records = new ArrayList<>(page.getRecords().size());
        for (ForumPost post : page.getRecords()) {
            records.add(toVO(post));
        }
        fillQuoted(records);
        return PageResult.of(records, page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    public PageResult<MyPostVO> myPosts(long current, long size, ForumActor actor) {
        Long tenantId = TenantContext.requireId();
        long pageNo = current < 1 ? 1 : Math.min(current, properties.getMaxPageNumber());
        long pageSize = size < 1 ? properties.getPageSizeDefault() : Math.min(size, properties.getPageSizeMax());
        long total = postMapper.countMyPosts(tenantId, actor.userId());
        if (total == 0) {
            return PageResult.of(List.of(), 0, pageNo, pageSize);
        }
        List<MyPostVO> rows = postMapper.selectMyPosts(tenantId, actor.userId(),
                (pageNo - 1) * pageSize, pageSize);
        for (MyPostVO row : rows) {
            row.setSummary(RichTextSanitizer.summarize(row.getContent(), 200));
            // 原文不进响应：列表只需要一眼能看出回了什么
            row.setContent(null);
        }
        return PageResult.of(rows, total, pageNo, pageSize);
    }

    /**
     * 判断是否倒序。
     *
     * <p>只有明确写 {@code desc} 才倒序，其余（含空、拼错、注入串）一律正序：
     * 排序方向不接受「猜」，免得 {@code order=;DROP} 之类的东西被当成有效值往 SQL 里带。</p>
     *
     * @param order 排序参数
     * @return 倒序返回 {@code true}
     */
    static boolean isDescending(String order) {
        return ORDER_DESC.equalsIgnoreCase(order == null ? null : order.trim());
    }

    /**
     * 批量回填被引用楼层摘要。
     *
     * <p>一次 {@code selectBatchIds} 解决整页的引用，不做 N+1；
     * 软删过滤由 {@code @TableLogic} 在 SQL 层完成，查不到即视为「已删除」，
     * 待审楼层同理（{@code status != PUBLISHED} 也不返回摘要）。</p>
     *
     * @param records 当前页楼层视图
     */
    private void fillQuoted(List<PostVO> records) {
        Set<Long> quotedIds = records.stream()
                .map(PostVO::getReplyToPostId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (quotedIds.isEmpty()) {
            return;
        }
        Map<Long, ForumPost> quotedPosts = postMapper.selectBatchIds(quotedIds).stream()
                .collect(Collectors.toMap(ForumPost::getId, post -> post, (left, right) -> left));
        for (PostVO vo : records) {
            if (vo.getReplyToPostId() == null) {
                continue;
            }
            ForumPost source = quotedPosts.get(vo.getReplyToPostId());
            QuotedPostVO quoted = new QuotedPostVO();
            quoted.setPostId(vo.getReplyToPostId());
            if (source == null || !"PUBLISHED".equals(source.getStatus())) {
                quoted.setDeleted(true);
            } else {
                quoted.setDeleted(false);
                quoted.setFloor(source.getFloor());
                quoted.setAuthorId(source.getAuthorId());
                quoted.setExcerpt(RichTextSanitizer.summarize(source.getContent(),
                        properties.getQuoteExcerptLength()));
            }
            vo.setQuoted(quoted);
        }
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
            throw new BizException(ForumErrorCodes.RATE_LIMITED, "forum.post.reply-too-frequent",
                    "回复过于频繁，请 " + properties.getReplyIntervalSeconds() + " 秒后再试",
                    properties.getReplyIntervalSeconds());
        }
    }

    /**
     * 回帖收尾：给楼主发「被回复」通知（自己回自己不通知；待审楼层等通过后才通知）。
     *
     * @param post 已落库楼层
     * @param thread 所属帖子
     * @param actor 回复人
     * @return 视图
     */
    private PostVO finish(ForumPost post, ForumThread thread, ForumActor actor) {
        if ("PUBLISHED".equals(post.getStatus())) {
            if (!actor.userId().equals(thread.getAuthorId())) {
                notifyService.notifyReply(thread.getId(), post.getId(), thread.getTitle(),
                        thread.getAuthorId(), "有鲸友");
            }
            // 待审楼层不派发 @：楼层列表看不到待审内容，先通知等于通知了一个「点进去没有」的链接
            mentionService.dispatch(thread.getId(), post.getId(), MentionService.SOURCE_POST,
                    post.getContent(), thread.getTitle(), actor.userId());
        }
        return toVO(post);
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
        vo.setStatus(post.getStatus());
        vo.setCreateTime(post.getCreateTime());
        return vo;
    }
}
