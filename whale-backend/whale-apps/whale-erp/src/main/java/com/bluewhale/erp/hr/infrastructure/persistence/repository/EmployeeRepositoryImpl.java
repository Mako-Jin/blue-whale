package com.bluewhale.erp.hr.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.erp.hr.domain.model.Employee;
import com.bluewhale.erp.hr.domain.model.HrQuery;
import com.bluewhale.erp.hr.domain.model.HrStatus;
import com.bluewhale.erp.hr.domain.repository.EmployeeRepository;
import com.bluewhale.erp.hr.infrastructure.persistence.entity.EmployeeEntity;
import com.bluewhale.erp.hr.infrastructure.persistence.mapper.EmployeeMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Optional;

/**
 * 员工档案仓储实现（MyBatis-Plus）。
 *
 * @author BlueWhale
 */
@Repository
@RequiredArgsConstructor
public class EmployeeRepositoryImpl implements EmployeeRepository {

    private final EmployeeMapper employeeMapper;

    @Override
    public List<Employee> page(HrQuery query) {
        Page<EmployeeEntity> page = employeeMapper.selectPage(
                new Page<>(Math.max(query.getCurrent(), 1L), Math.max(query.getSize(), 1L)), filter(query));
        return page.getRecords().stream().map(this::toDomain).toList();
    }

    @Override
    public long count(HrQuery query) {
        return employeeMapper.selectCount(filter(query));
    }

    @Override
    public Optional<Employee> findByEmployeeNo(String employeeNo) {
        EmployeeEntity entity = employeeMapper.selectOne(new LambdaQueryWrapper<EmployeeEntity>()
                .eq(EmployeeEntity::getEmployeeNo, employeeNo)
                .last("limit 1"));
        return entity == null ? Optional.empty() : Optional.of(toDomain(entity));
    }

    @Override
    public Optional<Employee> findByUserId(Long userId) {
        EmployeeEntity entity = employeeMapper.selectOne(new LambdaQueryWrapper<EmployeeEntity>()
                .eq(EmployeeEntity::getUserId, userId)
                .orderByAsc(EmployeeEntity::getEmployeeNo)
                .last("limit 1"));
        return entity == null ? Optional.empty() : Optional.of(toDomain(entity));
    }

    @Override
    public List<Employee> listActive() {
        return employeeMapper.selectList(new LambdaQueryWrapper<EmployeeEntity>()
                        .eq(EmployeeEntity::getStatus, HrStatus.EMPLOYEE_ACTIVE)
                        .orderByAsc(EmployeeEntity::getEmployeeNo))
                .stream().map(this::toDomain).toList();
    }

    @Override
    public void insert(Employee employee) {
        EmployeeEntity entity = new EmployeeEntity();
        fill(entity, employee);
        employeeMapper.insert(entity);
        employee.setId(entity.getId());
    }

    @Override
    public void update(Employee employee) {
        EmployeeEntity entity = new EmployeeEntity();
        entity.setId(employee.getId());
        entity.setName(employee.getName());
        entity.setGender(employee.getGender());
        entity.setPhone(employee.getPhone());
        entity.setEmail(employee.getEmail());
        entity.setOrgCode(employee.getOrgCode());
        entity.setPosition(employee.getPosition());
        entity.setLeaveDate(employee.getLeaveDate());
        entity.setStatus(employee.getStatus());
        entity.setBaseSalary(employee.getBaseSalary());
        entity.setAllowance(employee.getAllowance());
        entity.setSocialBase(employee.getSocialBase());
        entity.setUserId(employee.getUserId());
        entity.setRemark(employee.getRemark());
        employeeMapper.updateById(entity);
    }

    /**
     * 构建查询条件。
     *
     * @param query 查询条件
     * @return Lambda 条件
     */
    private LambdaQueryWrapper<EmployeeEntity> filter(HrQuery query) {
        LambdaQueryWrapper<EmployeeEntity> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(query.getKeyword())) {
            wrapper.and(w -> w.like(EmployeeEntity::getEmployeeNo, query.getKeyword())
                    .or().like(EmployeeEntity::getName, query.getKeyword())
                    .or().like(EmployeeEntity::getPosition, query.getKeyword()));
        }
        if (StringUtils.hasText(query.getStatus())) {
            wrapper.eq(EmployeeEntity::getStatus, query.getStatus());
        }
        if (StringUtils.hasText(query.getOrgCode())) {
            wrapper.eq(EmployeeEntity::getOrgCode, query.getOrgCode());
        }
        return wrapper.orderByAsc(EmployeeEntity::getEmployeeNo);
    }

    /**
     * 实体转领域模型。
     *
     * @param entity 实体
     * @return 领域模型
     */
    private Employee toDomain(EmployeeEntity entity) {
        Employee employee = new Employee();
        employee.setId(entity.getId());
        employee.setEmployeeNo(entity.getEmployeeNo());
        employee.setName(entity.getName());
        employee.setGender(entity.getGender());
        employee.setPhone(entity.getPhone());
        employee.setEmail(entity.getEmail());
        employee.setOrgCode(entity.getOrgCode());
        employee.setPosition(entity.getPosition());
        employee.setHireDate(entity.getHireDate());
        employee.setLeaveDate(entity.getLeaveDate());
        employee.setStatus(entity.getStatus());
        employee.setBaseSalary(entity.getBaseSalary());
        employee.setAllowance(entity.getAllowance());
        employee.setSocialBase(entity.getSocialBase());
        employee.setUserId(entity.getUserId());
        employee.setRemark(entity.getRemark());
        return employee;
    }

    /**
     * 领域模型写入实体。
     *
     * @param entity 实体
     * @param employee 领域模型
     */
    private void fill(EmployeeEntity entity, Employee employee) {
        entity.setEmployeeNo(employee.getEmployeeNo());
        entity.setName(employee.getName());
        entity.setGender(employee.getGender());
        entity.setPhone(employee.getPhone());
        entity.setEmail(employee.getEmail());
        entity.setOrgCode(employee.getOrgCode());
        entity.setPosition(employee.getPosition());
        entity.setHireDate(employee.getHireDate());
        entity.setLeaveDate(employee.getLeaveDate());
        entity.setStatus(employee.getStatus());
        entity.setBaseSalary(employee.getBaseSalary());
        entity.setAllowance(employee.getAllowance());
        entity.setSocialBase(employee.getSocialBase());
        entity.setUserId(employee.getUserId());
        entity.setRemark(employee.getRemark());
    }
}
