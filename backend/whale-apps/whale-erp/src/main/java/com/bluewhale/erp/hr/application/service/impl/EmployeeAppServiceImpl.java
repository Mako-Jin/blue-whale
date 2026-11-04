package com.bluewhale.erp.hr.application.service.impl;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.erp.common.ErpErrorCode;
import com.bluewhale.erp.hr.application.command.EmployeeCreateCommand;
import com.bluewhale.erp.hr.application.command.EmployeeUpdateCommand;
import com.bluewhale.erp.hr.application.command.EmploymentChangeCreateCommand;
import com.bluewhale.erp.hr.application.service.EmployeeAppService;
import com.bluewhale.erp.hr.application.view.EmployeeView;
import com.bluewhale.erp.hr.application.view.EmploymentChangeView;
import com.bluewhale.erp.hr.domain.model.Employee;
import com.bluewhale.erp.hr.domain.model.EmploymentChange;
import com.bluewhale.erp.hr.domain.model.HrQuery;
import com.bluewhale.erp.hr.domain.model.HrStatus;
import com.bluewhale.erp.hr.domain.model.OrgUnit;
import com.bluewhale.erp.hr.domain.repository.EmployeeRepository;
import com.bluewhale.erp.hr.domain.repository.EmploymentChangeRepository;
import com.bluewhale.erp.hr.domain.repository.HrOrgRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * 员工档案与异动应用服务实现。
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class EmployeeAppServiceImpl implements EmployeeAppService {

    private final EmployeeRepository employeeRepository;

    private final EmploymentChangeRepository changeRepository;

    private final HrOrgRepository orgRepository;

    @Override
    public PageResult<EmployeeView> page(HrQuery query) {
        List<EmployeeView> records = employeeRepository.page(query).stream().map(EmployeeView::of).toList();
        return PageResult.of(records, employeeRepository.count(query), query.getCurrent(), query.getSize());
    }

    @Override
    public EmployeeView detail(String employeeNo) {
        return EmployeeView.of(requireEmployee(employeeNo));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public EmployeeView create(EmployeeCreateCommand command) {
        if (employeeRepository.findByEmployeeNo(command.getEmployeeNo()).isPresent()) {
            throw ErpErrorCode.exception(ErpErrorCode.EMPLOYEE_CODE_CONFLICT);
        }
        requireEnabledOrg(command.getOrgCode());
        Employee employee = new Employee();
        employee.setEmployeeNo(command.getEmployeeNo().trim());
        employee.setName(command.getName().trim());
        employee.setGender(command.getGender());
        employee.setPhone(command.getPhone());
        employee.setEmail(command.getEmail());
        employee.setOrgCode(command.getOrgCode());
        employee.setPosition(command.getPosition());
        employee.setHireDate(command.getHireDate());
        employee.setStatus(HrStatus.EMPLOYEE_ACTIVE);
        employee.setBaseSalary(command.getBaseSalary() == null ? BigDecimal.ZERO : command.getBaseSalary());
        employee.setAllowance(command.getAllowance() == null ? BigDecimal.ZERO : command.getAllowance());
        employee.setSocialBase(command.getSocialBase() == null ? BigDecimal.ZERO : command.getSocialBase());
        employee.setUserId(command.getUserId());
        employee.setRemark(command.getRemark());
        employee.validateForCreate();
        employeeRepository.insert(employee);
        return EmployeeView.of(employee);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public EmployeeView update(String employeeNo, EmployeeUpdateCommand command) {
        Employee employee = requireEmployee(employeeNo);
        employee.setName(command.getName().trim());
        employee.setGender(command.getGender());
        employee.setPhone(command.getPhone());
        employee.setEmail(command.getEmail());
        if (command.getPosition() != null) {
            employee.setPosition(command.getPosition());
        }
        if (command.getBaseSalary() != null) {
            employee.setBaseSalary(command.getBaseSalary());
        }
        if (command.getAllowance() != null) {
            employee.setAllowance(command.getAllowance());
        }
        if (command.getSocialBase() != null) {
            employee.setSocialBase(command.getSocialBase());
        }
        if (command.getUserId() != null) {
            employee.setUserId(command.getUserId());
        }
        employee.setRemark(command.getRemark());
        employee.validateForCreate();
        employeeRepository.update(employee);
        return EmployeeView.of(employee);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public EmploymentChangeView createChange(EmploymentChangeCreateCommand command) {
        Employee employee = requireEmployee(command.getEmployeeNo());
        EmploymentChange change = new EmploymentChange();
        change.setChangeNo(changeRepository.nextChangeNo());
        change.setEmployeeNo(employee.getEmployeeNo());
        change.setChangeType(command.getChangeType());
        change.setEffectiveDate(command.getEffectiveDate());
        change.setFromOrgCode(employee.getOrgCode());
        change.setToOrgCode(command.getToOrgCode());
        change.setFromPosition(employee.getPosition());
        change.setToPosition(command.getToPosition());
        change.setFromSalary(employee.getBaseSalary());
        change.setToSalary(command.getToSalary());
        change.setStatus(HrStatus.CHANGE_DRAFT);
        change.setRemark(command.getRemark());
        change.validate();
        changeRepository.insert(change);
        return EmploymentChangeView.of(change);
    }

    @Override
    public PageResult<EmploymentChangeView> pageChanges(HrQuery query) {
        List<EmploymentChangeView> records = changeRepository.page(query).stream()
                .map(EmploymentChangeView::of).toList();
        return PageResult.of(records, changeRepository.count(query), query.getCurrent(), query.getSize());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public EmploymentChangeView effectiveChange(Long id) {
        EmploymentChange change = changeRepository.findById(id)
                .orElseThrow(() -> ErpErrorCode.exception(ErpErrorCode.EMPLOYMENT_CHANGE_NOT_FOUND));
        Employee employee = requireEmployee(change.getEmployeeNo());
        if (change.getToOrgCode() != null && !change.getToOrgCode().isBlank()) {
            requireEnabledOrg(change.getToOrgCode());
        }
        change.effective();
        change.applyTo(employee);
        employeeRepository.update(employee);
        changeRepository.update(change);
        return EmploymentChangeView.of(change);
    }

    /**
     * 查询员工，不存在按 404 处理。
     *
     * @param employeeNo 员工编号
     * @return 员工
     */
    private Employee requireEmployee(String employeeNo) {
        return employeeRepository.findByEmployeeNo(employeeNo)
                .orElseThrow(() -> ErpErrorCode.exception(ErpErrorCode.EMPLOYEE_NOT_FOUND));
    }

    /**
     * 校验组织存在且启用。
     *
     * @param orgCode 组织编码
     */
    private void requireEnabledOrg(String orgCode) {
        OrgUnit org = orgRepository.findByCode(orgCode)
                .orElseThrow(() -> ErpErrorCode.exception(ErpErrorCode.ORG_NOT_FOUND, "组织 " + orgCode + " 不存在"));
        if (!org.enabled()) {
            throw ErpErrorCode.exception(ErpErrorCode.HR_INVALID, "组织 " + orgCode + " 已停用");
        }
    }
}
