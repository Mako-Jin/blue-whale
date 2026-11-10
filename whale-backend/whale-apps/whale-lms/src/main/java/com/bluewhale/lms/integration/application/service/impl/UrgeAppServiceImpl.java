package com.bluewhale.lms.integration.application.service.impl;

import com.bluewhale.lms.common.LmsActor;
import com.bluewhale.lms.common.LmsErrorCode;
import com.bluewhale.lms.config.LmsProperties;
import com.bluewhale.lms.integration.application.service.NotifyAppService;
import com.bluewhale.lms.integration.application.service.UrgeAppService;
import com.bluewhale.lms.integration.application.view.ScheduleConflictView;
import com.bluewhale.lms.integration.application.view.UrgeItemView;
import com.bluewhale.lms.integration.application.view.UrgeRunView;
import com.bluewhale.lms.integration.domain.gateway.LeaveGateway;
import com.bluewhale.lms.integration.domain.model.LeaveConflictEntry;
import com.bluewhale.lms.integration.domain.model.MemberSnapshot;
import com.bluewhale.lms.integration.domain.repository.MemberSnapshotRepository;
import com.bluewhale.lms.training.application.service.TrainingAppService;
import com.bluewhale.lms.training.application.view.CompletionItemView;
import com.bluewhale.lms.training.application.view.TrainingCompletionView;
import com.bluewhale.lms.training.domain.model.ClassSession;
import com.bluewhale.lms.training.domain.model.TrainingClass;
import com.bluewhale.lms.training.domain.model.TrainingClassQuery;
import com.bluewhale.lms.training.domain.model.TrainingClassStatus;
import com.bluewhale.lms.training.domain.repository.TrainingAssignmentRepository;
import com.bluewhale.lms.training.domain.repository.TrainingClassRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 未完成必修催办应用服务实现。
 *
 * <p>三条边界值得单独说明：</p>
 * <ol>
 *   <li><b>离职与未收录都不发</b>：快照为 {@code LEFT} 的人保留学习记录但停止催办；
 *       快照里根本没有的账号同样不发——同步未跑就群发，可能给已离职半年的人发提醒；</li>
 *   <li><b>重复跑不重复发</b>：幂等键按「培训班 + 日期」生成，同一天补跑多少次
 *       都只会投递一次，靠 {@code lr_notify_log} 的唯一键兜底；</li>
 *   <li><b>逐班兜底</b>：单个班级认定或投递失败只记 {@code error} 并继续下一个，
 *       否则一个班的脏数据会让整轮催办停在同一处。</li>
 * </ol>
 *
 * @author BlueWhale
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UrgeAppServiceImpl implements UrgeAppService {

    /** 单次分页大小（避免一次把全部班级载入内存） */
    private static final int PAGE_SIZE = 100;

    /** 系统操作人角色（催办由调度触发，没有真实登录用户） */
    private static final String SYSTEM_ROLE = "tenant_admin";

    private final TrainingClassRepository trainingClassRepository;

    private final TrainingAssignmentRepository trainingAssignmentRepository;

    private final TrainingAppService trainingAppService;

    private final MemberSnapshotRepository memberSnapshotRepository;

    private final NotifyAppService notifyAppService;

    private final LeaveGateway leaveGateway;

    private final LmsProperties properties;

    /** 催办开关 */
    @Value("${whale.lms.urge.enabled:true}")
    private boolean urgeEnabled;

    @Override
    public UrgeRunView run(LocalDate day, LmsActor actor) {
        assertGate();
        requireAdmin(actor);
        LocalDate urgeDay = day == null ? LocalDate.now() : day;
        UrgeRunView result = new UrgeRunView();
        result.setDay(urgeDay);
        int limit = Math.max(properties.getUrgeBatchSize(), 1);
        TrainingClassQuery query = new TrainingClassQuery();
        query.setStatus(TrainingClassStatus.PUBLISHED.name());
        long offset = 0L;
        while (result.getScannedClasses() < limit) {
            List<TrainingClass> classes = trainingClassRepository.page(query, offset,
                    Math.min(limit - result.getScannedClasses(), PAGE_SIZE));
            if (classes.isEmpty()) {
                break;
            }
            for (TrainingClass trainingClass : classes) {
                UrgeItemView item = urgeOne(trainingClass, urgeDay, true);
                accumulate(result, item);
                if (result.getScannedClasses() >= limit) {
                    break;
                }
            }
            if (classes.size() < PAGE_SIZE) {
                break;
            }
            offset += classes.size();
        }
        log.info("催办执行完成: day={}, scanned={}, urgent={}, notified={}",
                urgeDay, result.getScannedClasses(), result.getUrgentClasses(), result.getNotified());
        return result;
    }

    @Override
    public UrgeItemView preview(Long classId, LmsActor actor) {
        assertGate();
        requireAdmin(actor);
        TrainingClass trainingClass = trainingClassRepository.findById(classId)
                .orElseThrow(() -> LmsErrorCode.exception(LmsErrorCode.TRAINING_NOT_FOUND));
        return urgeOne(trainingClass, LocalDate.now(), false);
    }

    @Override
    public UrgeRunView runScheduled() {
        if (!urgeEnabled) {
            return null;
        }
        return run(LocalDate.now(), systemActor());
    }

    @Override
    public List<ScheduleConflictView> scheduleConflicts(Long classId, LmsActor actor) {
        assertGate();
        requireAdmin(actor);
        TrainingClass trainingClass = trainingClassRepository.findById(classId)
                .orElseThrow(() -> LmsErrorCode.exception(LmsErrorCode.TRAINING_NOT_FOUND));
        List<ClassSession> sessions = trainingClassRepository.listSessions(trainingClass.getId());
        if (sessions.isEmpty()) {
            return List.of();
        }
        LocalDate from = sessions.stream().filter(item -> item.getStartTime() != null)
                .map(item -> item.getStartTime().toLocalDate()).min(LocalDate::compareTo).orElse(null);
        LocalDate to = sessions.stream().filter(item -> item.getEndTime() != null)
                .map(item -> item.getEndTime().toLocalDate()).max(LocalDate::compareTo).orElse(null);
        if (from == null || to == null) {
            return List.of();
        }
        Set<Long> userIds = new LinkedHashSet<>();
        trainingAssignmentRepository.listByClass(trainingClass.getId())
                .forEach(item -> {
                    if (item.getUserId() != null) {
                        userIds.add(item.getUserId());
                    }
                });
        if (userIds.isEmpty()) {
            return List.of();
        }
        List<LeaveConflictEntry> leaves = leaveGateway.listApproved(userIds, from, to);
        if (leaves.isEmpty()) {
            return List.of();
        }
        List<ScheduleConflictView> conflicts = new ArrayList<>();
        for (ClassSession session : sessions) {
            if (session.getStartTime() == null) {
                continue;
            }
            LocalDate sessionDay = session.getStartTime().toLocalDate();
            for (LeaveConflictEntry leave : leaves) {
                if (!leave.covers(sessionDay)) {
                    continue;
                }
                conflicts.add(toConflict(trainingClass, session, leave));
            }
        }
        return conflicts;
    }

    /**
     * 构造冲突视图。
     *
     * @param trainingClass 培训班
     * @param session 场次
     * @param leave 请假
     * @return 冲突视图
     */
    private ScheduleConflictView toConflict(TrainingClass trainingClass, ClassSession session,
                                            LeaveConflictEntry leave) {
        ScheduleConflictView view = new ScheduleConflictView();
        view.setClassId(trainingClass.getId());
        view.setSessionId(session.getId());
        view.setSessionNo(session.getSessionNo());
        view.setSessionTitle(session.getTitle());
        view.setStartTime(session.getStartTime());
        view.setEndTime(session.getEndTime());
        view.setUserId(leave.getApplicantId());
        view.setUsername(leave.getApplicant());
        view.setLeaveCode(leave.getCode());
        view.setLeaveType(leave.getLeaveType());
        view.setLeaveStart(leave.getStartDate());
        view.setLeaveEnd(leave.getEndDate());
        return view;
    }

    /**
     * 处理单个培训班。
     *
     * @param trainingClass 培训班
     * @param day 催办归属日期
     * @param deliver 是否真正投递（false 为预览）
     * @return 结果
     */
    private UrgeItemView urgeOne(TrainingClass trainingClass, LocalDate day, boolean deliver) {
        UrgeItemView item = new UrgeItemView();
        item.setClassId(trainingClass.getId());
        item.setClassCode(trainingClass.getClassCode());
        item.setClassName(trainingClass.getTitle());
        try {
            List<CompletionItemView> pending = listPending(trainingClass.getId());
            item.setPendingCount(pending.size());
            if (pending.isEmpty()) {
                return item;
            }
            Set<Long> userIds = new LinkedHashSet<>();
            for (CompletionItemView entry : pending) {
                if (entry.getUserId() != null) {
                    userIds.add(entry.getUserId());
                }
            }
            Map<Long, MemberSnapshot> members = loadMembers(userIds);
            List<Long> reachable = new ArrayList<>();
            for (Long userId : userIds) {
                MemberSnapshot member = members.get(userId);
                if (member == null) {
                    item.setSkippedUnrecorded(item.getSkippedUnrecorded() + 1);
                } else if (!member.getStatus().isActive()) {
                    item.setSkippedOffboard(item.getSkippedOffboard() + 1);
                } else {
                    reachable.add(userId);
                }
            }
            if (deliver && !reachable.isEmpty()) {
                item.setNotified(notifyAppService.notifyUrge(trainingClass.getId(),
                        trainingClass.getTitle(), reachable, day));
            }
        } catch (RuntimeException exception) {
            item.setError(exception.getMessage());
            log.warn("催办单班失败: classId={}, error={}", trainingClass.getId(), exception.getMessage());
        }
        return item;
    }

    /**
     * 取该班未完成学员。
     *
     * @param classId 培训班 ID
     * @return 未完成认定明细
     */
    private List<CompletionItemView> listPending(Long classId) {
        TrainingCompletionView completion = trainingAppService.completion(classId, systemActor());
        if (completion.getItems() == null) {
            return List.of();
        }
        return completion.getItems().stream().filter(entry -> !entry.isCompleted()).toList();
    }

    /**
     * 批量加载员工快照。
     *
     * @param userIds 用户 ID 集合
     * @return 用户 ID → 快照
     */
    private Map<Long, MemberSnapshot> loadMembers(Set<Long> userIds) {
        Map<Long, MemberSnapshot> members = new HashMap<>();
        for (MemberSnapshot snapshot : memberSnapshotRepository.listByUserIds(userIds)) {
            if (snapshot.getUserId() != null) {
                members.putIfAbsent(snapshot.getUserId(), snapshot);
            }
        }
        return members;
    }

    /**
     * 累加单个班级结果。
     *
     * @param result 汇总
     * @param item 单班结果
     */
    private void accumulate(UrgeRunView result, UrgeItemView item) {
        result.setScannedClasses(result.getScannedClasses() + 1);
        if (item.getPendingCount() > 0) {
            result.setUrgentClasses(result.getUrgentClasses() + 1);
        }
        result.setNotified(result.getNotified() + item.getNotified());
        result.setSkippedOffboard(result.getSkippedOffboard() + item.getSkippedOffboard());
        result.setSkippedUnrecorded(result.getSkippedUnrecorded() + item.getSkippedUnrecorded());
        if (item.getError() != null && !item.getError().isBlank()) {
            result.setFailedClasses(result.getFailedClasses() + 1);
        }
        result.getItems().add(item);
    }

    /**
     * 构造系统操作人（调度触发时没有登录用户）。
     *
     * @return 操作人
     */
    private LmsActor systemActor() {
        return new LmsActor(0L, "system", Set.of(SYSTEM_ROLE), Set.of());
    }

    /**
     * 校验操作人具备管理角色。
     *
     * @param actor 操作人
     */
    private void requireAdmin(LmsActor actor) {
        if (!actor.canManageAll()) {
            throw LmsErrorCode.exception(LmsErrorCode.URGE_INVALID, "无权执行催办");
        }
    }

    /**
     * 断言需求门禁通过。
     */
    private void assertGate() {
        if (!properties.isDemandGatePassed()) {
            throw LmsErrorCode.exception(LmsErrorCode.GATE_CLOSED);
        }
    }
}
