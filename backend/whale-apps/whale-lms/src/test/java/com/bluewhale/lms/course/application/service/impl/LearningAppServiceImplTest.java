package com.bluewhale.lms.course.application.service.impl;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.lms.common.LmsActor;
import com.bluewhale.lms.common.LmsErrorCode;
import com.bluewhale.lms.config.LmsProperties;
import com.bluewhale.lms.course.application.command.ProgressReportCommand;
import com.bluewhale.lms.course.application.view.EnrollmentView;
import com.bluewhale.lms.course.domain.model.Course;
import com.bluewhale.lms.course.domain.model.CourseChapter;
import com.bluewhale.lms.course.domain.model.CourseStatus;
import com.bluewhale.lms.course.domain.model.Enrollment;
import com.bluewhale.lms.course.domain.model.LearningProgress;
import com.bluewhale.lms.course.domain.repository.CourseRepository;
import com.bluewhale.lms.course.domain.repository.EnrollmentRepository;
import com.bluewhale.lms.course.domain.repository.ProgressRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link LearningAppServiceImpl} 单元测试：报名幂等、未发布拒报、进度只增不减与越权校验。
 *
 * @author BlueWhale
 */
class LearningAppServiceImplTest {

    private CourseRepository courseRepository;

    private EnrollmentRepository enrollmentRepository;

    private ProgressRepository progressRepository;

    private LmsProperties properties;

    private LearningAppServiceImpl service;

    private final LmsActor learner = new LmsActor(9L, "learner", Set.of(), Set.of());

    @BeforeEach
    void setUp() {
        courseRepository = mock(CourseRepository.class);
        enrollmentRepository = mock(EnrollmentRepository.class);
        progressRepository = mock(ProgressRepository.class);
        properties = new LmsProperties();
        service = new LearningAppServiceImpl(courseRepository, enrollmentRepository, progressRepository, properties);
    }

    @Test
    @DisplayName("未发布课程不可报名")
    void enrollShouldRequirePublishedCourse() {
        when(courseRepository.findById(1L)).thenReturn(Optional.of(course(CourseStatus.DRAFT)));
        BizException exception = assertThrows(BizException.class, () -> service.enroll(1L, learner));
        assertEquals(LmsErrorCode.COURSE_NOT_PUBLISHED, exception.getCode());
        verify(enrollmentRepository, never()).save(any(Enrollment.class));
    }

    @Test
    @DisplayName("报名幂等：重复报名返回既有记录，不重复落库")
    void enrollShouldBeIdempotent() {
        when(courseRepository.findById(1L)).thenReturn(Optional.of(course(CourseStatus.PUBLISHED)));
        Enrollment existing = enrollment(1L, 9L);
        existing.setId(55L);
        when(enrollmentRepository.findByCourseAndUser(1L, 9L)).thenReturn(Optional.empty())
                .thenReturn(Optional.of(existing));
        when(courseRepository.listChapters(1L)).thenReturn(List.of());
        when(progressRepository.listByEnrollment(55L)).thenReturn(List.of());

        EnrollmentView first = service.enroll(1L, learner);
        assertNotNull(first);
        verify(enrollmentRepository).save(any(Enrollment.class));

        EnrollmentView second = service.enroll(1L, learner);
        assertEquals(55L, second.getId());
        verify(enrollmentRepository).save(any(Enrollment.class));
    }

    @Test
    @DisplayName("进度只增不减：上报 30 后再报 80 生效，再报 20 保留 80")
    void reportProgressShouldKeepMax() {
        when(courseRepository.findById(1L)).thenReturn(Optional.of(course(CourseStatus.PUBLISHED)));
        Enrollment enrollment = enrollment(1L, 9L);
        enrollment.setId(55L);
        when(enrollmentRepository.findByCourseAndUser(1L, 9L)).thenReturn(Optional.of(enrollment));
        CourseChapter chapter = chapter(100L, 1L, "第一章");
        when(courseRepository.findChapterById(100L)).thenReturn(Optional.of(chapter));
        when(courseRepository.listChapters(1L)).thenReturn(List.of(chapter));

        when(progressRepository.find(55L, 100L)).thenReturn(Optional.empty())
                .thenReturn(Optional.of(progress(55L, 100L, 30)))
                .thenReturn(Optional.of(progress(55L, 100L, 80)));
        when(progressRepository.listByEnrollment(55L)).thenReturn(List.of(progress(55L, 100L, 30)))
                .thenReturn(List.of(progress(55L, 100L, 80)))
                .thenReturn(List.of(progress(55L, 100L, 80)));

        EnrollmentView first = service.reportProgress(1L, command(100L, 30), learner);
        assertEquals(30, first.getOverallPercent());

        EnrollmentView raised = service.reportProgress(1L, command(100L, 80), learner);
        assertEquals(80, raised.getOverallPercent());

        EnrollmentView lowered = service.reportProgress(1L, command(100L, 20), learner);
        assertEquals(80, lowered.getOverallPercent());
        assertEquals(80, lowered.getChapters().getFirst().getPercent());
    }

