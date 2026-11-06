package com.bluewhale.lms.training.application.service.impl;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.lms.common.LmsActor;
import com.bluewhale.lms.common.LmsErrorCode;
import com.bluewhale.lms.config.LmsProperties;
import com.bluewhale.lms.training.application.command.CheckinSupplementCommand;
import com.bluewhale.lms.training.application.view.CheckinView;
import com.bluewhale.lms.training.domain.model.Checkin;
import com.bluewhale.lms.training.domain.model.CheckinType;
import com.bluewhale.lms.training.domain.model.ClassSession;
import com.bluewhale.lms.training.domain.model.TrainingClass;
import com.bluewhale.lms.training.domain.model.TrainingClassStatus;
import com.bluewhale.lms.training.domain.model.TrainingMode;
import com.bluewhale.lms.training.domain.repository.CheckinRepository;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link CheckinAppServiceImpl} 单元测试：签到时间窗、分配归属、重复签到幂等与补签留痕。
 *
 * @author BlueWhale
 */
class CheckinAppServiceImplTest {

    private TrainingClassRepository classRepository;

    private TrainingAssignmentRepository assignmentRepository;

    private CheckinRepository checkinRepository;

    private CheckinAppServiceImpl service;

    private final LmsActor learner = new LmsActor(9L, "learner", Set.of("lms_member"), Set.of());

    private final LmsActor admin = new LmsActor(7L, "lmsadmin", Set.of("lms_admin"), Set.of());

    @BeforeEach
    void setUp() {
        classRepository = mock(TrainingClassRepository.class);
        assignmentRepository = mock(TrainingAssignmentRepository.class);
        checkinRepository = mock(CheckinRepository.class);
        service = new CheckinAppServiceImpl(classRepository, assignmentRepository, checkinRepository,
                new LmsProperties());
    }

    @Test
    @DisplayName("超出签到时间窗拒绝")
    void checkinShouldRejectOutsideWindow() {
        when(classRepository.findSessionById(300L)).thenReturn(Optional.of(
                session(LocalDateTime.now().plusHours(1), LocalDateTime.now().plusHours(3))));
        when(classRepository.findById(100L)).thenReturn(Optional.of(trainingClass(TrainingClassStatus.PUBLISHED)));
        when(assignmentRepository.listUserIds(100L)).thenReturn(List.of(9L));

        BizException exception = assertThrows(BizException.class, () -> service.checkin(300L, learner));
        assertEquals(LmsErrorCode.CHECKIN_WINDOW_CLOSED, exception.getCode());
        verify(checkinRepository, never()).save(any(Checkin.class));
    }

    @Test
    @DisplayName("未分配学员不可签到")
    void checkinShouldRejectUnassignedLearner() {
        when(classRepository.findSessionById(300L)).thenReturn(Optional.of(
                session(LocalDateTime.now().minusMinutes(10), LocalDateTime.now().plusMinutes(30))));
        when(classRepository.findById(100L)).thenReturn(Optional.of(trainingClass(TrainingClassStatus.PUBLISHED)));
        when(assignmentRepository.listUserIds(100L)).thenReturn(List.of(11L));

        BizException exception = assertThrows(BizException.class, () -> service.checkin(300L, learner));
        assertEquals(LmsErrorCode.CHECKIN_NOT_ASSIGNED, exception.getCode());
    }

    @Test
    @DisplayName("重复签到幂等返回既有记录")
    void checkinShouldBeIdempotent() {
        when(classRepository.findSessionById(300L)).thenReturn(Optional.of(
                session(LocalDateTime.now().minusMinutes(10), LocalDateTime.now().plusMinutes(30))));
        when(classRepository.findById(100L)).thenReturn(Optional.of(trainingClass(TrainingClassStatus.PUBLISHED)));
        when(assignmentRepository.listUserIds(100L)).thenReturn(List.of(9L));
        Checkin existing = Checkin.self(session(LocalDateTime.now().minusMinutes(10),
                LocalDateTime.now().plusMinutes(30)), 9L, "learner", LocalDateTime.now());
        existing.setId(999L);
        when(checkinRepository.findBySessionAndUser(300L, 9L)).thenReturn(Optional.of(existing));

        CheckinView view = service.checkin(300L, learner);

        assertEquals(999L, view.getId());
        verify(checkinRepository, never()).save(any(Checkin.class));
    }

