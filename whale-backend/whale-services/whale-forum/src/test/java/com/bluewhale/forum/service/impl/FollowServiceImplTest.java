package com.bluewhale.forum.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.tenant.TenantContext;
import com.bluewhale.forum.common.ForumActor;
import com.bluewhale.forum.common.ForumPermissions;
import com.bluewhale.forum.config.ForumProperties;
import com.bluewhale.forum.entity.ForumFollow;
import com.bluewhale.forum.mapper.ForumFollowMapper;
import com.bluewhale.forum.service.BoardService;
import com.bluewhale.forum.service.ForumNotifyService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 结契单测：自关注拒绝、版块可见性、幂等、关注列表与通知去重。
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
class FollowServiceImplTest {

    @Mock
    private ForumFollowMapper followMapper;

    @Mock
    private BoardService boardService;

    @Mock
    private ForumNotifyService notifyService;

    @Spy
    private ForumProperties properties = new ForumProperties();

    @InjectMocks
    private FollowServiceImpl followService;

    @BeforeEach
    void setUp() {
        TenantContext.set(1L);
        lenient().when(followMapper.insertIgnore(any(ForumFollow.class))).thenReturn(1);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("关注自己：400 拒绝，不落关系表")
    void rejectsSelfFollow() {
        assertThatThrownBy(() -> followService.follow("USER", 100L, actor()))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("不能关注自己");
        verify(followMapper, never()).insertIgnore(any(ForumFollow.class));
    }

    @Test
    @DisplayName("关注版块：先校验可见性，再写关系")
    void followBoardChecksVisibility() {
        followService.follow("board", 9L, actor());

        verify(boardService).requireVisibleBoard(eq(9L), any());
        ArgumentCaptor<ForumFollow> captor = ArgumentCaptor.forClass(ForumFollow.class);
        verify(followMapper).insertIgnore(captor.capture());
        ForumFollow saved = captor.getValue();
        assertThat(saved.getTenantId()).isEqualTo(1L);
        assertThat(saved.getUserId()).isEqualTo(100L);
        assertThat(saved.getTargetType()).isEqualTo("BOARD");
        assertThat(saved.getTargetId()).isEqualTo(9L);
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreateTime()).isNotNull();
    }

    @Test
    @DisplayName("重复关注：INSERT IGNORE 命中 0 行也不报错")
    void repeatedFollowIsIdempotent() {
        when(followMapper.insertIgnore(any(ForumFollow.class))).thenReturn(0);

        assertThat(followService.follow("USER", 200L, actor()).getFollowing()).isTrue();
    }

    @Test
    @DisplayName("非法关注类型：400 拒绝")
    void rejectsUnknownTargetType() {
        assertThatThrownBy(() -> followService.follow("POST", 11L, actor()))
                .isInstanceOf(BizException.class);
        verify(followMapper, never()).insertIgnore(any(ForumFollow.class));
    }

    @Test
    @DisplayName("取关：幂等删除并返回未关注")
    void unfollowAlwaysSucceeds() {
        assertThat(followService.unfollow("USER", 200L, actor()).getFollowing()).isFalse();
        verify(followMapper).delete(any(LambdaQueryWrapper.class));
    }

    @Test
    @DisplayName("我的关注：只回关注时间倒序的分页数据")
    void myFollowsMapsPage() {
        ForumFollow follow = new ForumFollow();
        follow.setTargetType("BOARD");
        follow.setTargetId(9L);
        follow.setCreateTime(LocalDateTime.of(2026, 11, 12, 10, 0));
        Page<ForumFollow> page = new Page<>(1, 20);
        page.setRecords(List.of(follow));
        page.setTotal(1);
        when(followMapper.selectPage(any(), any())).thenReturn(page);

        var result = followService.myFollows(null, 1, 20, actor());

        assertThat(result.getTotal()).isEqualTo(1);
        assertThat(result.getRecords()).hasSize(1);
        assertThat(result.getRecords().get(0).getTargetType()).isEqualTo("BOARD");
        assertThat(result.getRecords().get(0).getTargetId()).isEqualTo(9L);
    }

    @Test
    @DisplayName("新帖通知：作者与版块关注者各收到一条，幂等键带 threadId")
    void notifyFollowersOnNewThread() {
        when(followMapper.selectFollowerIds(1L, 100L, 9L, properties.getFollowNotifyLimit()))
                .thenReturn(List.of(7L, 8L));

        int sent = followService.notifyNewThread(11L, 9L, 100L, "新帖标题", 0);

        assertThat(sent).isEqualTo(2);
        verify(notifyService).enqueue(eq(7L), eq("FOLLOW"), eq(11L),
                eq("forum:follow:thread:11:7"), anyString(), anyString());
        verify(notifyService).enqueue(eq(8L), eq("FOLLOW"), eq(11L),
                eq("forum:follow:thread:11:8"), anyString(), anyString());
    }

    @Test
    @DisplayName("匿名新帖：只通知版块关注者，不碰作者关注者")
    void anonymousThreadSkipsAuthorFollowers() {
        when(followMapper.selectBoardFollowerIds(1L, 9L, properties.getFollowNotifyLimit()))
                .thenReturn(List.of(7L));

        int sent = followService.notifyNewThread(11L, 9L, 100L, "匿名帖", 1);

        assertThat(sent).isEqualTo(1);
        verify(followMapper, never()).selectFollowerIds(anyLong(), anyLong(), anyLong(), anyInt());
        verify(notifyService, times(1)).enqueue(eq(7L), eq("FOLLOW"), eq(11L),
                anyString(), anyString(), anyString());
    }

    /**
     * 构造操作者。
     *
     * @return 操作者
     */
    private ForumActor actor() {
        return new ForumActor(100L, Set.of(ForumPermissions.BROWSE, ForumPermissions.REACTION_USE), null);
    }
}
