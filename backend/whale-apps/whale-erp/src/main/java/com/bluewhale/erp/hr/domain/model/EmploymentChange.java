package com.bluewhale.erp.hr.domain.model;

import com.bluewhale.erp.common.ErpErrorCode;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 员工异动单。
 *
 * <p>不变量：类型在 HIRE/TRANSFER/PROMOTION/RESIGN/RETURN 内；生效日期非空；
 * 仅草稿可生效或取消；生效时按类型回写员工档案。</p>
 *
 * @author BlueWhale
 */
@Data
public class EmploymentChange {

    /** 主键 */
    private Long id;

    /** 异动单号 */
    private String changeNo;

    /** 员工编号 */
    private String employeeNo;

    /** 类型：HIRE / TRANSFER / PROMOTION / RESIGN / RETURN */
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

    /** 状态：DRAFT / EFFECTIVE / CANCELLED */
    private String status;

    /** 备注 */
    private String remark;

    /**
     * 校验异动信息。
     */
    public void validate() {
        if (employeeNo == null || employeeNo.isBlank()) {
            throw ErpErrorCode.exception(ErpErrorCode.HR_INVALID, "异动员工不能为空");
        }
        if (!isKnownType(changeType)) {
            throw ErpErrorCode.exception(ErpErrorCode.HR_INVALID, "异动类型非法");
        }
        if (effectiveDate == null) {
            throw ErpErrorCode.exception(ErpErrorCode.HR_INVALID, "生效日期不能为空");
        }
        if ((HrStatus.CHANGE_TRANSFER.equals(changeType)
                || HrStatus.CHANGE_PROMOTION.equals(changeType))
                && (toOrgCode == null || toOrgCode.isBlank())) {
            throw ErpErrorCode.exception(ErpErrorCode.HR_INVALID, "调动/晋升必须填写目标组织");
        }
        if (toSalary != null && toSalary.signum() < 0) {
            throw ErpErrorCode.exception(ErpErrorCode.HR_INVALID, "新月薪不能为负数");
        }
    }

    /**
     * 生效异动单。
     */
    public void effective() {
        requireDraft();
        status = HrStatus.CHANGE_EFFECTIVE;
    }

    /**
     * 取消异动单。
     */
    public void cancel() {
        requireDraft();
        status = HrStatus.CHANGE_CANCELLED;
    }

    /**
     * 按类型回写员工档案。
     *
     * @param employee 员工
     */
    public void applyTo(Employee employee) {
        switch (changeType) {
            case HrStatus.CHANGE_HIRE -> employee.activate();
            case HrStatus.CHANGE_TRANSFER, HrStatus.CHANGE_PROMOTION ->
                    employee.transfer(toOrgCode, toPosition, toSalary);
            case HrStatus.CHANGE_RESIGN -> employee.resign(effectiveDate);
            case HrStatus.CHANGE_RETURN -> {
                employee.activate();
                if (toOrgCode != null && !toOrgCode.isBlank()) {
                    employee.transfer(toOrgCode, toPosition, toSalary);
                }
            }
            default -> throw ErpErrorCode.exception(ErpErrorCode.HR_INVALID, "异动类型非法");
        }
    }

    /**
     * 校验草稿状态。
     */
    private void requireDraft() {
        if (!HrStatus.CHANGE_DRAFT.equals(status)) {
            throw ErpErrorCode.exception(ErpErrorCode.HR_STATE_CONFLICT, "只有草稿异动单可以生效或取消");
        }
    }

    /**
     * 判断异动类型是否合法。
     *
     * @param type 类型
     * @return 是否合法
     */
    private boolean isKnownType(String type) {
        return HrStatus.CHANGE_HIRE.equals(type)
                || HrStatus.CHANGE_TRANSFER.equals(type)
                || HrStatus.CHANGE_PROMOTION.equals(type)
                || HrStatus.CHANGE_RESIGN.equals(type)
                || HrStatus.CHANGE_RETURN.equals(type);
    }
}
