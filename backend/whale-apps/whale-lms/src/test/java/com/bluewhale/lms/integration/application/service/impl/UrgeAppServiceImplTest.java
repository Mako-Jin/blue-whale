package com.bluewhale.lms.integration.application.service.impl;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.lms.common.LmsActor;
import com.bluewhale.lms.common.LmsErrorCode;
import com.bluewhale.lms.config.LmsProperties;
import com.bluewhale.lms.integration.application.service.NotifyAppService;
import com.bluewhale.lms.integration.application.view.ScheduleConflictView;
import com.bluewhale.lms.integration.application.view.UrgeItemView;
import com.bluewhale.lms.integration.application.view.UrgeRunView;
import com.bluewhale.lms.integration.domain.gateway.LeaveGateway;
import com.bluewhale.lms.integration.domain.model.LeaveConflictEntry;
import com.bluewhale.lms.integration.domain.model.MemberSnapshot;
import com.bluewhale.lms.integration.domain.model.MemberStatus;
import com.bluewhale.lms.integration.domain.repository.MemberSnapshotRepository;
import com.bluewhale.lms.training.application.service.TrainingAppService;
import com.bluewhale.lms.training.application.view.CompletionItemView;
import com.bluewhale.lms.training.application.view.TrainingCompletionView;
import com.bluewhale.lms.training.domain.model.ClassSession;
import com.bluewhale.lms.training.domain.model.TrainingAssignment;
import com.bluewhale.lms.training.domain.model.TrainingClass;
import com.bluewhale.lms.training.domain.repository.TrainingAssignmentRepository;
import com.bluewhale.lms.training.domain.repository.TrainingClassRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 催办：离职与未收录跳过、全完成不打扰、单班失败不拖垮整轮、预览不投递。
 *
 * @author BlueWhale
 */
class UrgeAppServiceImplTest {

    /** 班级 ID */
    private static final long CLASS_ID = 9L;

    /** 催办日期 */
    private static final LocalDate DAY = LocalDate.of(2026, 11, 6);

    private TrainingClassRepository trainingClassRepository;

    private TrainingAssignmentRepository trainingAssignmentRepository;

    private TrainingAppService trainingAppService;

    private MemberSnapshotRepository memberSnapshotRepository;

    private NotifyAppService notifyAppService;

    private LeaveGateway leaveGateway;

    private UrgeAppServiceImpl service;

    private LmsActor admin;

    @BeforeEach
    void setUp() {
        trainingClassRepository = mock(TrainingClassRepository.class);
        trainingAssignmentRepository = mock(TrainingAssignmentRepository.class);
        trainingAppService = mock(TrainingAppService.class);
        memberSnapshotRepository = mock(MemberSnapshotRepository.class);
        notifyAppService = mock(NotifyAppService.class);
        leaveGateway = mock(LeaveGateway.class);
        LmsProperties properties = new LmsProperties();
        properties.setDemandGatePassed(true);
        properties.setUrgeBatchSize(50);
        service = new UrgeAppServiceImpl(trainingClassRepository, trainingAssignmentRepository,
                trainingAppService, memberSnapshotRepository, notifyAppService, leaveGateway, properties);
        admin = new LmsActor(1L, "admin", Set.of("tenant_admin"), Set.of());
    }

    /**
     * 离职学员与未收录账号都不发，但在职学员照发。
     */
    @Test
    @DisplayName("离职与未收录学员跳过，在职学员照发")
    void skipOffboardAndUnrecorded() {
        TrainingClass trainingClass = trainingClass(CLASS_ID, "PX-001", "安全生产培训");
        when(trainingClassRepository.page(any(), anyLong(), anyInt())).thenReturn(List.of(trainingClass));
        when(trainingAppService.completion(eq(CLASS_ID), any())).thenReturn(completion(
                pending(100L), pending(200L), pending(300L)));
        when(memberSnapshotRepository.listByUserIds(any())).thenReturn(List.of(
                member(100L, MemberStatus.ACTIVE),
                member(200L, MemberStatus.LEFT)));
        when(notifyAppService.notifyUrge(eq(CLASS_ID), eq("安全生产培训"), anyList(), eq(DAY))).thenReturn(1);

        UrgeRunView result = service.run(DAY, admin);

        assertEquals(1, result.getScannedClasses());
        assertEquals(1, result.getUrgentClasses());
        assertEquals(1, result.getNotified());
        assertEquals(1, result.getSkippedOffboard());
        assertEquals(1, result.getSkippedUnrecorded());
        UrgeItemView item = result.getItems().getFirst();
        assertEquals(3, item.getPendingCount());
    }

