package com.bluewhale.lms.training.application.service.impl;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.lms.common.LmsActor;
import com.bluewhale.lms.common.LmsErrorCode;
import com.bluewhale.lms.config.LmsProperties;
import com.bluewhale.lms.course.domain.model.Enrollment;
import com.bluewhale.lms.course.domain.model.LearningProgress;
import com.bluewhale.lms.course.domain.repository.CourseRepository;
import com.bluewhale.lms.course.domain.repository.EnrollmentRepository;
import com.bluewhale.lms.course.domain.repository.ProgressRepository;
import com.bluewhale.lms.training.application.command.AssignmentBatchCommand;
import com.bluewhale.lms.training.application.command.SessionSaveCommand;
import com.bluewhale.lms.training.application.view.AssignmentResultView;
import com.bluewhale.lms.training.application.view.CompletionItemView;
import com.bluewhale.lms.training.application.view.LecturerHoursView;
import com.bluewhale.lms.training.application.view.TrainingCompletionView;
import com.bluewhale.lms.training.domain.model.Checkin;
import com.bluewhale.lms.training.domain.model.ClassSession;
import com.bluewhale.lms.training.domain.model.LecturerHours;
import com.bluewhale.lms.training.domain.model.TrainingAssignment;
import com.bluewhale.lms.training.domain.model.TrainingClass;
import com.bluewhale.lms.training.domain.model.TrainingClassStatus;
import com.bluewhale.lms.training.domain.model.TrainingMode;
import com.bluewhale.lms.training.domain.repository.CheckinRepository;
import com.bluewhale.lms.training.domain.repository.LecturerHoursRepository;
import com.bluewhale.lms.training.domain.repository.TrainingAssignmentRepository;
import com.bluewhale.lms.training.domain.repository.TrainingClassRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link TrainingAppServiceImpl} 单元测试：必修分配幂等、容量校验、课时合计、发布冻结。
 *
 * @author BlueWhale
 */
class TrainingAppServiceImplTest {

    private TrainingClassRepository classRepository;

    private TrainingAssignmentRepository assignmentRepository;

    private LecturerHoursRepository lecturerHoursRepository;

    private CheckinRepository checkinRepository;

    private CourseRepository courseRepository;

    private EnrollmentRepository enrollmentRepository;

    private ProgressRepository progressRepository;

    private TrainingAppServiceImpl service;

    private final LmsActor admin = new LmsActor(7L, "lmsadmin", Set.of("lms_admin"), Set.of());

    @BeforeEach
    void setUp() {
        classRepository = mock(TrainingClassRepository.class);
        assignmentRepository = mock(TrainingAssignmentRepository.class);
        lecturerHoursRepository = mock(LecturerHoursRepository.class);
        checkinRepository = mock(CheckinRepository.class);
        courseRepository = mock(CourseRepository.class);
        enrollmentRepository = mock(EnrollmentRepository.class);
        progressRepository = mock(ProgressRepository.class);
        service = new TrainingAppServiceImpl(classRepository, assignmentRepository, lecturerHoursRepository,
                checkinRepository, courseRepository, enrollmentRepository, progressRepository, new LmsProperties());
    }

    @Test
    @DisplayName("必修分配幂等：已分配计入 skipped，仅新人落库并生成报名")
    void assignShouldBeIdempotent() {
        TrainingClass trainingClass = trainingClass(TrainingClassStatus.PUBLISHED, 1L);
        when(classRepository.findById(100L)).thenReturn(Optional.of(trainingClass));
        when(assignmentRepository.countByClass(100L)).thenReturn(1L);
        when(assignmentRepository.findByClassAndUser(100L, 9L)).thenReturn(Optional.empty());
        when(assignmentRepository.findByClassAndUser(100L, 10L))
                .thenReturn(Optional.of(new TrainingAssignment()));

        AssignmentResultView result = service.assign(100L, command(List.of(9L, 10L, 9L)), admin);

        assertEquals(1, result.getCreated());
        assertEquals(1, result.getSkipped());
        ArgumentCaptor<TrainingAssignment> captor = ArgumentCaptor.forClass(TrainingAssignment.class);
        verify(assignmentRepository, times(1)).save(captor.capture());
        assertEquals(9L, captor.getValue().getUserId());
        verify(enrollmentRepository, times(1)).save(any(Enrollment.class));
    }

    @Test
    @DisplayName("未发布培训班不可分配")
    void assignShouldRequirePublishedClass() {
        when(classRepository.findById(100L)).thenReturn(Optional.of(trainingClass(TrainingClassStatus.DRAFT, null)));
        BizException exception = assertThrows(BizException.class,
                () -> service.assign(100L, command(List.of(9L)), admin));
        assertEquals(LmsErrorCode.TRAINING_STATE_CONFLICT, exception.getCode());
        verify(assignmentRepository, never()).save(any(TrainingAssignment.class));
    }

