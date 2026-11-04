package com.bluewhale.oa.hr.application.service.impl;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.oa.hr.application.command.LeaveSaveCommand;
import com.bluewhale.oa.hr.application.command.LeaveStatusCommand;
import com.bluewhale.oa.hr.application.service.LeaveAppService;
import com.bluewhale.oa.hr.application.view.LeaveView;
import com.bluewhale.oa.hr.domain.model.Leave;
import com.bluewhale.oa.hr.domain.model.LeaveQuery;
import com.bluewhale.oa.hr.domain.model.LeaveStatus;
import com.bluewhale.oa.hr.domain.repository.LeaveRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 请假申请应用服务实现。
 *
 * <p>保留存量 {@code /oa/leave/**} 行为：错误码仍使用通用 {@code 400/404}，
 * 仅重构包结构与依赖方向。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LeaveAppServiceImpl implements LeaveAppService {

    private final LeaveRepository leaveRepository;

    @Override
    public PageResult<LeaveView> page(LeaveQuery query) {
        List<LeaveView> records = leaveRepository.page(query).stream().map(this::toView).toList();
        return PageResult.of(records, leaveRepository.count(query), query.getCurrent(), query.getSize());
    }

    @Override
    public LeaveView detail(Long id) {
        return toView(requireLeave(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LeaveView create(LeaveSaveCommand command) {
        Leave leave = new Leave();
        apply(leave, command);
        leave.ensureValidPeriod();
        assertCodeUnique(command.getCode(), null);
        leave.submit();
        leaveRepository.insert(leave);
        log.info("请假申请已创建: id={}, code={}", leave.getId(), leave.getCode());
        return toView(leave);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LeaveView update(Long id, LeaveSaveCommand command) {
        Leave leave = requireEditable(id);
        apply(leave, command);
        leave.ensureValidPeriod();
        assertCodeUnique(command.getCode(), id);
        leaveRepository.update(leave);
        log.info("请假申请已更新: id={}, code={}", id, leave.getCode());
        return toView(leave);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Long id, LeaveStatusCommand command) {
        Leave leave = requireLeave(id);
        leave.changeStatus(LeaveStatus.of(command.getStatus()));
        leaveRepository.update(leave);
        log.info("请假申请状态已变更: id={}, status={}", id, leave.getStatus());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        requireEditable(id);
        leaveRepository.deleteById(id);
        log.info("请假申请已删除: id={}", id);
    }

    /**
     * 查询申请，缺失时抛业务异常。
     *
     * @param id 主键
     * @return 申请
     */
    private Leave requireLeave(Long id) {
        return leaveRepository.findById(id)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "请假申请不存在"));
    }

    /**
     * 查询并校验申请处于可编辑状态。
     *
     * @param id 主键
     * @return 申请
     */
    private Leave requireEditable(Long id) {
        Leave leave = requireLeave(id);
        leave.ensureEditable();
        return leave;
    }

    /**
     * 校验申请单号在租户内唯一。
     *
     * @param code 申请单号
     * @param excludeId 更新时排除自身ID，新增传 {@code null}
     */
    private void assertCodeUnique(String code, Long excludeId) {
        if (leaveRepository.existsByCode(code, excludeId)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "申请单号已存在");
        }
    }

    /**
     * 命令字段写入申请模型。
     *
     * @param leave 申请
     * @param command 保存命令
     */
    private void apply(Leave leave, LeaveSaveCommand command) {
        leave.setCode(command.getCode());
        leave.setApplicant(command.getApplicant());
        leave.setLeaveType(command.getLeaveType());
        leave.setStartDate(command.getStartDate());
        leave.setEndDate(command.getEndDate());
        leave.setDays(command.getDays());
        leave.setReason(command.getReason());
        leave.setRemark(command.getRemark());
    }

    /**
     * 领域模型转视图。
     *
     * @param leave 申请
     * @return 申请视图
     */
    private LeaveView toView(Leave leave) {
        LeaveView view = new LeaveView();
        view.setId(leave.getId());
        view.setCode(leave.getCode());
        view.setApplicant(leave.getApplicant());
        view.setLeaveType(leave.getLeaveType());
        view.setStartDate(leave.getStartDate());
        view.setEndDate(leave.getEndDate());
        view.setDays(leave.getDays());
        view.setReason(leave.getReason());
        view.setStatus(leave.getStatus() == null ? null : leave.getStatus().name());
        view.setRemark(leave.getRemark());
        view.setCreateTime(leave.getCreateTime());
        view.setUpdateTime(leave.getUpdateTime());
        return view;
    }
}
