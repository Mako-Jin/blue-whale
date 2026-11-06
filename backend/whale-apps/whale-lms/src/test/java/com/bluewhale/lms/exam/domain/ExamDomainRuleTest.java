package com.bluewhale.lms.exam.domain;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.lms.common.LmsErrorCode;
import com.bluewhale.lms.exam.domain.model.AttemptStatus;
import com.bluewhale.lms.exam.domain.model.Exam;
import com.bluewhale.lms.exam.domain.model.ExamAttempt;
import com.bluewhale.lms.exam.domain.model.ExamStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 考试与作答领域规则：时间窗、单次截止、交卷记账、及格判定。
 *
 * @author BlueWhale
 */
class ExamDomainRuleTest {

    /** 考试基准开考时间 */
    private static final LocalDateTime START = LocalDateTime.of(2026, 3, 1, 9, 0);

    @Test
    @DisplayName("未到开考时间不可作答")
    void shouldRejectBeforeStart() {
        Exam exam = publishedExam();
        BizException exception = assertThrows(BizException.class,
                () -> exam.assertOpen(START.minusMinutes(1)));
        assertEquals(LmsErrorCode.EXAM_WINDOW_CLOSED, exception.getCode());
    }

    @Test
    @DisplayName("超过考试截止时间不可作答")
    void shouldRejectAfterEnd() {
        Exam exam = publishedExam();
        BizException exception = assertThrows(BizException.class,
                () -> exam.assertOpen(START.plusHours(3)));
        assertEquals(LmsErrorCode.EXAM_WINDOW_CLOSED, exception.getCode());
    }

    @Test
    @DisplayName("未发布考试不可作答")
    void shouldRejectUnpublishedExam() {
        Exam exam = publishedExam();
        exam.setStatus(ExamStatus.DRAFT);
        BizException exception = assertThrows(BizException.class, () -> exam.assertOpen(START));
        assertEquals(LmsErrorCode.EXAM_STATE_CONFLICT, exception.getCode());
    }

    @Test
    @DisplayName("单次截止取「开始 + 时长」与考试截止的较早者")
    void deadlineShouldBeMinOfDurationAndEnd() {
        Exam exam = publishedExam();
        assertEquals(START.plusMinutes(60), exam.deadlineOf(START));
        assertEquals(exam.getEndTime(), exam.deadlineOf(START.plusHours(1).plusMinutes(30)));
    }

    @Test
    @DisplayName("发布考试时校验基本参数，截止早于开考直接拒绝")
    void publishShouldValidateWindow() {
        Exam exam = publishedExam();
        exam.setStatus(ExamStatus.DRAFT);
        exam.setEndTime(exam.getStartTime().minusMinutes(1));
        BizException exception = assertThrows(BizException.class, exam::publish);
        assertEquals(LmsErrorCode.EXAM_INVALID, exception.getCode());
    }

    @Test
    @DisplayName("仅已发布考试可结课")
    void closeShouldRequirePublished() {
        Exam exam = publishedExam();
        exam.setStatus(ExamStatus.DRAFT);
        BizException exception = assertThrows(BizException.class, exam::close);
        assertEquals(LmsErrorCode.EXAM_STATE_CONFLICT, exception.getCode());
    }

    @Test
    @DisplayName("考试编码归一为大写并去空白")
    void normalizeCodeShouldUpperTrim() {
        assertEquals("EXAM-01", Exam.normalizeCode(" exam-01 "));
    }

    @Test
    @DisplayName("超过单次截止时间不可再保存答案")
    void saveShouldRejectAfterDeadline() {
        ExamAttempt attempt = inProgressAttempt();
        BizException exception = assertThrows(BizException.class,
                () -> attempt.assertWithinDeadline(attempt.getDeadlineAt().plusSeconds(1)));
        assertEquals(LmsErrorCode.ATTEMPT_EXPIRED, exception.getCode());
    }