    @Test
    @DisplayName("分配名单为空直接拒绝")
    void assignShouldRejectEmptyList() {
        when(classRepository.findById(100L)).thenReturn(Optional.of(trainingClass(TrainingClassStatus.PUBLISHED, null)));
        BizException exception = assertThrows(BizException.class,
                () -> service.assign(100L, command(List.of()), admin));
        assertEquals(LmsErrorCode.ASSIGNMENT_EMPTY, exception.getCode());
    }

    @Test
    @DisplayName("分配人数超计划人数时拒绝")
    void assignShouldRejectOverCapacity() {
        TrainingClass trainingClass = trainingClass(TrainingClassStatus.PUBLISHED, null);
        trainingClass.setCapacity(2);
        when(classRepository.findById(100L)).thenReturn(Optional.of(trainingClass));
        when(assignmentRepository.countByClass(100L)).thenReturn(2L);
        BizException exception = assertThrows(BizException.class,
                () -> service.assign(100L, command(List.of(9L)), admin));
        assertEquals(LmsErrorCode.TRAINING_CAPACITY_EXCEEDED, exception.getCode());
    }

    @Test
    @DisplayName("未关联课程时只落分配，不生成学习报名")
    void assignShouldSkipEnrollmentWithoutCourse() {
        when(classRepository.findById(100L)).thenReturn(Optional.of(trainingClass(TrainingClassStatus.PUBLISHED, null)));
        when(assignmentRepository.findByClassAndUser(100L, 9L)).thenReturn(Optional.empty());
        service.assign(100L, command(List.of(9L)), admin);
        verify(enrollmentRepository, never()).save(any(Enrollment.class));
    }

    @Test
    @DisplayName("课时合计等于各场次课时之和")
    void lecturerHoursShouldEqualSumOfSessions() {
        when(classRepository.findById(100L)).thenReturn(Optional.of(trainingClass(TrainingClassStatus.PUBLISHED, null)));
        when(lecturerHoursRepository.listByClass(100L)).thenReturn(List.of(
                hours(300L, "张老师", "2.5"),
                hours(301L, "张老师", "3.0"),
                hours(302L, "张老师", "1.5"),
                hours(303L, "李老师", "4.0")));

        List<LecturerHoursView> views = service.lecturerHours(100L, admin);

        assertEquals(2, views.size());
        LecturerHoursView zhang = views.stream()
                .filter(view -> "张老师".equals(view.getLecturer())).findFirst().orElseThrow();
        assertEquals(new BigDecimal("7.0"), zhang.getTotalHours());
        assertEquals(3L, zhang.getSessionCount());
    }

    @Test
    @DisplayName("新增场次按有效讲师写课时台账（继承班级讲师）")
    void addSessionShouldSyncLecturerHours() {
        TrainingClass trainingClass = trainingClass(TrainingClassStatus.DRAFT, null);
        trainingClass.setLecturer("李老师");
        when(classRepository.findById(100L)).thenReturn(Optional.of(trainingClass));
        when(classRepository.countSessions(100L)).thenReturn(0L);
        when(classRepository.saveSession(any(ClassSession.class))).thenAnswer(invocation -> {
            ClassSession saved = invocation.getArgument(0);
            saved.setId(300L);
            return 300L;
        });

        service.addSession(100L, sessionCommand(), admin);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<LecturerHours>> captor = ArgumentCaptor.forClass(List.class);
        verify(lecturerHoursRepository).replaceBySession(eq(300L), captor.capture());
        assertEquals(1, captor.getValue().size());
        assertEquals("李老师", captor.getValue().getFirst().getLecturer());
        assertEquals(new BigDecimal("2.5"), captor.getValue().getFirst().getHours());
    }

    @Test
    @DisplayName("已发布培训班不可再改场次")
    void addSessionShouldRejectPublishedClass() {
        when(classRepository.findById(100L)).thenReturn(Optional.of(trainingClass(TrainingClassStatus.PUBLISHED, null)));
        BizException exception = assertThrows(BizException.class,
                () -> service.addSession(100L, sessionCommand(), admin));
        assertEquals(LmsErrorCode.TRAINING_STATE_CONFLICT, exception.getCode());
        verify(classRepository, never()).saveSession(any(ClassSession.class));
    }

