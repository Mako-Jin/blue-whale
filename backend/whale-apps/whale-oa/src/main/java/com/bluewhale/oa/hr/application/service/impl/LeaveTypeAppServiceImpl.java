package com.bluewhale.oa.hr.application.service.impl;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.oa.common.OaErrorCode;
import com.bluewhale.oa.hr.application.command.LeaveTypeSaveCommand;
import com.bluewhale.oa.hr.application.service.LeaveTypeAppService;
import com.bluewhale.oa.hr.application.view.LeaveTypeView;
import com.bluewhale.oa.hr.domain.model.LeaveType;
import com.bluewhale.oa.hr.domain.model.LeaveTypeQuery;
import com.bluewhale.oa.hr.domain.model.LeaveTypeUnit;
import com.bluewhale.oa.hr.domain.repository.LeaveTypeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * 假期类型应用服务实现。
 *
 * @author BlueWhale
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LeaveTypeAppServiceImpl implements LeaveTypeAppService {

    /** 默认最小请假单位（半天） */
    private static final BigDecimal DEFAULT_MIN_UNIT = new BigDecimal("0.5");

    private final LeaveTypeRepository leaveTypeRepository;

    @Override
    public PageResult<LeaveTypeView> page(LeaveTypeQuery query) {
        List<LeaveTypeView> records = leaveTypeRepository.page(query).stream().map(this::toView).toList();
        return PageResult.of(records, leaveTypeRepository.count(query), query.getCurrent(), query.getSize());
    }

    @Override
    public List<LeaveTypeView> listEnabled() {
        return leaveTypeRepository.listEnabled().stream().map(this::toView).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LeaveTypeView create(LeaveTypeSaveCommand command) {
        if (leaveTypeRepository.findByCode(command.getCode()).isPresent()) {
            throw OaErrorCode.exception(OaErrorCode.LEAVE_TYPE_EXISTS, "假期类型编码已存在: " + command.getCode());
        }
        LeaveType leaveType = new LeaveType();
        leaveType.setEnabled(Boolean.TRUE);
        leaveType.setSortNo(0);
        apply(leaveType, command);
        leaveType.ensureValid();
        leaveTypeRepository.insert(leaveType);
        log.info("假期类型已创建: id={}, code={}", leaveType.getId(), leaveType.getCode());
        return toView(leaveType);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LeaveTypeView update(Long id, LeaveTypeSaveCommand command) {
        LeaveType leaveType = requireLeaveType(id);
        leaveTypeRepository.findByCode(command.getCode())
                .filter(other -> !other.getId().equals(id))
                .ifPresent(other -> {
                    throw OaErrorCode.exception(OaErrorCode.LEAVE_TYPE_EXISTS,
                            "假期类型编码已存在: " + command.getCode());
                });
        apply(leaveType, command);
        leaveType.ensureValid();
        leaveTypeRepository.update(leaveType);
        log.info("假期类型已更新: id={}, code={}", id, leaveType.getCode());
        return toView(leaveType);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LeaveTypeView toggle(Long id, boolean enabled) {
        LeaveType leaveType = requireLeaveType(id);
        leaveType.toggle(enabled);
        leaveTypeRepository.update(leaveType);
        log.info("假期类型启停: id={}, enabled={}", id, enabled);
        return toView(leaveType);
    }

    /**
     * 查询假期类型，缺失时抛业务异常。
     *
     * @param id 主键
     * @return 假期类型
     */
    private LeaveType requireLeaveType(Long id) {
        return leaveTypeRepository.findById(id)
                .orElseThrow(() -> OaErrorCode.exception(OaErrorCode.LEAVE_TYPE_NOT_FOUND,
                        "假期类型不存在，id=" + id));
    }

    /**
     * 命令字段写入领域模型。
     *
     * @param leaveType 假期类型
     * @param command 保存命令
     */
    private void apply(LeaveType leaveType, LeaveTypeSaveCommand command) {
        leaveType.setCode(command.getCode());
        leaveType.setName(command.getName());
        leaveType.setUnit(LeaveTypeUnit.of(command.getUnit()));
        leaveType.setAnnualQuota(command.getAnnualQuota() == null
                ? BigDecimal.ZERO : command.getAnnualQuota());
        leaveType.setMinUnit(command.getMinUnit() == null ? DEFAULT_MIN_UNIT : command.getMinUnit());
        leaveType.setRequiresAttachment(command.getRequiresAttachment() == null
                ? Boolean.FALSE : command.getRequiresAttachment());
        leaveType.setAllowNegative(command.getAllowNegative() == null
                ? Boolean.FALSE : command.getAllowNegative());
        if (command.getEnabled() != null) {
            leaveType.setEnabled(command.getEnabled());
        }
        if (command.getSortNo() != null) {
            leaveType.setSortNo(command.getSortNo());
        }
        leaveType.setRemark(command.getRemark());
    }

    /**
     * 领域模型转视图。
     *
     * @param leaveType 假期类型
     * @return 假期类型视图
     */
    private LeaveTypeView toView(LeaveType leaveType) {
        LeaveTypeView view = new LeaveTypeView();
        view.setId(leaveType.getId());
        view.setCode(leaveType.getCode());
        view.setName(leaveType.getName());
        view.setUnit(leaveType.getUnit() == null ? null : leaveType.getUnit().name());
        view.setAnnualQuota(leaveType.getAnnualQuota());
        view.setMinUnit(leaveType.getMinUnit());
        view.setRequiresAttachment(leaveType.getRequiresAttachment());
        view.setAllowNegative(leaveType.getAllowNegative());
        view.setEnabled(leaveType.getEnabled());
        view.setSortNo(leaveType.getSortNo());
        view.setRemark(leaveType.getRemark());
        view.setCreateTime(leaveType.getCreateTime());
        view.setUpdateTime(leaveType.getUpdateTime());
        return view;
    }
}
