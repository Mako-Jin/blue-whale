package com.bluewhale.erp.hr.domain.model;

import com.bluewhale.erp.common.ErpErrorCode;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 员工档案聚合。
 *
 * <p>不变量：基本工资、津贴与社保基数非空且非负；离职日期不早于入职日期；
 * 仅在职员工可调动或离职。</p>
 *
 * @author BlueWhale
 */
@Data
public class Employee {

    /** 主键 */
    private Long id;

    /** 员工编号 */
    private String employeeNo;

    /** 姓名 */
    private String name;

    /** 性别：MALE / FEMALE / UNKNOWN */
    private String gender;

    /** 手机号 */
    private String phone;

    /** 邮箱 */
    private String email;

    /** 所属组织编码 */
    private String orgCode;

    /** 岗位 */
    private String position;

    /** 入职日期 */
    private LocalDate hireDate;

    /** 离职日期 */
    private LocalDate leaveDate;

    /** 状态：ACTIVE / LEFT */
    private String status;

    /** 月基本工资 */
    private BigDecimal baseSalary;

    /** 固定津贴 */
    private BigDecimal allowance;

    /** 社保/公积金基数（0 表示取基本工资） */
    private BigDecimal socialBase;

    /** 平台账号 ID */
    private Long userId;

    /** 备注 */
    private String remark;

    /**
     * 校验建档信息。
     */
    public void validateForCreate() {
        if (employeeNo == null || employeeNo.isBlank()) {
            throw ErpErrorCode.exception(ErpErrorCode.HR_INVALID, "员工编号不能为空");
        }
        if (name == null || name.isBlank()) {
            throw ErpErrorCode.exception(ErpErrorCode.HR_INVALID, "员工姓名不能为空");
        }
        if (orgCode == null || orgCode.isBlank()) {
            throw ErpErrorCode.exception(ErpErrorCode.HR_INVALID, "所属组织不能为空");
        }
        if (hireDate == null) {
            throw ErpErrorCode.exception(ErpErrorCode.HR_INVALID, "入职日期不能为空");
        }
        requireNonNegative(baseSalary, "基本工资");
        requireNonNegative(allowance, "固定津贴");
        requireNonNegative(socialBase, "社保基数");
    }

    /**
     * 调动：变更组织、岗位与月薪。
     *
     * @param targetOrgCode 新组织编码
     * @param targetPosition 新岗位
     * @param targetSalary 新月薪（为空则保持不变）
     */
    public void transfer(String targetOrgCode, String targetPosition, BigDecimal targetSalary) {
        requireActive("只有在职员工可以调动");
        if (targetOrgCode == null || targetOrgCode.isBlank()) {
            throw ErpErrorCode.exception(ErpErrorCode.HR_INVALID, "调动目标组织不能为空");
        }
        requireNonNegative(targetSalary, "月薪");
        orgCode = targetOrgCode;
        if (targetPosition != null && !targetPosition.isBlank()) {
            position = targetPosition;
        }
        if (targetSalary != null) {
            baseSalary = targetSalary;
        }
    }

    /**
     * 离职：置为离职状态并登记离职日期。
     *
     * @param date 离职日期
     */
    public void resign(LocalDate date) {
        requireActive("员工已离职，不能重复操作");
        if (date == null || date.isBefore(hireDate)) {
            throw ErpErrorCode.exception(ErpErrorCode.HR_INVALID, "离职日期不能早于入职日期");
        }
        status = HrStatus.EMPLOYEE_LEFT;
        leaveDate = date;
    }

    /**
     * 复职：恢复在职状态并清空离职日期。
     */
    public void activate() {
        status = HrStatus.EMPLOYEE_ACTIVE;
        leaveDate = null;
    }

    /**
     * 计算计薪基数（社保基数为 0 时取基本工资）。
     *
     * @return 计薪基数
     */
    public BigDecimal contributionBase() {
        if (socialBase != null && socialBase.signum() > 0) {
            return socialBase;
        }
        return baseSalary == null ? BigDecimal.ZERO : baseSalary;
    }

    /**
     * 校验在职状态。
     *
     * @param message 失败提示
     */
    private void requireActive(String message) {
        if (!HrStatus.EMPLOYEE_ACTIVE.equals(status)) {
            throw ErpErrorCode.exception(ErpErrorCode.HR_STATE_CONFLICT, message);
        }
    }

    /**
     * 校验金额非负。
     *
     * @param value 金额
     * @param label 字段名
     */
    private void requireNonNegative(BigDecimal value, String label) {
        if (value != null && value.signum() < 0) {
            throw ErpErrorCode.exception(ErpErrorCode.HR_INVALID, label + "不能为负数");
        }
    }
}