    @Test
    @DisplayName("完成认定：面授班要求全部场次签到，完成率按人数折算")
    void completionShouldRequireAllSessionsAttended() {
        when(classRepository.findById(100L)).thenReturn(Optional.of(trainingClass(TrainingClassStatus.PUBLISHED, null)));
        when(classRepository.listSessions(100L)).thenReturn(List.of(session(300L, 100L), session(301L, 100L)));
        when(assignmentRepository.listByClass(100L)).thenReturn(List.of(
                assignment(9L, "zhangsan"), assignment(10L, "lisi")));
        when(checkinRepository.listByClass(100L, null)).thenReturn(List.of(
                checkin(300L, 9L), checkin(301L, 9L), checkin(300L, 10L)));

        TrainingCompletionView view = service.completion(100L, admin);

        assertEquals(2, view.getTotal());
        assertEquals(1, view.getCompleted());
        assertEquals(new BigDecimal("50.0"), view.getCompletionRate());
        CompletionItemView first = view.getItems().getFirst();
        assertEquals(2, first.getSessionsAttended());
        assertTrue(first.isCompleted());
        assertEquals("ATTENDANCE", first.getBasis());
        CompletionItemView second = view.getItems().get(1);
        assertEquals(1, second.getSessionsAttended());
        assertFalse(second.isCompleted());
        assertEquals("ATTENDANCE_PENDING", second.getBasis());
    }

    @Test
    @DisplayName("完成认定：线上班按课程章节完成度判定，只完成一章不算整门课完成")
    void completionShouldUseCourseProgressForOnlineClass() {
        when(classRepository.findById(100L)).thenReturn(Optional.of(trainingClass(TrainingClassStatus.PUBLISHED, 200L)));
        when(classRepository.listSessions(100L)).thenReturn(List.of());
        when(assignmentRepository.listByClass(100L)).thenReturn(List.of(
                assignment(9L, "zhangsan"), assignment(10L, "lisi"), assignment(11L, "wangwu")));
        when(checkinRepository.listByClass(100L, null)).thenReturn(List.of());
        when(courseRepository.countChapters(200L)).thenReturn(2L);
        when(enrollmentRepository.findByCourseAndUser(200L, 9L)).thenReturn(Optional.of(enrollment(50L)));
        when(enrollmentRepository.findByCourseAndUser(200L, 10L)).thenReturn(Optional.of(enrollment(51L)));
        when(progressRepository.listByEnrollment(50L)).thenReturn(List.of(
                progress(1L, 100), progress(2L, 100)));
        when(progressRepository.listByEnrollment(51L)).thenReturn(List.of(
                progress(1L, 100), progress(2L, 40)));

        TrainingCompletionView view = service.completion(100L, admin);

        assertEquals(1, view.getCompleted());
        assertEquals(100, view.getItems().getFirst().getCoursePercent());
        assertEquals("COURSE_PROGRESS", view.getItems().getFirst().getBasis());
        assertEquals(50, view.getItems().get(1).getCoursePercent());
        assertEquals("COURSE_PROGRESS_PENDING", view.getItems().get(1).getBasis());
        assertEquals(0, view.getItems().get(2).getCoursePercent());
    }

    @Test
    @DisplayName("完成认定：混合班须签到与课程进度同时满足")
    void completionShouldRequireBothForBlendedClass() {
        TrainingClass trainingClass = trainingClass(TrainingClassStatus.PUBLISHED, 200L);
        trainingClass.setMode(TrainingMode.BLENDED);
        when(classRepository.findById(100L)).thenReturn(Optional.of(trainingClass));
        when(classRepository.listSessions(100L)).thenReturn(List.of(session(300L, 100L)));
        when(assignmentRepository.listByClass(100L)).thenReturn(List.of(assignment(9L, "zhangsan")));
        when(checkinRepository.listByClass(100L, null)).thenReturn(List.of(checkin(300L, 9L)));
        when(courseRepository.countChapters(200L)).thenReturn(2L);
        when(enrollmentRepository.findByCourseAndUser(200L, 9L)).thenReturn(Optional.of(enrollment(50L)));
        when(progressRepository.listByEnrollment(50L)).thenReturn(List.of(
                progress(1L, 100), progress(2L, 50)));

        TrainingCompletionView view = service.completion(100L, admin);

        assertEquals(0, view.getCompleted());
        assertEquals("COURSE_PROGRESS_PENDING", view.getItems().getFirst().getBasis());
    }

    @Test
    @DisplayName("完成认定：既无场次又未关联课程时不可认定")
    void completionShouldMarkNoBasisWhenNothingToCheck() {
        when(classRepository.findById(100L)).thenReturn(Optional.of(trainingClass(TrainingClassStatus.PUBLISHED, null)));
        when(classRepository.listSessions(100L)).thenReturn(List.of());
        when(assignmentRepository.listByClass(100L)).thenReturn(List.of(assignment(9L, "zhangsan")));
        when(checkinRepository.listByClass(100L, null)).thenReturn(List.of());

        TrainingCompletionView view = service.completion(100L, admin);

        assertEquals(0, view.getCompleted());
        assertEquals(new BigDecimal("0.0"), view.getCompletionRate());
        assertEquals("NO_BASIS", view.getItems().getFirst().getBasis());
    }

