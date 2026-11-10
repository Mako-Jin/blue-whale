package com.bluewhale.music.domain.model;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.music.domain.exception.MusicErrorCode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 歌曲评论聚合单元测试：正文校验、回复目标校验、状态流转幂等。
 *
 * @author BlueWhale
 */
class SongCommentTest {

    /** 歌曲 ID */
    private static final Long SONG_ID = 9L;

    /** 评论用户 */
    private static final Long USER_ID = 7L;

    /**
     * 先发后审模式新评论立即可见，正文去首尾空白。
     */
    @Test
    void shouldCreateVisibleCommentInPostAuditMode() {
        SongComment comment = SongComment.create(SONG_ID, USER_ID, null, "  好听  ", false);

        assertEquals(SONG_ID, comment.getSongId());
        assertEquals(USER_ID, comment.getUserId());
        assertEquals(SongComment.TOP_LEVEL_PARENT_ID, comment.getParentId());
        assertEquals("好听", comment.getContent());
        assertEquals(CommentStatus.VISIBLE, comment.getStatus());
        assertEquals(0L, comment.getLikeCount());
        assertTrue(comment.visible());
        assertNull(comment.getId());
    }

    /**
     * 先审后发模式新评论为待审核状态。
     */
    @Test
    void shouldCreatePendingCommentInPreAuditMode() {
        SongComment comment = SongComment.create(SONG_ID, USER_ID, null, "待审核", true);

        assertEquals(CommentStatus.PENDING, comment.getStatus());
        assertFalse(comment.visible());
    }

    /**
     * 正文为空、全空白或超过 500 字均拒绝。
     */
    @Test
    void shouldRejectInvalidContent() {
        assertCommentContentInvalid(null);
        assertCommentContentInvalid("   ");
        assertCommentContentInvalid("好".repeat(501));
    }

    /**
     * 500 字正文恰好允许。
     */
    @Test
    void shouldAcceptMaxLengthContent() {
        SongComment comment = SongComment.create(SONG_ID, USER_ID, null, "好".repeat(500), false);
        assertEquals(500, comment.getContent().length());
    }

    /**
     * 回复可见父评论成功并继承父评论 ID。
     */
    @Test
    void shouldReplyToVisibleParent() {
        SongComment parent = visibleParent(100L, SONG_ID);

        SongComment reply = SongComment.create(SONG_ID, USER_ID, parent, "同感", false);

        assertEquals(100L, reply.getParentId());
        assertEquals(CommentStatus.VISIBLE, reply.getStatus());
    }

    /**
     * 父评论跨歌曲或不可见时拒绝回复。
     */
    @Test
    void shouldRejectInvalidParent() {
        SongComment otherSongParent = visibleParent(100L, 999L);
        SongComment hiddenParent = visibleParent(101L, SONG_ID);
        hiddenParent.setStatus(CommentStatus.HIDDEN);
        SongComment pendingParent = visibleParent(102L, SONG_ID);
        pendingParent.setStatus(CommentStatus.PENDING);

        assertParentInvalid(otherSongParent);
        assertParentInvalid(hiddenParent);
        assertParentInvalid(pendingParent);
    }

    /**
     * 审核通过幂等：PENDING / HIDDEN → VISIBLE 返回 true，VISIBLE 返回 false。
     */
    @Test
    void shouldApproveIdempotently() {
        SongComment pending = SongComment.create(SONG_ID, USER_ID, null, "内容", true);
        assertTrue(pending.approve());
        assertEquals(CommentStatus.VISIBLE, pending.getStatus());
        assertFalse(pending.approve());

        SongComment hidden = SongComment.create(SONG_ID, USER_ID, null, "内容", false);
        hidden.hide();
        assertTrue(hidden.approve());
        assertEquals(CommentStatus.VISIBLE, hidden.getStatus());
    }

    /**
     * 隐藏幂等：VISIBLE → HIDDEN 返回 true（需扣减计数），PENDING → HIDDEN 与重复隐藏返回 false。
     */
    @Test
    void shouldHideIdempotently() {
        SongComment visible = SongComment.create(SONG_ID, USER_ID, null, "内容", false);
        assertTrue(visible.hide());
        assertEquals(CommentStatus.HIDDEN, visible.getStatus());
        assertFalse(visible.hide());

        SongComment pending = SongComment.create(SONG_ID, USER_ID, null, "内容", true);
        assertFalse(pending.hide());
        assertEquals(CommentStatus.HIDDEN, pending.getStatus());
    }

    /**
     * 断言正文非法。
     *
     * @param content 正文
     */
    private void assertCommentContentInvalid(String content) {
        BizException ex = assertThrows(BizException.class,
                () -> SongComment.create(SONG_ID, USER_ID, null, content, false));
        assertEquals(MusicErrorCode.COMMENT_CONTENT_INVALID, ex.getCode());
    }

    /**
     * 断言父评论非法。
     *
     * @param parent 父评论
     */
    private void assertParentInvalid(SongComment parent) {
        BizException ex = assertThrows(BizException.class,
                () -> SongComment.create(SONG_ID, USER_ID, parent, "回复", false));
        assertEquals(MusicErrorCode.COMMENT_PARENT_INVALID, ex.getCode());
    }

    /**
     * 构造可见父评论。
     *
     * @param id 评论 ID
     * @param songId 歌曲 ID
     * @return 父评论
     */
    private SongComment visibleParent(Long id, Long songId) {
        SongComment parent = SongComment.create(songId, USER_ID, null, "父评论", false);
        parent.setId(id);
        return parent;
    }
}
