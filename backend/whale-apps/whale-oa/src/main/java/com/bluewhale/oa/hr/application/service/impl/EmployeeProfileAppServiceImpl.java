package com.bluewhale.oa.hr.application.service.impl;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.oa.common.OaErrorCode;
import com.bluewhale.oa.hr.application.command.EmployeeProfileSaveCommand;
import com.bluewhale.oa.hr.application.service.EmployeeProfileAppService;
import com.bluewhale.oa.hr.application.view.EmployeeProfileView;
import com.bluewhale.oa.hr.domain.model.EmployeeProfile;
import com.bluewhale.oa.hr.domain.model.EmployeeProfileQuery;
import com.bluewhale.oa.hr.domain.model.EmploymentStatus;
import com.bluewhale.oa.hr.domain.repository.EmployeeProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 员工扩展档案应用服务实现。
 *
 * @author BlueWhale
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmployeeProfileAppServiceImpl implements EmployeeProfileAppService {

    private final EmployeeProfileRepository employeeProfileRepository;

    @Override
    public PageResult<EmployeeProfileView> page(EmployeeProfileQuery query) {
        List<EmployeeProfileView> records = employeeProfileRepository.page(query).stream()
                .map(this::toView).toList();
        return PageResult.of(records, employeeProfileRepository.count(query),
                query.getCurrent(), query.getSize());
    }

    @Override
    public EmployeeProfileView findByUserId(Long userId) {
        return toView(requireByUserId(userId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public EmployeeProfileView save(EmployeeProfileSaveCommand command) {
        EmployeeProfile existing = employeeProfileRepository.findByUserId(command.getUserId()).orElse(null);
        EmployeeProfile profile = existing == null ? new EmployeeProfile() : existing;
        if (existing == null) {
            profile.setUserId(command.getUserId());
            profile.setEmploymentStatus(EmploymentStatus.ACTIVE);
            profile.setCultureVisible(Boolean.TRUE);
        }
        apply(profile, command);
        profile.ensureValid();
        if (existing == null) {
            employeeProfileRepository.insert(profile);
        } else {
            employeeProfileRepository.update(profile);
        }
        log.info("员工扩展档案已保存: userId={}, status={}", profile.getUserId(), profile.getEmploymentStatus());
        return toView(profile);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long userId) {
        EmployeeProfile profile = requireByUserId(userId);
        employeeProfileRepository.deleteById(profile.getId());
        log.info("员工扩展档案已删除: userId={}", userId);
    }

    /**
     * 按用户ID查询档案，缺失时抛业务异常。
     *
     * @param userId 用户ID
     * @return 档案
     */
    private EmployeeProfile requireByUserId(Long userId) {
        return employeeProfileRepository.findByUserId(userId)
                .orElseThrow(() -> OaErrorCode.exception(OaErrorCode.EMPLOYEE_PROFILE_NOT_FOUND,
                        "员工档案不存在，userId=" + userId));
    }

    /**
     * 命令字段写入领域模型。
     *
     * @param profile 档案
     * @param command 保存命令
     */
    private void apply(EmployeeProfile profile, EmployeeProfileSaveCommand command) {
        profile.setEmployeeNo(command.getEmployeeNo());
        profile.setDeptId(command.getDeptId());
        profile.setHireDate(command.getHireDate());
        profile.setRegularDate(command.getRegularDate());
        profile.setBirthday(command.getBirthday());
        if (command.getEmploymentStatus() != null) {
            profile.setEmploymentStatus(EmploymentStatus.of(command.getEmploymentStatus()));
        }
        profile.setLeaveDate(command.getLeaveDate());
        profile.setCultureVisible(command.getCultureVisible() == null
                ? Boolean.TRUE : command.getCultureVisible());
        profile.setRemark(command.getRemark());
    }

    /**
     * 领域模型转视图。
     *
     * @param profile 档案
     * @return 档案视图
     */
    private EmployeeProfileView toView(EmployeeProfile profile) {
        EmployeeProfileView view = new EmployeeProfileView();
        view.setId(profile.getId());
        view.setUserId(profile.getUserId());
        view.setEmployeeNo(profile.getEmployeeNo());
        view.setDeptId(profile.getDeptId());
        view.setHireDate(profile.getHireDate());
        view.setRegularDate(profile.getRegularDate());
        view.setBirthday(profile.getBirthday());
        view.setEmploymentStatus(profile.getEmploymentStatus() == null
                ? null : profile.getEmploymentStatus().name());
        view.setLeaveDate(profile.getLeaveDate());
        view.setCultureVisible(profile.getCultureVisible());
        view.setRemark(profile.getRemark());
        view.setCreateTime(profile.getCreateTime());
        view.setUpdateTime(profile.getUpdateTime());
        return view;
    }
}
