package com.bluewhale.music.domain.model;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.music.domain.exception.MusicErrorCode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 版权下架申诉聚合根状态机单元测试。
 *
 * @author BlueWhale
 */
class SongAppealTest {

    /** 申诉人用户 ID */
    private static final Long APPLICANT_ID = 1L;

    /** 复核人用户 ID */
    private static final Long REVIEWER_ID = 2L;

    /** 歌曲 ID */
    private static final Long SONG_ID = 3L;

    /**
     * 创建申诉：去首尾空白、初始状态为待复核。
     */
    @Test
    void shouldCreatePendingAppealWithTrimmedReason() {
        SongAppeal appeal = SongAppeal.create(SONG_ID, APPLICANT_ID, "  作品为原创，请重新复核  ");
        assertEquals(SONG_ID, appeal.getSongId());
        assertEquals(APPLICANT_ID, appeal.getApplicantId());
        assertEquals("作品为原创，请重新复核", appeal.getReason());
        assertEquals(AppealStatus.PENDING, appeal.getStatus());
        assertTrue(appeal.pending());
        assertNull(appeal.getReviewedAt());
    }

    /**
     * 申诉理由为空或超过 500 个字符应被拒绝。
     */
    @Test
    void shouldRejectBlankOrTooLongReason() {
        assertEquals(MusicErrorCode.APPEAL_REASON_INVALID,
                assertThrows(BizException.class, () -> SongAppeal.create(SONG_ID, APPLICANT_ID, "   ")).getCode());
        assertEquals(MusicErrorCode.APPEAL_REASON_INVALID,
                assertThrows(BizException.class,
                        () -> SongAppeal.create(SONG_ID, APPLICANT_ID, "x".repeat(501))).getCode());
        assertEquals(MusicErrorCode.APPEAL_REASON_INVALID,
                assertThrows(BizException.class,
                        () -> SongAppeal.create(SONG_ID, APPLICANT_ID, null)).getCode());
    }

    /**
     * 申诉通过：记录复核人、复核时间与审核意见。
     */
    @Test
    void shouldApprovePendingAppeal() {
        SongAppeal appeal = SongAppeal.create(SONG_ID, APPLICANT_ID, "原创作品");
        appeal.approve(REVIEWER_ID, "  复核通过  ");
        assertEquals(AppealStatus.APPROVED, appeal.getStatus());
        assertEquals(REVIEWER_ID, appeal.getReviewerId());
        assertEquals("复核通过", appeal.getReviewOpinion());
        assertNotNull(appeal.getReviewedAt());
    }

    /**
     * 申诉驳回：状态置为驳回且歌曲保持下架。
     */
    @Test
    void shouldRejectPendingAppeal() {
        SongAppeal appeal = SongAppeal.create(SONG_ID, APPLICANT_ID, "原创作品");
        appeal.reject(REVIEWER_ID, null);
        assertEquals(AppealStatus.REJECTED, appeal.getStatus());
        assertEquals(REVIEWER_ID, appeal.getReviewerId());
        assertNull(appeal.getReviewOpinion());
    }

    /**
     * 已复核的申诉不可重复流转。
     */
    @Test
    void shouldRejectReviewOnReviewedAppeal() {
        SongAppeal appeal = SongAppeal.create(SONG_ID, APPLICANT_ID, "原创作品");
        appeal.approve(REVIEWER_ID, "通过");
        assertEquals(MusicErrorCode.APPEAL_STATE_CONFLICT,
                assertThrows(BizException.class, () -> appeal.reject(REVIEWER_ID, "驳回")).getCode());
    }

    /**
     * 审核意见超过 500 个字符应被拒绝。
     */
    @Test
    void shouldRejectTooLongOpinion() {
        SongAppeal appeal = SongAppeal.create(SONG_ID, APPLICANT_ID, "原创作品");
        assertEquals(MusicErrorCode.APPEAL_REASON_INVALID,
                assertThrows(BizException.class,
                        () -> appeal.approve(REVIEWER_ID, "x".repeat(501))).getCode());
        assertEquals(AppealStatus.PENDING, appeal.getStatus());
    }
}
