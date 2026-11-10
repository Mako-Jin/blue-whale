package com.bluewhale.lms.cert.application.support;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.lms.cert.domain.model.CertSource;
import com.bluewhale.lms.common.LmsErrorCode;
import com.bluewhale.lms.course.domain.model.Enrollment;
import com.bluewhale.lms.course.domain.model.LearningProgress;
import com.bluewhale.lms.course.domain.repository.CourseRepository;
import com.bluewhale.lms.course.domain.repository.EnrollmentRepository;
import com.bluewhale.lms.course.domain.repository.ProgressRepository;
import com.bluewhale.lms.exam.domain.model.ExamAttempt;
import com.bluewhale.lms.exam.domain.model.ExamScore;
import com.bluewhale.lms.exam.domain.repository.AttemptRepository;
import com.bluewhale.lms.exam.domain.repository.ScoreRepository;
import com.bluewhale.lms.training.domain.model.TrainingAssignment;
import com.bluewhale.lms.training.domain.repository.TrainingAssignmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 发证来源校验：每条依据都要落到可核验的事实上。
 *
 * @author BlueWhale
 */
class CertificateIssueValidatorTest {

    /** 持证人用户 ID */
    private static final long HOLDER_ID = 100L;

    /** 来源业务 ID */
    private static final long SOURCE_ID = 77L;

    private EnrollmentRepository enrollmentRepository;

    private ProgressRepository progressRepository;

    private CourseRepository courseRepository;

    private TrainingAssignmentRepository trainingAssignmentRepository;

    private AttemptRepository attemptRepository;

    private ScoreRepository scoreRepository;

    private CertificateIssueValidator validator;

    @BeforeEach
    void setUp() {
        enrollmentRepository = mock(EnrollmentRepository.class);
        progressRepository = mock(ProgressRepository.class);
        courseRepository = mock(CourseRepository.class);
        trainingAssignmentRepository = mock(TrainingAssignmentRepository.class);
        attemptRepository = mock(AttemptRepository.class);
        scoreRepository = mock(ScoreRepository.class);
        validator = new CertificateIssueValidator(enrollmentRepository, progressRepository, courseRepository,
                trainingAssignmentRepository, attemptRepository, scoreRepository);
    }

    @Test
    @DisplayName("手工发证不校验来源")
    void manualShouldSkipVerification() {
        assertDoesNotThrow(() -> validator.verify(CertSource.MANUAL, HOLDER_ID, null));
    }

    @Test
    @DisplayName("考试发证：作答记录不属于持证人即拒绝")
    void examShouldRejectOtherHoldersAttempt() {
        ExamAttempt attempt = new ExamAttempt();
        attempt.setId(SOURCE_ID);
        attempt.setUserId(999L);
        when(attemptRepository.findById(SOURCE_ID)).thenReturn(Optional.of(attempt));
        BizException exception = assertThrows(BizException.class,
                () -> validator.verify(CertSource.EXAM, HOLDER_ID, SOURCE_ID));
        assertEquals(LmsErrorCode.CERTIFICATE_SOURCE_INVALID, exception.getCode());
    }

    @Test
    @DisplayName("考试发证：尚未判分出成绩即拒绝")
    void examShouldRejectUngradedAttempt() {
        when(attemptRepository.findById(SOURCE_ID)).thenReturn(Optional.of(attempt(HOLDER_ID)));
        when(scoreRepository.findLatest(SOURCE_ID)).thenReturn(Optional.empty());
        BizException exception = assertThrows(BizException.class,
                () -> validator.verify(CertSource.EXAM, HOLDER_ID, SOURCE_ID));
        assertEquals(LmsErrorCode.CERTIFICATE_SOURCE_INVALID, exception.getCode());
    }

    @Test
    @DisplayName("考试发证：未通过即拒绝")
    void examShouldRejectFailedScore() {
        when(attemptRepository.findById(SOURCE_ID)).thenReturn(Optional.of(attempt(HOLDER_ID)));
        when(scoreRepository.findLatest(SOURCE_ID)).thenReturn(Optional.of(score(false, "58")));
        BizException exception = assertThrows(BizException.class,
                () -> validator.verify(CertSource.EXAM, HOLDER_ID, SOURCE_ID));
        assertEquals(LmsErrorCode.CERTIFICATE_SOURCE_INVALID, exception.getCode());
        assertTrue(exception.getMessage().contains("58"));
    }

    @Test
    @DisplayName("考试发证：成绩通过则放行")
    void examShouldPassWithPassedScore() {
        when(attemptRepository.findById(SOURCE_ID)).thenReturn(Optional.of(attempt(HOLDER_ID)));
        when(scoreRepository.findLatest(SOURCE_ID)).thenReturn(Optional.of(score(true, "86")));
        assertDoesNotThrow(() -> validator.verify(CertSource.EXAM, HOLDER_ID, SOURCE_ID));
    }

