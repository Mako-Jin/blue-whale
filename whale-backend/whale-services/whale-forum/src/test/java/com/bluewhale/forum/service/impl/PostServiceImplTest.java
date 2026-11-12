package com.bluewhale.forum.service.impl;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.tenant.TenantContext;
import com.bluewhale.forum.common.ForumActor;
import com.bluewhale.forum.common.ForumErrorCodes;
import com.bluewhale.forum.common.ForumPermissions;
import com.bluewhale.forum.config.ForumProperties;
import com.bluewhale.forum.dto.PostCreateRequest;
import com.bluewhale.forum.dto.PostVO;
import com.bluewhale.forum.entity.ForumBoard;
import com.bluewhale.forum.entity.ForumPost;
import com.bluewhale.forum.entity.ForumThread;
import com.bluewhale.forum.mapper.ForumPostMapper;
import com.bluewhale.forum.mapper.ForumThreadMapper;
import com.bluewhale.forum.service.BoardService;
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
import org.springframework.dao.DuplicateKeyException;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 楼层服务单测：唯一键自愈与状态准入。
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
class PostServiceImplTest {

    @Mock
    private ForumPostMapper postMapper;

    @Mock
    private ForumThreadMapper threadMapper;

    @Mock
    private ForumPostWriter postWriter;

    @Mock
    private ThreadService threadService;

    @Mock
    private BoardService boardService;

    @Spy
    private ForumProperties properties = new ForumProperties();

    @InjectMocks
    private PostServiceImpl postService;

    @BeforeEach
    void setUp() {
        TenantContext.set(1L);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("一次成功：正常路径不重算、不重试")
    void succeedsOnFirstAttempt() {
        when(threadService.requireVisibleThread(anyLong(), any())).thenReturn(thread("PUBLISHED"));
        when(boardService.requireVisibleBoard(anyLong(), any())).thenReturn(new ForumBoard());
        when(postMapper.selectCount(any())).thenReturn(0L);
        when(postWriter.append(anyLong(), anyLong(), anyString(), any(), anyInt())).thenReturn(post(7));

        PostVO vo = postService.create(11L, request("回复内容"), author());

        assertThat(vo.getFloor()).isEqualTo(7);
        verify(postWriter, times(1)).append(anyLong(), anyLong(), anyString(), any(), anyInt());
        verify(threadMapper, never()).syncNextFloor(anyLong());
    }

    @Test
    @DisplayName("唯一键撞上一次后重算楼层并自愈成功")
    void healsOnceWhenUniqueKeyConflicts() {
        when(threadService.requireVisibleThread(anyLong(), any())).thenReturn(thread("PUBLISHED"));
        when(boardService.requireVisibleBoard(anyLong(), any())).thenReturn(new ForumBoard());
        when(postMapper.selectCount(any())).thenReturn(0L);
        when(postWriter.append(anyLong(), anyLong(), anyString(), any(), anyInt()))
                .thenThrow(new DuplicateKeyException("uk_fm_post_floor"))
                .thenReturn(post(8));

        PostVO vo = postService.create(11L, request("回复内容"), author());

        assertThat(vo.getFloor()).isEqualTo(8);
        verify(threadMapper, times(1)).syncNextFloor(11L);
        verify(postWriter, times(2)).append(anyLong(), anyLong(), anyString(), any(), anyInt());
    }

    @Test
    @DisplayName("自愈后仍撞键返回 30010，且只试两次")
    void reportsConflictWhenHealFails() {
        when(threadService.requireVisibleThread(anyLong(), any())).thenReturn(thread("PUBLISHED"));
        when(boardService.requireVisibleBoard(anyLong(), any())).thenReturn(new ForumBoard());
        when(postMapper.selectCount(any())).thenReturn(0L);
        when(postWriter.append(anyLong(), anyLong(), anyString(), any(), anyInt()))
                .thenThrow(new DuplicateKeyException("uk_fm_post_floor"));

        assertThatThrownBy(() -> postService.create(11L, request("回复内容"), author()))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getCode())
                        .isEqualTo(ForumErrorCodes.FLOOR_CONFLICT));
        verify(postWriter, times(2)).append(anyLong(), anyLong(), anyString(), any(), anyInt());
    }

    @Test
    @DisplayName("已关闭帖子回帖返回 30062，且不进重试")
    void closedThreadRejectsReply() {
        when(threadService.requireVisibleThread(anyLong(), any())).thenReturn(thread("CLOSED"));
        when(boardService.requireVisibleBoard(anyLong(), any())).thenReturn(new ForumBoard());

        assertThatThrownBy(() -> postService.create(11L, request("回复内容"), author()))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getCode())
                        .isEqualTo(ForumErrorCodes.THREAD_STATE_ILLEGAL));
        verify(postWriter, times(0)).append(anyLong(), anyLong(), anyString(), any(), anyInt());
    }

    @Test
    @DisplayName("回帖间隔未到返回 30042")
    void replyIntervalLimited() {
        when(threadService.requireVisibleThread(anyLong(), any())).thenReturn(thread("PUBLISHED"));
        when(boardService.requireVisibleBoard(anyLong(), any())).thenReturn(new ForumBoard());
        when(postMapper.selectCount(any())).thenReturn(1L);

        assertThatThrownBy(() -> postService.create(11L, request("回复内容"), author()))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getCode())
                        .isEqualTo(ForumErrorCodes.RATE_LIMITED));
    }

    /**
     * 构造帖子。
     *
     * @param status 状态
     * @return 帖子实体
     */
    private ForumThread thread(String status) {
        ForumThread thread = new ForumThread();
        thread.setId(11L);
        thread.setTenantId(1L);
        thread.setBoardId(9L);
        thread.setStatus(status);
        thread.setAuthorId(100L);
        thread.setNextFloor(2);
        return thread;
    }

    /**
     * 构造回帖请求。
     *
     * @param content 正文
     * @return 请求
     */
    private PostCreateRequest request(String content) {
        PostCreateRequest request = new PostCreateRequest();
        request.setContent(content);
        return request;
    }

    /**
     * 构造楼层。
     *
     * @param floor 楼层号
     * @return 楼层实体
     */
    private ForumPost post(int floor) {
        ForumPost post = new ForumPost();
        post.setId(1000L);
        post.setThreadId(11L);
        post.setFloor(floor);
        post.setAuthorId(100L);
        post.setContent("回复内容");
        post.setLikeCount(0);
        post.setStatus("PUBLISHED");
        return post;
    }

    /**
     * 构造操作者。
     *
     * @return 操作者
     */
    private ForumActor author() {
        return new ForumActor(100L, Set.of(ForumPermissions.BROWSE, ForumPermissions.POST_ADD), null);
    }
}
