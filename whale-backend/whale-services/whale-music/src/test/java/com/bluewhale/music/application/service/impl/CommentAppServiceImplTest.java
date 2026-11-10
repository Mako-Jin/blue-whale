package com.bluewhale.music.application.service.impl;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.tenant.TenantContext;
import com.bluewhale.music.application.command.CommentAuditQueryCommand;
import com.bluewhale.music.application.command.CommentSubmitCommand;
import com.bluewhale.music.application.view.CommentView;
import com.bluewhale.music.config.MusicProperties;
import com.bluewhale.music.domain.exception.MusicErrorCode;
import com.bluewhale.music.domain.model.CommentStatus;
import com.bluewhale.music.domain.model.CopyrightState;
import com.bluewhale.music.domain.model.PublishState;
import com.bluewhale.music.domain.model.Song;
import com.bluewhale.music.domain.model.SongComment;
import com.bluewhale.music.domain.model.SongVisibility;
import com.bluewhale.music.domain.repository.CommentNotificationPort;
import com.bluewhale.music.domain.repository.CommentRepository;
import com.bluewhale.music.domain.repository.SongRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 评论应用服务单元测试：发表 / 回复 / 分页可见性 / 审核通过与隐藏的计数流转。
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
class CommentAppServiceImplTest {

    /** 测试租户 */
    private static final Long TENANT_ID = 1L;

    /** 歌曲 ID */
    private static final Long SONG_ID = 9L;

    /** 评论用户 */
    private static final Long USER_ID = 7L;

    /** 父评论作者 */
    private static final Long PARENT_AUTHOR = 8L;

    /** 评论 ID */
    private static final Long COMMENT_ID = 100L;

    @Mock
    private CommentRepository commentRepository;

    @Mock
    private SongRepository songRepository;

    @Mock
    private MusicProperties musicProperties;

    @Mock
    private CommentNotificationPort commentNotificationPort;

    @InjectMocks
    private CommentAppServiceImpl commentAppService;

    /**
     * 清理测试线程的租户上下文。
     */
    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    /**
     * 先发后审模式发表顶级评论：立即可见并累加歌曲评论计数。
     */
    @Test
    void shouldSubmitVisibleCommentInPostAuditMode() {
        TenantContext.set(TENANT_ID);
        when(songRepository.findById(SONG_ID)).thenReturn(Optional.of(playableSong()));
        when(musicProperties.isCommentPreAudit()).thenReturn(false);

        CommentView view = commentAppService.submit(SONG_ID, command(null, "好听"), USER_ID);

        assertEquals(CommentStatus.VISIBLE.name(), view.getStatus());
        assertEquals("好听", view.getContent());
        verify(commentRepository).insert(any(SongComment.class));
        verify(songRepository).addCommentCount(SONG_ID, 1L);
    }

    /**
     * 先审后发模式发表评论：进入待审核且不累加计数。
     */
    @Test
    void shouldSubmitPendingCommentInPreAuditMode() {
        TenantContext.set(TENANT_ID);
        when(songRepository.findById(SONG_ID)).thenReturn(Optional.of(playableSong()));
        when(musicProperties.isCommentPreAudit()).thenReturn(true);

        CommentView view = commentAppService.submit(SONG_ID, command(null, "待审核"), USER_ID);

        assertEquals(CommentStatus.PENDING.name(), view.getStatus());
        verify(commentRepository).insert(any(SongComment.class));
        verify(songRepository, never()).addCommentCount(anyLong(), anyLong());
    }

    /**
     * 歌曲不存在或不可见时拒绝评论。
     */
    @Test
    void shouldRejectCommentWhenSongInvisible() {
        when(songRepository.findById(SONG_ID)).thenReturn(Optional.empty());

        BizException ex = assertThrows(BizException.class,
                () -> commentAppService.submit(SONG_ID, command(null, "内容"), USER_ID));
        assertEquals(MusicErrorCode.SONG_NOT_FOUND, ex.getCode());
    }

    /**
     * 回复他人可见评论：事务提交后向父评论作者发送站内通知。
     */
    @Test
    void shouldSubmitReplyAndNotifyParentAuthor() {
        TenantContext.set(TENANT_ID);
        when(songRepository.findById(SONG_ID)).thenReturn(Optional.of(playableSong()));
        when(musicProperties.isCommentPreAudit()).thenReturn(false);
        when(commentRepository.findById(COMMENT_ID)).thenReturn(Optional.of(parentComment()));

        CommentView view = commentAppService.submit(SONG_ID, command(COMMENT_ID, "同感"), USER_ID);

        assertEquals(COMMENT_ID, view.getParentId());
        verify(commentNotificationPort).sendSafely(eq(TENANT_ID), eq(CommentNotificationPort.BIZ_TYPE_REPLY),
                anyString(), anyString(), anyString(), eq(PARENT_AUTHOR));
    }

    /**
     * 回复自己的评论不发送通知。
     */
    @Test
    void shouldNotNotifyWhenReplyingToSelf() {
        TenantContext.set(TENANT_ID);
        when(songRepository.findById(SONG_ID)).thenReturn(Optional.of(playableSong()));
        when(musicProperties.isCommentPreAudit()).thenReturn(false);
        SongComment ownParent = parentComment();
        ownParent.setUserId(USER_ID);
        when(commentRepository.findById(COMMENT_ID)).thenReturn(Optional.of(ownParent));

        commentAppService.submit(SONG_ID, command(COMMENT_ID, "补充"), USER_ID);

        verify(commentNotificationPort, never()).sendSafely(any(), anyString(), anyString(), anyString(),
                anyString(), any());
    }

