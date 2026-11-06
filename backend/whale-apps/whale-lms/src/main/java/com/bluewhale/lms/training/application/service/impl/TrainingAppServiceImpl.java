package com.bluewhale.lms.training.application.service.impl;

import com.bluewhale.common.core.result.PageResult;
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
import com.bluewhale.lms.training.application.command.TrainingClassSaveCommand;
import com.bluewhale.lms.training.application.command.TrainingClassStatusCommand;
import com.bluewhale.lms.training.application.service.TrainingAppService;
import com.bluewhale.lms.training.application.view.AssignmentResultView;
import com.bluewhale.lms.training.application.view.CompletionItemView;
import com.bluewhale.lms.training.application.view.LecturerHoursView;
import com.bluewhale.lms.training.application.view.SessionView;
import com.bluewhale.lms.training.application.view.TrainingClassDetailView;
import com.bluewhale.lms.training.application.view.TrainingClassView;
import com.bluewhale.lms.training.application.view.TrainingCompletionView;
import com.bluewhale.lms.training.domain.model.AssignmentScope;
import com.bluewhale.lms.training.domain.model.Checkin;
import com.bluewhale.lms.training.domain.model.ClassSession;
import com.bluewhale.lms.training.domain.model.LecturerHours;
import com.bluewhale.lms.training.domain.model.TrainingAssignment;
import com.bluewhale.lms.training.domain.model.TrainingClass;
import com.bluewhale.lms.training.domain.model.TrainingClassQuery;
import com.bluewhale.lms.training.domain.model.TrainingClassStatus;
import com.bluewhale.lms.training.domain.model.TrainingMode;
import com.bluewhale.lms.training.domain.repository.CheckinRepository;
import com.bluewhale.lms.training.domain.repository.LecturerHoursRepository;
import com.bluewhale.lms.training.domain.repository.TrainingAssignmentRepository;
import com.bluewhale.lms.training.domain.repository.TrainingClassRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 培训计划应用服务实现。
 *
 * <p>三条验收口径（REQ-FUNC-088）在此固化：</p>
 * <ol>
 *   <li><b>必修分配批量报名幂等</b>：{@code assign} 去重后逐人落分配，已存在计入
 *       {@code skipped}；关联课程时同步生成学习报名（同样幂等），重复调用不产生重复数据；</li>
 *   <li><b>签到仅限时间窗且留审计</b>：时间窗判定委托 {@link ClassSession#withinCheckinWindow}，
 *       补签必须留操作人与备注（{@code lr_checkin.operator_id/remark}）；</li>
 *   <li><b>课时合计 = 各场次之和</b>：每次场次保存都按「有效讲师 × 场次课时」重写台账，
 *       查询时按讲师聚合台账，天然等于场次课时之和。</li>
 * </ol>
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class TrainingAppServiceImpl implements TrainingAppService {

    private final TrainingClassRepository classRepository;

    private final TrainingAssignmentRepository assignmentRepository;

    private final LecturerHoursRepository lecturerHoursRepository;

    private final CheckinRepository checkinRepository;

    private final CourseRepository courseRepository;

    private final EnrollmentRepository enrollmentRepository;

    private final ProgressRepository progressRepository;

    private final LmsProperties properties;

    @Override
    public PageResult<TrainingClassView> page(TrainingClassQuery query, LmsActor actor) {
        assertGate();
        long current = Math.max(query.getCurrent(), 1L);
        long size = Math.min(Math.max(query.getSize(), 1L), properties.getCoursePageLimit());
        long total = classRepository.count(query);
        List<TrainingClassView> views = classRepository.page(query, (current - 1) * size, (int) size)
                .stream()
                .map(this::toView)
                .toList();
        return PageResult.of(views, total, current, size);
    }

    @Override
    public TrainingClassDetailView detail(Long classId, LmsActor actor) {
        assertGate();
        TrainingClass trainingClass = requireClass(classId);
        List<ClassSession> sessions = classRepository.listSessions(classId);
        TrainingClassDetailView view = TrainingClassDetailView.fromClass(trainingClass);
        view.setSessions(sessions.stream().map(SessionView::from).toList());
        view.setAssignedCount(assignmentRepository.countByClass(classId));
        view.setCheckinCount((long) checkinRepository.listByClass(classId, null).size());
        view.setTotalHours(sumHours(sessions));
        return view;
    }

    @Override
    @Transactional
    public Long create(TrainingClassSaveCommand command, LmsActor actor) {
        assertGate();
        actor.requireUserId();
        String code = TrainingClass.normalizeCode(command.getClassCode());
        classRepository.findByCode(code).ifPresent(existing -> {
            throw LmsErrorCode.exception(LmsErrorCode.TRAINING_CODE_CONFLICT);
        });
        TrainingClass trainingClass = new TrainingClass();
        applyCommand(trainingClass, command, code);
        trainingClass.setStatus(TrainingClassStatus.DRAFT);
        trainingClass.validatePeriod();
        return classRepository.save(trainingClass);
    }

    @Override
    @Transactional
    public void update(Long classId, TrainingClassSaveCommand command, LmsActor actor) {
        assertGate();
        actor.requireUserId();
        TrainingClass trainingClass = requireClass(classId);
        trainingClass.assertEditable();
        String code = TrainingClass.normalizeCode(command.getClassCode());
        if (!code.equals(trainingClass.getClassCode())) {
            classRepository.findByCode(code).ifPresent(existing -> {
                throw LmsErrorCode.exception(LmsErrorCode.TRAINING_CODE_CONFLICT);
            });
        }
        applyCommand(trainingClass, command, code);
        trainingClass.validatePeriod();
        classRepository.save(trainingClass);
    }

    @Override
    @Transactional
    public void changeStatus(Long classId, TrainingClassStatusCommand command, LmsActor actor) {
        assertGate();
        actor.requireUserId();
        TrainingClass trainingClass = requireClass(classId);
        String action = command.getAction() == null ? "" : command.getAction().trim().toUpperCase(Locale.ROOT);
        switch (action) {
            case "PUBLISH" -> trainingClass.publish(classRepository.countSessions(classId));
            case "CLOSE" -> trainingClass.close();
            default -> throw LmsErrorCode.exception(LmsErrorCode.TRAINING_STATE_CONFLICT,
                    "不支持的状态动作：" + command.getAction());
        }
        classRepository.save(trainingClass);
    }

    @Override
    @Transactional
    public void delete(Long classId, LmsActor actor) {
        assertGate();
        actor.requireUserId();
        TrainingClass trainingClass = requireClass(classId);
        if (!trainingClass.getStatus().canDelete()) {
            throw LmsErrorCode.exception(LmsErrorCode.TRAINING_STATE_CONFLICT, "仅草稿培训班可删除");
        }
        for (ClassSession session : classRepository.listSessions(classId)) {
            lecturerHoursRepository.deleteBySession(session.getId());
            classRepository.deleteSession(session.getId());
        }
        classRepository.delete(classId);
    }

    @Override
    @Transactional
    public Long addSession(Long classId, SessionSaveCommand command, LmsActor actor) {
        assertGate();
        actor.requireUserId();
        TrainingClass trainingClass = requireClass(classId);
        trainingClass.assertEditable();
        ClassSession session = new ClassSession();
        session.setClassId(classId);
        session.setSessionNo(command.getSessionNo() != null
                ? command.getSessionNo() : (int) classRepository.countSessions(classId) + 1);
        session.setTitle(command.getTitle() == null ? null : command.getTitle().trim());
        session.setStartTime(command.getStartTime());
        session.setEndTime(command.getEndTime());
        session.setLocation(command.getLocation());
        session.setLecturer(command.getLecturer());
        session.setHours(command.getHours());
        session.validate();
        Long sessionId = classRepository.saveSession(session);
        syncLecturerHours(session, trainingClass);
        return sessionId;
    }

    @Override
    @Transactional
    public void removeSession(Long sessionId, LmsActor actor) {
        assertGate();
        actor.requireUserId();
        ClassSession session = classRepository.findSessionById(sessionId)
                .orElseThrow(() -> LmsErrorCode.exception(LmsErrorCode.SESSION_NOT_FOUND));
        TrainingClass trainingClass = requireClass(session.getClassId());
        trainingClass.assertEditable();
        lecturerHoursRepository.deleteBySession(sessionId);
        classRepository.deleteSession(sessionId);
    }

    @Override
    @Transactional
    public AssignmentResultView assign(Long classId, AssignmentBatchCommand command, LmsActor actor) {
        assertGate();
        actor.requireUserId();
        TrainingClass trainingClass = requireClass(classId);
        trainingClass.assertAssignable();
        List<Long> userIds = normalizeUserIds(command.getUserIds());
        if (userIds.isEmpty()) {
            throw LmsErrorCode.exception(LmsErrorCode.ASSIGNMENT_EMPTY);
        }
        if (userIds.size() > properties.getAssignBatchLimit()) {
            throw LmsErrorCode.exception(LmsErrorCode.ASSIGNMENT_EMPTY,
                    "单次分配人数不得超过 " + properties.getAssignBatchLimit());
        }
        long assigned = assignmentRepository.countByClass(classId);
        if (trainingClass.getCapacity() != null && trainingClass.getCapacity() > 0
                && assigned + userIds.size() > trainingClass.getCapacity()) {
            throw LmsErrorCode.exception(LmsErrorCode.TRAINING_CAPACITY_EXCEEDED,
                    "计划人数 " + trainingClass.getCapacity() + "，当前已分配 " + assigned
                            + "，本次 " + userIds.size() + " 人将超员");
        }
        AssignmentScope scope = AssignmentScope.parse(command.getScopeType());
        String scopeValue = command.getScopeValue() == null ? null : command.getScopeValue().trim();
        int created = 0;
        int skipped = 0;
        for (Long userId : userIds) {
            if (assignmentRepository.findByClassAndUser(classId, userId).isPresent()) {
                skipped++;
                continue;
            }
            TrainingAssignment assignment = new TrainingAssignment();
            assignment.setClassId(classId);
            assignment.setScopeType(scope);
            assignment.setScopeValue(scopeValue);
            assignment.setUserId(userId);
            assignment.setAssignedAt(LocalDateTime.now());
            assignmentRepository.save(assignment);
            ensureCourseEnrollment(trainingClass, userId, actor);
            created++;
        }
        return AssignmentResultView.of(created, skipped);
    }

    @Override
    public List<LecturerHoursView> lecturerHours(Long classId, LmsActor actor) {
        assertGate();
        requireClass(classId);
        List<LecturerHours> entries = lecturerHoursRepository.listByClass(classId);
        Map<String, List<LecturerHours>> grouped = entries.stream()
                .collect(Collectors.groupingBy(LecturerHours::getLecturer,
                        LinkedHashMap::new, Collectors.toList()));
        List<LecturerHoursView> views = new ArrayList<>();
        grouped.forEach((lecturer, list) -> views.add(LecturerHoursView.of(
                lecturer,
                list.stream().map(LecturerHours::getSessionId).distinct().count(),
                list.stream()
                        .map(item -> item.getHours() == null ? BigDecimal.ZERO : item.getHours())
                        .reduce(BigDecimal.ZERO, BigDecimal::add))));
        return views;
    }

    @Override
    public TrainingCompletionView completion(Long classId, LmsActor actor) {
        assertGate();
        TrainingClass trainingClass = requireClass(classId);
        if (trainingClass.getStatus() == TrainingClassStatus.DRAFT) {
            throw LmsErrorCode.exception(LmsErrorCode.TRAINING_STATE_CONFLICT,
                    "草稿培训班没有可认定的学员，请先发布");
        }
        List<ClassSession> sessions = classRepository.listSessions(classId);
        Set<Long> sessionIds = sessions.stream()
                .map(ClassSession::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, Set<Long>> attended = new HashMap<>();
        for (Checkin checkin : checkinRepository.listByClass(classId, null)) {
            if (checkin.getUserId() == null || checkin.getSessionId() == null
                    || !sessionIds.contains(checkin.getSessionId())) {
                continue;
            }
            attended.computeIfAbsent(checkin.getUserId(), key -> new HashSet<>()).add(checkin.getSessionId());
        }
        Long courseId = trainingClass.getCourseId();
        long courseChapterTotal = courseId == null ? 0L : courseRepository.countChapters(courseId);
        List<CompletionItemView> items = new ArrayList<>();
        int completed = 0;
        for (TrainingAssignment assignment : assignmentRepository.listByClass(classId)) {
            CompletionItemView item = evaluate(sessionIds.size(), attended, courseId, courseChapterTotal, assignment);
            if (item.isCompleted()) {
                completed++;
            }
            items.add(item);
        }
        TrainingCompletionView view = new TrainingCompletionView();
        view.setClassId(classId);
        view.setTotal(items.size());
        view.setCompleted(completed);
        view.setCompletionRate(rate(completed, items.size()));
        view.setItems(items);
        return view;
    }

    /**
     * 认定单个学员：所有适用条件（场次签到、课程进度）同时满足才计完成。
     *
     * @param sessionsTotal 班级场次总数
     * @param attended 学员 → 已签到场次集合
     * @param courseId 关联课程 ID（可为空）
     * @param courseChapterTotal 关联课程章节总数
     * @param assignment 分配记录
     * @return 认定明细
     */
    private CompletionItemView evaluate(int sessionsTotal, Map<Long, Set<Long>> attended,
                                        Long courseId, long courseChapterTotal, TrainingAssignment assignment) {
        CompletionItemView item = new CompletionItemView();
        item.setUserId(assignment.getUserId());
        item.setUsername(assignment.getUsername());
        item.setAssignedAt(assignment.getAssignedAt());
        item.setSessionsTotal(sessionsTotal);
        int attendedCount = attended.getOrDefault(assignment.getUserId(), Set.of()).size();
        item.setSessionsAttended(attendedCount);
        boolean hasSessions = sessionsTotal > 0;
        boolean attendanceOk = !hasSessions || attendedCount >= sessionsTotal;
        boolean hasCourse = courseId != null;
        boolean courseOk = true;
        if (hasCourse) {
            int percent = coursePercent(courseId, courseChapterTotal, assignment.getUserId());
            item.setCoursePercent(percent);
            courseOk = courseChapterTotal > 0 && percent >= 100;
        }
        item.setCompleted((hasSessions || hasCourse) && attendanceOk && courseOk);
        item.setBasis(resolveBasis(hasSessions, hasCourse, attendanceOk, courseOk));
        return item;
    }

    /**
     * 计算学员在关联课程上的完成度：已完成章节数 ÷ 课程章节总数。
     *
     * <p>以章节总数为分母而不是「已上报进度条数」，避免只上报一章 100% 就被误判为整门课完成。</p>
     *
     * @param courseId 课程 ID
     * @param courseChapterTotal 课程章节总数
     * @param userId 学员用户 ID
     * @return 完成百分比（0–100）
     */
    private int coursePercent(Long courseId, long courseChapterTotal, Long userId) {
        if (courseChapterTotal <= 0) {
            return 0;
        }
        Long enrollmentId = enrollmentRepository.findByCourseAndUser(courseId, userId)
                .map(Enrollment::getId)
                .orElse(null);
        if (enrollmentId == null) {
            return 0;
        }
        long done = progressRepository.listByEnrollment(enrollmentId).stream()
                .filter(progress -> progress.getPercent() != null && progress.getPercent() >= 100)
                .map(LearningProgress::getChapterId)
                .distinct()
                .count();
        return (int) Math.min(100L, done * 100L / courseChapterTotal);
    }

    /**
     * 判定依据文案。
     *
     * @param hasSessions 是否有场次
     * @param hasCourse 是否关联课程
     * @param attendanceOk 签到是否达标
     * @param courseOk 课程进度是否达标
     * @return 依据
     */
    private String resolveBasis(boolean hasSessions, boolean hasCourse, boolean attendanceOk, boolean courseOk) {
        if (!hasSessions && !hasCourse) {
            return "NO_BASIS";
        }
        if (!attendanceOk) {
            return "ATTENDANCE_PENDING";
        }
        if (!courseOk) {
            return "COURSE_PROGRESS_PENDING";
        }
        if (hasSessions && hasCourse) {
            return "ATTENDANCE_AND_COURSE";
        }
        return hasSessions ? "ATTENDANCE" : "COURSE_PROGRESS";
    }

    /**
     * 完成率（0–100，保留 1 位小数；无学员按 0）。
     *
     * @param completed 完成人数
     * @param total 总人数
     * @return 完成率
     */
    private BigDecimal rate(int completed, int total) {
        if (total <= 0) {
            return BigDecimal.ZERO.setScale(1, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(completed * 100L)
                .divide(BigDecimal.valueOf(total), 1, RoundingMode.HALF_UP);
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
     * 加载培训班，不存在按 404 处理。
     *
     * @param classId 培训班 ID
     * @return 培训班
     */
    private TrainingClass requireClass(Long classId) {
        return classRepository.findById(classId)
                .orElseThrow(() -> LmsErrorCode.exception(LmsErrorCode.TRAINING_NOT_FOUND));
    }

    /**
     * 将保存命令写入培训班（编码已归一）。
     *
     * @param trainingClass 目标培训班
     * @param command 保存命令
     * @param code 规范化编码
     */
    private void applyCommand(TrainingClass trainingClass, TrainingClassSaveCommand command, String code) {
        trainingClass.setClassCode(code);
        trainingClass.setTitle(command.getTitle() == null ? null : command.getTitle().trim());
        trainingClass.setCourseId(resolveCourseId(command.getCourseId()));
        trainingClass.setMode(TrainingMode.parse(command.getMode()));
        trainingClass.setLecturer(command.getLecturer());
        trainingClass.setStartDate(command.getStartDate());
        trainingClass.setEndDate(command.getEndDate());
        trainingClass.setLocation(command.getLocation());
        trainingClass.setCapacity(command.getCapacity());
        trainingClass.setRemark(command.getRemark());
    }

    /**
     * 校验关联课程存在（为空表示不关联）。
     *
     * @param courseId 课程 ID
     * @return 课程 ID
     */
    private Long resolveCourseId(Long courseId) {
        if (courseId == null) {
            return null;
        }
        courseRepository.findById(courseId)
                .orElseThrow(() -> LmsErrorCode.exception(LmsErrorCode.COURSE_NOT_FOUND));
        return courseId;
    }

    /**
     * 分配时同步生成关联课程的学习报名（幂等）。
     *
     * @param trainingClass 培训班
     * @param userId 学员用户 ID
     * @param actor 操作人
     */
    private void ensureCourseEnrollment(TrainingClass trainingClass, Long userId, LmsActor actor) {
        Long courseId = trainingClass.getCourseId();
        if (courseId == null) {
            return;
        }
        if (enrollmentRepository.findByCourseAndUser(courseId, userId).isPresent()) {
            return;
        }
        Enrollment enrollment = new Enrollment();
        enrollment.setCourseId(courseId);
        enrollment.setUserId(userId);
        enrollment.setUsername(actor.getUsername());
        enrollment.setEnrolledAt(LocalDateTime.now());
        enrollmentRepository.save(enrollment);
    }

    /**
     * 按场次重写讲师课时台账。
     *
     * @param session 场次
     * @param trainingClass 培训班（用于继承班级讲师）
     */
    private void syncLecturerHours(ClassSession session, TrainingClass trainingClass) {
        LecturerHours entry = LecturerHours.of(session, session.effectiveLecturer(trainingClass.getLecturer()));
        lecturerHoursRepository.replaceBySession(session.getId(),
                entry == null ? List.of() : List.of(entry));
    }

    /**
     * 汇总场次课时。
     *
     * @param sessions 场次列表
     * @return 课时合计
     */
    private BigDecimal sumHours(List<ClassSession> sessions) {
        return sessions.stream()
                .map(item -> item.getHours() == null ? BigDecimal.ZERO : item.getHours())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * 名单去重（保持顺序、过滤空值）。
     *
     * @param userIds 原始名单
     * @return 去重后的名单
     */
    private List<Long> normalizeUserIds(List<Long> userIds) {
        if (userIds == null) {
            return List.of();
        }
        LinkedHashSet<Long> unique = new LinkedHashSet<>();
        for (Long userId : userIds) {
            if (userId != null && userId > 0) {
                unique.add(userId);
            }
        }
        return new ArrayList<>(unique);
    }

    /**
     * 领域转列表视图（含统计）。
     *
     * @param trainingClass 培训班
     * @return 视图
     */
    private TrainingClassView toView(TrainingClass trainingClass) {
        TrainingClassView view = TrainingClassView.from(trainingClass);
        view.setAssignedCount(assignmentRepository.countByClass(trainingClass.getId()));
        view.setSessionCount(classRepository.countSessions(trainingClass.getId()));
        return view;
    }
}
