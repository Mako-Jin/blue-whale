package com.bluewhale.forum.service.impl;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.forum.common.ForumErrorCodes;
import com.bluewhale.forum.entity.ForumPost;
import com.bluewhale.forum.entity.ForumThread;
import com.bluewhale.forum.mapper.ForumPostMapper;
import com.bluewhale.forum.mapper.ForumThreadMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 楼层写入器：把「分配楼层号 + 插楼层 + 计数」放进一个独立事务。
 *
 * <p><b>为什么必须同事务</b>：分配是 {@code next_floor + 1}，插入带唯一键
 * {@code uk_fm_post_floor}。两者同事务时，万一插入失败（内容超长、唯一键撞），
 * 自增一起回滚，楼层号保持连续；若拆成两个事务，失败那次的自增回不去，
 * 「并发 50 条无跳号」这条验收永远过不了。</p>
 *
 * <p><b>为什么不再重试</b>：分配用单语句 {@code LAST_INSERT_ID(expr)} 原子完成，
 * 不存在「读到旧值后写失败」的窗口，因此无需 CAS 重试循环。</p>
 *
 * @author BlueWhale
 */
@Component
@RequiredArgsConstructor
public class ForumPostWriter {

    private final ForumThreadMapper threadMapper;

    private final ForumPostMapper postMapper;

    /**
     * 在同一事务内分配楼层号并落库。
     *
     * @param threadId 帖子ID
     * @param authorId 作者ID
     * @param content 已消毒正文
     * @param replyToPostId 引用楼层ID（可空）
     * @param maxPostsPerThread 单帖楼层上限（含楼主占用的 1 楼）
     * @param status 初始状态：PUBLISHED 直接可见 / PENDING 待审（不计入回复数）
     * @return 落库后的楼层
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public ForumPost append(Long threadId, Long authorId, String content, Long replyToPostId,
                            int maxPostsPerThread, String status) {
        ForumThread thread = threadMapper.selectById(threadId);
        if (thread == null) {
            throw new BizException(ForumErrorCodes.THREAD_NOT_FOUND, "帖子不存在或已删除");
        }
        if (threadMapper.allocateFloor(threadId) != 1) {
            throw new BizException(ForumErrorCodes.THREAD_NOT_FOUND, "帖子不存在或已删除");
        }
        long allocated = threadMapper.lastAllocatedFloor();
        if (allocated > maxPostsPerThread) {
            // 已分配的号随事务回滚释放，不会留下空洞
            throw new BizException(ForumErrorCodes.FLOOR_LIMIT_REACHED,
                    "本帖楼层已达上限（" + maxPostsPerThread + "），请另开新帖");
        }
        int floor = (int) allocated;
        ForumPost post = new ForumPost();
        post.setTenantId(thread.getTenantId());
        post.setThreadId(threadId);
        post.setFloor(floor);
        post.setReplyToPostId(replyToPostId);
        post.setAuthorId(authorId);
        post.setAnonymous(0);
        post.setContent(content);
        post.setLikeCount(0);
        post.setStatus(status == null ? "PUBLISHED" : status);
        postMapper.insert(post);
        if (!"PENDING".equals(post.getStatus())) {
            // 待审楼层不计入回复数，审核通过时再 +1——否则「回复数」会把看不到的楼层算进去
            threadMapper.increaseReplyCount(threadId, authorId, LocalDateTime.now());
        }
        return post;
    }
}
