package com.bluewhale.oa.hr.domain.model;

import com.bluewhale.oa.common.OaErrorCode;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 员工扩展档案聚合。
 *
 * <p>一人一条，按租户 + 用户ID 唯一；姓名、部门名称、手机号等基础信息仍由 Auth 维护，
 * 本聚合只保存工号、入离职日期、生日与公开展示开关。</p>
 *
 * @author BlueWhale
 */
@Data
public class EmployeeProfile {

    /** 主键 */
    private Long id;

    /** 用户ID */
    private Long userId;

    /** 工号 */
    private String employeeNo;

    /** 部门ID */
    private Long deptId;

    /** 入职日期 */
    private LocalDate hireDate;

    /** 转正日期 */
    private LocalDate regularDate;

    /** 生日 */
    private LocalDate birthday;

    /** 在职状态 */
    private EmploymentStatus employmentStatus;

    /** 离职日期 */
    private LocalDate leaveDate;

    /** 文化墙是否公开展示 */
    private Boolean cultureVisible;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /**
     * 校验档案基本约束。
     *
     * <p>用户ID必填；转正日期不得早于入职日期；离职日期不得早于入职日期。</p>
     */
    public void ensureValid() {
        if (userId == null) {
            throw OaErrorCode.exception(OaErrorCode.EMPLOYEE_PROFILE_INVALID, "员工档案必须关联用户");
        }
        if (regularDate != null && hireDate != null && regularDate.isBefore(hireDate)) {
            throw OaErrorCode.exception(OaErrorCode.EMPLOYEE_PROFILE_INVALID, "转正日期不能早于入职日期");
        }
        if (leaveDate != null && hireDate != null && leaveDate.isBefore(hireDate)) {
            throw OaErrorCode.exception(OaErrorCode.EMPLOYEE_PROFILE_INVALID, "离职日期不能早于入职日期");
        }
    }

    /**
     * 变更在职状态。
     *
     * @param target 目标状态
     */
    public void markEmployment(EmploymentStatus target) {
        if (target == null) {
            throw OaErrorCode.exception(OaErrorCode.EMPLOYEE_PROFILE_INVALID, "在职状态不能为空");
        }
        employmentStatus = target;
        if (target != EmploymentStatus.LEFT) {
            leaveDate = null;
        }
    }
}
