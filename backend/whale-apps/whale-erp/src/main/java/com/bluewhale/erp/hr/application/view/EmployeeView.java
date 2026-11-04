package com.bluewhale.erp.hr.application.view;

import com.bluewhale.erp.hr.domain.model.Employee;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 员工视图。
 *
 * @author BlueWhale
 */
@Data
public class EmployeeView {

    /** 主键 */
    private Long id;

    /** 员工编号 */
    private String employeeNo;

    /** 姓名 */
    private String name;

    /** 性别 */
    private String gender;

    /** 手机号 */
    private String phone;

    /** 邮箱 */
    private String email;

    /** 组织编码 */
    private String orgCode;

    /** 岗位 */
    private String position;

    /** 入职日期 */
    private LocalDate hireDate;

    /** 离职日期 */
    private LocalDate leaveDate;

    /** 状态 */
    private String status;

    /** 月基本工资 */
    private BigDecimal baseSalary;

    /** 固定津贴 */
    private BigDecimal allowance;

    /** 社保基数 */
    private BigDecimal socialBase;

    /** 平台账号 ID */
    private Long userId;

    /** 备注 */
    private String remark;

    /**
     * 由领域对象构建视图。
     *
     * @param employee 员工
     * @return 视图
     */
    public static EmployeeView of(Employee employee) {
        EmployeeView view = new EmployeeView();
        view.setId(employee.getId());
        view.setEmployeeNo(employee.getEmployeeNo());
        view.setName(employee.getName());
        view.setGender(employee.getGender());
        view.setPhone(employee.getPhone());
        view.setEmail(employee.getEmail());
        view.setOrgCode(employee.getOrgCode());
        view.setPosition(employee.getPosition());
        view.setHireDate(employee.getHireDate());
        view.setLeaveDate(employee.getLeaveDate());
        view.setStatus(employee.getStatus());
        view.setBaseSalary(employee.getBaseSalary());
        view.setAllowance(employee.getAllowance());
        view.setSocialBase(employee.getSocialBase());
        view.setUserId(employee.getUserId());
        view.setRemark(employee.getRemark());
        return view;
    }
}
