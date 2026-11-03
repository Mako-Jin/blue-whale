package com.bluewhale.video.domain.model;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.video.domain.exception.VideoErrorCode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 内容评论正文规范化与可见性单元测试（M1-5）。
 *
 * @author BlueWhale
 */
class ContentCommentTest {

    /**
     * 正文去除首尾空白后保持原文。
     */
    @Test
    void shouldTrimCommentContent() {
        assertEquals("好看", ContentComment.normalize("  好看  "));
    }

    /**
     * 空正文与超长正文均按评论正文非法处理。
     */
    @Test
    void shouldRejectBlankOrTooLongContent() {
        assertEquals(VideoErrorCode.COMMENT_CONTENT_INVALID,
                assertThrows(BizException.class, () -> ContentComment.normalize(null)).getCode());
        assertEquals(VideoErrorCode.COMMENT_CONTENT_INVALID,
                assertThrows(BizException.class, () -> ContentComment.normalize("   ")).getCode());
        assertEquals(VideoErrorCode.COMMENT_CONTENT_INVALID,
                assertThrows(BizException.class,
                        () -> ContentComment.normalize("评".repeat(ContentComment.CONTENT_MAX_LENGTH + 1))).getCode());
    }

    /**
     * 仅 VISIBLE 状态的评论对用户侧可见。
     */
    @Test
    void shouldBeVisibleOnlyWhenVisibleStatus() {
        ContentComment comment = new ContentComment();
        comment.setStatus(CommentStatus.VISIBLE);
        assertTrue(comment.visible());

        comment.setStatus(CommentStatus.PENDING);
        assertFalse(comment.visible());

        comment.setStatus(CommentStatus.HIDDEN);
        assertFalse(comment.visible());
    }
}