    /**
     * 全部完成时不打扰任何人。
     */
    @Test
    @DisplayName("全部完成不发送催办")
    void noUrgeWhenAllCompleted() {
        when(trainingClassRepository.page(any(), anyLong(), anyInt()))
                .thenReturn(List.of(trainingClass(CLASS_ID, "PX-001", "安全生产培训")));
        CompletionItemView done = pending(100L);
        done.setCompleted(true);
        when(trainingAppService.completion(eq(CLASS_ID), any())).thenReturn(completion(done));

        UrgeRunView result = service.run(DAY, admin);

        assertEquals(0, result.getUrgentClasses());
        verify(notifyAppService, never()).notifyUrge(anyLong(), any(), anyList(), any());
    }

    /**
     * 同一天重跑由通知层幂等兜底，第二轮投递数为 0。
     */
    @Test
    @DisplayName("同日重跑不重复投递")
    void rerunSameDayDoesNotDuplicate() {
        when(trainingClassRepository.page(any(), anyLong(), anyInt()))
                .thenReturn(List.of(trainingClass(CLASS_ID, "PX-001", "安全生产培训")));
        when(trainingAppService.completion(eq(CLASS_ID), any())).thenReturn(completion(pending(100L)));
        when(memberSnapshotRepository.listByUserIds(any()))
                .thenReturn(List.of(member(100L, MemberStatus.ACTIVE)));
        when(notifyAppService.notifyUrge(eq(CLASS_ID), any(), anyList(), eq(DAY))).thenReturn(1, 0);

        UrgeRunView first = service.run(DAY, admin);
        UrgeRunView second = service.run(DAY, admin);

        assertEquals(1, first.getNotified());
        assertEquals(0, second.getNotified());
    }

    /**
     * 单班认定异常只记错误，后续班级继续处理。
     */
    @Test
    @DisplayName("单班失败不影响整轮")
    void singleClassFailureIsIsolated() {
        when(trainingClassRepository.page(any(), anyLong(), anyInt())).thenReturn(List.of(
                trainingClass(1L, "PX-001", "坏班"),
                trainingClass(2L, "PX-002", "好班")));
        when(trainingAppService.completion(eq(1L), any()))
                .thenThrow(LmsErrorCode.exception(LmsErrorCode.TRAINING_STATE_CONFLICT, "草稿班"));
        when(trainingAppService.completion(eq(2L), any())).thenReturn(completion(pending(100L)));
        when(memberSnapshotRepository.listByUserIds(any()))
                .thenReturn(List.of(member(100L, MemberStatus.ACTIVE)));
        when(notifyAppService.notifyUrge(eq(2L), any(), anyList(), eq(DAY))).thenReturn(1);

        UrgeRunView result = service.run(DAY, admin);

        assertEquals(2, result.getScannedClasses());
        assertEquals(1, result.getFailedClasses());
        assertEquals(1, result.getNotified());
        assertNotNull(result.getItems().get(0).getError());
    }

    /**
     * 预览只算名单不投递。
     */
    @Test
    @DisplayName("预览不投递")
    void previewDoesNotDeliver() {
        when(trainingClassRepository.findById(CLASS_ID))
                .thenReturn(Optional.of(trainingClass(CLASS_ID, "PX-001", "安全生产培训")));
        when(trainingAppService.completion(eq(CLASS_ID), any())).thenReturn(completion(pending(100L)));
        when(memberSnapshotRepository.listByUserIds(any()))
                .thenReturn(List.of(member(100L, MemberStatus.ACTIVE)));

        UrgeItemView item = service.preview(CLASS_ID, admin);

        assertEquals(1, item.getPendingCount());
        assertEquals(0, item.getNotified());
        verify(notifyAppService, never()).notifyUrge(anyLong(), any(), anyList(), any());
    }

    /**
     * 非管理员不可执行催办。
     */
    @Test
    @DisplayName("非管理员不可催办")
    void runRejectsNonAdmin() {
        LmsActor member = new LmsActor(9L, "member", Set.of("lms_member"), Set.of());
        BizException exception = assertThrows(BizException.class, () -> service.run(DAY, member));
        assertEquals(LmsErrorCode.URGE_INVALID, exception.getCode());
    }

