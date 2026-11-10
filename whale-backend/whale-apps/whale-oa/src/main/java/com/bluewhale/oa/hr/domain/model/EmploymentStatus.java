package com.bluewhale.oa.hr.domain.model;

import com.bluewhale.oa.common.OaErrorCode;

/**
 * 员工在职状态。
 *
 * <p>取值与 {@code oa_employee_profile.employment_status} 一致，用于人事异动与档案展示。</p>
 *
 * @author BlueWhale
 */
public enum EmploymentStatus {

    /** 在职 */
    ACTIVE,

    /** 试用 */
    PROBATION,

    /** 正式 */
    REGULAR,

    /** 离职 */
    LEFT;

    /**
     * 解析在职状态文本。
     *
     * @param value 状态文本
     * @return 在职状态
     */
    public static EmploymentStatus of(String value) {
        if (value == null) {
            throw OaErrorCode.exception(OaErrorCode.EMPLOYEE_PROFILE_INVALID, "在职状态不能为空");
        }
        try {
            return EmploymentStatus.valueOf(value);
        } catch (IllegalArgumentException exception) {
            throw OaErrorCode.exception(OaErrorCode.EMPLOYEE_PROFILE_INVALID,
                    "在职状态仅支持 ACTIVE / PROBATION / REGULAR / LEFT");
        }
    }
}