    @Test
    @DisplayName("课程发证：未报名即拒绝")
    void courseShouldRejectNotEnrolled() {
        when(enrollmentRepository.findByCourseAndUser(SOURCE_ID, HOLDER_ID)).thenReturn(Optional.empty());
        BizException exception = assertThrows(BizException.class,
                () -> validator.verify(CertSource.COURSE, HOLDER_ID, SOURCE_ID));
        assertEquals(LmsErrorCode.CERTIFICATE_SOURCE_INVALID, exception.getCode());
    }

    @Test
    @DisplayName("课程发证：章节未全部学完即拒绝")
    void courseShouldRejectUnfinished() {
        when(enrollmentRepository.findByCourseAndUser(SOURCE_ID, HOLDER_ID)).thenReturn(Optional.of(enrollment()));
        when(courseRepository.countChapters(SOURCE_ID)).thenReturn(3L);
        when(progressRepository.listByEnrollment(88L)).thenReturn(List.of(progress(100), progress(60)));
        BizException exception = assertThrows(BizException.class,
                () -> validator.verify(CertSource.COURSE, HOLDER_ID, SOURCE_ID));
        assertEquals(LmsErrorCode.CERTIFICATE_SOURCE_INVALID, exception.getCode());
    }

    @Test
    @DisplayName("课程发证：章节全部学完则放行")
    void courseShouldPassWhenAllChaptersDone() {
        when(enrollmentRepository.findByCourseAndUser(SOURCE_ID, HOLDER_ID)).thenReturn(Optional.of(enrollment()));
        when(courseRepository.countChapters(SOURCE_ID)).thenReturn(2L);
        when(progressRepository.listByEnrollment(88L)).thenReturn(List.of(progress(100), progress(100)));
        assertDoesNotThrow(() -> validator.verify(CertSource.COURSE, HOLDER_ID, SOURCE_ID));
    }

    @Test
    @DisplayName("培训发证：未分配即拒绝")
    void trainingShouldRejectUnassigned() {
        when(trainingAssignmentRepository.findByClassAndUser(SOURCE_ID, HOLDER_ID)).thenReturn(Optional.empty());
        BizException exception = assertThrows(BizException.class,
                () -> validator.verify(CertSource.TRAINING, HOLDER_ID, SOURCE_ID));
        assertEquals(LmsErrorCode.CERTIFICATE_SOURCE_INVALID, exception.getCode());
    }

    @Test
    @DisplayName("培训发证：已分配则放行（M4 口径）")
    void trainingShouldPassWhenAssigned() {
        when(trainingAssignmentRepository.findByClassAndUser(SOURCE_ID, HOLDER_ID))
                .thenReturn(Optional.of(new TrainingAssignment()));
        assertDoesNotThrow(() -> validator.verify(CertSource.TRAINING, HOLDER_ID, SOURCE_ID));
    }

    @Test
    @DisplayName("非手工发证缺少来源 ID 即拒绝")
    void shouldRejectMissingSourceId() {
        BizException exception = assertThrows(BizException.class,
                () -> validator.verify(CertSource.EXAM, HOLDER_ID, null));
        assertEquals(LmsErrorCode.CERTIFICATE_SOURCE_INVALID, exception.getCode());
    }

    /**
     * 构造作答记录。
     *
     * @param userId 学员用户 ID
     * @return 作答记录
     */
    private ExamAttempt attempt(long userId) {
        ExamAttempt attempt = new ExamAttempt();
        attempt.setId(SOURCE_ID);
        attempt.setUserId(userId);
        return attempt;
    }

    /**
     * 构造成绩。
     *
     * @param passed 是否及格
     * @param total 总分
     * @return 成绩
     */
    private ExamScore score(boolean passed, String total) {
        ExamScore score = new ExamScore();
        score.setAttemptId(SOURCE_ID);
        score.setPassed(passed);
        score.setTotalScore(new BigDecimal(total));
        return score;
    }

    /**
     * 构造报名记录。
     *
     * @return 报名记录
     */
    private Enrollment enrollment() {
        Enrollment enrollment = new Enrollment();
        enrollment.setId(88L);
        enrollment.setCourseId(SOURCE_ID);
        enrollment.setUserId(HOLDER_ID);
        return enrollment;
    }

    /**
     * 构造章节进度。
     *
     * @param percent 百分比
     * @return 进度
     */
    private LearningProgress progress(int percent) {
        LearningProgress progress = new LearningProgress();
        progress.setPercent(percent);
        return progress;
    }
}