    @Test
    @DisplayName("未报名先报进度拒绝；章节不属于课程拒绝")
    void reportProgressShouldCheckEnrollmentAndChapter() {
        when(courseRepository.findById(1L)).thenReturn(Optional.of(course(CourseStatus.PUBLISHED)));
        when(enrollmentRepository.findByCourseAndUser(1L, 9L)).thenReturn(Optional.empty());
        BizException noEnrollment = assertThrows(BizException.class,
                () -> service.reportProgress(1L, command(100L, 50), learner));
        assertEquals(LmsErrorCode.ENROLLMENT_NOT_FOUND, noEnrollment.getCode());

        Enrollment enrollment = enrollment(1L, 9L);
        enrollment.setId(55L);
        when(enrollmentRepository.findByCourseAndUser(1L, 9L)).thenReturn(Optional.of(enrollment));
        CourseChapter otherCourseChapter = chapter(200L, 2L, "其他课程章节");
        when(courseRepository.findChapterById(200L)).thenReturn(Optional.of(otherCourseChapter));
        BizException wrongChapter = assertThrows(BizException.class,
                () -> service.reportProgress(1L, command(200L, 50), learner));
        assertEquals(LmsErrorCode.CHAPTER_NOT_IN_COURSE, wrongChapter.getCode());
    }

    @Test
    @DisplayName("缺身份写操作失败关闭")
    void writeShouldRequireActor() {
        LmsActor anonymous = LmsActor.anonymous();
        BizException exception = assertThrows(BizException.class, () -> service.enroll(1L, anonymous));
        assertEquals(LmsErrorCode.ACTOR_REQUIRED, exception.getCode());
        assertTrue(exception.getCode() == LmsErrorCode.ACTOR_REQUIRED);
    }

    /**
     * 构造进度上报命令。
     *
     * @param chapterId 章节 ID
     * @param percent 百分比
     * @return 命令
     */
    private ProgressReportCommand command(Long chapterId, int percent) {
        ProgressReportCommand command = new ProgressReportCommand();
        command.setChapterId(chapterId);
        command.setPercent(percent);
        return command;
    }

    /**
     * 构造课程。
     *
     * @param status 状态
     * @return 课程
     */
    private Course course(CourseStatus status) {
        Course course = new Course();
        course.setId(1L);
        course.setCourseCode("SAFE-01");
        course.setTitle("安全生产培训");
        course.setStatus(status);
        return course;
    }

    /**
     * 构造报名记录。
     *
     * @param courseId 课程 ID
     * @param userId 用户 ID
     * @return 报名记录
     */
    private Enrollment enrollment(Long courseId, Long userId) {
        Enrollment enrollment = new Enrollment();
        enrollment.setCourseId(courseId);
        enrollment.setUserId(userId);
        enrollment.setUsername("learner");
        return enrollment;
    }

    /**
     * 构造章节。
     *
     * @param id 章节 ID
     * @param courseId 课程 ID
     * @param title 标题
     * @return 章节
     */
    private CourseChapter chapter(Long id, Long courseId, String title) {
        CourseChapter chapter = new CourseChapter();
        chapter.setId(id);
        chapter.setCourseId(courseId);
        chapter.setTitle(title);
        return chapter;
    }

    /**
     * 构造进度。
     *
     * @param enrollmentId 学习记录 ID
     * @param chapterId 章节 ID
     * @param percent 百分比
     * @return 进度
     */
    private LearningProgress progress(Long enrollmentId, Long chapterId, int percent) {
        LearningProgress progress = new LearningProgress();
        progress.setEnrollmentId(enrollmentId);
        progress.setChapterId(chapterId);
        progress.setPercent(percent);
        return progress;
    }
}