    @Test
    @DisplayName("截止后仍受理交卷，但按截止时刻记账")
    void submitShouldRecordDeadlineWhenLate() {
        ExamAttempt attempt = inProgressAttempt();
        LocalDateTime late = attempt.getDeadlineAt().plusMinutes(5);
        attempt.submit(late);
        assertEquals(AttemptStatus.SUBMITTED, attempt.getStatus());
        assertEquals(attempt.getDeadlineAt(), attempt.getSubmittedAt());
    }

    @Test
    @DisplayName("按时交卷以实际交卷时刻记账")
    void submitShouldRecordNowWhenInTime() {
        ExamAttempt attempt = inProgressAttempt();
        LocalDateTime now = attempt.getDeadlineAt().minusMinutes(1);
        attempt.submit(now);
        assertEquals(now, attempt.getSubmittedAt());
    }

    @Test
    @DisplayName("已交卷不可再次交卷")
    void submitShouldRejectTwice() {
        ExamAttempt attempt = inProgressAttempt();
        attempt.submit(LocalDateTime.now());
        BizException exception = assertThrows(BizException.class, () -> attempt.submit(LocalDateTime.now()));
        assertEquals(LmsErrorCode.ATTEMPT_STATE_CONFLICT, exception.getCode());
    }

    @Test
    @DisplayName("全部判完才置为已判分，未判完保持已交卷")
    void applyScoresShouldDependOnAllGraded() {
        ExamAttempt attempt = inProgressAttempt();
        attempt.submit(attempt.getDeadlineAt().minusMinutes(10));
        attempt.applyScores(new BigDecimal("5"), new BigDecimal("0"), new BigDecimal("6"), false);
        assertEquals(AttemptStatus.SUBMITTED, attempt.getStatus());
        assertFalse(attempt.getPassed());

        attempt.applyScores(new BigDecimal("5"), new BigDecimal("8"), new BigDecimal("6"), true);
        assertEquals(AttemptStatus.GRADED, attempt.getStatus());
        assertEquals(new BigDecimal("13"), attempt.getTotalScore());
        assertTrue(attempt.getPassed());
    }

    @Test
    @DisplayName("及格线相等视为及格")
    void applyScoresShouldTreatEqualAsPassed() {
        ExamAttempt attempt = inProgressAttempt();
        attempt.submit(attempt.getDeadlineAt().minusMinutes(10));
        attempt.applyScores(new BigDecimal("6"), BigDecimal.ZERO, new BigDecimal("6"), true);
        assertTrue(attempt.getPassed());
    }

    /**
     * 构造已发布考试（09:00–11:00，单次 60 分钟）。
     *
     * @return 考试
     */
    private Exam publishedExam() {
        Exam exam = new Exam();
        exam.setExamCode("EXAM-01");
        exam.setTitle("安全生产考试");
        exam.setPaperId(11L);
        exam.setStartTime(START);
        exam.setEndTime(START.plusHours(2));
        exam.setDurationMinutes(60);
        exam.setMaxAttempts(2);
        exam.setShuffle(true);
        exam.setPassScore(new BigDecimal("60"));
        exam.setSwitchLimit(3);
        exam.setStatus(ExamStatus.PUBLISHED);
        return exam;
    }

    /**
     * 构造作答中的记录。
     *
     * @return 作答记录
     */
    private ExamAttempt inProgressAttempt() {
        ExamAttempt attempt = new ExamAttempt();
        attempt.setExamId(21L);
        attempt.setPaperId(11L);
        attempt.setUserId(7L);
        attempt.setAttemptNo(1);
        attempt.setSeed("EXAM-01#7#1");
        attempt.setStartedAt(START);
        attempt.setDeadlineAt(START.plusMinutes(60));
        attempt.setStatus(AttemptStatus.IN_PROGRESS);
        attempt.setSwitchCount(0);
        return attempt;
    }
}
