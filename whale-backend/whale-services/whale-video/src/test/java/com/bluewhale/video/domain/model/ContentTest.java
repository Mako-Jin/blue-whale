package com.bluewhale.video.domain.model;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.video.domain.exception.VideoErrorCode;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 视频内容聚合根状态机、可见性与合规规则单元测试（M1-1 / M1-7）。
 *
 * @author BlueWhale
 */
class ContentTest {

    /** 视频时长上限（毫秒） */
    private static final long MAX_DURATION_MS = 7_200_000L;

    /**
     * 信息完整的内容可以上架，并刷新上架时间。
     */
    @Test
    void shouldPublishReadyContent() {
        Content content = readyContent();
        content.publish(MAX_DURATION_MS);
        assertEquals(PublishState.PUBLISHED, content.getPublishState());
        assertNotNull(content.getPublishedAt());
        assertTrue(content.playable());
    }

    /**
     * 缺失视频文件、时长为 0 或超过类型上限都不能上架。
     */
    @Test
    void shouldRejectPublishWithoutFileOrValidDuration() {
        Content noFile = readyContent();
        noFile.setVideoFileId(null);
        assertEquals(VideoErrorCode.CONTENT_DURATION_INVALID,
                assertThrows(BizException.class, () -> noFile.publish(MAX_DURATION_MS)).getCode());

        Content zeroDuration = readyContent();
        zeroDuration.setDurationMs(0L);
        assertEquals(VideoErrorCode.CONTENT_DURATION_INVALID,
                assertThrows(BizException.class, () -> zeroDuration.publish(MAX_DURATION_MS)).getCode());

        Content tooLong = readyContent();
        tooLong.setDurationMs(MAX_DURATION_MS + 1);
        assertEquals(VideoErrorCode.CONTENT_DURATION_INVALID,
                assertThrows(BizException.class, () -> tooLong.publish(MAX_DURATION_MS)).getCode());
    }

    /**
     * 标题为空或超过 200 字符时不能上架。
     */
    @Test
    void shouldRejectPublishWithInvalidTitle() {
        Content blankTitle = readyContent();
        blankTitle.setTitle("   ");
        assertEquals(VideoErrorCode.CONTENT_TITLE_INVALID,
                assertThrows(BizException.class, () -> blankTitle.publish(MAX_DURATION_MS)).getCode());

        Content longTitle = readyContent();
        longTitle.setTitle("长".repeat(Content.TITLE_MAX_LENGTH + 1));
        assertEquals(VideoErrorCode.CONTENT_TITLE_INVALID,
                assertThrows(BizException.class, () -> longTitle.publish(MAX_DURATION_MS)).getCode());
    }

    /**
     * 版权下架后即使重新拥有文件也不能上架。
     */
    @Test
    void shouldRejectPublishAfterTakedown() {
        Content content = readyContent();
        content.takedown("版权争议");
        assertEquals(VideoErrorCode.CONTENT_STATE_CONFLICT,
                assertThrows(BizException.class, () -> content.publish(MAX_DURATION_MS)).getCode());
    }

    /**
     * 电影授权到期后不能上架。
     */
    @Test
    void shouldRejectPublishWhenLicenseExpired() {
        Content content = readyContent();
        content.setCopyrightExpireAt(LocalDateTime.now().minusDays(1));
        assertEquals(VideoErrorCode.CONTENT_STATE_CONFLICT,
                assertThrows(BizException.class, () -> content.publish(MAX_DURATION_MS)).getCode());
    }

    /**
     * 版权下架强制切到 OFFLINE 与 TAKEDOWN，并留痕原因与时间。
     */
    @Test
    void shouldForceOfflineOnTakedown() {
        Content content = readyContent();
        content.publish(MAX_DURATION_MS);
        content.takedown("  侵权投诉  ");
        assertEquals(PublishState.OFFLINE, content.getPublishState());
        assertEquals(CopyrightState.TAKEDOWN, content.getCopyrightState());
        assertEquals("侵权投诉", content.getTakedownReason());
        assertNotNull(content.getTakedownAt());
        assertFalse(content.playable());
    }

    /**
     * 版权下架必须填写原因。
     */
    @Test
    void shouldRejectTakedownWithoutReason() {
        Content content = readyContent();
        assertEquals(VideoErrorCode.TAKEDOWN_REASON_REQUIRED,
                assertThrows(BizException.class, () -> content.takedown("  ")).getCode());
    }

    /**
     * 草稿与待审内容不能下架，重复下架幂等。
     */
    @Test
    void shouldRejectOfflineDraftAndPendingReview() {
        Content draft = readyContent();
        assertEquals(VideoErrorCode.CONTENT_STATE_CONFLICT,
                assertThrows(BizException.class, draft::offline).getCode());

        Content pending = readyContent();
        pending.setPublishState(PublishState.PENDING_REVIEW);
        assertEquals(VideoErrorCode.CONTENT_STATE_CONFLICT,
                assertThrows(BizException.class, pending::offline).getCode());

        Content published = readyContent();
        published.publish(MAX_DURATION_MS);
        published.offline();
        published.offline();
        assertEquals(PublishState.OFFLINE, published.getPublishState());
    }

