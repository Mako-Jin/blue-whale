package com.bluewhale.lms.course.application.service.impl;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.lms.common.LmsActor;
import com.bluewhale.lms.common.LmsErrorCode;
import com.bluewhale.lms.config.LmsProperties;
import com.bluewhale.lms.course.application.command.ProgressReportCommand;
import com.bluewhale.lms.course.application.service.LearningAppService;
import com.bluewhale.lms.course.application.view.EnrollmentView;
import com.bluewhale.lms.course.application.view.LearningMetricsView;
import com.bluewhale.lms.course.domain.model.Course;
import com.bluewhale.lms.course.domain.model.CourseChapter;
import com.bluewhale.lms.course.domain.model.Enrollment;
import com.bluewhale.lms.course.domain.model.LearningProgress;
import com.bluewhale.lms.course.domain.repository.CourseRepository;
import com.bluewhale.lms.course.domain.repository.EnrollmentRepository;
import com.bluewhale.lms.course.domain.repository.ProgressRepository;
import com.bluewhale.lms.credit.application.service.CreditAppService;
import com.bluewhale.lms.credit.domain.model.CreditSource;
import com.bluewhale.lms.integration.application.service.NotifyAppService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 学习应用服务实现。
 *
 * <p>规则（REQ-FUNC-087 验收口径）：仅发布课程可报名；同一（租户、课程、学员）
 * 报名幂等返回既有记录；进度只增不减（重复上报取较大值）；未报名先报进度按 404 语义拒绝；
 * 章节必须属于所报课程。</p>
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class LearningAppServiceImpl implements LearningAppService {

    private final CourseRepository courseRepository;

    private final EnrollmentRepository enrollmentRepository;

    private final ProgressRepository progressRepository;

    private final NotifyAppService notifyAppService;

    private final CreditAppService creditAppService;

    private final LmsProperties properties;

    @Override
    @Transactional
    public EnrollmentView enroll(Long courseId, LmsActor actor) {
        assertGate();
        Long userId = actor.requireUserId();
        Course course = requireCourse(courseId);
        if (!course.getStatus().isPublished()) {
            throw LmsErrorCode.exception(LmsErrorCode.COURSE_NOT_PUBLISHED);
        }
        Optional<Enrollment> existing = enrollmentRepository.findByCourseAndUser(courseId, userId);
        Enrollment enrollment = existing.orElseGet(() -> {
            Enrollment created = new Enrollment();
            created.setCourseId(courseId);
            created.setUserId(userId);
            created.setUsername(actor.getUsername());
            created.setEnrolledAt(LocalDateTime.now());
            enrollmentRepository.save(created);
            return created;
        });
        if (existing.isEmpty()) {
            // 仅首次报名通知；重复报名（幂等返回既有记录）不再打扰
            notifyAppService.notifyEnroll(courseId, course.getTitle(), userId);
        }
        return withProgress(enrollment);
    }

    @Override
    public PageResult<EnrollmentView> myPage(long current, long size, LmsActor actor) {
        assertGate();
        Long userId = actor.requireUserId();
        long safeCurrent = Math.max(current, 1L);
        int safeSize = (int) Math.min(Math.max(size, 1L), 100);
        long total = enrollmentRepository.countByUser(userId);
        List<EnrollmentView> views = enrollmentRepository.pageByUser(userId, (safeCurrent - 1) * safeSize, safeSize)
                .stream()
                .map(this::withProgress)
                .toList();
        return PageResult.of(views, total, safeCurrent, safeSize);
    }

    @Override
    @Transactional
    public EnrollmentView reportProgress(Long courseId, ProgressReportCommand command, LmsActor actor) {
        assertGate();
        Long userId = actor.requireUserId();
        requireCourse(courseId);
        Enrollment enrollment = enrollmentRepository.findByCourseAndUser(courseId, userId)
                .orElseThrow(() -> LmsErrorCode.exception(LmsErrorCode.ENROLLMENT_NOT_FOUND));
        CourseChapter chapter = courseRepository.findChapterById(command.getChapterId())
                .filter(item -> item.getCourseId().equals(courseId))
                .orElseThrow(() -> LmsErrorCode.exception(LmsErrorCode.CHAPTER_NOT_IN_COURSE));
        LearningProgress progress = progressRepository.find(enrollment.getId(), chapter.getId())
                .orElseGet(() -> {
                    LearningProgress created = new LearningProgress();
                    created.setEnrollmentId(enrollment.getId());
                    created.setChapterId(chapter.getId());
                    created.setPercent(0);
                    return created;
                });
        progress.merge(command.getPercent());
        progress.setLastLearnedAt(LocalDateTime.now());
        progressRepository.save(progress);
        EnrollmentView view = withProgress(enrollment);
        accrueWhenCompleted(view, courseId, userId, actor.getUsername());
        return view;
    }

    @Override
    public LearningMetricsView metrics() {
        assertGate();
        LearningMetricsView view = new LearningMetricsView();
        view.setEnrollTotal(enrollmentRepository.countAll());
        view.setLearnerTotal(enrollmentRepository.countDistinctLearners());
        view.setLearnerCompleted(enrollmentRepository.countDistinctCompletedLearners());
        return view;
    }

    /**
     * 课程学满时自动记学分。
     *
     * <p>三条约束：</p>
     * <ol>
     *   <li><b>只在整体进度达 100% 时触发</b>：中途每次上报都记账会把一门课记成十几次；</li>
     *   <li><b>失败不影响进度上报</b>：学分是附加奖励，进度是学员的劳动成果，
     *       不能因为「没配学分规则」把学习进度一起回滚掉；</li>
     *   <li><b>未配规则静默跳过</b>：规则由管理员另行维护，学员不该看到「未配置学分规则」这种提示。</li>
     * </ol>
     *
     * @param view 上报后的学习视图
     * @param courseId 课程 ID
     * @param userId 学员用户 ID
     * @param username 学员用户名（快照到台账便于展示）
     */
    private void accrueWhenCompleted(EnrollmentView view, Long courseId, Long userId, String username) {
        if (view.getOverallPercent() == null || view.getOverallPercent() < properties.getProgressMaxPercent()) {
            return;
        }
        creditAppService.accrueQuietly(CreditSource.COURSE, courseId, userId, username);
    }

    /**
     * 断言需求门禁通过。
     */
    private void assertGate() {
        if (!properties.isDemandGatePassed()) {
            throw LmsErrorCode.exception(LmsErrorCode.GATE_CLOSED);
        }
    }

    /**
     * 加载课程，不存在按 404 处理。
     *
     * @param courseId 课程 ID
     * @return 课程
     */
    private Course requireCourse(Long courseId) {
        return courseRepository.findById(courseId)
                .orElseThrow(() -> LmsErrorCode.exception(LmsErrorCode.COURSE_NOT_FOUND));
    }

    /**
     * 组装学习视图：课程信息 + 整体进度 + 章节明细。
     *
     * @param enrollment 报名记录
     * @return 学习视图
     */
    private EnrollmentView withProgress(Enrollment enrollment) {
        EnrollmentView view = EnrollmentView.from(enrollment);
        courseRepository.findById(enrollment.getCourseId()).ifPresent(course -> {
            view.setCourseTitle(course.getTitle());
            view.setCourseStatus(course.getStatus().name());
        });
        List<CourseChapter> chapters = courseRepository.listChapters(enrollment.getCourseId());
        List<LearningProgress> progressList = progressRepository.listByEnrollment(enrollment.getId());
        List<EnrollmentView.ChapterProgressView> chapterViews = chapters.stream()
                .map(chapter -> EnrollmentView.ChapterProgressView.from(chapter.getId(), chapter.getTitle(),
                        progressList.stream()
                                .filter(progress -> progress.getChapterId().equals(chapter.getId()))
                                .findFirst()
                                .orElse(null)))
                .toList();
        view.setChapters(chapterViews);
        view.setOverallPercent(overallPercent(chapters.size(), progressList));
        return view;
    }

    /**
     * 计算课程整体完成百分比（章节完成度均值，向下取整；无章节为 0）。
     *
     * @param chapterCount 章节数
     * @param progressList 进度列表
     * @return 整体百分比
     */
    private int overallPercent(int chapterCount, List<LearningProgress> progressList) {
        if (chapterCount <= 0) {
            return 0;
        }
        long sum = progressList.stream()
                .mapToInt(progress -> progress.getPercent() == null ? 0 : progress.getPercent())
                .sum();
        return (int) (sum / chapterCount);
    }
}
