package com.bluewhale.lms.training.application.service.impl;

import com.bluewhale.lms.common.LmsActor;
import com.bluewhale.lms.common.LmsErrorCode;
import com.bluewhale.lms.config.LmsProperties;
import com.bluewhale.lms.training.application.command.CheckinSupplementCommand;
import com.bluewhale.lms.training.application.service.CheckinAppService;
import com.bluewhale.lms.training.application.view.CheckinView;
import com.bluewhale.lms.training.domain.model.Checkin;
import com.bluewhale.lms.training.domain.model.ClassSession;
import com.bluewhale.lms.training.domain.model.TrainingClass;
import com.bluewhale.lms.training.domain.repository.CheckinRepository;
import com.bluewhale.lms.training.domain.repository.TrainingAssignmentRepository;
import com.bluewhale.lms.training.domain.repository.TrainingClassRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 面授签到应用服务实现。
 *
 * <p>签到四道关卡：班级已发布 → 学员已分配 → 位于场次时间窗（含宽限）→ 未重复签到；
 * 补签放开时间窗但强制留痕（操作人 + 备注），且不允许在草稿班级上补签。</p>
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class CheckinAppServiceImpl implements CheckinAppService {

    private final TrainingClassRepository classRepository;

    private final TrainingAssignmentRepository assignmentRepository;

    private final CheckinRepository checkinRepository;

    private final LmsProperties properties;

    @Override
    @Transactional
    public CheckinView checkin(Long sessionId, LmsActor actor) {
        assertGate();
        Long userId = actor.requireUserId();
        ClassSession session = requireSession(sessionId);
        TrainingClass trainingClass = requireClass(session.getClassId());
        trainingClass.assertCheckinable();
        if (!assignmentRepository.listUserIds(trainingClass.getId()).contains(userId)) {
            throw LmsErrorCode.exception(LmsErrorCode.CHECKIN_NOT_ASSIGNED);
        }
        Optional<Checkin> existing = checkinRepository.findBySessionAndUser(sessionId, userId);
        if (existing.isPresent()) {
            return CheckinView.from(existing.get());
        }
        LocalDateTime now = LocalDateTime.now();
        if (!session.withinCheckinWindow(now, properties.getCheckinGraceMinutes())) {
            throw LmsErrorCode.exception(LmsErrorCode.CHECKIN_WINDOW_CLOSED,
                    "签到时间窗为 " + session.getStartTime() + " ~ " + session.getEndTime()
                            + "（结束后宽限 " + Math.max(properties.getCheckinGraceMinutes(), 0) + " 分钟）");
        }
        Checkin checkin = Checkin.self(session, userId, actor.getUsername(), now);
        checkinRepository.save(checkin);
        return CheckinView.from(checkin);
    }

    @Override
    @Transactional
    public CheckinView supplement(Long sessionId, CheckinSupplementCommand command, LmsActor actor) {
        assertGate();
        Long operatorId = actor.requireUserId();
        ClassSession session = requireSession(sessionId);
        TrainingClass trainingClass = requireClass(session.getClassId());
        if (trainingClass.getStatus().canEditContent()) {
            throw LmsErrorCode.exception(LmsErrorCode.TRAINING_STATE_CONFLICT, "草稿培训班不可补签");
        }
        Optional<Checkin> existing = checkinRepository.findBySessionAndUser(sessionId, command.getUserId());
        if (existing.isPresent()) {
            return CheckinView.from(existing.get());
        }
        Checkin checkin = Checkin.supplement(session, command.getUserId(),
                command.getUsername(), operatorId, command.getRemark(), LocalDateTime.now());
        checkinRepository.save(checkin);
        return CheckinView.from(checkin);
    }

    @Override
    public List<CheckinView> listByClass(Long classId, Long sessionId, LmsActor actor) {
        assertGate();
        requireClass(classId);
        return checkinRepository.listByClass(classId, sessionId).stream()
                .map(CheckinView::from)
                .toList();
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
     * 加载场次，不存在按 404 处理。
     *
     * @param sessionId 场次 ID
     * @return 场次
     */
    private ClassSession requireSession(Long sessionId) {
        return classRepository.findSessionById(sessionId)
                .orElseThrow(() -> LmsErrorCode.exception(LmsErrorCode.SESSION_NOT_FOUND));
    }
}