    @Test
    @DisplayName("时间窗内自助签到落库为 SELF")
    void checkinShouldSaveSelf() {
        when(classRepository.findSessionById(300L)).thenReturn(Optional.of(
                session(LocalDateTime.now().minusMinutes(10), LocalDateTime.now().plusMinutes(30))));
        when(classRepository.findById(100L)).thenReturn(Optional.of(trainingClass(TrainingClassStatus.PUBLISHED)));
        when(assignmentRepository.listUserIds(100L)).thenReturn(List.of(9L));
        when(checkinRepository.findBySessionAndUser(300L, 9L)).thenReturn(Optional.empty());

        service.checkin(300L, learner);

        ArgumentCaptor<Checkin> captor = ArgumentCaptor.forClass(Checkin.class);
        verify(checkinRepository).save(captor.capture());
        assertEquals(CheckinType.SELF, captor.getValue().getCheckinType());
        assertEquals(9L, captor.getValue().getUserId());
        assertEquals(100L, captor.getValue().getClassId());
    }

    @Test
    @DisplayName("补签缺备注拒绝")
    void supplementShouldRequireRemark() {
        when(classRepository.findSessionById(300L)).thenReturn(Optional.of(
                session(LocalDateTime.now().minusDays(2), LocalDateTime.now().minusDays(2).plusHours(2))));
        when(classRepository.findById(100L)).thenReturn(Optional.of(trainingClass(TrainingClassStatus.PUBLISHED)));
        when(checkinRepository.findBySessionAndUser(300L, 9L)).thenReturn(Optional.empty());

        BizException exception = assertThrows(BizException.class,
                () -> service.supplement(300L, supplementCommand(" "), admin));
        assertEquals(LmsErrorCode.CHECKIN_REMARK_REQUIRED, exception.getCode());
    }

    @Test
    @DisplayName("草稿培训班不可补签")
    void supplementShouldRejectDraftClass() {
        when(classRepository.findSessionById(300L)).thenReturn(Optional.of(
                session(LocalDateTime.now().minusDays(2), LocalDateTime.now().minusDays(2).plusHours(2))));
        when(classRepository.findById(100L)).thenReturn(Optional.of(trainingClass(TrainingClassStatus.DRAFT)));

        BizException exception = assertThrows(BizException.class,
                () -> service.supplement(300L, supplementCommand("忘记签到"), admin));
        assertEquals(LmsErrorCode.TRAINING_STATE_CONFLICT, exception.getCode());
    }

    @Test
    @DisplayName("补签留痕：记录操作人与原因")
    void supplementShouldRecordOperatorAndRemark() {
        when(classRepository.findSessionById(300L)).thenReturn(Optional.of(
                session(LocalDateTime.now().minusDays(2), LocalDateTime.now().minusDays(2).plusHours(2))));
        when(classRepository.findById(100L)).thenReturn(Optional.of(trainingClass(TrainingClassStatus.PUBLISHED)));
        when(checkinRepository.findBySessionAndUser(300L, 9L)).thenReturn(Optional.empty());

        service.supplement(300L, supplementCommand("设备故障无法签到"), admin);

        ArgumentCaptor<Checkin> captor = ArgumentCaptor.forClass(Checkin.class);
        verify(checkinRepository).save(captor.capture());
        assertEquals(CheckinType.SUPPLEMENT, captor.getValue().getCheckinType());
        assertEquals(7L, captor.getValue().getOperatorId());
        assertEquals("设备故障无法签到", captor.getValue().getRemark());
    }

    /**
     * 构造补签命令。
     *
     * @param remark 备注
     * @return 命令
     */
    private CheckinSupplementCommand supplementCommand(String remark) {
        CheckinSupplementCommand command = new CheckinSupplementCommand();
        command.setUserId(9L);
        command.setUsername("learner");
        command.setRemark(remark);
        return command;
    }

    /**
     * 构造培训班。
     *
     * @param status 状态
     * @return 培训班
     */
    private TrainingClass trainingClass(TrainingClassStatus status) {
        TrainingClass trainingClass = new TrainingClass();
        trainingClass.setId(100L);
        trainingClass.setClassCode("TR-2026-001");
        trainingClass.setTitle("新员工入职培训");
        trainingClass.setMode(TrainingMode.OFFLINE);
        trainingClass.setStartDate(LocalDate.of(2026, 11, 10));
        trainingClass.setEndDate(LocalDate.of(2026, 11, 12));
        trainingClass.setStatus(status);
        return trainingClass;
    }

    /**
     * 构造场次。
     *
     * @param start 开始时间
     * @param end 结束时间
     * @return 场次
     */
    private ClassSession session(LocalDateTime start, LocalDateTime end) {
        ClassSession session = new ClassSession();
        session.setId(300L);
        session.setClassId(100L);
        session.setSessionNo(1);
        session.setTitle("开班第一讲");
        session.setStartTime(start);
        session.setEndTime(end);
        session.setHours(new BigDecimal("2.0"));
        return session;
    }
}