    /**
     * 用户侧分页：仅可见评论，顶级评论携带回复分组。
     */
    @Test
    void shouldPageVisibleCommentsWithReplies() {
        when(songRepository.findById(SONG_ID)).thenReturn(Optional.of(playableSong()));
        SongComment top = persistedComment(COMMENT_ID, CommentStatus.VISIBLE);
        when(commentRepository.pageTopLevel(SONG_ID, 0L, 20L)).thenReturn(List.of(top));
        when(commentRepository.countTopLevel(SONG_ID)).thenReturn(1L);
        SongComment reply = persistedComment(101L, CommentStatus.VISIBLE);
        reply.setParentId(COMMENT_ID);
        when(commentRepository.findReplies(SONG_ID, List.of(COMMENT_ID))).thenReturn(List.of(reply));

        PageResult<CommentView> page = commentAppService.pageBySong(SONG_ID, 1L, 20L, USER_ID);

        assertEquals(1L, page.getTotal());
        assertEquals(1, page.getRecords().size());
        assertEquals(1, page.getRecords().get(0).getReplies().size());
        assertEquals(COMMENT_ID, page.getRecords().get(0).getReplies().get(0).getParentId());
    }

    /**
     * 审核分页填充歌曲标题。
     */
    @Test
    void shouldPageAuditWithSongTitle() {
        SongComment comment = persistedComment(COMMENT_ID, CommentStatus.VISIBLE);
        when(commentRepository.pageAudit(null, CommentStatus.VISIBLE, null, 0L, 20L))
                .thenReturn(List.of(comment));
        when(commentRepository.countAudit(null, CommentStatus.VISIBLE, null)).thenReturn(1L);
        Song song = playableSong();
        song.setTitle("冒烟歌曲");
        when(songRepository.findByIds(List.of(SONG_ID))).thenReturn(List.of(song));

        CommentAuditQueryCommand command = new CommentAuditQueryCommand();
        command.setStatus("visible");
        PageResult<CommentView> page = commentAppService.pageAudit(command);

        assertEquals(1L, page.getTotal());
        assertEquals("冒烟歌曲", page.getRecords().get(0).getSongTitle());
    }

    /**
     * 审核通过待审核评论：状态流转并累加计数。
     */
    @Test
    void shouldApprovePendingComment() {
        TenantContext.set(TENANT_ID);
        SongComment comment = persistedComment(COMMENT_ID, CommentStatus.PENDING);
        when(commentRepository.findById(COMMENT_ID)).thenReturn(Optional.of(comment));

        CommentView view = commentAppService.approve(COMMENT_ID);

        assertEquals(CommentStatus.VISIBLE.name(), view.getStatus());
        verify(commentRepository).update(comment);
        verify(songRepository).addCommentCount(SONG_ID, 1L);
    }

    /**
     * 重复审核通过幂等：不更新、不计数。
     */
    @Test
    void shouldApproveVisibleCommentIdempotently() {
        SongComment comment = persistedComment(COMMENT_ID, CommentStatus.VISIBLE);
        when(commentRepository.findById(COMMENT_ID)).thenReturn(Optional.of(comment));

        commentAppService.approve(COMMENT_ID);

        verify(commentRepository, never()).update(any(SongComment.class));
        verify(songRepository, never()).addCommentCount(anyLong(), anyLong());
    }

    /**
     * 隐藏可见评论：状态流转并扣减计数。
     */
    @Test
    void shouldHideVisibleComment() {
        TenantContext.set(TENANT_ID);
        SongComment comment = persistedComment(COMMENT_ID, CommentStatus.VISIBLE);
        when(commentRepository.findById(COMMENT_ID)).thenReturn(Optional.of(comment));

        CommentView view = commentAppService.hide(COMMENT_ID);

        assertEquals(CommentStatus.HIDDEN.name(), view.getStatus());
        verify(commentRepository).update(comment);
        verify(songRepository).addCommentCount(SONG_ID, -1L);
    }

    /**
     * 审核不存在的评论返回评论不存在。
     */
    @Test
    void shouldRejectAuditWhenCommentMissing() {
        when(commentRepository.findById(COMMENT_ID)).thenReturn(Optional.empty());

        BizException ex = assertThrows(BizException.class, () -> commentAppService.hide(COMMENT_ID));
        assertEquals(MusicErrorCode.COMMENT_NOT_FOUND, ex.getCode());
    }

    /**
     * 构造评论提交命令。
     *
     * @param parentId 父评论 ID
     * @param content 正文
     * @return 命令
     */
    private CommentSubmitCommand command(Long parentId, String content) {
        CommentSubmitCommand command = new CommentSubmitCommand();
        command.setParentId(parentId);
        command.setContent(content);
        return command;
    }

    /**
     * 构造可播放的公开歌曲。
     *
     * @return 歌曲
     */
    private Song playableSong() {
        Song song = new Song();
        song.setId(SONG_ID);
        song.setTitle("冒烟歌曲");
        song.setUploaderId(USER_ID);
        song.setVisibility(SongVisibility.PUBLIC);
        song.setPublishState(PublishState.PUBLISHED);
        song.setCopyrightState(CopyrightState.VALID);
        return song;
    }

    /**
     * 构造其他用户的可见父评论。
     *
     * @return 父评论
     */
    private SongComment parentComment() {
        SongComment parent = SongComment.create(SONG_ID, PARENT_AUTHOR, null, "父评论", false);
        parent.setId(COMMENT_ID);
        return parent;
    }

    /**
     * 构造指定状态的持久化评论。
     *
     * @param id 评论 ID
     * @param status 状态
     * @return 评论
     */
    private SongComment persistedComment(Long id, CommentStatus status) {
        SongComment comment = SongComment.create(SONG_ID, USER_ID, null, "内容", false);
        comment.setId(id);
        comment.setStatus(status);
        return comment;
    }
}
