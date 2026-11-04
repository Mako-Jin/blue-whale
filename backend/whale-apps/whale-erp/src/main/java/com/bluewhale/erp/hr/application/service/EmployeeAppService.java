package com.bluewhale.erp.hr.application.service;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.erp.hr.application.command.EmployeeCreateCommand;
import com.bluewhale.erp.hr.application.command.EmployeeUpdateCommand;
import com.bluewhale.erp.hr.application.command.EmploymentChangeCreateCommand;
import com.bluewhale.erp.hr.application.view.EmployeeView;
import com.bluewhale.erp.hr.application.view.EmploymentChangeView;
import com.bluewhale.erp.hr.domain.model.HrQuery;

/**
 * 员工档案与异动应用服务。
 *
 * @author BlueWhale
 */
public interface EmployeeAppService {

    /**
     * 分页查询员工。
     *
     * @param query 查询条件
     * @return 员工分页
     */
    PageResult<EmployeeView> page(HrQuery query);

    /**
     * 员工详情。
     *
     * @param employeeNo 员工编号
     * @return 员工
     */
    EmployeeView detail(String employeeNo);

    /**
     * 新增员工。
     *
     * @param command 创建命令
     * @return 员工
     */
    EmployeeView create(EmployeeCreateCommand command);

    /**
     * 编辑员工非组织类档案。
     *
     * @param employeeNo 员工编号
     * @param command 编辑命令
     * @return 员工
     */
    EmployeeView update(String employeeNo, EmployeeUpdateCommand command);

    /**
     * 新增异动单（草稿）。
     *
     * @param command 创建命令
     * @return 异动单
     */
    EmploymentChangeView createChange(EmploymentChangeCreateCommand command);

    /**
     * 分页查询异动单。
     *
     * @param query 查询条件
     * @return 异动单分页
     */
    PageResult<EmploymentChangeView> pageChanges(HrQuery query);

    /**
     * 异动生效并回写员工档案。
     *
     * @param id 异动单主键
     * @return 异动单
     */
    EmploymentChangeView effectiveChange(Long id);
}
