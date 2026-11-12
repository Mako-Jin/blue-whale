package com.bluewhale.forum.service.impl;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.tenant.TenantContext;
import com.bluewhale.forum.common.ForumActor;
import com.bluewhale.forum.common.ForumErrorCodes;
import com.bluewhale.forum.common.ForumPermissions;
import com.bluewhale.forum.config.ForumProperties;
import com.bluewhale.forum.entity.ForumPost;
import com.bluewhale.forum.entity.ForumThread;
import com.bluewhale.forum.mapper.ForumPostMapper;
import com.bluewhale.forum.mapper.ForumThreadMapper;
import com.bluewhale.forum.service.ExpService;
import com.bluewhale.forum.service.FollowService;
import com.bluewhale.forum.service.ForumNotifyService;
import com.bluewhale.forum.service.ModerationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 审核台单测：驳回必填意见、状态机不可回退、通过后计数与轨迹。
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
class AuditServiceImplTest {

    @Mock
    private ForumThreadMapper threadMapper;

    @Mock
    private ForumPostMapper postMapper;

    @Mock
    private ModerationService moderationService;

    @Mock
    private ForumNotifyService notifyService;

    @Mock
    private ExpService expService;

    @Mock
    private FollowService followService;

    @Spy
    private ForumProperties properties = new ForumProperties();

    @InjectMocks
    private AuditServiceImpl service;

    @BeforeEach
    void setUp() {
        TenantContext.set(1L);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("驳回评论为空：返回 30020，不改任何状态")
    void rejectWithoutRemarkIsRejected() {
        assertThatThrownBy(() -> service.reject("THREAD", 11L, "  ", admin()))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getCode())
                        .isEqualTo(ForumErrorCodes.AUDIT_REMARK_REQUIRED));
        verify(threadMapper, never()).updateById(any(ForumThread.class));
    }

    @Test
    @DisplayName("审核已发布帖：返回 30062，不允许把已发布内容重审")
    void passPublishedThreadIsRejected() {
        ForumThread thread = thread("PUBLISHED");
        when(threadMapper.selectById(11L)).thenReturn(thread);

        assertThatThrownBy(() -> service.pass("THREAD", 11L, null, admin()))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getCode())
                        .isEqualTo(ForumErrorCodes.THREAD_STATE_ILLEGAL));
        verify(threadMapper, never()).updateById(any(ForumThread.class));
    }

    @Test
    @DisplayName("审核通过待审帖：置 PUBLISHED 并写发布时间与轨迹")
    void passPendingThreadPublishes() {
        ForumThread thread = thread("PENDING");
        when(threadMapper.selectById(11L)).thenReturn(thread);
        when(moderationService.record(anyString(), anyLong(), anyString(), any(), anyLong())).thenReturn(99L);

        service.pass("THREAD", 11L, "内容合规", admin());

        assertThat(thread.getStatus()).isEqualTo("PUBLISHED");
        assertThat(thread.getPublishTime()).isNotNull();
        verify(threadMapper).updateById(thread);
        verify(moderationService).record(eq("THREAD"), eq(11L), eq("AUDIT_PASS"), eq("内容合规"), eq(900L));
    }

    @Test
    @DisplayName("驳回待审帖：置 REJECTED、留痕并通知作者")
    void rejectPendingThreadNotifiesAuthor() {
        ForumThread thread = thread("PENDING");
        when(threadMapper.selectById(11L)).thenReturn(thread);
        when(moderationService.record(anyString(), anyLong(), anyString(), any(), anyLong())).thenReturn(99L);

        service.reject("THREAD", 11L, "含联系方式", admin());

        assertThat(thread.getStatus()).isEqualTo("REJECTED");
        // 第一个参数是「被通知人」= 作者 100，不是帖子ID
        verify(notifyService).notifyModeration(eq(100L), eq("THREAD"), eq(11L),
                eq("AUDIT_REJECT"), eq("含联系方式"), eq(99L));
    }

    @Test
    @DisplayName("楼层通过：置 PUBLISHED 并补回回复数（待审时未计入）")
    void passPendingPostIncreasesReplyCount() {
        ForumPost post = new ForumPost();
        post.setId(21L);
        post.setThreadId(11L);
        post.setAuthorId(100L);
        post.setStatus("PENDING");
        when(postMapper.selectById(21L)).thenReturn(post);
        when(moderationService.record(anyString(), anyLong(), anyString(), any(), anyLong())).thenReturn(99L);

        service.pass("POST", 21L, null, admin());

        assertThat(post.getStatus()).isEqualTo("PUBLISHED");
        verify(threadMapper).increaseReplyCount(eq(11L), eq(100L), any());
    }

    /**
     * 构造帖子。
     *
     * @param status 状态
     * @return 帖子
     */
    private ForumThread thread(String status) {
        ForumThread thread = new ForumThread();
        thread.setId(11L);
        thread.setTenantId(1L);
        thread.setAuthorId(100L);
        thread.setBoardId(9L);
        thread.setTitle("待审帖");
        thread.setContent("<p>正文</p>");
        thread.setStatus(status);
        return thread;
    }

    /**
     * 构造审核员。
     *
     * @return 操作者
     */
    private ForumActor admin() {
        return new ForumActor(900L, Set.of(ForumPermissions.BROWSE, ForumPermissions.MANAGE_AUDIT), null);
    }
}