    @Test
    @DisplayName("完成认定：草稿培训班拒绝计算")
    void completionShouldRejectDraftClass() {
        when(classRepository.findById(100L)).thenReturn(Optional.of(trainingClass(TrainingClassStatus.DRAFT, null)));
        BizException exception = assertThrows(BizException.class,
                () -> service.completion(100L, admin));
        assertEquals(LmsErrorCode.TRAINING_STATE_CONFLICT, exception.getCode());
    }

    /**
     * 构造分配命令。
     *
     * @param userIds 学员 ID 列表
     * @return 命令
     */
    private AssignmentBatchCommand command(List<Long> userIds) {
        AssignmentBatchCommand command = new AssignmentBatchCommand();
        command.setScopeType("USER");
        command.setUserIds(userIds);
        return command;
    }

    /**
     * 构造场次命令。
     *
     * @return 场次命令
     */
    private SessionSaveCommand sessionCommand() {
        SessionSaveCommand command = new SessionSaveCommand();
        command.setTitle("开班第一讲");
        command.setStartTime(LocalDateTime.of(2026, 11, 10, 9, 0));
        command.setEndTime(LocalDateTime.of(2026, 11, 10, 11, 30));
        command.setHours(new BigDecimal("2.5"));
        return command;
    }

    /**
     * 构造培训班。
     *
     * @param status 状态
     * @param courseId 关联课程 ID
     * @return 培训班
     */
    private TrainingClass trainingClass(TrainingClassStatus status, Long courseId) {
        TrainingClass trainingClass = new TrainingClass();
        trainingClass.setId(100L);
        trainingClass.setClassCode("TR-2026-001");
        trainingClass.setTitle("新员工入职培训");
        trainingClass.setMode(TrainingMode.OFFLINE);
        trainingClass.setCourseId(courseId);
        trainingClass.setStartDate(LocalDate.of(2026, 11, 10));
        trainingClass.setEndDate(LocalDate.of(2026, 11, 12));
        trainingClass.setStatus(status);
        return trainingClass;
    }

    /**
     * 构造课时台账。
     *
     * @param sessionId 场次 ID
     * @param lecturer 讲师
     * @param hours 课时
     * @return 台账记录
     */
    private LecturerHours hours(Long sessionId, String lecturer, String hours) {
        LecturerHours entry = new LecturerHours();
        entry.setClassId(100L);
        entry.setSessionId(sessionId);
        entry.setLecturer(lecturer);
        entry.setHours(new BigDecimal(hours));
        return entry;
    }

    /**
     * 构造分配记录。
     *
     * @param userId 学员用户 ID
     * @param username 用户名
     * @return 分配记录
     */
    private TrainingAssignment assignment(Long userId, String username) {
        TrainingAssignment assignment = new TrainingAssignment();
        assignment.setClassId(100L);
        assignment.setUserId(userId);
        assignment.setUsername(username);
        assignment.setAssignedAt(LocalDateTime.of(2026, 11, 1, 10, 0));
        return assignment;
    }

    /**
     * 构造场次。
     *
     * @param sessionId 场次 ID
     * @param classId 培训班 ID
     * @return 场次
     */
    private ClassSession session(Long sessionId, Long classId) {
        ClassSession session = new ClassSession();
        session.setId(sessionId);
        session.setClassId(classId);
        session.setSessionNo(1);
        session.setHours(new BigDecimal("2.0"));
        return session;
    }

    /**
     * 构造签到记录。
     *
     * @param sessionId 场次 ID
     * @param userId 学员用户 ID
     * @return 签到记录
     */
    private Checkin checkin(Long sessionId, Long userId) {
        Checkin checkin = new Checkin();
        checkin.setClassId(100L);
        checkin.setSessionId(sessionId);
        checkin.setUserId(userId);
        return checkin;
    }

    /**
     * 构造学习报名。
     *
     * @param enrollmentId 报名 ID
     * @return 报名
     */
    private Enrollment enrollment(Long enrollmentId) {
        Enrollment enrollment = new Enrollment();
        enrollment.setId(enrollmentId);
        enrollment.setCourseId(200L);
        return enrollment;
    }

    /**
     * 构造章节进度。
     *
     * @param chapterId 章节 ID
     * @param percent 完成百分比
     * @return 进度
     */
    private LearningProgress progress(Long chapterId, int percent) {
        LearningProgress progress = new LearningProgress();
        progress.setChapterId(chapterId);
        progress.setPercent(percent);
        return progress;
    }
}
