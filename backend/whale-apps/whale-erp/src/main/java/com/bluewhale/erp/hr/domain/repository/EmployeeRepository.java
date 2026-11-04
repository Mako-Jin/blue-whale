package com.bluewhale.erp.hr.domain.repository;

import com.bluewhale.erp.hr.domain.model.Employee;
import com.bluewhale.erp.hr.domain.model.HrQuery;

import java.util.List;
import java.util.Optional;

/**
 * 员工档案仓储。
 *
 * @author BlueWhale
 */
public interface EmployeeRepository {

    /**
     * 分页查询员工。
     *
     * @param query 查询条件
     * @return 员工列表
     */
    List<Employee> page(HrQuery query);

    /**
     * 统计员工数量。
     *
     * @param query 查询条件
     * @return 总条数
     */
    long count(HrQuery query);

    /**
     * 按员工编号查询。
     *
     * @param employeeNo 员工编号
     * @return 员工
     */
    Optional<Employee> findByEmployeeNo(String employeeNo);

    /**
     * 按平台账号查询在职员工。
     *
     * @param userId 平台账号 ID
     * @return 员工
     */
    Optional<Employee> findByUserId(Long userId);

    /**
     * 查询全部在职员工。
     *
     * @return 在职员工列表
     */
    List<Employee> listActive();

    /**
     * 新增员工。
     *
     * @param employee 员工
     */
    void insert(Employee employee);

    /**
     * 更新员工档案。
     *
     * @param employee 员工
     */
    void update(Employee employee);
}