    /**
     * 请假覆盖场次日期时给出冲突提示。
     */
    @Test
    @DisplayName("请假覆盖场次日期时提示冲突")
    void scheduleConflictDetected() {
        TrainingClass trainingClass = trainingClass(CLASS_ID, "PX-001", "安全生产培训");
        when(trainingClassRepository.findById(CLASS_ID)).thenReturn(Optional.of(trainingClass));
        when(trainingClassRepository.listSessions(CLASS_ID))
                .thenReturn(List.of(session(1, LocalDateTime.of(2026, 11, 10, 9, 0))));
        when(trainingAssignmentRepository.listByClass(CLASS_ID))
                .thenReturn(List.of(assignment(100L)));

        LeaveConflictEntry leave = new LeaveConflictEntry();
        leave.setCode("LV-001");
        leave.setApplicantId(100L);
        leave.setApplicant("zhangsan");
        leave.setLeaveType("ANNUAL");
        leave.setStartDate(LocalDate.of(2026, 11, 9));
        leave.setEndDate(LocalDate.of(2026, 11, 11));
        when(leaveGateway.listApproved(any(), any(), any())).thenReturn(List.of(leave));

        List<ScheduleConflictView> conflicts = service.scheduleConflicts(CLASS_ID, admin);

        assertEquals(1, conflicts.size());
        ScheduleConflictView conflict = conflicts.getFirst();
        assertEquals(100L, conflict.getUserId());
        assertEquals("LV-001", conflict.getLeaveCode());
        assertEquals(LocalDate.of(2026, 11, 9), conflict.getLeaveStart());
    }

    /**
     * 请假不覆盖场次日期时不提示。
     */
    @Test
    @DisplayName("请假不覆盖场次日期时不提示")
    void noConflictWhenLeaveDoesNotCover() {
        when(trainingClassRepository.findById(CLASS_ID))
                .thenReturn(Optional.of(trainingClass(CLASS_ID, "PX-001", "安全生产培训")));
        when(trainingClassRepository.listSessions(CLASS_ID))
                .thenReturn(List.of(session(1, LocalDateTime.of(2026, 11, 20, 9, 0))));
        when(trainingAssignmentRepository.listByClass(CLASS_ID)).thenReturn(List.of(assignment(100L)));
        LeaveConflictEntry leave = new LeaveConflictEntry();
        leave.setApplicantId(100L);
        leave.setStartDate(LocalDate.of(2026, 11, 1));
        leave.setEndDate(LocalDate.of(2026, 11, 3));
        when(leaveGateway.listApproved(any(), any(), any())).thenReturn(List.of(leave));

        assertTrue(service.scheduleConflicts(CLASS_ID, admin).isEmpty());
    }

    /**
     * 构造培训班。
     *
     * @param id 主键
     * @param code 编码
     * @param title 名称
     * @return 培训班
     */
    private TrainingClass trainingClass(Long id, String code, String title) {
        TrainingClass trainingClass = new TrainingClass();
        trainingClass.setId(id);
        trainingClass.setClassCode(code);
        trainingClass.setTitle(title);
        return trainingClass;
    }

    /**
     * 构造场次。
     *
     * @param sessionNo 序号
     * @param startTime 开始时间
     * @return 场次
     */
    private ClassSession session(int sessionNo, LocalDateTime startTime) {
        ClassSession session = new ClassSession();
        session.setId((long) sessionNo);
        session.setSessionNo(sessionNo);
        session.setTitle("第 " + sessionNo + " 场");
        session.setStartTime(startTime);
        session.setEndTime(startTime.plusHours(2));
        return session;
    }

    /**
     * 构造分配记录。
     *
     * @param userId 学员
     * @return 分配
     */
    private TrainingAssignment assignment(Long userId) {
        TrainingAssignment assignment = new TrainingAssignment();
        assignment.setClassId(CLASS_ID);
        assignment.setUserId(userId);
        assignment.setUsername("user" + userId);
        return assignment;
    }

    /**
     * 构造完成认定结果。
     *
     * @param items 明细
     * @return 认定视图
     */
    private TrainingCompletionView completion(CompletionItemView... items) {
        TrainingCompletionView view = new TrainingCompletionView();
        view.setClassId(CLASS_ID);
        view.setItems(List.of(items));
        return view;
    }

    /**
     * 构造未完成明细。
     *
     * @param userId 学员
     * @return 明细
     */
    private CompletionItemView pending(Long userId) {
        CompletionItemView item = new CompletionItemView();
        item.setUserId(userId);
        item.setUsername("user" + userId);
        item.setCompleted(false);
        return item;
    }

    /**
     * 构造员工快照。
     *
     * @param userId 账号
     * @param status 状态
     * @return 快照
     */
    private MemberSnapshot member(Long userId, MemberStatus status) {
        MemberSnapshot snapshot = new MemberSnapshot();
        snapshot.setEmployeeNo("E" + userId);
        snapshot.setUserId(userId);
        snapshot.setStatus(status);
        return snapshot;
    }
}
