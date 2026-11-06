package com.bluewhale.lms.training.domain.model;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.lms.common.LmsErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 培训领域规则测试：状态机、内容冻结、场次时间与课时、签到时间窗、补签留痕。
 *
 * <p>对应 REQ-FUNC-088 验收：签到仅限时间窗且留审计；课时以场次为单位可加总。</p>
 *
 * @author BlueWhale
 */
class TrainingClassDomainRuleTest {

    @Test
    @DisplayName("发布需草稿且至少 1 个场次")
    void publishShouldRequireDraftAndSession() {
        TrainingClass draft = trainingClass(TrainingClassStatus.DRAFT);
        BizException noSession = assertThrows(BizException.class, () -> draft.publish(0));
        assertEquals(LmsErrorCode.TRAINING_STATE_CONFLICT, noSession.getCode());

        draft.publish(1);
        assertEquals(TrainingClassStatus.PUBLISHED, draft.getStatus());

        BizException again = assertThrows(BizException.class, () -> draft.publish(1));
        assertEquals(LmsErrorCode.TRAINING_STATE_CONFLICT, again.getCode());
    }

    @Test
    @DisplayName("发布后内容冻结：场次与头信息均不可编辑")
    void publishedClassShouldFreezeContent() {
        TrainingClass published = trainingClass(TrainingClassStatus.PUBLISHED);
        assertFalse(published.getStatus().canEditContent());
        BizException exception = assertThrows(BizException.class, published::assertEditable);
        assertEquals(LmsErrorCode.TRAINING_STATE_CONFLICT, exception.getCode());
    }

    @Test
    @DisplayName("结课为终态：不可重复结课、不可分配、不可删除")
    void closedClassShouldBeTerminal() {
        TrainingClass published = trainingClass(TrainingClassStatus.PUBLISHED);
        published.close();
        assertEquals(TrainingClassStatus.CLOSED, published.getStatus());
        assertFalse(published.getStatus().canClose());
        assertFalse(published.getStatus().canAssign());
        assertFalse(published.getStatus().canCheckin());
        assertFalse(published.getStatus().canDelete());

        BizException exception = assertThrows(BizException.class, published::close);
        assertEquals(LmsErrorCode.TRAINING_STATE_CONFLICT, exception.getCode());
    }

    @Test
    @DisplayName("结班日期不得早于开班日期")
    void periodShouldRejectReversedDates() {
        TrainingClass trainingClass = trainingClass(TrainingClassStatus.DRAFT);
        trainingClass.setEndDate(LocalDate.of(2026, 11, 1));
        BizException exception = assertThrows(BizException.class, trainingClass::validatePeriod);
        assertEquals(LmsErrorCode.TRAINING_PERIOD_INVALID, exception.getCode());
    }

    @Test
    @DisplayName("场次时间与课时校验")
    void sessionShouldValidateTimeAndHours() {
        ClassSession session = session(LocalDateTime.of(2026, 11, 10, 9, 0),
                LocalDateTime.of(2026, 11, 10, 11, 0), new BigDecimal("2.0"));
        session.validate();

        ClassSession reversed = session(LocalDateTime.of(2026, 11, 10, 9, 0),
                LocalDateTime.of(2026, 11, 10, 9, 0), new BigDecimal("2.0"));
        BizException timeException = assertThrows(BizException.class, reversed::validate);
        assertEquals(LmsErrorCode.SESSION_TIME_INVALID, timeException.getCode());

        ClassSession negative = session(LocalDateTime.of(2026, 11, 10, 9, 0),
                LocalDateTime.of(2026, 11, 10, 11, 0), new BigDecimal("-1"));
        BizException hoursException = assertThrows(BizException.class, negative::validate);
        assertEquals(LmsErrorCode.SESSION_TIME_INVALID, hoursException.getCode());
    }

    @Test
    @DisplayName("签到时间窗：含结束后宽限，超窗即拒")
    void checkinWindowShouldRespectGrace() {
        LocalDateTime start = LocalDateTime.of(2026, 11, 10, 9, 0);
        LocalDateTime end = LocalDateTime.of(2026, 11, 10, 11, 0);
        ClassSession session = session(start, end, new BigDecimal("2.0"));

        assertFalse(session.withinCheckinWindow(start.minusMinutes(1), 30));
        assertTrue(session.withinCheckinWindow(start, 30));
        assertTrue(session.withinCheckinWindow(end.plusMinutes(30), 30));
        assertFalse(session.withinCheckinWindow(end.plusMinutes(31), 30));
        assertFalse(session.withinCheckinWindow(end.plusMinutes(1), 0));
    }

    @Test
    @DisplayName("补签必须留操作人与备注")
    void supplementShouldRequireOperatorAndRemark() {
        ClassSession session = session(LocalDateTime.of(2026, 11, 10, 9, 0),
                LocalDateTime.of(2026, 11, 10, 11, 0), new BigDecimal("2.0"));
        session.setId(300L);
        session.setClassId(100L);
        LocalDateTime now = LocalDateTime.of(2026, 11, 10, 12, 0);

        BizException noOperator = assertThrows(BizException.class,
                () -> Checkin.supplement(session, 9L, "learner", null, "忘记签到", now));
        assertEquals(LmsErrorCode.CHECKIN_REMARK_REQUIRED, noOperator.getCode());

        BizException noRemark = assertThrows(BizException.class,
                () -> Checkin.supplement(session, 9L, "learner", 7L, "  ", now));
        assertEquals(LmsErrorCode.CHECKIN_REMARK_REQUIRED, noRemark.getCode());

        Checkin checkin = Checkin.supplement(session, 9L, "learner", 7L, "忘记签到", now);
        assertEquals(CheckinType.SUPPLEMENT, checkin.getCheckinType());
        assertEquals(7L, checkin.getOperatorId());
        assertEquals("忘记签到", checkin.getRemark());
    }

    @Test
    @DisplayName("课时台账：无讲师则不计课时，场次讲师优先于班级讲师")
    void lecturerHoursShouldUseEffectiveLecturer() {
        ClassSession session = session(LocalDateTime.of(2026, 11, 10, 9, 0),
                LocalDateTime.of(2026, 11, 10, 11, 0), new BigDecimal("2.5"));
        session.setId(300L);
        session.setClassId(100L);

        assertNull(LecturerHours.of(session, " "));

        session.setLecturer("张老师");
        LecturerHours entry = LecturerHours.of(session, session.effectiveLecturer("李老师"));
        assertEquals("张老师", entry.getLecturer());
        assertEquals(new BigDecimal("2.5"), entry.getHours());

        session.setLecturer(null);
        LecturerHours inherited = LecturerHours.of(session, session.effectiveLecturer("李老师"));
        assertEquals("李老师", inherited.getLecturer());
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
     * @param hours 课时
     * @return 场次
     */
    private ClassSession session(LocalDateTime start, LocalDateTime end, BigDecimal hours) {
        ClassSession session = new ClassSession();
        session.setId(300L);
        session.setClassId(100L);
        session.setSessionNo(1);
        session.setTitle("开班第一讲");
        session.setStartTime(start);
        session.setEndTime(end);
        session.setHours(hours);
        return session;
    }
}
