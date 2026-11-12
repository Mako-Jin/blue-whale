package com.bluewhale.forum.service.impl;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.tenant.TenantContext;
import com.bluewhale.forum.common.ForumActor;
import com.bluewhale.forum.common.ForumErrorCodes;
import com.bluewhale.forum.common.ForumPermissions;
import com.bluewhale.forum.config.ForumProperties;
import com.bluewhale.forum.dto.ReactionStateVO;
import com.bluewhale.forum.entity.ForumReaction;
import com.bluewhale.forum.entity.ForumThread;
import com.bluewhale.forum.mapper.ForumReactionMapper;
import com.bluewhale.forum.mapper.ForumThreadMapper;
import com.bluewhale.forum.service.ExpService;
import com.bluewhale.forum.service.ThreadService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 互动服务单测：幂等（重复投花不重复计数）、取消可再投、限流与非法类型。
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
class ReactionServiceImplTest {

    @Mock
    private ForumReactionMapper reactionMapper;

    @Mock
    private ForumThreadMapper threadMapper;

    @Mock
    private ThreadService threadService;

    @Mock
    private ReactionStateReader reactionStateReader;

    @Mock
    private ExpService expService;

    @Spy
    private ForumProperties properties = new ForumProperties();

    @InjectMocks
    private ReactionServiceImpl reactionService;

    @BeforeEach
    void setUp() {
        TenantContext.set(1L);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("首次投花：插入成功后计数 +1 并回读")
    void firstLikeIncrementsCounter() {
        when(threadService.requireVisibleThread(11L, actor())).thenReturn(thread(6, 2));
        when(reactionMapper.selectCount(any())).thenReturn(0L);
        when(reactionMapper.insertIgnore(any())).thenReturn(1);
        when(reactionStateReader.typesOf(11L, 100L)).thenReturn(Set.of("LIKE"));
        when(threadMapper.selectById(11L)).thenReturn(thread(6, 2));

        ReactionStateVO vo = reactionService.toggle(11L, "like", true, actor());

        assertThat(vo.getLiked()).isTrue();
        assertThat(vo.getCollected()).isFalse();
        assertThat(vo.getLikeCount()).isEqualTo(6);
        verify(threadMapper).changeLikeCount(11L, 1);
    }

    @Test
    @DisplayName("重复投花：唯一键忽略插入，计数不动（幂等）")
    void duplicateLikeIsIdempotent() {
        when(threadService.requireVisibleThread(11L, actor())).thenReturn(thread(6, 2));
        when(reactionMapper.selectCount(any())).thenReturn(1L);
        when(reactionMapper.insertIgnore(any())).thenReturn(0);
        when(reactionStateReader.typesOf(11L, 100L)).thenReturn(Set.of("LIKE"));
        when(threadMapper.selectById(11L)).thenReturn(thread(6, 2));

        ReactionStateVO vo = reactionService.toggle(11L, "LIKE", true, actor());

        assertThat(vo.getLiked()).isTrue();
        verify(threadMapper, never()).changeLikeCount(anyLong(), anyInt());
    }

    @Test
    @DisplayName("取消藏帖：删除到行才把计数 -1")
    void cancelCollectDecrementsOnce() {
        when(threadService.requireVisibleThread(11L, actor())).thenReturn(thread(6, 1));
        when(reactionMapper.deletePhysical(100L, "THREAD", 11L, "COLLECT")).thenReturn(1);
        when(reactionStateReader.typesOf(11L, 100L)).thenReturn(Set.of());
        when(threadMapper.selectById(11L)).thenReturn(thread(6, 1));

        ReactionStateVO vo = reactionService.toggle(11L, "COLLECT", false, actor());

        assertThat(vo.getCollected()).isFalse();
        verify(threadMapper).changeCollectCount(11L, -1);
    }

    @Test
    @DisplayName("取消没投过的花：删除 0 行，计数不动")
    void cancelWithoutRecordDoesNothing() {
        when(threadService.requireVisibleThread(11L, actor())).thenReturn(thread(6, 1));
        when(reactionMapper.deletePhysical(100L, "THREAD", 11L, "LIKE")).thenReturn(0);
        when(reactionStateReader.typesOf(11L, 100L)).thenReturn(Set.of());
        when(threadMapper.selectById(11L)).thenReturn(thread(6, 1));

        reactionService.toggle(11L, "LIKE", false, actor());

        verify(threadMapper, never()).changeLikeCount(anyLong(), anyInt());
    }

    @Test
    @DisplayName("非法互动类型按参数错误拒绝")
    void unknownTypeRejected() {
        assertThatThrownBy(() -> reactionService.toggle(11L, "SHARE", true, actor()))
                .isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("超过互动窗口上限返回 30042")
    void reactionRateLimited() {
        when(threadService.requireVisibleThread(11L, actor())).thenReturn(thread(6, 1));
        when(reactionMapper.selectCount(any())).thenReturn(120L);

        assertThatThrownBy(() -> reactionService.toggle(11L, "LIKE", true, actor()))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getCode())
                        .isEqualTo(ForumErrorCodes.RATE_LIMITED));
    }

    /**
     * 构造操作者。
     *
     * @return 操作者
     */
    private ForumActor actor() {
        return new ForumActor(100L, Set.of(ForumPermissions.REACTION_USE), null);
    }

    /**
     * 构造帖子。
     *
     * @param likeCount 投花数
     * @param collectCount 收藏数
     * @return 帖子实体
     */
    private ForumThread thread(int likeCount, int collectCount) {
        ForumThread thread = new ForumThread();
        thread.setId(11L);
        thread.setTenantId(1L);
        thread.setBoardId(9L);
        thread.setAuthorId(100L);
        thread.setStatus("PUBLISHED");
        thread.setLikeCount(likeCount);
        thread.setCollectCount(collectCount);
        return thread;
    }

    /**
     * 构造互动记录。
     *
     * @param type 互动类型
     * @return 互动记录
     */
    private ForumReaction reaction(String type) {
        ForumReaction reaction = new ForumReaction();
        reaction.setUserId(100L);
        reaction.setTargetType("THREAD");
        reaction.setTargetId(11L);
        reaction.setReactionType(type);
        return reaction;
    }
}
