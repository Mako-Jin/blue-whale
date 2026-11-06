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
import com.bluewhale.lms.training.domain.repository.TrainingAssignmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

/**
 * 发证来源校验：把「凭什么发这张证」落到可核验的事实上。
 *
 * <p>只读引用其他上下文的仓储端口，不写对方的表——证书域不产生课程/考试状态，
 * 只核对它们是否已达成。校验强度按来源区分，且这区分是有意为之：</p>
 * <ul>
 *   <li><b>EXAM</b>：最严格。作答记录必须属于持证人，且最新版成绩为及格。判分未完成的
 *       作答（最新成绩还不存在）一律拒绝，避免「没出分先发证」；</li>
 *   <li><b>COURSE</b>：报名记录存在，且全部章节进度满 100%。按章节数比对而不是只看
 *       总进度百分比，避免只学完一节就把均值刷上去；</li>
 *   <li><b>TRAINING</b>：仅校验分配关系存在。<b>注意</b>：M2 的培训完成认定依赖签到与
 *       场次，尚未落成可查询的完成标记，此处按「已分配」放行；收紧到「已签到且场次结束」
 *       安排在 M5 集成（与催办、通知同一批动线），届时改这一个方法即可。</li>
 * </ul>
 *
 * @author BlueWhale
 */
@Component
@RequiredArgsConstructor
public class CertificateIssueValidator {

    /** 已完成进度的阈值 */
    private static final int COMPLETED_PERCENT = 100;

    private final EnrollmentRepository enrollmentRepository;

    private final ProgressRepository progressRepository;

    private final CourseRepository courseRepository;

    private final TrainingAssignmentRepository trainingAssignmentRepository;

    private final AttemptRepository attemptRepository;

    private final ScoreRepository scoreRepository;

    /**
     * 校验发证来源是否满足条件。
     *
     * @param source 来源类型
     * @param userId 持证人用户 ID
     * @param sourceId 来源业务 ID
     */
    public void verify(CertSource source, Long userId, Long sourceId) {
        if (source == null || !source.requiresVerification()) {
            return;
        }
        if (sourceId == null || sourceId <= 0) {
            throw invalid("发证依据为" + sourceLabel(source) + "，必须提供来源业务 ID");
        }
        switch (source) {
            case EXAM -> verifyExam(userId, sourceId);
            case COURSE -> verifyCourse(userId, sourceId);
            case TRAINING -> verifyTraining(userId, sourceId);
            default -> {
                // MANUAL 已在前面提前返回，此处不会命中
            }
        }
    }

    /**
     * 校验考试通过。
     *
     * @param userId 持证人用户 ID
     * @param attemptId 作答记录 ID
     */
    private void verifyExam(Long userId, Long attemptId) {
        ExamAttempt attempt = attemptRepository.findById(attemptId)
                .orElseThrow(() -> invalid("按考试发证时，来源必须是有效的作答记录"));
        if (!userId.equals(attempt.getUserId())) {
            throw invalid("该次作答记录不属于持证人");
        }
        ExamScore score = scoreRepository.findLatest(attemptId)
                .orElseThrow(() -> invalid("该次作答尚未判分出成绩，不能发证"));
        if (!score.isPassed()) {
            BigDecimal total = score.getTotalScore() == null ? BigDecimal.ZERO : score.getTotalScore();
            throw invalid("考试未通过（总分 " + total.stripTrailingZeros().toPlainString() + "），不能发证");
        }
    }

    /**
     * 校验课程完成。
     *
     * @param userId 持证人用户 ID
     * @param courseId 课程 ID
     */
    private void verifyCourse(Long userId, Long courseId) {
        Enrollment enrollment = enrollmentRepository.findByCourseAndUser(courseId, userId)
                .orElseThrow(() -> invalid("持证人未报名该课程，不能按课程发证"));
        long chapters = courseRepository.countChapters(courseId);
        if (chapters <= 0) {
            throw invalid("课程尚无章节，无法认定完成");
        }
        List<LearningProgress> progresses = progressRepository.listByEnrollment(enrollment.getId());
        long completed = progresses.stream()
                .filter(progress -> progress.getPercent() != null && progress.getPercent() >= COMPLETED_PERCENT)
                .count();
        if (completed < chapters) {
            throw invalid("课程尚未学完（已完成 " + completed + "/" + chapters + " 章），不能发证");
        }
    }

    /**
     * 校验培训分配（M4 口径：存在性校验，完成度校验见类注释）。
     *
     * @param userId 持证人用户 ID
     * @param classId 培训班 ID
     */
    private void verifyTraining(Long userId, Long classId) {
        trainingAssignmentRepository.findByClassAndUser(classId, userId)
                .orElseThrow(() -> invalid("持证人未被分配到该培训班，不能按培训发证"));
    }

    /**
     * 来源类型的中文标签。
     *
     * @param source 来源类型
     * @return 标签
     */
    private String sourceLabel(CertSource source) {
        return switch (source) {
            case COURSE -> "课程完成";
            case EXAM -> "考试通过";
            case TRAINING -> "培训完成";
            default -> "手工";
        };
    }

    /**
     * 构造来源校验异常。
     *
     * @param message 提示语
     * @return 业务异常
     */
    private BizException invalid(String message) {
        return LmsErrorCode.exception(LmsErrorCode.CERTIFICATE_SOURCE_INVALID, message);
    }
}