    /**
     * 恢复版权后保持 OFFLINE，需运营显式重新上架才可播放。
     */
    @Test
    void shouldRestoreCopyrightAndKeepOffline() {
        Content content = readyContent();
        content.publish(MAX_DURATION_MS);
        content.takedown("版权争议");
        content.restoreCopyright();
        assertEquals(CopyrightState.AUTHORIZED, content.getCopyrightState());
        assertEquals(PublishState.OFFLINE, content.getPublishState());
        assertNull(content.getTakedownReason());
        assertNull(content.getTakedownAt());
        assertFalse(content.playable());

        content.publish(MAX_DURATION_MS);
        assertTrue(content.playable());
    }

    /**
     * 非版权下架状态不允许恢复版权。
     */
    @Test
    void shouldRejectRestoreWhenNotTakedown() {
        Content content = readyContent();
        assertEquals(VideoErrorCode.CONTENT_STATE_CONFLICT,
                assertThrows(BizException.class, content::restoreCopyright).getCode());
    }

    /**
     * 私密内容仅上传者可见，运营可见全部。
     */
    @Test
    void shouldLimitPrivateContentVisibility() {
        Content content = readyContent();
        content.setVisibility(ContentVisibility.PRIVATE);
        content.publish(MAX_DURATION_MS);
        assertTrue(content.visibleTo(content.getUploaderId(), false));
        assertFalse(content.visibleTo(999L, false));
        assertFalse(content.visibleTo(null, false));
        assertTrue(content.visibleTo(999L, true));
    }

    /**
     * 未发布、版权下架与授权到期内容对普通用户不可见。
     */
    @Test
    void shouldHideUnpublishedOrUnauthorizedContent() {
        Content draft = readyContent();
        assertTrue(draft.visibleTo(draft.getUploaderId(), false));
        assertFalse(draft.visibleTo(999L, false));

        Content takedown = readyContent();
        takedown.publish(MAX_DURATION_MS);
        takedown.takedown("版权争议");
        assertFalse(takedown.visibleTo(999L, false));

        Content expired = readyContent();
        expired.setCopyrightExpireAt(LocalDateTime.now().minusMinutes(1));
        expired.setPublishState(PublishState.PUBLISHED);
        assertFalse(expired.visibleTo(999L, false));
        assertTrue(expired.visibleTo(999L, true));
    }

    /**
     * 非本人操作内容应被拒绝。
     */
    @Test
    void shouldRejectNonOwnerOperation() {
        Content content = readyContent();
        assertEquals(VideoErrorCode.CONTENT_NOT_OWNED,
                assertThrows(BizException.class, () -> content.assertOwnedBy(999L)).getCode());
        assertEquals(VideoErrorCode.CONTENT_NOT_OWNED,
                assertThrows(BizException.class, () -> content.assertOwnedBy(null)).getCode());
        content.assertOwnedBy(content.getUploaderId());
    }

    /**
     * 内容类型必须与接口路径一致，不一致按不存在处理。
     */
    @Test
    void shouldRejectMismatchedContentType() {
        Content content = readyContent();
        content.assertType(ContentType.VIDEO);
        assertEquals(VideoErrorCode.CONTENT_NOT_FOUND,
                assertThrows(BizException.class, () -> content.assertType(ContentType.SHORT_VIDEO)).getCode());
        assertEquals(VideoErrorCode.CONTENT_NOT_FOUND,
                assertThrows(BizException.class, () -> content.assertType(null)).getCode());
    }

    /**
     * 内容来源大小写不敏感，非法取值拒绝，空值保留原值。
     */
    @Test
    void shouldApplyDeclaredSource() {
        Content content = readyContent();
        content.setSource(null);
        content.applySource("original");
        assertEquals(CopyrightSource.ORIGINAL, content.getSource());
        content.applySource("  ");
        assertEquals(CopyrightSource.ORIGINAL, content.getSource());
        assertEquals(VideoErrorCode.CONTENT_SOURCE_INVALID,
                assertThrows(BizException.class, () -> content.applySource("PIRATED")).getCode());
    }

    /**
     * 未声明内容来源时禁止入库。
     */
    @Test
    void shouldRequireDeclaredSource() {
        Content content = readyContent();
        content.setSource(null);
        assertEquals(VideoErrorCode.CONTENT_SOURCE_REQUIRED,
                assertThrows(BizException.class, content::assertSourceDeclared).getCode());
        content.applySource("ROYALTY_FREE");
        content.assertSourceDeclared();
    }

    /**
     * 仅「已发布 + 公开 + 可播放」的内容开放评论。
     */
    @Test
    void shouldAllowCommentOnlyForPlayablePublicContent() {
        Content content = readyContent();
        content.publish(MAX_DURATION_MS);
        assertTrue(content.commentable());

        content.setVisibility(ContentVisibility.PRIVATE);
        assertFalse(content.commentable());

        content.setVisibility(ContentVisibility.PUBLIC);
        content.takedown("版权争议");
        assertFalse(content.commentable());
    }

    /**
     * 构造一条信息完整、可发布的内容。
     *
     * @return 内容
     */
    private Content readyContent() {
        Content content = new Content();
        content.setId(9L);
        content.setContentType(ContentType.VIDEO);
        content.setTitle("深海纪录片");
        content.setVideoFileId(10L);
        content.setCoverFileId(11L);
        content.setDurationMs(3_600_000L);
        content.setUploaderId(7L);
        content.setVisibility(ContentVisibility.PUBLIC);
        content.setPublishState(PublishState.DRAFT);
        content.setCopyrightState(CopyrightState.AUTHORIZED);
        content.setSource(CopyrightSource.ORIGINAL);
        content.setPlayCount(0L);
        content.setLikeCount(0L);
        content.setFavoriteCount(0L);
        content.setCommentCount(0L);
        return content;
    }
}
