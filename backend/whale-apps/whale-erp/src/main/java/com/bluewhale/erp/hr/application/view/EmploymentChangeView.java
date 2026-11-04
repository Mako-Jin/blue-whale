package com.bluewhale.erp.hr.application.view;

import com.bluewhale.erp.hr.domain.model.EmploymentChange;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 员工异动视图。
 *
 * @author BlueWhale
 */
@Data
public class EmploymentChangeView {

    /** 主键 */
    private Long id;

    /** 异动单号 */
    private String changeNo;

    /** 员工编号 */
    private String employeeNo;

    /** 异动类型 */
    private String changeType;

    /** 生效日期 */
    private LocalDate effectiveDate;

    /** 原组织编码 */
    private String fromOrgCode;

    /** 新组织编码 */
    private String toOrgCode;

    /** 原岗位 */
    private String fromPosition;

    /** 新岗位 */
    private String toPosition;

    /** 原月薪 */
    private BigDecimal fromSalary;

    /** 新月薪 */
    private BigDecimal toSalary;

    /** 状态 */
    private String status;

    /** 备注 */
    private String remark;

    /**
     * 由领域对象构建视图。
     *
     * @param change 异动单
     * @return 视图
     */
    public static EmploymentChangeView of(EmploymentChange change) {
        EmploymentChangeView view = new EmploymentChangeView();
        view.setId(change.getId());
        view.setChangeNo(change.getChangeNo());
        view.setEmployeeNo(change.getEmployeeNo());
        view.setChangeType(change.getChangeType());
        view.setEffectiveDate(change.getEffectiveDate());
        view.setFromOrgCode(change.getFromOrgCode());
        view.setToOrgCode(change.getToOrgCode());
        view.setFromPosition(change.getFromPosition());
        view.setToPosition(change.getToPosition());
        view.setFromSalary(change.getFromSalary());
        view.setToSalary(change.getToSalary());
        view.setStatus(change.getStatus());
        view.setRemark(change.getRemark());
        return view;
    }
}
