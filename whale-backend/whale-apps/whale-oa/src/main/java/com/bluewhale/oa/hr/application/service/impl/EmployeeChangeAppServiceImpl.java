package com.bluewhale.oa.hr.application.service.impl;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.oa.approval.application.command.ApprovalInstanceRegisterCommand;
import com.bluewhale.oa.approval.application.service.ApprovalInstanceAppService;
import com.bluewhale.oa.approval.application.view.ApprovalInstanceView;
import com.bluewhale.oa.common.OaErrorCode;
import com.bluewhale.oa.common.application.service.SequenceAppService;
import com.bluewhale.oa.hr.application.command.EmployeeChangeCommands;
import com.bluewhale.oa.hr.application.service.EmployeeChangeAppService;
import com.bluewhale.oa.hr.application.view.EmployeeChangeViews;
import com.bluewhale.oa.hr.domain.gateway.EmployeeLifecycleGateway;
import com.bluewhale.oa.hr.domain.gateway.LifecycleResult;
import com.bluewhale.oa.hr.domain.model.EmployeeChange;
import com.bluewhale.oa.hr.domain.model.EmployeeChangeQuery;
import com.bluewhale.oa.hr.domain.model.EmployeeChangeStatus;
import com.bluewhale.oa.hr.domain.model.EmployeeChangeTask;
import com.bluewhale.oa.hr.domain.model.EmployeeChangeTaskStatus;
import com.bluewhale.oa.hr.domain.model.EmployeeChangeType;
import com.bluewhale.oa.hr.domain.model.EmployeeProfile;
import com.bluewhale.oa.hr.domain.model.EmploymentStatus;
import com.bluewhale.oa.hr.domain.repository.EmployeeChangeRepository;
import com.bluewhale.oa.hr.domain.repository.EmployeeProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * 人员异动应用服务实现。
 *
 * <p>编排异动单状态机、审批登记、Auth 生命周期同步与员工档案回写。
 * 审批通过后的执行按状态判断幂等：重复的审批结果事件不会重复调用 Auth；手工重试校验四眼原则。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmployeeChangeAppServiceImpl implements EmployeeChangeAppService {

    /** 审批业务类型 */
    private static final String BUSINESS_TYPE = "HR_CHANGE";

    /** 审批结果：通过 */
    private static final String RESULT_APPROVED = "APPROVED";

    /** 审批结果：驳回 */
    private static final String RESULT_REJECTED = "REJECTED";

    /** 系统自动执行人 */
    private static final String SYSTEM_EXECUTOR = "系统自动执行";

    private final EmployeeChangeRepository employeeChangeRepository;
    private final EmployeeProfileRepository employeeProfileRepository;
    private final EmployeeLifecycleGateway employeeLifecycleGateway;
    private final ApprovalInstanceAppService approvalInstanceAppService;
    private final SequenceAppService sequenceAppService;

    @Override
    public PageResult<EmployeeChangeViews.ListItem> page(EmployeeChangeQuery query) {
        PageResult<EmployeeChange> page = employeeChangeRepository.page(query);
        List<EmployeeChangeViews.ListItem> records = page.getRecords().stream()
                .map(this::toListItem).toList();
        return PageResult.of(records, page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    public EmployeeChangeViews.Detail detail(Long id) {
        EmployeeChange change = requireChange(id);
        return toDetail(change, employeeChangeRepository.findTasks(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public EmployeeChangeViews.Detail create(EmployeeChangeCommands.Save command, Long applicantId,
                                            String applicantName) {
        EmployeeChange change = new EmployeeChange();
        change.setStatus(EmployeeChangeStatus.DRAFT);
        change.setApplicantId(applicantId);
        change.setApplicantName(applicantName);
        apply(change, command);
        fillSnapshot(change);
        change.ensureValid();
        change.setChangeNo(sequenceAppService.nextCode(BUSINESS_TYPE, "OA-HC"));
        employeeChangeRepository.insert(change);
        if (change.getChangeType().isLeave()) {
            createLeaveTasks(change);
        }
        log.info("人员异动单已创建: changeNo={}, type={}", change.getChangeNo(), change.getChangeType());
        return toDetail(change, employeeChangeRepository.findTasks(change.getId()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public EmployeeChangeViews.Detail update(Long id, EmployeeChangeCommands.Save command) {
        EmployeeChange change = requireChange(id);
        change.requireEditable();
        apply(change, command);
        change.ensureValid();
        employeeChangeRepository.update(change);
        return toDetail(change, employeeChangeRepository.findTasks(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public EmployeeChangeViews.Detail submit(Long id) {
        EmployeeChange change = requireChange(id);
        change.requireSubmittable();
        change.ensureValid();
        ApprovalInstanceRegisterCommand registerCommand = new ApprovalInstanceRegisterCommand();
        registerCommand.setBusinessType(BUSINESS_TYPE);
        registerCommand.setBusinessId(change.getId());
        registerCommand.setBusinessCode(change.getChangeNo());
        registerCommand.setTitle(change.getEmployeeName() + "的" + change.getChangeType().label() + "申请");
        registerCommand.setInitiatorId(change.getApplicantId());
        registerCommand.setInitiatorName(change.getApplicantName());
        registerCommand.setRemark(change.getReason());
        ApprovalInstanceView instance = approvalInstanceAppService.register(registerCommand);
        change.markSubmitted(instance.getId());
        employeeChangeRepository.update(change);
        log.info("人员异动单已提交审批: changeNo={}, instanceId={}", change.getChangeNo(), instance.getId());
        return toDetail(change, employeeChangeRepository.findTasks(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public EmployeeChangeViews.ExecuteResult execute(Long id, Long operatorId, String operatorName) {
        EmployeeChange change = requireChange(id);
        if (change.isExecuted()) {
            return executeResult(change, "该异动单已执行，无需重复执行", null);
        }
        if (change.getStatus() != EmployeeChangeStatus.APPROVED
                && change.getStatus() != EmployeeChangeStatus.EXEC_FAILED) {
            throw OaErrorCode.exception(OaErrorCode.HR_CHANGE_STATUS_CONFLICT,
                    "仅审批通过或执行失败的异动单可执行");
        }
        change.assertFourEyes(operatorId);
        return doExecute(change, operatorId, operatorName);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void doneTask(Long changeId, Long taskId, String remark) {
        EmployeeChangeTask task = employeeChangeRepository.findTask(changeId, taskId);
        if (task == null) {
            throw OaErrorCode.exception(OaErrorCode.HR_CHANGE_NOT_FOUND, "异动任务不存在或无权访问");
        }
        task.markDone();
        if (remark != null && !remark.isBlank()) {
            task.setRemark(remark.length() <= 255 ? remark : remark.substring(0, 255));
        }
        employeeChangeRepository.updateTask(task);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void syncApprovalResult(Long changeId, String result) {
        EmployeeChange change = employeeChangeRepository.findById(changeId);
        if (change == null || !change.isSubmitted()) {
            // 重复事件或状态已推进：保持幂等，不重复执行副作用
            return;
        }
        if (RESULT_APPROVED.equalsIgnoreCase(result)) {
            change.markApproved();
            employeeChangeRepository.update(change);
            doExecute(change, null, SYSTEM_EXECUTOR);
            return;
        }
        String reason = RESULT_REJECTED.equalsIgnoreCase(result) ? "审批驳回" : "审批终止：" + result;
        change.markRejected(reason);
        employeeChangeRepository.update(change);
        log.info("人员异动审批未通过: changeNo={}, result={}", change.getChangeNo(), result);
    }

    /**
     * 执行异动：同步 Auth 生命周期与员工档案，成功/失败均落状态。
     *
     * @param change 异动单
     * @param operatorId 手工执行人ID（系统执行时为空）
     * @param operatorName 执行人姓名
     * @return 执行结果
     */
    private EmployeeChangeViews.ExecuteResult doExecute(EmployeeChange change, Long operatorId,
                                                       String operatorName) {
        try {
            LifecycleResult lifecycle = employeeLifecycleGateway.apply(change);
            if (change.getUserId() == null && lifecycle != null) {
                change.setUserId(lifecycle.userId());
            }
            syncEmployeeProfile(change);
            String message = change.getChangeType().label() + "已执行";
            change.markExecuted(operatorId, operatorName, message);
            employeeChangeRepository.update(change);
            log.info("人员异动已执行: changeNo={}, type={}, userId={}",
                    change.getChangeNo(), change.getChangeType(), change.getUserId());
            return executeResult(change, message, lifecycle == null ? null : lifecycle.temporaryPassword());
        } catch (RuntimeException exception) {
            change.markExecuteFailed(exception.getMessage() == null ? "执行失败" : exception.getMessage());
            employeeChangeRepository.update(change);
            log.warn("人员异动执行失败: changeNo={}, error={}", change.getChangeNo(), exception.getMessage());
            return executeResult(change, "执行失败：" + exception.getMessage(), null);
        }
    }

    /**
     * 同步员工扩展档案（不存在时新建）。
     *
     * @param change 异动单
     */
    private void syncEmployeeProfile(EmployeeChange change) {
        Long userId = change.getUserId();
        if (userId == null) {
            return;
        }
        EmployeeProfile profile = employeeProfileRepository.findByUserId(userId).orElse(null);
        boolean created = profile == null;
        if (created) {
            profile = new EmployeeProfile();
            profile.setUserId(userId);
            profile.setEmploymentStatus(EmploymentStatus.ACTIVE);
            profile.setCultureVisible(Boolean.TRUE);
        }
        if (change.getToDeptId() != null) {
            profile.setDeptId(change.getToDeptId());
        }
        switch (change.getChangeType()) {
            case ONBOARD -> {
                profile.setHireDate(change.getEffectiveDate());
                profile.setEmploymentStatus(EmploymentStatus.of(change.getToStatus()));
                profile.setLeaveDate(null);
            }
            case REGULAR -> {
                profile.setRegularDate(change.getEffectiveDate());
                profile.setEmploymentStatus(EmploymentStatus.of(change.getToStatus()));
            }
            case TRANSFER -> profile.setEmploymentStatus(EmploymentStatus.of(change.getToStatus()));
            case LEAVE -> {
                profile.setEmploymentStatus(EmploymentStatus.LEFT);
                profile.setLeaveDate(change.getEffectiveDate());
            }
            case REHIRE -> {
                profile.setEmploymentStatus(EmploymentStatus.of(change.getToStatus()));
                profile.setLeaveDate(null);
            }
            default -> {
                // 枚举已穷尽，无需默认处理
            }
        }
        profile.ensureValid();
        if (created) {
            employeeProfileRepository.insert(profile);
        } else {
            employeeProfileRepository.update(profile);
        }
    }

    /**
     * 创建离职清单任务。
     *
     * @param change 已落库的异动单
     */
    private void createLeaveTasks(EmployeeChange change) {
        List<EmployeeChangeTask> tasks = new ArrayList<>();
        tasks.add(task(change, "HANDOVER", "工作交接"));
        tasks.add(task(change, "ACCOUNT", "账号回收"));
        tasks.add(task(change, "ASSET", "资产归还"));
        tasks.add(task(change, "FILE", "档案归档"));
        tasks.forEach(employeeChangeRepository::insertTask);
    }

    /**
     * 构造离职清单任务。
     *
     * @param change 异动单
     * @param code 任务编码
     * @param name 任务名称
     * @return 任务
     */
    private EmployeeChangeTask task(EmployeeChange change, String code, String name) {
        EmployeeChangeTask task = new EmployeeChangeTask();
        task.setChangeId(change.getId());
        task.setTaskCode(code);
        task.setTaskName(name);
        task.setStatus(EmployeeChangeTaskStatus.PENDING);
        return task;
    }

    /**
     * 命令字段写入聚合，并按类型补齐默认状态。
     *
     * @param change 异动单
     * @param command 保存命令
     */
    private void apply(EmployeeChange change, EmployeeChangeCommands.Save command) {
        change.setChangeType(EmployeeChangeType.of(command.getChangeType()));
        if (command.getUserId() != null) {
            change.setUserId(command.getUserId());
        }
        change.setEmployeeName(command.getEmployeeName() == null ? null : command.getEmployeeName().trim());
        change.setUsername(command.getUsername());
        change.setFromDeptId(command.getFromDeptId());
        change.setToDeptId(command.getToDeptId());
        change.setFromStatus(command.getFromStatus());
        change.setToStatus(defaultToStatus(change.getChangeType(), command.getToStatus()));
        change.setEffectiveDate(command.getEffectiveDate());
        change.setReason(command.getReason());
        change.setRemark(command.getRemark());
        if (change.getChangeType() == EmployeeChangeType.LEAVE && change.getToStatus() == null) {
            change.setToStatus(EmploymentStatus.LEFT.name());
        }
    }

    /**
     * 目标在职状态默认值。
     *
     * @param type 异动类型
     * @param toStatus 命令显式状态
     * @return 目标状态
     */
    private String defaultToStatus(EmployeeChangeType type, String toStatus) {
        if (toStatus != null && !toStatus.isBlank()) {
            return toStatus.trim().toUpperCase();
        }
        return switch (type) {
            case ONBOARD -> EmploymentStatus.PROBATION.name();
            case REGULAR -> EmploymentStatus.REGULAR.name();
            case LEAVE -> EmploymentStatus.LEFT.name();
            case REHIRE -> EmploymentStatus.ACTIVE.name();
            case TRANSFER -> null;
        };
    }

    /**
     * 补全原部门/原状态快照（目标用户已有档案时）。
     *
     * @param change 异动单
     */
    private void fillSnapshot(EmployeeChange change) {
        if (change.getUserId() == null
                || (change.getFromDeptId() != null && change.getFromStatus() != null)) {
            return;
        }
        employeeProfileRepository.findByUserId(change.getUserId()).ifPresent(profile -> {
            if (change.getFromDeptId() == null) {
                change.setFromDeptId(profile.getDeptId());
            }
            if (change.getFromStatus() == null && profile.getEmploymentStatus() != null) {
                change.setFromStatus(profile.getEmploymentStatus().name());
            }
            if (change.getChangeType() == EmployeeChangeType.TRANSFER
                    && change.getToStatus() == null && profile.getEmploymentStatus() != null) {
                change.setToStatus(profile.getEmploymentStatus().name());
            }
        });
    }

    /**
     * 按ID查询异动单，缺失时抛业务异常。
     *
     * @param id 异动单ID
     * @return 异动单
     */
    private EmployeeChange requireChange(Long id) {
        EmployeeChange change = employeeChangeRepository.findById(id);
        if (change == null) {
            throw OaErrorCode.exception(OaErrorCode.HR_CHANGE_NOT_FOUND);
        }
        return change;
    }

    /**
     * 构造执行结果视图。
     *
     * @param change 异动单
     * @param message 提示信息
     * @param temporaryPassword 一次性临时口令（仅入职建号返回）
     * @return 执行结果
     */
    private EmployeeChangeViews.ExecuteResult executeResult(EmployeeChange change, String message,
                                                            String temporaryPassword) {
        EmployeeChangeViews.ExecuteResult result = new EmployeeChangeViews.ExecuteResult();
        result.setId(change.getId());
        result.setStatus(change.getStatus().name());
        result.setMessage(message);
        result.setTemporaryPassword(temporaryPassword);
        return result;
    }

    /**
     * 列表项转换。
     *
     * @param change 异动单
     * @return 列表项
     */
    private EmployeeChangeViews.ListItem toListItem(EmployeeChange change) {
        EmployeeChangeViews.ListItem view = new EmployeeChangeViews.ListItem();
        view.setId(change.getId());
        view.setChangeNo(change.getChangeNo());
        view.setChangeType(change.getChangeType() == null ? null : change.getChangeType().name());
        view.setChangeTypeLabel(change.getChangeType() == null ? null : change.getChangeType().label());
        view.setUserId(change.getUserId());
        view.setEmployeeName(change.getEmployeeName());
        view.setEffectiveDate(change.getEffectiveDate());
        view.setStatus(change.getStatus() == null ? null : change.getStatus().name());
        view.setApplicantName(change.getApplicantName());
        view.setExecutedTime(change.getExecutedTime());
        view.setCreateTime(change.getCreateTime());
        return view;
    }

    /**
     * 详情转换。
     *
     * @param change 异动单
     * @param tasks 任务列表
     * @return 详情
     */
    private EmployeeChangeViews.Detail toDetail(EmployeeChange change, List<EmployeeChangeTask> tasks) {
        EmployeeChangeViews.Detail view = new EmployeeChangeViews.Detail();
        view.setId(change.getId());
        view.setChangeNo(change.getChangeNo());
        view.setChangeType(change.getChangeType() == null ? null : change.getChangeType().name());
        view.setChangeTypeLabel(change.getChangeType() == null ? null : change.getChangeType().label());
        view.setUserId(change.getUserId());
        view.setEmployeeName(change.getEmployeeName());
        view.setUsername(change.getUsername());
        view.setFromDeptId(change.getFromDeptId());
        view.setToDeptId(change.getToDeptId());
        view.setFromStatus(change.getFromStatus());
        view.setToStatus(change.getToStatus());
        view.setEffectiveDate(change.getEffectiveDate());
        view.setReason(change.getReason());
        view.setStatus(change.getStatus() == null ? null : change.getStatus().name());
        view.setApprovalInstanceId(change.getApprovalInstanceId());
        view.setApplicantId(change.getApplicantId());
        view.setApplicantName(change.getApplicantName());
        view.setExecutorId(change.getExecutorId());
        view.setExecutorName(change.getExecutorName());
        view.setExecutedTime(change.getExecutedTime());
        view.setExecuteResult(change.getExecuteResult());
        view.setRemark(change.getRemark());
        view.setCreateTime(change.getCreateTime());
        view.setUpdateTime(change.getUpdateTime());
        view.setTasks(tasks == null ? List.of() : tasks.stream().map(this::toTask).toList());
        return view;
    }

    /**
     * 任务转换。
     *
     * @param task 任务
     * @return 任务视图
     */
    private EmployeeChangeViews.Task toTask(EmployeeChangeTask task) {
        EmployeeChangeViews.Task view = new EmployeeChangeViews.Task();
        view.setId(task.getId());
        view.setTaskCode(task.getTaskCode());
        view.setTaskName(task.getTaskName());
        view.setAssigneeId(task.getAssigneeId());
        view.setAssigneeName(task.getAssigneeName());
        view.setStatus(task.getStatus() == null ? null : task.getStatus().name());
        view.setDoneTime(task.getDoneTime());
        view.setRemark(task.getRemark());
        return view;
    }
}
